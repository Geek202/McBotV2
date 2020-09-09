package me.geek.tom.mcbot.mappings.mojmap;

import com.google.gson.annotations.SerializedName;

public class MojangVersionJson {

    public Downloads downloads;
    public String id;

    public static class Downloads {
        public Download client;
        @SerializedName("client_mappings")
        public Download cMappings;
        public Download server;
        @SerializedName("server_mappings")
        public Download sMappings;
    }

    public static class Download {
        public String sha1;
        public Long size;
        public String url;
    }

}
