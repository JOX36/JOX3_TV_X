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

/**
 * Acceso centralizado a SharedPreferences: favoritos, progreso de VOD,
 * continuar viendo, y las cuentas/listas guardadas (Xtream/M3U).
 *
 * IMPORTANTE: savePlaylistConfig()/getPlaylistConfig()/clearPlaylistConfig()
 * se mantienen con el mismo nombre y comportamiento aparente de "una sola
 * cuenta" para que el resto de la app (HomeActivity, DetailActivity, etc.)
 * no necesite ningún cambio: por debajo, ahora operan sobre la cuenta
 * ACTIVA dentro de una lista de varias cuentas guardadas.
 */
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
    private static final String PREFIX_QUALITY = "quality_";
    private static final String KEY_PARENTAL_PIN = "parental_pin";
    private static final String KEY_ADULT_KEYWORDS = "adult_keywords";
    /** Palabras clave por defecto si JOX3 nunca configuró ninguna. */
    private static final String DEFAULT_ADULT_KEYWORDS = "XXX,ADULTO,ADULTOS,+18,PORN";

    private final SharedPreferences prefs;
    private final Gson gson = new Gson();

    public AppPrefs(Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // ---- Favoritos ----

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

    // ---- Progreso de reproducción (VOD) ----

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

    // ---- Calidad real medida por el reproductor ----

    /**
     * El reproductor mide la resolución REAL del stream mientras se
     * reproduce (vs.width x vs.height vía ExoPlayer) y la guarda aquí.
     * Esto es mucho más confiable que adivinar la calidad buscando
     * palabras como "HD"/"FHD" en el nombre del canal — funciona aunque
     * el nombre no diga nada. Se guarda por itemId, así que la primera
     * vez que se reproduce un canal/película todavía no hay dato (se
     * usa el respaldo por nombre/categoría hasta que se reproduzca al
     * menos una vez).
     */
    public void saveDetectedQuality(String itemId, String quality) {
        if (itemId == null || quality == null) return;
        prefs.edit().putString(PREFIX_QUALITY + itemId, quality).apply();
    }

    /** Calidad real ya medida para este ítem, o null si nunca se ha reproducido. */
    public String getDetectedQuality(String itemId) {
        if (itemId == null) return null;
        return prefs.getString(PREFIX_QUALITY + itemId, null);
    }

    // ---- Control parental ----

    /** true si ya se configuró un PIN (la sección de Ajustes lo muestra distinto). */
    public boolean hasParentalPin() {
        return prefs.getString(KEY_PARENTAL_PIN, null) != null;
    }

    /** Guarda/cambia el PIN. Pasar null o "" para quitar la protección. */
    public void setParentalPin(String pin) {
        if (pin == null || pin.trim().isEmpty()) {
            prefs.edit().remove(KEY_PARENTAL_PIN).apply();
        } else {
            prefs.edit().putString(KEY_PARENTAL_PIN, pin.trim()).apply();
        }
    }

    public boolean checkParentalPin(String attempt) {
        String saved = prefs.getString(KEY_PARENTAL_PIN, null);
        return saved != null && saved.equals(attempt != null ? attempt.trim() : null);
    }

    /**
     * Palabras clave (separadas por coma) que marcan una categoría como
     * contenido para adultos. Configurable desde Ajustes; si nunca se
     * tocó, usa la lista por defecto (XXX, ADULTO, +18, etc.).
     */
    public List<String> getAdultKeywords() {
        String raw = prefs.getString(KEY_ADULT_KEYWORDS, DEFAULT_ADULT_KEYWORDS);
        List<String> result = new ArrayList<>();
        for (String word : raw.split(",")) {
            String trimmed = word.trim().toUpperCase();
            if (!trimmed.isEmpty()) result.add(trimmed);
        }
        return result;
    }

    public void setAdultKeywords(String commaSeparated) {
        prefs.edit().putString(KEY_ADULT_KEYWORDS, commaSeparated).apply();
    }

    /** true si la categoría coincide con alguna palabra clave de adultos. */
    public boolean isAdultCategory(String category) {
        if (category == null) return false;
        String upper = category.toUpperCase();
        for (String keyword : getAdultKeywords()) {
            if (upper.contains(keyword)) return true;
        }
        return false;
    }

    // ---- Sesión de control parental (se olvida al cerrar la app) ----
    // No se guarda en SharedPreferences a propósito: "una vez por sesión"
    // significa que, al cerrar la app por completo, hay que volver a
    // escribir el PIN. Un campo estático en memoria logra exactamente
    // eso sin tocar disco.
    private static boolean adultUnlockedThisSession = false;

    public static boolean isAdultUnlockedThisSession() {
        return adultUnlockedThisSession;
    }

    public static void setAdultUnlockedThisSession(boolean unlocked) {
        adultUnlockedThisSession = unlocked;
    }

    // ---- Continuar viendo ----

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

    /** Limpia todo el historial de "continuar viendo" — se usa al cambiar de
     *  cuenta, para que no queden mezclados canales/películas de la cuenta
     *  anterior (cuyos IDs no existen en la cuenta nueva). */
    public void clearRecentlyWatched() {
        prefs.edit().remove(KEY_CONTINUE_WATCHING).apply();
    }

    public void removeFromContinueWatching(String favKey) {
        List<MediaItem> list = getRecentlyWatched();
        list.removeIf(existing -> existing.favKey().equals(favKey));
        prefs.edit().putString(KEY_CONTINUE_WATCHING, gson.toJson(list)).apply();
    }

    // ---- Cuentas guardadas (multi-cuenta) ----

    /** Lista completa de cuentas guardadas, más reciente primero. */
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

    /** Agrega (o actualiza si ya existe por id) una cuenta y la marca activa. */
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

    /** Devuelve la cuenta actualmente activa, o null si no hay ninguna. */
    public PlaylistConfig getPlaylistConfig() {
        String activeId = getActiveAccountId();
        if (activeId == null) return null;
        for (PlaylistConfig account : getAccounts()) {
            if (account.id.equals(activeId)) return account;
        }
        return null;
    }

    /** Cambia cuál cuenta está activa, sin borrar ninguna. */
    public void setActiveAccountId(String id) {
        prefs.edit().putString(KEY_ACTIVE_ACCOUNT_ID, id).apply();
    }

    public String getActiveAccountId() {
        return prefs.getString(KEY_ACTIVE_ACCOUNT_ID, null);
    }

    /** Elimina una cuenta guardada específica por su id. */
    public void removeAccount(String id) {
        List<PlaylistConfig> accounts = getAccounts();
        accounts.removeIf(existing -> existing.id.equals(id));
        saveAccountsList(accounts);

        if (id.equals(getActiveAccountId())) {
            prefs.edit().remove(KEY_ACTIVE_ACCOUNT_ID).apply();
        }
    }

    /** Compatibilidad: deja de tener cuenta activa (no borra cuentas guardadas). */
    public void clearPlaylistConfig() {
        prefs.edit().remove(KEY_ACTIVE_ACCOUNT_ID).apply();
    }

    // ---- Captura de errores (debug) ----

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
