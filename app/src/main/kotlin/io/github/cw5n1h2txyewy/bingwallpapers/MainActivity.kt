package io.github.cw5n1h2txyewy.bingwallpapers

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import android.window.OnBackInvokedDispatcher
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.time.ZoneId
import java.util.concurrent.Executors
import kotlin.math.roundToInt
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Recent Bing photographs, detail viewer and the download-directory photo library. */
class MainActivity : Activity() {
    private var accent = 0
    private var ink = 0
    private val network = Executors.newFixedThreadPool(3)
    private lateinit var previews: ThumbnailCache
    private val librarySnapshots = SnapshotCache<List<ImageStore.Saved>>(5 * 60 * 1000)
    private var body: View? = null
    private var recentPage: View? = null
    private var libraryPage: View? = null
    private var libraryPageKey: String? = null
    private var loadingLibraryKey: String? = null
    private var libraryRequest = 0L
    private var imagesFetchedAt = 0L
    private var lastMetadataAttempt = 0L
    private val loadingMarkets = mutableSetOf<String>()
    private lateinit var root: LinearLayout
    private lateinit var content: LinearLayout
    private lateinit var status: TextView
    private var images: List<Wallpaper> = emptyList()
    private var selected: Wallpaper? = null
    private var market = "zh-CN"
    private var gallery = false
    private val updates = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            status.text = AppSettings.lastStatus(this@MainActivity)
            if (intent.getBooleanExtra("complete", false) &&
                librarySnapshots.get(librarySourceKey(), System.currentTimeMillis()) == null) {
                librarySnapshots.clear()
                libraryPage = null
                if (gallery && selected == null) render()
            }
        }
    }

    override fun attachBaseContext(base: Context) = super.attachBaseContext(AppSettings.wrap(base))

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        accent = getColor(R.color.accent)
        ink = getColor(R.color.primary_text)
        previews = ThumbnailCache(File(cacheDir, "previews-v1"), 64L * 1024 * 1024)
        if (Build.VERSION.SDK_INT >= 33) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, ::navigateBack)
        }
        market = getPreferences(MODE_PRIVATE).getString("market", "zh-CN") ?: "zh-CN"
        if (state != null) {
            gallery = state.getBoolean("gallery")
            try {
                images = BingClient.parse(state.getString("images", "{}"))
                state.getString("selected")?.let { selected = Wallpaper(JSONObject(it)) }
            } catch (_: JSONException) {
                // No restored metadata: fetch below.
            }
        }
        if (images.isEmpty()) images = cachedMetadata()
        imagesFetchedAt = getPreferences(MODE_PRIVATE).getLong("cache-time-$market", 0)
        render()
        refreshRecent(false)
    }

    override fun onStart() {
        super.onStart()
        // Signature permission protects older releases; API 33+ also marks it unexported.
        registerReceiver(updates, IntentFilter(DownloadService.ACTION), "$packageName.INTERNAL", null,
            if (Build.VERSION.SDK_INT >= 33) Context.RECEIVER_NOT_EXPORTED else 0)
        status.text = AppSettings.lastStatus(this)
    }

    override fun onStop() {
        unregisterReceiver(updates)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        // Recheck cheap grant/revision keys, without rescanning a fresh directory snapshot.
        if (gallery && selected == null && librarySnapshots.get(librarySourceKey(), System.currentTimeMillis()) == null) render()
        refreshRecent(false)
    }

    override fun onDestroy() {
        network.shutdownNow()
        super.onDestroy()
    }

    override fun onSaveInstanceState(state: Bundle) {
        super.onSaveInstanceState(state)
        state.putBoolean("gallery", gallery)
        try {
            state.putString("images", encoded(images))
            selected?.let { state.putString("selected", it.toJson().toString()) }
        } catch (_: JSONException) {
            state.remove("selected")
        }
    }

    /** Restore one market's metadata without trusting missing or corrupt cache data. */
    private fun cachedMetadata(): List<Wallpaper> = try {
        BingClient.parse(getPreferences(MODE_PRIVATE).getString("cache-$market", "{}") ?: "{}")
    } catch (_: JSONException) {
        emptyList()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    /** Return from detail or library before closing the Activity. */
    private fun navigateBack() {
        when {
            selected != null -> { selected = null; render() }
            gallery -> { gallery = false; render() }
            else -> finish()
        }
    }

    // API 29–32 fallback only; API 33+ uses the platform dispatcher registered in onCreate.
    @SuppressLint("GestureBackNavigation")
    @Deprecated("Legacy Android back callback")
    override fun onBackPressed() = navigateBack()

    private fun text(value: String, sp: Int, color: Int): TextView = TextView(this).apply {
        text = value
        textSize = sp.toFloat()
        setTextColor(color)
        setPadding(dp(16), dp(8), dp(16), dp(8))
    }

    private fun button(label: String, action: () -> Unit): Button = Button(this).apply {
        text = label
        setTextColor(accent)
        isAllCaps = false
        backgroundTintList = ColorStateList.valueOf(getColor(R.color.button_background))
        elevation = 0f
        setOnClickListener { action() }
    }

    /** Rebuild the shell while retaining cached tab pages and decoded previews. */
    private fun render() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(getColor(R.color.background))
            setOnApplyWindowInsetsListener { view, insets ->
                val bars = if (Build.VERSION.SDK_INT >= 30) {
                    insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                } else {
                    @Suppress("DEPRECATION")
                    insets.systemWindowInsets
                }
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                insets
            }
        }
        setContentView(root)
        root.requestApplyInsets()
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val brand = text(getString(if (selected != null) R.string.detail_title else R.string.app_name), 26, ink)
        brand.setTypeface(null, Typeface.BOLD)
        header.addView(brand, LinearLayout.LayoutParams(0, dp(64), 1f))
        if (selected != null) brand.setOnClickListener { selected = null; render() }
        header.addView(button("⋮", ::menu), LinearLayout.LayoutParams(dp(52), dp(48)))
        root.addView(header)
        if (selected == null) {
            val tabs = LinearLayout(this)
            tabs.addView(button(getString(R.string.recent)) { showTab(false) }, LinearLayout.LayoutParams(0, dp(52), 1f))
            tabs.addView(button(getString(R.string.downloaded)) { showTab(true) }, LinearLayout.LayoutParams(0, dp(52), 1f))
            root.addView(tabs)
            root.addView(text(getString(if (gallery) R.string.gallery_tagline else R.string.recent_tagline), 14, accent))
        }
        val scroll = ScrollView(this).apply { isFillViewport = true }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, dp(12), dp(12))
        }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        body = scroll
        status = text(AppSettings.lastStatus(this), 12, accent).apply { maxLines = 4 }
        root.addView(status)
        when {
            selected != null -> detail()
            gallery -> library()
            else -> recent()
        }
    }

    /** Reattach a retained page, preserving its scroll position and loaded image views. */
    private fun replaceBody(page: View) {
        if (body === page) return
        (page.parent as? ViewGroup)?.removeView(page)
        val index = root.indexOfChild(body)
        root.removeView(body)
        root.addView(page, index, LinearLayout.LayoutParams(-1, 0, 1f))
        body = page
    }

    /** Switch tabs without rebuilding the active page or refetching fresh data. */
    private fun showTab(showLibrary: Boolean) {
        if (gallery != showLibrary) {
            gallery = showLibrary
            selected = null
            render()
        } else if (gallery && librarySnapshots.get(librarySourceKey(), System.currentTimeMillis()) == null) render()
        if (!gallery) refreshRecent(false)
    }

    /** Show a cached preview or load it on a worker without modifying the original. */
    private fun photo(key: String, url: String?, uri: Uri?, height: Int, crop: Boolean): ImageView {
        val photo = ImageView(this).apply {
            contentDescription = getString(R.string.photo_description)
            scaleType = if (crop) ImageView.ScaleType.CENTER_CROP else ImageView.ScaleType.FIT_CENTER
            layoutParams = LinearLayout.LayoutParams(-1, dp(height))
            background = GradientDrawable().apply {
                setColor(getColor(R.color.photo_placeholder))
                cornerRadius = dp(16).toFloat()
            }
            clipToOutline = true
        }
        val previewKey = if (uri == null) "$key|$crop"
            else "$key|${getSharedPreferences("bing", MODE_PRIVATE).getLong("preview-revision", 0)}"
        previews.peek(previewKey)?.let { photo.setImageBitmap(it); return photo }
        network.execute {
            try {
                val bitmap = previews.get(previewKey) {
                    if (uri != null) {
                        ImageStore(this).thumbnail(uri)
                    } else {
                        val data = ByteArrayOutputStream()
                        BingClient.fetch(this, requireNotNull(url), data, 8 * 1024 * 1024)
                        val raw = data.toByteArray()
                        val options = BitmapFactory.Options().apply { inSampleSize = if (crop) 2 else 1 }
                        BitmapFactory.decodeByteArray(raw, 0, raw.size, options)
                    }
                }
                // Retained pages may be off screen while their previews finish loading.
                runOnUiThread { if (!isDestroyed) photo.setImageBitmap(bitmap) }
            } catch (error: Exception) {
                if (error !is IOException && error !is RuntimeException) throw error
                runOnUiThread { if (!isDestroyed) photo.contentDescription = getString(R.string.preview_failed) }
            }
        }
        return photo
    }

    /** Bind the recent metadata snapshot and retain the list between tab switches. */
    private fun recent() {
        recentPage?.let { replaceBody(it); return }
        val displayed = images.toList()
        val page = ListView(this)
        if (displayed.isEmpty()) page.addHeaderView(text(getString(R.string.loading_recent), 16, ink))
        page.addFooterView(button(getString(R.string.refresh_recent), ::load), null, true)
        page.adapter = object : BaseAdapter() {
            override fun getCount(): Int = displayed.size
            override fun getItem(index: Int): Wallpaper = displayed[index]
            override fun getItemId(index: Int): Long = index.toLong()
            override fun getView(index: Int, recycled: View?, parent: ViewGroup): View {
                val image = getItem(index)
                return LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(12), 0, dp(12), dp(12))
                    addView(photo(image.base, image.url("1920x1080"), null, 210, true))
                    addView(text("${image.date}   ·   ${image.title}", 17, ink))
                    addView(text(image.copyright, 12, getColor(R.color.secondary_text)))
                }
            }
        }
        page.setOnItemClickListener { _, _, index, _ ->
            val item = index - page.headerViewsCount
            if (item in displayed.indices) { selected = displayed[item]; render() }
        }
        recentPage = page
        replaceBody(page)
    }

    /** Render the selected photo with direct download and sharing actions. */
    private fun detail() {
        val image = selected ?: return
        content.addView(photo(image.base + "detail", image.url("1920x1080"), null, 300, false))
        content.addView(text(image.title, 24, ink))
        content.addView(text(image.date, 14, accent))
        content.addView(text(image.copyright, 15, getColor(R.color.secondary_text)))
        content.addView(text(getString(R.string.original_note), 13, getColor(R.color.secondary_text)))
        content.addView(button(getString(R.string.download)) { choose(listOf(image)) })
        content.addView(button(getString(R.string.share_link)) {
            val share = Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, image.copyright + "\n" + image.url("UHD"))
            startActivity(Intent.createChooser(share, getString(R.string.share)))
        })
        content.addView(button(getString(R.string.back_recent)) { selected = null; gallery = false; render() })
    }

    /** Reuse a fresh directory snapshot or enumerate it once on a background worker. */
    private fun library() {
        val key = librarySourceKey()
        val cached = librarySnapshots.get(key, System.currentTimeMillis())
        val page = libraryPage
        if (cached != null && page != null && key == libraryPageKey) { replaceBody(page); return }
        libraryAccessNotice()
        if (cached != null) { bindLibrary(cached, key); return }
        content.addView(text(getString(R.string.loading_library), 15, ink))
        if (key == loadingLibraryKey) return
        loadingLibraryKey = key
        val request = ++libraryRequest
        network.execute {
            try {
                val saved = ImageStore(this).list()
                runOnUiThread {
                    if (isDestroyed || request != libraryRequest) return@runOnUiThread
                    loadingLibraryKey = null
                    if (key != librarySourceKey()) return@runOnUiThread
                    librarySnapshots.put(key, saved.toList(), System.currentTimeMillis())
                    libraryPage = null
                    if (gallery && selected == null) render()
                }
            } catch (error: RuntimeException) {
                runOnUiThread {
                    if (!isDestroyed && request == libraryRequest) {
                        loadingLibraryKey = null
                        if (gallery && selected == null) {
                            content.removeAllViews()
                            libraryAccessNotice()
                            content.addView(text(getString(R.string.directory_failed, error.message), 15, ink))
                        }
                    }
                }
            }
        }
    }

    /** Identify the directory grant and local changes without enumerating any files. */
    private fun librarySourceKey(): String {
        val uri = ImageStore(this).directoryUri()
        val preferences = getSharedPreferences("bing", MODE_PRIVATE)
        return "$uri|${preferences.getLong("library-revision", 0)}|${preferences.getLong("preview-revision", 0)}"
    }

    /** Bind a successful directory snapshot to its retained page. */
    private fun bindLibrary(saved: List<ImageStore.Saved>, key: String) {
        content.addView(text(getString(R.string.photo_count, saved.size), 14, accent))
        if (saved.isEmpty()) content.addView(text(getString(R.string.empty_library), 16, ink))
        showLibrary(saved)
        libraryPageKey = key
    }

    /** Force a new directory snapshot and refreshed previews for externally changed files. */
    private fun refreshLibrary() {
        getSharedPreferences("bing", MODE_PRIVATE).edit().putLong("preview-revision", System.currentTimeMillis()).apply()
        librarySnapshots.clear()
        libraryPage = null
        loadingLibraryKey = null
        render()
    }

    /** Request a directory grant independent of media indexing and photo permissions. */
    private fun libraryAccessNotice() {
        val selectedDirectory = ImageStore(this).directoryUri() != null
        content.addView(text(getString(if (selectedDirectory) R.string.directory_ready else R.string.directory_prompt), 14, ink))
        content.addView(button(getString(if (selectedDirectory) R.string.reselect_directory else R.string.select_directory)) {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                .putExtra(DocumentsContract.EXTRA_INITIAL_URI,
                    Uri.parse("content://com.android.externalstorage.documents/document/primary%3ADCIM%2FBingWallpapers"))
            try {
                startActivityForResult(intent, 3)
            } catch (_: ActivityNotFoundException) {
                status.setText(R.string.no_picker)
            }
        })
        content.addView(button(getString(R.string.refresh_library), ::refreshLibrary))
    }

    /** Bind only visible rows, keeping large legacy collections out of bitmap memory. */
    private fun showLibrary(saved: List<ImageStore.Saved>) {
        (content.parent as? ViewGroup)?.removeView(content)
        val list = ListView(this).apply {
            dividerHeight = dp(12)
            addHeaderView(content, null, false)
        }
        list.adapter = object : BaseAdapter() {
            override fun getCount(): Int = saved.size
            override fun getItem(index: Int): ImageStore.Saved = saved[index]
            override fun getItemId(index: Int): Long = index.toLong()
            override fun getView(index: Int, recycled: View?, parent: ViewGroup): View {
                val item = getItem(index)
                return LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(12), 0, dp(12), 0)
                    addView(photo(item.uri.toString(), null, item.uri, 190, true))
                    addView(text(item.name, 14, ink))
                }
            }
        }
        list.setOnItemClickListener { _, _, index, _ ->
            if (index < list.headerViewsCount) return@setOnItemClickListener
            val item = saved[index - list.headerViewsCount]
            try {
                startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(item.uri, "image/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
            } catch (error: RuntimeException) {
                if (error !is ActivityNotFoundException && error !is SecurityException) throw error
                status.setText(R.string.open_failed)
            }
        }
        libraryPage = list
        replaceBody(list)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (requestCode == 3 && resultCode == RESULT_OK && uri != null) {
            try {
                ImageStore(this).selectDirectory(uri)
                librarySnapshots.clear()
                libraryPage = null
                gallery = true
                selected = null
                render()
            } catch (error: RuntimeException) {
                if (error !is IllegalArgumentException && error !is SecurityException) throw error
                status.text = getString(R.string.directory_grant_failed, error.message)
            }
        }
    }

    /** Show download, refresh, market and appearance settings. */
    private fun menu() {
        val labels = intArrayOf(R.string.refresh, R.string.download_all, R.string.change_region,
            R.string.notifications, R.string.theme, R.string.language, R.string.about).map { getString(it) }.toTypedArray()
        AlertDialog.Builder(this).setTitle(R.string.app_name).setItems(labels) { _, which ->
            when (which) {
                0 -> if (gallery && selected == null) refreshLibrary() else load()
                1 -> if (images.isEmpty()) status.setText(R.string.load_first) else choose(images.toList())
                2 -> AlertDialog.Builder(this).setTitle(R.string.region_title)
                    .setItems(arrayOf(getString(R.string.china), getString(R.string.usa), getString(R.string.japan))) { _, index ->
                        market = arrayOf("zh-CN", "en-US", "ja-JP")[index]
                        getPreferences(MODE_PRIVATE).edit().putString("market", market).apply()
                        recentPage = null
                        imagesFetchedAt = 0
                        lastMetadataAttempt = 0
                        images = cachedMetadata()
                        imagesFetchedAt = getPreferences(MODE_PRIVATE).getLong("cache-time-$market", 0)
                        selected = null
                        gallery = false
                        render()
                        refreshRecent(false)
                    }.show()
                3 -> if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
                    else status.setText(R.string.no_notification_permission_needed)
                4 -> showAppearance(true)
                5 -> showAppearance(false)
                else -> AlertDialog.Builder(this)
                    .setTitle(getString(R.string.about_title, getString(R.string.app_name), "1.0.8"))
                    .setMessage(R.string.about_body).setPositiveButton(R.string.ok, null).show()
            }
        }.show()
    }

    /** Apply a persisted appearance option and recreate only the current Activity. */
    private fun showAppearance(theme: Boolean) {
        val key = if (theme) "theme" else "language"
        val codes = if (theme) arrayOf("system", "light", "dark") else arrayOf("zh", "en", "ja", "system")
        val labels = if (theme) arrayOf(getString(R.string.follow_system), getString(R.string.light), getString(R.string.dark))
            else arrayOf("简体中文", "English", "日本語", getString(R.string.follow_system))
        val current = getSharedPreferences("appearance", MODE_PRIVATE).getString(key, if (theme) "system" else "zh")
        AlertDialog.Builder(this).setTitle(if (theme) R.string.theme else R.string.language)
            .setSingleChoiceItems(labels, codes.indexOf(current)) { dialog, index ->
                dialog.dismiss()
                if (codes[index] != current) {
                    getSharedPreferences("appearance", MODE_PRIVATE).edit().putString(key, codes[index]).apply()
                    recreate()
                }
            }.setNeutralButton(R.string.cancel, null).show()
    }

    /** Encode metadata using the existing on-disk JSON contract. */
    @Throws(JSONException::class)
    private fun encoded(list: List<Wallpaper>): String {
        val array = JSONArray()
        list.forEach { array.put(it.toJson()) }
        return JSONObject().put("images", array).toString()
    }

    /** Download immediately when a size is tapped, with the cancel action at the left. */
    private fun choose(list: List<Wallpaper>) {
        val sizes = Resolution.entries
        val labels = sizes.map { it.label(this) }.toTypedArray()
        val picker = AlertDialog.Builder(this)
            .setTitle(if (list.size > 1) getString(R.string.choose_batch_size, list.size) else getString(R.string.choose_size))
            .setItems(labels) { dialog, index ->
                dialog.dismiss()
                try {
                    val array = JSONObject(encoded(list)).getJSONArray("images").toString()
                    startForegroundService(Intent(this, DownloadService::class.java)
                        .putExtra("images", array).putExtra("resolution", sizes[index].name))
                    status.text = getString(R.string.queued, sizes[index].label(this))
                } catch (error: Exception) {
                    if (error !is JSONException && error !is RuntimeException) throw error
                    status.text = getString(R.string.start_failed, error.message)
                }
            }.setNeutralButton(R.string.cancel, null).create()
        picker.show()
        // Some platform themes center a sole button; keep this cancel action at the left.
        val cancel = picker.getButton(AlertDialog.BUTTON_NEUTRAL)
        cancel.layoutParams = (cancel.layoutParams as LinearLayout.LayoutParams).apply { weight = 0f; gravity = Gravity.START }
    }

    private fun load() {
        selected = null
        gallery = false
        render()
        refreshRecent(true)
    }

    /** Show daily cached metadata first and refresh once per market without hiding other tabs. */
    private fun refreshRecent(force: Boolean) {
        val now = System.currentTimeMillis()
        if (market in loadingMarkets) return
        if (!force && images.isNotEmpty() && CachePolicy.sameDay(imagesFetchedAt, now, ZoneId.systemDefault())) return
        if (!force && !CachePolicy.retryAllowed(lastMetadataAttempt, now)) return
        lastMetadataAttempt = now
        val requestedMarket = market
        loadingMarkets.add(requestedMarket)
        if (!gallery && selected == null) status.setText(if (images.isEmpty()) R.string.loading else R.string.updating)
        network.execute {
            try {
                val raw = BingClient.metadata(this, requestedMarket)
                val loaded = BingClient.parse(raw)
                val fetchedAt = System.currentTimeMillis()
                getPreferences(MODE_PRIVATE).edit().putString("cache-$requestedMarket", raw)
                    .putLong("cache-time-$requestedMarket", fetchedAt).apply()
                runOnUiThread {
                    if (isDestroyed) return@runOnUiThread
                    loadingMarkets.remove(requestedMarket)
                    if (market != requestedMarket) return@runOnUiThread
                    images = loaded
                    imagesFetchedAt = fetchedAt
                    recentPage = null
                    if (!gallery && selected == null) render()
                }
            } catch (error: Exception) {
                if (error !is IOException && error !is JSONException && error !is RuntimeException) throw error
                runOnUiThread {
                    if (isDestroyed) return@runOnUiThread
                    loadingMarkets.remove(requestedMarket)
                    if (market == requestedMarket && !gallery && selected == null) {
                        status.text = getString(if (images.isEmpty()) R.string.refresh_failed else R.string.refresh_failed_cached, error.message)
                    }
                }
            }
        }
    }
}
