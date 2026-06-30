package com.jox3.tv.ui.home;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.datasource.okhttp.OkHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.jox3.tv.R;
import com.jox3.tv.data.XtreamClient;
import com.jox3.tv.model.MediaItem;
import com.jox3.tv.model.PlaylistConfig;
import com.jox3.tv.ui.player.PlayerActivity;
import com.jox3.tv.ui.settings.SettingsActivity;
import com.jox3.tv.util.AppPrefs;
import com.jox3.tv.util.AppState;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HomeActivity extends AppCompatActivity {

    private static final int MAX_ITEMS_PER_ROW = 12;
    private static final int HERO_SLIDE_COUNT = 5;
    private static final long HERO_AUTOPLAY_MS = 5000;

    private LinearLayout layoutEmpty, rowFavoritesContainer, rowContinueContainer;
    private TextView accountStatusText;
    private View scrollContent;
    private RecyclerView rowContinue, rowFavorites;
    private LinearLayout rowContinueSeriesContainer, rowDiscoverContainer, rowFeaturedContainer;
    private RecyclerView rowContinueSeries, rowDiscover, rowFeatured;
    private TextView tvFeaturedTitle;
    private View btnShuffleDiscover;
    private View dividerA, dividerB, dividerC, dividerD, dividerE, dividerF;
    private View homeDefaultContent;
    private LinearLayout globalSearchResults;
    private View layoutLoading;
    private TextView loadingEmoji, loadingText;
    private Runnable loadingAnimationRunnable;
    private int loadingAnimationStep = 0;
    private MediaCardAdapter continueSeriesAdapter, discoverAdapter, featuredAdapter;
    private EditText inputSearch;
    private View searchBarContainer;
    private View btnGoSettings;

    private final ExecutorService synopsisExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private ViewPager2 heroPager;
    private LinearLayout heroDots;
    private HeroSlideAdapter heroAdapter;
    private final List<MediaItem> heroItems = new ArrayList<>();

    private FrameLayout miniPlayerContainer;
    private PlayerView miniPlayerView;
    private ImageView btnMiniExpand, btnMiniMute;
    private TextView miniChannelName, miniEpgNow;
    private LinearLayout miniEpgProgressTrack;
    private View miniEpgProgressFill;
    private ExoPlayer miniPlayer;
    private MediaItem miniPlayerChannel;
    private boolean miniPlayerMuted = true;
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
    private MediaCardAdapter continueAdapter, favAdapter;
    private String currentSearchQuery = "";

    // ---- Panel lateral (drawer) ----
    private View drawerScrim, drawerPanel, btnOpenDrawer, btnSettings, btnSearchHeader;
    private View drawerItemHome, drawerItemLive, drawerItemMovies, drawerItemSeries,
            drawerItemFavorites, drawerItemHistory, drawerItemSearch, drawerItemSettings;
    private int drawerWidthPx;

    // ---- Card grande "Última reproducción" ----
    private View continueHeroContainer, continueHeroCard;
    private ImageView continueHeroThumb, continueHeroBg;
    private TextView continueHeroTitle, continueHeroSub, continueHeroQuality, continueHeroLiveBadge;
    private View continueHeroProgressFill;
    // El ítem que se está mostrando actualmente en la card. El click
    // siempre abre ESTE, no el primero de la lista general (que puede
    // ser una película/serie si la viste después del canal).
    private MediaItem continueHeroItem;
    private View chipFavorites, chipLive, chipMovies, chipSeries;

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
        applyOrientationLayout();

        // Carga en segundo plano (hasta 5 cuentas alternas guardadas,
        // todas menos la activa) para que el buscador global pueda
        // encontrar contenido que la cuenta principal no tenga. No afecta
        // en nada la carga normal de la cuenta activa.
        com.jox3.tv.util.AlternateCatalogCache.get().startBackgroundLoadIfNeeded(this);
    }

    /**
     * En vertical, el mini-reproductor se oculta y el banner ocupa todo el
     * ancho (LinearLayout redistribuye el espacio automáticamente al quitar
     * un hijo con peso). En horizontal, vuelve a mostrarse el mini-reproductor
     * al lado del banner como siempre.
     */
    private void applyOrientationLayout() {
        boolean isPortrait = getResources().getConfiguration().orientation
                == android.content.res.Configuration.ORIENTATION_PORTRAIT;
        miniPlayerContainer.setVisibility(isPortrait ? View.GONE : View.VISIBLE);
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
        initMiniPlayer();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopHeroAutoplay();
        releaseMiniPlayer();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        synopsisExecutor.shutdownNow();
    }

    // ---------------- Mini-reproductor de canal en vivo ----------------

    private androidx.media3.datasource.DataSource.Factory trustAllDataSourceFactory;

    /** Igual que en PlayerActivity: acepta certificados SSL autofirmados,
     *  comunes en servidores Xtream caseros, para que el mini-reproductor
     *  no falle en silencio al intentar reproducir. */
    private androidx.media3.datasource.DataSource.Factory getTrustAllDataSourceFactory() {
        if (trustAllDataSourceFactory != null) return trustAllDataSourceFactory;
        try {
            javax.net.ssl.TrustManager[] trustAllCerts = new javax.net.ssl.TrustManager[]{
                    new javax.net.ssl.X509TrustManager() {
                        public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) {}
                        public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) {}
                        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                            return new java.security.cert.X509Certificate[]{};
                        }
                    }
            };
            javax.net.ssl.SSLContext sslContext = javax.net.ssl.SSLContext.getInstance("SSL");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());

            okhttp3.OkHttpClient client = new okhttp3.OkHttpClient.Builder()
                    .sslSocketFactory(sslContext.getSocketFactory(),
                            (javax.net.ssl.X509TrustManager) trustAllCerts[0])
                    .hostnameVerifier((hostname, session) -> true)
                    .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                    .build();

            trustAllDataSourceFactory = new OkHttpDataSource.Factory(client);
        } catch (Exception e) {
            trustAllDataSourceFactory = new androidx.media3.datasource.DefaultHttpDataSource.Factory();
        }
        return trustAllDataSourceFactory;
    }

    private void initMiniPlayer() {
        boolean isPortrait = getResources().getConfiguration().orientation
                == android.content.res.Configuration.ORIENTATION_PORTRAIT;
        if (isPortrait) {
            miniPlayerContainer.setVisibility(View.GONE);
            return;
        }

        AppState state = AppState.get();
        if (state.liveChannels.isEmpty()) {
            miniPlayerContainer.setVisibility(View.GONE);
            return;
        }
        miniPlayerContainer.setVisibility(View.VISIBLE);

        miniPlayerChannel = pickMiniPlayerChannel(state);
        if (miniPlayerChannel == null) return;

        miniChannelName.setText(miniPlayerChannel.name);

        if (miniPlayer != null) miniPlayer.release();
        miniPlayer = new ExoPlayer.Builder(this)
                .setMediaSourceFactory(new DefaultMediaSourceFactory(getTrustAllDataSourceFactory()))
                .build();
        miniPlayerView.setPlayer(miniPlayer);

        androidx.media3.common.MediaItem mediaItem =
                androidx.media3.common.MediaItem.fromUri(miniPlayerChannel.url);
        miniPlayer.setMediaItem(mediaItem);
        miniPlayer.setVolume(miniPlayerMuted ? 0f : 1f);
        miniPlayer.prepare();
        miniPlayer.setPlayWhenReady(true);

        updateMuteIcon();
        loadMiniPlayerEpg(miniPlayerChannel);
    }

    /** Trae "qué está dando ahora" para el canal del mini-reproductor (1 sola llamada). */
    private void loadMiniPlayerEpg(MediaItem channel) {
        miniEpgNow.setVisibility(View.GONE);
        miniEpgProgressTrack.setVisibility(View.GONE);
        PlaylistConfig config = prefs.getPlaylistConfig();
        if (config == null || !PlaylistConfig.TYPE_XTREAM.equals(config.type)) return;

        synopsisExecutor.execute(() -> {
            XtreamClient client = new XtreamClient(config);
            List<XtreamClient.EpgProgram> programs = client.getShortEpg(channel.id);
            if (programs.isEmpty()) return;

            XtreamClient.EpgProgram now = programs.get(0);
            mainHandler.post(() -> {
                if (miniPlayerChannel == channel) {
                    miniEpgNow.setText("Ahora: " + now.title);
                    miniEpgNow.setVisibility(View.VISIBLE);

                    int percent = now.progressPercent();
                    if (percent >= 0) {
                        miniEpgProgressTrack.setVisibility(View.VISIBLE);
                        LinearLayout.LayoutParams params =
                                (LinearLayout.LayoutParams) miniEpgProgressFill.getLayoutParams();
                        params.weight = percent;
                        miniEpgProgressFill.setLayoutParams(params);
                    }
                }
            });
        });
    }

    /** Prioriza el último canal en vivo visto; si no hay ninguno, elige uno al azar. */
    private MediaItem pickMiniPlayerChannel(AppState state) {
        List<MediaItem> recentList = prefs.getRecentlyWatched();
        for (MediaItem recent : recentList) {
            if (MediaItem.LIVE.equals(recent.type)) {
                for (MediaItem live : state.liveChannels) {
                    if (live.favKey().equals(recent.favKey())) return live;
                }
            }
        }

        // DIAGNÓSTICO TEMPORAL: si cae aquí es porque no encontró ninguna
        // coincidencia. Este Toast nos dice exactamente por qué, para
        // saber si el problema es que "recientes" viene vacío, o que sí
        // trae datos pero no hace match contra los canales actuales.
        int liveCountInRecent = 0;
        for (MediaItem recent : recentList) {
            if (MediaItem.LIVE.equals(recent.type)) liveCountInRecent++;
        }
        android.widget.Toast.makeText(this,
                "DEBUG mini-player: recientes=" + recentList.size()
                        + " (en vivo entre ellos=" + liveCountInRecent + ")"
                        + " · canales totales=" + state.liveChannels.size(),
                android.widget.Toast.LENGTH_LONG).show();

        if (state.liveChannels.isEmpty()) return null;
        int randomIndex = (int) (Math.random() * state.liveChannels.size());
        return state.liveChannels.get(randomIndex);
    }

    private void releaseMiniPlayer() {
        if (miniPlayer != null) {
            miniPlayer.release();
            miniPlayer = null;
        }
        miniPlayerView.setPlayer(null);
    }

    private void toggleMiniPlayerMute() {
        miniPlayerMuted = !miniPlayerMuted;
        if (miniPlayer != null) miniPlayer.setVolume(miniPlayerMuted ? 0f : 1f);
        updateMuteIcon();
    }

    private void updateMuteIcon() {
        btnMiniMute.setImageResource(miniPlayerMuted ? R.drawable.ic_mute : R.drawable.ic_audio);
    }

    private void expandMiniPlayer() {
        if (miniPlayerChannel == null) return;
        AppState state = AppState.get();
        state.channelList = state.liveChannels;
        state.channelIdx = state.liveChannels.indexOf(miniPlayerChannel);

        Intent intent = new Intent(this, PlayerActivity.class);
        intent.putExtra("item", miniPlayerChannel);
        startActivity(intent);
    }

    private void bindViews() {
        layoutEmpty = findViewById(R.id.layout_empty);
        scrollContent = findViewById(R.id.scroll_content);
        rowFavoritesContainer = findViewById(R.id.row_favorites_container);
        rowContinueContainer = findViewById(R.id.row_continue_container);
        accountStatusText = findViewById(R.id.account_status_text);

        rowContinue = findViewById(R.id.row_continue);
        rowFavorites = findViewById(R.id.row_favorites);

        rowContinueSeriesContainer = findViewById(R.id.row_continue_series_container);
        rowContinueSeries = findViewById(R.id.row_continue_series);
        rowDiscoverContainer = findViewById(R.id.row_discover_container);
        rowDiscover = findViewById(R.id.row_discover);
        btnShuffleDiscover = findViewById(R.id.btn_shuffle_discover);
        rowFeaturedContainer = findViewById(R.id.row_featured_container);
        rowFeatured = findViewById(R.id.row_featured);
        tvFeaturedTitle = findViewById(R.id.tv_featured_title);

        dividerA = findViewById(R.id.divider_a);
        dividerB = findViewById(R.id.divider_b);
        dividerC = findViewById(R.id.divider_c);
        dividerD = findViewById(R.id.divider_d);
        dividerE = findViewById(R.id.divider_e);
        dividerF = findViewById(R.id.divider_f);

        homeDefaultContent = findViewById(R.id.home_default_content);
        globalSearchResults = findViewById(R.id.global_search_results);
        layoutLoading = findViewById(R.id.layout_loading);
        loadingEmoji = findViewById(R.id.loading_emoji);
        loadingText = findViewById(R.id.loading_text);

        inputSearch = findViewById(R.id.input_search);
        searchBarContainer = findViewById(R.id.search_bar_container);
        btnGoSettings = findViewById(R.id.btn_go_settings);

        heroPager = findViewById(R.id.hero_pager);
        heroDots = findViewById(R.id.hero_dots);

        miniPlayerContainer = findViewById(R.id.mini_player_container);
        miniPlayerView = findViewById(R.id.mini_player_view);
        btnMiniExpand = findViewById(R.id.btn_mini_expand);
        btnMiniMute = findViewById(R.id.btn_mini_mute);
        miniChannelName = findViewById(R.id.mini_channel_name);
        miniEpgNow = findViewById(R.id.mini_epg_now);
        miniEpgProgressTrack = findViewById(R.id.mini_epg_progress_track);
        miniEpgProgressFill = findViewById(R.id.mini_epg_progress_fill);

        btnOpenDrawer = findViewById(R.id.btn_open_drawer);
        btnSettings = findViewById(R.id.btn_settings);
        btnSearchHeader = findViewById(R.id.btn_search_header);
        drawerScrim = findViewById(R.id.drawer_scrim);
        drawerPanel = findViewById(R.id.drawer_panel);
        drawerItemHome = findViewById(R.id.drawer_item_home);
        drawerItemLive = findViewById(R.id.drawer_item_live);
        drawerItemMovies = findViewById(R.id.drawer_item_movies);
        drawerItemSeries = findViewById(R.id.drawer_item_series);
        drawerItemFavorites = findViewById(R.id.drawer_item_favorites);
        drawerItemHistory = findViewById(R.id.drawer_item_history);
        drawerItemSearch = findViewById(R.id.drawer_item_search);
        drawerItemSettings = findViewById(R.id.drawer_item_settings);

        continueHeroContainer = findViewById(R.id.continue_hero_container);
        continueHeroCard = findViewById(R.id.continue_hero_card);
        continueHeroThumb = findViewById(R.id.continue_hero_thumb);
        continueHeroBg = findViewById(R.id.continue_hero_bg);
        continueHeroTitle = findViewById(R.id.continue_hero_title);
        continueHeroSub = findViewById(R.id.continue_hero_sub);
        continueHeroQuality = findViewById(R.id.continue_hero_quality);
        continueHeroLiveBadge = findViewById(R.id.continue_hero_live_badge);
        continueHeroProgressFill = findViewById(R.id.continue_hero_progress_fill);

        chipFavorites = findViewById(R.id.chip_favorites);
        chipLive = findViewById(R.id.chip_live);
        chipMovies = findViewById(R.id.chip_movies);
        chipSeries = findViewById(R.id.chip_series);

        // 280dp en píxeles, para animar el panel exactamente su propio
        // ancho al abrir/cerrar (mismo valor que layout_width en el XML).
        drawerWidthPx = (int) (280 * getResources().getDisplayMetrics().density);
    }

    private void setupRows() {
        rowContinue.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        rowFavorites.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        rowContinueSeries.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        rowDiscover.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        rowFeatured.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));

        continueAdapter = new MediaCardAdapter(new ArrayList<>(), prefs, this::openItem);
        favAdapter = new MediaCardAdapter(new ArrayList<>(), prefs, this::openItem);
        continueSeriesAdapter = new MediaCardAdapter(new ArrayList<>(), prefs, this::openItem);
        discoverAdapter = new MediaCardAdapter(new ArrayList<>(), prefs, this::openItem);
        featuredAdapter = new MediaCardAdapter(new ArrayList<>(), prefs, this::openItem);

        rowContinue.setAdapter(continueAdapter);
        rowFavorites.setAdapter(favAdapter);
        rowContinueSeries.setAdapter(continueSeriesAdapter);
        rowDiscover.setAdapter(discoverAdapter);
        rowFeatured.setAdapter(featuredAdapter);
    }

    private void setupHeroPager() {
        // En vertical (celular) se usa la card simple: solo imagen + título,
        // tocar abre la pantalla de detalle. En horizontal (TV box) se usa
        // la card rica con sinopsis y botones Reproducir/Favorito directo
        // en el banner, ya que ahí se navega con control remoto y no hay
        // "toque" — conviene tener todo a la vista de una vez.
        boolean isPortrait = getResources().getConfiguration().orientation
                == android.content.res.Configuration.ORIENTATION_PORTRAIT;
        int heroLayoutResId = isPortrait ? R.layout.item_hero_slide : R.layout.item_hero_slide_tv;

        heroAdapter = new HeroSlideAdapter(heroItems, prefs, new HeroSlideAdapter.OnHeroAction() {
            @Override public void onPlay(MediaItem heroItem) {
                if (MediaItem.SERIES.equals(heroItem.type)) {
                    openItem(heroItem, -1);
                } else if (MediaItem.VOD.equals(heroItem.type)) {
                    Intent intent = new Intent(HomeActivity.this, PlayerActivity.class);
                    intent.putExtra("item", heroItem);
                    startActivity(intent);
                } else {
                    openItem(heroItem, -1);
                }
            }
            @Override public void onToggleFav(MediaItem item) { prefs.toggleFav(item.favKey()); }

            @Override public void onOpenDetail(MediaItem item) {
                openItem(item, -1);
            }
        }, heroLayoutResId);
        heroPager.setAdapter(heroAdapter);
        heroPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override public void onPageSelected(int position) {
                updateHeroDots(position);
            }
        });

        // Las flechas ◀▶ que superponíamos sobre el banner se quitaron: a
        // JOX3 le tapaban parte de la imagen y ya no hacían falta. Ahora el
        // propio indicador de posición (los puntos/rayitas debajo del
        // banner) es el que recibe el foco del control remoto, y las
        // flechas izquierda/derecha del D-pad cambian de card directamente
        // desde ahí — sin necesitar un botón visible aparte.
        heroDots.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != android.view.KeyEvent.ACTION_DOWN) return false;
            int count = heroAdapter.getItemCount();
            if (count == 0) return false;
            int current = heroPager.getCurrentItem();
            if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT) {
                heroPager.setCurrentItem((current - 1 + count) % count, true);
                return true;
            } else if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT) {
                heroPager.setCurrentItem((current + 1) % count, true);
                return true;
            }
            return false;
        });
    }

    private void startHeroAutoplay() {
        heroHandler.removeCallbacks(heroAutoplayRunnable);
        heroHandler.postDelayed(heroAutoplayRunnable, HERO_AUTOPLAY_MS);
    }

    private void stopHeroAutoplay() {
        heroHandler.removeCallbacks(heroAutoplayRunnable);
    }

    private Runnable pendingHomeSearch;

    private void setupSearch() {
        inputSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                // Misma razón que en ContentListActivity: la búsqueda
                // global recorre la cuenta activa Y hasta 5 cuentas
                // alternas completas en cada tecla, lo que se sentía
                // pegado al escribir con catálogos grandes. Se espera
                // 350ms de silencio antes de ejecutar la búsqueda real.
                if (pendingHomeSearch != null) mainHandler.removeCallbacks(pendingHomeSearch);
                currentSearchQuery = s.toString().trim();
                pendingHomeSearch = () -> {
                    applyFilters();
                    updateGlobalSearch();
                };
                mainHandler.postDelayed(pendingHomeSearch, 350);
            }
        });
    }

    /**
     * Mientras hay texto en el buscador, esconde el contenido normal del
     * Home (banner, chips, secciones) y muestra en su lugar los
     * resultados encontrados en TODAS las cuentas (la activa + hasta 5
     * alternas cargadas en segundo plano), agrupados en una fila por
     * cuenta. Al borrar el texto, vuelve a mostrarse el Home normal.
     */
    private void updateGlobalSearch() {
        if (currentSearchQuery.isEmpty()) {
            homeDefaultContent.setVisibility(View.VISIBLE);
            globalSearchResults.setVisibility(View.GONE);
            globalSearchResults.removeAllViews();
            return;
        }

        homeDefaultContent.setVisibility(View.GONE);
        globalSearchResults.setVisibility(View.VISIBLE);
        globalSearchResults.removeAllViews();

        String lowerQuery = currentSearchQuery.toLowerCase();
        LayoutInflater inflater = LayoutInflater.from(this);
        boolean anyResults = false;

        // 1) Cuenta ACTIVA primero (el catálogo que ya tiene cargado AppState).
        AppState state = AppState.get();
        List<MediaItem> activeResults = new ArrayList<>();
        activeResults.addAll(filterByName(state.liveChannels, lowerQuery));
        activeResults.addAll(filterByName(state.movies, lowerQuery));
        activeResults.addAll(filterByName(state.series, lowerQuery));
        if (!activeResults.isEmpty()) {
            String activeName = "Esta cuenta";
            com.jox3.tv.model.PlaylistConfig activeConfig = prefs.getPlaylistConfig();
            if (activeConfig != null && activeConfig.name != null && !activeConfig.name.isEmpty()) {
                activeName = activeConfig.name;
            }
            addSearchResultSection(inflater, activeName, activeResults);
            anyResults = true;
        }

        // 2) Cuentas ALTERNAS (las que ya hayan terminado de cargar en
        // segundo plano para cuando el usuario escribió la búsqueda).
        for (com.jox3.tv.util.AlternateCatalogCache.AccountCatalog catalog :
                com.jox3.tv.util.AlternateCatalogCache.get().getAllCatalogs()) {
            if (!catalog.loaded) continue;
            List<MediaItem> results = new ArrayList<>();
            results.addAll(filterByName(catalog.liveChannels, lowerQuery));
            results.addAll(filterByName(catalog.movies, lowerQuery));
            results.addAll(filterByName(catalog.series, lowerQuery));
            if (!results.isEmpty()) {
                addSearchResultSection(inflater, catalog.accountName, results);
                anyResults = true;
            }
        }

        if (!anyResults) {
            TextView empty = new TextView(this);
            empty.setText("Sin resultados en ninguna cuenta");
            empty.setTextColor(getResources().getColor(R.color.text_secondary));
            empty.setTextSize(14f);
            empty.setPadding(dp(18), dp(20), dp(18), dp(20));
            globalSearchResults.addView(empty);
        }
    }

    /** Una fila horizontal de resultados, con el nombre de la cuenta como título de sección. */
    private void addSearchResultSection(LayoutInflater inflater, String accountName, List<MediaItem> items) {
        View sectionView = inflater.inflate(R.layout.section_category, globalSearchResults, false);
        TextView title = sectionView.findViewById(R.id.section_title);
        RecyclerView recycler = sectionView.findViewById(R.id.section_recycler);

        title.setText("📡  " + accountName + "  (" + items.size() + ")");
        title.setOnClickListener(null);
        title.setClickable(false);
        title.setFocusable(false);

        recycler.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        recycler.setAdapter(new MediaCardAdapter(capList(items), prefs, this::openItem));

        globalSearchResults.addView(sectionView);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    private void setupButtons() {
        if (btnGoSettings != null) {
            btnGoSettings.setOnClickListener(v ->
                    startActivity(new Intent(this, SettingsActivity.class)));
        }

        View btnSeeAllContinue = findViewById(R.id.btn_see_all_continue);
        View btnSeeAllFavorites = findViewById(R.id.btn_see_all_favorites);

        // Todavía no existe una pantalla de "ver todo lo visto" ni un
        // historial completo aparte; por ahora estos dos son un aviso
        // simple. Cuando se decida cómo debe verse esa pantalla, se
        // conecta aquí.
        btnSeeAllContinue.setOnClickListener(v ->
                android.widget.Toast.makeText(this, "Próximamente", android.widget.Toast.LENGTH_SHORT).show());
        btnSeeAllFavorites.setOnClickListener(v -> openContentList("favorites"));

        btnMiniExpand.setOnClickListener(v -> expandMiniPlayer());
        miniPlayerContainer.setOnClickListener(v -> expandMiniPlayer());
        btnMiniMute.setOnClickListener(v -> toggleMiniPlayerMute());

        continueHeroCard.setOnClickListener(v -> {
            if (continueHeroItem != null) openItem(continueHeroItem, 0);
        });

        chipFavorites.setOnClickListener(v -> openContentList("favorites"));
        chipLive.setOnClickListener(v -> openContentList(MediaItem.LIVE));
        chipMovies.setOnClickListener(v -> openContentList(MediaItem.VOD));
        chipSeries.setOnClickListener(v -> openContentList(MediaItem.SERIES));

        btnShuffleDiscover.setOnClickListener(v -> bindDiscoverRow());

        // ---- Panel lateral (drawer) ----
        btnOpenDrawer.setOnClickListener(v -> openDrawer());
        btnSettings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));

        btnSearchHeader.setOnClickListener(v -> {
            searchBarContainer.setVisibility(View.VISIBLE);
            inputSearch.requestFocus();
            android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager)
                    getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(inputSearch, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
        });
        drawerScrim.setOnClickListener(v -> closeDrawer());

        drawerItemHome.setOnClickListener(v -> closeDrawer());
        drawerItemLive.setOnClickListener(v -> { closeDrawer(); openContentList(MediaItem.LIVE); });
        drawerItemMovies.setOnClickListener(v -> { closeDrawer(); openContentList(MediaItem.VOD); });
        drawerItemSeries.setOnClickListener(v -> { closeDrawer(); openContentList(MediaItem.SERIES); });
        drawerItemFavorites.setOnClickListener(v -> { closeDrawer(); openContentList("favorites"); });
        // "Historial" todavía no tiene pantalla propia (JOX3 dijo que lo
        // iba a pensar más). Por ahora solo avisa; está listo para
        // conectarse en cuanto se decida qué debe mostrar.
        drawerItemHistory.setOnClickListener(v -> {
            closeDrawer();
            android.widget.Toast.makeText(this, "Próximamente", android.widget.Toast.LENGTH_SHORT).show();
        });
        drawerItemSearch.setOnClickListener(v -> {
            closeDrawer();
            searchBarContainer.setVisibility(View.VISIBLE);
            inputSearch.requestFocus();
        });
        drawerItemSettings.setOnClickListener(v -> {
            closeDrawer();
            startActivity(new Intent(this, SettingsActivity.class));
        });
    }

    /** Abre el panel lateral animando su posición y mostrando el scrim oscuro. */
    private void openDrawer() {
        drawerScrim.setVisibility(View.VISIBLE);
        drawerScrim.setAlpha(0f);
        drawerScrim.animate().alpha(1f).setDuration(220).start();

        drawerPanel.setVisibility(View.VISIBLE);
        drawerPanel.animate()
                .translationX(0f)
                .setDuration(220)
                .start();
    }

    /** Cierra el panel lateral devolviéndolo fuera de pantalla. */
    private void closeDrawer() {
        drawerScrim.animate().alpha(0f).setDuration(200)
                .withEndAction(() -> drawerScrim.setVisibility(View.GONE))
                .start();
        drawerPanel.animate()
                .translationX(-drawerWidthPx)
                .setDuration(200)
                .withEndAction(() -> drawerPanel.setVisibility(View.GONE))
                .start();
    }

    /** Permite que el botón "atrás" cierre el panel o la búsqueda en vez de salir de la app. */
    @Override
    public void onBackPressed() {
        if (drawerPanel.getVisibility() == View.VISIBLE) {
            closeDrawer();
            return;
        }
        if (searchBarContainer.getVisibility() == View.VISIBLE) {
            searchBarContainer.setVisibility(View.GONE);
            inputSearch.setText("");
            return;
        }
        super.onBackPressed();
    }

    private void openContentList(String type) {
        Intent intent = new Intent(this, ContentListActivity.class);
        intent.putExtra(ContentListActivity.EXTRA_TYPE, type);
        startActivity(intent);
    }

    private boolean autoLoadAttempted = false;

    private void refreshContent() {
        AppState state = AppState.get();
        boolean hasData = !state.liveChannels.isEmpty()
                || !state.movies.isEmpty()
                || !state.series.isEmpty();

        if (!hasData) {
            PlaylistConfig config = prefs.getPlaylistConfig();
            if (config != null && !autoLoadAttempted) {
                autoLoadAttempted = true;
                autoLoadActiveAccount(config);
                return;
            }
        }

        layoutEmpty.setVisibility(hasData ? View.GONE : View.VISIBLE);
        scrollContent.setVisibility(hasData ? View.VISIBLE : View.GONE);

        if (!hasData) return;

        List<MediaItem> continueWatching = prefs.getRecentlyWatched();
        List<MediaItem> continueMoviesList = continueMovies();
        rowContinueContainer.setVisibility(continueMoviesList.isEmpty() ? View.GONE : View.VISIBLE);
        continueAdapter.updateData(capList(continueMoviesList));
        bindContinueHero(continueWatching);
        updateAccountStatus();

        List<MediaItem> favorites = collectFavorites(state);
        rowFavoritesContainer.setVisibility(favorites.isEmpty() ? View.GONE : View.VISIBLE);
        favAdapter.updateData(capList(favorites));

        bindContinueSeriesRow(continueWatching);
        bindDiscoverRow();
        bindFeaturedRow(state);

        setupHero(state);
        applyFilters();
        updateSectionDividers();
    }

    /**
     * "Seguir viendo series": mismo origen de datos que "Vistos Reciente"
     * (prefs.getRecentlyWatched()), pero filtrado solo a series — y le
     * agrega su propio badge T{temporada} E{episodio} en MediaCardAdapter.
     */
    private void bindContinueSeriesRow(List<MediaItem> continueWatching) {
        List<MediaItem> series = new ArrayList<>();
        for (MediaItem item : continueWatching) {
            if (MediaItem.SERIES.equals(item.type)) series.add(item);
        }
        rowContinueSeriesContainer.setVisibility(series.isEmpty() ? View.GONE : View.VISIBLE);
        continueSeriesAdapter.updateData(capList(series));
    }

    /**
     * "Descubre algo nuevo": random de películas/series que NO están en
     * "Vistos Reciente" todavía. Se puede regenerar con el botón 🔀 sin
     * tener que recargar toda la cuenta otra vez.
     */
    private void bindDiscoverRow() {
        AppState state = AppState.get();
        java.util.Set<String> watchedIds = new java.util.HashSet<>();
        for (MediaItem item : prefs.getRecentlyWatched()) watchedIds.add(item.id);

        List<MediaItem> pool = new ArrayList<>();
        for (MediaItem item : state.movies) {
            if (!watchedIds.contains(item.id) && item.logoUrl != null && !item.logoUrl.isEmpty()
                    && !prefs.isAdultCategory(item.category)) {
                pool.add(item);
            }
        }
        for (MediaItem item : state.series) {
            if (!watchedIds.contains(item.id) && item.logoUrl != null && !item.logoUrl.isEmpty()
                    && !prefs.isAdultCategory(item.category)) {
                pool.add(item);
            }
        }
        Collections.shuffle(pool);

        List<MediaItem> selected = pool.size() > MAX_ITEMS_PER_ROW
                ? pool.subList(0, MAX_ITEMS_PER_ROW) : pool;
        rowDiscoverContainer.setVisibility(selected.isEmpty() ? View.GONE : View.VISIBLE);
        discoverAdapter.updateData(new ArrayList<>(selected));
    }

    /**
     * Fila fija de UNA categoría destacada: se elige automáticamente la
     * categoría con más ítems entre tus películas y series (sin inventar
     * ningún nombre — usa el nombre real tal como viene en tu lista).
     */
    private void bindFeaturedRow(AppState state) {
        java.util.Map<String, Integer> countByCategory = new java.util.HashMap<>();
        java.util.Map<String, List<MediaItem>> itemsByCategory = new java.util.HashMap<>();

        for (MediaItem item : state.movies) addToCategory(countByCategory, itemsByCategory, item);
        for (MediaItem item : state.series) addToCategory(countByCategory, itemsByCategory, item);

        String bestCategory = null;
        int bestCount = 0;
        for (java.util.Map.Entry<String, Integer> entry : countByCategory.entrySet()) {
            if (entry.getValue() > bestCount) {
                bestCount = entry.getValue();
                bestCategory = entry.getKey();
            }
        }

        if (bestCategory == null || bestCount < 4) {
            // Si ninguna categoría tiene al menos 4 ítems con miniatura,
            // no vale la pena mostrar esta fila (se vería muy vacía).
            rowFeaturedContainer.setVisibility(View.GONE);
            return;
        }

        List<MediaItem> items = new ArrayList<>(itemsByCategory.get(bestCategory));
        Collections.shuffle(items);
        tvFeaturedTitle.setText("⭐  " + bestCategory);
        featuredAdapter.updateData(capList(items));
        rowFeaturedContainer.setVisibility(View.VISIBLE);
    }

    private void addToCategory(java.util.Map<String, Integer> countByCategory,
                                java.util.Map<String, List<MediaItem>> itemsByCategory,
                                MediaItem item) {
        if (item.category == null || item.category.trim().isEmpty()) return;
        if (item.logoUrl == null || item.logoUrl.isEmpty()) return;
        if (prefs.isAdultCategory(item.category)) return;
        countByCategory.merge(item.category, 1, Integer::sum);
        itemsByCategory.computeIfAbsent(item.category, k -> new ArrayList<>()).add(item);
    }

    /**
     * Cada separador se muestra solo si la sección que viene justo
     * después de él también está visible — así nunca queda una línea
     * "huérfana" antes de una sección oculta.
     */
    private void updateSectionDividers() {
        dividerA.setVisibility(rowContinueSeriesContainer.getVisibility());
        dividerB.setVisibility(rowContinueContainer.getVisibility());
        dividerC.setVisibility(continueHeroContainer.getVisibility());
        dividerD.setVisibility(rowFavoritesContainer.getVisibility());
        dividerE.setVisibility(rowDiscoverContainer.getVisibility());
        dividerF.setVisibility(rowFeaturedContainer.getVisibility());
    }

    /**
     * Texto discreto de estado de cuenta debajo del header. A propósito
     * NO lleva caja, ícono grande ni color de marca — solo una línea chica
     * en gris, para no competir visualmente con el resto del Home. Si la
     * cuenta es M3U (sin fecha de vencimiento) o no hay cuenta, se oculta.
     */
    private void updateAccountStatus() {
        com.jox3.tv.model.PlaylistConfig config = prefs.getPlaylistConfig();
        if (config == null || config.expDateEpoch <= 0) {
            accountStatusText.setVisibility(View.GONE);
            return;
        }
        long daysLeft = (config.expDateEpoch * 1000L - System.currentTimeMillis())
                / (1000L * 60 * 60 * 24);
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy", new Locale("es"));
        String dateText = sdf.format(new Date(config.expDateEpoch * 1000L));

        String name = config.name != null && !config.name.isEmpty() ? config.name : "Lista activa";
        String text = daysLeft >= 0
                ? name + " · vence " + dateText + " (" + daysLeft + " días)"
                : name + " · vencida desde " + dateText;

        accountStatusText.setText(text);
        accountStatusText.setVisibility(View.VISIBLE);
    }

    /**
     * Llena la card grande de "Última reproducción" con el ítem visto más
     * recientemente (el primero de la misma lista que alimenta "Vistos
     * Reciente"). Si no hay nada visto todavía, oculta la card completa.
     */
    /**
     * Llena la card grande de "Última reproducción" con el canal EN VIVO
     * visto más recientemente. Películas y series YA NO entran aquí: ya
     * tienen su propio espacio ("Continúa tus películas" / "Seguir viendo
     * series"), así que mostrarlas también acá era redundante.
     */
    private void bindContinueHero(List<MediaItem> continueWatching) {
        MediaItem item = null;
        for (MediaItem candidate : continueWatching) {
            if (MediaItem.LIVE.equals(candidate.type)) {
                item = candidate;
                break;
            }
        }
        if (item == null) {
            continueHeroContainer.setVisibility(View.GONE);
            return;
        }
        continueHeroContainer.setVisibility(View.VISIBLE);
        continueHeroItem = item;

        continueHeroTitle.setText(item.name);
        continueHeroLiveBadge.setVisibility(View.VISIBLE);

        // Prioridad: 1) calidad REAL medida por el reproductor la última
        // vez que se vio este canal (mucho más confiable); 2) si todavía
        // nunca se reprodujo, se intenta adivinar por el nombre o la
        // categoría como respaldo.
        String quality = prefs.getDetectedQuality(item.id);
        if (quality == null) quality = MediaCardAdapter.detectQuality(item.name);
        if (quality == null) quality = MediaCardAdapter.detectQuality(item.category);
        if (quality != null) {
            continueHeroQuality.setText(quality);
            continueHeroQuality.setVisibility(View.VISIBLE);
        } else {
            continueHeroQuality.setVisibility(View.GONE);
        }

        if (item.logoUrl != null && !item.logoUrl.isEmpty()) {
            com.bumptech.glide.Glide.with(continueHeroThumb.getContext())
                    .load(item.logoUrl)
                    .centerCrop()
                    .into(continueHeroThumb);

            // Fondo difuminado (vidrio esmerilado): misma imagen, pero con
            // blur real de jp.wasabeef:glide-transformations. Esto mismo
            // se puede reutilizar a futuro en cualquier otra pantalla que
            // necesite este efecto.
            com.bumptech.glide.Glide.with(continueHeroBg.getContext())
                    .load(item.logoUrl)
                    .apply(new com.bumptech.glide.request.RequestOptions()
                            .transform(new jp.wasabeef.glide.transformations.BlurTransformation(25, 3)))
                    .into(continueHeroBg);
        } else {
            continueHeroThumb.setImageDrawable(null);
            continueHeroBg.setImageDrawable(null);
        }

        // Siempre es un canal en vivo a esta altura (el filtro de arriba ya
        // se encargó de eso). La posición/duración guardada de la última
        // vez que se vio no sirve de nada para un canal (pudo haber sido
        // hace horas o un día completo, la programación ya cambió) — en vez
        // de eso, se consulta la guía EPG EN VIVO ahora mismo, igual que ya
        // se hace para el mini-reproductor del Home.
        continueHeroSub.setText(item.category != null ? item.category.toUpperCase() : "");
        loadContinueHeroLiveEpg(item);
    }

    /**
     * Trae "qué está dando ahora" para el canal de la card de "Última
     * reproducción", igual que loadMiniPlayerEpg() pero para esta card.
     * Se hace en tiempo real cada vez que se refresca el Home: si la
     * última vez que viste este canal fue hace un buen rato, la guía que
     * se muestra es la de AHORA, no la de ese momento.
     */
    /**
     * Mismo concepto que en DetailActivity/PlayerActivity: si el canal
     * guardado en "recientes" vino de una cuenta ALTERNA (se reprodujo
     * desde un resultado del buscador global), su EPG debe pedirse a ESE
     * servidor, no al de la cuenta activa.
     */
    private PlaylistConfig resolveAccountForItem(MediaItem mediaItem) {
        if (mediaItem.sourceAccountId == null) return prefs.getPlaylistConfig();
        for (PlaylistConfig account : prefs.getAccounts()) {
            if (account.id.equals(mediaItem.sourceAccountId)) return account;
        }
        return prefs.getPlaylistConfig();
    }

    private void loadContinueHeroLiveEpg(MediaItem channel) {
        setContinueHeroProgress(0);
        PlaylistConfig config = resolveAccountForItem(channel);
        if (config == null || !PlaylistConfig.TYPE_XTREAM.equals(config.type)) return;

        synopsisExecutor.execute(() -> {
            XtreamClient client = new XtreamClient(config);
            List<XtreamClient.EpgProgram> programs = client.getShortEpg(channel.id);
            if (programs.isEmpty()) return;

            XtreamClient.EpgProgram now = programs.get(0);
            mainHandler.post(() -> {
                // Si para cuando termina de cargar la guía el usuario ya
                // recargó el Home y ahora el ítem más reciente es otro,
                // no pisamos el texto de un canal que ya no es el que se
                // está mostrando.
                List<MediaItem> current = prefs.getRecentlyWatched();
                if (current.isEmpty() || !current.get(0).id.equals(channel.id)) return;

                continueHeroSub.setText("▶ Ahora: " + now.title);
                int percent = now.progressPercent();
                setContinueHeroProgress(Math.max(percent, 0));
            });
        });
    }

    private void setContinueHeroProgress(int percent) {
        LinearLayout.LayoutParams fillParams =
                (LinearLayout.LayoutParams) continueHeroProgressFill.getLayoutParams();
        fillParams.weight = percent;
        continueHeroProgressFill.setLayoutParams(fillParams);
    }

    /**
     * Si hay una cuenta guardada como activa pero la memoria está vacía
     * (la app se cerró y se volvió a abrir), recarga sus datos en
     * segundo plano automáticamente, sin que el usuario tenga que volver
     * a Ajustes a "elegirla" de nuevo.
     */
    private void autoLoadActiveAccount(PlaylistConfig config) {
        layoutEmpty.setVisibility(View.GONE);
        scrollContent.setVisibility(View.GONE);
        showLoadingScreen();

        synopsisExecutor.execute(() -> {
            try {
                AppState state = AppState.get();
                if (PlaylistConfig.TYPE_XTREAM.equals(config.type)) {
                    XtreamClient client = new XtreamClient(config);
                    state.liveChannels = client.getLiveChannels();
                    state.movies = client.getMovies();
                    state.series = client.getSeries();
                } else if (config.m3uUrl != null) {
                    URL url = new URL(config.m3uUrl);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(15000);
                    conn.setReadTimeout(20000);
                    List<MediaItem> all;
                    try (java.io.InputStream input = conn.getInputStream()) {
                        all = com.jox3.tv.data.M3uParser.parse(input);
                    } finally {
                        conn.disconnect();
                    }
                    state.liveChannels.clear();
                    state.movies.clear();
                    state.series.clear();
                    for (MediaItem mi : all) {
                        if (MediaItem.VOD.equals(mi.type)) state.movies.add(mi);
                        else if (MediaItem.SERIES.equals(mi.type)) state.series.add(mi);
                        else state.liveChannels.add(mi);
                    }
                }
                mainHandler.post(() -> {
                    hideLoadingScreen();
                    refreshContent();
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    hideLoadingScreen();
                    layoutEmpty.setVisibility(View.VISIBLE);
                    scrollContent.setVisibility(View.GONE);
                    android.widget.Toast.makeText(this,
                            "No se pudo cargar tu cuenta automáticamente: " + e.getMessage(),
                            android.widget.Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    /**
     * Pantalla de carga con un emoji que va cambiando y un texto que
     * rota, para que esos segundos de descarga inicial (antes en blanco
     * total) se sientan como que la app está trabajando, no congelada.
     */
    private static final String[] LOADING_EMOJIS = {"📡", "📺", "🎬", "📡"};
    private static final String[] LOADING_TEXTS = {
            "Cargando tu lista...",
            "Sintonizando canales...",
            "Organizando tu catálogo...",
            "Ya casi está..."
    };

    private void showLoadingScreen() {
        layoutLoading.setVisibility(View.VISIBLE);
        loadingAnimationStep = 0;
        loadingAnimationRunnable = new Runnable() {
            @Override public void run() {
                int i = loadingAnimationStep % LOADING_EMOJIS.length;
                loadingEmoji.setText(LOADING_EMOJIS[i]);
                loadingText.setText(LOADING_TEXTS[i]);
                loadingAnimationStep++;
                mainHandler.postDelayed(this, 1400);
            }
        };
        mainHandler.post(loadingAnimationRunnable);
    }

    private void hideLoadingScreen() {
        layoutLoading.setVisibility(View.GONE);
        if (loadingAnimationRunnable != null) {
            mainHandler.removeCallbacks(loadingAnimationRunnable);
            loadingAnimationRunnable = null;
        }
    }

    /** Construye hasta 5 recomendaciones para el carrusel del banner,
     *  mezclando películas, series Y canales en vivo (JOX3 pidió que el
     *  banner también recomiende series y canales, no solo películas). */
    private void setupHero(AppState state) {
        // Solo entran al banner los ítems que sí tienen miniatura/poster.
        // Sin esto, algunos elementos (sobre todo recomendaciones sin
        // imagen propia del proveedor) aparecían en el carrusel con la
        // card completamente vacía/gris.
        List<MediaItem> moviePool = new ArrayList<>();
        List<MediaItem> seriesPool = new ArrayList<>();
        List<MediaItem> livePool = new ArrayList<>();
        for (MediaItem item : state.movies)
            if (item.logoUrl != null && !item.logoUrl.isEmpty() && !prefs.isAdultCategory(item.category))
                moviePool.add(item);
        for (MediaItem item : state.series)
            if (item.logoUrl != null && !item.logoUrl.isEmpty() && !prefs.isAdultCategory(item.category))
                seriesPool.add(item);
        for (MediaItem item : state.liveChannels)
            if (item.logoUrl != null && !item.logoUrl.isEmpty() && !prefs.isAdultCategory(item.category))
                livePool.add(item);

        Collections.shuffle(moviePool);
        Collections.shuffle(seriesPool);
        Collections.shuffle(livePool);

        // Reparto pareja entre los 3 tipos (más o menos un tercio cada
        // uno); si algún tipo no tiene suficiente, los otros rellenan el
        // espacio que sobra para siempre completar HERO_SLIDE_COUNT.
        List<MediaItem> selected = new ArrayList<>();
        int perType = (HERO_SLIDE_COUNT + 2) / 3;
        selected.addAll(moviePool.subList(0, Math.min(perType, moviePool.size())));
        selected.addAll(seriesPool.subList(0, Math.min(perType, seriesPool.size())));
        selected.addAll(livePool.subList(0, Math.min(perType, livePool.size())));

        if (selected.size() < HERO_SLIDE_COUNT) {
            List<MediaItem> leftovers = new ArrayList<>();
            leftovers.addAll(moviePool.subList(Math.min(perType, moviePool.size()), moviePool.size()));
            leftovers.addAll(seriesPool.subList(Math.min(perType, seriesPool.size()), seriesPool.size()));
            leftovers.addAll(livePool.subList(Math.min(perType, livePool.size()), livePool.size()));
            Collections.shuffle(leftovers);
            for (MediaItem item : leftovers) {
                if (selected.size() >= HERO_SLIDE_COUNT) break;
                selected.add(item);
            }
        }

        if (selected.isEmpty()) {
            heroPager.setVisibility(View.GONE);
            heroDots.setVisibility(View.GONE);
            stopHeroAutoplay();
            return;
        }

        Collections.shuffle(selected);
        if (selected.size() > HERO_SLIDE_COUNT) {
            selected = selected.subList(0, HERO_SLIDE_COUNT);
        }

        heroItems.clear();
        heroItems.addAll(selected);
        heroAdapter.notifyDataSetChanged();

        heroPager.setVisibility(View.VISIBLE);
        heroPager.setCurrentItem(0, false);
        buildHeroDots();
        updateHeroDots(0);
        startHeroAutoplay();
        loadHeroSynopsisInBackground();
    }

    /**
     * Trae la sinopsis real de cada item del carrusel desde la API de Xtream.
     * Son solo 5 llamadas como máximo (nunca para todo el catálogo), así que
     * el costo es bajo. Si la lista activa es M3U (sin esa API), no hace nada
     * y se queda con el texto de respaldo.
     */
    private void loadHeroSynopsisInBackground() {
        PlaylistConfig config = prefs.getPlaylistConfig();
        if (config == null || !PlaylistConfig.TYPE_XTREAM.equals(config.type)) return;

        List<MediaItem> snapshot = new ArrayList<>(heroItems);
        synopsisExecutor.execute(() -> {
            XtreamClient client = new XtreamClient(config);
            for (int i = 0; i < snapshot.size(); i++) {
                MediaItem item = snapshot.get(i);
                String synopsis = null;
                try {
                    if (MediaItem.VOD.equals(item.type)) {
                        synopsis = client.getVodSynopsis(item.id);
                    } else if (MediaItem.SERIES.equals(item.type)) {
                        synopsis = client.getSeriesSynopsis(item.seriesId != null ? item.seriesId : item.id);
                    }
                } catch (Exception ignored) {
                    // si falla una, seguimos con las demás sin interrumpir
                }

                if (synopsis != null) {
                    item.synopsis = synopsis;
                    int position = i;
                    mainHandler.post(() -> heroAdapter.notifySynopsisLoaded(position));
                }
            }
        });
    }

    private void buildHeroDots() {
        heroDots.removeAllViews();
        heroDots.setVisibility(heroItems.size() > 1 ? View.VISIBLE : View.GONE);

        float density = getResources().getDisplayMetrics().density;
        // El contenedor de cada punto es un poco más alto que el "punto"
        // visible (7dp vs 3dp): ese espacio de más es justamente donde
        // aparece el halo de resplandor cuando el punto está activo, sin
        // tener que cambiarle el tamaño al View cada vez (así se evitan
        // saltos al cambiar de página).
        int widthPx = (int) (22 * density);
        int heightPx = (int) (7 * density);

        for (int i = 0; i < heroItems.size(); i++) {
            View dot = new View(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(widthPx, heightPx);
            params.setMarginEnd((int) (5 * density));
            dot.setLayoutParams(params);
            dot.setBackgroundResource(R.drawable.dot_inactive);
            heroDots.addView(dot);
        }
    }

    private void updateHeroDots(int activeIndex) {
        for (int i = 0; i < heroDots.getChildCount(); i++) {
            View dot = heroDots.getChildAt(i);
            dot.setBackgroundResource(i == activeIndex ? R.drawable.dot_active_glow : R.drawable.dot_inactive);
        }
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

    /**
     * La búsqueda en el Home ahora es simple: como ya no hay filas de TV en
     * vivo/Películas/Series aquí (se movieron a sus propias pantallas, que
     * ya tienen su propia búsqueda), esta solo filtra por nombre dentro de
     * "Continúa tus películas" y "Favoritos".
     */
    private void applyFilters() {
        AppState state = AppState.get();
        String lowerQuery = currentSearchQuery.toLowerCase();

        if (lowerQuery.isEmpty()) {
            continueAdapter.updateData(capList(continueMovies()));
            favAdapter.updateData(capList(collectFavorites(state)));
        } else {
            continueAdapter.updateData(capList(filterByName(continueMovies(), lowerQuery)));
            favAdapter.updateData(capList(filterByName(collectFavorites(state), lowerQuery)));
        }
    }

    /** Solo películas con avance, para la fila "Continúa tus películas". */
    private List<MediaItem> continueMovies() {
        List<MediaItem> movies = new ArrayList<>();
        for (MediaItem item : prefs.getRecentlyWatched()) {
            if (MediaItem.VOD.equals(item.type)) movies.add(item);
        }
        return movies;
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
        AppState state = AppState.get();

        if (MediaItem.LIVE.equals(item.type)) {
            int idx = state.liveChannels.indexOf(item);
            if (idx >= 0) {
                // Canal de la cuenta activa: navegación anterior/siguiente
                // funciona normal dentro de toda la lista de canales.
                state.channelList = state.liveChannels;
                state.channelIdx = idx;
            } else {
                // Canal encontrado por el buscador global en una cuenta
                // ALTERNA: no pertenece a state.liveChannels, así que se
                // arma una lista de un solo elemento. El canal reproduce
                // perfecto igual (su URL ya trae su propio servidor y
                // credenciales), pero los botones anterior/siguiente del
                // reproductor no tendrían a qué otro canal saltar.
                state.channelList = java.util.Collections.singletonList(item);
                state.channelIdx = 0;
            }

            Intent intent = new Intent(this, PlayerActivity.class);
            intent.putExtra("item", item);
            startActivity(intent);
            return;
        }

        // Películas y series abren primero la pantalla de detalle
        // (sinopsis, similares, o selección de temporada/episodio)
        Intent intent = new Intent(this, com.jox3.tv.ui.home.DetailActivity.class);
        intent.putExtra("item", item);
        startActivity(intent);
    }
}
