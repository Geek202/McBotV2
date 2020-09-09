package me.geek.tom.mcbot.mappings.mojmap;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import me.geek.tom.mcbot.McBot;
import me.geek.tom.mcbot.mappings.GameVersion;
import me.geek.tom.mcbot.mappings.MappingsDatabase;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.io.File;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.List;

public class MojmapDatabase extends MappingsDatabase<MojmapParser> {

    private static final String MOJANG_VERSIONS_URL = "https://launchermeta.mojang.com/mc/game/version_manifest.json";
    private static final Gson GSON = new GsonBuilder().create();

    private final File storageDir;

    public MojmapDatabase(McBot mcBot, File storageDir) {
        super(mcBot);
        this.storageDir = storageDir;
    }

    @Override
    public Mono<List<GameVersion>> getAvailableVersions() {
        return getVersions();
    }

    public static Mono<List<GameVersion>> getVersions() {
        return getVersionsFile()
                .map(versions -> versions.versions)
                .flatMapIterable(l -> l)
                .map(v -> new GameVersion(v.id, "release".equals(v.type)))
                .collectList();
    }

    private Mono<File> downloadClientMappings(MojangVersionJson versionJson) {
        return Mono.fromCallable(() -> {
            URL url = new URL(versionJson.downloads.cMappings.url);
            File destination = new File(storageDir, url.getPath());
            File hashFile = new File(storageDir, url.getPath() + ".sha1");

            if (hashFile.exists() && destination.exists()) {
                String currentHash = FileUtils.readFileToString(hashFile, Charset.defaultCharset());
                if (currentHash.equals(versionJson.downloads.cMappings.sha1))
                    return destination;
            }

            FileUtils.copyURLToFile(url, destination);
            FileUtils.writeStringToFile(hashFile, versionJson.downloads.cMappings.sha1, Charset.defaultCharset());
            return destination;
        });
    }

    @NotNull
    private static Mono<MojangVersionsFile> getVersionsFile() {
        return HttpClient.create()
                .get()
                .uri(MOJANG_VERSIONS_URL)
                .responseSingle(($, content) -> content.asString())
                .map(content -> GSON.fromJson(content, MojangVersionsFile.class));
    }

    @Override
    public Mono<MojmapParser> updateMappings(String version) {
        return getVersionsFile()
                .flatMapIterable(versions -> versions.versions)
                .filter(v -> v.id.equals(version))
                .collectList()
                .filter(v -> !v.isEmpty())
                .map(v -> v.get(0))
                .map(v -> v.url)
                .flatMap(url -> HttpClient.create()
                        .get()
                        .uri(url)
                        .responseSingle(($, content) -> content.asString())
                        .map(s -> GSON.fromJson(s, MojangVersionJson.class)))
                .flatMap(v -> downloadClientMappings(v)
                        .map(f -> new MojmapParser(v.id, v.id, f)));
    }
}
