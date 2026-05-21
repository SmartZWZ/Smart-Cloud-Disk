package com.example.clouddisk;

import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.OpenableColumns;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.LayoutInflater;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.media3.common.MediaItem;
import androidx.media3.datasource.okhttp.OkHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.room.Room;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;
import java.io.InputStream;
import java.io.StringReader;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Credentials;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.BufferedSink;
import okio.Okio;
import okio.Source;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.github.chrisbanes.photoview.PhotoView;
import androidx.core.content.FileProvider;
import java.io.File;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.view.WindowManager;
import java.util.concurrent.Executor;

public class FileListActivity extends AppCompatActivity implements SensorEventListener {

    private RecyclerView rvFiles, rvTrans;
    private FileAdapter adapter;
    private TransferAdapter transAdapter;
    private List<CloudFile> dataList = new ArrayList<>();
    private List<TransferTask> transList = new ArrayList<>();
    private MediaPlayer mediaPlayer; // 用于音频
    private ExoPlayer exoPlayer;
    private PlayerView videoPlayerView;
    private ImageView imgDisc;
    private PhotoView ivFullImage;
    private View layoutMe, layoutTrans, layoutFilesContainer, layoutAi, playerView, layoutAudioControls;
    private View layoutTransCategories, layoutTransListContainer;
    private View layoutVideoPlaylist;
    private RecyclerView rvVideoPlaylist;
    private SeekBar seekBar;
    private ProgressBar storageProgress;
    private TextView tvStorageText, tvPlayerTime, tvCurrentPath, tvEmptyHint, tvAudioTitle;
    private EditText etSearch;
    private ImageButton btnSearch, btnPlayPause, btnAudioMenu;
    private FloatingActionButton fabShowPlayer, fabDownload, fabUpload, fabMove, fabDelete, fabNewFolder;
    private ImageButton btnBackDir;

    private List<CloudFile> currentImageItems = new ArrayList<>();
    private List<CloudFile> currentVideoItems = new ArrayList<>();
    private List<CloudFile> currentAudioItems = new ArrayList<>();
    private int currentAudioIndex = -1;
    private int playMode = 0; // 0: 顺序播放, 1: 单曲循环, 2: 循环播放
    private int currentImageIndex = -1;
    private String currentPath = ""; // 相对路径，如 "movies/"
    private boolean isSearchMode = false;
    private long cloudUsedBytes = 0;
    private Handler handler = new Handler();
    private ActivityResultLauncher<String> pickFileLauncher;
    private AppDatabase db;

    private String user_dav = Config.WEBDAV_USER;
    private String pass_dav = Config.WEBDAV_PASS;
    private static final String CD2_URL = Config.getWebDavUrl();
    private static final String CD2_TOKEN = Config.CD2_TOKEN;
    private final long TOTAL_CAPACITY = 1024L * 1024 * 1024 * 1024; // 1TB

    private SensorManager sensorManager;
    private Sensor lightSensor;
    private boolean isBiometricAuthenticated = false;

    private com.google.android.material.switchmaterial.SwitchMaterial switch2FA;
    private View btnAppLockSettings;
    private android.widget.Button btnViewQR;
    
