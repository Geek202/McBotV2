package me.geek.tom.mcbot.mappings;

import me.geek.tom.mcbot.McBot;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.concurrent.*;

public abstract class MappingsDatabase<T extends MappingsParser> {

    private static final ScheduledExecutorService UPDATE_EXECUTOR = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = Executors.defaultThreadFactory().newThread(r);
        t.setName("Mappings-Update-Worker");
        return t;
    });

    private final McBot mcBot;
    private final Map<String, T> versions;
    private static final List<ScheduledFuture<?>> updaters = new ArrayList<>();

    public MappingsDatabase(McBot mcBot) {
        this.mcBot = mcBot;
        versions = Collections.synchronizedMap(new HashMap<>());
        updaters.add(UPDATE_EXECUTOR.scheduleAtFixedRate(
                () -> updateMappings().block(),
                12, 12, TimeUnit.HOURS));
    }

    public static void shutdown() {
        updaters.forEach(f -> f.cancel(false));
        UPDATE_EXECUTOR.shutdown();
    }

    public Mono<Void> updateMappings() {
        return getAvailableVersions()
                .flatMapIterable(v -> v)
                .filter(v -> versions.containsKey(v.version))
                .flatMap(v -> updateMappings(v.version))
                .collectList().then();
    }

    protected T updateParser(T parser) {
        this.versions.put(parser.getGameVersion(), parser);
        return parser;
    }

    public McBot getMcBot() {
        return mcBot;
    }

    protected Map<String, T> getParsers() {
        return versions;
    }

    public abstract Mono<List<GameVersion>> getAvailableVersions();

    public abstract Mono<T> updateMappings(String version);
}
