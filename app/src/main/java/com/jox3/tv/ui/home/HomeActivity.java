package com.jox3.tv.ui.home;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.jox3.tv.R;
import com.jox3.tv.model.MediaItem;
import com.jox3.tv.ui.player.PlayerActivity;
import com.jox3.tv.ui.settings.SettingsActivity;
import com.jox3.tv.util.AppPrefs;
import com.jox3.tv.util.AppState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

public class HomeActivity extends AppCompatActivity {

    private static final int MAX_ITEMS_PER_ROW = 12;
    private static final int HERO_SLIDE_COUNT = 5;
    private static final long HERO_AUTOPLAY_MS = 5000;

    private LinearLayout layoutEmpty, rowFavoritesContainer, rowContinueContainer;
    private View scrollContent;
    private RecyclerView rowContinue, rowFavorites, rowCategories, rowLive, rowMovies, rowSeries;
    private EditText inputSearch;
    private View btnSettings;
    private View btnGoSettings;

    private ViewPager2 heroPager;
    private LinearLayout heroDots;
    private HeroSlideAdapter heroAdapter;
    private final List<MediaItem> heroItems = new ArrayList<>();
    private final Handler heroHandler = new Handler(Looper.getMainLooper());
    private final Runnable heroAutoplayRunnable = new Runnable() {
        @Override public void run() {
            if (heroItems.isEmpty()) return;
            int next = (heroPager.getCurrentItem() + 1) % heroItems.size();
            heroPager.setCurrentItem(next, true);
            heroHandler.postDelayed(this, HERO_AUTOPLAY_MS);
        }
    };

