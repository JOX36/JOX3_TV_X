package com.jox3.tv.ui.settings;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.jox3.tv.R;
import com.jox3.tv.data.M3uParser;
import com.jox3.tv.data.XtreamClient;
import com.jox3.tv.model.MediaItem;
import com.jox3.tv.model.PlaylistConfig;
import com.jox3.tv.util.AppPrefs;
import com.jox3.tv.util.AppState;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SettingsActivity extends AppCompatActivity {

    private RecyclerView accountsRecycler;
    private TextView tvNoAccounts;
    private Button btnToggleAddAccount;
    private LinearLayout addAccountContainer, formXtream, formM3u;
    private Button tabXtream, tabM3u, btnSaveXtream, btnSaveM3u;
    private EditText inputNameXtream, inputServer, inputUser, inputPass;
    private EditText inputNameM3u, inputM3uUrl;
    private ProgressBar progressLoading;
    private TextView tvStatus;
    private TextView tvParentalStatus;
    private Button btnSetParentalPin;

    private AppPrefs prefs;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // Mientras alguna cuenta alterna sigue cargando en segundo plano
    // (AlternateCatalogCache), refrescamos la lista cada 1.5s para que el
    // punto pase de naranja a verde solo, sin que JOX3 tenga que salir y
    // volver a entrar a Ajustes para ver que ya quedó lista.
    private final Runnable statusPollRunnable = this::pollAccountStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        prefs = new AppPrefs(this);

        bindViews();
        setupTabs();
        setupActions();
        showAppVersion();
        refreshAccountsList();
        mainHandler.postDelayed(statusPollRunnable, 1500);
    }

    private void pollAccountStatus() {
        if (isFinishing() || isDestroyed()) return;

        String activeId = prefs.getActiveAccountId();
        boolean anyPending = false;
        for (PlaylistConfig account : prefs.getAccounts()) {
            if (account.id.equals(activeId) || !PlaylistConfig.TYPE_XTREAM.equals(account.type)) continue;
            com.jox3.tv.util.AlternateCatalogCache.AccountCatalog cache =
                    com.jox3.tv.util.AlternateCatalogCache.get().getCatalogFor(account.id);
            if (cache == null || cache.loading) {
                anyPending = true;
                break;
            }
        }

        refreshAccountsList();

        // Se detiene solo cuando ya no queda ninguna cuenta "en camino":
        // loadAccountInBackground siempre termina marcando loaded=true o
        // false en su finally, así que esto nunca queda pegado.
        if (anyPending) {
            mainHandler.postDelayed(statusPollRunnable, 1500);
        }
    }

    private void bindViews() {
        accountsRecycler = findViewById(R.id.accounts_recycler);
        tvNoAccounts = findViewById(R.id.tv_no_accounts);
        btnToggleAddAccount = findViewById(R.id.btn_toggle_add_account);
        addAccountContainer = findViewById(R.id.add_account_container);

        formXtream = findViewById(R.id.form_xtream);
        formM3u = findViewById(R.id.form_m3u);
        tabXtream = findViewById(R.id.tab_xtream);
        tabM3u = findViewById(R.id.tab_m3u);
        btnSaveXtream = findViewById(R.id.btn_save_xtream);
        btnSaveM3u = findViewById(R.id.btn_save_m3u);

        inputNameXtream = findViewById(R.id.input_name_xtream);
        inputServer = findViewById(R.id.input_server);
        inputUser = findViewById(R.id.input_user);
        inputPass = findViewById(R.id.input_pass);
        inputNameM3u = findViewById(R.id.input_name_m3u);
        inputM3uUrl = findViewById(R.id.input_m3u_url);

        progressLoading = findViewById(R.id.progress_loading);
        tvStatus = findViewById(R.id.tv_status);

        tvParentalStatus = findViewById(R.id.tv_parental_status);
        btnSetParentalPin = findViewById(R.id.btn_set_parental_pin);

        accountsRecycler.setLayoutManager(new LinearLayoutManager(this));
    }

    private void setupTabs() {
        tabXtream.setOnClickListener(v -> showTab(true));
        tabM3u.setOnClickListener(v -> showTab(false));
    }

    private void showTab(boolean xtream) {
        formXtream.setVisibility(xtream ? View.VISIBLE : View.GONE);
        formM3u.setVisibility(xtream ? View.GONE : View.VISIBLE);
        tabXtream.setBackgroundTintList(getColorStateList(
                xtream ? R.color.accent : R.color.bg_secondary));
        tabM3u.setBackgroundTintList(getColorStateList(
                xtream ? R.color.bg_secondary : R.color.accent));
    }

    private void setupActions() {
        btnSaveXtream.setOnClickListener(v -> onSaveXtream());
        btnSaveM3u.setOnClickListener(v -> onSaveM3u());
        btnToggleAddAccount.setOnClickListener(v -> toggleAddAccountForm());
        btnSetParentalPin.setOnClickListener(v -> showSetPinDialog());
        updateParentalStatus();
    }

    private void updateParentalStatus() {
        boolean hasPin = prefs.hasParentalPin();
        tvParentalStatus.setText(hasPin
                ? "✓ PIN configurado — las categorías de adultos están protegidas"
                : "Sin PIN configurado — las categorías de adultos NO están protegidas todavía");
        btnSetParentalPin.setText(hasPin ? "Cambiar o quitar PIN" : "Configurar PIN");
    }

    private void showSetPinDialog() {
        EditText input = new EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
                | android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        input.setHint("Nuevo PIN (déjalo vacío para quitar protección)");

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Control parental")
                .setMessage("Define un PIN de 4 dígitos o más. Se pedirá una vez por sesión al entrar a categorías de contenido para adultos.")
                .setView(input)
                .setPositiveButton("Guardar", (dialog, which) -> {
                    String pin = input.getText().toString().trim();
                    prefs.setParentalPin(pin.isEmpty() ? null : pin);
                    com.jox3.tv.util.AppPrefs.setAdultUnlockedThisSession(false);
                    Toast.makeText(this,
                            pin.isEmpty() ? "Protección de control parental desactivada" : "PIN guardado",
                            Toast.LENGTH_SHORT).show();
                    updateParentalStatus();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void toggleAddAccountForm() {
        boolean isVisible = addAccountContainer.getVisibility() == View.VISIBLE;
        addAccountContainer.setVisibility(isVisible ? View.GONE : View.VISIBLE);
        btnToggleAddAccount.setText(isVisible ? "+  Agregar cuenta nueva" : "Cancelar");
    }

    private void showAppVersion() {
        TextView tvVersion = findViewById(R.id.tv_app_version);
        if (tvVersion == null) return;
        try {
            String versionName = getPackageManager()
                    .getPackageInfo(getPackageName(), 0).versionName;
            tvVersion.setText("JOX3 TV · versión " + versionName);
        } catch (Exception e) {
            tvVersion.setText("JOX3 TV");
        }
    }

    // ---------------- Lista de cuentas ----------------

    private void refreshAccountsList() {
        List<PlaylistConfig> accounts = prefs.getAccounts();
        String activeId = prefs.getActiveAccountId();

        boolean hasAccounts = !accounts.isEmpty();
        accountsRecycler.setVisibility(hasAccounts ? View.VISIBLE : View.GONE);
        tvNoAccounts.setVisibility(hasAccounts ? View.GONE : View.VISIBLE);

        accountsRecycler.setAdapter(new AccountListAdapter(accounts, activeId,
                new AccountListAdapter.OnAccountAction() {
                    @Override public void onSelect(PlaylistConfig account) {
                        switchToAccount(account);
                    }
                    @Override public void onDelete(PlaylistConfig account) {
                        confirmDeleteAccount(account);
                    }
                }));

        // Si no hay ninguna cuenta todavía, dejamos el formulario abierto
        // de entrada para que sea obvio qué hacer.
        if (!hasAccounts) {
            addAccountContainer.setVisibility(View.VISIBLE);
            btnToggleAddAccount.setText("Cancelar");
        }
    }

    private void confirmDeleteAccount(PlaylistConfig account) {
        new AlertDialog.Builder(this)
                .setTitle("Eliminar cuenta")
                .setMessage("¿Eliminar \"" + account.name + "\"? Esto no se puede deshacer.")
                .setPositiveButton("Eliminar", (d, w) -> {
                    boolean wasActive = account.id.equals(prefs.getActiveAccountId());
                    prefs.removeAccount(account.id);
                    if (wasActive) {
                        AppState state = AppState.get();
                        state.liveChannels.clear();
                        state.movies.clear();
                        state.series.clear();
                        prefs.clearRecentlyWatched();
                    }
                    com.jox3.tv.util.AlternateCatalogCache.get().refresh(this);
                    refreshAccountsList();
                    Toast.makeText(this, "Cuenta eliminada", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    /**
     * Cambia a una cuenta ya guardada. Antes esto SIEMPRE volvía a pedirle
     * todo el catálogo al servidor (testConnection + 3 descargas), aunque
     * esa cuenta ya estuviera precargada en segundo plano por
     * AlternateCatalogCache para el buscador global. Ahora:
     *
     *   - Si es una cuenta Xtream y su catálogo ya está listo en caché ->
     *     cambio INSTANTÁNEO, sin tocar la red, solo tocar y listo.
     *   - Si no hay nada en caché todavía (recién agregada, M3U, o su
     *     carga en 2do plano sigue en curso) -> cae al camino de siempre,
     *     que sí pide los datos, para no dejar la app sin catálogo.
     */
    private void switchToAccount(PlaylistConfig account) {
        if (account.id.equals(prefs.getActiveAccountId())) {
            finish();
            return;
        }

        com.jox3.tv.util.AlternateCatalogCache.AccountCatalog cached =
                com.jox3.tv.util.AlternateCatalogCache.get().getCatalogFor(account.id);

        boolean readyFromCache = PlaylistConfig.TYPE_XTREAM.equals(account.type)
                && cached != null && cached.loaded;

        if (readyFromCache) {
            applyAccountFromCache(account, cached);
        } else {
            boolean stillLoadingInBackground = cached != null && cached.loading;
            switchToAccountFromNetwork(account, stillLoadingInBackground);
        }
    }

    /** Camino rápido: copia lo que AlternateCatalogCache ya descargó. */
    private void applyAccountFromCache(PlaylistConfig account,
                                        com.jox3.tv.util.AlternateCatalogCache.AccountCatalog cached) {
        AppState state = AppState.get();
        state.liveChannels = new java.util.ArrayList<>(cached.liveChannels);
        state.movies = new java.util.ArrayList<>(cached.movies);
        state.series = new java.util.ArrayList<>(cached.series);

        prefs.setActiveAccountId(account.id);
        prefs.clearRecentlyWatched();
        // La cuenta que era "principal" hasta ahora pasa a ser una alterna
        // más, y la nueva activa sale de la lista de alternas — hay que
        // recalcular la caché del buscador global para que refleje esto.
        com.jox3.tv.util.AlternateCatalogCache.get().refresh(this);

        Toast.makeText(this, "Cuenta cambiada: " + account.name, Toast.LENGTH_SHORT).show();
        finish();
    }

    /** Camino anterior: solo se usa cuando todavía no hay nada en caché. */
    private void switchToAccountFromNetwork(PlaylistConfig account, boolean stillLoadingInBackground) {
        setLoading(true, stillLoadingInBackground
                ? "Esa cuenta aún se está cargando en segundo plano, esperando..."
                : "Cambiando a \"" + account.name + "\"...");

        executor.execute(() -> {
            try {
                if (PlaylistConfig.TYPE_XTREAM.equals(account.type)) {
                    XtreamClient client = new XtreamClient(account);
                    boolean ok = client.testConnection();
                    if (!ok) {
                        postStatus("No se pudo conectar con esa cuenta.", false);
                        return;
                    }
                    List<MediaItem> live = client.getLiveChannels();
                    List<MediaItem> movies = client.getMovies();
                    List<MediaItem> series = client.getSeries();

                    AppState state = AppState.get();
                    state.liveChannels = live;
                    state.movies = movies;
                    state.series = series;

                } else {
                    List<MediaItem> all = downloadAndParseM3u(account.m3uUrl);
                    AppState state = AppState.get();
                    state.liveChannels.clear();
                    state.movies.clear();
                    state.series.clear();
                    for (MediaItem item : all) {
                        if (MediaItem.VOD.equals(item.type)) state.movies.add(item);
                        else if (MediaItem.SERIES.equals(item.type)) state.series.add(item);
                        else state.liveChannels.add(item);
                    }
                }

                prefs.setActiveAccountId(account.id);
                prefs.clearRecentlyWatched();
                com.jox3.tv.util.AlternateCatalogCache.get().refresh(this);

                mainHandler.post(() -> {
                    Toast.makeText(this, "Cuenta cambiada: " + account.name, Toast.LENGTH_SHORT).show();
                    finish();
                });

            } catch (Exception e) {
                postStatus("Error al cambiar de cuenta: " + e.getMessage(), false);
            }
        });
    }

    // ---------------- Agregar cuenta: XTREAM ----------------

    private void onSaveXtream() {
        String name = textOf(inputNameXtream, "Mi Lista Privada");
        String server = textOf(inputServer, "");
        String user = textOf(inputUser, "");
        String pass = textOf(inputPass, "");

        if (server.isEmpty() || user.isEmpty()) {
            setStatus("Completa al menos el servidor y el usuario.");
            return;
        }

        PlaylistConfig config = new PlaylistConfig();
        config.name = name;
        config.type = PlaylistConfig.TYPE_XTREAM;
        config.serverUrl = server;
        config.username = user;
        config.password = pass;

        setLoading(true, "Conectando con el servidor...");

        executor.execute(() -> {
            try {
                XtreamClient client = new XtreamClient(config);
                boolean ok = client.testConnection();
                if (!ok) {
                    postStatus("No se pudo validar la conexión. Revisa los datos.", false);
                    return;
                }

                postStatus("Conectado. Descargando canales...", false);
                List<MediaItem> live = client.getLiveChannels();
                List<MediaItem> movies = client.getMovies();
                List<MediaItem> series = client.getSeries();

                long[] dates = client.getAccountDates();
                config.createdAtEpoch = dates[0];
                config.expDateEpoch = dates[1];

                AppState state = AppState.get();
                state.liveChannels = live;
                state.movies = movies;
                state.series = series;

                prefs.savePlaylistConfig(config);
                prefs.clearRecentlyWatched();
                com.jox3.tv.util.AlternateCatalogCache.get().refresh(this);

                mainHandler.post(() -> {
                    Toast.makeText(this, "Cuenta guardada: " + live.size() + " canales, "
                            + movies.size() + " películas, " + series.size() + " series",
                            Toast.LENGTH_SHORT).show();
                    finish();
                });

            } catch (IOException e) {
                postStatus("Error de red: " + e.getMessage(), false);
            } catch (Exception e) {
                postStatus("Error inesperado: " + e.getMessage(), false);
            }
        });
    }

    // ---------------- Agregar cuenta: M3U ----------------

    private void onSaveM3u() {
        String name = textOf(inputNameM3u, "Mi Lista M3U");
        String url = textOf(inputM3uUrl, "");

        if (url.isEmpty()) {
            setStatus("Ingresa la URL de tu lista M3U.");
            return;
        }

        PlaylistConfig config = new PlaylistConfig();
        config.name = name;
        config.type = PlaylistConfig.TYPE_M3U;
        config.m3uUrl = url;

        setLoading(true, "Descargando lista M3U...");

        executor.execute(() -> {
            try {
                List<MediaItem> all = downloadAndParseM3u(url);

                AppState state = AppState.get();
                state.liveChannels.clear();
                state.movies.clear();
                state.series.clear();
                for (MediaItem item : all) {
                    if (MediaItem.VOD.equals(item.type)) state.movies.add(item);
                    else if (MediaItem.SERIES.equals(item.type)) state.series.add(item);
                    else state.liveChannels.add(item);
                }

                prefs.savePlaylistConfig(config);
                prefs.clearRecentlyWatched();
                com.jox3.tv.util.AlternateCatalogCache.get().refresh(this);

                mainHandler.post(() -> {
                    Toast.makeText(this, "Cuenta guardada: " + all.size() + " elementos en total",
                            Toast.LENGTH_SHORT).show();
                    finish();
                });

            } catch (IOException e) {
                postStatus("Error descargando la lista: " + e.getMessage(), false);
            } catch (Exception e) {
                postStatus("Error inesperado: " + e.getMessage(), false);
            }
        });
    }

    private List<MediaItem> downloadAndParseM3u(String urlStr) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(20000);
        conn.setRequestMethod("GET");
        try (InputStream input = conn.getInputStream()) {
            return M3uParser.parse(input);
        } finally {
            conn.disconnect();
        }
    }

    // ---------------- Helpers de UI ----------------

    private void clearForm() {
        inputNameXtream.setText("");
        inputServer.setText("");
        inputUser.setText("");
        inputPass.setText("");
        inputNameM3u.setText("");
        inputM3uUrl.setText("");
    }

    private void setLoading(boolean loading, String status) {
        mainHandler.post(() -> {
            progressLoading.setVisibility(loading ? View.VISIBLE : View.GONE);
            tvStatus.setText(status);
            btnSaveXtream.setEnabled(!loading);
            btnSaveM3u.setEnabled(!loading);
        });
    }

    private void postStatus(String message, boolean keepLoading) {
        mainHandler.post(() -> {
            progressLoading.setVisibility(keepLoading ? View.VISIBLE : View.GONE);
            tvStatus.setText(message);
            btnSaveXtream.setEnabled(true);
            btnSaveM3u.setEnabled(true);
        });
    }

    private void setStatus(String message) {
        tvStatus.setText(message);
    }

    private String textOf(EditText editText, String fallback) {
        String t = editText.getText() != null ? editText.getText().toString().trim() : "";
        return t.isEmpty() ? fallback : t;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mainHandler.removeCallbacks(statusPollRunnable);
        executor.shutdownNow();
    }
}
