package com.jox3.tv.ui.home;

import android.app.DownloadManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.jox3.tv.R;
import com.jox3.tv.data.XtreamClient;
import com.jox3.tv.model.MediaItem;
import com.jox3.tv.model.PlaylistConfig;
import com.jox3.tv.ui.player.PlayerActivity;
import com.jox3.tv.util.AppPrefs;
import com.jox3.tv.util.AppState;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Pantalla de detalle de una película o serie.
 * - Película: sinopsis + botones Reproducir/Favorito + fila de similares.
 * - Serie: sinopsis + selector de temporadas + lista de episodios (esto es
 *   lo que permite que las series sean reproducibles desde la app).
 */
public class DetailActivity extends AppCompatActivity {

    private ImageView btnBack, detailPoster;
    private TextView detailBadge, detailTitle, detailCategory, detailSynopsis;
    private LinearLayout detailActions, seriesSection, similarSection, episodesContainer;
    private TextView btnPlay, btnFav, btnDownload, episodesStatus;
    private RecyclerView seasonChips, similarRecycler;

    private View detailMetaRow;
    private TextView detailRating, detailExtraMeta, detailDirector, detailCast;

    private AppPrefs prefs;
    private MediaItem item;

    /**
     * Resuelve qué cuenta usar para pedir episodios/info extra de "item":
     * si vino de una cuenta ALTERNA (sourceAccountId no nulo, encontrado
     * vía buscador global), usa esa cuenta específica; si no, usa la
     * cuenta activa de siempre. Si la cuenta alterna ya no existe (se
     * borró mientras tanto), cae de vuelta a la activa en vez de fallar.
     */
    private PlaylistConfig resolveAccountForItem(MediaItem mediaItem) {
        if (mediaItem.sourceAccountId == null) return prefs.getPlaylistConfig();
        for (PlaylistConfig account : prefs.getAccounts()) {
            if (account.id.equals(mediaItem.sourceAccountId)) return account;
        }
        return prefs.getPlaylistConfig();
    }
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final Map<Integer, List<MediaItem>> episodesBySeason = new LinkedHashMap<>();
    private int selectedSeason = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detail);
        prefs = new AppPrefs(this);

        item = (MediaItem) getIntent().getSerializableExtra("item");
        if (item == null) { finish(); return; }

        bindViews();
        renderBasicInfo();

        if (MediaItem.SERIES.equals(item.type)) {
            setupSeriesMode();
        } else {
            setupMovieMode();
        }

        loadSynopsisInBackground();
    }

    private void bindViews() {
        btnBack = findViewById(R.id.btn_back);
        detailPoster = findViewById(R.id.detail_poster);
        detailBadge = findViewById(R.id.detail_badge);
        detailTitle = findViewById(R.id.detail_title);
        detailCategory = findViewById(R.id.detail_category);
        detailSynopsis = findViewById(R.id.detail_synopsis);
        detailActions = findViewById(R.id.detail_actions);
        seriesSection = findViewById(R.id.series_section);
        similarSection = findViewById(R.id.similar_section);
        episodesContainer = findViewById(R.id.episodes_container);
        btnPlay = findViewById(R.id.btn_play);
        btnFav = findViewById(R.id.btn_fav);
        btnDownload = findViewById(R.id.btn_download);
        episodesStatus = findViewById(R.id.episodes_status);
        seasonChips = findViewById(R.id.season_chips);
        similarRecycler = findViewById(R.id.similar_recycler);

        detailMetaRow = findViewById(R.id.detail_meta_row);
        detailRating = findViewById(R.id.detail_rating);
        detailExtraMeta = findViewById(R.id.detail_extra_meta);
        detailDirector = findViewById(R.id.detail_director);
        detailCast = findViewById(R.id.detail_cast);

        btnBack.setOnClickListener(v -> finish());
    }

    private void renderBasicInfo() {
        detailTitle.setText(item.name);
        detailCategory.setText(item.category != null ? item.category.toUpperCase() : "");
        detailBadge.setText(MediaItem.SERIES.equals(item.type) ? "SERIE" : "PELÍCULA");
        detailSynopsis.setText("Cargando sinopsis...");

        if (item.logoUrl != null && !item.logoUrl.isEmpty()) {
            Glide.with(this).load(item.logoUrl).centerCrop().into(detailPoster);
        }

        updateFavButton();
    }

    private void updateFavButton() {
        boolean isFav = prefs.isFav(item.favKey());
        btnFav.setText(isFav ? "En favoritos" : "Favorito");
    }

    // ---------------- Modo película ----------------

    private void setupMovieMode() {
        detailActions.setVisibility(View.VISIBLE);
        similarSection.setVisibility(View.VISIBLE);

        btnPlay.setOnClickListener(v -> openPlayer(item));
        btnFav.setOnClickListener(v -> {
            prefs.toggleFav(item.favKey());
            updateFavButton();
        });
        btnDownload.setOnClickListener(v -> downloadItem(item));

        loadSimilar();
    }

    private void loadSimilar() {
        AppState state = AppState.get();
        List<MediaItem> similar = new ArrayList<>();
        for (MediaItem candidate : state.movies) {
            if (candidate.favKey().equals(item.favKey())) continue;
            if (item.category != null && item.category.equals(candidate.category)) {
                similar.add(candidate);
            }
            if (similar.size() >= 12) break;
        }

        similarRecycler.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        similarRecycler.setAdapter(new MediaCardAdapter(similar, prefs, (clicked, pos) -> openDetailOrPlayer(clicked)));
    }

    // ---------------- Modo serie ----------------

    private void setupSeriesMode() {
        seriesSection.setVisibility(View.VISIBLE);
        loadEpisodesInBackground();
    }

    private void loadEpisodesInBackground() {
        PlaylistConfig config = resolveAccountForItem(item);
        if (config == null || !PlaylistConfig.TYPE_XTREAM.equals(config.type)) {
            episodesStatus.setText("Esta función requiere una lista Xtream Codes.");
            return;
        }

        String seriesId = item.seriesId != null ? item.seriesId : item.id;

        executor.execute(() -> {
            try {
                XtreamClient client = new XtreamClient(config);
                List<MediaItem> episodes = client.getSeriesEpisodes(seriesId);

                Map<Integer, List<MediaItem>> grouped = new LinkedHashMap<>();
                for (MediaItem ep : episodes) {
                    grouped.computeIfAbsent(ep.season, k -> new ArrayList<>()).add(ep);
                }

                mainHandler.post(() -> {
                    episodesBySeason.clear();
                    episodesBySeason.putAll(grouped);

                    if (episodesBySeason.isEmpty()) {
                        episodesStatus.setText("No se encontraron episodios.");
                        return;
                    }

                    episodesStatus.setVisibility(View.GONE);
                    buildSeasonChips();
                    selectSeason(episodesBySeason.keySet().iterator().next());
                });
            } catch (Exception e) {
                mainHandler.post(() ->
                        episodesStatus.setText("Error al cargar episodios: " + e.getMessage()));
            }
        });
    }

    private void buildSeasonChips() {
        List<String> seasonLabels = new ArrayList<>();
        List<Integer> seasonNumbers = new ArrayList<>(episodesBySeason.keySet());
        for (Integer season : seasonNumbers) {
            seasonLabels.add(season >= 0 ? "Temporada " + season : "Especiales");
        }

        seasonChips.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        seasonChips.setAdapter(new CategoryChipAdapter(seasonLabels, label -> {
            int index = seasonLabels.indexOf(label);
            if (index >= 0) selectSeason(seasonNumbers.get(index));
        }));
    }

    private void selectSeason(int season) {
        selectedSeason = season;
        episodesContainer.removeAllViews();

        List<MediaItem> episodes = episodesBySeason.get(season);
        if (episodes == null) return;

        LayoutInflater inflater = LayoutInflater.from(this);
        for (MediaItem episode : episodes) {
            View row = inflater.inflate(R.layout.item_episode_row, episodesContainer, false);
            TextView number = row.findViewById(R.id.episode_number);
            TextView title = row.findViewById(R.id.episode_title);
            TextView synopsis = row.findViewById(R.id.episode_synopsis);
            ImageView thumb = row.findViewById(R.id.episode_thumb);
            ImageView downloadIcon = row.findViewById(R.id.episode_download);

            number.setText(episode.episode >= 0 ? String.valueOf(episode.episode) : "-");
            title.setText(episode.name);

            if (episode.synopsis != null && !episode.synopsis.isEmpty()) {
                synopsis.setText(episode.synopsis);
                synopsis.setVisibility(View.VISIBLE);
            } else {
                synopsis.setVisibility(View.GONE);
            }

            if (episode.logoUrl != null && !episode.logoUrl.isEmpty()) {
                Glide.with(thumb.getContext()).load(episode.logoUrl).centerCrop().into(thumb);
            } else {
                thumb.setImageDrawable(null);
            }

            downloadIcon.setOnClickListener(v -> downloadItem(episode));

            row.setOnClickListener(v -> {
                AppState.get().episodeQueue = episodes;
                AppState.get().episodeIdx = episodes.indexOf(episode);
                openPlayer(episode);
            });

            episodesContainer.addView(row);
        }
    }

    // ---------------- Sinopsis ----------------

    private void loadSynopsisInBackground() {
        PlaylistConfig config = resolveAccountForItem(item);
        if (config == null || !PlaylistConfig.TYPE_XTREAM.equals(config.type)) {
            detailSynopsis.setText("Disponible en tu lista privada.");
            return;
        }

        executor.execute(() -> {
            XtreamClient client = new XtreamClient(config);
            XtreamClient.ExtraInfo extra = MediaItem.SERIES.equals(item.type)
                    ? client.getSeriesExtraInfo(item.seriesId != null ? item.seriesId : item.id)
                    : client.getVodExtraInfo(item.id);

            mainHandler.post(() -> bindExtraInfo(extra));
        });
    }

    private void bindExtraInfo(XtreamClient.ExtraInfo extra) {
        if (extra == null) {
            detailSynopsis.setText("Sin sinopsis disponible.");
            return;
        }

        detailSynopsis.setText(extra.plot != null && !extra.plot.isEmpty()
                ? extra.plot : "Sin sinopsis disponible.");

        boolean hasRating = extra.rating != null && !extra.rating.isEmpty()
                && !extra.rating.equals("0");
        boolean hasExtraMeta = extra.releaseDate != null || extra.genre != null || extra.country != null;

        if (hasRating || hasExtraMeta) {
            detailMetaRow.setVisibility(View.VISIBLE);
            detailRating.setText(hasRating ? ("★ " + extra.rating) : "");

            StringBuilder meta = new StringBuilder();
            if (extra.releaseDate != null && extra.releaseDate.length() >= 4) {
                meta.append(extra.releaseDate.substring(0, 4));
            }
            if (extra.country != null && !extra.country.isEmpty()) {
                if (meta.length() > 0) meta.append("  ·  ");
                meta.append(extra.country);
            }
            if (extra.genre != null && !extra.genre.isEmpty()) {
                if (meta.length() > 0) meta.append("  ·  ");
                meta.append(extra.genre);
            }
            detailExtraMeta.setText(meta.toString());
        }

        if (extra.director != null && !extra.director.isEmpty()) {
            detailDirector.setText("Director: " + extra.director);
            detailDirector.setVisibility(View.VISIBLE);
        }

        if (extra.cast != null && !extra.cast.isEmpty()) {
            detailCast.setText("Actores: " + extra.cast);
            detailCast.setVisibility(View.VISIBLE);
        }
    }

    // ---------------- Descarga ----------------

    /**
     * Descarga el archivo a la carpeta pública de Descargas del teléfono,
     * dentro de la subcarpeta "JOX3 TV". Usa DownloadManager del sistema,
     * que se encarga de mostrar una notificación de progreso nativa de
     * Android y funciona igual sin importar la versión de Android (con o
     * sin almacenamiento por alcance).
     */
    private void downloadItem(MediaItem target) {
        if (target.url == null || target.url.isEmpty()) {
            android.widget.Toast.makeText(this, "No hay archivo disponible para descargar",
                    android.widget.Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            String safeName = target.name != null
                    ? target.name.replaceAll("[^a-zA-Z0-9 ._-]", "").trim() : "video";
            if (safeName.isEmpty()) safeName = "video";

            String extension = ".mp4";
            int dotIdx = target.url.lastIndexOf('.');
            if (dotIdx > 0 && dotIdx > target.url.length() - 6) {
                extension = target.url.substring(dotIdx);
            }

            String fileName = safeName + "_" + System.currentTimeMillis() + extension;

            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(target.url));
            request.setTitle(target.name != null ? target.name : "JOX3 TV");
            request.setDescription("Descargando desde JOX3 TV...");
            request.setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS, "JOX3 TV/" + fileName);
            request.setAllowedOverMetered(true);
            request.setAllowedOverRoaming(true);

            // Muchos servidores Xtream rechazan descargas que no se
            // identifiquen como un reproductor de video válido.
            request.addRequestHeader("User-Agent",
                    "Mozilla/5.0 (Linux; Android 13) ExoPlayerLib/1.6.1");
            PlaylistConfig sourceConfig = resolveAccountForItem(target);
            if (sourceConfig != null && sourceConfig.serverUrl != null) {
                request.addRequestHeader("Referer", sourceConfig.serverUrl);
            }

            DownloadManager downloadManager =
                    (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            downloadManager.enqueue(request);

            android.widget.Toast.makeText(this,
                    "Descargando \"" + target.name + "\" a Descargas/JOX3 TV",
                    android.widget.Toast.LENGTH_LONG).show();

        } catch (Exception e) {
            android.widget.Toast.makeText(this,
                    "Error al iniciar la descarga: " + e.getMessage(),
                    android.widget.Toast.LENGTH_LONG).show();
        }
    }

    // ---------------- Navegación ----------------

    private void openPlayer(MediaItem target) {
        Intent intent = new Intent(this, PlayerActivity.class);
        intent.putExtra("item", target);
        startActivity(intent);
    }

    /** Desde "similares": si es serie abre otro detalle, si es película reproduce directo. */
    private void openDetailOrPlayer(MediaItem target) {
        if (MediaItem.SERIES.equals(target.type)) {
            Intent intent = new Intent(this, DetailActivity.class);
            intent.putExtra("item", target);
            startActivity(intent);
        } else {
            openPlayer(target);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }
}
