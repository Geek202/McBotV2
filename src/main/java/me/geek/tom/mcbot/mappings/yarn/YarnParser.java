package me.geek.tom.mcbot.mappings.yarn;

import me.geek.tom.mcbot.mappings.Mapping;
import me.geek.tom.mcbot.mappings.MappingsParser;
import net.fabricmc.mapping.reader.v2.TinyMetadata;
import net.fabricmc.mapping.tree.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipFile;

public class YarnParser extends MappingsParser {

    private static final Logger LOGGER = LoggerFactory.getLogger(YarnParser.class);

    private final File yarnArchive;
    private final Mono<List<Mapping>> cache;

    public YarnParser(String gameVersion, String version, File yarnArchive) {
        super(gameVersion, version);
        this.yarnArchive = yarnArchive;
        this.cache = loadMappings().cache();
    }

    public Mono<List<Mapping>> loadMappings() {
        return Mono.create(sink -> {
            try (InputStream is = getInputStream()) {

                TinyTree tree = TinyMappingFactory.loadWithDetection(new BufferedReader(new InputStreamReader(is)));
                TinyMetadata meta = tree.getMetadata();
                LOGGER.debug("Loaded yarn tiny version: " + meta.getMajorVersion() + "-" + meta.getMinorVersion()
                        + ", namespaces: " + String.join(", ", meta.getNamespaces()));
                int fields = tree.getClasses().stream().mapToInt(c -> c.getFields().size()).sum();
                int methods = tree.getClasses().stream().mapToInt(c -> c.getMethods().size()).sum();
                int params = tree.getClasses().stream().mapToInt(c ->
                        c.getMethods().stream().mapToInt(m -> m.getParameters().size()).sum()).sum();

                String official = meta.getNamespaces().get(0);
                String intermediate = meta.getNamespaces().get(1);
                String named = meta.getNamespaces().get(2);
                List<Mapping> mappings = new ArrayList<>();
                for (ClassDef cls : tree.getClasses()) {
                    mappings.add(new Mapping(cls.getName(intermediate), cls.getName(official),
                            cls.getName(named), Mapping.getIntermediaryId(cls.getName(intermediate)), Mapping.Type.CLASS));

                    for (FieldDef field : cls.getFields()) {
                        mappings.add(new Mapping(field.getName(intermediate), field.getName(official),
                                field.getName(named), cls.getName(intermediate),
                                field.getDescriptor(named), Mapping.getIntermediaryId(field.getName(intermediate)), Mapping.Type.FIELD));
                    }

                    for (MethodDef method : cls.getMethods()) {
                        mappings.add(new Mapping(method.getName(intermediate), method.getName(official),
                                method.getName(named), cls.getName(intermediate),
                                method.getDescriptor(named), Mapping.getIntermediaryId(method.getName(intermediate)), Mapping.Type.METHOD));
                    }
                }

                LOGGER.debug("Loaded " + tree.getClasses().size() + " classes, " + fields + " fields, "
                        + methods + " methods and " + params + " parameters!");
                sink.success(mappings);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    @Override
    public Mono<List<Mapping>> readMappings() {
        return cache;
    }

    private InputStream getInputStream() throws IOException {
        if (yarnArchive.getName().endsWith(".jar")) {
            ZipFile zipFile = new ZipFile(yarnArchive);
            return zipFile.getInputStream(zipFile.getEntry("mappings/mappings.tiny"));
        } else {
            return new GZIPInputStream(new FileInputStream(yarnArchive));
        }
    }
}
