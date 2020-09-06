package me.geek.tom.mcbot.mappings;

@SuppressWarnings("unused")
public class GameVersion {

    public String version;
    public boolean stable;

    @SuppressWarnings("unused")
    public GameVersion() { }
    public GameVersion(String version, boolean stable) {
        this.version = version;
        this.stable = stable;
    }
}
