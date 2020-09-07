package me.geek.tom.mcbot.storage.api;

import com.github.steveice10.opennbt.NBTIO;
import com.github.steveice10.opennbt.tag.builtin.CompoundTag;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class GuildStorageManager<T extends GuildDataStorage> {

    private static final Logger LOGGER = LoggerFactory.getLogger(GuildStorageManager.class);

    @Nonnull
    private final File dataFolder;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

    private final Map<String, T> data = new HashMap<>();
    private final List<String> dirty = new ArrayList<>();

    private final Supplier<T> constructor;

    private ScheduledFuture<?> future;

    public GuildStorageManager(@NotNull File dataFolder, Supplier<T> constructor) {
        this.dataFolder = dataFolder;
        this.constructor = constructor;
    }

    public void start() {
        if (!dataFolder.exists() && !dataFolder.mkdirs())
            LOGGER.warn("Failed to create data directory: " + dataFolder.getAbsolutePath());
        for (File file : Objects.requireNonNull(dataFolder.listFiles(), "data files list")) {
            if (file.isDirectory()) continue;
            if (!file.getName().endsWith(".nbt")) return;
            try {
                CompoundTag tag = NBTIO.readFile(file);
                T data = constructor.get();
                data.read(tag);
                this.data.put(data.getGuildId(), data);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        future = executor.scheduleAtFixedRate(this::save, 30, 30, TimeUnit.SECONDS);
    }

    public void stop() {
        if (future != null) {
            future.cancel(false);
        }
        if (!executor.isShutdown()) {
            executor.shutdown();
        }
        this.save();
    }

    private void markDirty(String guildId) {
        if (!dirty.contains(guildId))
            dirty.add(guildId);
    }

    public void modify(String guildId, Consumer<T> onSuccess) {
        if (data.containsKey(guildId)) {
            onSuccess.accept(data.get(guildId));
        } else {
            T storage = constructor.get();
            storage.setGuildId(guildId);
            data.put(guildId, storage);
            onSuccess.accept(storage);
        }
        markDirty(guildId);
    }

    private void save() {
        for (String guild : dirty) {
            saveGuild(guild);
        }
        dirty.clear();
    }

    private void saveGuild(String guild) {
        File saveFile = new File(dataFolder, guild + ".nbt");
        if (data.containsKey(guild)) {
            CompoundTag save = new CompoundTag("");
            data.get(guild).write(save);
            try {
                NBTIO.writeFile(save, saveFile);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else if (saveFile.exists()) {
            try {
                Files.delete(saveFile.toPath());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public T read(String id) {
        return data.computeIfAbsent(id, s -> {
            T storage = constructor.get();
            storage.setGuildId(id);
            data.put(id, storage);
            markDirty(id);
            return storage;
        });
    }
}
