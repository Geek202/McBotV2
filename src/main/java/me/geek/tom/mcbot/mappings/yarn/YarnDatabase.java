package me.geek.tom.mcbot.mappings.yarn;

import me.geek.tom.mcbot.McBot;
import me.geek.tom.mcbot.mappings.GameVersion;
import me.geek.tom.mcbot.mappings.MappingsDatabase;
import me.geek.tom.mcbot.mappings.MappingsApiUtils;
import me.geek.tom.mcbot.mappings.MavenDownloader;
import reactor.core.publisher.Mono;

import java.io.FileNotFoundException;
import java.util.List;

public class YarnDatabase extends MappingsDatabase<YarnParser> {

    public YarnDatabase(McBot mcBot) {
        super(mcBot);
    }

    @Override
    public Mono<List<GameVersion>> getAvailableVersions() {
        return getMcBot().getMappingUtils()
                .fetchYarnGameVersions();
    }

    @Override
    public Mono<YarnParser> updateMappings(String gameVersion) {
        MappingsApiUtils apiUtils = getMcBot().getMappingUtils();
        if (getParsers().containsKey(gameVersion)) {
            return apiUtils.getLatestYarnVersion(gameVersion)
                    .map(v -> v.version)
                    .flatMap(v -> {
                        YarnParser parser = getParsers().get(gameVersion);
                        if (parser != null && parser.getVersion().equals(v))
                            return Mono.just(parser);

                        return downloadYarn(gameVersion, v);
                    }).map(this::updateParser);
        } else {
            return apiUtils.getLatestYarnVersion(gameVersion)
                    .map(v -> v.version)
                    .flatMap(version -> downloadYarn(gameVersion, version))
                    .map(this::updateParser);
        }
    }

    private Mono<YarnParser> downloadYarn(String gameVersion, String version) {
        MavenDownloader downloader = getMcBot().getFabricMavenDownloader();
        return downloader
                .download("net.fabricmc", "yarn", version, "mergedv2", "jar")
                .onErrorResume(FileNotFoundException.class, e ->
                        downloader.download("net.fabricmc", "yarn", version, "tiny", "gz"))
                .map(f -> new YarnParser(gameVersion, version, f));
    }

}
