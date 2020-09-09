package me.geek.tom.mcbot.commands;

import me.geek.tom.mcbot.McBot;
import me.geek.tom.mcbot.commands.api.Command;
import me.geek.tom.mcbot.mappings.GameVersion;
import me.geek.tom.mcbot.mappings.MappingsApiUtils;
import me.geek.tom.mcbot.mappings.mojmap.MojmapDatabase;
import me.geek.tom.mcbot.mappings.mojmap.MojmapParser;
import reactor.core.publisher.Mono;

@Command
public class CommandMojmap extends CommandMappings<MojmapParser, MojmapDatabase> {

    private final String name;

    public CommandMojmap(LookupType lookupType, String name) {
        super(lookupType, "moj");
        this.name = name;
    }

    @SuppressWarnings("unused")
    public CommandMojmap() {
        this(LookupType.ALL, "mojmap");
    }

    @Override
    public MojmapDatabase getDatabase(McBot mcBot) {
        return mcBot.getMojmapDatabase();
    }

    @Override
    public CommandMappings<MojmapParser, MojmapDatabase> createSubcommand(LookupType type, String name) {
        return new CommandMojmap(type, name);
    }

    @Override
    public String getTitle(String searchTerm) {
        return "Mojmap mappings query for " + searchTerm;
    }

    @Override
    public Mono<GameVersion> getLatestVersion(MappingsApiUtils utils) {
        return MojmapDatabase.getVersions()
                .flatMapIterable(l -> l)
                .filter(v -> v.stable)
                .collectList()
                .filter(l -> !l.isEmpty())
                .map(l -> l.get(0));
    }

    @Override
    public String getName() {
        return name;
    }
}
