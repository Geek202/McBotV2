package me.geek.tom.mcbot.mappings;

import reactor.core.publisher.Mono;

import java.util.List;

public abstract class MappingsParser {

    private final String gameVersion;
    private final String version;

    protected MappingsParser(String gameVersion, String version) {
        this.gameVersion = gameVersion;
        this.version = version;
    }

    public abstract Mono<List<Mapping>> readMappings();

    public String getGameVersion() {
        return gameVersion;
    }

    public String getVersion() {
        return version;
    }
}