    private AppPrefs prefs;
    private MediaCardAdapter continueAdapter, favAdapter, liveAdapter, moviesAdapter, seriesAdapter;
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
        setupHeroPager();
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
        startHeroAutoplay();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopHeroAutoplay();
    }

    private void bindViews() {
        layoutEmpty = findViewById(R.id.layout_empty);
        scrollContent = findViewById(R.id.scroll_content);
        rowFavoritesContainer = findViewById(R.id.row_favorites_container);
        rowContinueContainer = findViewById(R.id.row_continue_container);

        rowContinue = findViewById(R.id.row_continue);
        rowFavorites = findViewById(R.id.row_favorites);
        rowCategories = findViewById(R.id.row_categories);
        rowLive = findViewById(R.id.row_live);
        rowMovies = findViewById(R.id.row_movies);
        rowSeries = findViewById(R.id.row_series);

        inputSearch = findViewById(R.id.input_search);
        btnSettings = findViewById(R.id.btn_settings);
        btnGoSettings = findViewById(R.id.btn_go_settings);

        heroPager = findViewById(R.id.hero_pager);
        heroDots = findViewById(R.id.hero_dots);
    }

    private void setupRows() {
        rowContinue.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        rowFavorites.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        rowCategories.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        rowLive.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        rowMovies.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        rowSeries.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));

        continueAdapter = new MediaCardAdapter(new ArrayList<>(), prefs, this::openItem);
        favAdapter = new MediaCardAdapter(new ArrayList<>(), prefs, this::openItem);
        liveAdapter = new MediaCardAdapter(new ArrayList<>(), prefs, this::openItem);
        moviesAdapter = new MediaCardAdapter(new ArrayList<>(), prefs, this::openItem);
        seriesAdapter = new MediaCardAdapter(new ArrayList<>(), prefs, this::openItem);
        categoryAdapter = new CategoryChipAdapter(new ArrayList<>(), category -> {
            selectedCategory = category;
            applyFilters();
        });

        rowContinue.setAdapter(continueAdapter);
        rowFavorites.setAdapter(favAdapter);
        rowLive.setAdapter(liveAdapter);
        rowMovies.setAdapter(moviesAdapter);
        rowSeries.setAdapter(seriesAdapter);
        rowCategories.setAdapter(categoryAdapter);
    }

    private void setupHeroPager() {
        heroAdapter = new HeroSlideAdapter(heroItems, prefs, new HeroSlideAdapter.OnHeroAction() {
            @Override public void onPlay(MediaItem item) { openItem(item, -1); }
            @Override public void onToggleFav(MediaItem item) { prefs.toggleFav(item.favKey()); }
        });
        heroPager.setAdapter(heroAdapter);
        heroPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override public void onPageSelected(int position) {
                updateHeroDots(position);
            }
        });
    }

    private void startHeroAutoplay() {
        heroHandler.removeCallbacks(heroAutoplayRunnable);
        heroHandler.postDelayed(heroAutoplayRunnable, HERO_AUTOPLAY_MS);
    }

    private void stopHeroAutoplay() {
        heroHandler.removeCallbacks(heroAutoplayRunnable);
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
    }

    private void refreshContent() {
        AppState state = AppState.get();
        boolean hasData = !state.liveChannels.isEmpty()
                || !state.movies.isEmpty()
                || !state.series.isEmpty();

        layoutEmpty.setVisibility(hasData ? View.GONE : View.VISIBLE);
        scrollContent.setVisibility(hasData ? View.VISIBLE : View.GONE);

        if (!hasData) return;

        moviesAdapter.updateData(capList(state.movies));
        seriesAdapter.updateData(capList(state.series));

        List<MediaItem> continueWatching = prefs.getRecentlyWatched();
        rowContinueContainer.setVisibility(continueWatching.isEmpty() ? View.GONE : View.VISIBLE);
        continueAdapter.updateData(capList(continueWatching));

        List<MediaItem> favorites = collectFavorites(state);
        rowFavoritesContainer.setVisibility(favorites.isEmpty() ? View.GONE : View.VISIBLE);
        favAdapter.updateData(capList(favorites));

        setupHero(state);
        setupCategories(state);
        applyFilters();
    }

    private void setupHero(AppState state) {
        List<MediaItem> candidates = new ArrayList<>();
        candidates.addAll(state.movies);
        candidates.addAll(state.series);
        if (candidates.size() < HERO_SLIDE_COUNT) {
            candidates.addAll(state.liveChannels);
        }

        if (candidates.isEmpty()) {
            heroPager.setVisibility(View.GONE);
            heroDots.setVisibility(View.GONE);
            stopHeroAutoplay();
            return;
        }

        Collections.shuffle(candidates);
        List<MediaItem> selected = candidates.size() > HERO_SLIDE_COUNT
                ? candidates.subList(0, HERO_SLIDE_COUNT) : candidates;

        heroItems.clear();
        heroItems.addAll(selected);
        heroAdapter.notifyDataSetChanged();

        heroPager.setVisibility(View.VISIBLE);
        heroPager.setCurrentItem(0, false);
        buildHeroDots();
        updateHeroDots(0);
        startHeroAutoplay();
    }

    private void buildHeroDots() {
        heroDots.removeAllViews();
        heroDots.setVisibility(heroItems.size() > 1 ? View.VISIBLE : View.GONE);

        for (int i = 0; i < heroItems.size(); i++) {
            View dot = new View(this);
            int widthPx = (int) (22 * getResources().getDisplayMetrics().density);
            int heightPx = (int) (3 * getResources().getDisplayMetrics().density);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(widthPx, heightPx);
            params.setMarginEnd((int) (5 * getResources().getDisplayMetrics().density));
            dot.setLayoutParams(params);
            dot.setBackgroundResource(R.drawable.bg_chip_inactive);
            heroDots.addView(dot);
        }
    }

    private void updateHeroDots(int activeIndex) {
        for (int i = 0; i < heroDots.getChildCount(); i++) {
            View dot = heroDots.getChildAt(i);
            dot.setBackgroundResource(i == activeIndex ? R.drawable.bg_chip_active : R.drawable.bg_chip_inactive);
        }
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
        liveAdapter.updateData(capList(liveFiltered));

        if (lowerQuery.isEmpty()) {
            moviesAdapter.updateData(capList(state.movies));
            seriesAdapter.updateData(capList(state.series));
        } else {
            moviesAdapter.updateData(capList(filterByName(state.movies, lowerQuery)));
            seriesAdapter.updateData(capList(filterByName(state.series, lowerQuery)));
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

    private List<MediaItem> capList(List<MediaItem> source) {
        if (source.size() <= MAX_ITEMS_PER_ROW) return source;
        return new ArrayList<>(source.subList(0, MAX_ITEMS_PER_ROW));
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
