package com.jox3.tv.model;

import java.io.Serializable;

public class PlaylistConfig implements Serializable {

    public static final String TYPE_XTREAM = "xtream";
    public static final String TYPE_M3U = "m3u";

    public String name;
    public String type;

    public String serverUrl;
    public String username;
    public String password;

    public String m3uUrl;
    public String m3uLocalPath;

    public long createdAtEpoch = -1;
    public long expDateEpoch = -1;

    public boolean isEmpty() {
        if (TYPE_XTREAM.equals(type)) {
            return serverUrl == null || serverUrl.trim().isEmpty()
                    || username == null || username.trim().isEmpty();
        }
        if (TYPE_M3U.equals(type)) {
            return (m3uUrl == null || m3uUrl.trim().isEmpty())
                    && (m3uLocalPath == null || m3uLocalPath.trim().isEmpty());
        }
        return true;
    }
}
