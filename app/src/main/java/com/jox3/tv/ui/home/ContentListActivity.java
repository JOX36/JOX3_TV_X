package com.jox3.tv.ui.home;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.jox3.tv.R;
import com.jox3.tv.model.MediaItem;
import com.jox3.tv.ui.player.PlayerActivity;
import com.jox3.tv.util.AppPrefs;
import com.jox3.tv.util.AppState;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ContentListActivity extends AppCompatActivity {

    public static final String EXTRA_TYPE = "extra_type";

    private ImageView btnBack;
    private TextView tvScreenTitle, tvTotalCount;
    private EditText inputSearch;
    private LinearLayout layoutEmpty, sectionsContainer;
    private View scrollContent;

    private AppPrefs prefs;
    private String contentType;
    private List<MediaItem> allItems = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_content_list);
        prefs = new AppPrefs(this);

        contentType = getIntent().getStringExtra(EXTRA_TYPE);
        if (contentType == null) contentType = MediaItem.LIVE;

        bindViews();
        setupHeader();
        setupSearch();
        loadItems();
    }

    private void bindViews() {
        btnBack = findViewById(R.id.btn_back);
        tvScreenTitle = findViewById(R.id.tv_screen_title);
        tvTotalCount = findViewById(R.id.tv_total_count);
        inputSearch = findViewById(R.id.input_search);
        layoutEmpty = findViewById(R.id.layout_empty);
        sectionsContainer = findViewById(R.id.sections_container);
        scrollContent = findViewById(R.id.scroll_content);

        btnBack.setOnClickListener(v -> finish());
    }

    private void setupHeader() {
        String title;
        switch (contentType) {
            case MediaItem.VOD: title = "Películas"; break;
            case MediaItem.SERIES: title = "Series"; break;
            default: title = "Canales en vivo"; break;
        }
        tvScreenTitle.setText(title);
    }

    private void setupSearch() {
        inputSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                renderSections(s.toString().trim().toLowerCase());
            }
        });
    }

    private void loadItems() {
        AppState state = AppState.get();
        switch (contentType) {
            case MediaItem.VOD: allItems = state.movies; break;
            case MediaItem.SERIES: allItems = state.series; break;
            default: allItems = state.liveChannels; break;
        }
        tvTotalCount.setText(allItems.size() + " en total");
        renderSections("");
    }

    private void renderSections(String query) {
        sectionsContainer.removeAllViews();

        List<MediaItem> filtered = new ArrayList<>();
        for (MediaItem item : allItems) {
            if (query.isEmpty() || (item.name != null && item.name.toLowerCase().contains(query))) {
                filtered.add(item);
            }
        }

        layoutEmpty.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
        scrollContent.setVisibility(filtered.isEmpty() ? View.GONE : View.VISIBLE);
        if (filtered.isEmpty()) return;

        Map<String, List<MediaItem>> grouped = new LinkedHashMap<>();
        for (MediaItem item : filtered) {
            String category = item.category != null && !item.category.isEmpty()
                    ? item.category : "General";
            grouped.computeIfAbsent(category, k -> new ArrayList<>()).add(item);
        }

        LayoutInflater inflater = LayoutInflater.from(this);
        for (Map.Entry<String, List<MediaItem>> entry : grouped.entrySet()) {
            View sectionView = inflater.inflate(R.layout.section_category, sectionsContainer, false);

            TextView title = sectionView.findViewById(R.id.section_title);
            RecyclerView recycler = sectionView.findViewById(R.id.section_recycler);

            title.setText(entry.getKey() + " (" + entry.getValue().size() + ")");
            recycler.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
            recycler.setAdapter(new MediaCardAdapter(entry.getValue(), prefs, this::openItem));

            sectionsContainer.addView(sectionView);
        }
    }

    private void openItem(MediaItem item, int position) {
        if (MediaItem.SERIES.equals(item.type) && (item.url == null || item.url.isEmpty())) {
            android.widget.Toast.makeText(this,
                    "Selección de episodios próximamente", android.widget.Toast.LENGTH_SHORT).show();
            return;
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
