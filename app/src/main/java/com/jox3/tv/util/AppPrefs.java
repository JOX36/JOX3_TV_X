package com.jox3.tv.util;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.jox3.tv.model.MediaItem;
import com.jox3.tv.model.PlaylistConfig;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AppPrefs {

    private static final String PREFS_NAME = "jox3tv_prefs";
    private static final String KEY_FAVORITES = "favorites";
    private static final String KEY_ACCOUNTS = "saved_accounts";
    private static final String KEY_ACTIVE_ACCOUNT_ID = "active_account_id";
    private static final String KEY_CONTINUE_WATCHING = "continue_watching";
    private static final int MAX_CONTINUE_WATCHING = 12;
    private static final String PREFIX_POS = "pos_";
    private static final String PREFIX_DUR = "dur_";
    private static final String KEY_CRASH_LOG = "last_crash_log";

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

    public void addRecentlyWatched(MediaItem item) {
        if (item == null) return;
        List<MediaItem> list = getRecentlyWatched();

        list.removeIf(existing -> existing.favKey().equals(item.favKey()));
        list.add(0, item);

        while (list.size() > MAX_CONTINUE_WATCHING) {
            list.remove(list.size() - 1);
        }

        prefs.edit().putString(KEY_CONTINUE_WATCHING, gson.toJson(list)).apply();
    }

    public List<MediaItem> getRecentlyWatched() {
        String json = prefs.getString(KEY_CONTINUE_WATCHING, null);
        if (json == null) return new ArrayList<>();
        try {
            Type listType = new TypeToken<ArrayList<MediaItem>>(){}.getType();
            List<MediaItem> list = gson.fromJson(json, listType);
            return list != null ? list : new ArrayList<>();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public void removeFromContinueWatching(String favKey) {
        List<MediaItem> list = getRecentlyWatched();
        list.removeIf(existing -> existing.favKey().equals(favKey));
        prefs.edit().putString(KEY_CONTINUE_WATCHING, gson.toJson(list)).apply();
    }

    public List<PlaylistConfig> getAccounts() {
        String json = prefs.getString(KEY_ACCOUNTS, null);
        if (json == null) return new ArrayList<>();
        try {
            Type listType = new TypeToken<ArrayList<PlaylistConfig>>(){}.getType();
            List<PlaylistConfig> list = gson.fromJson(json, listType);
            return list != null ? list : new ArrayList<>();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private void saveAccountsList(List<PlaylistConfig> accounts) {
        prefs.edit().putString(KEY_ACCOUNTS, gson.toJson(accounts)).apply();
    }

    public void savePlaylistConfig(PlaylistConfig config) {
        if (config.id == null || config.id.isEmpty()) {
            config.id = java.util.UUID.randomUUID().toString();
        }
        List<PlaylistConfig> accounts = getAccounts();
        accounts.removeIf(existing -> existing.id.equals(config.id));
        accounts.add(0, config);
        saveAccountsList(accounts);
        setActiveAccountId(config.id);
    }

    public PlaylistConfig getPlaylistConfig() {
        String activeId = getActiveAccountId();
        if (activeId == null) return null;
        for (PlaylistConfig account : getAccounts()) {
            if (account.id.equals(activeId)) return account;
        }
        return null;
    }

    public void setActiveAccountId(String id) {
        prefs.edit().putString(KEY_ACTIVE_ACCOUNT_ID, id).apply();
    }

    public String getActiveAccountId() {
        return prefs.getString(KEY_ACTIVE_ACCOUNT_ID, null);
    }

    public void removeAccount(String id) {
        List<PlaylistConfig> accounts = getAccounts();
        accounts.removeIf(existing -> existing.id.equals(id));
        saveAccountsList(accounts);

        if (id.equals(getActiveAccountId())) {
            prefs.edit().remove(KEY_ACTIVE_ACCOUNT_ID).apply();
        }
    }

    public void clearPlaylistConfig() {
        prefs.edit().remove(KEY_ACTIVE_ACCOUNT_ID).apply();
    }

    public void saveCrashLog(String stackTrace) {
        prefs.edit().putString(KEY_CRASH_LOG, stackTrace).apply();
    }

    public String getCrashLog() {
        return prefs.getString(KEY_CRASH_LOG, null);
    }

    public void clearCrashLog() {
        prefs.edit().remove(KEY_CRASH_LOG).apply();
    }
}
