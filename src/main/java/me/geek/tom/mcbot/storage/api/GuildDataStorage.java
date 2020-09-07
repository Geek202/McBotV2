package me.geek.tom.mcbot.storage.api;

import com.github.steveice10.opennbt.tag.builtin.CompoundTag;
import com.github.steveice10.opennbt.tag.builtin.StringTag;

public abstract class GuildDataStorage {

    private String guildId;

    public String getGuildId() {
        return guildId;
    }

    public void setGuildId(String guildId) {
        this.guildId = guildId;
    }

    public void read(CompoundTag tag) {
        this.setGuildId((String) tag.get("GuildId").getValue());
    }

    public void write(CompoundTag tag) {
        tag.put(new StringTag("GuildId", this.getGuildId()));
    }
}
