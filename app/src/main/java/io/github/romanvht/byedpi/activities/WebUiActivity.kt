package io.github.romanvht.byedpi.activities

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.romanvht.byedpi.BuildConfig
import io.github.romanvht.byedpi.R
import io.github.romanvht.byedpi.data.*
import io.github.romanvht.byedpi.services.appStatus
import io.github.romanvht.byedpi.services.ServiceManager
import io.github.romanvht.byedpi.services.TestService
import io.github.romanvht.byedpi.utility.AppStyles
import io.github.romanvht.byedpi.utility.AutoTuneUtils
import io.github.romanvht.byedpi.utility.HistoryUtils
import io.github.romanvht.byedpi.utility.PermissionUtils
import io.github.romanvht.byedpi.utility.SettingsUtils
import io.github.romanvht.byedpi.utility.ThemeFx
import io.github.romanvht.byedpi.utility.getCmdArgs
import io.github.romanvht.byedpi.utility.getPreferences
import io.github.romanvht.byedpi.utility.getStringNotNull
import io.github.romanvht.byedpi.utility.mode
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * HTML/CSS presentation layer. All four main tabs live inside one WebView and talk to Android
 * through [Bridge]; heavier sub-screens (editors, domain lists, strategy testing) stay native.
 */
class WebUiActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var historyUtils: HistoryUtils
    private val appIconCache = HashMap<String, String>()

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        pushState()
    }

    private val vpnRegister =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == RESULT_OK) {
                ServiceManager.start(this, Mode.VPN)
            } else {
                Toast.makeText(this, R.string.vpn_permission_denied, Toast.LENGTH_SHORT).show()
            }
            pushState()
        }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                STARTED_BROADCAST, STOPPED_BROADCAST, FAILED_BROADCAST -> pushState()
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        historyUtils = HistoryUtils(this)

        val themeName = getPreferences().getStringNotNull("app_theme", "system")
        SettingsUtils.setTheme(themeName)
        AppStyles.overlay(themeName)?.let { theme.applyStyle(it, true) }

        // A transparent WebView forces the whole window into alpha compositing,
        // which makes Chromium's raster/scroll noticeably slower. Use the
        // theme's window background instead.
        val bgValue = android.util.TypedValue()
        theme.resolveAttribute(android.R.attr.colorBackground, bgValue, true)
        val windowBg = if (bgValue.resourceId != 0) {
            androidx.core.content.ContextCompat.getColor(this, bgValue.resourceId)
        } else bgValue.data

        webView = WebView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(windowBg)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.textZoom = 100
            if (BuildConfig.DEBUG) WebView.setWebContentsDebuggingEnabled(true)
            addJavascriptInterface(Bridge(), "Native")
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    pushState()
                    pushTune(TestService.state.value)
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest
                ): Boolean {
                    val target = request.url
                    if (target.scheme == "file") return false
                    runCatching { startActivity(Intent(Intent.ACTION_VIEW, target)) }
                    return true
                }
            }
            loadUrl("file:///android_asset/webui/index.html")
        }

        setContentView(webView)

        lifecycleScope.launch {
            TestService.loadResults(this@WebUiActivity)
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                TestService.state.collect { pushTune(it) }
            }
        }

        val dark = themeName !in LIGHT_UI_THEMES
        WindowCompat.getInsetsController(window, window.decorView)
            .isAppearanceLightStatusBars = !dark
        window.statusBarColor = Color.parseColor(THEME_BG[themeName] ?: "#101114")

        val filter = IntentFilter().apply {
            addAction(STARTED_BROADCAST)
            addAction(STOPPED_BROADCAST)
            addAction(FAILED_BROADCAST)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                webView.evaluateJavascript(
                    "(function(){if(window.UI&&window.UI.back())return '1';return '0';})()"
                ) { result ->
                    if (result != "\"1\"") {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                        isEnabled = true
                    }
                }
            }
        })

        if (!PermissionUtils.hasNotificationPermission(this)) {
            PermissionUtils.requestNotificationPermission(this, 1)
        }

        if (!TestService.isRunning && getPreferences().getBoolean("auto_connect", false) &&
            appStatus.first != AppStatus.Running
        ) {
            startService()
        }
    }

    override fun onResume() {
        super.onResume()
        ServiceManager.refresh(this)
        getPreferences().registerOnSharedPreferenceChangeListener(prefsListener)
        pushState()
    }

    override fun onPause() {
        super.onPause()
        getPreferences().unregisterOnSharedPreferenceChangeListener(prefsListener)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(receiver)
        webView.destroy()
    }

    private var statePushPending = false

    private fun pushState() {
        runOnUiThread {
            if (!::webView.isInitialized) return@runOnUiThread
            val now = android.os.SystemClock.elapsedRealtime()
            // Several broadcasts (status, network, prefs) often arrive at once;
            // coalesce them so the web layer does not rebuild the UI repeatedly.
            if (now - lastStatePush < 120L) {
                if (statePushPending) return@runOnUiThread
                statePushPending = true
                webView.postDelayed({
                    statePushPending = false
                    if (!::webView.isInitialized) return@postDelayed
                    lastStatePush = android.os.SystemClock.elapsedRealtime()
                    val json = JSONObject.quote(buildState().toString())
                    webView.evaluateJavascript("window.UI && UI.refresh($json)", null)
                }, 130L)
                return@runOnUiThread
            }
            lastStatePush = now
            val json = JSONObject.quote(buildState().toString())
            webView.evaluateJavascript("window.UI && UI.refresh($json)", null)
        }
    }

    private fun restartIfRunning() {
        if (appStatus.first == AppStatus.Running) {
            ServiceManager.restart(this, getPreferences().mode())
        }
    }

    private fun startService() {
        if (TestService.isRunning) {
            Toast.makeText(this, R.string.test_unavailable, Toast.LENGTH_SHORT).show()
            return
        }
        if (getPreferences().mode() == Mode.VPN) {
            val prepare = VpnService.prepare(this)
            if (prepare != null) vpnRegister.launch(prepare)
            else ServiceManager.start(this, Mode.VPN)
        } else {
            ServiceManager.start(this, Mode.Proxy)
        }
    }

    private var lastStatePush = 0L
    private val builtinCommands: List<String> by lazy {
        runCatching {
            assets.open("proxytest_strategies.list").bufferedReader().use { it.readText() }
        }.getOrDefault("").lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .toList()
    }
    private var appLabelCache: List<Pair<String, String>>? = null

    private fun buildState(): JSONObject {
        val prefs = getPreferences()
        val (status, runMode) = appStatus
        val running = status == AppStatus.Running
        val mode = prefs.mode()
        val cmd = prefs.getCmdArgs()
        val pinned = historyUtils.getPinnedHistory()
        val builtin = builtinCommands
        val allCommands = ArrayList<Pair<String, String>>()
        pinned.forEach { allCommands.add(it.text to it.name.orEmpty()) }
        builtin.forEach { text ->
            if (allCommands.none { normCmd(it.first) == normCmd(text) }) allCommands.add(text to "")
        }
        // Results for commands outside the candidate list (a merged auto-tune
        // command, for example) still deserve a row in the picker.
        val live = TestService.state.value.strategies.filter { it.totalRequests > 0 }
        live.forEach { result ->
            if (allCommands.none { normCmd(it.first) == normCmd(result.command) }) {
                allCommands.add(0, result.command to "")
            }
        }
        val matched = pinned.firstOrNull { normCmd(it.text) == normCmd(cmd) }
        val resultByCmd = live.associateBy { normCmd(it.command) }
        val bestResult = live.filter { it.isCompleted }.maxByOrNull { it.winningScore() }
        val networkKey = AutoTuneUtils.networkKey(this)
        val networkLabel = when {
            networkKey.startsWith("wifi") -> "Wi-Fi"
            networkKey.startsWith("cell") -> "Mobile"
            networkKey == "ethernet" -> "Ethernet"
            else -> "Сеть"
        }

        return JSONObject().apply {
            put("theme", prefs.getStringNotNull("app_theme", "system"))
            put("tab", prefs.getStringNotNull("webui_tab", "home"))
            put("running", running)
            put("mode", mode.name.lowercase())
            put("runMode", runMode.name.lowercase())
            put("proxy", prefs.getStringNotNull("byedpi_proxy_ip", "127.0.0.1") + ":" +
                prefs.getStringNotNull("byedpi_proxy_port", "1080"))
            put("network", networkLabel)
            put("strategyCmd", cmd)
            put("command", prefs.getCmdArgs())
            put("strategyName", matched?.name.orEmpty())
            put("cmdEnabled", prefs.getBoolean("byedpi_enable_cmd_settings", true))
            put("animations", prefs.getBoolean(ThemeFx.KEY_ANIMATIONS, true))
            put("dnsIp", prefs.getStringNotNull("dns_ip", "1.1.1.1"))
            put("ipv6", prefs.getBoolean("ipv6_enable", false))
            put("autoStrategy", prefs.getBoolean("auto_strategy_enable", false))
            put("autoConnect", prefs.getBoolean("auto_connect", false))
            put("autoStart", prefs.getBoolean("autostart", false))
            put("filterMode", prefs.getStringNotNull("applist_type", "disable"))
            put("selectedCount", prefs.getStringSet("selected_apps", emptySet())?.size ?: 0)
            put("version", BuildConfig.VERSION_NAME)
            put("strategies", JSONArray().apply {
                allCommands.take(48).forEach { pair ->
                    val r = resultByCmd[normCmd(pair.first)]
                    val score = r?.successCount ?: 0
                    val total = r?.totalRequests ?: 0
                    val done = when {
                        r == null -> 0
                        r.isCompleted -> r.totalRequests
                        else -> r.currentProgress
                    }
                    put(JSONObject().apply {
                        put("name", pair.second)
                        put("cmd", pair.first)
                        put("score", score)
                        put("total", total)
                        put("done", done)
                        put("percent", if (done > 0) score * 100 / done else 0)
                        put("tested", r != null)
                        put("completed", r?.isCompleted == true)
                    })
                }
            })
            put("best", JSONObject().apply {
                put("cmd", bestResult?.command.orEmpty())
                put("score", bestResult?.successCount ?: 0)
                put("total", bestResult?.totalRequests ?: 0)
                put("percent", bestResult?.weightedPercentage ?: 0)
            })
        }
    }

    /** Apps are requested separately: their icons make the state payload too heavy. */
    private fun appsJson(): String = JSONArray().apply {
        val selected = getPreferences().getStringSet("selected_apps", emptySet()) ?: emptySet()
        queryApps(selected).forEach { app ->
            put(JSONObject().apply {
                put("label", app.first)
                put("pkg", app.second)
                put("selected", app.third)
                // Icons are decoded lazily by the page so the first paint is instant.
                put("icon", if (app.third) iconData(app.second) else "")
            })
        }
    }.toString()

    /** Small cached data-URI icon so the HTML list looks like a real launcher. */
    private fun iconData(pkg: String): String {
        appIconCache[pkg]?.let { return it }
        val encoded = runCatching {
            val size = 64
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val drawable = packageManager.getApplicationIcon(pkg)
            drawable.setBounds(0, 0, size, size)
            drawable.draw(canvas)
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.WEBP, 70, stream)
            bitmap.recycle()
            "data:image/webp;base64," + Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
        }.getOrDefault("")
        appIconCache[pkg] = encoded
        return encoded
    }

    private fun pushTune(state: TestState) {
        if (!::webView.isInitialized) return
        val payload = buildTune(state).toString()
        webView.post {
            webView.evaluateJavascript(
                "window.UI && UI.tune(${JSONObject.quote(payload)})", null
            )
        }
    }

    private fun buildTune(state: TestState): JSONObject {
        val completed = state.strategies.filter { it.isCompleted && it.totalRequests > 0 }
        val best = completed.maxByOrNull { it.winningScore() }
        return JSONObject().apply {
            put("loaded", state.isLoaded)
            put("running", state.isRunning)
            put("stopping", state.isStopping)
            put("failed", state.failed)
            put("interrupted", state.wasInterrupted)
            put("current", state.currentStrategy)
            put("total", state.strategies.size)
            put("bestCommand", best?.command.orEmpty())
            put("bestScore", best?.successCount ?: 0)
            put("bestTotal", best?.totalRequests ?: 0)
            put("strategies", JSONArray().apply {
                state.strategies.forEach { result ->
                    val done = when {
                        result.isCompleted -> result.totalRequests
                        else -> result.currentProgress
                    }
                    put(JSONObject().apply {
                        put("cmd", result.command)
                        put("success", result.successCount)
                        put("total", result.totalRequests)
                        put("done", done)
                        put("percent", if (done > 0) result.successCount * 100 / done else 0)
                        put("completed", result.isCompleted)
                        put("progress", result.currentProgress)
                    })
                }
            })
        }
    }

    private fun StrategyResult.winningScore(): Int = weightedPercentage * 1000 + successCount

    /** Command strings come from different sources; compare them whitespace-insensitively. */
    private fun normCmd(cmd: String): String = cmd.trim().replace(Regex("\\s+"), " ")

    private fun queryApps(selected: Set<String>): List<Triple<String, String, Boolean>> {
        // PackageManager lookups are expensive on cold cache; the installed app
        // list does not change while the screen is open, so keep it around.
        val cached = appLabelCache ?: run {
            val pm = packageManager
            val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val list = pm.queryIntentActivities(launcherIntent, 0)
                .asSequence()
                .mapNotNull { it.activityInfo?.packageName }
                .distinct()
                .filter { it != packageName }
                .map { pkg ->
                    val label = runCatching {
                        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                    }.getOrDefault(pkg)
                    label to pkg
                }
                .sortedBy { it.first.lowercase() }
                .take(400)
                .toList()
            appLabelCache = list
            list
        }
        return cached.map { Triple(it.first, it.second, selected.contains(it.second)) }
    }

    private inner class Bridge {
        @JavascriptInterface
        fun state(): String = buildState().toString()

        @JavascriptInterface
        fun toggle() {
            runOnUiThread {
                when (appStatus.first) {
                    AppStatus.Running -> ServiceManager.stop()
                    AppStatus.Halted -> startService()
                }
            }
        }

        @JavascriptInterface
        fun setPref(key: String, value: String) {
            runOnUiThread {
                val prefs = getPreferences()
                when (key) {
                    // The page exposes the toggle as "ipv6" while every consumer
                    // of the preference reads the boolean "ipv6_enable".
                    "ipv6" -> {
                        val enabled = value.toBoolean()
                        prefs.edit {
                            putBoolean("ipv6_enable", enabled)
                            remove("ipv6")
                        }
                        restartIfRunning()
                    }
                    "ipv6_enable", "auto_strategy_enable", "auto_connect", "autostart",
                    "byedpi_enable_cmd_settings" ->
                        prefs.edit { putBoolean(key, value.toBoolean()) }
                    "theme_animations" -> prefs.edit { putBoolean(ThemeFx.KEY_ANIMATIONS, value.toBoolean()) }
                    "app_theme" -> {
                        if (value == prefs.getStringNotNull("app_theme", "system")) return@runOnUiThread
                        prefs.edit { putString("app_theme", value) }
                        SettingsUtils.setTheme(value)
                        if (AppCompatDelegate.getDefaultNightMode() ==
                            AppStyles.nightMode(value) ?: AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                        ) {
                            recreate()
                        }
                    }
                    else -> {
                        prefs.edit { putString(key, value) }
                        if (key == "applist_type") restartIfRunning()
                    }
                }
                pushState()
            }
        }

        @JavascriptInterface
        fun setTab(tab: String) {
            getPreferences().edit { putString("webui_tab", tab) }
        }

        @JavascriptInterface
        fun applyStrategy(cmd: String) {
            runOnUiThread {
                getPreferences().edit { putString("byedpi_cmd_args", cmd) }
                if (appStatus.first == AppStatus.Running) {
                    ServiceManager.restart(this@WebUiActivity, getPreferences().mode())
                }
                pushState()
            }
        }

        @JavascriptInterface
        fun setCmd(cmd: String) {
            runOnUiThread {
                getPreferences().edit { putString("byedpi_cmd_args", cmd.trim()) }
                restartIfRunning()
                pushState()
            }
        }

        @JavascriptInterface
        fun clipboard(): String {
            val cm = getSystemService(ClipboardManager::class.java) ?: return ""
            val clip = cm.primaryClip ?: return ""
            if (clip.itemCount == 0) return ""
            return clip.getItemAt(0).coerceToText(this@WebUiActivity).toString()
        }

        @JavascriptInterface
        fun apps(): String = appsJson()

        @JavascriptInterface
        fun icon(pkg: String): String = iconData(pkg)

        @JavascriptInterface
        fun setApps(json: String, mode: String) {
            runOnUiThread {
                val array = JSONArray(json)
                val selected = mutableSetOf<String>()
                for (i in 0 until array.length()) selected.add(array.getString(i))
                val filter = when (mode) {
                    "whitelist" -> "whitelist"
                    "blacklist" -> "blacklist"
                    else -> "disable"
                }
                getPreferences().edit {
                    putStringSet("selected_apps", selected)
                    putString("applist_type", filter)
                }
                restartIfRunning()
                pushState()
            }
        }

        @JavascriptInterface
        fun startTune() {
            runOnUiThread { if (!TestService.isRunning) TestService.start(this@WebUiActivity) }
        }

        @JavascriptInterface
        fun stopTune() {
            runOnUiThread { if (TestService.isRunning) TestService.stop() }
        }

        @JavascriptInterface
        fun open(screen: String) {
            runOnUiThread {
                when (screen) {
                    "cmd" -> startActivity(Intent(this@WebUiActivity, SettingsActivity::class.java)
                        .putExtra("open_fragment", "cmd"))
                    "ui" -> startActivity(Intent(this@WebUiActivity, SettingsActivity::class.java)
                        .putExtra("open_fragment", "ui"))
                    "settings" -> startActivity(Intent(this@WebUiActivity, SettingsActivity::class.java))
                    "domains" -> startActivity(Intent(this@WebUiActivity, TestSettingsActivity::class.java)
                        .putExtra("open_fragment", "domain_lists"))
                    "test" -> startActivity(Intent(this@WebUiActivity, TestActivity::class.java))
                    "appfilter" -> startActivity(Intent(this@WebUiActivity, AppFilterActivity::class.java))
                }
            }
        }

        @JavascriptInterface
        fun openUrl(url: String) {
            runOnUiThread {
                runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            }
        }

        @JavascriptInterface
        fun toast(message: String) {
            runOnUiThread { Toast.makeText(this@WebUiActivity, message, Toast.LENGTH_SHORT).show() }
        }
    }

    private companion object {
        private val LIGHT_UI_THEMES = setOf(
            "light", "minimal", "brutal", "stamp", "metro", "bricks", "doge", "win98"
        )

        private val THEME_BG = mapOf(
            "system" to "#101114", "light" to "#ffffff", "dark" to "#101114",
            "minimal" to "#f7f7f8", "terminal" to "#050807", "bento" to "#101114",
            "aurora" to "#0b0b14", "brutal" to "#f4f4f5", "soft" to "#101725",
            "gauge" to "#2a2117", "dotmatrix" to "#050505", "flow" to "#071c1f",
            "orbit" to "#0d1024", "stamp" to "#f3ecdb", "metro" to "#f2f4f7",
            "tuner" to "#241a10", "bricks" to "#fdf6e3", "pixel" to "#0d1424",
            "doge" to "#ffd93b", "win98" to "#008080", "impact" to "#121212",
            "sigma" to "#0f0e0b"
        )
    }
}
