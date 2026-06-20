package com.jox3.tv.util;

import com.jox3.tv.model.MediaItem;

import java.util.ArrayList;
import java.util.List;

public class AppState {

    private static AppState instance;

    public List<MediaItem> channelList = new ArrayList<>();
    public int channelIdx = -1;

    public List<MediaItem> episodeQueue = new ArrayList<>();
    public int episodeIdx = -1;

    public List<MediaItem> liveChannels = new ArrayList<>();
    public List<MediaItem> movies = new ArrayList<>();
    public List<MediaItem> series = new ArrayList<>();

    private AppState() { }

    public static synchronized AppState get() {
        if (instance == null) instance = new AppState();
        return instance;
    }
}
