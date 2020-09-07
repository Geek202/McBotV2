package me.geek.tom.mcbot.storage;

import com.github.steveice10.opennbt.tag.builtin.CompoundTag;
import com.github.steveice10.opennbt.tag.builtin.StringTag;
import me.geek.tom.mcbot.commands.api.CommandManager;
import me.geek.tom.mcbot.storage.api.GuildDataStorage;

public class GeneralGuildSettings extends GuildDataStorage {

    private String commandPrefix;

    public GeneralGuildSettings() {
        this.commandPrefix = CommandManager.DEFAULT_COMMAND_PREFIX;
    }

    public String getCommandPrefix() {
        return commandPrefix;
    }

    public void setCommandPrefix(String commandPrefix) {
        this.commandPrefix = commandPrefix;
    }

    @Override
    public void read(CompoundTag tag) {
        super.read(tag);
        if (tag.contains("CommandPrefix"))
            this.commandPrefix = (String)tag.get("CommandPrefix").getValue();
    }

    @Override
    public void write(CompoundTag tag) {
        super.write(tag);
        tag.put(new StringTag("CommandPrefix", this.commandPrefix));
    }
}
