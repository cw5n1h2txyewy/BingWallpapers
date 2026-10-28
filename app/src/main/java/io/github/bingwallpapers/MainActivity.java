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
    private int GREEN, INK;
    private final ExecutorService network = Executors.newFixedThreadPool(3);
    private ThumbnailCache previews;
    private final SnapshotCache<List<ImageStore.Saved>> librarySnapshots = new SnapshotCache<>(5 * 60 * 1000);
    private View body, recentPage, libraryPage;
    private String libraryPageKey, loadingLibraryKey;
    private long libraryRequest, imagesFetchedAt, lastMetadataAttempt;
    private final Set<String> loadingMarkets = new HashSet<>();
    private LinearLayout root, content;
    private TextView status;
    private List<Wallpaper> images = new ArrayList<>();
    private Wallpaper selected;
    private String market = "zh-CN";
    private boolean gallery;
    private final BroadcastReceiver updates = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            status.setText(AppSettings.lastStatus(MainActivity.this));
            if (intent.getBooleanExtra("complete", false)
                    && librarySnapshots.get(librarySourceKey(), System.currentTimeMillis()) == null) {
                librarySnapshots.clear(); libraryPage = null;
                if (gallery && selected == null) render();
            }
        }
    };

    @Override protected void attachBaseContext(Context base) { super.attachBaseContext(AppSettings.wrap(base)); }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        GREEN = getColor(R.color.accent); INK = getColor(R.color.primary_text);
        previews = new ThumbnailCache(new File(getCacheDir(), "previews-v1"), 64L * 1024 * 1024);
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
        if (images.isEmpty()) {
            try { images = BingClient.parse(getPreferences(MODE_PRIVATE).getString("cache-" + market, "{}")); }
            catch (JSONException ignored) { /* No valid metadata cache yet. */ }
        }
        imagesFetchedAt = getPreferences(MODE_PRIVATE).getLong("cache-time-" + market, 0);
        render();
        refreshRecent(false);
    }

    @Override protected void onStart() {
        super.onStart();
        // Signature permission protects older releases; API 33+ also marks it unexported.
        registerReceiver(updates, new IntentFilter(DownloadService.ACTION), "io.github.bingwallpapers.INTERNAL", null,
                Build.VERSION.SDK_INT >= 33 ? Context.RECEIVER_NOT_EXPORTED : 0);
        status.setText(AppSettings.lastStatus(this));
    }
    @Override protected void onStop() { unregisterReceiver(updates); super.onStop(); }
    @Override protected void onResume() {
        super.onResume();
        // Recheck cheap grant/revision keys, without rescanning a fresh directory snapshot.
        if (gallery && selected == null && librarySnapshots.get(librarySourceKey(), System.currentTimeMillis()) == null) render();
        refreshRecent(false);
    }
    @Override protected void onDestroy() { network.shutdownNow(); super.onDestroy(); }
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
        button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.button_background)));
        button.setElevation(0);
        button.setOnClickListener(view -> action.run()); return button;
    }
    private void render() {
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(getColor(R.color.background));
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            android.graphics.Insets bars = Build.VERSION.SDK_INT >= 30
                    ? insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout())
                    : insets.getSystemWindowInsets();
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom); return insets;
        });
        setContentView(root);
        root.requestApplyInsets();
        LinearLayout header = new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL);
        TextView brand = text(getString(selected != null ? R.string.detail_title : R.string.app_name), 26, INK);
        brand.setTypeface(null, Typeface.BOLD);
        header.addView(brand, new LinearLayout.LayoutParams(0, dp(64), 1));
        if (selected != null) brand.setOnClickListener(view -> { selected = null; render(); });
        header.addView(button("⋮", this::menu), new LinearLayout.LayoutParams(dp(52), dp(48))); root.addView(header);
        if (selected == null) {
            LinearLayout tabs = new LinearLayout(this);
            tabs.addView(button(getString(R.string.recent), () -> showTab(false)), new LinearLayout.LayoutParams(0, dp(52), 1));
            tabs.addView(button(getString(R.string.downloaded), () -> showTab(true)), new LinearLayout.LayoutParams(0, dp(52), 1));
            root.addView(tabs);
            root.addView(text(getString(gallery ? R.string.gallery_tagline : R.string.recent_tagline), 14, GREEN));
        }
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true);
        content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(dp(12), 0, dp(12), dp(12));
        scroll.addView(content); root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        body = scroll;
        status = text(AppSettings.lastStatus(this), 12, GREEN);
        status.setMaxLines(4); root.addView(status);
        if (selected != null) detail(); else if (gallery) library(); else recent();
    }

    /** Reattach a retained page, preserving its scroll position and loaded image views. */
    private void replaceBody(View page) {
        if (body == page) return;
        if (page.getParent() instanceof ViewGroup parent) parent.removeView(page);
        int index = root.indexOfChild(body);
        root.removeView(body);
        root.addView(page, index, new LinearLayout.LayoutParams(-1, 0, 1));
        body = page;
    }

    /** Switch tabs without rebuilding the active page or refetching fresh data. */
    private void showTab(boolean showLibrary) {
        if (gallery != showLibrary) { gallery = showLibrary; selected = null; render(); }
        else if (gallery && librarySnapshots.get(librarySourceKey(), System.currentTimeMillis()) == null) render();
        if (!gallery) refreshRecent(false);
    }

    private ImageView photo(String key, String url, Uri uri, int height, boolean crop) {
        ImageView photo = new ImageView(this);
        photo.setContentDescription(getString(R.string.photo_description));
        photo.setScaleType(crop ? ImageView.ScaleType.CENTER_CROP : ImageView.ScaleType.FIT_CENTER);
        photo.setBackgroundColor(getColor(R.color.photo_placeholder));
        photo.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(height)));
        GradientDrawable shape = new GradientDrawable(); shape.setColor(getColor(R.color.photo_placeholder)); shape.setCornerRadius(dp(16));
        photo.setBackground(shape); photo.setClipToOutline(true);
        String previewKey = uri == null ? key + "|" + crop : key + "|" + getSharedPreferences("bing", MODE_PRIVATE).getLong("preview-revision", 0);
        Bitmap ready = previews.peek(previewKey);
        if (ready != null) { photo.setImageBitmap(ready); return photo; }
        network.execute(() -> {
            try {
                Bitmap bitmap = previews.get(previewKey, () -> {
                    if (uri != null) return new ImageStore(this).thumbnail(uri);
                    ByteArrayOutputStream data = new ByteArrayOutputStream(); BingClient.fetch(this, url, data, 8 * 1024 * 1024);
                    byte[] raw = data.toByteArray();
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inSampleSize = crop ? 2 : 1;
                    return BitmapFactory.decodeByteArray(raw, 0, raw.length, options);
                });
                // Retained pages may be off screen while their previews finish loading.
                runOnUiThread(() -> { if (!isDestroyed()) photo.setImageBitmap(bitmap); });
            } catch (IOException | RuntimeException error) {
                runOnUiThread(() -> { if (!isDestroyed()) photo.setContentDescription(getString(R.string.preview_failed)); });
            }
        });
        return photo;
    }

    private void recent() {
        if (recentPage != null) { replaceBody(recentPage); return; }
        List<Wallpaper> displayed = new ArrayList<>(images);
        ListView page = new ListView(this);
        if (displayed.isEmpty()) page.addHeaderView(text(getString(R.string.loading_recent), 16, INK));
        page.addFooterView(button(getString(R.string.refresh_recent), this::load), null, true);
        page.setAdapter(new BaseAdapter() {
            @Override public int getCount() { return displayed.size(); }
            @Override public Wallpaper getItem(int index) { return displayed.get(index); }
            @Override public long getItemId(int index) { return index; }
            @Override public View getView(int index, View recycled, ViewGroup parent) {
                Wallpaper image = getItem(index);
                LinearLayout card = new LinearLayout(MainActivity.this); card.setOrientation(LinearLayout.VERTICAL);
                card.setPadding(dp(12), 0, dp(12), dp(12));
                card.addView(photo(image.base, image.url("1920x1080"), null, 210, true));
                card.addView(text(image.date + "   ·   " + image.title, 17, INK));
                card.addView(text(image.copyright, 12, getColor(R.color.secondary_text)));
                return card;
            }
        });
        page.setOnItemClickListener((parent, view, index, id) -> {
            int item = index - page.getHeaderViewsCount();
            if (item >= 0 && item < displayed.size()) { selected = displayed.get(item); render(); }
        });
        recentPage = page; replaceBody(page);
    }

    private void detail() {
        Wallpaper image = selected;
        content.addView(photo(image.base + "detail", image.url("1920x1080"), null, 300, false));
        content.addView(text(image.title, 24, INK)); content.addView(text(image.date, 14, GREEN));
        content.addView(text(image.copyright, 15, getColor(R.color.secondary_text)));
        content.addView(text(getString(R.string.original_note), 13, getColor(R.color.secondary_text)));
        content.addView(button(getString(R.string.download), () -> choose(Collections.singletonList(image))));
        content.addView(button(getString(R.string.share_link), () -> {
            Intent share = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, image.copyright + "\n" + image.url("UHD"));
            startActivity(Intent.createChooser(share, getString(R.string.share)));
        }));
        content.addView(button(getString(R.string.back_recent), () -> { selected = null; gallery = false; render(); }));
    }

    private void library() {
        String key = librarySourceKey();
        List<ImageStore.Saved> cached = librarySnapshots.get(key, System.currentTimeMillis());
        if (cached != null && libraryPage != null && key.equals(libraryPageKey)) { replaceBody(libraryPage); return; }
        libraryAccessNotice();
        if (cached != null) { bindLibrary(cached, key); return; }
        content.addView(text(getString(R.string.loading_library), 15, INK));
        if (key.equals(loadingLibraryKey)) return;
        loadingLibraryKey = key;
        long request = ++libraryRequest;
        network.execute(() -> {
            try {
                List<ImageStore.Saved> saved = new ImageStore(this).list();
                runOnUiThread(() -> {
                    if (isDestroyed() || request != libraryRequest) return;
                    loadingLibraryKey = null;
                    if (!key.equals(librarySourceKey())) return;
                    librarySnapshots.put(key, new ArrayList<>(saved), System.currentTimeMillis());
                    libraryPage = null;
                    if (gallery && selected == null) render();
                });
            } catch (RuntimeException error) { runOnUiThread(() -> {
                if (!isDestroyed() && request == libraryRequest) {
                    loadingLibraryKey = null;
                    if (gallery && selected == null) {
                        content.removeAllViews(); libraryAccessNotice();
                        content.addView(text(getString(R.string.directory_failed, error.getMessage()), 15, INK));
                    }
                }
            }); }
        });
    }

    /** Identify the directory grant and local changes without enumerating any files. */
    private String librarySourceKey() {
        Uri uri = new ImageStore(this).directoryUri();
        return String.valueOf(uri) + "|" + getSharedPreferences("bing", MODE_PRIVATE).getLong("library-revision", 0)
                + "|" + getSharedPreferences("bing", MODE_PRIVATE).getLong("preview-revision", 0);
    }

    /** Bind a successful directory snapshot to its retained page. */
    private void bindLibrary(List<ImageStore.Saved> saved, String key) {
        content.addView(text(getString(R.string.photo_count, saved.size()), 14, GREEN));
        if (saved.isEmpty()) content.addView(text(getString(R.string.empty_library), 16, INK));
        showLibrary(saved); libraryPageKey = key;
    }

    /** Force a new directory snapshot and refreshed previews for externally changed files. */
    private void refreshLibrary() {
        getSharedPreferences("bing", MODE_PRIVATE).edit().putLong("preview-revision", System.currentTimeMillis()).apply();
        librarySnapshots.clear(); libraryPage = null; loadingLibraryKey = null;
        render();
    }

    /** Request a directory grant independent of media indexing and photo permissions. */
    private void libraryAccessNotice() {
        boolean selectedDirectory = new ImageStore(this).directoryUri() != null;
        content.addView(text(selectedDirectory ? getString(R.string.directory_ready)
                : getString(R.string.directory_prompt), 14, INK));
        content.addView(button(selectedDirectory ? getString(R.string.reselect_directory) : getString(R.string.select_directory), () -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                    .putExtra(android.provider.DocumentsContract.EXTRA_INITIAL_URI, Uri.parse("content://com.android.externalstorage.documents/document/primary%3ADCIM%2FBingWallpapers"));
            try { startActivityForResult(intent, 3); }
            catch (ActivityNotFoundException error) { status.setText(getString(R.string.no_picker)); }
        }));
        content.addView(button(getString(R.string.refresh_library), this::refreshLibrary));
    }

    /** Bind only visible rows, keeping large legacy collections out of bitmap memory. */
    private void showLibrary(List<ImageStore.Saved> saved) {
        ((ViewGroup) content.getParent()).removeView(content);
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
            catch (ActivityNotFoundException | SecurityException error) { status.setText(R.string.open_failed); }
        });
        libraryPage = list; replaceBody(list);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 3 && resultCode == RESULT_OK && data != null && data.getData() != null) {
            try {
                new ImageStore(this).selectDirectory(data.getData());
                librarySnapshots.clear(); libraryPage = null; gallery = true; selected = null; render();
            } catch (IllegalArgumentException | SecurityException error) { status.setText(getString(R.string.directory_grant_failed, error.getMessage())); }
        }
    }

    private void menu() {
        new AlertDialog.Builder(this).setTitle(R.string.app_name).setItems(new String[]{getString(R.string.refresh), getString(R.string.download_all), getString(R.string.change_region), getString(R.string.notifications), getString(R.string.theme), getString(R.string.language), getString(R.string.about)}, (dialog, which) -> {
            switch (which) {
                case 0 -> { if (gallery && selected == null) refreshLibrary(); else load(); }
                case 1 -> { if (images.isEmpty()) status.setText(getString(R.string.load_first)); else choose(new ArrayList<>(images)); }
                case 2 -> new AlertDialog.Builder(this).setTitle(getString(R.string.region_title)).setItems(new String[]{getString(R.string.china), getString(R.string.usa), getString(R.string.japan)}, (d, index) -> {
                    market = new String[]{"zh-CN", "en-US", "ja-JP"}[index];
                    getPreferences(MODE_PRIVATE).edit().putString("market", market).apply();
                    images = new ArrayList<>(); recentPage = null; imagesFetchedAt = 0; lastMetadataAttempt = 0;
                    try { images = BingClient.parse(getPreferences(MODE_PRIVATE).getString("cache-" + market, "{}")); }
                    catch (JSONException ignored) { /* New market has no metadata cache. */ }
                    imagesFetchedAt = getPreferences(MODE_PRIVATE).getLong("cache-time-" + market, 0);
                    selected = null; gallery = false; render(); refreshRecent(false);
                }).show();
                case 3 -> {
                    if (Build.VERSION.SDK_INT >= 33) requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 1);
                    else status.setText(getString(R.string.no_notification_permission_needed));
                }
                case 4 -> showAppearance(true);
                case 5 -> showAppearance(false);
                default -> new AlertDialog.Builder(this).setTitle(getString(R.string.about_title, getString(R.string.app_name), "1.0.5"))
                        .setMessage(R.string.about_body)
                        .setPositiveButton(getString(R.string.ok), null).show();
            }
        }).show();
    }


    /** Apply a persisted appearance option and recreate only the current Activity. */
    private void showAppearance(boolean theme) {
        String key = theme ? "theme" : "language";
        String[] codes = theme ? new String[]{"system", "light", "dark"} : new String[]{"zh", "en", "ja", "system"};
        String[] labels = theme ? new String[]{getString(R.string.follow_system), getString(R.string.light), getString(R.string.dark)}
                : new String[]{"简体中文", "English", "日本語", getString(R.string.follow_system)};
        String current = getSharedPreferences("appearance", MODE_PRIVATE).getString(key, theme ? "system" : "zh");
        int selectedIndex = java.util.Arrays.asList(codes).indexOf(current);
        new AlertDialog.Builder(this).setTitle(theme ? R.string.theme : R.string.language)
                .setSingleChoiceItems(labels, selectedIndex, (dialog, index) -> {
                    dialog.dismiss();
                    if (!codes[index].equals(current)) {
                        getSharedPreferences("appearance", MODE_PRIVATE).edit().putString(key, codes[index]).apply();
                        recreate();
                    }
                }).setNeutralButton(R.string.cancel, null).show();
    }

    private static String encoded(List<Wallpaper> list) throws JSONException {
        JSONArray array = new JSONArray(); for (Wallpaper image : list) array.put(image.toJson());
        return new JSONObject().put("images", array).toString();
    }
    private void choose(List<Wallpaper> list) {
        Resolution[] sizes = Resolution.values(); String[] labels = new String[sizes.length];
        for (int i = 0; i < sizes.length; i++) labels[i] = sizes[i].label(this);
        AlertDialog picker = new AlertDialog.Builder(this).setTitle(list.size() > 1 ? getString(R.string.choose_batch_size, list.size()) : getString(R.string.choose_size))
                .setItems(labels, (dialog, index) -> {
                    dialog.dismiss();
                    try {
                        String array = new JSONObject(encoded(list)).getJSONArray("images").toString();
                        startForegroundService(new Intent(this, DownloadService.class).putExtra("images", array).putExtra("resolution", sizes[index].name()));
                        status.setText(getString(R.string.queued, sizes[index].label(this)));
                    } catch (JSONException | RuntimeException error) { status.setText(getString(R.string.start_failed, error.getMessage())); }
                }).setNeutralButton(getString(R.string.cancel), null).create();
        picker.show();
        // Some platform themes center a sole button; keep this cancel action at the left.
        Button cancel = picker.getButton(AlertDialog.BUTTON_NEUTRAL);
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) cancel.getLayoutParams();
        params.weight = 0;
        params.gravity = Gravity.START;
        cancel.setLayoutParams(params);
    }
    private void load() {
        selected = null; gallery = false; render(); refreshRecent(true);
    }

    /** Show daily cached metadata first and refresh once per market without hiding other tabs. */
    private void refreshRecent(boolean force) {
        long now = System.currentTimeMillis();
        if (loadingMarkets.contains(market)) return;
        if (!force && !images.isEmpty() && CachePolicy.sameDay(imagesFetchedAt, now, java.time.ZoneId.systemDefault())) return;
        if (!force && !CachePolicy.retryAllowed(lastMetadataAttempt, now)) return;
        lastMetadataAttempt = now;
        String requestedMarket = market;
        loadingMarkets.add(requestedMarket);
        if (!gallery && selected == null) status.setText(images.isEmpty() ? getString(R.string.loading) : getString(R.string.updating));
        network.execute(() -> {
            try {
                String raw = BingClient.metadata(this, requestedMarket); List<Wallpaper> loaded = BingClient.parse(raw);
                long fetchedAt = System.currentTimeMillis();
                getPreferences(MODE_PRIVATE).edit().putString("cache-" + requestedMarket, raw)
                        .putLong("cache-time-" + requestedMarket, fetchedAt).apply();
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    loadingMarkets.remove(requestedMarket);
                    if (!market.equals(requestedMarket)) return;
                    images = loaded; imagesFetchedAt = fetchedAt; recentPage = null;
                    if (!gallery && selected == null) render();
                });
            } catch (IOException | JSONException | RuntimeException error) {
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    loadingMarkets.remove(requestedMarket);
                    if (market.equals(requestedMarket) && !gallery && selected == null)
                        status.setText(getString(images.isEmpty() ? R.string.refresh_failed : R.string.refresh_failed_cached, error.getMessage()));
                });
            }
        });
    }
}
