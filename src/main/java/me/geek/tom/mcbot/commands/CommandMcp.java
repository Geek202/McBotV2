package me.geek.tom.mcbot.commands;

import me.geek.tom.mcbot.McBot;
import me.geek.tom.mcbot.commands.api.Command;
import me.geek.tom.mcbot.mappings.GameVersion;
import me.geek.tom.mcbot.mappings.MappingsApiUtils;
import me.geek.tom.mcbot.mappings.mcp.McpDatabase;
import me.geek.tom.mcbot.mappings.mcp.McpParser;
import reactor.core.publisher.Mono;

@Command
public class CommandMcp extends CommandMappings<McpParser, McpDatabase> {

    private final String name;

    public CommandMcp(LookupType lookupType, String name) {
        super(lookupType, "mcp");
        this.name = name;
    }

    @SuppressWarnings("unused")
    public CommandMcp() {
        this(LookupType.ALL, "mcp");
    }

    @Override
    public McpDatabase getDatabase(McBot mcBot) {
        return mcBot.getMcpDatabase();
    }

    @Override
    public CommandMappings<McpParser, McpDatabase> createSubcommand(LookupType type, String name) {
        return new CommandMcp(type, name);
    }

    @Override
    public String getTitle(String searchTerm) {
        return "MCP mappings lookup for " + searchTerm + ":";
    }

    @Override
    public Mono<GameVersion> getLatestVersion(MappingsApiUtils utils) {
        return utils.getLatestMcpGameVersion();
    }

    @Override
    public String getName() {
        return name;
    }
}
