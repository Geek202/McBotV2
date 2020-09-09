package me.geek.tom.mcbot.mappings.mojmap;

import me.geek.tom.mcbot.mappings.Mapping;
import me.geek.tom.mcbot.mappings.MappingsParser;
import me.geek.tom.mcbot.mappings.mcp.McpParser;
import net.minecraftforge.srgutils.IMappingFile;
import reactor.core.publisher.Mono;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class MojmapParser extends MappingsParser {

    private final File mappingsFile;
    private final Mono<List<Mapping>> mappings;

    protected MojmapParser(String gameVersion, String version, File mappingsFile) {
        super(gameVersion, version);
        this.mappingsFile = mappingsFile;
        this.mappings = this.parseMojmap().cache(Duration.ofHours(1L));
    }

    private Mono<List<Mapping>> parseMojmap() {
        return Mono.create(sink -> {
            try (InputStream is = new FileInputStream(mappingsFile)) {
                IMappingFile mappings = ((IMappingFile) McpParser.load.invoke(null, is)).reverse();

                List<Mapping> mps = new ArrayList<>();
                for (IMappingFile.IClass cls : mappings.getClasses()) {
                    mps.add(new Mapping("", cls.getOriginal(), cls.getMapped(), -1, Mapping.Type.CLASS));

                    for (IMappingFile.IField field : cls.getFields()) {
                        mps.add(new Mapping(field.getOriginal(), field.getOriginal(), field.getMapped(), cls.getMapped(),
                                "", -1, Mapping.Type.FIELD));
                    }

                    for (IMappingFile.IMethod method : cls.getMethods()) {
                        mps.add(new Mapping(method.getOriginal(), method.getOriginal(), method.getMapped(), cls.getMapped(),
                                method.getMappedDescriptor(), -1, Mapping.Type.METHOD));
                    }
                }
                sink.success(mps);
            } catch (IOException | IllegalAccessException | InvocationTargetException e) {
                sink.error(e);
            }
        });
    }

    @Override
    public Mono<List<Mapping>> readMappings() {
        return mappings;
    }
}
