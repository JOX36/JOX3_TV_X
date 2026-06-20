package com.jox3.tv.model;

import java.io.Serializable;

public class MediaItem implements Serializable {

    public static final String LIVE = "live";
    public static final String VOD = "vod";
    public static final String SERIES = "series";

    public String id;
    public String name;
    public String logoUrl;
    public String url;
    public String category;
    public String type;

    public String seriesId;
    public int season = -1;
    public int episode = -1;

    public MediaItem() { }

    public MediaItem(String id, String name, String logoUrl, String url,
                      String category, String type) {
        this.id = id;
        this.name = name;
        this.logoUrl = logoUrl;
        this.url = url;
        this.category = category;
        this.type = type;
    }

    public String favKey() {
        return type + ":" + id;
    }

    @Override
    public String toString() {
        return name;
    }
}
