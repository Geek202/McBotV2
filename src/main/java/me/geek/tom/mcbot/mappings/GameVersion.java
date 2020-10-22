package me.geek.tom.mcbot.mappings;

import com.github.zafarkhaja.semver.Version;
import com.google.common.base.Objects;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public class GameVersion implements Comparable<GameVersion> {

    public String version;
    public boolean stable;

    public GameVersion() { }
    public GameVersion(String version, boolean stable) {
        this.version = version;
        this.stable = stable;
    }

    private int[] splitVersion() {
        return Arrays.stream(this.version.split("\\."))
                .mapToInt(Integer::parseInt)
                .toArray();
    }

    private Version getSemver() {
        if (this.version.split("\\.").length < 3) {
            return Version.valueOf(this.version + ".0");
        }
        return Version.valueOf(this.version);
    }

    @Override
    public int compareTo(@NotNull GameVersion o) {
        if (Objects.equal(this.version, o.version)) return 0;
        return this.getSemver().compareTo(o.getSemver());
    }
}
