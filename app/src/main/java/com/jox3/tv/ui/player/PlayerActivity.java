package com.jox3.tv.ui.player;

import android.annotation.SuppressLint;
import android.app.PictureInPictureParams;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Rational;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.C;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.TrackGroup;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.Tracks;
import androidx.media3.common.VideoSize;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;

import com.jox3.tv.R;
import com.jox3.tv.model.MediaItem;
import com.jox3.tv.util.AppPrefs;
import com.jox3.tv.util.AppState;

import java.util.ArrayList;
import java.util.List;

public class PlayerActivity extends AppCompatActivity {

    public static volatile boolean requestClose = false;
    public static final String EXTRA_URL = "extra_url";
    public static final String EXTRA_TITLE = "extra_title";
    public static final String EXTRA_TYPE = "extra_type";

    private ExoPlayer player;
    private PlayerView playerView;
    private LinearLayout topBar, bottomBar;
    private LinearLayout layoutSeek, layoutLiveBtns, layoutVodBtns;
    private TextView tvName, tvResolution, tvStatus;
    private ImageView btnBack, btnFav;

    private Button btnPrev, btnNext, btnPipLive, btnStop;

    private Button btnPlayPause, btnRewind, btnForward;
    private Button btnAudio, btnSubs, btnPip, btnStopVod;
    private Button btnSpeed, btnLock, btnPrevEp, btnNextEp;

    private SeekBar seekBar;
    private TextView tvPosition, tvDuration;

    private LinearLayout gestureIndicator;
    private ImageView gestureIcon;
    private ProgressBar gestureProgress;
    private TextView gestureLabel;

    private boolean screenLocked = false;
    private View lockOverlay;
    private View btnUnlock;

    private int currentEpisodeIdx = -1;
    private View nextEpisodePanel;
    private TextView tvNextEpisodeTitle;
    private boolean nextEpisodeShown = false;
    private Runnable autoNextRunnable;

    private MediaItem item;
    private AppPrefs prefs;
    private AppState state;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private boolean barsVisible = false;
    private boolean isTv = false;
    private boolean isInPip = false;
    private boolean playerReleased = false;
    private boolean seekBarTracking = false;
    private int retryCount = 0;
    private float currentSpeed = 1.0f;

    private AudioManager audioManager;
    private float gestStartX, gestStartY;
    private boolean gestActive, gestIsVol, gestIsBright, gestIsSeek;
    private int gestStartVol;
    private float gestStartBright;
    private long seekStartPos;
    private static final int TOUCH_SLOP_PX = 24;

    private BroadcastReceiver pipCloseReceiver;

    private final Runnable pipCheckRunnable = new Runnable() {
        @Override public void run() {
            if (requestClose && !playerReleased) {
                requestClose = false;
                exitPlayer();
                return;
            }
            if (isInPip) handler.postDelayed(this, 500);
        }
    };

    private final Runnable seekUpdateRunnable = new Runnable() {
        @Override public void run() {
            updateSeekBar();
            checkAutoNext();
            handler.postDelayed(this, 500);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        setFullscreen();
        setContentView(R.layout.activity_player);
        hideSystemBars();

        prefs = new AppPrefs(this);
        state = AppState.get();
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        isTv = getPackageManager().hasSystemFeature(
                android.content.pm.PackageManager.FEATURE_LEANBACK);

        item = (MediaItem) getIntent().getSerializableExtra("item");
        if (item == null) {
            String url = getIntent().getStringExtra(EXTRA_URL);
            String title = getIntent().getStringExtra(EXTRA_TITLE);
            String type = getIntent().getStringExtra(EXTRA_TYPE);
            if (url != null) {
                String itemType = "live".equals(type) ? MediaItem.LIVE :
                        "series".equals(type) ? MediaItem.SERIES : MediaItem.VOD;
                item = new MediaItem("0", title != null ? title : "", "", url, "", itemType);
            }
        }
        if (item == null) { finish(); return; }
        prefs.addRecentlyWatched(item);

        pipCloseReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context ctx, Intent i) { exitPlayer(); }
        };
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(pipCloseReceiver,
                    new IntentFilter("com.jox3.tv.CLOSE_PIP"), Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(pipCloseReceiver, new IntentFilter("com.jox3.tv.CLOSE_PIP"));
        }

