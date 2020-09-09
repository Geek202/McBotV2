package me.geek.tom.mcbot.mappings.mojmap;

import java.util.List;

public class MojangVersionsFile {

    public LatestReleases latest;
    public List<MojangGameVersion> versions;

    public static class LatestReleases {
        public String release;
        public String snapshot;
    }

    public static class MojangGameVersion {
        public String id;
        public String type;
        public String url;
        public String time;
        public String releaseTime;
    }

}
