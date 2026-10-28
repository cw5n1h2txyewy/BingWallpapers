package io.github.bingwallpapers;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** Recent Bing photographs, detail viewer and the download-directory photo library. */
public final class MainActivity extends Activity {
    private static final int GREEN = Color.rgb(39, 99, 75), INK = Color.rgb(29, 48, 39);
    private final ExecutorService network = Executors.newFixedThreadPool(3);
    private final android.util.LruCache<String, Bitmap> cache = new android.util.LruCache<>(16 * 1024) {
        @Override protected int sizeOf(String key, Bitmap value) { return value.getAllocationByteCount() / 1024; }
    };
    private LinearLayout root, content;
    private TextView status;
    private List<Wallpaper> images = new ArrayList<>();
    private Wallpaper selected;
    private String market = "zh-CN";
    private boolean gallery;
    private int generation;
    private final BroadcastReceiver updates = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            status.setText(intent.getStringExtra("message"));
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (Build.VERSION.SDK_INT >= 33) getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::navigateBack);
        market = getPreferences(MODE_PRIVATE).getString("market", "zh-CN");
        if (state != null) {
            gallery = state.getBoolean("gallery");
            try {
                images = BingClient.parse(state.getString("images", "{}"));
                String detail = state.getString("selected");
                if (detail != null) selected = new Wallpaper(new JSONObject(detail));
            } catch (JSONException ignored) { /* No restored metadata: fetch below. */ }
        }
        render();
        if (images.isEmpty()) load();
    }

    @Override protected void onStart() {
        super.onStart();
        // Signature permission protects older releases; API 33+ also marks it unexported.
        registerReceiver(updates, new IntentFilter(DownloadService.ACTION), "io.github.bingwallpapers.INTERNAL", null,
                Build.VERSION.SDK_INT >= 33 ? Context.RECEIVER_NOT_EXPORTED : 0);
        status.setText(getSharedPreferences("bing", MODE_PRIVATE).getString("status", "图片保存至 DCIM/BingWallpapers"));
    }
    @Override protected void onStop() { unregisterReceiver(updates); super.onStop(); }
    @Override protected void onResume() {
        super.onResume();
        // Photo grants may change in Settings or after returning from the selection dialog.
        if (gallery && selected == null) { cache.evictAll(); render(); }
    }
    @Override protected void onDestroy() { network.shutdownNow(); cache.evictAll(); super.onDestroy(); }
    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putBoolean("gallery", gallery);
        try {
            state.putString("images", encoded(images));
            if (selected != null) state.putString("selected", selected.toJson().toString());
        } catch (JSONException error) { state.remove("selected"); }
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private void navigateBack() {
        if (selected != null) { selected = null; render(); }
        else if (gallery) { gallery = false; render(); }
        else finish();
    }
    // API 29–32 fallback only; API 33+ uses the platform dispatcher registered in onCreate.
    @android.annotation.SuppressLint("GestureBackNavigation")
    @Override public void onBackPressed() { navigateBack(); }
    private TextView text(String value, int sp, int color) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(sp); view.setTextColor(color);
        view.setPadding(dp(16), dp(8), dp(16), dp(8)); return view;
    }
    private Button button(String label, Runnable action) {
        Button button = new Button(this); button.setText(label); button.setTextColor(GREEN); button.setAllCaps(false);
        button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(225, 236, 226)));
        button.setElevation(0);
        button.setOnClickListener(view -> action.run()); return button;
    }
    private void render() {
        generation++;
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.rgb(245, 247, 243));
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            android.graphics.Insets bars = Build.VERSION.SDK_INT >= 30
                    ? insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout())
                    : insets.getSystemWindowInsets();
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom); return insets;
        });
        setContentView(root);
        root.requestApplyInsets();
        LinearLayout header = new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL);
        TextView brand = text(selected != null ? "‹  每日风景" : "必应美图", 26, INK);
        brand.setTypeface(null, Typeface.BOLD);
        header.addView(brand, new LinearLayout.LayoutParams(0, dp(64), 1));
        if (selected != null) brand.setOnClickListener(view -> { selected = null; render(); });
        header.addView(button("⋮", this::menu), new LinearLayout.LayoutParams(dp(52), dp(48))); root.addView(header);
        if (selected == null) {
            LinearLayout tabs = new LinearLayout(this);
            tabs.addView(button("最近八天", () -> { gallery = false; render(); }), new LinearLayout.LayoutParams(0, dp(52), 1));
            tabs.addView(button("已下载", () -> { gallery = true; render(); }), new LinearLayout.LayoutParams(0, dp(52), 1));
            root.addView(tabs);
            root.addView(text(gallery ? "你的风景收藏" : "每天一张，发现世界之美", 14, GREEN));
        }
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true);
        content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(dp(12), 0, dp(12), dp(12));
        scroll.addView(content); root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        status = text(getSharedPreferences("bing", MODE_PRIVATE).getString("status", "图片保存至 DCIM/BingWallpapers"), 12, GREEN);
        status.setMaxLines(4); root.addView(status);
        if (selected != null) detail(); else if (gallery) library(); else recent();
    }

    private ImageView photo(String key, String url, Uri uri, int height, boolean crop) {
        ImageView photo = new ImageView(this);
        photo.setContentDescription("风景图片");
        photo.setScaleType(crop ? ImageView.ScaleType.CENTER_CROP : ImageView.ScaleType.FIT_CENTER);
        photo.setBackgroundColor(Color.rgb(218, 228, 218));
        photo.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(height)));
        GradientDrawable shape = new GradientDrawable(); shape.setColor(Color.rgb(218, 228, 218)); shape.setCornerRadius(dp(16));
        photo.setBackground(shape); photo.setClipToOutline(true);
        Bitmap existing = cache.get(key);
        if (existing != null) { photo.setImageBitmap(existing); return photo; }
        int token = generation;
        network.execute(() -> {
            try {
                Bitmap bitmap;
                if (uri != null) bitmap = getContentResolver().loadThumbnail(uri, new android.util.Size(960, 540), null);
                else {
                    ByteArrayOutputStream data = new ByteArrayOutputStream(); BingClient.fetch(url, data, 8 * 1024 * 1024);
                    byte[] raw = data.toByteArray();
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inSampleSize = crop ? 2 : 1;
                    bitmap = BitmapFactory.decodeByteArray(raw, 0, raw.length, options);
                }
                if (bitmap == null) throw new IOException("无法解码图片");
                cache.put(key, bitmap);
                runOnUiThread(() -> { if (!isDestroyed() && token == generation) photo.setImageBitmap(bitmap); });
            } catch (IOException | RuntimeException error) {
                runOnUiThread(() -> { if (!isDestroyed() && token == generation) photo.setContentDescription("预览加载失败，请刷新重试"); });
            }
        });
        return photo;
    }

    private void recent() {
        if (images.isEmpty()) { content.addView(text("正在获取 Bing 图片…\n若加载失败，请检查网络后点击刷新。", 16, INK)); }
        for (Wallpaper image : images) {
            LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.bottomMargin = dp(16); card.setLayoutParams(params);
            card.addView(photo(image.base, image.url("1920x1080"), null, 210, true));
            card.addView(text(image.date + "   ·   " + image.title, 17, INK));
            card.addView(text(image.copyright, 12, Color.DKGRAY));
            card.setOnClickListener(view -> { selected = image; render(); }); content.addView(card);
        }
        content.addView(button("刷新最近图片", this::load));
    }

    private void detail() {
        Wallpaper image = selected;
        content.addView(photo(image.base + "detail", image.url("1920x1080"), null, 300, false));
        content.addView(text(image.title, 24, INK)); content.addView(text(image.date, 14, GREEN));
        content.addView(text(image.copyright, 15, Color.DKGRAY));
        content.addView(text("保留 Bing 原始 JPEG；UHD 按实际尺寸命名。", 13, Color.DKGRAY));
        content.addView(button("下载图片", () -> choose(Collections.singletonList(image))));
        content.addView(button("分享图片链接", () -> {
            Intent share = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, image.copyright + "\n" + image.url("UHD"));
            startActivity(Intent.createChooser(share, "分享美图"));
        }));
        content.addView(button("返回最近图片", () -> { selected = null; gallery = false; render(); }));
    }

    private void library() {
        int token = generation;
        libraryAccessNotice();
        content.addView(text("正在读取已下载图片…", 15, INK));
        network.execute(() -> {
            try {
                List<ImageStore.Saved> saved = new ImageStore(this).list();
                runOnUiThread(() -> {
                    if (isDestroyed() || token != generation) return;
                    content.removeAllViews();
                    libraryAccessNotice();
                    if (saved.isEmpty()) content.addView(text(GalleryAccess.isFull(this)
                            ? "保存目录中暂无已被系统相册收录的图片。"
                            : "暂无可访问的图片。旧软件的图片需要照片读取授权。", 16, INK));
                    showLibrary(saved);
                });
            } catch (RuntimeException error) { runOnUiThread(() -> { if (!isDestroyed()) status.setText("读取相册失败：" + error.getMessage()); }); }
        });
    }

    /** Explain full, partial and denied media access without blocking new downloads. */
    private void libraryAccessNotice() {
        if (GalleryAccess.isFull(this)) {
            content.addView(text("DCIM/BingWallpapers · 包含旧软件保存的图片", 13, GREEN));
        } else {
            content.addView(text(GalleryAccess.isPartial(this)
                    ? "当前只允许部分照片。要显示保存目录中的全部旧图片，请允许访问所有照片。"
                    : "显示旧软件保存在 DCIM/BingWallpapers 的图片需要照片读取权限。应用只列出此目录；下载新图片不需要此权限。", 14, INK));
            content.addView(button(GalleryAccess.isPartial(this) ? "管理照片访问权限" : "授权读取旧图片", () -> requestPermissions(GalleryAccess.permissions(), 2)));
            content.addView(button("打开应用权限设置", () -> startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName())))));
        }
    }

    /** Bind only visible rows, keeping large legacy collections out of bitmap memory. */
    private void showLibrary(List<ImageStore.Saved> saved) {
        ScrollView oldScroll = (ScrollView) content.getParent();
        int position = root.indexOfChild(oldScroll);
        oldScroll.removeView(content);
        root.removeView(oldScroll);
        ListView list = new ListView(this);
        list.setDividerHeight(dp(12));
        list.addHeaderView(content, null, false);
        list.setAdapter(new BaseAdapter() {
            @Override public int getCount() { return saved.size(); }
            @Override public ImageStore.Saved getItem(int index) { return saved.get(index); }
            @Override public long getItemId(int index) { return index; }
            @Override public View getView(int index, View recycled, ViewGroup parent) {
                ImageStore.Saved item = getItem(index);
                LinearLayout card = new LinearLayout(MainActivity.this);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setPadding(dp(12), 0, dp(12), 0);
                card.addView(photo(item.uri().toString(), null, item.uri(), 190, true));
                card.addView(text(item.name(), 14, INK));
                return card;
            }
        });
        list.setOnItemClickListener((parent, view, index, id) -> {
            if (index < list.getHeaderViewsCount()) return;
            ImageStore.Saved item = saved.get(index - list.getHeaderViewsCount());
            try { startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(item.uri(), "image/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)); }
            catch (ActivityNotFoundException | SecurityException error) { status.setText("无法打开图片，请检查照片访问权限"); }
        });
        root.addView(list, position, new LinearLayout.LayoutParams(-1, 0, 1));
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 2 && gallery && selected == null) { cache.evictAll(); render(); }
    }

    private void menu() {
        new AlertDialog.Builder(this).setTitle("必应美图").setItems(new String[]{"刷新", "下载全部最近图片", "切换地区", "允许下载通知", "关于"}, (dialog, which) -> {
            switch (which) {
                case 0 -> { selected = null; load(); }
                case 1 -> { if (images.isEmpty()) status.setText("请先加载图片"); else choose(new ArrayList<>(images)); }
                case 2 -> new AlertDialog.Builder(this).setTitle("Bing 地区").setItems(new String[]{"中国", "美国", "日本"}, (d, index) -> {
                    market = new String[]{"zh-CN", "en-US", "ja-JP"}[index];
                    getPreferences(MODE_PRIVATE).edit().putString("market", market).apply(); images.clear(); selected = null; gallery = false; load();
                }).show();
                case 3 -> {
                    if (Build.VERSION.SDK_INT >= 33) requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 1);
                    else status.setText("当前系统无需额外通知权限");
                }
                default -> new AlertDialog.Builder(this).setTitle("必应美图 1.0.1")
                        .setMessage("独立开发的 Bing 图片浏览与下载工具，非微软官方应用。\n\n保存位置：DCIM/BingWallpapers\n下载无需存储权限；读取旧软件的图片需要照片权限。\n\n图片版权归原作者及权利人所有，请遵守 Bing 使用条款。近期列表由 Bing 接口提供，通常为八天。\n\n在已下载页授权访问所有照片后，可显示保存目录中的旧图片。")
                        .setPositiveButton("知道了", null).show();
            }
        }).show();
    }

    private static String encoded(List<Wallpaper> list) throws JSONException {
        JSONArray array = new JSONArray(); for (Wallpaper image : list) array.put(image.toJson());
        return new JSONObject().put("images", array).toString();
    }
    private void choose(List<Wallpaper> list) {
        Resolution[] sizes = Resolution.values(); String[] labels = new String[sizes.length];
        for (int i = 0; i < sizes.length; i++) labels[i] = sizes[i].label;
        int last = getPreferences(MODE_PRIVATE).getInt("size", 0);
        new AlertDialog.Builder(this).setTitle(list.size() > 1 ? "下载全部 " + list.size() + " 张 · 选择尺寸" : "选择下载尺寸")
                .setSingleChoiceItems(labels, last, (dialog, index) -> {
                    dialog.dismiss(); getPreferences(MODE_PRIVATE).edit().putInt("size", index).apply();
                    try {
                        String array = new JSONObject(encoded(list)).getJSONArray("images").toString();
                        startForegroundService(new Intent(this, DownloadService.class).putExtra("images", array).putExtra("resolution", sizes[index].name()));
                        status.setText("已加入下载队列 · " + sizes[index].label);
                    } catch (JSONException | RuntimeException error) { status.setText("启动下载失败：" + error.getMessage()); }
                }).setNegativeButton("取消", null).show();
    }
    private void load() {
        selected = null; gallery = false; render(); status.setText("正在刷新…");
        String requestedMarket = market;
        int token = generation;
        network.execute(() -> {
            try {
                String raw = BingClient.metadata(requestedMarket); List<Wallpaper> loaded = BingClient.parse(raw);
                getPreferences(MODE_PRIVATE).edit().putString("cache-" + requestedMarket, raw).apply();
                runOnUiThread(() -> { if (!isDestroyed() && token == generation) { images = loaded; render(); } });
            } catch (IOException | JSONException | RuntimeException error) {
                List<Wallpaper> cached = new ArrayList<>();
                try { cached = BingClient.parse(getPreferences(MODE_PRIVATE).getString("cache-" + requestedMarket, "{}")); }
                catch (JSONException ignored) { /* First launch has no offline metadata. */ }
                List<Wallpaper> fallback = cached;
                runOnUiThread(() -> { if (!isDestroyed() && token == generation) {
                    if (!fallback.isEmpty()) { images = fallback; render(); }
                    status.setText("刷新失败" + (fallback.isEmpty() ? "" : "，显示缓存") + "：" + error.getMessage());
                } });
            }
        });
    }
}
