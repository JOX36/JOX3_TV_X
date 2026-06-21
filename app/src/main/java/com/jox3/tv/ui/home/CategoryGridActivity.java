package com.jox3.tv.ui.home;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.jox3.tv.R;
import com.jox3.tv.model.MediaItem;
import com.jox3.tv.ui.player.PlayerActivity;
import com.jox3.tv.util.AppPrefs;
import com.jox3.tv.util.AppState;

import java.util.ArrayList;
import java.util.List;

public class CategoryGridActivity extends AppCompatActivity {

    public static final String EXTRA_TYPE = "extra_type";
    public static final String EXTRA_CATEGORY = "extra_category";

    private static final int GRID_COLUMNS = 3;

    private ImageView btnBack;
    private TextView tvTitle, tvTotalCount;
    private RecyclerView gridRecycler;
    private AppPrefs prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_category_grid);
        prefs = new AppPrefs(this);

        btnBack = findViewById(R.id.btn_back);
        tvTitle = findViewById(R.id.tv_category_title);
        tvTotalCount = findViewById(R.id.tv_total_count);
        gridRecycler = findViewById(R.id.grid_recycler);

        btnBack.setOnClickListener(v -> finish());

        String type = getIntent().getStringExtra(EXTRA_TYPE);
        String category = getIntent().getStringExtra(EXTRA_CATEGORY);
        if (type == null) type = MediaItem.LIVE;
        if (category == null) category = "General";

        tvTitle.setText(category);
        loadItems(type, category);
    }

    private void loadItems(String type, String category) {
        AppState state = AppState.get();
        List<MediaItem> source;
        if ("favorites".equals(type)) {
            source = new ArrayList<>();
            for (MediaItem item : state.liveChannels) if (prefs.isFav(item.favKey())) source.add(item);
            for (MediaItem item : state.movies) if (prefs.isFav(item.favKey())) source.add(item);
            for (MediaItem item : state.series) if (prefs.isFav(item.favKey())) source.add(item);
        } else {
            switch (type) {
                case MediaItem.VOD: source = state.movies; break;
                case MediaItem.SERIES: source = state.series; break;
                default: source = state.liveChannels; break;
            }
        }

        List<MediaItem> filtered = new ArrayList<>();
        for (MediaItem item : source) {
            String itemCategory = item.category != null && !item.category.isEmpty()
                    ? item.category : "General";
            if (itemCategory.equals(category)) filtered.add(item);
        }

        tvTotalCount.setText(filtered.size() + " elementos");

        gridRecycler.setLayoutManager(new GridLayoutManager(this, GRID_COLUMNS));
        gridRecycler.setAdapter(new MediaCardAdapter(filtered, prefs, this::openItem,
                R.layout.item_media_card_grid));
    }

    private void openItem(MediaItem item, int position) {
        AppState state = AppState.get();

        if (MediaItem.LIVE.equals(item.type)) {
            state.channelList = state.liveChannels;
            state.channelIdx = state.liveChannels.indexOf(item);

            Intent intent = new Intent(this, PlayerActivity.class);
            intent.putExtra("item", item);
            startActivity(intent);
            return;
        }

        Intent intent = new Intent(this, DetailActivity.class);
        intent.putExtra("item", item);
        startActivity(intent);
    }
}
