package com.jox3.tv.data;

import com.jox3.tv.model.MediaItem;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class M3uParser {

    private static final Pattern LOGO_PATTERN =
            Pattern.compile("tvg-logo=\"([^\"]*)\"");
    private static final Pattern GROUP_PATTERN =
            Pattern.compile("group-title=\"([^\"]*)\"");

    public static List<MediaItem> parse(InputStream input) throws IOException {
        List<MediaItem> items = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(input, StandardCharsets.UTF_8))) {

            String line;
            String pendingLogo = null;
            String pendingGroup = "Sin categoría";
            String pendingName = null;
            int autoId = 0;

            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                if (line.startsWith("#EXTM3U")) {
                    continue;
                }

                if (line.startsWith("#EXTINF")) {
                    pendingLogo = extract(LOGO_PATTERN, line);
                    pendingGroup = extract(GROUP_PATTERN, line);
                    if (pendingGroup == null) pendingGroup = "Sin categoría";
                    pendingName = extractName(line);
                    continue;
                }

                if (line.startsWith("#")) {
                    continue;
                }

                String url = line;
                String name = (pendingName != null && !pendingName.isEmpty())
                        ? pendingName : "Canal " + (autoId + 1);
                String type = guessType(url);

                MediaItem item = new MediaItem(
                        "m3u_" + (autoId++),
                        name,
                        pendingLogo != null ? pendingLogo : "",
                        url,
                        pendingGroup,
                        type
                );
                items.add(item);

                pendingLogo = null;
                pendingGroup = "Sin categoría";
                pendingName = null;
            }
        }

        return items;
    }

    private static String extract(Pattern pattern, String line) {
        Matcher m = pattern.matcher(line);
        if (m.find()) return m.group(1);
        return null;
    }

    private static String extractName(String line) {
        int idx = line.lastIndexOf(',');
        if (idx >= 0 && idx < line.length() - 1) {
            return line.substring(idx + 1).trim();
        }
        return null;
    }

    private static String guessType(String url) {
        String lower = url.toLowerCase();
        if (lower.contains("/series/") || lower.contains("season") || lower.contains("episode")) {
            return MediaItem.SERIES;
        }
        if (lower.contains("/movie/") || lower.endsWith(".mp4") || lower.endsWith(".mkv")) {
            return MediaItem.VOD;
        }
        return MediaItem.LIVE;
    }
}
