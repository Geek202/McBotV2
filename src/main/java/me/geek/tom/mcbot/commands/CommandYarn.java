package me.geek.tom.mcbot.commands;

import me.geek.tom.mcbot.McBot;
import me.geek.tom.mcbot.commands.api.Command;
import me.geek.tom.mcbot.mappings.GameVersion;
import me.geek.tom.mcbot.mappings.MappingsApiUtils;
import me.geek.tom.mcbot.mappings.yarn.YarnDatabase;
import me.geek.tom.mcbot.mappings.yarn.YarnParser;
import reactor.core.publisher.Mono;

@Command
public class CommandYarn extends CommandMappings<YarnParser, YarnDatabase> {

    private final String name;

    public CommandYarn(LookupType lookupType, String name) {
        super(lookupType, "y");
        this.name = name;
    }

    @SuppressWarnings("unused")
    public CommandYarn() {
        this(LookupType.ALL, "yarn");
    }

    @Override
    public YarnDatabase getDatabase(McBot mcBot) {
        return mcBot.getYarnDatabase();
    }

    @Override
    public CommandMappings<YarnParser, YarnDatabase> createSubcommand(LookupType type, String name) {
        return new CommandYarn(type, name);
    }

    @Override
    public String getTitle(String searchTerm) {
        return "Yarn mappings lookup for: " + searchTerm;
    }

    @Override
    public Mono<GameVersion> getLatestVersion(MappingsApiUtils utils) {
        return utils.getLatestYarnGameVersion();
    }

    @Override
    public String getName() {
        return name;
    }
}
