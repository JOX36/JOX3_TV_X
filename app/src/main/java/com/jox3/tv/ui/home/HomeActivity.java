package com.jox3.tv.ui.home;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HomeActivity extends AppCompatActivity {

    private static final int MAX_ITEMS_PER_ROW = 12;
    private static final int HERO_SLIDE_COUNT = 5;
    private static final long HERO_AUTOPLAY_MS = 5000;

    private LinearLayout layoutEmpty, rowFavoritesContainer, rowContinueContainer;
    private View scrollContent;
    private RecyclerView rowContinue, rowFavorites;
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
    private View drawerScrim, drawerPanel, btnOpenDrawer;
    private View drawerItemHome, drawerItemLive, drawerItemMovies, drawerItemSeries,
            drawerItemFavorites, drawerItemHistory, drawerItemSearch, drawerItemSettings;
    private int drawerWidthPx;

    // ---- Card grande "Última reproducción" ----
    private View continueHeroContainer, continueHeroCard;
    private ImageView continueHeroThumb;
    private TextView continueHeroTitle, continueHeroSub;
    private View continueHeroProgressFill;
    private View quicknavLive, quicknavMovies, quicknavSeries;

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

        rowContinue = findViewById(R.id.row_continue);
        rowFavorites = findViewById(R.id.row_favorites);

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
        continueHeroTitle = findViewById(R.id.continue_hero_title);
        continueHeroSub = findViewById(R.id.continue_hero_sub);
        continueHeroProgressFill = findViewById(R.id.continue_hero_progress_fill);

        quicknavLive = findViewById(R.id.quicknav_live);
        quicknavMovies = findViewById(R.id.quicknav_movies);
        quicknavSeries = findViewById(R.id.quicknav_series);

        // 280dp en píxeles, para animar el panel exactamente su propio
        // ancho al abrir/cerrar (mismo valor que layout_width en el XML).
        drawerWidthPx = (int) (280 * getResources().getDisplayMetrics().density);
    }

    private void setupRows() {
        rowContinue.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        rowFavorites.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));

        continueAdapter = new MediaCardAdapter(new ArrayList<>(), prefs, this::openItem);
        favAdapter = new MediaCardAdapter(new ArrayList<>(), prefs, this::openItem);

        rowContinue.setAdapter(continueAdapter);
        rowFavorites.setAdapter(favAdapter);
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
            List<MediaItem> recent = prefs.getRecentlyWatched();
            if (!recent.isEmpty()) openItem(recent.get(0), 0);
        });

        quicknavLive.setOnClickListener(v -> openContentList(MediaItem.LIVE));
        quicknavMovies.setOnClickListener(v -> openContentList(MediaItem.VOD));
        quicknavSeries.setOnClickListener(v -> openContentList(MediaItem.SERIES));

        // ---- Panel lateral (drawer) ----
        btnOpenDrawer.setOnClickListener(v -> openDrawer());
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
        rowContinueContainer.setVisibility(continueWatching.isEmpty() ? View.GONE : View.VISIBLE);
        continueAdapter.updateData(capList(continueWatching));
        bindContinueHero(continueWatching);

        List<MediaItem> favorites = collectFavorites(state);
        rowFavoritesContainer.setVisibility(favorites.isEmpty() ? View.GONE : View.VISIBLE);
        favAdapter.updateData(capList(favorites));

        setupHero(state);
        applyFilters();
    }

    /**
     * Llena la card grande de "Última reproducción" con el ítem visto más
     * recientemente (el primero de la misma lista que alimenta "Vistos
     * Reciente"). Si no hay nada visto todavía, oculta la card completa.
     */
    private void bindContinueHero(List<MediaItem> continueWatching) {
        if (continueWatching.isEmpty()) {
            continueHeroContainer.setVisibility(View.GONE);
            return;
        }
        continueHeroContainer.setVisibility(View.VISIBLE);
        MediaItem item = continueWatching.get(0);

        continueHeroTitle.setText(item.name);
        continueHeroSub.setText(item.category != null ? item.category.toUpperCase() : "");

        if (item.logoUrl != null && !item.logoUrl.isEmpty()) {
            com.bumptech.glide.Glide.with(continueHeroThumb.getContext())
                    .load(item.logoUrl)
                    .centerCrop()
                    .into(continueHeroThumb);
        } else {
            continueHeroThumb.setImageDrawable(null);
        }

        long pos = prefs.getPos(item.id);
        long dur = prefs.getDur(item.id);
        int percent = dur > 0 ? (int) Math.min(100, Math.max(0, (pos * 100) / dur)) : 0;
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
        // (se podría mostrar un spinner de carga aquí si se desea más adelante)

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
                mainHandler.post(this::refreshContent);
            } catch (Exception e) {
                mainHandler.post(() -> {
                    layoutEmpty.setVisibility(View.VISIBLE);
                    scrollContent.setVisibility(View.GONE);
                    android.widget.Toast.makeText(this,
                            "No se pudo cargar tu cuenta automáticamente: " + e.getMessage(),
                            android.widget.Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    /** Construye hasta 5 recomendaciones para el carrusel del banner: prioriza
     *  películas y series (contenido más "recomendable"), y si no hay
     *  suficientes, completa con canales en vivo. */
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
     * "Vistos Reciente" y "Favoritos".
     */
    private void applyFilters() {
        AppState state = AppState.get();
        String lowerQuery = currentSearchQuery.toLowerCase();

        if (lowerQuery.isEmpty()) {
            continueAdapter.updateData(capList(prefs.getRecentlyWatched()));
            favAdapter.updateData(capList(collectFavorites(state)));
        } else {
            continueAdapter.updateData(capList(filterByName(prefs.getRecentlyWatched(), lowerQuery)));
            favAdapter.updateData(capList(filterByName(collectFavorites(state), lowerQuery)));
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
        AppState state = AppState.get();

        if (MediaItem.LIVE.equals(item.type)) {
            state.channelList = state.liveChannels;
            state.channelIdx = state.liveChannels.indexOf(item);

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
