package com.jox3.tv.ui.home;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.jox3.tv.R;
import com.jox3.tv.model.MediaItem;
import com.jox3.tv.ui.player.PlayerActivity;
import com.jox3.tv.util.AppPrefs;
import com.jox3.tv.util.AppState;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Muestra TODOS los items de una categoría específica en una grilla vertical.
 * Incluye chips arriba para cambiar de categoría sin salir de la pantalla.
 */
public class CategoryGridActivity extends AppCompatActivity {

    public static final String EXTRA_TYPE = "extra_type";       // live | vod | series | favorites
    public static final String EXTRA_CATEGORY = "extra_category";

    private static final int GRID_COLUMNS = 3;

    private ImageView btnBack;
    private TextView tvTitle, tvTotalCount, btnCategoryToggle;
    private RecyclerView gridRecycler, categoryDropdownList;
    private AppPrefs prefs;
    private String contentType;
    private String currentCategory;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_category_grid);
        prefs = new AppPrefs(this);

        btnBack = findViewById(R.id.btn_back);
        tvTitle = findViewById(R.id.tv_category_title);
        tvTotalCount = findViewById(R.id.tv_total_count);
        gridRecycler = findViewById(R.id.grid_recycler);
        btnCategoryToggle = findViewById(R.id.btn_category_toggle);
        // El zoom de foco crece desde el centro por defecto; como este
        // botón está pegado al borde izquierdo de la pantalla, eso hacía
        // que el texto se "perdiera" fuera del área visible al recibir
        // foco. Fijamos el pivote en su propio borde izquierdo.
        btnCategoryToggle.setPivotX(0f);
        categoryDropdownList = findViewById(R.id.category_dropdown_list);

        btnBack.setOnClickListener(v -> finish());
        btnCategoryToggle.setOnClickListener(v -> toggleCategoryDropdown());

        contentType = getIntent().getStringExtra(EXTRA_TYPE);
        currentCategory = getIntent().getStringExtra(EXTRA_CATEGORY);
        if (contentType == null) contentType = MediaItem.LIVE;
        if (currentCategory == null) currentCategory = "General";

        categoryDropdownList.setLayoutManager(new LinearLayoutManager(this));
        gridRecycler.setLayoutManager(new GridLayoutManager(this, GRID_COLUMNS));

        showCategory(currentCategory);
    }

    private void toggleCategoryDropdown() {
        boolean isVisible = categoryDropdownList.getVisibility() == View.VISIBLE;
        categoryDropdownList.setVisibility(isVisible ? View.GONE : View.VISIBLE);
    }

    private List<MediaItem> getSourceList() {
        AppState state = AppState.get();
        if ("favorites".equals(contentType)) {
            List<MediaItem> favs = new ArrayList<>();
            for (MediaItem item : state.liveChannels) if (prefs.isFav(item.favKey())) favs.add(item);
            for (MediaItem item : state.movies) if (prefs.isFav(item.favKey())) favs.add(item);
            for (MediaItem item : state.series) if (prefs.isFav(item.favKey())) favs.add(item);
            return favs;
        }
        switch (contentType) {
            case MediaItem.VOD: return state.movies;
            case MediaItem.SERIES: return state.series;
            default: return state.liveChannels;
        }
    }

    /** Construye la lista desplegable con todas las categorías disponibles para este tipo. */
    private void buildCategorySwitcher() {
        LinkedHashSet<String> categories = new LinkedHashSet<>();
        for (MediaItem item : getSourceList()) {
            categories.add(item.category != null && !item.category.isEmpty()
                    ? item.category : "General");
        }

        List<String> categoryList = new ArrayList<>(categories);
        categoryDropdownList.setAdapter(new CategoryDropdownAdapter(categoryList, currentCategory, category -> {
            categoryDropdownList.setVisibility(View.GONE);
            if (prefs.isAdultCategory(category)) {
                ParentalPinDialog.requireUnlock(this, prefs, () -> showCategory(category));
            } else {
                showCategory(category);
            }
        }));
    }

    /** Cambia la grilla a otra categoría, sin salir ni recargar toda la pantalla. */
    private void showCategory(String category) {
        currentCategory = category;
        tvTitle.setText(category);
        int titleColor = MediaCardAdapter.getCategoryColor(category);
        boolean hasGenreColor = titleColor != android.graphics.Color.parseColor("#B8B8CC");
        tvTitle.setTextColor(hasGenreColor ? titleColor
                : tvTitle.getResources().getColor(R.color.text_primary));
        btnCategoryToggle.setText(category + "  ▾");
        buildCategorySwitcher();

        List<MediaItem> filtered = new ArrayList<>();
        for (MediaItem item : getSourceList()) {
            String itemCategory = item.category != null && !item.category.isEmpty()
                    ? item.category : "General";
            if (itemCategory.equals(category)) filtered.add(item);
        }

        tvTotalCount.setText(filtered.size() + " elementos");
        gridRecycler.setAdapter(new MediaCardAdapter(filtered, prefs, this::openItem,
                R.layout.item_media_card_grid));
        gridRecycler.scrollToPosition(0);
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
