package com.jox3.tv.util;

import android.content.Context;

import com.jox3.tv.data.XtreamClient;
import com.jox3.tv.model.MediaItem;
import com.jox3.tv.model.PlaylistConfig;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Catálogos de las cuentas ALTERNAS (todas las guardadas excepto la
 * activa), cargados en segundo plano para que el buscador global pueda
 * encontrar contenido que la cuenta principal no tenga.
 *
 * La cuenta ACTIVA sigue funcionando exactamente igual que siempre: sigue
 * siendo AppState la que la alimenta, y todo el resto de la app (Home,
 * reproductor, favoritos) no se entera de que esta clase existe. Esta
 * caché es de solo lectura para el buscador, nunca se usa para reproducir
 * directamente "como si fuera la cuenta activa".
 *
 * Límite: máximo 5 cuentas alternas en memoria a la vez (definido por
 * JOX3), para no disparar el consumo de datos/batería si hay muchas
 * cuentas guardadas.
 */
public class AlternateCatalogCache {

    private static final int MAX_ALTERNATE_ACCOUNTS = 5;

    private static AlternateCatalogCache instance;

    public static synchronized AlternateCatalogCache get() {
        if (instance == null) instance = new AlternateCatalogCache();
        return instance;
    }

    /** Catálogo de UNA cuenta alterna: su nombre + sus 3 listas. */
    public static class AccountCatalog {
        public final String accountId;
        public final String accountName;
        public List<MediaItem> liveChannels = new ArrayList<>();
        public List<MediaItem> movies = new ArrayList<>();
        public List<MediaItem> series = new ArrayList<>();
        public boolean loaded = false;
        public boolean loading = false;

        AccountCatalog(String accountId, String accountName) {
            this.accountId = accountId;
            this.accountName = accountName;
        }
    }

    // accountId -> su catálogo. LinkedHashMap para mantener el orden de
    // las cuentas tal como están guardadas (más reciente primero).
    private final Map<String, AccountCatalog> catalogs = new LinkedHashMap<>();
    private final ExecutorService executor = Executors.newFixedThreadPool(3);
    private boolean startedOnce = false;

    private AlternateCatalogCache() { }

    /**
     * Dispara la carga en segundo plano de hasta 5 cuentas alternas (todas
     * las guardadas EXCEPTO la activa). Seguro de llamar varias veces: si
     * ya se inició una vez en esta sesión de la app, no vuelve a repetir
     * el trabajo a menos que se llame refresh() explícitamente (por
     * ejemplo, después de agregar una cuenta nueva en Ajustes).
     */
    public synchronized void startBackgroundLoadIfNeeded(Context context) {
        if (startedOnce) return;
        startedOnce = true;
        refresh(context);
    }

    /** Fuerza una recarga completa (ej. después de agregar/quitar una cuenta). */
    public synchronized void refresh(Context context) {
        Context appContext = context.getApplicationContext();
        AppPrefs prefs = new AppPrefs(appContext);
        String activeId = prefs.getActiveAccountId();

        catalogs.clear();

        int count = 0;
        for (PlaylistConfig account : prefs.getAccounts()) {
            if (account.id == null || account.id.equals(activeId)) continue;
            if (count >= MAX_ALTERNATE_ACCOUNTS) break;
            count++;

            AccountCatalog catalog = new AccountCatalog(account.id,
                    account.name != null && !account.name.isEmpty() ? account.name : "Cuenta sin nombre");
            catalogs.put(account.id, catalog);
            loadAccountInBackground(account, catalog);
        }
    }

    private void loadAccountInBackground(PlaylistConfig account, AccountCatalog catalog) {
        catalog.loading = true;
        executor.execute(() -> {
            try {
                if (PlaylistConfig.TYPE_XTREAM.equals(account.type)) {
                    XtreamClient client = new XtreamClient(account);
                    catalog.liveChannels = client.getLiveChannels();
                    catalog.movies = client.getMovies();
                    catalog.series = client.getSeries();
                    // Cada ítem queda "marcado" con la cuenta de la que
                    // vino. Así, si después se abre desde el buscador
                    // global, DetailActivity y PlayerActivity saben a qué
                    // servidor preguntarle por episodios/EPG en vez de
                    // asumir siempre la cuenta activa.
                    stampSourceAccount(catalog.liveChannels, account.id);
                    stampSourceAccount(catalog.movies, account.id);
                    stampSourceAccount(catalog.series, account.id);
                }
                // Cuentas M3U: por ahora no se cargan en segundo plano. El
                // parser M3U trabaja distinto (no es una API por demanda
                // como Xtream) y agregarlo aquí sin verlo de cerca podía
                // duplicar lógica ya existente en otra parte de la app.
                // Esta cuenta simplemente no participará del buscador
                // global por ahora; el resto de la app no se ve afectada.
                catalog.loaded = true;
            } catch (Exception e) {
                // Si una cuenta alterna falla (servidor caído, credenciales
                // vencidas, etc.) simplemente queda vacía — no debe tumbar
                // la carga de las demás cuentas ni de la cuenta principal.
                catalog.loaded = false;
            } finally {
                catalog.loading = false;
            }
        });
    }

    private static void stampSourceAccount(List<MediaItem> items, String accountId) {
        for (MediaItem item : items) item.sourceAccountId = accountId;
    }

    /** Catálogos de todas las cuentas alternas actualmente en caché. */
    public synchronized List<AccountCatalog> getAllCatalogs() {
        return new ArrayList<>(catalogs.values());
    }

    /**
     * Catálogo de UNA cuenta alterna puntual, o null si esa cuenta no está
     * en caché todavía (por ejemplo, recién agregada antes de que corra
     * refresh(), o es una cuenta M3U que esta clase no cachea).
     *
     * Se agrega para que Ajustes pueda cambiar de cuenta al instante
     * usando estos datos ya descargados, en vez de volver a pedirle todo
     * el catálogo al servidor Xtream y hacer esperar a JOX3 cada vez que
     * alterna listas.
     */
    public synchronized AccountCatalog getCatalogFor(String accountId) {
        if (accountId == null) return null;
        return catalogs.get(accountId);
    }
}
