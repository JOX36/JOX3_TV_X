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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Pantalla "Ver todo" reutilizable para los 3 tipos de contenido (Canales,
 * Películas, Series). Muestra todo el catálogo de ese tipo, agrupado por
 * categoría real (Deportes, Noticias, etc.), cada una con su propia fila
 * horizontal con scroll independiente. Arriba tiene chips de acceso directo
 * a cada categoría (saltan directo a su grilla completa) y una lupa que
 * despliega un buscador cuando se necesita.
 */
public class ContentListActivity extends AppCompatActivity {

    public static final String EXTRA_TYPE = "extra_type"; // "live" | "vod" | "series" | "favorites"

    private ImageView btnBack, btnSearchToggle;
    private TextView tvScreenTitle, tvTotalCount, btnCategoryToggle;
    private EditText inputSearch;
    private View searchBarContainer;
    private RecyclerView categoryDropdownList;
    private LinearLayout layoutEmpty, sectionsContainer;
    private View scrollContent;

    private AppPrefs prefs;
    private String contentType;
    private String selectedCategory; // null = "Todos"
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
        btnSearchToggle = findViewById(R.id.btn_search_toggle);
        tvScreenTitle = findViewById(R.id.tv_screen_title);
        tvTotalCount = findViewById(R.id.tv_total_count);
        inputSearch = findViewById(R.id.input_search);
        searchBarContainer = findViewById(R.id.search_bar_container);
        btnCategoryToggle = findViewById(R.id.btn_category_toggle);
        categoryDropdownList = findViewById(R.id.category_dropdown_list);
        layoutEmpty = findViewById(R.id.layout_empty);
        sectionsContainer = findViewById(R.id.sections_container);
        scrollContent = findViewById(R.id.scroll_content);

        btnBack.setOnClickListener(v -> finish());
        btnSearchToggle.setOnClickListener(v -> toggleSearchBar());
        btnCategoryToggle.setOnClickListener(v -> toggleCategoryDropdown());

        categoryDropdownList.setLayoutManager(new LinearLayoutManager(this));
    }

    private void toggleCategoryDropdown() {
        boolean isVisible = categoryDropdownList.getVisibility() == View.VISIBLE;
        categoryDropdownList.setVisibility(isVisible ? View.GONE : View.VISIBLE);
    }

    private void toggleSearchBar() {
        boolean isVisible = searchBarContainer.getVisibility() == View.VISIBLE;
        if (isVisible) {
            searchBarContainer.setVisibility(View.GONE);
            inputSearch.setText("");
        } else {
            searchBarContainer.setVisibility(View.VISIBLE);
            inputSearch.requestFocus();
        }
    }

    private void setupHeader() {
        String title;
        switch (contentType) {
            case MediaItem.VOD: title = "Películas"; break;
            case MediaItem.SERIES: title = "Series"; break;
            case "favorites": title = "★ Favoritos"; break;
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
        if ("favorites".equals(contentType)) {
            allItems = new ArrayList<>();
            for (MediaItem item : state.liveChannels) if (prefs.isFav(item.favKey())) allItems.add(item);
            for (MediaItem item : state.movies) if (prefs.isFav(item.favKey())) allItems.add(item);
            for (MediaItem item : state.series) if (prefs.isFav(item.favKey())) allItems.add(item);
        } else {
            switch (contentType) {
                case MediaItem.VOD: allItems = state.movies; break;
                case MediaItem.SERIES: allItems = state.series; break;
                default: allItems = state.liveChannels; break;
            }
        }
        tvTotalCount.setText(allItems.size() + " en total");
        buildCategoryShortcuts();
        renderSections("");
    }

    /** Lista desplegable: "Todos" + una fila por cada categoría real. Filtra
     *  en esta misma pantalla (no navega a otra), y marca cuál está activa. */
    private void buildCategoryShortcuts() {
        LinkedHashSet<String> categories = new LinkedHashSet<>();
        for (MediaItem item : allItems) {
            categories.add(item.category != null && !item.category.isEmpty()
                    ? item.category : "General");
        }

        List<String> categoryList = new ArrayList<>();
        categoryList.add("Todos");
        categoryList.addAll(categories);

        String activeLabel = selectedCategory != null ? selectedCategory : "Todos";

        categoryDropdownList.setAdapter(new CategoryDropdownAdapter(categoryList, activeLabel, category -> {
            categoryDropdownList.setVisibility(View.GONE);
            selectedCategory = "Todos".equals(category) ? null : category;
            btnCategoryToggle.setText((selectedCategory != null ? selectedCategory : "Categorías") + "  ▾");
            buildCategoryShortcuts();
            renderSections(inputSearch.getText() != null ? inputSearch.getText().toString().trim().toLowerCase() : "");
        }));
    }

    /** Agrupa por categoría real (o solo la seleccionada) y construye una sección por cada una. */
    private void renderSections(String query) {
        sectionsContainer.removeAllViews();

        List<MediaItem> filtered = new ArrayList<>();
        for (MediaItem item : allItems) {
            String itemCategory = item.category != null && !item.category.isEmpty()
                    ? item.category : "General";
            boolean matchesCategory = selectedCategory == null || selectedCategory.equals(itemCategory);
            boolean matchesQuery = query.isEmpty() || (item.name != null && item.name.toLowerCase().contains(query));
            if (matchesCategory && matchesQuery) filtered.add(item);
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

            title.setText(entry.getKey() + " (" + entry.getValue().size() + ")  ›");
            recycler.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
            recycler.setAdapter(new MediaCardAdapter(entry.getValue(), prefs, this::openItem));

            String categoryName = entry.getKey();
            title.setOnClickListener(v -> openCategoryGrid(categoryName));

            sectionsContainer.addView(sectionView);
        }
    }

    private void openCategoryGrid(String category) {
        Intent intent = new Intent(this, CategoryGridActivity.class);
        intent.putExtra(CategoryGridActivity.EXTRA_TYPE, contentType);
        intent.putExtra(CategoryGridActivity.EXTRA_CATEGORY, category);
        startActivity(intent);
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
