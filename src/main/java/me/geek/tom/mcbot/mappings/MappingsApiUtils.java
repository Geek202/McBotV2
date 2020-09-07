package me.geek.tom.mcbot.mappings;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import me.geek.tom.mcbot.mappings.mcp.McpVersion;
import me.geek.tom.mcbot.mappings.yarn.YarnVersion;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class MappingsApiUtils {

    // Mcp stuff
    public static final String MCP_MAPPINGS_URL = "http://export.mcpbot.bspk.rs/versions.json";
    public static final String TEMP_VERSION_JSON = "https://assets.tterrag.com/temp_mappings.json";

    // Yarn stuff
    public static final String YARN_GAME_VERSIONS_URL = "https://meta.fabricmc.net/v1/versions/game/";
    public static final String YARN_MAPPINGS_VERSIONS_URL = "https://meta.fabricmc.net/v1/versions/mappings/";

    private static final Gson GSON = new GsonBuilder().create();

    private Mono<JsonObject> fetchMcpVersions(String url) {
        return HttpClient.create()
                .get()
                .uri(url)
                .responseSingle(($, content) -> content.asString())
                .map(s -> GSON.fromJson(s, JsonObject.class));
    }

    public Mono<List<McpVersion>> getAllMcpVersions() {
        return fetchMcpVersions(MCP_MAPPINGS_URL)
                .zipWith(fetchMcpVersions(TEMP_VERSION_JSON), (a, b) -> {
                    JsonObject ret = a.deepCopy();
                    for (Map.Entry<String, JsonElement> entry : b.entrySet()) {
                        ret.add(entry.getKey(), entry.getValue().deepCopy());
                    }
                    return ret;
                }).flatMapIterable(JsonObject::entrySet)
                .map(o -> new McpVersion(o.getKey(), o.getValue().getAsJsonObject().getAsJsonArray("snapshot")
                                .get(0).getAsString()))
                .collectList();
    }

    public Mono<List<YarnVersion>> fetchYarnVersions(String gameVersion) {
        return HttpClient.create()
                .get()
                .uri(YARN_MAPPINGS_VERSIONS_URL + gameVersion)
                .responseSingle(($, content) -> content.asString())
                .map(s -> GSON.fromJson(s, new TypeToken<List<YarnVersion>>(){}.getType()));
    }

    public Mono<List<GameVersion>> fetchYarnGameVersions() {
        return HttpClient.create()
                .get()
                .uri(YARN_GAME_VERSIONS_URL)
                .responseSingle(($, content) -> content.asString())
                .map(s -> GSON.fromJson(s, new TypeToken<List<GameVersion>>(){}.getType()));
    }

    public Mono<McpVersion> getLatestMcpVersion(String gameVersion) {
        return getAllMcpVersions()
                .flatMapIterable(l -> l)
                .filter(v -> v.gameVersion.equals(gameVersion))
                .collectList()
                .filter(l -> !l.isEmpty())
                .map(l -> l.get(0));
    }

    public Mono<YarnVersion> getLatestYarnVersion(String gameVersion) {
        return fetchYarnVersions(gameVersion)
                .filter(v -> !v.isEmpty())
                .map(v -> v.get(0));
    }

    public Mono<GameVersion> getLatestYarnGameVersion() {
        return fetchYarnGameVersions()
                .filter(v -> !v.isEmpty())
                .map(v -> v.get(0));
    }

    public Mono<GameVersion> getLatestMcpGameVersion() {
        return getAllMcpVersions()
                .filter(v -> !v.isEmpty())
                .flatMapIterable(l -> l)
                .map(v -> new GameVersion(v.gameVersion, true)) // Mcp only targets stable releases
                .collectList()
                .doOnNext(v -> v.sort(Comparator.<GameVersion,GameVersion>comparing(a -> a).reversed()))
                .map(l -> l.get(0));
    }
}
