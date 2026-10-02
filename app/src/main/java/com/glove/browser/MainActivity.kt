package com.glove.browser

import android.Manifest
import android.annotation.SuppressLint
import android.app.DownloadManager
import android.app.role.RoleManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.util.Base64
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.glove.browser.databinding.ActivityMainBinding
import org.json.JSONObject
import java.io.File
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var store: BrowserStore
    private val tabs = mutableListOf<Tab>()
    private var index = 0
    private var desktop = false
    private val desktopScripts = mutableMapOf<WebView, ScriptHandler>()
    private val closedTabs = mutableListOf<String>()
    private val logoUri: String by lazy {
        val bytes = assets.open("logo.png").use { it.readBytes() }
        "data:image/png;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
    private var switcherIncognito = false
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var geoOrigin: String? = null
    private var geoCallback: GeolocationPermissions.Callback? = null
    private var permissionRequest: PermissionRequest? = null
    private var cameraUri: Uri? = null
    private var customView: View? = null
    private var customCallback: WebChromeClient.CustomViewCallback? = null
    private var suggestionItems = emptyList<LinkItem>()
    private val tabAdapter = TabAdapter()

    private val roleRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { }

    private val openPage = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.getStringExtra(PagesActivity.EXTRA_URL)?.let { navigate(it) }
    }

    private val pickFile = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val cb = fileCallback
        fileCallback = null
        val data = result.data
        val uris = when {
            result.resultCode != RESULT_OK -> null
            data?.data != null -> arrayOf(data.data!!)
            data?.clipData != null -> Array(data.clipData!!.itemCount) { data.clipData!!.getItemAt(it).uri }
            cameraUri != null -> arrayOf(cameraUri!!)
            else -> null
        }
        cb?.onReceiveValue(uris)
        cameraUri = null
    }

    private val askPermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        val geo = geoCallback
        val origin = geoOrigin
        if (geo != null && origin != null) {
            val ok = granted[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                granted[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            geo.invoke(origin, ok, false)
            geoCallback = null
            geoOrigin = null
        }
        permissionRequest?.let { req ->
            if (granted.values.all { it }) req.grant(req.resources) else req.deny()
            permissionRequest = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        store = BrowserStore(this)
        desktop = store.desktopSite
        CookieManager.getInstance().setAcceptCookie(true)
        binding.browser.isFocusableInTouchMode = true

        binding.tabGrid.layoutManager = GridLayoutManager(this, 2)
        binding.tabGrid.adapter = tabAdapter
        binding.tabCount.setOnClickListener { openSwitcher() }
        binding.menu.setOnClickListener { showMenu() }
        binding.security.setOnClickListener { onSecurityClick() }
        binding.action.setOnClickListener { onActionClick() }
        binding.switcherBack.setOnClickListener { closeSwitcher() }
        binding.switcherMode.setOnClickListener {
            switcherIncognito = !switcherIncognito
            paintSwitcher()
            tabAdapter.notifyDataSetChanged()
        }
        binding.newTab.setOnClickListener {
            openTab(switcherIncognito)
            closeSwitcher()
        }
        binding.swipe.setColorSchemeColors(ContextCompat.getColor(this, R.color.accent))
        binding.swipe.setDistanceToTriggerSync((140 * resources.displayMetrics.density).toInt())
        binding.swipe.setOnChildScrollUpCallback { _, _ ->
            val web = tabs.getOrNull(index)?.webView
            web != null && (web.scrollY > 0 || web.canScrollVertically(-1))
        }
        binding.swipe.setOnRefreshListener {
            val tab = current()
            if (tab.home) showHome(tab) else tab.webView.reload()
        }
        binding.address.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE) {
                navigate(binding.address.text.toString())
                true
            } else false
        }
        binding.address.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (binding.address.hasFocus()) showSuggestions(s?.toString().orEmpty())
            }
        })
        binding.address.setOnFocusChangeListener { _, hasFocus ->
            val tab = current()
            if (hasFocus) {
                binding.address.setText(if (tab.home) "" else tab.webView.url.orEmpty().removePrefix("https://orion.glove"))
                binding.address.selectAll()
                binding.action.setImageResource(R.drawable.ic_close)
                showSuggestions(binding.address.text.toString())
            } else {
                binding.suggestions.visibility = View.GONE
                paintLocation(tab)
            }
            val lift = if (hasFocus) 6f * resources.displayMetrics.density else 0f
            binding.omnibox.animate().translationZ(lift).setDuration(140).start()
        }
        binding.suggestions.setOnItemClickListener { _, _, position, _ ->
            suggestionItems.getOrNull(position)?.let { navigate(it.url) }
        }
        binding.findQuery.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                current().webView.findAllAsync(s?.toString().orEmpty())
            }
        })
        binding.findUp.setOnClickListener { current().webView.findNext(false) }
        binding.findDown.setOnClickListener { current().webView.findNext(true) }
        binding.findClose.setOnClickListener { closeFind() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = goBack()
        })

        openTab(false)
        intent?.dataString?.let { if (it.startsWith("http")) navigate(it) }
        if (!store.introSeen) showIntro()
        binding.browser.requestFocus()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.dataString?.let { if (it.startsWith("http")) navigate(it) }
    }

    private fun goBack() {
        when {
            customView != null -> hideCustomView()
            binding.findBar.visibility == View.VISIBLE -> closeFind()
            binding.intro.visibility == View.VISIBLE -> finishIntro()
            binding.suggestions.visibility == View.VISIBLE -> {
                binding.address.clearFocus()
                binding.suggestions.visibility = View.GONE
            }
            binding.switcher.visibility == View.VISIBLE -> closeSwitcher()
            current().webView.canGoBack() -> current().webView.goBack()
            !current().home -> showHome(current())
            else -> moveTaskToBack(true)
        }
    }

    private fun current() = tabs[index]

    private fun openTab(incognito: Boolean, url: String? = null) {
        val tab = Tab(createWebView(incognito), incognito)
        tabs += tab
        index = tabs.lastIndex
        show(tab)
        if (url == null) showHome(tab) else tab.webView.loadUrl(url)
        paintToolbar()
    }

    private fun show(tab: Tab) {
        val previous = binding.webContainer.getChildAt(0) as? WebView
        if (previous != null && previous !== tab.webView) {
            tabs.find { it.webView === previous }?.let { capture(it) }
        }
        (tab.webView.parent as? ViewGroup)?.removeView(tab.webView)
        binding.webContainer.removeAllViews()
        binding.webContainer.addView(
            tab.webView,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        )
        if (previous !== tab.webView) {
            tab.webView.alpha = 0f
            tab.webView.animate().alpha(1f).setDuration(160).start()
        }
        paintLocation(tab)
        paintToolbar()
        binding.swipe.isRefreshing = false
        binding.swipe.isEnabled = tab.webView.scrollY <= 0
    }

    private fun showHome(tab: Tab) {
        tab.home = true
        tab.searchGen++
        tab.title = getString(R.string.new_tab)
        val shortcuts = store.bookmarks().take(8).map { it.title to it.url }
        tab.webView.loadDataWithBaseURL(HOME, Orion.homeHtml(shortcuts, logoUri), "text/html", "utf-8", null)
        paintLocation(tab)
    }

    private fun navigate(raw: String) {
        val text = raw.trim()
        hideKeyboard()
        binding.address.clearFocus()
        binding.suggestions.visibility = View.GONE
        if (text.isEmpty()) return
        val tab = current()
        tab.home = false
        if (text.startsWith("https://orion.glove/search")) {
            val q = Uri.parse(text).getQueryParameter("q").orEmpty()
            if (q.isBlank()) showHome(tab) else runSearch(tab, q)
        } else if (looksLikeUrl(text)) {
            tab.webView.loadUrl(normalize(text))
        } else {
            runSearch(tab, text)
        }
    }

    private fun runSearch(tab: Tab, query: String) {
        tab.home = false
        tab.title = query
        tab.searchGen++
        val gen = tab.searchGen
        tab.webView.loadDataWithBaseURL(
            HOME,
            Orion.resultsHtml(query, emptyList(), "Ищем…"),
            "text/html",
            "utf-8",
            null
        )
        thread(name = "orion") {
            val html = try {
                Orion.resultsHtml(query, Orion.search(query), null)
            } catch (_: Exception) {
                Orion.resultsHtml(query, emptyList(), "Не удалось получить результаты. Проверьте сеть.")
            }
            runOnUiThread {
                if (tab.searchGen == gen && tabs.contains(tab)) {
                    tab.webView.loadDataWithBaseURL(HOME, html, "text/html", "utf-8", null)
                }
            }
        }
    }

    private fun looksLikeUrl(text: String): Boolean {
        val lower = text.lowercase()
        if (lower.startsWith("http://") || lower.startsWith("https://")) return true
        return !text.contains(" ") && (text.contains(".") || lower.startsWith("localhost"))
    }

    private fun normalize(text: String): String {
        val lower = text.lowercase()
        return if (lower.startsWith("http://") || lower.startsWith("https://")) text else "https://$text"
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(incognito: Boolean): WebView {
        val view = WebView(this)
        val settings = view.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = !incognito
        settings.javaScriptCanOpenWindowsAutomatically = true
        settings.setSupportMultipleWindows(true)
        settings.loadsImagesAutomatically = true
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.mediaPlaybackRequiresUserGesture = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        settings.setGeolocationEnabled(true)
        settings.safeBrowsingEnabled = true
        settings.cacheMode = if (incognito) WebSettings.LOAD_NO_CACHE else WebSettings.LOAD_DEFAULT
        applyClientMode(view)
        view.setOnScrollChangeListener { scrolled, _, scrollY, _, _ ->
            if (tabs.getOrNull(index)?.webView === scrolled) {
                binding.swipe.isEnabled = scrollY <= 0
            }
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(view, true)
        view.setDownloadListener { url, userAgent, contentDisposition, mime, _ ->
            download(url, userAgent, contentDisposition, mime)
        }
        view.setOnLongClickListener {
            val hit = view.hitTestResult
            val extra = hit.extra
            if (extra.isNullOrBlank()) return@setOnLongClickListener false
            when (hit.type) {
                WebView.HitTestResult.SRC_ANCHOR_TYPE,
                WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE,
                WebView.HitTestResult.IMAGE_TYPE -> {
                    showLinkMenu(extra)
                    true
                }
                else -> false
            }
        }
        view.setFindListener { active, number, done ->
            binding.findCount.text = if (done && number > 0) "${active + 1}/$number" else "0/0"
        }
        view.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(v: WebView, request: WebResourceRequest): Boolean {
                val uri = request.url
                if (uri.host == "orion.glove") {
                    val tab = tabs.find { it.webView === v } ?: return true
                    val q = uri.getQueryParameter("q").orEmpty()
                    if (q.isBlank()) showHome(tab) else runSearch(tab, q)
                    return true
                }
                val scheme = uri.scheme?.lowercase() ?: return false
                if (scheme == "http" || scheme == "https" || scheme == "file" || scheme == "about" || scheme == "data") {
                    return false
                }
                return try {
                    startActivity(Intent(Intent.ACTION_VIEW, uri))
                    true
                } catch (_: Exception) {
                    true
                }
            }

            override fun onPageStarted(v: WebView, url: String, favicon: Bitmap?) {
                val tab = tabs.find { it.webView === v } ?: return
                if (!url.startsWith(HOME)) tab.home = false
                tab.loading = true
                if (desktop) runDesktopScript(v)
                if (tabs.getOrNull(index)?.webView === v && !binding.address.hasFocus()) paintLocation(tab)
            }

            override fun onPageFinished(v: WebView, url: String) {
                val tab = tabs.find { it.webView === v } ?: return
                tab.loading = false
                if (!tab.home && !tab.incognito && !url.startsWith(HOME)) {
                    tab.title = v.title ?: url
                    store.addHistory(tab.title, url)
                }
                if (desktop) runDesktopScript(v)
                if (tab.home) publishNews(tab)
                if (tabs.getOrNull(index)?.webView === v) {
                    binding.swipe.isRefreshing = false
                    binding.swipe.isEnabled = v.scrollY <= 0
                    paintLocation(tab)
                    paintToolbar()
                }
            }
        }
        view.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(v: WebView, newProgress: Int) {
                val tab = tabs.find { it.webView === v } ?: return
                tab.loading = newProgress in 1..99
                if (tabs.getOrNull(index)?.webView === v) {
                    binding.progress.progress = newProgress
                    binding.progress.visibility = if (tab.loading) View.VISIBLE else View.INVISIBLE
                    if (!tab.loading) binding.swipe.isRefreshing = false
                    if (!binding.address.hasFocus()) paintLocation(tab)
                }
            }

            override fun onReceivedTitle(v: WebView, title: String?) {
                val tab = tabs.find { it.webView === v } ?: return
                if (!tab.home && !title.isNullOrBlank()) tab.title = title
            }

            override fun onReceivedIcon(v: WebView, icon: Bitmap?) {
                tabs.find { it.webView === v }?.favicon = icon
            }

            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                customCallback?.onCustomViewHidden()
                customView = view
                customCallback = callback
                binding.fullscreen.addView(view)
                binding.fullscreen.visibility = View.VISIBLE
                binding.browser.visibility = View.GONE
            }

            override fun onHideCustomView() = hideCustomView()

            override fun onCreateWindow(v: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: android.os.Message): Boolean {
                val incognito = tabs.find { it.webView === v }?.incognito == true
                val probe = WebView(this@MainActivity)
                probe.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        openTab(incognito, request.url.toString())
                        view.destroy()
                        return true
                    }
                }
                val transport = resultMsg.obj as WebView.WebViewTransport
                transport.webView = probe
                resultMsg.sendToTarget()
                return true
            }

            override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {
                val fine = ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.ACCESS_FINE_LOCATION)
                if (fine == PackageManager.PERMISSION_GRANTED) callback.invoke(origin, true, false) else {
                    geoOrigin = origin
                    geoCallback = callback
                    askPermissions.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                }
            }

            override fun onPermissionRequest(request: PermissionRequest) {
                val needed = request.resources.mapNotNull {
                    when (it) {
                        PermissionRequest.RESOURCE_VIDEO_CAPTURE -> Manifest.permission.CAMERA
                        PermissionRequest.RESOURCE_AUDIO_CAPTURE -> Manifest.permission.RECORD_AUDIO
                        else -> null
                    }
                }.distinct()
                val missing = needed.filter {
                    ContextCompat.checkSelfPermission(this@MainActivity, it) != PackageManager.PERMISSION_GRANTED
                }
                if (missing.isEmpty()) request.grant(request.resources) else {
                    permissionRequest = request
                    askPermissions.launch(missing.toTypedArray())
                }
            }

            override fun onShowFileChooser(v: WebView, callback: ValueCallback<Array<Uri>>, params: FileChooserParams): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = callback
                val capture = File(cacheDir, "capture-${System.currentTimeMillis()}.jpg")
                cameraUri = FileProvider.getUriForFile(this@MainActivity, "$packageName.files", capture)
                val camera = Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)
                    .putExtra(android.provider.MediaStore.EXTRA_OUTPUT, cameraUri)
                val content = Intent(Intent.ACTION_GET_CONTENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = params.acceptTypes?.firstOrNull()?.ifBlank { "*/*" } ?: "*/*"
                }
                pickFile.launch(Intent.createChooser(content, getString(R.string.open)).apply {
                    putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(camera))
                })
                return true
            }
        }
        return view
    }

    private fun download(url: String, userAgent: String, disposition: String, mime: String) {
        try {
            val name = URLUtil.guessFileName(url, disposition, mime)
            val request = DownloadManager.Request(Uri.parse(url))
                .setMimeType(mime)
                .addRequestHeader("User-Agent", userAgent)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
            CookieManager.getInstance().getCookie(url)?.let { request.addRequestHeader("Cookie", it) }
            getSystemService(DownloadManager::class.java).enqueue(request)
            store.addDownload(name, url)
            Toast.makeText(this, name, Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            Toast.makeText(this, "Не удалось скачать", Toast.LENGTH_SHORT).show()
        }
    }

    private fun paintLocation(tab: Tab) {
        val incognito = tab.incognito
        val icon = if (incognito) Color.WHITE else Color.parseColor("#3C4043")
        val text = if (incognito) Color.WHITE else Color.parseColor("#202124")
        val hint = if (incognito) Color.parseColor("#9AA0A6") else Color.parseColor("#80868B")
        binding.address.setTextColor(text)
        binding.address.setHintTextColor(hint)
        binding.security.setColorFilter(icon)
        binding.action.setColorFilter(icon)
        val bg = GradientDrawable().apply {
            cornerRadius = 20f * resources.displayMetrics.density
            setColor(if (incognito) Color.parseColor("#303134") else Color.parseColor("#F1F3F4"))
        }
        binding.omnibox.background = bg
        if (!binding.address.hasFocus()) {
            val url = tab.webView.url.orEmpty()
            binding.address.setText(
                when {
                    tab.home -> ""
                    url.startsWith(HOME) -> tab.title
                    else -> hostOf(url)
                }
            )
            binding.action.setImageResource(if (tab.loading) R.drawable.ic_close else R.drawable.ic_reload)
            binding.action.contentDescription = getString(if (tab.loading) R.string.stop else R.string.reload)
        }
        val url = tab.webView.url.orEmpty()
        val secure = url.startsWith("https://") && !tab.home
        binding.security.setImageResource(
            when {
                tab.home -> R.drawable.ic_search
                secure -> R.drawable.ic_lock
                else -> R.drawable.ic_info
            }
        )
    }

    private fun paintToolbar() {
        val incognito = tabs.getOrNull(index)?.incognito == true
        val bar = if (incognito) Color.parseColor("#202124") else Color.WHITE
        val icon = if (incognito) Color.WHITE else Color.parseColor("#3C4043")
        binding.toolbar.setBackgroundColor(bar)
        binding.root.setBackgroundColor(bar)
        window.statusBarColor = bar
        window.navigationBarColor = bar
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = !incognito
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightNavigationBars = !incognito
        binding.menu.setColorFilter(icon)
        binding.tabCount.setTextColor(icon)
        (binding.tabCount.background.mutate() as? GradientDrawable)?.setStroke(
            (1.6f * resources.displayMetrics.density).toInt(),
            icon
        )
        binding.tabCount.text = tabs.count { it.incognito == incognito }.coerceAtLeast(1).toString()
    }

    private fun hostOf(url: String?): String {
        if (url.isNullOrBlank() || url.startsWith(HOME) || url.startsWith("data:")) return ""
        return Uri.parse(url).host?.removePrefix("www.") ?: url
    }

    private fun onSecurityClick() {
        val tab = current()
        if (tab.home) {
            binding.address.requestFocus()
            return
        }
        val url = tab.webView.url ?: return
        val secure = url.startsWith("https://")
        AlertDialog.Builder(this)
            .setTitle(if (secure) R.string.secure else R.string.insecure)
            .setMessage(url)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun onActionClick() {
        if (binding.address.hasFocus()) {
            binding.address.setText("")
            return
        }
        val tab = current()
        if (tab.loading) tab.webView.stopLoading() else if (tab.home) showHome(tab) else tab.webView.reload()
    }

    private fun showMenu() {
        val tab = current()
        val url = tab.webView.url
        val bookmarked = url != null && !tab.home && store.isBookmarked(url)
        val popup = PopupMenu(this, binding.menu)
        popup.menu.apply {
            add(0, 1, 1, R.string.new_tab)
            add(0, 2, 2, R.string.incognito)
            add(0, 3, 3, R.string.bookmarks)
            add(0, 4, 4, R.string.history)
            add(0, 5, 5, R.string.downloads)
            add(0, 6, 6, R.string.forward).isEnabled = tab.webView.canGoForward()
            add(0, 7, 7, R.string.share).isEnabled = !tab.home
            add(0, 8, 8, R.string.find).isEnabled = !tab.home
            add(0, 9, 9, if (bookmarked) R.string.remove_bookmark else R.string.add_bookmark).isEnabled = !tab.home
            add(0, 10, 10, R.string.desktop).isCheckable = true
            findItem(10).isChecked = desktop
            add(0, 11, 11, R.string.copy_link).isEnabled = !tab.home
            add(0, 12, 12, R.string.home)
            add(0, 14, 14, R.string.duplicate).isEnabled = !tab.home
            add(0, 15, 15, R.string.reopen).isEnabled = closedTabs.isNotEmpty()
            add(0, 16, 16, R.string.zoom_in).isEnabled = !tab.home
            add(0, 17, 17, R.string.zoom_out).isEnabled = !tab.home
            add(0, 18, 18, R.string.close_others).isEnabled = tabs.size > 1
            add(0, 13, 19, R.string.settings)
        }
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> openTab(false)
                2 -> openTab(true)
                3 -> openList(PagesActivity.MODE_BOOKMARKS)
                4 -> openList(PagesActivity.MODE_HISTORY)
                5 -> openList(PagesActivity.MODE_DOWNLOADS)
                6 -> tab.webView.goForward()
                7 -> share(tab.webView.url)
                8 -> {
                    binding.findBar.visibility = View.VISIBLE
                    binding.findQuery.requestFocus()
                }
                9 -> {
                    val page = tab.webView.url ?: return@setOnMenuItemClickListener false
                    store.toggleBookmark(tab.webView.title ?: page, page)
                }
                10 -> toggleDesktop()
                11 -> copy(tab.webView.url)
                12 -> showHome(current())
                13 -> startActivity(Intent(this, SettingsActivity::class.java))
                14 -> openTab(tab.incognito, if (tab.home) null else tab.webView.url)
                15 -> if (closedTabs.isNotEmpty()) openTab(false, closedTabs.removeAt(0))
                16 -> tab.webView.settings.textZoom = (tab.webView.settings.textZoom + 10).coerceAtMost(200)
                17 -> tab.webView.settings.textZoom = (tab.webView.settings.textZoom - 10).coerceAtLeast(50)
                18 -> closeOthers()
            }
            true
        }
        popup.show()
    }

    private fun openList(mode: String) {
        openPage.launch(Intent(this, PagesActivity::class.java).putExtra(PagesActivity.EXTRA_MODE, mode))
    }

    private fun toggleDesktop() {
        desktop = !desktop
        store.desktopSite = desktop
        tabs.forEach {
            try {
                applyClientMode(it.webView)
                if (!it.home) it.webView.reload()
            } catch (_: Throwable) {
            }
        }
    }

    private fun applyClientMode(view: WebView) {
        val settings = view.settings
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        if (desktop) {
            settings.userAgentString = DESKTOP_UA
            ensureDesktopScript(view)
            val screen = resources.displayMetrics.widthPixels.coerceAtLeast(1)
            view.setInitialScale((screen * 100 / DESKTOP_WIDTH).coerceIn(25, 100))
        } else {
            settings.userAgentString = WebSettings.getDefaultUserAgent(this)
            desktopScripts.remove(view)?.let { handler ->
                try {
                    handler.remove()
                } catch (_: Throwable) {
                }
            }
            view.setInitialScale(0)
        }
    }

    private fun runDesktopScript(view: WebView) {
        try {
            view.evaluateJavascript(DESKTOP_BOOTSTRAP, null)
        } catch (_: Throwable) {
        }
    }

    private fun publishNews(tab: Tab) {
        NewsFeed.load { body ->
            runOnUiThread {
                if (!tab.home || !tabs.contains(tab)) return@runOnUiThread
                try {
                    tab.webView.evaluateJavascript(
                        "typeof gloveNews==='function'&&gloveNews(${JSONObject.quote(body)})",
                        null
                    )
                } catch (_: Throwable) {
                }
            }
        }
    }

    private fun showIntro() {
        binding.intro.alpha = 0f
        binding.intro.visibility = View.VISIBLE
        binding.intro.animate().alpha(1f).setDuration(220).start()
        binding.introStart.setOnClickListener { finishIntro() }
        binding.introDefault.setOnClickListener { requestDefaultBrowser() }
        binding.intro.post { requestDefaultBrowser() }
    }

    private fun finishIntro() {
        store.introSeen = true
        binding.intro.animate().alpha(0f).setDuration(160).withEndAction {
            binding.intro.visibility = View.GONE
        }.start()
    }

    private fun requestDefaultBrowser() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val roles = getSystemService(RoleManager::class.java)
                if (roles != null && roles.isRoleAvailable(RoleManager.ROLE_BROWSER)) {
                    if (!roles.isRoleHeld(RoleManager.ROLE_BROWSER)) {
                        roleRequest.launch(roles.createRequestRoleIntent(RoleManager.ROLE_BROWSER))
                    }
                    return
                }
            }
            startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
        } catch (_: Exception) {
        }
    }

    private fun rememberClosed(tab: Tab) {
        if (tab.incognito || tab.home) return
        val url = tab.webView.url ?: return
        if (!url.startsWith("http")) return
        closedTabs.add(0, url)
        while (closedTabs.size > 8) closedTabs.removeAt(closedTabs.lastIndex)
    }

    private fun closeOthers() {
        val keep = current()
        val drop = tabs.filter { it !== keep }
        drop.forEach { rememberClosed(it) }
        tabs.removeAll { it !== keep }
        index = 0
        drop.forEach { releaseWebView(it.webView) }
        show(keep)
        paintToolbar()
    }

    private fun releaseWebView(view: WebView) {
        desktopScripts.remove(view)?.let {
            try {
                it.remove()
            } catch (_: Exception) {
            }
        }
        view.destroy()
    }

    private fun ensureDesktopScript(view: WebView) {
        if (desktopScripts.containsKey(view)) return
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return
        try {
            desktopScripts[view] = WebViewCompat.addDocumentStartJavaScript(
                view,
                DESKTOP_BOOTSTRAP,
                setOf("http://*", "https://*")
            )
        } catch (_: Throwable) {
        }
    }

    private fun share(url: String?) {
        if (url.isNullOrBlank() || url.startsWith(HOME)) return
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, url)
        }, getString(R.string.share)))
    }

    private fun copy(url: String?) {
        if (url.isNullOrBlank() || url.startsWith(HOME)) return
        getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("url", url))
        Toast.makeText(this, R.string.copy_link, Toast.LENGTH_SHORT).show()
    }

    private fun showLinkMenu(url: String) {
        val labels = arrayOf(
            getString(R.string.open),
            getString(R.string.open_new),
            getString(R.string.copy_link),
            getString(R.string.share),
            getString(R.string.download)
        )
        AlertDialog.Builder(this)
            .setItems(labels) { _, which ->
                when (which) {
                    0 -> navigate(url)
                    1 -> openTab(current().incognito, url)
                    2 -> copy(url)
                    3 -> share(url)
                    4 -> download(url, current().webView.settings.userAgentString, "", "*/*")
                }
            }
            .show()
    }

    private fun showSuggestions(query: String) {
        val q = query.trim()
        val recent = (store.history() + store.bookmarks()).distinctBy { it.url }
        val matched = recent.filter {
            q.isEmpty() || it.title.contains(q, true) || it.url.contains(q, true)
        }.take(8)
        suggestionItems = if (q.isEmpty()) matched else listOf(LinkItem("Искать: $q", q, 0)) + matched
        binding.suggestions.adapter = object : android.widget.BaseAdapter() {
            override fun getCount() = suggestionItems.size
            override fun getItem(position: Int) = suggestionItems[position]
            override fun getItemId(position: Int) = position.toLong()
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val row = convertView ?: layoutInflater.inflate(R.layout.item_link, parent, false)
                val item = suggestionItems[position]
                row.findViewById<TextView>(R.id.title).text = item.title
                row.findViewById<TextView>(R.id.url).text = item.url
                return row
            }
        }
        binding.suggestions.visibility = if (suggestionItems.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun openSwitcher() {
        capture(current())
        switcherIncognito = current().incognito
        binding.suggestions.visibility = View.GONE
        binding.switcher.alpha = 0f
        binding.switcher.translationY = 16f * resources.displayMetrics.density
        binding.switcher.visibility = View.VISIBLE
        binding.switcher.animate().alpha(1f).translationY(0f).setDuration(180).start()
        paintSwitcher()
        tabAdapter.notifyDataSetChanged()
    }

    private fun closeSwitcher() {
        binding.switcher.visibility = View.GONE
        paintToolbar()
    }

    private fun paintSwitcher() {
        val count = tabs.count { it.incognito == switcherIncognito }
        val dark = switcherIncognito
        val bg = if (dark) Color.parseColor("#202124") else Color.parseColor("#DEE1E6")
        val fg = if (dark) Color.WHITE else Color.parseColor("#202124")
        binding.switcher.setBackgroundColor(bg)
        binding.switcherTitle.setTextColor(fg)
        binding.switcherTitle.text = resources.getQuantityString(R.plurals.tab_count, count, count)
        binding.switcherMode.setText(if (dark) R.string.new_tab else R.string.incognito)
        binding.switcherBack.setColorFilter(fg)
        window.statusBarColor = bg
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = !dark
    }

    private fun closeFind() {
        current().webView.clearMatches()
        binding.findBar.visibility = View.GONE
        hideKeyboard()
    }

    private fun hideCustomView() {
        binding.fullscreen.removeAllViews()
        binding.fullscreen.visibility = View.GONE
        binding.browser.visibility = View.VISIBLE
        customCallback?.onCustomViewHidden()
        customView = null
        customCallback = null
    }

    private fun closeTab(at: Int) {
        if (at !in tabs.indices) return
        val closingCurrent = at == index
        val doomed = tabs.removeAt(at)
        rememberClosed(doomed)
        doomed.preview?.recycle()
        releaseWebView(doomed.webView)
        if (at < index) index--
        if (tabs.isEmpty()) {
            openTab(false)
        } else if (closingCurrent) {
            index = index.coerceIn(0, tabs.lastIndex)
            show(tabs[index])
        }
        paintToolbar()
        if (binding.switcher.visibility == View.VISIBLE) {
            paintSwitcher()
            tabAdapter.notifyDataSetChanged()
        }
    }

    private fun capture(tab: Tab) {
        val w = tab.webView.width
        val h = tab.webView.height
        if (w < 2 || h < 2) return
        val bitmap = Bitmap.createBitmap(w / 2, h / 2, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.scale(0.5f, 0.5f)
        tab.webView.draw(canvas)
        tab.preview?.recycle()
        tab.preview = bitmap
    }

    private fun hideKeyboard() {
        getSystemService(InputMethodManager::class.java)
            .hideSoftInputFromWindow(binding.address.windowToken, 0)
    }

    private fun visibleTabPositions(): List<Int> =
        tabs.indices.filter { tabs[it].incognito == switcherIncognito }

    override fun onPause() {
        super.onPause()
        tabs.forEach { it.webView.onPause() }
    }

    override fun onResume() {
        super.onResume()
        tabs.forEach { it.webView.onResume() }
        paintToolbar()
    }

    override fun onDestroy() {
        tabs.forEach {
            it.preview?.recycle()
            releaseWebView(it.webView)
        }
        super.onDestroy()
    }

    private inner class TabAdapter : RecyclerView.Adapter<TabAdapter.Holder>() {
        override fun getItemCount() = visibleTabPositions().size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val view = layoutInflater.inflate(R.layout.item_tab, parent, false)
            val margin = (6 * resources.displayMetrics.density).toInt()
            view.layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(margin, margin, margin, margin) }
            return Holder(view)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val at = visibleTabPositions()[position]
            val tab = tabs[at]
            holder.title.text = tab.title.ifBlank { getString(R.string.new_tab) }
            holder.title.setTextColor(if (tab.incognito) Color.WHITE else Color.parseColor("#202124"))
            holder.favicon.setImageBitmap(tab.favicon)
            if (tab.favicon == null) holder.favicon.setImageResource(R.drawable.ic_search)
            holder.preview.setImageBitmap(tab.preview)
            holder.close.setColorFilter(if (tab.incognito) Color.WHITE else Color.parseColor("#3C4043"))
            holder.itemView.background = GradientDrawable().apply {
                cornerRadius = 16f * resources.displayMetrics.density
                setColor(if (tab.incognito) Color.parseColor("#3C4043") else Color.WHITE)
            }
            holder.itemView.setOnClickListener {
                index = at
                show(tabs[at])
                closeSwitcher()
            }
            holder.close.setOnClickListener { closeTab(at) }
        }

        inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
            val title: TextView = view.findViewById(R.id.title)
            val favicon: ImageView = view.findViewById(R.id.favicon)
            val preview: ImageView = view.findViewById(R.id.preview)
            val close: ImageView = view.findViewById(R.id.close)
        }
    }

    private class Tab(
        val webView: WebView,
        val incognito: Boolean,
        var title: String = "Новая вкладка",
        var home: Boolean = true,
        var loading: Boolean = false,
        var searchGen: Int = 0,
        var favicon: Bitmap? = null,
        var preview: Bitmap? = null
    )

    companion object {
        private const val HOME = "https://orion.glove/"
        private const val DESKTOP_WIDTH = 1280
        private const val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
        private const val DESKTOP_BOOTSTRAP = """
            (function () {
              var WIDTH = 'width=1280, initial-scale=1';
              function apply() {
                var head = document.head || document.getElementsByTagName('head')[0];
                if (!head) return;
                var meta = document.querySelector('meta[name="viewport"]');
                if (!meta) {
                  meta = document.createElement('meta');
                  meta.setAttribute('name', 'viewport');
                  head.appendChild(meta);
                }
                if (meta.getAttribute('content') !== WIDTH) meta.setAttribute('content', WIDTH);
              }
              apply();
              try {
                Object.defineProperty(navigator, 'platform', { get: function () { return 'Win32'; } });
                Object.defineProperty(navigator, 'maxTouchPoints', { get: function () { return 0; } });
              } catch (e) {}
              var watch = function () {
                var head = document.head;
                if (!head) return;
                new MutationObserver(apply).observe(head, { childList: true, subtree: true, attributes: true, attributeFilter: ['content'] });
              };
              if (document.head) watch();
              else document.addEventListener('DOMContentLoaded', function () { apply(); watch(); });
            })();
        """
    }
}