        initViews();
        initPlayer();
        if (isTv) showBars();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        MediaItem newItem = (MediaItem) intent.getSerializableExtra("item");
        if (newItem != null) {
            isInPip = false;
            item = newItem;
            tvName.setText(item.name);
            tvResolution.setVisibility(View.GONE);
            updateFavBtn();
            setupButtonsForType();
            initPlayer();
            showBars();
        }
    }

    private void setFullscreen() {
        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_FULLSCREEN |
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
        } else {
            int flags = View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
            getWindow().getDecorView().setSystemUiVisibility(flags);
            getWindow().getDecorView().setOnSystemUiVisibilityChangeListener(
                    v -> { if ((v & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) setFullscreen(); });
        }
    }

    private void hideSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            android.view.WindowInsetsController wic = getWindow().getInsetsController();
            if (wic != null) {
                wic.hide(android.view.WindowInsets.Type.statusBars() |
                        android.view.WindowInsets.Type.navigationBars());
                wic.setSystemBarsBehavior(
                        android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private void initViews() {
        playerView = findViewById(R.id.player_view);
        topBar = findViewById(R.id.top_bar);
        bottomBar = findViewById(R.id.bottom_bar);
        tvName = findViewById(R.id.tv_name);
        tvResolution = findViewById(R.id.tv_resolution);
        tvStatus = findViewById(R.id.tv_status);
        btnBack = findViewById(R.id.btn_back);
        btnFav = findViewById(R.id.btn_fav);

        layoutSeek = findViewById(R.id.layout_seek);
        layoutLiveBtns = findViewById(R.id.layout_live_btns);
        layoutVodBtns = findViewById(R.id.layout_vod_btns);

        btnPrev = findViewById(R.id.btn_prev);
        btnNext = findViewById(R.id.btn_next);
        btnPipLive = findViewById(R.id.btn_pip_live);
        btnStop = findViewById(R.id.btn_stop);

        btnPlayPause = findViewById(R.id.btn_play_pause);
        btnRewind = findViewById(R.id.btn_rewind);
        btnForward = findViewById(R.id.btn_forward);
        btnAudio = findViewById(R.id.btn_audio);
        btnSubs = findViewById(R.id.btn_subs);
        btnPip = findViewById(R.id.btn_pip);
        btnStopVod = findViewById(R.id.btn_stop_vod);
        btnSpeed = findViewById(R.id.btn_speed);
        btnLock = findViewById(R.id.btn_lock);
        btnPrevEp = findViewById(R.id.btn_prev_ep);
        btnNextEp = findViewById(R.id.btn_next_ep);

        seekBar = findViewById(R.id.seek_bar);
        tvPosition = findViewById(R.id.tv_position);
        tvDuration = findViewById(R.id.tv_duration);

        gestureIndicator = findViewById(R.id.gesture_indicator);
        gestureIcon = findViewById(R.id.gesture_icon);
        gestureProgress = findViewById(R.id.gesture_progress);
        gestureLabel = findViewById(R.id.gesture_label);

        lockOverlay = findViewById(R.id.lock_overlay);
        btnUnlock = findViewById(R.id.btn_unlock);
        if (lockOverlay != null) {
            lockOverlay.setOnClickListener(v -> {
                if (!screenLocked) return;
                if (btnUnlock != null) {
                    btnUnlock.setVisibility(View.VISIBLE);
                    handler.removeCallbacks(hideUnlockRunnable);
                    handler.postDelayed(hideUnlockRunnable, 2000);
                }
            });
        }

        nextEpisodePanel = findViewById(R.id.next_episode_panel);
        tvNextEpisodeTitle = findViewById(R.id.tv_next_episode_title);

        playerView.setPadding(0, 0, 0, 0);
        tvName.setText(item.name);
        updateFavBtn();

        btnBack.setOnClickListener(v -> exitPlayer());
        btnFav.setOnClickListener(v -> { prefs.toggleFav(item.favKey()); updateFavBtn(); });

        btnPrev.setOnClickListener(v -> navigateChannel(-1));
        btnNext.setOnClickListener(v -> navigateChannel(1));
        btnPipLive.setOnClickListener(v -> enterPip());
        btnStop.setOnClickListener(v -> exitPlayer());

        btnPlayPause.setOnClickListener(v -> togglePlayPause());
        btnRewind.setOnClickListener(v -> {
            if (player != null)
                player.seekTo(Math.max(0, player.getCurrentPosition() - 10000));
        });
        btnForward.setOnClickListener(v -> {
            if (player != null)
                player.seekTo(Math.min(player.getDuration(),
                        player.getCurrentPosition() + 10000));
        });
        btnAudio.setOnClickListener(v -> showAudioTracks());
        btnSubs.setOnClickListener(v -> showSubtitleTracks());
        btnPip.setOnClickListener(v -> enterPip());
        btnStopVod.setOnClickListener(v -> exitPlayer());

        if (btnSpeed != null) btnSpeed.setOnClickListener(v -> showSpeedDialog());
        if (btnPrevEp != null) btnPrevEp.setOnClickListener(v -> navigateEpisode(-1));
        if (btnNextEp != null) btnNextEp.setOnClickListener(v -> navigateEpisode(1));
        if (btnLock != null) btnLock.setOnClickListener(v -> toggleLock());

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean fromUser) {
                if (fromUser && player != null)
                    tvPosition.setText(formatTime((long)(p / 100.0 * player.getDuration())));
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {
                seekBarTracking = true;
                handler.removeCallbacks(seekUpdateRunnable);
            }
            @Override public void onStopTrackingTouch(SeekBar sb) {
                seekBarTracking = false;
                if (player != null)
                    player.seekTo((long)(sb.getProgress() / 100.0 * player.getDuration()));
                handler.post(seekUpdateRunnable);
            }
        });

        setupButtonsForType();

        playerView.setOnClickListener(v -> {
            if (screenLocked) return;
            if (!isTv && !isInPip) toggleBars();
        });
        playerView.setOnTouchListener(this::onTouch);
    }

    private void setupButtonsForType() {
        boolean isLive = item.type.equals(MediaItem.LIVE);
        playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FILL);

        layoutLiveBtns.setVisibility(isLive ? View.VISIBLE : View.GONE);
        layoutVodBtns.setVisibility(isLive ? View.GONE : View.VISIBLE);
        layoutSeek.setVisibility(isLive ? View.GONE : View.VISIBLE);

        if (btnSpeed != null) btnSpeed.setVisibility(isLive ? View.GONE : View.VISIBLE);
        if (btnLock != null) btnLock.setVisibility(View.VISIBLE);

        updateEpisodeNavButtons();
    }

    private void showSpeedDialog() {
        String[] speeds = {"0.5x", "0.75x", "1x (Normal)", "1.25x", "1.5x", "2x"};
        float[] vals = {0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f};
        int current = 2;
        for (int i = 0; i < vals.length; i++)
            if (vals[i] == currentSpeed) { current = i; break; }

        new AlertDialog.Builder(this)
                .setTitle("Velocidad de reproducción")
                .setSingleChoiceItems(speeds, current, (d, which) -> {
                    currentSpeed = vals[which];
                    if (player != null)
                        player.setPlaybackSpeed(currentSpeed);
                    if (btnSpeed != null)
                        btnSpeed.setText(currentSpeed == 1.0f ? "1x" :
                                speeds[which].replace(" (Normal)", ""));
                    d.dismiss();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void toggleLock() {
        screenLocked = !screenLocked;
        if (lockOverlay != null)
            lockOverlay.setVisibility(screenLocked ? View.VISIBLE : View.GONE);

        if (btnUnlock != null) {
            btnUnlock.setOnClickListener(v -> toggleLock());
            if (screenLocked) {
                btnUnlock.setVisibility(View.VISIBLE);
                handler.removeCallbacks(hideUnlockRunnable);
                handler.postDelayed(hideUnlockRunnable, 2000);
            } else {
                btnUnlock.setVisibility(View.GONE);
            }
        }

        if (btnLock != null) {
            btnLock.setCompoundDrawablesWithIntrinsicBounds(
                    0, screenLocked ? R.drawable.ic_lock_accent : R.drawable.ic_lock_open, 0, 0);
        }

        if (screenLocked) {
            topBar.setVisibility(View.GONE);
            bottomBar.setVisibility(View.GONE);
            barsVisible = false;
            Toast.makeText(this, "Pantalla bloqueada", Toast.LENGTH_SHORT).show();
        } else {
            showBars();
            Toast.makeText(this, "Pantalla desbloqueada", Toast.LENGTH_SHORT).show();
        }
    }

    public void setEpisodeList(List<MediaItem> episodes, int currentIdx) {
        this.currentEpisodeIdx = currentIdx;
        state.episodeQueue = episodes;
        state.episodeIdx = currentIdx;
    }

    private void navigateEpisode(int dir) {
        if (state.episodeQueue == null || state.episodeQueue.isEmpty()) return;
        int newIdx = state.episodeIdx + dir;
        if (newIdx < 0 || newIdx >= state.episodeQueue.size()) return;
        state.episodeIdx = newIdx;
        item = state.episodeQueue.get(newIdx);
        tvName.setText(item.name);
        nextEpisodeShown = false;
        if (nextEpisodePanel != null) nextEpisodePanel.setVisibility(View.GONE);
        updateEpisodeNavButtons();
        initPlayer();
    }

    private void updateEpisodeNavButtons() {
        boolean hasQueue = state.episodeQueue != null && !state.episodeQueue.isEmpty();
        if (btnPrevEp != null)
            btnPrevEp.setVisibility(hasQueue && state.episodeIdx > 0 ? View.VISIBLE : View.GONE);
        if (btnNextEp != null)
            btnNextEp.setVisibility(hasQueue && state.episodeIdx < state.episodeQueue.size() - 1 ? View.VISIBLE : View.GONE);
    }

    private void checkAutoNext() {
        if (player == null || nextEpisodeShown) return;
        if (state.episodeQueue == null || state.episodeQueue.isEmpty()) return;
        if (state.episodeIdx < 0 || state.episodeIdx >= state.episodeQueue.size() - 1) return;
        if (!item.type.equals(MediaItem.VOD)) return;

        long dur = player.getDuration();
        long pos = player.getCurrentPosition();
        if (dur <= 0) return;
        long remaining = dur - pos;

        if (remaining <= 60000 && remaining > 0) {
            nextEpisodeShown = true;
            showNextEpisodePanel();
        }
    }

    private void showNextEpisodePanel() {
        if (nextEpisodePanel == null) return;
        MediaItem next = state.episodeQueue.get(state.episodeIdx + 1);
        if (tvNextEpisodeTitle != null)
            tvNextEpisodeTitle.setText("Siguiente: " + next.name);
        nextEpisodePanel.setVisibility(View.VISIBLE);

        View btnPlayNext = nextEpisodePanel.findViewById(R.id.btn_play_next);
        View btnSkipNext = nextEpisodePanel.findViewById(R.id.btn_skip_next);

        if (btnPlayNext != null) btnPlayNext.setOnClickListener(v -> {
            nextEpisodePanel.setVisibility(View.GONE);
            playNextEpisode(next);
        });
        if (btnSkipNext != null) btnSkipNext.setOnClickListener(v -> {
            nextEpisodePanel.setVisibility(View.GONE);
            handler.postDelayed(() -> playNextEpisode(next), 10000);
            Toast.makeText(this, "Reproduciendo siguiente en 10 segundos...",
                    Toast.LENGTH_LONG).show();
        });
    }

    private void playNextEpisode(MediaItem next) {
        state.episodeIdx++;
        item = next;
        tvName.setText(item.name);
        nextEpisodeShown = false;
        initPlayer();
    }

    private void togglePlayPause() {
        if (player == null) return;
        if (player.isPlaying()) {
            player.pause();
            setPlayPauseIcon(false);
        } else {
            player.play();
            setPlayPauseIcon(true);
        }
    }

    private void setPlayPauseIcon(boolean playing) {
        if (btnPlayPause == null) return;
        btnPlayPause.setCompoundDrawablesWithIntrinsicBounds(
                0, playing ? R.drawable.ic_pause : R.drawable.ic_play, 0, 0);
    }

    private void showAudioTracks() {
        if (player == null) return;
        Tracks tracks = player.getCurrentTracks();
        List<String> names = new ArrayList<>();
        List<TrackGroup> groups = new ArrayList<>();

        for (Tracks.Group g : tracks.getGroups()) {
            if (g.getType() == C.TRACK_TYPE_AUDIO) {
                for (int i = 0; i < g.length; i++) {
                    androidx.media3.common.Format f = g.getTrackFormat(i);
                    String lang = f.language != null ? f.language.toUpperCase() : "Audio " + (groups.size() + 1);
                    String label = f.label != null ? f.label : lang;
                    names.add(label);
                    groups.add(g.getMediaTrackGroup());
                }
            }
        }

        if (names.isEmpty()) {
            Toast.makeText(this, "Sin pistas de audio disponibles", Toast.LENGTH_SHORT).show();
            return;
        }

        // Desambiguar nombres duplicados agregando un índice (Bug #4)
        disambiguateNames(names);

        new AlertDialog.Builder(this)
                .setTitle("Seleccionar audio")
                .setItems(names.toArray(new String[0]), (d, which) -> {
                    player.setTrackSelectionParameters(
                            player.getTrackSelectionParameters().buildUpon()
                                    .setOverrideForType(new TrackSelectionOverride(groups.get(which), 0))
                                    .build());
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void showSubtitleTracks() {
        if (player == null) return;
        Tracks tracks = player.getCurrentTracks();
        List<String> names = new ArrayList<>();
        names.add("Desactivar");
        List<TrackGroup> groups = new ArrayList<>();
        groups.add(null);

        for (Tracks.Group g : tracks.getGroups()) {
            if (g.getType() == C.TRACK_TYPE_TEXT) {
                for (int i = 0; i < g.length; i++) {
                    androidx.media3.common.Format f = g.getTrackFormat(i);
                    String lang = f.language != null ? f.language.toUpperCase() : "Sub " + groups.size();
                    names.add(f.label != null ? f.label : lang);
                    groups.add(g.getMediaTrackGroup());
                }
            }
        }

        if (names.size() == 1) {
            Toast.makeText(this, "Sin subtítulos disponibles", Toast.LENGTH_SHORT).show();
            return;
        }

        // Desambiguar nombres duplicados (excepto "Desactivar" en índice 0)
        disambiguateNamesFrom(names, 1);

        new AlertDialog.Builder(this)
                .setTitle("Subtítulos")
                .setItems(names.toArray(new String[0]), (d, which) -> {
                    if (which == 0) {
                        player.setTrackSelectionParameters(
                                player.getTrackSelectionParameters().buildUpon()
                                        .setDisabledTrackTypes(
                                                com.google.common.collect.ImmutableSet.of(C.TRACK_TYPE_TEXT))
                                        .build());
                    } else {
                        player.setTrackSelectionParameters(
                                player.getTrackSelectionParameters().buildUpon()
                                        .setOverrideForType(new TrackSelectionOverride(groups.get(which), 0))
                                        .build());
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    /**
     * Bug #4 fix: si hay nombres de pista repetidos (p. ej. dos pistas "ES"),
     * se les agrega un índice " (1)", " (2)" para que el usuario pueda distinguirlas
     * en el diálogo de selección.
     */
    private void disambiguateNames(List<String> names) {
        disambiguateNamesFrom(names, 0);
    }

    private void disambiguateNamesFrom(List<String> names, int fromIndex) {
        java.util.Map<String, Integer> counts = new java.util.HashMap<>();
        for (int i = fromIndex; i < names.size(); i++) {
            String n = names.get(i);
            counts.put(n, counts.getOrDefault(n, 0) + 1);
        }
        java.util.Map<String, Integer> seen = new java.util.HashMap<>();
        for (int i = fromIndex; i < names.size(); i++) {
            String n = names.get(i);
            if (counts.get(n) > 1) {
                int idx = seen.getOrDefault(n, 0) + 1;
                seen.put(n, idx);
                names.set(i, n + " (" + idx + ")");
            }
        }
    }

    private void initPlayer() {
        if (player != null) { player.release(); player = null; }
        playerReleased = false;
        nextEpisodeShown = false;
        if (nextEpisodePanel != null) nextEpisodePanel.setVisibility(View.GONE);
        setStatus("Cargando...");
        if (btnPlayPause != null) setPlayPauseIcon(true);

        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);
        playerView.setUseController(false);

        String url = item.url;
        androidx.media3.common.MediaItem mi;
        if (url.contains(".m3u8") || url.contains("type=m3u")) {
            mi = new androidx.media3.common.MediaItem.Builder()
                    .setUri(url)
                    .setMimeType(androidx.media3.common.MimeTypes.APPLICATION_M3U8)
                    .build();
        } else if (url.contains(".mkv") || url.contains(".avi") || url.contains(".ts")) {
            mi = new androidx.media3.common.MediaItem.Builder()
                    .setUri(url)
                    .setMimeType("video/x-matroska")
                    .build();
        } else {
            mi = androidx.media3.common.MediaItem.fromUri(url);
        }

        player.setMediaItem(mi);
        player.prepare();
        player.setPlayWhenReady(true);
        player.setPlaybackSpeed(currentSpeed);
        player.addListener(makeListener());

        if (item.type.equals(MediaItem.VOD)) {
            long pos = prefs.getPos(item.id);
            long dur = prefs.getDur(item.id);
            if (pos > 10000 && dur > 0 && (int)(pos * 100 / dur) < 95) {
                askResume(pos);
            }
        }
    }

    private void askResume(long savedPos) {
        new AlertDialog.Builder(this)
                .setTitle("Continuar viendo")
                .setMessage("¿Deseas continuar desde donde lo dejaste?")
                .setPositiveButton("Continuar", (d, w) -> {
                    if (player != null) player.seekTo(savedPos);
                })
                .setNegativeButton("Desde el inicio", (d, w) ->
                        prefs.saveProgress(item.id, 0, 0))
                .setCancelable(false)
                .show();
    }

    private Player.Listener makeListener() {
        return new Player.Listener() {
            @Override public void onPlaybackStateChanged(int s) {
                if (s == Player.STATE_READY) {
                    setStatus(item.type.equals(MediaItem.LIVE) ? "EN VIVO" : "");
                    retryCount = 0;
                    if (!isTv && !barsVisible) showBars();
                    if (!item.type.equals(MediaItem.LIVE))
                        handler.post(seekUpdateRunnable);
                }
                if (s == Player.STATE_BUFFERING) setStatus("Cargando...");
                if (s == Player.STATE_ENDED) handler.removeCallbacks(seekUpdateRunnable);
            }

            @Override public void onIsPlayingChanged(boolean isPlaying) {
                if (btnPlayPause != null)
                    setPlayPauseIcon(isPlaying);
            }

            @Override public void onPlayerError(@NonNull PlaybackException e) {
                if (item.type.equals(MediaItem.LIVE) && retryCount < 3) {
                    retryCount++;
                    setStatus("Reconectando " + retryCount + "/3...");
                    handler.postDelayed(() -> initPlayer(), 3000);
                } else setStatus("Error");
            }

            @Override public void onVideoSizeChanged(@NonNull VideoSize vs) {
                if (vs.width > 0 && vs.height > 0) {
                    String q = vs.height >= 2160 ? "4K" :
                            vs.height >= 1080 ? "FHD" :
                            vs.height >= 720 ? "HD" : "SD";
                    runOnUiThread(() -> {
                        tvResolution.setText(vs.width + "x" + vs.height + " " + q);
                        tvResolution.setVisibility(View.VISIBLE);
                    });
                }
            }
        };
    }

    private void updateSeekBar() {
        if (player == null || seekBar == null || seekBarTracking) return;
        long dur = player.getDuration();
        long pos = player.getCurrentPosition();
        if (dur > 0) {
            seekBar.setProgress((int)(pos * 100 / dur));
            tvPosition.setText(formatTime(pos));
            tvDuration.setText(formatTime(dur));
        }
    }

    private String formatTime(long ms) {
        long s = ms / 1000, m = s / 60; s %= 60;
        long h = m / 60; m %= 60;
        return h > 0 ? String.format("%d:%02d:%02d", h, m, s)
                : String.format("%d:%02d", m, s);
    }

    private void navigateChannel(int dir) {
        // Bug #3 fix: validar null antes de isEmpty() para evitar NPE
        if (state.channelList == null || state.channelList.isEmpty() || state.channelIdx < 0) return;
        int next = state.channelIdx + dir;
        if (next < 0) next = state.channelList.size() - 1;
        if (next >= state.channelList.size()) next = 0;
        state.channelIdx = next;
        item = state.channelList.get(next);
        tvName.setText(item.name);
        tvResolution.setVisibility(View.GONE);
        updateFavBtn();
        isInPip = false;
        playerView.animate().alpha(0f).setDuration(150).withEndAction(() -> {
            initPlayer();
            playerView.animate().alpha(1f).setDuration(300).start();
        }).start();
    }

    private void toggleBars() { if (barsVisible) hideBars(); else showBars(); }

    private void showBars() {
        if (screenLocked) return;
        barsVisible = true;
        topBar.setVisibility(View.VISIBLE);
        bottomBar.setVisibility(View.VISIBLE);
        if (!isTv) {
            handler.removeCallbacks(this::hideBars);
            handler.postDelayed(this::hideBars, 4000);
            if (!item.type.equals(MediaItem.LIVE))
                handler.post(seekUpdateRunnable);
        }
    }

    private void hideBars() {
        if (isTv || screenLocked) return;
        barsVisible = false;
        topBar.setVisibility(View.GONE);
        bottomBar.setVisibility(View.GONE);
    }

    private void setStatus(String s) { runOnUiThread(() -> tvStatus.setText(s)); }
    private void updateFavBtn() {
        btnFav.setImageResource(prefs.isFav(item.favKey())
                ? R.drawable.ic_star_filled : R.drawable.ic_star_outline);
    }

    private void showGestureIndicator(boolean isVolume, int value, int max, String label) {
        if (gestureIndicator == null) return;
        gestureIndicator.setVisibility(View.VISIBLE);
        if (gestureIcon != null)
            gestureIcon.setImageResource(isVolume ?
                    android.R.drawable.ic_lock_silent_mode_off :
                    android.R.drawable.ic_menu_view);
        if (gestureProgress != null) {
            gestureProgress.setMax(max);
            gestureProgress.setProgress(value);
        }
        if (gestureLabel != null) gestureLabel.setText(label);
        handler.removeCallbacks(hideGestureRunnable);
        handler.postDelayed(hideGestureRunnable, 800);
    }

    private final Runnable hideGestureRunnable = () -> {
        if (gestureIndicator != null) gestureIndicator.setVisibility(View.GONE);
    };

    private void enterPip() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                topBar.setVisibility(View.GONE);
                bottomBar.setVisibility(View.GONE);
                handler.removeCallbacksAndMessages(null);
                PictureInPictureParams params = new PictureInPictureParams.Builder()
                        .setAspectRatio(new Rational(16, 9)).build();
                enterPictureInPictureMode(params);
            } catch (Exception e) {
                Toast.makeText(this, "PiP no disponible", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onPictureInPictureModeChanged(boolean inPip, @NonNull Configuration conf) {
        super.onPictureInPictureModeChanged(inPip, conf);
        isInPip = inPip;
        if (inPip) {
            requestClose = false;
            handler.postDelayed(pipCheckRunnable, 500);
        } else {
            handler.removeCallbacks(pipCheckRunnable);
            if (isFinishing()) {
                exitPlayer();
            } else {
                setFullscreen();
                hideSystemBars();
                showBars();
                if (player != null && !player.isPlaying()) player.play();
            }
        }
    }

    private void saveProgress() {
        if (player != null && item != null && !item.type.equals(MediaItem.LIVE))
            prefs.saveProgress(item.id, player.getCurrentPosition(), player.getDuration());
    }

    private final Runnable hideUnlockRunnable = () -> {
        if (btnUnlock != null) btnUnlock.setVisibility(View.GONE);
    };

    private void exitPlayer() {
        if (playerReleased) return;
        playerReleased = true;
        handler.removeCallbacks(seekUpdateRunnable);
        handler.removeCallbacks(pipCheckRunnable);
        handler.removeCallbacksAndMessages(null);
        saveProgress();

        if (player != null) {
            player.setPlayWhenReady(false);
            player.stop();
            player.clearMediaItems();
            player.release();
            player = null;
        }
        if (playerView != null) playerView.setPlayer(null);

        if (!isFinishing()) {
            finish();
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private boolean onTouch(View v, MotionEvent e) {
        if (isInPip || screenLocked) return false;
        float w = v.getWidth(), h = v.getHeight();

        switch (e.getAction()) {
            case MotionEvent.ACTION_DOWN:
                gestStartX = e.getX(); gestStartY = e.getY();
                gestActive = false;
                gestIsVol = gestStartX < w / 3f;
                gestIsBright = gestStartX > w * 2f / 3f;
                gestIsSeek = !gestIsVol && !gestIsBright;
                if (gestIsVol)
                    gestStartVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                if (gestIsBright) {
                    float b = getWindow().getAttributes().screenBrightness;
                    if (b < 0) try {
                        b = Settings.System.getInt(getContentResolver(),
                                Settings.System.SCREEN_BRIGHTNESS) / 255f;
                    } catch (Exception ignored) { b = 0.5f; }
                    gestStartBright = b;
                }
                if (gestIsSeek && player != null) seekStartPos = player.getCurrentPosition();
                // Bug #1 fix: no consumir el evento todavía para permitir que
                // el click se evalúe normalmente si no hay gesto real.
                return false;

            case MotionEvent.ACTION_MOVE:
                float dx = e.getX() - gestStartX;
                float dy = e.getY() - gestStartY;
                // Bug #1 fix: umbral unificado con TOUCH_SLOP_PX, y solo se
                // marca como gesto "real" (consumiendo el evento) cuando se
                // supera el umbral, evitando que toques leves disparen un gesto.
                if (!gestActive && (Math.abs(dx) > TOUCH_SLOP_PX || Math.abs(dy) > TOUCH_SLOP_PX)) {
                    gestActive = true;
                }
                if (!gestActive) return false;

                if (gestIsVol && Math.abs(dy) > Math.abs(dx)) {
                    int max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                    int vol = Math.max(0, Math.min(max, (int)(gestStartVol - dy / h * max)));
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, vol, 0);
                    int pct = (int)(vol * 100f / max);
                    showGestureIndicator(true, vol, max, "🔊 " + pct + "%");
                } else if (gestIsBright && Math.abs(dy) > Math.abs(dx)) {
                    float bright = Math.max(0.01f, Math.min(1f, gestStartBright - dy / h));
                    WindowManager.LayoutParams lp = getWindow().getAttributes();
                    lp.screenBrightness = bright;
                    getWindow().setAttributes(lp);
                    int pct = (int)(bright * 100f);
                    showGestureIndicator(false, pct, 100, "☀️ " + pct + "%");
                } else if (gestIsSeek && Math.abs(dx) > Math.abs(dy) && player != null
                        && !item.type.equals(MediaItem.LIVE)) {
                    long pos = Math.max(0, Math.min(player.getDuration(),
                            seekStartPos + (long)(dx / w * 120000)));
                    player.seekTo(pos);
                }
                // Si hubo gesto real, consumimos el evento para que no se
                // dispare además un click al soltar.
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                // Bug #2 fix: resetear explícitamente el estado del gesto al
                // soltar el dedo o si el sistema cancela el touch (notificación,
                // llamada entrante, etc.), evitando estado "sucio" residual.
                boolean wasGesture = gestActive;
                gestActive = false;
                gestIsVol = false;
                gestIsBright = false;
                gestIsSeek = false;
                // Si fue un gesto real ya consumimos el MOVE; no dejamos que
                // además se dispare el click. Si no fue gesto, dejamos que
                // el sistema procese el click normalmente.
                return wasGesture;
        }
        return false;
    }

    @Override
    public boolean dispatchKeyEvent(android.view.KeyEvent e) {
        if (!isTv || e.getAction() != android.view.KeyEvent.ACTION_DOWN)
            return super.dispatchKeyEvent(e);

        switch (e.getKeyCode()) {
            case android.view.KeyEvent.KEYCODE_DPAD_CENTER:
            case android.view.KeyEvent.KEYCODE_ENTER:
            case android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                togglePlayPause(); return true;
            case android.view.KeyEvent.KEYCODE_DPAD_LEFT:
            case android.view.KeyEvent.KEYCODE_MEDIA_REWIND:
                if (player != null && !item.type.equals(MediaItem.LIVE))
                    player.seekTo(Math.max(0, player.getCurrentPosition() - 10000));
                else navigateChannel(-1); return true;
            case android.view.KeyEvent.KEYCODE_DPAD_RIGHT:
            case android.view.KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                if (player != null && !item.type.equals(MediaItem.LIVE))
                    player.seekTo(Math.min(player.getDuration(), player.getCurrentPosition() + 10000));
                else navigateChannel(1); return true;
            case android.view.KeyEvent.KEYCODE_DPAD_UP: navigateChannel(-1); return true;
            case android.view.KeyEvent.KEYCODE_DPAD_DOWN: navigateChannel(1); return true;
            case android.view.KeyEvent.KEYCODE_BACK:
            case android.view.KeyEvent.KEYCODE_MEDIA_STOP:
                exitPlayer(); return true;
        }
        return super.dispatchKeyEvent(e);
    }

    @Override protected void onPause() {
        super.onPause();
        boolean inPipNow = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                && isInPictureInPictureMode();
        if (!inPipNow && !playerReleased && player != null) player.pause();
    }

    @Override protected void onResume() {
        super.onResume();
        setFullscreen();
        hideSystemBars();
        if (!isInPip && player != null && !playerReleased) player.play();
    }

    @Override protected void onStop() {
        super.onStop();
        saveProgress();
        if (isInPip && !isFinishing()) exitPlayer();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        if (pipCloseReceiver != null) {
            try { unregisterReceiver(pipCloseReceiver); } catch (Exception ignored) {}
        }
        if (!playerReleased) exitPlayer();
    }

    @Override public void onBackPressed() {
        boolean inPipNow = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                && isInPictureInPictureMode();
        if (isInPip || inPipNow) { moveTaskToBack(false); return; }
        exitPlayer();
    }
}
