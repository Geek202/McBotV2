package me.geek.tom.mcbot.mappings.mcp;

import me.geek.tom.mcbot.McBot;
import me.geek.tom.mcbot.mappings.GameVersion;
import me.geek.tom.mcbot.mappings.MappingsApiUtils;
import me.geek.tom.mcbot.mappings.MappingsDatabase;
import me.geek.tom.mcbot.mappings.MavenDownloader;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

import java.util.List;

public class McpDatabase extends MappingsDatabase<McpParser> {
    public McpDatabase(McBot mcBot) {
        super(mcBot);
    }

    @Override
    public Mono<List<GameVersion>> getAvailableVersions() {
        return getMcBot().getMappingUtils().getAllMcpVersions()
                .flatMapIterable(l -> l)
                .map(v -> new GameVersion(v.gameVersion, true))
                .collectList();
    }

    @Override
    public Mono<McpParser> updateMappings(String gameVersion) {
        MappingsApiUtils apiUtils = getMcBot().getMappingUtils();
        if (getParsers().containsKey(gameVersion)) {
            return apiUtils.getLatestMcpVersion(gameVersion)
                    .map(v -> v.latestSnapshot)
                    .flatMap(v -> {
                        McpParser parser = getParsers().get(gameVersion);
                        if (parser.getVersion().equals(v))
                            return Mono.just(parser);

                        return downloadMcp(gameVersion, v);
                    }).map(this::updateParser);
        } else {
            return apiUtils.getLatestMcpVersion(gameVersion)
                    .map(v -> v.latestSnapshot)
                    .flatMap(version -> downloadMcp(gameVersion, version))
                    .map(this::updateParser);
        }
    }

    @NotNull
    private Mono<McpParser> downloadMcp(String gameVersion, String v) {
        MavenDownloader downloader = getMcBot().getForgeMavenDownloader();
        boolean pre13 = getMinVersion(gameVersion) < 13;
        return downloader
                .download("de.oceanlabs.mcp", "mcp_snapshot", v + "-" + gameVersion,
                        null, "zip")
                .zipWith(downloader.download("de.oceanlabs.mcp", pre13 ? "mcp" : "mcp_config",
                        gameVersion, pre13 ? "srg" : "", "zip"),
                        (mcpBot, mcpConfig) -> new McpParser(gameVersion, v, mcpConfig, mcpBot, pre13));
    }

    private static int getMinVersion(String version) {
        String minversion = version.substring(version.indexOf('.') + 1);
        int seconddot = minversion.indexOf('.');
        if (seconddot != -1) {
            minversion = minversion.substring(0, seconddot);
        }
        return Integer.parseInt(minversion);
    }
}
