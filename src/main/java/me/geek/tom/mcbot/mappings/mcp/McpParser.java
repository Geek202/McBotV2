package me.geek.tom.mcbot.mappings.mcp;

import me.geek.tom.mcbot.mappings.Mapping;
import me.geek.tom.mcbot.mappings.MappingsParser;
import net.minecraftforge.srgutils.IMappingFile;
import reactor.core.publisher.Mono;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipFile;

public class McpParser extends MappingsParser {

    private static Method load;

    static {
        try {
            Class<?> internalUtils = Class.forName("net.minecraftforge.srgutils.InternalUtils");
            load = internalUtils.getDeclaredMethod("load", InputStream.class);
            load.setAccessible(true);
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            e.printStackTrace();
            load = null;
        }
    }

    private final File mcpConfigZip;
    private final File mcpBotZip;
    private final boolean isPre13;
    private final Mono<List<Mapping>> mappings;

    public McpParser(String gameVersion, String version, File mcpConfigZip, File mcpBotZip, boolean isPre13) {
        super(gameVersion, version);
        this.mcpConfigZip = mcpConfigZip;
        this.mcpBotZip = mcpBotZip;
        this.isPre13 = isPre13;
        this.mappings = loadMcpBotZip().cache();
    }

    @Override
    public Mono<List<Mapping>> readMappings() {
        return mappings;
    }

    private Mono<List<Mapping>> loadMcpBotZip() {
        return readSrgZip()
                .map(mappings -> {
                    try (ZipFile mcpBotZip = new ZipFile(this.mcpBotZip)) {

                        List<Mapping> mapping = new ArrayList<>();
                        for (IMappingFile.IClass cls : mappings.getClasses()) {
                            mapping.add(new Mapping("", cls.getOriginal(), cls.getMapped(), -1, Mapping.Type.CLASS));
                        }

                        for (Mapping.Type type : Arrays.stream(Mapping.Type.values()).filter(type ->
                                type != Mapping.Type.CLASS).collect(Collectors.toList())) {

                            Map<String, Mapping> currentMappings = new HashMap<>();
                            for (IMappingFile.IClass cls : mappings.getClasses()) {
                                switch (type) {
                                    case FIELD:
                                        cls.getFields().forEach(f -> {
                                            if (currentMappings.containsKey(f.getMapped())) {
                                                Mapping current = currentMappings.get(f.getMapped());
                                                String currentOwner = current.getOwner();
                                                if (currentOwner.length() > cls.getMapped().length()) {
                                                    current.setOwner(cls.getMapped());
                                                }
                                            } else {
                                                currentMappings.put(f.getMapped(),
                                                        new Mapping(f.getMapped(), f.getOriginal(), "", cls.getMapped(),
                                                                "", Mapping.getIntermediaryId(f.getMapped()), Mapping.Type.FIELD));
                                            }
                                        });
                                    case METHOD:
                                        cls.getMethods().forEach(m -> {
                                            if (currentMappings.containsKey(m.getMapped())) {
                                                Mapping current = currentMappings.get(m.getMapped());
                                                String currentOwner = current.getOwner();
                                                if (currentOwner.length() > cls.getMapped().length()) {
                                                    current.setOwner(cls.getMapped());
                                                }
                                            } else
                                                currentMappings.put(m.getMapped(),
                                                    new Mapping(m.getMapped(), m.getOriginal(), "", cls.getMapped(),
                                                            m.getMappedDescriptor(), Mapping.getIntermediaryId(m.getMapped()),
                                                            Mapping.Type.METHOD));
                                        });
                                }
                            }

                            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                                    mcpBotZip.getInputStream(mcpBotZip.getEntry(
                                            type.name().toLowerCase(Locale.getDefault()) + "s.csv"))))) {
                                String line;
                                while ((line = reader.readLine()) != null) {
                                    if ("searge,name,side,desc".equals(line)) continue;
                                    String[] pts = line.split(",", 4);
                                    if (currentMappings.containsKey(pts[0]))
                                        currentMappings.get(pts[0]).setName(pts[1]);
                                }
                            }

                            mapping.addAll(currentMappings.values());
                        }
                        // Remove invalid mappings:
                        return mapping.stream().filter(m -> !m.getName().isEmpty()).collect(Collectors.toList());
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    private Mono<IMappingFile> readSrgZip() {
        return Mono.create(sink -> {
            try (ZipFile zip = new ZipFile(mcpConfigZip);
                 InputStream is = zip.getInputStream(zip.getEntry(getSrgFile()))) {

                IMappingFile mappings = (IMappingFile) load.invoke(null, is);
                sink.success(mappings);
            } catch (IOException | IllegalAccessException | InvocationTargetException e) {
                sink.error(e);
            }
        });
    }

    private String getSrgFile() {
        return isPre13 ? "joined.srg" : "config/joined.tsrg";
    }
}
