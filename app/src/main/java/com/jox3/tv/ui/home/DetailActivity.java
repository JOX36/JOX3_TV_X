package com.jox3.tv.ui.home;

import android.content.Intent;
import android.os.Bundle;
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
    private TextView btnPlay, btnFav, episodesStatus;
    private RecyclerView seasonChips, similarRecycler;

    private AppPrefs prefs;
    private MediaItem item;
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
        episodesStatus = findViewById(R.id.episodes_status);
        seasonChips = findViewById(R.id.season_chips);
        similarRecycler = findViewById(R.id.similar_recycler);

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
        PlaylistConfig config = prefs.getPlaylistConfig();
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
        PlaylistConfig config = prefs.getPlaylistConfig();
        if (config == null || !PlaylistConfig.TYPE_XTREAM.equals(config.type)) {
            detailSynopsis.setText("Disponible en tu lista privada.");
            return;
        }

        executor.execute(() -> {
            XtreamClient client = new XtreamClient(config);
            String synopsis = MediaItem.SERIES.equals(item.type)
                    ? client.getSeriesSynopsis(item.seriesId != null ? item.seriesId : item.id)
                    : client.getVodSynopsis(item.id);

            mainHandler.post(() -> {
                detailSynopsis.setText(synopsis != null && !synopsis.isEmpty()
                        ? synopsis : "Sin sinopsis disponible.");
            });
        });
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
