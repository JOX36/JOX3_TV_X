package com.jox3.tv.ui.settings;

import android.content.Intent;
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

import androidx.appcompat.app.AppCompatActivity;

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

    private LinearLayout formXtream, formM3u, cardActiveList;
    private Button tabXtream, tabM3u, btnSaveXtream, btnSaveM3u, btnDeleteList;
    private EditText inputNameXtream, inputServer, inputUser, inputPass;
    private EditText inputNameM3u, inputM3uUrl;
    private ProgressBar progressLoading;
    private TextView tvStatus, tvActiveName, tvActiveMeta;

    private AppPrefs prefs;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        prefs = new AppPrefs(this);

        bindViews();
        setupTabs();
        setupActions();
        loadExistingConfig();
    }

    private void bindViews() {
        formXtream = findViewById(R.id.form_xtream);
        formM3u = findViewById(R.id.form_m3u);
        cardActiveList = findViewById(R.id.card_active_list);

        tabXtream = findViewById(R.id.tab_xtream);
        tabM3u = findViewById(R.id.tab_m3u);
        btnSaveXtream = findViewById(R.id.btn_save_xtream);
        btnSaveM3u = findViewById(R.id.btn_save_m3u);
        btnDeleteList = findViewById(R.id.btn_delete_list);

        inputNameXtream = findViewById(R.id.input_name_xtream);
        inputServer = findViewById(R.id.input_server);
        inputUser = findViewById(R.id.input_user);
        inputPass = findViewById(R.id.input_pass);

        inputNameM3u = findViewById(R.id.input_name_m3u);
        inputM3uUrl = findViewById(R.id.input_m3u_url);

        progressLoading = findViewById(R.id.progress_loading);
        tvStatus = findViewById(R.id.tv_status);
        tvActiveName = findViewById(R.id.tv_active_name);
        tvActiveMeta = findViewById(R.id.tv_active_meta);
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
        btnDeleteList.setOnClickListener(v -> onDeleteList());
    }

    private void loadExistingConfig() {
        PlaylistConfig config = prefs.getPlaylistConfig();
        if (config == null || config.isEmpty()) {
            cardActiveList.setVisibility(View.GONE);
            return;
        }

        if (PlaylistConfig.TYPE_XTREAM.equals(config.type)) {
            inputNameXtream.setText(config.name);
            inputServer.setText(config.serverUrl);
            inputUser.setText(config.username);
            inputPass.setText(config.password);
            showTab(true);
        } else {
            inputNameM3u.setText(config.name);
            inputM3uUrl.setText(config.m3uUrl);
            showTab(false);
        }

        showActiveListCard(config);
    }

    private void showActiveListCard(PlaylistConfig config) {
        AppState state = AppState.get();
        cardActiveList.setVisibility(View.VISIBLE);
        tvActiveName.setText("● " + config.name);
        tvActiveMeta.setText(
                ("xtream".equals(config.type) ? "Xtream Codes" : "Lista M3U") + " · "
                        + state.liveChannels.size() + " canales · "
                        + state.movies.size() + " películas · "
                        + state.series.size() + " series");
    }

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

                AppState state = AppState.get();
                state.liveChannels = live;
                state.movies = movies;
                state.series = series;

                prefs.savePlaylistConfig(config);

                mainHandler.post(() -> {
                    setLoading(false, "✅ Lista cargada: " + live.size() + " canales, "
                            + movies.size() + " películas, " + series.size() + " series.");
                    showActiveListCard(config);
                    Toast.makeText(this, "Lista guardada correctamente", Toast.LENGTH_SHORT).show();
                });

            } catch (IOException e) {
                postStatus("Error de red: " + e.getMessage(), false);
            } catch (Exception e) {
                postStatus("Error inesperado: " + e.getMessage(), false);
            }
        });
    }

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

                mainHandler.post(() -> {
                    setLoading(false, "✅ Lista cargada: " + all.size() + " elementos en total.");
                    showActiveListCard(config);
                    Toast.makeText(this, "Lista guardada correctamente", Toast.LENGTH_SHORT).show();
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

    private void onDeleteList() {
        prefs.clearPlaylistConfig();
        AppState state = AppState.get();
        state.liveChannels.clear();
        state.movies.clear();
        state.series.clear();
        cardActiveList.setVisibility(View.GONE);
        inputServer.setText("");
        inputUser.setText("");
        inputPass.setText("");
        inputM3uUrl.setText("");
        setStatus("Lista eliminada.");
        Toast.makeText(this, "Lista eliminada", Toast.LENGTH_SHORT).show();
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
        executor.shutdownNow();
    }
}