    private static final String SP_LOCK_FACE = "lock_face";
    private static final String SP_LOCK_FINGER = "lock_finger";
    private static final String SP_LOCK_PASS = "lock_pass";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file_list);

        // 初始化传感器
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        }

        // 应用锁校验
        SharedPreferences sp = getSharedPreferences("user_prefs", MODE_PRIVATE);
        boolean isAnyLockEnabled = sp.getBoolean(SP_LOCK_FACE, false)
                || sp.getBoolean(SP_LOCK_FINGER, false)
                || sp.getBoolean(SP_LOCK_PASS, false);
        
        if (isAnyLockEnabled && !isBiometricAuthenticated) {
            showBiometricPrompt();
        }

        // 初始化
        rvFiles = findViewById(R.id.rv_files);
        rvTrans = findViewById(R.id.rv_trans_tasks);
        layoutFilesContainer = findViewById(R.id.layout_files_container);
        layoutMe = findViewById(R.id.layout_me);
        layoutTrans = findViewById(R.id.layout_trans);
        layoutAi = findViewById(R.id.layout_ai);
        layoutTransCategories = findViewById(R.id.layout_trans_categories);
        layoutTransListContainer = findViewById(R.id.layout_trans_list_container);
        playerView = findViewById(R.id.player_full_screen);
        imgDisc = findViewById(R.id.img_disc);
        ivFullImage = findViewById(R.id.iv_full_image);
        videoPlayerView = findViewById(R.id.video_player_view);
        layoutVideoPlaylist = findViewById(R.id.layout_video_playlist);
        rvVideoPlaylist = findViewById(R.id.rv_video_playlist);
        layoutAudioControls = findViewById(R.id.layout_audio_controls);
        seekBar = findViewById(R.id.player_seekbar);
        storageProgress = findViewById(R.id.storage_progress);
        tvStorageText = findViewById(R.id.tv_storage_text);
        tvPlayerTime = findViewById(R.id.tv_player_time);
        tvCurrentPath = findViewById(R.id.tv_current_path);
        tvEmptyHint = findViewById(R.id.tv_empty_hint);
        tvAudioTitle = findViewById(R.id.tv_audio_title);
        btnAudioMenu = findViewById(R.id.btn_audio_menu);
        etSearch = findViewById(R.id.et_search);
        btnSearch = findViewById(R.id.btn_search);
        btnPlayPause = findViewById(R.id.btn_play_pause);
        TextView tvUsernameDisplay = findViewById(R.id.tv_username_display);
        TextView tvVersionDisplay = findViewById(R.id.tv_app_version);
        
        // 显示当前登录用户名
        String savedUsername = sp.getString("username", "admin");
        tvUsernameDisplay.setText("当前用户: " + savedUsername);

        // 动态显示版本号
        tvVersionDisplay.setText("版本号: " + BuildConfig.VERSION_NAME);
        
        // 方案核心：每个账号自动进入自己的专属文件夹，但底层共用 admin
        currentPath = savedUsername + "/";
        user_dav = Config.WEBDAV_USER;
        pass_dav = Config.WEBDAV_PASS;

        btnBackDir = findViewById(R.id.btn_back_dir);
        fabShowPlayer = findViewById(R.id.fab_show_player);
        fabDownload = findViewById(R.id.fab_download);
        fabUpload = findViewById(R.id.fab_upload);
        fabNewFolder = findViewById(R.id.fab_new_folder);
        fabMove = findViewById(R.id.fab_move);
        fabDelete = findViewById(R.id.fab_delete);

        btnAudioMenu.setOnClickListener(v -> showAudioMenu());
        
        btnSearch.setOnClickListener(v -> {
            String query = etSearch.getText().toString().trim();
            if (!query.isEmpty()) performSearch(query);
        });

        etSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                String query = etSearch.getText().toString().trim();
                if (!query.isEmpty()) performSearch(query);
                return true;
            }
            return false;
        });

        findViewById(R.id.btn_prev).setOnClickListener(v -> playPreviousAudio());
        findViewById(R.id.btn_next).setOnClickListener(v -> playNextAudio());

        // 初始化数据库
        db = Room.databaseBuilder(getApplicationContext(), AppDatabase.class, "cloud_db")
                .fallbackToDestructiveMigration()
                .build();

        // 退出登录
        findViewById(R.id.btn_logout).setOnClickListener(v -> {
            sp.edit().putBoolean("is_logged_in", false).apply();
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });

        // 修改密码
        findViewById(R.id.btn_change_password).setOnClickListener(v -> showChangePasswordDialog());

        // 2FA 管理
        switch2FA = findViewById(R.id.switch_2fa);
        btnViewQR = findViewById(R.id.btn_view_qr);
        
        switch2FA.setOnClickListener(v -> {
            boolean isChecked = switch2FA.isChecked();
            if (!isChecked) {
                // 如果是尝试关闭，弹出确认框
                new AlertDialog.Builder(this)
                        .setTitle("确认关闭安全盾？")
                        .setMessage("关闭后，登录将不再需要动态验证码，账号风险将增加。")
                        .setPositiveButton("确认关闭", (dialog, which) -> toggle2FA(false))
                        .setNegativeButton("取消", (dialog, which) -> switch2FA.setChecked(true))
                        .show();
            } else {
                toggle2FA(true);
            }
        });

        btnViewQR.setOnClickListener(v -> toggle2FA(true));

        // 检查更新
        findViewById(R.id.btn_check_update_me).setOnClickListener(v -> checkUpdateMe());

        // 应用锁设置入口
        btnAppLockSettings = findViewById(R.id.btn_app_lock_settings);
        btnAppLockSettings.setOnClickListener(v -> showAppLockDetailDialog());

        // 关于我们
        findViewById(R.id.btn_about_us).setOnClickListener(v -> showAboutDialog());

        // 注销账号逻辑
        findViewById(R.id.tv_cancel_account).setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("确认注销账号？")
                    .setMessage("此操作将永久删除您的本地账号以及云端文件夹（" + savedUsername + "）内的所有数据，且不可恢复！")
                    .setPositiveButton("确认注销", (dialog, which) -> cancelAccount(savedUsername, null))
                    .setNegativeButton("取消", null)
                    .show();
        });

        // 传输列表分类点击
        findViewById(R.id.card_upload).setOnClickListener(v -> showFilteredTrans(false));
        findViewById(R.id.card_download).setOnClickListener(v -> showFilteredTrans(true));
        findViewById(R.id.btn_trans_back).setOnClickListener(v -> {
            layoutTransCategories.setVisibility(View.VISIBLE);
            layoutTransListContainer.setVisibility(View.GONE);
        });

        pickFileLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) {
                uploadFile(uri);
            }
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (playerView.getVisibility() == View.VISIBLE) {
                    if (videoPlayerView.getVisibility() == View.VISIBLE) {
                        exitFullScreenVideo();
                    } else {
                        // 关闭音频播放层，但保持播放，显示悬浮按钮
                        playerView.setVisibility(View.GONE);
                        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                            fabShowPlayer.setVisibility(View.VISIBLE);
                        }
                    }
                } else if (isSearchMode) {
                    // 如果在搜索模式，退出搜索，回到当前目录列表
                    fetchFilesFromCD2();
                } else if (adapter != null && adapter.isSelectionMode()) {
                    adapter.setSelectionMode(false);
                } else if (layoutTransListContainer.getVisibility() == View.VISIBLE) {
                    layoutTransCategories.setVisibility(View.VISIBLE);
                    layoutTransListContainer.setVisibility(View.GONE);
                } else if (!currentPath.isEmpty()) {
                    // 如果在子文件夹，返回上一级
                    goBackDir();
                } else {
                    // 在根目录时，左滑手势让应用进入后台，而不是直接退出进程，实现后台播放
                    moveTaskToBack(true);
                }
            }
        });

        btnBackDir.setOnClickListener(v -> {
            if (isSearchMode) {
                fetchFilesFromCD2();
            } else {
                goBackDir();
            }
        });

        rvVideoPlaylist.setLayoutManager(new LinearLayoutManager(this));

        rvFiles.setLayoutManager(new LinearLayoutManager(this));
        adapter = new FileAdapter(dataList);
        rvFiles.setAdapter(adapter);

        rvTrans.setLayoutManager(new LinearLayoutManager(this));
        transAdapter = new TransferAdapter(transList);
        rvTrans.setAdapter(transAdapter);

        ivFullImage.setOnSingleFlingListener((e1, e2, velocityX, velocityY) -> {
            if (ivFullImage.getScale() > 1.05f) return false; // 放大状态下不翻页
            float diffX = e2.getX() - e1.getX();
            if (Math.abs(diffX) > 200) {
                if (diffX > 0) showPreviousImage();
                else showNextImage();
                return true;
            }
            return false;
        });

        // 底部导航切换
        com.google.android.material.bottomnavigation.BottomNavigationView nav = findViewById(R.id.bottom_navigation);
        nav.setItemIconTintList(null); // 关键：禁用系统涂色，显示图标原图颜色
        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_files) {
                layoutFilesContainer.setVisibility(View.VISIBLE);
                layoutMe.setVisibility(View.GONE);
                layoutTrans.setVisibility(View.GONE);
                layoutAi.setVisibility(View.GONE);
                fabUpload.setVisibility(View.VISIBLE);
                fabNewFolder.setVisibility(View.VISIBLE);
                
                // 修复 Bug：回到文件页时，如果音乐正在后台播放且播放器界面已关闭，则恢复显示唤回按钮
                if (mediaPlayer != null && mediaPlayer.isPlaying() && playerView.getVisibility() != View.VISIBLE) {
                    fabShowPlayer.setVisibility(View.VISIBLE);
                }
            } else if (id == R.id.nav_trans) {
                layoutFilesContainer.setVisibility(View.GONE);
                layoutMe.setVisibility(View.GONE);
                layoutTrans.setVisibility(View.VISIBLE);
                layoutAi.setVisibility(View.GONE);
                fabUpload.setVisibility(View.GONE);
                fabNewFolder.setVisibility(View.GONE);
                fabShowPlayer.setVisibility(View.GONE);
                fabDownload.setVisibility(View.GONE);
                fabMove.setVisibility(View.GONE);
                fabDelete.setVisibility(View.GONE);
            } else if (id == R.id.nav_ai) {
                layoutFilesContainer.setVisibility(View.GONE);
                layoutMe.setVisibility(View.GONE);
                layoutTrans.setVisibility(View.GONE);
                layoutAi.setVisibility(View.VISIBLE);
                fabUpload.setVisibility(View.GONE);
                fabNewFolder.setVisibility(View.GONE);
                fabShowPlayer.setVisibility(View.GONE);
                fabDownload.setVisibility(View.GONE);
                fabMove.setVisibility(View.GONE);
                fabDelete.setVisibility(View.GONE);
            } else if (id == R.id.nav_me) {
                layoutFilesContainer.setVisibility(View.GONE);
                layoutMe.setVisibility(View.VISIBLE);
                layoutTrans.setVisibility(View.GONE);
                layoutAi.setVisibility(View.GONE);
                fabUpload.setVisibility(View.GONE);
                fabNewFolder.setVisibility(View.GONE);
                fabShowPlayer.setVisibility(View.GONE);
                fabDownload.setVisibility(View.GONE);
                fabMove.setVisibility(View.GONE);
                fabDelete.setVisibility(View.GONE);
            }
            return true;
        });

        // 设置 Adapter 监听器
        adapter.setOnItemClickListener(new FileAdapter.OnItemClickListener() {
            @androidx.media3.common.util.UnstableApi
            @Override
            public void onItemClick(CloudFile file) {
                if (file.isDirectory()) {
                    enterFolder(file.getName());
                } else {
                    previewFile(file);
                }
            }

            @Override
            public void onSelectionModeChanged(boolean enabled) {
                fabDownload.setVisibility(enabled ? View.VISIBLE : View.GONE);
                fabMove.setVisibility(enabled ? View.VISIBLE : View.GONE);
                fabDelete.setVisibility(enabled ? View.VISIBLE : View.GONE);
                
                if (enabled) {
                    fabUpload.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
                    fabUpload.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.RED));
                } else {
                    fabUpload.setImageResource(android.R.drawable.ic_menu_upload);
                    fabUpload.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#4CAF50")));
                }
            }
        });

        if (seekBar != null) {
            seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser && mediaPlayer != null) mediaPlayer.seekTo(progress);
                    if (tvPlayerTime != null && mediaPlayer != null) {
                        tvPlayerTime.setText(String.format(Locale.getDefault(), "%s / %s", 
                                formatTime(progress), formatTime(mediaPlayer.getDuration())));
                    }
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(SeekBar seekBar) {}
            });
        }

        // 延迟 3 秒开始静默索引，不占用 App 启动时的带宽
        handler.postDelayed(this::startIndexing, 3000);

        fetchFilesFromCD2();
        
        // 校验云端账号是否存在
        validateAccount(savedUsername, sp.getString("password", ""));
        
        // 获取初始 2FA 状态
        toggle2FAStatusOnly(savedUsername, sp.getString("password", ""));

        // 绑定 AI 助手按钮
        findViewById(R.id.btn_open_ai_chat).setOnClickListener(v -> showAiChatDialog());
    }

    private void toggle2FAStatusOnly(String user, String pass) {
        new Thread(() -> {
            try {
                JSONObject json = new JSONObject();
                json.put("username", user);
                json.put("password", pass);
                json.put("action", "status");

                RequestBody body = RequestBody.create(json.toString(), MediaType.parse("application/json"));
                Request request = new Request.Builder()
                        .url(Config.getBackendUrl() + "/manage_2fa")
                        .post(body)
                        .build();

                OkHttpClient client = new OkHttpClient();
                try (Response response = client.newCall(request).execute()) {
                    if (response.isSuccessful()) {
                        JSONObject respJson = new JSONObject(response.body().string());
                        boolean isEnabled = respJson.optBoolean("twoFactorEnabled", false);
                        runOnUiThread(() -> {
                            switch2FA.setChecked(isEnabled);
                            btnViewQR.setVisibility(isEnabled ? View.VISIBLE : View.GONE);
                        });
                    }
                }
            } catch (Exception ignored) {}
        }).start();
    }

    private void validateAccount(String username, String password) {
        if (username == null || username.equals("admin")) return; // 管理员或未登录不校验
        new Thread(() -> {
            try {
                OkHttpClient client = new OkHttpClient();
                JSONObject json = new JSONObject();
                json.put("username", username);
                json.put("password", password);

                RequestBody body = RequestBody.create(json.toString(), MediaType.parse("application/json"));
                Request request = new Request.Builder()
                        .url(Config.getBackendUrl() + "/login")
                        .post(body)
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        runOnUiThread(() -> {
                            Toast.makeText(this, "云端账号已失效，请重新登录", Toast.LENGTH_LONG).show();
                            getSharedPreferences("user_prefs", MODE_PRIVATE).edit().clear().apply();
                            startActivity(new Intent(this, MainActivity.class));
                            finish();
                        });
                    }
                }
            } catch (Exception e) { }
        }).start();
    }

    // 唤回全屏并强制置顶
    public void showPlayer(View v) {
        if (playerView != null) {
            playerView.setVisibility(View.VISIBLE);
            playerView.bringToFront();
            fabShowPlayer.setVisibility(View.GONE);
            if (videoPlayerView.getVisibility() == View.VISIBLE) {
                enterFullScreenVideo();
            }
        }
    }

    private void enterFullScreenVideo() {
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    private void exitFullScreenVideo() {
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
        playerView.setVisibility(View.GONE);
        if (exoPlayer != null) exoPlayer.pause();
    }

    private void enterFolder(String folderName) {
        currentPath += folderName + "/";
        // 延迟 3 秒开始静默索引，不占用 App 启动时的带宽
        handler.postDelayed(this::startIndexing, 3000);

        fetchFilesFromCD2();
        
        // 校验云端账号是否存在
        SharedPreferences sp = getSharedPreferences("user_prefs", MODE_PRIVATE);
        validateAccount(sp.getString("username", "admin"), sp.getString("password", ""));
    }

    private void goBackDir() {
        // 防止用户退回到根目录（即防止看到其他用户的文件夹）
        SharedPreferences sp = getSharedPreferences("user_prefs", MODE_PRIVATE);
        String userRoot = sp.getString("username", "") + "/";
        
        if (currentPath.equals(userRoot)) return;

        // 去掉最后的斜杠，再找上一个斜杠
        String temp = currentPath.substring(0, currentPath.length() - 1);
        int lastIndex = temp.lastIndexOf("/");
        if (lastIndex == -1) currentPath = userRoot;
        else currentPath = temp.substring(0, lastIndex + 1);
        // 延迟 3 秒开始静默索引，不占用 App 启动时的带宽
        handler.postDelayed(this::startIndexing, 3000);

        fetchFilesFromCD2();
        
        // 校验云端账号是否存在
        validateAccount(sp.getString("username", "admin"), sp.getString("password", ""));
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // 自动适配全屏显示，无需手动调整
    }

    @androidx.media3.common.util.UnstableApi
    private void previewFile(CloudFile file) {
        try {
            String pathInCloud = (file.getFullPath() != null && !file.getFullPath().isEmpty()) 
                    ? file.getFullPath() : (currentPath + file.getName());
            
            String encodedPath = encodePath(pathInCloud);
            String url = CD2_URL + encodedPath;
            String ext = file.getName().substring(file.getName().lastIndexOf(".") + 1).toLowerCase();

            // 预先隐藏非通用的预览组件
            ivFullImage.setVisibility(View.GONE);
            videoPlayerView.setVisibility(View.GONE);
            tvAudioTitle.setVisibility(View.GONE);
            
            // 如果不是音频，则隐藏音频碟片（但不要停止音乐，除非是视频或新音频）
            if (!ext.matches("mp3|wav|flac|aac|m4a")) {
                imgDisc.setVisibility(View.GONE);
                imgDisc.clearAnimation();
                layoutAudioControls.setVisibility(View.GONE);
            }

            if (ext.matches("mp4|mkv|mov|avi")) {
                // 播放视频前必须停止音频
                if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                    mediaPlayer.reset();
                }
                btnAudioMenu.setVisibility(View.GONE); // 播放视频时隐藏音频菜单
                videoPlayerView.setVisibility(View.VISIBLE);
                layoutVideoPlaylist.setVisibility(View.GONE);
                
                // 刷新视频播放列表
                currentVideoItems.clear();
                for (CloudFile f : dataList) {
                    if (!f.isDirectory() && f.getName().toLowerCase().matches(".*\\.(mp4|mkv|mov|avi)$")) {
                        currentVideoItems.add(f);
                    }
                }
                FileAdapter videoAdapter = new FileAdapter(currentVideoItems);
                videoAdapter.setOnItemClickListener(new FileAdapter.OnItemClickListener() {
                    @androidx.media3.common.util.UnstableApi
                    @Override public void onItemClick(CloudFile f) { previewFile(f); }
                    @Override public void onSelectionModeChanged(boolean enabled) {}
                });
                rvVideoPlaylist.setAdapter(videoAdapter);

                enterFullScreenVideo();

                if (exoPlayer == null) {
                    exoPlayer = new ExoPlayer.Builder(this).build();
                    videoPlayerView.setPlayer(exoPlayer);
                }
                
                // 配置 OkHttpDataSource 用于传递 WebDAV 认证头
                OkHttpDataSource.Factory dataSourceFactory = new OkHttpDataSource.Factory(new OkHttpClient())
                        .setDefaultRequestProperties(Collections.singletonMap("Authorization", Credentials.basic(user_dav, pass_dav)));
                
                MediaItem mediaItem = MediaItem.fromUri(url);
                exoPlayer.setMediaSource(new DefaultMediaSourceFactory(dataSourceFactory).createMediaSource(mediaItem));
                exoPlayer.prepare();
                exoPlayer.play();
                
                showPlayer(null);
            } else if (ext.matches("jpg|jpeg|png|gif|webp")) {
                // 查看图片，保持音乐播放，只显示图片层
                ivFullImage.setVisibility(View.VISIBLE);
                showPlayer(null);
                
                // 收集当前目录下的所有图片，用于左右滑动切换
                currentImageItems.clear();
                for (CloudFile f : dataList) {
                    if (!f.isDirectory() && f.getName().toLowerCase().matches(".*\\.(jpg|jpeg|png|gif|webp)$")) {
                        currentImageItems.add(f);
                    }
                }
                currentImageIndex = currentImageItems.indexOf(file);
                displayImage(file);
            } else if (ext.matches("mp3|wav|flac|aac|m4a")) {
                // 如果正在播放同一首，则不处理
                if (mediaPlayer != null && mediaPlayer.isPlaying() && currentAudioItems.size() > currentAudioIndex && currentAudioIndex != -1) {
                    if (currentAudioItems.get(currentAudioIndex).getName().equals(file.getName())) {
                        showPlayer(null);
                        return;
                    }
                }
                
                // 播放新音频，停止旧的
                if (exoPlayer != null) exoPlayer.pause();
                
                // 收集当前目录下的所有音频，用于播放列表和切歌
                currentAudioItems.clear();
                for (CloudFile f : dataList) {
                    if (!f.isDirectory() && f.getName().toLowerCase().matches(".*\\.(mp3|wav|flac|aac|m4a)$")) {
                        currentAudioItems.add(f);
                    }
                }
                currentAudioIndex = currentAudioItems.indexOf(file);

                imgDisc.setVisibility(View.VISIBLE);
                layoutAudioControls.setVisibility(View.VISIBLE);
                tvAudioTitle.setVisibility(View.VISIBLE);
                btnAudioMenu.setVisibility(View.VISIBLE);
                tvAudioTitle.setText(file.getName());
                showPlayer(null);
                playAudio(url);
            } else if (ext.matches("pdf|doc|docx|xls|xlsx|ppt|pptx")) {
                // 文档类型：在线预览通常需要转换，这里先实现“极速预览”逻辑：下载到临时目录并调用系统打开
                previewDocument(file, url);
            } else {
                Toast.makeText(this, "暂不支持预览该格式", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "预览失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void previewDocument(CloudFile file, String url) {
        AlertDialog progressDialog = new AlertDialog.Builder(this)
                .setTitle("正在准备预览")
                .setMessage("正在下载文档: " + file.getName())
                .setCancelable(false)
                .show();

        new Thread(() -> {
            try {
                OkHttpClient client = new OkHttpClient();
                Request request = new Request.Builder()
                        .url(url)
                        .header("Authorization", Credentials.basic(user_dav, pass_dav))
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    if (response.isSuccessful() && response.body() != null) {
                        File cacheDir = getExternalCacheDir();
                        File tempFile = new File(cacheDir, file.getName());
                        
                        try (BufferedSink sink = Okio.buffer(Okio.sink(tempFile))) {
                            sink.writeAll(response.body().source());
                        }

                        runOnUiThread(() -> {
                            progressDialog.dismiss();
                            openLocalFile(tempFile);
                        });
                    } else {
                        runOnUiThread(() -> {
                            progressDialog.dismiss();
                            Toast.makeText(this, "下载预览文件失败: " + response.code(), Toast.LENGTH_SHORT).show();
                        });
                    }
                }
            } catch (Exception e) {
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(this, "预览出错: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void openLocalFile(File file) {
        try {
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
            String extension = file.getName().substring(file.getName().lastIndexOf(".") + 1).toLowerCase();
            String mimeType = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
            
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, mimeType);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            
            startActivity(Intent.createChooser(intent, "打开文档"));
        } catch (Exception e) {
            Toast.makeText(this, "无法打开该文件，请安装相应的预览应用 (如 WPS 或 Office)", Toast.LENGTH_LONG).show();
        }
    }

    private void showAiChatDialog() {
        Intent intent = new Intent(this, ChatActivity.class);
        startActivity(intent);
    }

    private void performAiAssistantRequest(String query) {
        SharedPreferences sp = getSharedPreferences("user_prefs", MODE_PRIVATE);
        String username = sp.getString("username", "admin");

        AlertDialog loading = new AlertDialog.Builder(this)
                .setMessage("AI 正在分析文件并思考中...")
                .setCancelable(false)
                .show();

        new Thread(() -> {
            try {
                // 1. 获取本地文件列表
                java.util.List<FileIndex> allFiles = db.userDao().getAllFilesForAi(username);

                // 2. 构造请求体
                JSONObject json = new JSONObject();
                json.put("username", username);
                json.put("query", query);

                org.json.JSONArray fileArray = new org.json.JSONArray();
                for (FileIndex f : allFiles) {
                    JSONObject fObj = new JSONObject();
                    fObj.put("name", f.name);
                    fObj.put("fullPath", f.fullPath);
                    fObj.put("isDir", f.isDir);
                    fileArray.put(fObj);
                }
                json.put("files", fileArray);

                // 3. 发起请求
                OkHttpClient client = new OkHttpClient.Builder()
                        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                        .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                        .build();
                RequestBody body = RequestBody.create(
                        json.toString(), MediaType.parse("application/json; charset=utf-8"));

                Request request = new Request.Builder()
                        .url("http://47.97.50.135:8000/ai-assistant")
                        .post(body)
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    String respStr = response.body().string();
                    runOnUiThread(() -> {
                        loading.dismiss();
                        if (response.isSuccessful()) {
                            try {
                                JSONObject result = new JSONObject(respStr);
                                showAiAnswer(result.getString("answer"), result.getInt("today_total"));
                            } catch (Exception e) { e.printStackTrace(); }
                        } else if (response.code() == 429) {
                            Toast.makeText(this, "今日 50000 Token 额度已用完", Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(this, "助手连接失败 (错误码: " + response.code() + ")", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            } catch (Exception e) {
                runOnUiThread(() -> {
                    loading.dismiss();
                    Toast.makeText(this, "网络请求出错: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void showAiAnswer(String answer, int totalUsed) {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("助手回答")
                .setMessage(answer + "\n\n(今日已消耗: " + totalUsed + "/50000 Token)")
                .setPositiveButton("好的", null)
                .show();
    }

    private void performSearch(String query) {
        isSearchMode = true;
        SharedPreferences sp = getSharedPreferences("user_prefs", MODE_PRIVATE);
        String username = sp.getString("username", "");

        new Thread(() -> {
            // 直接查本地库，秒出结果
            java.util.List<FileIndex> dbResults = db.userDao().searchLocalFiles(username, query);
            List<CloudFile> results = new ArrayList<>();
            for (FileIndex fi : dbResults) {
                results.add(new CloudFile(fi.name, fi.isDir, 0, fi.fullPath));
            }

            runOnUiThread(() -> {
                dataList.clear();
                dataList.addAll(results);
                adapter.notifyDataSetChanged();
                tvEmptyHint.setVisibility(dataList.isEmpty() ? View.VISIBLE : View.GONE);
                tvCurrentPath.setText("搜索结果: " + query);
                btnBackDir.setVisibility(View.VISIBLE);
            });
        }).start();
    }

    private void startIndexing() {
        SharedPreferences sp = getSharedPreferences("user_prefs", MODE_PRIVATE);
        String username = sp.getString("username", "");
        String userRoot = username + "/";

        new Thread(() -> {
            // 1. 账号切换或重新进入时，首先清空旧表，执行“重新建表”逻辑
            db.userDao().clearFileIndex(username);
            
            // 2. 开始递归扫描并批量存入
            List<FileIndex> buffer = new ArrayList<>();
            scanFolderRecursive(username, userRoot, buffer);
            
            // 3. 扫描结束，最后一次性提交剩余数据
            if (!buffer.isEmpty()) {
                db.userDao().insertAllFileIndices(buffer);
            }
        }).start();
    }

    private void scanFolderRecursive(String username, String path, List<FileIndex> buffer) {
        OkHttpClient client = new OkHttpClient();
        RequestBody body = RequestBody.create("", null);
        String url;
        try { url = CD2_URL + encodePath(path); } catch (Exception e) { return; }

        Request request = new Request.Builder()
                .url(url).method("PROPFIND", body)
                .header("Authorization", Credentials.basic(user_dav, pass_dav))
                .header("Depth", "1").build();

        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                List<CloudFile> files = parseWebDavXml(response.body().string(), path);
                List<FileIndex> currentFolderIndices = new ArrayList<>();
                
                for (CloudFile f : files) {
                    String fullPath = path + f.getName() + (f.isDirectory() ? "/" : "");
                    FileIndex fi = new FileIndex(username, f.getName(), fullPath, f.isDirectory(), f.getSize());
                    currentFolderIndices.add(fi);
                    
                    // 如果是文件夹，递归进去
                    if (f.isDirectory()) {
                        scanFolderRecursive(username, fullPath, buffer);
                    }
                }
                
                // 每扫描完一个文件夹，执行一次批量插入，提升性能
                if (!currentFolderIndices.isEmpty()) {
                    db.userDao().insertAllFileIndices(currentFolderIndices);
                    // 关键：扫描完一部分，立即刷新一次容量显示，让用户看到进度
                    refreshStorageFromDB(username);
                }
            }
        } catch (Exception e) {}
    }

    private void displayImage(CloudFile file) {
        try {
            String pathInCloud = (file.getFullPath() != null && !file.getFullPath().isEmpty()) 
                    ? file.getFullPath() : (currentPath + file.getName());
            String encodedPath = encodePath(pathInCloud);
            String url = CD2_URL + encodedPath;
            ivFullImage.setVisibility(View.VISIBLE);
            GlideUrl glideUrl = new GlideUrl(url, new LazyHeaders.Builder()
                    .addHeader("Authorization", Credentials.basic(user_dav, pass_dav))
                    .build());
            Glide.with(this).load(glideUrl).into(ivFullImage);
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void showNextImage() {
        if (currentImageIndex < currentImageItems.size() - 1) {
            currentImageIndex++;
            displayImage(currentImageItems.get(currentImageIndex));
        } else {
            Toast.makeText(this, "已经是最后一张照片啦！", Toast.LENGTH_SHORT).show();
        }
    }

    private void showPreviousImage() {
        if (currentImageIndex > 0) {
            currentImageIndex--;
            displayImage(currentImageItems.get(currentImageIndex));
        } else {
            Toast.makeText(this, "已经是第一张照片啦！", Toast.LENGTH_SHORT).show();
        }
    }

    public void onPlayerControl(View v) {
        int id = v.getId();
        if (id == R.id.btn_close_player) {
            if (videoPlayerView.getVisibility() == View.VISIBLE) {
                exitFullScreenVideo();
            } else {
                playerView.setVisibility(View.GONE);
                if (mediaPlayer != null && mediaPlayer.isPlaying()) fabShowPlayer.setVisibility(View.VISIBLE);
            }
        } else if (id == R.id.btn_play_pause) {
            if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                mediaPlayer.pause();
                imgDisc.clearAnimation();
                btnPlayPause.setImageResource(android.R.drawable.ic_media_play);
            } else if (mediaPlayer != null) {
                // 如果歌曲已播放完毕（或接近末尾），先跳回起点
                if (mediaPlayer.getCurrentPosition() >= mediaPlayer.getDuration() - 1000) {
                    mediaPlayer.seekTo(0);
                    seekBar.setProgress(0);
                }
                mediaPlayer.start();
                startDiscAnimation();
                btnPlayPause.setImageResource(android.R.drawable.ic_media_pause);
                updateSeekBar(); // 重新启动进度条监听循环
            }
        }
    }

    private void showFilteredTrans(boolean isDownload) {
        layoutTransCategories.setVisibility(View.GONE);
        layoutTransListContainer.setVisibility(View.VISIBLE);
        List<TransferTask> filtered = new ArrayList<>();
        for (TransferTask task : transList) {
            if (task.isDownload() == isDownload) filtered.add(task);
        }
        transAdapter.updateList(filtered);
    }

    public void onUploadClick(View v) {
        if (adapter != null && adapter.isSelectionMode()) {
            adapter.setSelectionMode(false);
            return;
        }
        pickFileLauncher.launch("*/*");
    }

    private String encodePath(String path) {
        try {
            String[] parts = path.split("/");
            StringBuilder sb = new StringBuilder();
            for (int j = 0; j < parts.length; j++) {
                if (parts[j].isEmpty()) continue;
                sb.append(URLEncoder.encode(parts[j], StandardCharsets.UTF_8.name()).replace("+", "%20"));
                if (j < parts.length - 1 || path.endsWith("/")) sb.append("/");
            }
            return sb.toString();
        } catch (Exception e) {
            return path;
        }
    }

    public void onNewFolderClick(View v) {
        EditText et = new EditText(this);
        et.setHint("文件夹名称");
        new AlertDialog.Builder(this)
                .setTitle("在当前目录下新建")
                .setView(et)
                .setPositiveButton("创建", (dialog, which) -> {
                    String folderName = et.getText().toString().trim();
                    if (!folderName.isEmpty()) createFolder(currentPath + folderName);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void createFolder(String name) {
        new Thread(() -> {
            try {
                Request request = new Request.Builder()
                        .url(CD2_URL + encodePath(name))
                        .method("MKCOL", null)
                        .header("Authorization", Credentials.basic(user_dav, pass_dav))
                        .build();
                OkHttpClient client = new OkHttpClient();
                try (Response response = client.newCall(request).execute()) {
                    runOnUiThread(() -> {
                        if (response.isSuccessful()) {
                            Toast.makeText(this, "创建成功", Toast.LENGTH_SHORT).show();
                            
                            // --- 同步更新本地搜索索引 ---
                            new Thread(() -> {
                                SharedPreferences sp1 = getSharedPreferences("user_prefs", MODE_PRIVATE);
                                String uname = sp1.getString("username", "");
                                // 提取文件夹名
                                String folderName = name.substring(name.lastIndexOf("/") + 1);
                                String fPath = name.endsWith("/") ? name : (name + "/");
                                db.userDao().insertFileIndex(new FileIndex(uname, folderName, fPath, true, 0));
                            }).start();

                            fetchFilesFromCD2();
                        } else {
                            Toast.makeText(this, "创建失败: " + response.code(), Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            } catch (Exception e) { e.printStackTrace(); }
        }).start();
    }

    public void onMoveSelectedClick(View v) {
        if (adapter != null && adapter.isSelectionMode()) {
            java.util.Set<CloudFile> selected = adapter.getSelectedFiles();
            if (selected.isEmpty()) {
                Toast.makeText(this, "请选择要移动的文件", Toast.LENGTH_SHORT).show();
                return;
            }
            showFolderPickerDialog(selected);
        }
    }

    public void onDeleteSelectedClick(View v) {
        if (adapter != null && adapter.isSelectionMode()) {
            java.util.Set<CloudFile> selected = adapter.getSelectedFiles();
            if (selected.isEmpty()) {
                Toast.makeText(this, "请选择要删除的文件", Toast.LENGTH_SHORT).show();
                return;
            }
            confirmBulkDelete(selected);
        }
    }

    private String pickerPath = "";

    private boolean isPickerRefreshing = false;

    private void showFolderPickerDialog(java.util.Set<CloudFile> filesToMove) {
        SharedPreferences sp = getSharedPreferences("user_prefs", MODE_PRIVATE);
        String username = sp.getString("username", "");
        String userRoot = username + "/";
        pickerPath = userRoot;

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_folder_picker, null);
        RecyclerView rvPicker = dialogView.findViewById(R.id.rv_picker);
        TextView tvPickerPath = dialogView.findViewById(R.id.tv_picker_path);
        ImageButton btnPickerBack = dialogView.findViewById(R.id.btn_picker_back);
        
        List<CloudFile> pickerData = new ArrayList<>();
        FileAdapter pickerAdapter = new FileAdapter(pickerData);
        rvPicker.setLayoutManager(new LinearLayoutManager(this));
        rvPicker.setAdapter(pickerAdapter);

        AlertDialog pickerDialog = new AlertDialog.Builder(this)
                .setTitle("移动至...")
                .setView(dialogView)
                .setPositiveButton("移动到此处", (d, w) -> {
                    for (CloudFile file : filesToMove) {
                        String oldPath = (file.getFullPath() != null && !file.getFullPath().isEmpty()) 
                                ? file.getFullPath() : (currentPath + file.getName());
                        moveOrRename(oldPath, pickerPath + file.getName());
                    }
                    adapter.setSelectionMode(false);
                })
                .setNegativeButton("取消", null)
                .create();
        
        pickerDialog.show();

        pickerAdapter.setOnItemClickListener(new FileAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(CloudFile file) {
                if (file.isDirectory() && !isPickerRefreshing) {
                    pickerPath += file.getName() + "/";
                    refreshPickerUI(username, tvPickerPath, btnPickerBack, pickerData, pickerAdapter);
                }
            }
            @Override public void onSelectionModeChanged(boolean enabled) {}
        });

        btnPickerBack.setOnClickListener(v -> {
            if (isPickerRefreshing || pickerPath.equals(userRoot)) return;
            String temp = pickerPath.substring(0, pickerPath.length() - 1);
            int last = temp.lastIndexOf("/");
            if (last != -1) pickerPath = temp.substring(0, last + 1);
            else pickerPath = userRoot;
            refreshPickerUI(username, tvPickerPath, btnPickerBack, pickerData, pickerAdapter);
        });

        refreshPickerUI(username, tvPickerPath, btnPickerBack, pickerData, pickerAdapter);
    }

    private void refreshPickerUI(String username, TextView tvPath, ImageButton btnBack, 
                                List<CloudFile> data, FileAdapter adapter) {
        isPickerRefreshing = true;
        String displayPath = pickerPath.replace(username + "/", "");
        tvPath.setText(displayPath.isEmpty() ? "根目录" : displayPath);
        btnBack.setVisibility(pickerPath.equals(username + "/") ? View.GONE : View.VISIBLE);
        
        // 立即清空列表，防止加载过程中重复点击
        data.clear();
        adapter.notifyDataSetChanged();
        
        OkHttpClient client = new OkHttpClient();
        RequestBody body = RequestBody.create("", null);
        String url;
        try { url = CD2_URL + encodePath(pickerPath); } catch (Exception e) { url = CD2_URL; }

        Request request = new Request.Builder()
                .url(url).method("PROPFIND", body)
                .header("Authorization", Credentials.basic(user_dav, pass_dav))
                .header("Depth", "1").build();

        new Thread(() -> {
            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    // 关键：在选择器刷新时，传入 pickerPath 作为剥离基准
                    List<CloudFile> all = parseWebDavXml(response.body().string(), pickerPath);
                    List<CloudFile> folders = new ArrayList<>();
                    for (CloudFile f : all) if (f.isDirectory()) folders.add(f);
                    runOnUiThread(() -> {
                        data.addAll(folders);
                        adapter.notifyDataSetChanged();
                        isPickerRefreshing = false;
                    });
                } else {
                    isPickerRefreshing = false;
                }
            } catch (Exception e) {
                isPickerRefreshing = false;
            }
        }).start();
    }

    private void confirmBulkDelete(java.util.Set<CloudFile> files) {
        new AlertDialog.Builder(this)
                .setTitle("确认批量删除")
                .setMessage("确定要删除选中的 " + files.size() + " 个项目吗？")
                .setPositiveButton("删除", (dialog, which) -> {
                    for (CloudFile file : files) {
                        deleteFileFromCloud(currentPath + file.getName());
                    }
                    adapter.setSelectionMode(false);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void confirmDelete(CloudFile file) {
        new AlertDialog.Builder(this)
                .setTitle("确认删除")
                .setMessage("确定要删除 " + file.getName() + " 吗？")
                .setPositiveButton("删除", (dialog, which) -> deleteFileFromCloud(currentPath + file.getName()))
                .setNegativeButton("取消", null)
                .show();
    }

    private void moveOrRename(String oldPath, String newPath) {
        new Thread(() -> {
            try {
                Request request = new Request.Builder()
                        .url(CD2_URL + encodePath(oldPath))
                        .method("MOVE", null)
                        .header("Authorization", Credentials.basic(user_dav, pass_dav))
                        .header("Destination", CD2_URL + encodePath(newPath))
                        .build();
                OkHttpClient client = new OkHttpClient();
                try (Response response = client.newCall(request).execute()) {
                    runOnUiThread(() -> {
                        if (response.isSuccessful()) {
                            Toast.makeText(this, "操作成功", Toast.LENGTH_SHORT).show();
                            
                            // 重命名/移动后，最稳妥的是清除该用户索引并重新触发一次静默扫描
                            // 或者这里简单处理：直接触发一次扫描
                            startIndexing();
                            
                            fetchFilesFromCD2();
                        } else {
                            Toast.makeText(this, "操作失败: " + response.code(), Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            } catch (Exception e) { e.printStackTrace(); }
        }).start();
    }

    private void deleteFileFromCloud(String path) {
        new Thread(() -> {
            try {
                Request request = new Request.Builder()
                        .url(CD2_URL + encodePath(path))
                        .delete()
                        .header("Authorization", Credentials.basic(user_dav, pass_dav))
                        .build();
                OkHttpClient client = new OkHttpClient();
                try (Response response = client.newCall(request).execute()) {
                    runOnUiThread(() -> {
                        if (response.isSuccessful()) {
                            Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show();
                            
                            // --- 同步更新本地搜索索引 ---
                            new Thread(() -> {
                                SharedPreferences sp1 = getSharedPreferences("user_prefs", MODE_PRIVATE);
                                String uname = sp1.getString("username", "");
                                // 如果删除的是文件夹，执行递归删除索引；否则删除单条
                                if (path.endsWith("/")) {
                                    db.userDao().deleteFolderIndexRecursive(uname, path);
                                } else {
                                    db.userDao().deleteFileIndex(uname, path);
                                }
                            }).start();

                            fetchFilesFromCD2();
                        } else {
                            Toast.makeText(this, "删除失败: " + response.code(), Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            } catch (Exception e) { e.printStackTrace(); }
        }).start();
    }

    private void showChangePasswordDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_change_password, null);
        EditText etOld = view.findViewById(R.id.et_old_password);
        EditText etNew = view.findViewById(R.id.et_new_password);
        EditText etConfirm = view.findViewById(R.id.et_confirm_new_password);

        new AlertDialog.Builder(this)
                .setTitle("修改登录密码")
                .setView(view)
                .setPositiveButton("立即修改", (dialog, which) -> {
                    String oldP = etOld.getText().toString().trim();
                    String newP = etNew.getText().toString().trim();
                    String confirmP = etConfirm.getText().toString().trim();

                    if (oldP.isEmpty() || newP.isEmpty()) {
                        Toast.makeText(this, "密码不能为空", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (!newP.equals(confirmP)) {
                        Toast.makeText(this, "两次新密码输入不一致", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    performChangePassword(oldP, newP, null);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void performChangePassword(String oldP, String newP, String code) {
        SharedPreferences sp = getSharedPreferences("user_prefs", MODE_PRIVATE);
        String username = sp.getString("username", "");

        new Thread(() -> {
            try {
                JSONObject json = new JSONObject();
                json.put("username", username);
                json.put("oldPassword", oldP);
                json.put("newPassword", newP);
                if (code != null) json.put("code", code);

                RequestBody body = RequestBody.create(json.toString(), MediaType.parse("application/json"));
                Request request = new Request.Builder()
                        .url(Config.getBackendUrl() + "/change_password")
                        .post(body)
                        .build();

                OkHttpClient client = new OkHttpClient();
                try (Response response = client.newCall(request).execute()) {
                    String respStr = response.body().string();
                    if (response.isSuccessful()) {
                        if ("NEED_2FA".equals(respStr)) {
                            runOnUiThread(() -> show2FADialogForPassword(oldP, newP));
                        } else {
                            runOnUiThread(() -> {
                                Toast.makeText(this, "密码修改成功！请重新登录", Toast.LENGTH_LONG).show();
                                sp.edit().clear().apply();
                                startActivity(new Intent(this, MainActivity.class));
                                finish();
                            });
                        }
                    } else {
                        runOnUiThread(() -> {
                            String error = "FAIL_2FA".equals(respStr) ? "验证码错误" : ("修改失败: " + respStr);
                            Toast.makeText(this, error, Toast.LENGTH_SHORT).show();
                        });
                    }
                }
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "请求失败", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void show2FADialogForPassword(String oldP, String newP) {
        EditText etCode = new EditText(this);
        etCode.setHint("请输入 6 位安全验证码");
        etCode.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        etCode.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(6)});

        int padding = (int) (24 * getResources().getDisplayMetrics().density);
        android.widget.FrameLayout container = new android.widget.FrameLayout(this);
        container.setPadding(padding, padding / 2, padding, 0);
        container.addView(etCode);

        new AlertDialog.Builder(this)
                .setTitle("安全验证")
                .setView(container)
                .setPositiveButton("验证并修改", (dialog, which) -> {
                    String code = etCode.getText().toString();
                    if (code.length() == 6) {
                        performChangePassword(oldP, newP, code);
                    } else {
                        Toast.makeText(this, "请输入6位验证码", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void toggle2FA(boolean enable) {
        SharedPreferences sp = getSharedPreferences("user_prefs", MODE_PRIVATE);
        String username = sp.getString("username", "");
        String password = sp.getString("password", "");

        new Thread(() -> {
            try {
                JSONObject json = new JSONObject();
                json.put("username", username);
                json.put("password", password);
                json.put("action", enable ? "enable" : "disable");

                RequestBody body = RequestBody.create(json.toString(), MediaType.parse("application/json"));
                Request request = new Request.Builder()
                        .url(Config.getBackendUrl() + "/manage_2fa")
                        .post(body)
                        .build();

                OkHttpClient client = new OkHttpClient();
                try (Response response = client.newCall(request).execute()) {
                    if (response.isSuccessful()) {
                        String respStr = response.body().string();
                        JSONObject respJson = new JSONObject(respStr);
                        boolean isEnabled = respJson.optBoolean("twoFactorEnabled", false);
                        org.json.JSONArray secret = respJson.optJSONArray("twoFactorSecret");

                        runOnUiThread(() -> {
                            switch2FA.setChecked(isEnabled);
                            btnViewQR.setVisibility(isEnabled ? View.VISIBLE : View.GONE);
                            if (enable && isEnabled && secret != null) {
                                try {
                                    JSONObject qrJson = new JSONObject();
                                    qrJson.put("issuer", "CloudDisk");
                                    qrJson.put("account", username);
                                    qrJson.put("secret", secret);
                                    
                                    show2FAQRCodeDialog(username, qrJson.toString());
                                } catch (Exception ignored) {}
                            } else if (!enable && !isEnabled) {
                                Toast.makeText(this, "安全盾已关闭", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                }
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "2FA操作失败", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void downloadCDAuthy() {
        String url = "https://gh-proxy.org/https://github.com/SmartZWZ/CDAuthy/releases/download/V1.0/CDAuthy-V1.0.apk";
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
        request.setTitle("下载 CDAuthy 验证器");
        request.setDescription("配套 CloudDisk 安全盾使用");
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        request.setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, "CDAuthy-V1.0.apk");

        DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
        if (manager != null) {
            manager.enqueue(request);
            Toast.makeText(this, "正在为您下载配套验证器 CDAuthy...", Toast.LENGTH_LONG).show();
        }
    }

    private void show2FAQRCodeDialog(String username, String secretJson) {
        String qrUrl = "https://api.qrserver.com/v1/create-qr-code/?size=500x500&data=" + android.net.Uri.encode(secretJson);

        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setGravity(android.view.Gravity.CENTER_HORIZONTAL);

        android.widget.ImageView imageView = new android.widget.ImageView(this);
        int size = (int) (250 * getResources().getDisplayMetrics().density);
        android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(size, size);
        imageView.setLayoutParams(lp);

        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        imageView.setPadding(padding, padding, padding, padding);

        Glide.with(this)
                .load(qrUrl)
                .placeholder(android.R.drawable.progress_indeterminate_horizontal)
                .error(android.R.drawable.stat_notify_error)
                .into(imageView);
        
        layout.addView(imageView);

        // 添加下载配套验证器按钮
        com.google.android.material.button.MaterialButton btnDownload = new com.google.android.material.button.MaterialButton(this);
        btnDownload.setText("下载配套验证器 (CDAuthy)");
        android.widget.LinearLayout.LayoutParams btnLp = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        btnLp.setMargins(0, 0, 0, padding);
        btnDownload.setLayoutParams(btnLp);
        btnDownload.setOnClickListener(v -> downloadCDAuthy());
        layout.addView(btnDownload);

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("2FA 安全盾绑定")
                .setMessage("账号: " + username + "\n\n⚠️ 重要提示：\n请使用 CDAuthy 扫描二维码。若未安装，请点击下方按钮下载。建议【截图保存】此二维码以防丢失。")
                .setView(layout)
                .setPositiveButton("我已安全保存", null)
                .show();
    }

    private void checkUpdateMe() {
        Toast.makeText(this, "正在检查更新...", Toast.LENGTH_SHORT).show();
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder().url(Config.UPDATE_URL).build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull java.io.IOException e) {
                runOnUiThread(() -> Toast.makeText(FileListActivity.this, "检查失败，请检查网络", Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws java.io.IOException {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        String jsonStr = response.body().string();
                        JSONObject json = new JSONObject(jsonStr);
                        int remoteVersionCode = json.getInt("versionCode");
                        String versionName = json.getString("versionName");
                        String updateMessage = json.getString("updateMessage");
                        String downloadUrl = json.getString("downloadUrl");

                        if (remoteVersionCode > BuildConfig.VERSION_CODE) {
                            runOnUiThread(() -> showUpdateDialog(versionName, updateMessage, downloadUrl));
                        } else {
                            runOnUiThread(() -> Toast.makeText(FileListActivity.this, "当前已是最新版本", Toast.LENGTH_SHORT).show());
                        }
                    } catch (Exception e) {
                        runOnUiThread(() -> Toast.makeText(FileListActivity.this, "解析失败", Toast.LENGTH_SHORT).show());
                    }
                }
            }
        });
    }

    private void showUpdateDialog(String versionName, String message, String url) {
        boolean[] useProxy = {true}; // 默认开启加速代理

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("发现新版本: " + versionName)
                .setMessage(message)
                .setCancelable(false)
                .setMultiChoiceItems(new CharSequence[]{"使用 GitHub 加速下载 (推荐)"}, new boolean[]{true}, (dialog, which, isChecked) -> {
                    useProxy[0] = isChecked;
                })
                .setPositiveButton("立即下载", (dialog, which) -> {
                    String finalUrl = useProxy[0] ? (Config.GITHUB_PROXY + url) : url;
                    Toast.makeText(this, "开始下载新版本...", Toast.LENGTH_SHORT).show();
                    downloadNewVersion(finalUrl, versionName);
                })
                .setNegativeButton("稍后再说", null)
                .show();
    }

    private void downloadNewVersion(String url, String versionName) {
        android.app.DownloadManager.Request request = new android.app.DownloadManager.Request(Uri.parse(url));
        request.setTitle("CloudDisk 更新 - " + versionName);
        request.setDescription("正在下载新版本安装包...");
        request.setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        String fileName = "CloudDisk_v" + versionName + ".apk";
        request.setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, fileName);

        android.app.DownloadManager manager = (android.app.DownloadManager) getSystemService(DOWNLOAD_SERVICE);
        if (manager != null) {
            manager.enqueue(request);
        }
    }

    private void showAboutDialog() {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("关于 CloudDisk")
                .setMessage("CloudDisk 一站式私有云存储解决方案\n\n" +
                        "当前版本: " + BuildConfig.VERSION_NAME + "\n" +
                        "开发者: ZWZ\n" +
                        "联系我们: support@zwz.com\n\n" +
                        "Copyright © 2026 ZWZ Studio.\nAll Rights Reserved.")
                .setPositiveButton("确定", null)
                .show();
    }

    private void cancelAccount(String username, String code) {
        SharedPreferences sp = getSharedPreferences("user_prefs", MODE_PRIVATE);
        String password = sp.getString("password", "");

        new Thread(() -> {
            try {
                // 1. 通知云端后台注销账号并作废邀请码
                OkHttpClient client = new OkHttpClient();
                JSONObject json = new JSONObject();
                json.put("username", username);
                json.put("password", password);
                if (code != null) json.put("code", code);

                RequestBody body = RequestBody.create(json.toString(), MediaType.parse("application/json"));
                Request cancelReq = new Request.Builder()
                        .url(Config.getBackendUrl() + "/cancel_account")
                        .post(body)
                        .build();

                try (Response resp = client.newCall(cancelReq).execute()) {
                    String respStr = resp.body() != null ? resp.body().string() : "";
                    if (!resp.isSuccessful()) {
                        String error = "FAIL_2FA".equals(respStr) ? "验证码错误" : ("云端注销失败: " + respStr);
                        runOnUiThread(() -> Toast.makeText(this, error, Toast.LENGTH_SHORT).show());
                        return;
                    }

                    if ("NEED_2FA".equals(respStr)) {
                        runOnUiThread(() -> show2FADialogForCancel(username));
                        return;
                    }
                }

                // 2. 尝试删除云端 WebDAV 文件夹（即使失败也继续注销流程）
                try {
                    Request davReq = new Request.Builder()
                            .url(CD2_URL + encodePath(username))
                            .delete()
                            .header("Authorization", Credentials.basic(Config.WEBDAV_USER, Config.WEBDAV_PASS))
                            .build();
                    client.newCall(davReq).execute().close(); 
                } catch (Exception e) {
                    e.printStackTrace(); // 文件夹删除失败不影响账号注销逻辑
                }
                
                // 3. 清空本地数据库相关缓存
                db.userDao().clearFileIndex(username);
                db.userDao().deleteUser(username); // 同时删除本地 User 表记录
                
                // 4. 清除登录状态并退出
                runOnUiThread(() -> {
                    sp.edit().clear().apply();
                    Toast.makeText(this, "账户已永久注销，邀请码已作废", Toast.LENGTH_LONG).show();
                    startActivity(new Intent(this, MainActivity.class));
                    finish();
                });
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(this, "操作失败，请检查网络", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void show2FADialogForCancel(String username) {
        EditText etCode = new EditText(this);
        etCode.setHint("请输入 6 位安全验证码");
        etCode.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        etCode.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(6)});

        int padding = (int) (24 * getResources().getDisplayMetrics().density);
        android.widget.FrameLayout container = new android.widget.FrameLayout(this);
        container.setPadding(padding, padding / 2, padding, 0);
        container.addView(etCode);

        new AlertDialog.Builder(this)
                .setTitle("安全验证")
                .setMessage("注销账号属于敏感操作，请输入 6 位安全验证码。")
                .setView(container)
                .setPositiveButton("验证并注销", (dialog, which) -> {
                    String code = etCode.getText().toString();
                    if (code.length() == 6) {
                        cancelAccount(username, code);
                    } else {
                        Toast.makeText(this, "请输入6位验证码", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void uploadFile(Uri uri) {
        String fileName = getFileName(uri);
        SharedPreferences sp = getSharedPreferences("user_prefs", MODE_PRIVATE);
        String username = sp.getString("username", "admin");

        new Thread(() -> {
            try (InputStream is = getContentResolver().openInputStream(uri)) {
                if (is == null) return;
                long uploadFileSize = is.available();

                // 核心：上传前容量校验
                long currentUsed = db.userDao().getTotalUsedSize(username);
                if (currentUsed + uploadFileSize > TOTAL_CAPACITY) {
                    runOnUiThread(() -> {
                        new AlertDialog.Builder(this)
                                .setTitle("云空间不足")
                                .setMessage(String.format("待上传文件大小: %.2f MB\n您的 1TB 专属空间已满，无法继续上传任务。", uploadFileSize / 1024.0 / 1024.0))
                                .setPositiveButton("我知道了", null)
                                .show();
                    });
                    return;
                }

                // 通过校验，继续原有上传逻辑
                runOnUiThread(() -> {
                    TransferTask task = new TransferTask(System.currentTimeMillis(), fileName, false);
                    task.setStatusText("正在上传...");
                    transList.add(0, task);
                    transAdapter.notifyItemInserted(0);
                    rvTrans.scrollToPosition(0);
                    startUploadTask(uri, fileName, uploadFileSize, task);
                });

            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void startUploadTask(Uri uri, String fileName, long totalSize, TransferTask task) {
        new Thread(() -> {
            try (InputStream is = getContentResolver().openInputStream(uri)) {
                RequestBody requestBody = new RequestBody() {
                    @Override public MediaType contentType() { return MediaType.parse("application/octet-stream"); }
                    @Override public long contentLength() { return totalSize; }
                    @Override public void writeTo(BufferedSink sink) throws java.io.IOException {
                        try (Source source = Okio.source(is)) {
                            long totalBytesRead = 0;
                            long read;
                            int lastProgress = -1;
                            while ((read = source.read(sink.getBuffer(), 2048)) != -1) {
                                totalBytesRead += read;
                                sink.flush();
                                int progress = (int) ((totalBytesRead * 100) / totalSize);
                                if (progress != lastProgress) {
                                    lastProgress = progress;
                                    runOnUiThread(() -> {
                                        task.setProgress(progress);
                                        transAdapter.notifyDataSetChanged();
                                    });
                                }
                            }
                        }
                    }
                };

                Request request = new Request.Builder()
                        .url(CD2_URL + encodePath(currentPath + fileName))
                        .put(requestBody)
                        .header("Authorization", Credentials.basic(user_dav, pass_dav))
                        .build();

                OkHttpClient client = new OkHttpClient();
                try (Response response = client.newCall(request).execute()) {
                    runOnUiThread(() -> {
                        if (response.isSuccessful()) {
                            task.setProgress(100);
                            task.setStatusText("上传完成");
                            
                            // --- 同步更新本地搜索索引 ---
                            new Thread(() -> {
                                SharedPreferences sp1 = getSharedPreferences("user_prefs", MODE_PRIVATE);
                                String uname = sp1.getString("username", "");
                                String fPath = currentPath + fileName;
                                db.userDao().insertFileIndex(new FileIndex(uname, fileName, fPath, false, totalSize));
                                refreshStorageFromDB(uname);
                            }).start();

                            fetchFilesFromCD2(); // 刷新列表
                        } else {
                            task.setStatusText("上传失败: " + response.code());
                        }
                        transAdapter.notifyDataSetChanged();
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    task.setStatusText("上传出错");
                    transAdapter.notifyDataSetChanged();
                });
            }
        }).start();
    }

    private String getFileName(Uri uri) {
        String result = null;
        if (uri.getScheme() != null && uri.getScheme().equals("content")) {
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (index != -1) result = cursor.getString(index);
                }
            }
        }
        if (result == null) {
            result = uri.getPath();
            if (result != null) {
                int cut = result.lastIndexOf('/');
                if (cut != -1) result = result.substring(cut + 1);
            }
        }
        return result != null ? result : "unknown_file";
    }

    public void onDownloadClick(View v) {
        if (adapter != null && adapter.isSelectionMode()) {
            java.util.Set<CloudFile> selected = adapter.getSelectedFiles();
            if (selected.isEmpty()) {
                Toast.makeText(this, "请选择要下载的文件", Toast.LENGTH_SHORT).show();
                return;
            }

            for (CloudFile file : selected) {
                long id = downloadFile(file);
                if (id != -1) {
                    transList.add(0, new TransferTask(id, file.getName(), true));
                }
            }
            transAdapter.notifyDataSetChanged();
            updateDownloadProgress();

            Toast.makeText(this, "已加入下载队列", Toast.LENGTH_SHORT).show();
            adapter.setSelectionMode(false); // 下载后退出多选模式
        }
    }

    private long downloadFile(CloudFile file) {
        try {
            String encodedPath = encodePath(currentPath + file.getName());
            String downloadUrl = CD2_URL + encodedPath;

            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(downloadUrl));
            request.setTitle(file.getName());
            request.setDescription("云盘文件下载");
            request.addRequestHeader("Authorization", Credentials.basic(user_dav, pass_dav));
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, file.getName());

            DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            if (manager != null) {
                return manager.enqueue(request);
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "下载失败: " + file.getName(), Toast.LENGTH_SHORT).show();
        }
        return -1;
    }

    private void updateDownloadProgress() {
        if (transList.isEmpty()) return;

        DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
        boolean hasActiveTask = false;

        for (TransferTask task : transList) {
            if (task.getProgress() >= 100 && task.getStatusText().equals("下载完成")) continue;

            DownloadManager.Query query = new DownloadManager.Query();
            query.setFilterById(task.getId());
            try (Cursor cursor = manager.query(query)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int bytesDownloaded = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
                    int bytesTotal = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
                    int status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));

                    if (status == DownloadManager.STATUS_SUCCESSFUL) {
                        task.setProgress(100);
                        task.setStatusText("下载完成");
                    } else if (status == DownloadManager.STATUS_FAILED) {
                        int reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON));
                        task.setStatusText("下载失败 (码:" + reason + ")");
                    } else if (status == DownloadManager.STATUS_PENDING) {
                        task.setStatusText("等待中...");
                        hasActiveTask = true;
                    } else if (status == DownloadManager.STATUS_PAUSED) {
                        task.setStatusText("暂停中...");
                        hasActiveTask = true;
                    } else if (status == DownloadManager.STATUS_RUNNING) {
                        hasActiveTask = true;
                        if (bytesTotal > 0) {
                            int progress = (int) ((bytesDownloaded * 100L) / bytesTotal);
                            task.setProgress(progress);
                            task.setStatusText("正在下载 " + progress + "%");
                        } else {
                            task.setStatusText("正在连接...");
                        }
                    } else {
                        hasActiveTask = true;
                    }
                }
            }
        }
        transAdapter.notifyDataSetChanged();
        if (hasActiveTask) {
            handler.postDelayed(this::updateDownloadProgress, 1000);
        }
    }

    private void playAudio(String url) {
        try {
            if (mediaPlayer == null) {
                mediaPlayer = new MediaPlayer();
                mediaPlayer.setOnCompletionListener(mp -> onAudioComplete());
            } else {
                mediaPlayer.reset();
            }
            if (seekBar != null) seekBar.setProgress(0); // 播放前强制归位进度条
            
            Map<String, String> headers = new HashMap<>();
            headers.put("Authorization", Credentials.basic(user_dav, pass_dav));
            mediaPlayer.setDataSource(this, Uri.parse(url), headers);
            mediaPlayer.prepareAsync();
            mediaPlayer.setOnPreparedListener(mp -> {
                mp.start();
                startDiscAnimation();
                btnPlayPause.setImageResource(android.R.drawable.ic_media_pause);
                if (seekBar != null) {
                    seekBar.setMax(mp.getDuration());
                }
                updateSeekBar();
                
                // 激活跑马灯文字
                tvAudioTitle.setSelected(true);
            });
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void onAudioComplete() {
        if (seekBar != null) seekBar.setProgress(seekBar.getMax());
        handler.postDelayed(() -> {
            if (playMode == 1) { // 单曲循环
                if (currentAudioIndex != -1) playAudioFile(currentAudioItems.get(currentAudioIndex));
            } else if (playMode == 2) { // 循环播放
                playNextAudio();
            } else { // 顺序播放
                if (currentAudioIndex < currentAudioItems.size() - 1) {
                    playNextAudio();
                } else {
                    imgDisc.clearAnimation();
                    btnPlayPause.setImageResource(android.R.drawable.ic_media_play);
                }
            }
        }, 500);
    }

    private void playNextAudio() {
        if (currentAudioItems.isEmpty()) return;
        currentAudioIndex = (currentAudioIndex + 1) % currentAudioItems.size();
        playAudioFile(currentAudioItems.get(currentAudioIndex));
    }

    private void playPreviousAudio() {
        if (currentAudioItems.isEmpty()) return;
        currentAudioIndex = (currentAudioIndex - 1 + currentAudioItems.size()) % currentAudioItems.size();
        playAudioFile(currentAudioItems.get(currentAudioIndex));
    }

    private void playAudioFile(CloudFile file) {
        String pathInCloud = (file.getFullPath() != null && !file.getFullPath().isEmpty()) 
                ? file.getFullPath() : (currentPath + file.getName());
        String url = CD2_URL + encodePath(pathInCloud);
        tvAudioTitle.setText(file.getName());
        playAudio(url);
    }

    private void showAudioMenu() {
        PopupMenu popup = new PopupMenu(this, btnAudioMenu);
        popup.getMenu().add(0, 10, 0, "播放模式: " + getPlayModeName());
        popup.getMenu().add(0, 20, 1, "播放列表");
        
        popup.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 10) {
                playMode = (playMode + 1) % 3;
                Toast.makeText(this, "切换为: " + getPlayModeName(), Toast.LENGTH_SHORT).show();
            } else if (item.getItemId() == 20) {
                showPlaylistDialog();
            }
            return true;
        });
        popup.show();
    }

    private String getPlayModeName() {
        switch (playMode) {
            case 1: return "单曲循环";
            case 2: return "循环播放";
            default: return "顺序播放";
        }
    }

    private void showPlaylistDialog() {
        String[] names = new String[currentAudioItems.size()];
        for (int i = 0; i < currentAudioItems.size(); i++) names[i] = currentAudioItems.get(i).getName();
        
        new AlertDialog.Builder(this)
                .setTitle("当前播放列表")
                .setItems(names, (d, which) -> {
                    currentAudioIndex = which;
                    playAudioFile(currentAudioItems.get(which));
                })
                .show();
    }

    @androidx.media3.common.util.UnstableApi
    private void fetchFilesFromCD2() {
        isSearchMode = false;
        runOnUiThread(() -> etSearch.setText(""));

        // 更新 UI 状态
        SharedPreferences sp = getSharedPreferences("user_prefs", MODE_PRIVATE);
        String userRoot = sp.getString("username", "") + "/";
        
        // 界面显示路径时，把用户名那一部分去掉，让用户觉得自己就在根目录
        String displayPath = currentPath.replace(userRoot, "");
        tvCurrentPath.setText(displayPath.isEmpty() ? "全部文件" : displayPath);
        btnBackDir.setVisibility(currentPath.equals(userRoot) ? View.GONE : View.VISIBLE);

        OkHttpClient client = new OkHttpClient();
        RequestBody body = RequestBody.create("", null);
        String url;
        try {
            url = CD2_URL + encodePath(currentPath);
        } catch (Exception e) { url = CD2_URL; }

        Request request = new Request.Builder()
                .url(url).method("PROPFIND", body)
                .header("Authorization", Credentials.basic(user_dav, pass_dav))
                .header("Depth", "1").build();

        new Thread(() -> {
            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String xmlData = response.body().string();
                    List<CloudFile> parsedList = parseWebDavXml(xmlData, currentPath);
                    
                    // 排序逻辑：文件夹优先，然后按字母顺序
                    Collections.sort(parsedList, (f1, f2) -> {
                        if (f1.isDirectory() && !f2.isDirectory()) return -1;
                        if (!f1.isDirectory() && f2.isDirectory()) return 1;
                        return f1.getName().compareToIgnoreCase(f2.getName());
                    });

                    runOnUiThread(() -> {
                        dataList.clear();
                        dataList.addAll(parsedList);
                        adapter.notifyDataSetChanged();
                        updateStorageUI(parsedList);
                        tvEmptyHint.setVisibility(dataList.isEmpty() ? View.VISIBLE : View.GONE);
                    });
                }
            } catch (Exception e) { e.printStackTrace(); }
        }).start();
    }

    private List<CloudFile> parseWebDavXml(String xml, String relativeRoot) {
        List<CloudFile> files = new ArrayList<>();
        try {
            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            factory.setNamespaceAware(true);
            XmlPullParser parser = factory.newPullParser();
            parser.setInput(new StringReader(xml));
            String currentHref = null;
            long currentSize = 0;
            boolean isDir = false;
            int eventType = parser.getEventType();
            
            while (eventType != XmlPullParser.END_DOCUMENT) {
                String tagName = parser.getName();
                if (eventType == XmlPullParser.START_TAG) {
                    if ("href".equalsIgnoreCase(tagName)) {
                        try {
                            currentHref = URLDecoder.decode(parser.nextText(), StandardCharsets.UTF_8.name());
                        } catch (Exception e) {
                            currentHref = null;
                        }
                    }
                    else if ("getcontentlength".equalsIgnoreCase(tagName)) currentSize = Long.parseLong(parser.nextText());
                    else if ("collection".equalsIgnoreCase(tagName)) isDir = true;
                    else if ("quota-used-bytes".equalsIgnoreCase(tagName)) {
                        try { cloudUsedBytes = Long.parseLong(parser.nextText()); } catch (Exception e) {}
                    }
                } else if (eventType == XmlPullParser.END_TAG && "response".equalsIgnoreCase(tagName)) {
                    if (currentHref != null) {
                        // 移除 WebDAV 通用前缀 /dav/
                        String name = currentHref;
                        if (name.contains("/dav/")) {
                            name = name.substring(name.indexOf("/dav/") + 5);
                        }
                        
                        // 关键修复：使用传入的相对根路径剥离前缀，防止路径叠加
                        if (!relativeRoot.isEmpty() && name.startsWith(relativeRoot)) {
                            name = name.substring(relativeRoot.length());
                        }
                        
                        // 移除首尾斜杠
                        if (name.startsWith("/")) name = name.substring(1);
                        if (name.endsWith("/")) name = name.substring(0, name.length() - 1);

                        // 最终判断：如果剥离后不含斜杠且不为空，说明它是当前目录的直接子项
                        if (!name.isEmpty() && !name.contains("/")) {
                            files.add(new CloudFile(name, isDir, currentSize));
                        }
                    }
                    currentHref = null; currentSize = 0; isDir = false;
                }
                eventType = parser.next();
            }
        } catch (Exception e) { e.printStackTrace(); }
        return files;
    }

    private void refreshStorageFromDB(String username) {
        new Thread(() -> {
            long total = db.userDao().getTotalUsedSize(username);
            applyStorageToUI(total);
        }).start();
    }

    private void updateStorageUI(List<CloudFile> files) {
        SharedPreferences sp = getSharedPreferences("user_prefs", MODE_PRIVATE);
        String username = sp.getString("username", "admin");
        refreshStorageFromDB(username);
    }

    private void applyStorageToUI(long usedBytes) {
        double usedGB = usedBytes / (1024.0 * 1024 * 1024);
        int percent = (int) ((usedBytes * 100) / TOTAL_CAPACITY);
        runOnUiThread(() -> {
            tvStorageText.setText(String.format(Locale.getDefault(), "已使用: %.2f GB / 1024 GB", usedGB));
            storageProgress.setProgress(Math.min(percent, 100));
        });
    }

    private void startDiscAnimation() {
        if (imgDisc != null && mediaPlayer != null && mediaPlayer.isPlaying()) {
            Animation rotate = AnimationUtils.loadAnimation(this, R.anim.rotate);
            imgDisc.startAnimation(rotate);
        }
    }

    private void updateSeekBar() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            int current = mediaPlayer.getCurrentPosition();
            int duration = mediaPlayer.getDuration();
            seekBar.setProgress(current);
            if (tvPlayerTime != null) {
                tvPlayerTime.setText(String.format(Locale.getDefault(), "%s / %s", formatTime(current), formatTime(duration)));
            }
            handler.postDelayed(this::updateSeekBar, 1000);
        }
    }

    private String formatTime(int ms) {
        int seconds = ms / 1000;
        int minutes = seconds / 60;
        seconds %= 60;
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (videoPlayerView != null && exoPlayer != null) exoPlayer.pause();
        // 允许音频在后台播放且不受系统音量调节弹窗干扰，不再调用 mediaPlayer.pause()
        
        // 注销光线传感器
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 注册光线传感器
        if (sensorManager != null && lightSensor != null) {
            sensorManager.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    private float smoothedLux = -1f;
    private long lastUpdateTime = 0;
    private static final long UPDATE_INTERVAL_MS = 1000; 

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_LIGHT) {
            float lux = event.values[0];
            // 低通滤波：平滑光强变化
            if (smoothedLux == -1f) smoothedLux = lux;
            else smoothedLux = (smoothedLux * 0.8f) + (lux * 0.2f);

            long currentTime = System.currentTimeMillis();
            if (currentTime - lastUpdateTime > UPDATE_INTERVAL_MS) {
                adjustScreenBrightness(smoothedLux);
                lastUpdateTime = currentTime;
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // 不需要处理
    }

    private float lastLux = -1f;
    private static final float LUX_THRESHOLD = 10.0f; // 增大阈值

    private void adjustScreenBrightness(float lux) {
        // 只有当平滑后的光强变化超过 10 Lux 才真正修改硬件亮度
        if (lastLux != -1 && Math.abs(lux - lastLux) < LUX_THRESHOLD) {
            return;
        }
        lastLux = lux;

        WindowManager.LayoutParams layoutParams = getWindow().getAttributes();
        // 映射逻辑优化：增加渐变感
        float brightness = 0.1f + (lux / 200f); 
        if (brightness > 1.0f) brightness = 1.0f;
        if (brightness < 0.1f) brightness = 0.1f;
        
        layoutParams.screenBrightness = brightness;
        getWindow().setAttributes(layoutParams);
    }

    private void showAppLockDetailDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_app_lock_detail, null);
        com.google.android.material.switchmaterial.SwitchMaterial sFace = view.findViewById(R.id.sw_face);
        com.google.android.material.switchmaterial.SwitchMaterial sFinger = view.findViewById(R.id.sw_finger);
        com.google.android.material.switchmaterial.SwitchMaterial sPass = view.findViewById(R.id.sw_pass);

        SharedPreferences sp = getSharedPreferences("user_prefs", MODE_PRIVATE);
        sFace.setChecked(sp.getBoolean(SP_LOCK_FACE, false));
        sFinger.setChecked(sp.getBoolean(SP_LOCK_FINGER, false));
        sPass.setChecked(sp.getBoolean(SP_LOCK_PASS, false));

        // 硬件检测
        android.content.pm.PackageManager pm = getPackageManager();
        boolean hasFace = pm.hasSystemFeature(android.content.pm.PackageManager.FEATURE_FACE);
        boolean hasFinger = pm.hasSystemFeature(android.content.pm.PackageManager.FEATURE_FINGERPRINT);

        if (!hasFace) { sFace.setEnabled(false); sFace.setAlpha(0.4f); }
        if (!hasFinger) { sFinger.setEnabled(false); sFinger.setAlpha(0.4f); }

        sFace.setOnCheckedChangeListener((b, checked) -> handleLockToggle(SP_LOCK_FACE, checked, sFace));
        sFinger.setOnCheckedChangeListener((b, checked) -> handleLockToggle(SP_LOCK_FINGER, checked, sFinger));
        sPass.setOnCheckedChangeListener((b, checked) -> handleLockToggle(SP_LOCK_PASS, checked, sPass));

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("应用锁安全设置")
                .setView(view)
                .setPositiveButton("完成", null)
                .show();
    }

    private void handleLockToggle(String key, boolean checked, com.google.android.material.switchmaterial.SwitchMaterial sw) {
        if (checked) {
            verifyIdentityBeforeEnable(key, sw);
        } else {
            getSharedPreferences("user_prefs", MODE_PRIVATE).edit().putBoolean(key, false).apply();
        }
    }

    private void verifyIdentityBeforeEnable(String key, com.google.android.material.switchmaterial.SwitchMaterial sw) {
        String typeName = "安全锁";
        if (SP_LOCK_FACE.equals(key)) typeName = "面容识别";
        else if (SP_LOCK_FINGER.equals(key)) typeName = "指纹识别";
        else if (SP_LOCK_PASS.equals(key)) typeName = "设备密码";

        final String finalTypeName = typeName;
        Executor executor = ContextCompat.getMainExecutor(this);
        BiometricPrompt biometricPrompt = new BiometricPrompt(this, executor, new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                getSharedPreferences("user_prefs", MODE_PRIVATE).edit().putBoolean(key, true).apply();
                Toast.makeText(FileListActivity.this, finalTypeName + "开启成功", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);
                runOnUiThread(() -> sw.setChecked(false));
                Toast.makeText(FileListActivity.this, "开启失败: " + errString, Toast.LENGTH_SHORT).show();
            }
        });

        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("开启" + typeName)
                .setSubtitle("请通过" + typeName + "验证您的身份")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG | BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                .build();

        biometricPrompt.authenticate(promptInfo);
    }

    private void showBiometricPrompt() {
        showBiometricPromptInternal(false);
    }

    private void showBiometricPromptInternal(boolean isForSetting) {
        Executor executor = ContextCompat.getMainExecutor(this);
        BiometricPrompt biometricPrompt = new BiometricPrompt(this, executor, new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);
                Toast.makeText(FileListActivity.this, "认证错误: " + errString, Toast.LENGTH_SHORT).show();
                if (!isForSetting) finish();
                else {
                    // 这里的 isForSetting 分支在新的逻辑中已不再直接由 initAppLockSwitch 调用
                }
            }

            @Override
            public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                isBiometricAuthenticated = true;
                Toast.makeText(FileListActivity.this, "认证成功", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onAuthenticationFailed() {
                super.onAuthenticationFailed();
                Toast.makeText(FileListActivity.this, "认证失败，请重试", Toast.LENGTH_SHORT).show();
            }
        });

        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("应用锁")
                .setSubtitle("使用生物识别（指纹、面容）或设备密码进入")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG | BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                .build();

        biometricPrompt.authenticate(promptInfo);
    }

    private void initAppLockSwitch() {
        // 该方法已被弃用，逻辑已移至 showAppLockDetailDialog
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        if (exoPlayer != null) {
            exoPlayer.release();
            exoPlayer = null;
        }
        if (mediaPlayer != null) mediaPlayer.release();
    }
}