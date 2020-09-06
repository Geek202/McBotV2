package me.geek.tom.mcbot.mappings.mcp;

public class McpVersion {

    public final String gameVersion;
    public final String latestSnapshot;

    public McpVersion(String gameVersion, String latestSnapshot) {
        this.gameVersion = gameVersion;
        this.latestSnapshot = latestSnapshot;
    }
}
