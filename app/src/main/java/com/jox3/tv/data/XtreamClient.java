package com.jox3.tv.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jox3.tv.model.MediaItem;
import com.jox3.tv.model.PlaylistConfig;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class XtreamClient {

    private final OkHttpClient http;
    private final String baseUrl;
    private final String user;
    private final String pass;

    public XtreamClient(PlaylistConfig config) {
        this.baseUrl = stripTrailingSlash(config.serverUrl);
        this.user = config.username;
        this.pass = config.password;
        this.http = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .build();
    }

    private static String stripTrailingSlash(String url) {
        if (url == null) return "";
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    public boolean testConnection() throws IOException {
        String apiUrl = baseUrl + "/player_api.php?username=" + user + "&password=" + pass;
        Request req = new Request.Builder().url(apiUrl).build();
        try (Response resp = http.newCall(req).execute()) {
            if (!resp.isSuccessful() || resp.body() == null) return false;
            String body = resp.body().string();
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();
            JsonObject userInfo = json.has("user_info") ? json.getAsJsonObject("user_info") : null;
            return userInfo != null && "Active".equalsIgnoreCase(
                    getStringOrNull(userInfo, "status"));
        } catch (Exception e) {
            return false;
        }
    }

    public List<MediaItem> getLiveChannels() throws IOException {
        String url = apiUrl("get_live_streams");
        JsonArray arr = fetchArray(url);
        List<MediaItem> result = new ArrayList<>();
        if (arr == null) return result;

        for (JsonElement el : arr) {
            JsonObject o = el.getAsJsonObject();
            String streamId = getStringOrNull(o, "stream_id");
            if (streamId == null) continue;

            String name = getStringOrNull(o, "name");
            String logo = getStringOrNull(o, "stream_icon");
            String category = getStringOrNull(o, "category_id");

            String playUrl = baseUrl + "/live/" + user + "/" + pass + "/" + streamId + ".m3u8";

            result.add(new MediaItem(streamId, name != null ? name : "Canal " + streamId,
                    logo != null ? logo : "", playUrl,
                    category != null ? category : "General", MediaItem.LIVE));
        }
        return result;
    }

    public List<MediaItem> getMovies() throws IOException {
        String url = apiUrl("get_vod_streams");
        JsonArray arr = fetchArray(url);
        List<MediaItem> result = new ArrayList<>();
        if (arr == null) return result;

        for (JsonElement el : arr) {
            JsonObject o = el.getAsJsonObject();
            String streamId = getStringOrNull(o, "stream_id");
            if (streamId == null) continue;

            String name = getStringOrNull(o, "name");
            String logo = getStringOrNull(o, "stream_icon");
            String category = getStringOrNull(o, "category_id");
            String ext = getStringOrNull(o, "container_extension");
            if (ext == null) ext = "mp4";

            String playUrl = baseUrl + "/movie/" + user + "/" + pass + "/" + streamId + "." + ext;

            result.add(new MediaItem(streamId, name != null ? name : "Película " + streamId,
                    logo != null ? logo : "", playUrl,
                    category != null ? category : "General", MediaItem.VOD));
        }
        return result;
    }

    public List<MediaItem> getSeries() throws IOException {
        String url = apiUrl("get_series");
        JsonArray arr = fetchArray(url);
        List<MediaItem> result = new ArrayList<>();
        if (arr == null) return result;

        for (JsonElement el : arr) {
            JsonObject o = el.getAsJsonObject();
            String seriesId = getStringOrNull(o, "series_id");
            if (seriesId == null) continue;

            String name = getStringOrNull(o, "name");
            String logo = getStringOrNull(o, "cover");
            String category = getStringOrNull(o, "category_id");

            MediaItem item = new MediaItem(seriesId, name != null ? name : "Serie " + seriesId,
                    logo != null ? logo : "", "",
                    category != null ? category : "General", MediaItem.SERIES);
            item.seriesId = seriesId;
            result.add(item);
        }
        return result;
    }

    public List<MediaItem> getSeriesEpisodes(String seriesId) throws IOException {
        String url = baseUrl + "/player_api.php?username=" + user + "&password=" + pass
                + "&action=get_series_info&series_id=" + seriesId;
        Request req = new Request.Builder().url(url).build();
        List<MediaItem> result = new ArrayList<>();

        try (Response resp = http.newCall(req).execute()) {
            if (!resp.isSuccessful() || resp.body() == null) return result;
            String body = resp.body().string();
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();
            if (!json.has("episodes")) return result;

            JsonObject episodesBySeason = json.getAsJsonObject("episodes");
            for (String seasonKey : episodesBySeason.keySet()) {
                JsonArray episodes = episodesBySeason.getAsJsonArray(seasonKey);
                for (JsonElement el : episodes) {
                    JsonObject ep = el.getAsJsonObject();
                    String epId = getStringOrNull(ep, "id");
                    if (epId == null) continue;

                    String title = getStringOrNull(ep, "title");
                    String ext = getStringOrNull(ep, "container_extension");
                    if (ext == null) ext = "mp4";

                    int seasonNum = parseIntSafe(seasonKey);
                    int epNum = ep.has("episode_num") ? ep.get("episode_num").getAsInt() : -1;

                    String playUrl = baseUrl + "/series/" + user + "/" + pass + "/" + epId + "." + ext;

                    MediaItem item = new MediaItem(epId,
                            title != null ? title : "Episodio " + epNum,
                            "", playUrl, "Serie", MediaItem.SERIES);
                    item.seriesId = seriesId;
                    item.season = seasonNum;
                    item.episode = epNum;
                    result.add(item);
                }
            }
        }
        return result;
    }

    private String apiUrl(String action) {
        return baseUrl + "/player_api.php?username=" + user + "&password=" + pass
                + "&action=" + action;
    }

    private JsonArray fetchArray(String url) throws IOException {
        Request req = new Request.Builder().url(url).build();
        try (Response resp = http.newCall(req).execute()) {
            if (!resp.isSuccessful() || resp.body() == null) return null;
            String body = resp.body().string();
            JsonElement parsed = JsonParser.parseString(body);
            return parsed.isJsonArray() ? parsed.getAsJsonArray() : null;
        }
    }

    private static String getStringOrNull(JsonObject o, String key) {
        if (!o.has(key) || o.get(key).isJsonNull()) return null;
        try {
            return o.get(key).getAsString();
        } catch (Exception e) {
            return o.get(key).toString();
        }
    }

    private static int parseIntSafe(String s) {
        try { return Integer.parseInt(s); } catch (Exception e) { return -1; }
    }
}
