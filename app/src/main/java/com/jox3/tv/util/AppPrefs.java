package com.jox3.tv.util;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.jox3.tv.model.PlaylistConfig;

import java.util.HashSet;
import java.util.Set;

public class AppPrefs {

    private static final String PREFS_NAME = "jox3tv_prefs";
    private static final String KEY_FAVORITES = "favorites";
    private static final String KEY_PLAYLIST_CONFIG = "playlist_config";
    private static final String PREFIX_POS = "pos_";
    private static final String PREFIX_DUR = "dur_";

    private final SharedPreferences prefs;
    private final Gson gson = new Gson();

    public AppPrefs(Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public boolean isFav(String favKey) {
        return getFavorites().contains(favKey);
    }

    public void toggleFav(String favKey) {
        Set<String> favs = new HashSet<>(getFavorites());
        if (favs.contains(favKey)) favs.remove(favKey);
        else favs.add(favKey);
        prefs.edit().putStringSet(KEY_FAVORITES, favs).apply();
    }

    public Set<String> getFavorites() {
        return prefs.getStringSet(KEY_FAVORITES, new HashSet<>());
    }

    public void saveProgress(String itemId, long positionMs, long durationMs) {
        prefs.edit()
                .putLong(PREFIX_POS + itemId, positionMs)
                .putLong(PREFIX_DUR + itemId, durationMs)
                .apply();
    }

    public long getPos(String itemId) {
        return prefs.getLong(PREFIX_POS + itemId, 0);
    }

    public long getDur(String itemId) {
        return prefs.getLong(PREFIX_DUR + itemId, 0);
    }

    public void savePlaylistConfig(PlaylistConfig config) {
        prefs.edit().putString(KEY_PLAYLIST_CONFIG, gson.toJson(config)).apply();
    }

    public PlaylistConfig getPlaylistConfig() {
        String json = prefs.getString(KEY_PLAYLIST_CONFIG, null);
        if (json == null) return null;
        try {
            return gson.fromJson(json, PlaylistConfig.class);
        } catch (Exception e) {
            return null;
        }
    }

    public void clearPlaylistConfig() {
        prefs.edit().remove(KEY_PLAYLIST_CONFIG).apply();
    }
}
