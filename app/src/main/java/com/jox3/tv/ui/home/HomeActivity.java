package com.jox3.tv.ui.home;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
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
import java.util.List;

public class HomeActivity extends AppCompatActivity {

    private LinearLayout layoutEmpty, rowFavoritesContainer;
    private View scrollContent;
    private RecyclerView rowFavorites, rowLive, rowMovies, rowSeries;
    private EditText inputSearch;
    private TextView btnSettings, btnGoSettings;

    private AppPrefs prefs;
    private MediaCardAdapter favAdapter, liveAdapter, moviesAdapter, seriesAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        prefs = new AppPrefs(this);

        bindViews();
        setupRows();
        setupSearch();
        setupButtons();
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
        rowLive = findViewById(R.id.row_live);
        rowMovies = findViewById(R.id.row_movies);
        rowSeries = findViewById(R.id.row_series);

        inputSearch = findViewById(R.id.input_search);
        btnSettings = findViewById(R.id.btn_settings);
        btnGoSettings = findViewById(R.id.btn_go_settings);
    }

    private void setupRows() {
        rowFavorites.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        rowLive.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        rowMovies.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        rowSeries.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));

        favAdapter = new MediaCardAdapter(new ArrayList<>(), prefs, this::openItem);
        liveAdapter = new MediaCardAdapter(new ArrayList<>(), prefs, this::openItem);
        moviesAdapter = new MediaCardAdapter(new ArrayList<>(), prefs, this::openItem);
        seriesAdapter = new MediaCardAdapter(new ArrayList<>(), prefs, this::openItem);

        rowFavorites.setAdapter(favAdapter);
        rowLive.setAdapter(liveAdapter);
        rowMovies.setAdapter(moviesAdapter);
        rowSeries.setAdapter(seriesAdapter);
    }

    private void setupSearch() {
        inputSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                filterContent(s.toString().trim());
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

        liveAdapter.updateData(state.liveChannels);
        moviesAdapter.updateData(state.movies);
        seriesAdapter.updateData(state.series);

        List<MediaItem> favorites = collectFavorites(state);
        rowFavoritesContainer.setVisibility(favorites.isEmpty() ? View.GONE : View.VISIBLE);
        favAdapter.updateData(favorites);
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

    private void filterContent(String query) {
        AppState state = AppState.get();
        if (query.isEmpty()) {
            liveAdapter.updateData(state.liveChannels);
            moviesAdapter.updateData(state.movies);
            seriesAdapter.updateData(state.series);
            return;
        }

        String lower = query.toLowerCase();
        liveAdapter.updateData(filterByName(state.liveChannels, lower));
        moviesAdapter.updateData(filterByName(state.movies, lower));
        seriesAdapter.updateData(filterByName(state.series, lower));
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
