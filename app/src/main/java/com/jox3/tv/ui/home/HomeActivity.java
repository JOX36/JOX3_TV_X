package com.jox3.tv.ui.home;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.jox3.tv.R;
import com.jox3.tv.model.MediaItem;
import com.jox3.tv.ui.player.PlayerActivity;
import com.jox3.tv.ui.settings.SettingsActivity;
import com.jox3.tv.util.AppPrefs;
import com.jox3.tv.util.AppState;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public class HomeActivity extends AppCompatActivity {

    private LinearLayout layoutEmpty, rowFavoritesContainer;
    private View scrollContent;
    private RecyclerView rowFavorites, rowCategories, rowLive, rowMovies, rowSeries;
    private EditText inputSearch;
    private TextView btnSettings, btnGoSettings;

    private FrameLayout heroBanner;
    private TextView heroTitle, heroSubtitle, heroBadge;
    private MediaItem heroItem;

    private AppPrefs prefs;
    private MediaCardAdapter favAdapter, liveAdapter, moviesAdapter, seriesAdapter;
    private CategoryChipAdapter categoryAdapter;
    private String selectedCategory = "Todos";
    private String currentSearchQuery = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        prefs = new AppPrefs(this);

        bindViews();
        setupRows();
        setupSearch();
        setupButtons();
        checkCrashLog();
    }

    private void checkCrashLog() {
        String crashLog = prefs.getCrashLog();
        if (crashLog == null || crashLog.isEmpty()) return;

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("⚠️ La app se cerró la última vez")
                .setMessage(crashLog)
                .setPositiveButton("Copiar y cerrar", (d, w) -> {
                    android.content.ClipboardManager clipboard =
                            (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    android.content.ClipData clip =
                            android.content.ClipData.newPlainText("crash_log", crashLog);
                    clipboard.setPrimaryClip(clip);
                    android.widget.Toast.makeText(this, "Copiado al portapapeles",
                            android.widget.Toast.LENGTH_SHORT).show();
                    prefs.clearCrashLog();
                })
                .setNegativeButton("Cerrar", (d, w) -> prefs.clearCrashLog())
                .setCancelable(false)
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshContent();
    }

    private void bindViews() {
        layoutEmpty = findViewById(R.id.layout_empty);
        scrollContent = findViewById(R.id.scroll_content);
        rowFavoritesContainer = findViewById(R.id.row_favorites_container);

        rowFavorites = findViewById(R.id.row_favorites);
        rowCategories = findViewById(R.id.row_categories);
        rowLive = findViewById(R.id.row_live);
        rowMovies = findViewById(R.id.row_movies);
        rowSeries = findViewById(R.id.row_series);

        inputSearch = findViewById(R.id.input_search);
        btnSettings = findViewById(R.id.btn_settings);
        btnGoSettings = findViewById(R.id.btn_go_settings);

        heroBanner = findViewById(R.id.hero_banner);
        heroTitle = findViewById(R.id.hero_title);
        heroSubtitle = findViewById(R.id.hero_subtitle);
        heroBadge = findViewById(R.id.hero_badge);
    }

    private void setupRows() {
        rowFavorites.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        rowCategories.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        rowLive.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        rowMovies.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        rowSeries.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));

        favAdapter = new MediaCardAdapter(new ArrayList<>(), prefs, this::openItem);
        liveAdapter = new MediaCardAdapter(new ArrayList<>(), prefs, this::openItem);
        moviesAdapter = new MediaCardAdapter(new ArrayList<>(), prefs, this::openItem);
        seriesAdapter = new MediaCardAdapter(new ArrayList<>(), prefs, this::openItem);
        categoryAdapter = new CategoryChipAdapter(new ArrayList<>(), category -> {
            selectedCategory = category;
            applyFilters();
        });

        rowFavorites.setAdapter(favAdapter);
        rowLive.setAdapter(liveAdapter);
        rowMovies.setAdapter(moviesAdapter);
        rowSeries.setAdapter(seriesAdapter);
        rowCategories.setAdapter(categoryAdapter);
    }

    private void setupSearch() {
        inputSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                currentSearchQuery = s.toString().trim();
                applyFilters();
            }
        });
    }

    private void setupButtons() {
        btnSettings.setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));

        if (btnGoSettings != null) {
            btnGoSettings.setOnClickListener(v ->
                    startActivity(new Intent(this, SettingsActivity.class)));
        }

        heroBanner.setOnClickListener(v -> {
            if (heroItem != null) openItem(heroItem, -1);
        });
    }

    private void refreshContent() {
        AppState state = AppState.get();
        boolean hasData = !state.liveChannels.isEmpty()
                || !state.movies.isEmpty()
                || !state.series.isEmpty();

        layoutEmpty.setVisibility(hasData ? View.GONE : View.VISIBLE);
        scrollContent.setVisibility(hasData ? View.VISIBLE : View.GONE);

        if (!hasData) return;

        moviesAdapter.updateData(state.movies);
        seriesAdapter.updateData(state.series);

        List<MediaItem> favorites = collectFavorites(state);
        rowFavoritesContainer.setVisibility(favorites.isEmpty() ? View.GONE : View.VISIBLE);
        favAdapter.updateData(favorites);

        setupHero(state);
        setupCategories(state);
        applyFilters();
    }

    private void setupHero(AppState state) {
        MediaItem candidate = !state.liveChannels.isEmpty() ? state.liveChannels.get(0)
                : (!state.movies.isEmpty() ? state.movies.get(0) : null);

        if (candidate == null) {
            heroBanner.setVisibility(View.GONE);
            return;
        }

        heroItem = candidate;
        heroBanner.setVisibility(View.VISIBLE);
        heroTitle.setText(candidate.name);
        heroSubtitle.setText(candidate.category != null ? candidate.category : "");
        heroBadge.setVisibility(MediaItem.LIVE.equals(candidate.type) ? View.VISIBLE : View.GONE);
    }

    private void setupCategories(AppState state) {
        LinkedHashSet<String> categories = new LinkedHashSet<>();
        categories.add("Todos");
        for (MediaItem item : state.liveChannels) {
            categories.add(item.category != null && !item.category.isEmpty()
                    ? item.category : "General");
        }
        categoryAdapter.updateCategories(new ArrayList<>(categories));
        selectedCategory = "Todos";
    }

    private List<MediaItem> collectFavorites(AppState state) {
        List<MediaItem> favs = new ArrayList<>();
        for (MediaItem item : state.liveChannels)
            if (prefs.isFav(item.favKey())) favs.add(item);
        for (MediaItem item : state.movies)
            if (prefs.isFav(item.favKey())) favs.add(item);
        for (MediaItem item : state.series)
            if (prefs.isFav(item.favKey())) favs.add(item);
        return favs;
    }

    private void applyFilters() {
        AppState state = AppState.get();
        String lowerQuery = currentSearchQuery.toLowerCase();

        List<MediaItem> liveFiltered = new ArrayList<>();
        for (MediaItem item : state.liveChannels) {
            boolean matchesCategory = "Todos".equals(selectedCategory)
                    || selectedCategory.equals(item.category)
                    || ("General".equals(selectedCategory)
                        && (item.category == null || item.category.isEmpty()));
            boolean matchesQuery = lowerQuery.isEmpty()
                    || (item.name != null && item.name.toLowerCase().contains(lowerQuery));
            if (matchesCategory && matchesQuery) liveFiltered.add(item);
        }
        liveAdapter.updateData(liveFiltered);

        if (lowerQuery.isEmpty()) {
            moviesAdapter.updateData(state.movies);
            seriesAdapter.updateData(state.series);
        } else {
            moviesAdapter.updateData(filterByName(state.movies, lowerQuery));
            seriesAdapter.updateData(filterByName(state.series, lowerQuery));
        }
    }

    private List<MediaItem> filterByName(List<MediaItem> source, String lowerQuery) {
        List<MediaItem> result = new ArrayList<>();
        for (MediaItem item : source) {
            if (item.name != null && item.name.toLowerCase().contains(lowerQuery)) {
                result.add(item);
            }
        }
        return result;
    }

    private void openItem(MediaItem item, int position) {
        if (MediaItem.SERIES.equals(item.type)) {
            if (item.url == null || item.url.isEmpty()) {
                android.widget.Toast.makeText(this,
                        "Selección de episodios próximamente", android.widget.Toast.LENGTH_SHORT).show();
                return;
            }
        }

        AppState state = AppState.get();
        if (MediaItem.LIVE.equals(item.type)) {
            state.channelList = state.liveChannels;
            state.channelIdx = state.liveChannels.indexOf(item);
        }

        Intent intent = new Intent(this, PlayerActivity.class);
        intent.putExtra("item", item);
        startActivity(intent);
    }
}
