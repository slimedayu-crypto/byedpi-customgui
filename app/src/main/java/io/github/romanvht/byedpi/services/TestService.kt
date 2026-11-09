package io.github.romanvht.byedpi.services

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.AtomicFile
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.github.romanvht.byedpi.R
import io.github.romanvht.byedpi.activities.TestActivity
import io.github.romanvht.byedpi.data.*
import io.github.romanvht.byedpi.utility.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

class TestService : Service() {

    companion object {
        private const val TAG = "TestService"
        private const val CHANNEL = "Proxy test"
        private const val RESULTS_FILE = "proxy_test_results.json"
        private const val NOTIFICATION_ID = 4
        // Auto tune checks whitelisted apps through every candidate. Bounds keep the run
        // predictable on slow networks while covering the apps the user actually uses.
        private const val MAX_AUTO_APPS = 8
        private const val APP_CHECK_TIMEOUT_MS = 5000
        private const val APP_CHECK_CONCURRENCY = 3
        private const val APP_CHECK_BODY_BYTES = 128 * 1024
        private val mutableState = MutableStateFlow(TestState())
        private val resultsLock = Any()
        val state: StateFlow<TestState> = mutableState.asStateFlow()
        val isRunning: Boolean
            get() = state.value.isRunning

        // Set before the service starts and read by it; autoRun is also read from an IO thread.
        @Volatile
        private var autoRun = false
        @Volatile
        private var resumeMode: Mode? = null

        fun start(context: Context) = begin(context, auto = false, resume = null)

        /**
         * Quick automatic test: tries the most promising strategies, stops at the first good one,
         * applies the best result and brings the bypass back up if it was running.
         */
        fun startAuto(context: Context) {
            if (isRunning) return
            val (status, mode) = appStatus
            begin(context, auto = true, resume = if (status == AppStatus.Running) mode else null)
        }

        private fun begin(context: Context, auto: Boolean, resume: Mode?) {
            if (isRunning) return
            autoRun = auto
            resumeMode = resume
            ServiceManager.refresh(context)
            val previous = state.value
            mutableState.value = previous.copy(
                runId = previous.runId + 1,
                isRunning = true,
                isStopping = false,
                currentStrategy = 0,
                failed = false,
                wasInterrupted = false,
            )
            try {
                ContextCompat.startForegroundService(context, Intent(context, TestService::class.java).setAction(START_ACTION))
            } catch (e: Exception) {
                mutableState.value = previous.copy(failed = true)
                Log.e(TAG, "Failed to start proxy tests", e)
                autoRun = false
                resumeMode?.let { ServiceManager.restart(context, it) }
                resumeMode = null
            }
        }

        fun stop() {
            if (!isRunning) return
            mutableState.value = state.value.copy(isStopping = true)
        }

        suspend fun awaitStopped() {
            state.first { !it.isRunning }
        }

        suspend fun loadResults(context: Context) {
            val previous = state.value
            if (previous.isLoaded || previous.isRunning) return
            val app = context.applicationContext
            val loaded = withContext(Dispatchers.IO) {
                val results = try {
                    synchronized(resultsLock) {
                        AtomicFile(File(app.filesDir, RESULTS_FILE)).openRead().bufferedReader().use { reader ->
                            val type = object : TypeToken<List<StrategyResult>>() {}.type
                            Gson().fromJson<List<StrategyResult>>(reader, type) ?: emptyList()
                        }
                    }
                } catch (_: Exception) {
                    emptyList()
                }
                previous.copy(
                    isLoaded = true,
                    strategies = results,
                    wasInterrupted = app.getPreferences().getBoolean("is_test_running", false),
                )
            }
            withContext(Dispatchers.Main.immediate) {
                if (mutableState.compareAndSet(previous, loaded)) {
                    app.getPreferences().edit { putBoolean("is_test_running", false) }
                }
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val gson = Gson()
    private val engineMutex = Mutex()
    private var engine: NativeEngine? = null
    private var testJob: Job? = null
    private var lastStartId = 0
    private var destroyed = false
    private var notifiedStrategy = -1
    private var notifiedCompleted = -1

    override fun onCreate() {
        super.onCreate()
        registerNotificationChannel(this, CHANNEL, R.string.title_test)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        when (intent?.action) {
            START_ACTION -> {
                try {
                    startForeground()
                    if (!isRunning) {
                        @Suppress("DEPRECATION")
                        stopForeground(true)
                        stopSelfResult(startId)
                    } else if (testJob == null) {
                        startTesting()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to show proxy test notification", e)
                    if (testJob == null) {
                        mutableState.value = state.value.copy(isRunning = false, isStopping = false, failed = true)
                        stopSelfResult(startId)
                    } else {
                        stop()
                    }
                }
            }
            STOP_ACTION -> {
                stop()
                if (testJob == null) stopSelfResult(startId)
            }
            else -> if (testJob == null) stopSelfResult(startId)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        destroyed = true
        if (testJob != null) stop()
        scope.cancel()
        super.onDestroy()
    }

    private fun startTesting() {
        val previous = state.value.copy(isRunning = false, isStopping = false)
        testJob = scope.launch(start = CoroutineStart.LAZY) {
            val testingJob = coroutineContext.job
            var settings: TestSettings? = null
            var currentStrategy: StrategyResult? = null
            var prepared = false
            var failed = false
            var finishedAll = false
            val auto = autoRun
            val strategies = mutableListOf<StrategyResult>()
            val stopWatcher = launch {
                try {
                    state.first { it.isStopping }
                    testingJob.cancel()
                } finally {
                    stopEngine()
                }
            }

            try {
                if (state.value.isStopping) {
                    throw CancellationException("Proxy test stopped")
                }
                val options = withContext(Dispatchers.IO) { loadSettings() }
                settings = options
                if (options.sites.isEmpty()) {
                    Toast.makeText(this@TestService, R.string.test_settings_domain_empty, Toast.LENGTH_LONG).show()
                    return@launch
                }
                strategies.addAll(options.commands.map { StrategyResult(command = it) })
                prepared = true
                getPreferences().edit(commit = true) { putBoolean("is_test_running", true) }
                publish(strategies, 0)
                ServiceManager.waitStop()
                val configurations = withContext(Dispatchers.IO) {
                    options.commands.map { command ->
                        runCatching { testConfiguration(this@TestService, command, options.host, options.port) }
                    }
                }

                for ((index, strategy) in strategies.withIndex()) {
                    ensureActive()
                    currentStrategy = strategy
                    strategy.totalRequests = options.sites.size
                    publish(strategies, index + 1)
                    val configuration = configurations[index].getOrNull()
                    if (configuration == null || !checkStrategy(strategy, strategies, configuration, options)) {
                        resetStrategyResult(strategy, options)
                    }
                    strategy.isCompleted = true
                    publish(strategies)
                    saveResults(strategies)
                    stopEngine()
                    currentStrategy = null
                    delay((options.delaySec * 500L).coerceAtLeast(300L))
                }
                if (auto && !failed) {
                    verifyBest(strategies, options)
                }
                finishedAll = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to run proxy tests", e)
                failed = true
            } finally {
                stopWatcher.cancel()
                withContext(NonCancellable) {
                    mutableState.value = state.value.copy(isStopping = true)
                    settings?.let { options ->
                        currentStrategy?.takeIf { !it.isCompleted }?.let {
                            resetStrategyResult(it, options)
                            it.isCompleted = true
                        }
                    }
                    stopEngine()
                    if (prepared) {
                        try {
                            saveResults(strategies)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to save proxy test results", e)
                            failed = true
                        }
                        getPreferences().edit(commit = true) { putBoolean("is_test_running", false) }
                    }
                    testJob = null
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                    stopSelfResult(lastStartId)
                    mutableState.value = if (prepared) TestState(
                        runId = previous.runId,
                        isLoaded = true,
                        strategies = snapshot(strategies),
                        failed = failed,
                    ) else previous.copy(failed = failed)
                    if (auto) finishAuto(strategies, apply = finishedAll && !failed)
                }
            }
        }
        testJob?.start()
    }

    private suspend fun checkStrategy(
        strategy: StrategyResult,
        strategies: List<StrategyResult>,
        configuration: Configuration,
        settings: TestSettings,
    ): Boolean {
        return try {
            val current = startEngine(configuration)
            supervisorScope {
                val engineExit = async { current.awaitExit() }
                val check = async {
                    // Give the engine time to bind the SOCKS port before the first request;
                    // otherwise the first few sites fail for no reason.
                    delay((settings.delaySec * 500L).coerceAtLeast(1200L))
                    val host = when (configuration.host) {
                        "0.0.0.0" -> "127.0.0.1"
                        "::", "[::]" -> "::1"
                        else -> configuration.host
                    }
                    SiteCheckUtils(host, configuration.port).checkSitesAsync(
                        sites = settings.sites,
                        requestsCount = settings.requestsCount,
                        requestTimeout = settings.requestTimeout,
                        concurrentRequests = settings.requestLimit,
                        fullLog = true,
                        onSiteChecked = { site, successCount, countRequests ->
                            withContext(Dispatchers.Main.immediate) {
                                // One point per reachable site keeps the score comparable
                                // (e.g. "30/32") no matter how many attempts were made.
                                strategy.currentProgress += 1
                                if (successCount > 0) strategy.successCount += 1
                                strategy.siteResults.add(SiteResult(site, successCount, countRequests))
                                publish(strategies)
                            }
                        }
                    )
                    if (settings.appPackages.isNotEmpty()) {
                        checkApps(strategy, strategies, settings, host, configuration.port)
                    }
                    true
                }
                try {
                    select {
                        engineExit.onAwait { false }
                        check.onAwait { it }
                    }
                } finally {
                    engineExit.cancel()
                    check.cancel()
                }
            }
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            if (state.value.isStopping) throw CancellationException("Proxy test stopped")
            Log.e(TAG, "Proxy test engine failed", e)
            Toast.makeText(this, getString(R.string.failed_to_start, Sender.Proxy.name), Toast.LENGTH_SHORT).show()
            false
        }
    }

    /**
     * Deep-checks whitelisted apps through the same candidate engine. App results are
     * weighted higher than sites when the best strategy is picked.
     */
    private suspend fun checkApps(
        strategy: StrategyResult,
        strategies: List<StrategyResult>,
        settings: TestSettings,
        host: String,
        port: Int,
    ) {
        strategy.appMaxPoints = settings.appPackages.size * AppCheckStatus.WORKING.points
        val checker = AppCheckUtils(
            proxyIp = host,
            proxyPort = port,
            timeoutMs = APP_CHECK_TIMEOUT_MS,
            retryFailed = false,
            // Markers live near the top of the page, 128 KB is plenty and saves traffic.
            bodyLimitBytes = APP_CHECK_BODY_BYTES,
        )
        checker.checkApps(settings.appPackages, concurrency = APP_CHECK_CONCURRENCY) { result ->
            val status = when (result.status) {
                AppCheckUtils.Status.WORKING -> AppCheckStatus.WORKING
                AppCheckUtils.Status.PARTIAL -> AppCheckStatus.PARTIAL
                AppCheckUtils.Status.FAILED -> AppCheckStatus.FAILED
            }
            // Resolve the label outside the main thread and share one lookup per app.
            val label = appLabel(result.packageName)
            withContext(Dispatchers.Main.immediate) {
                strategy.appPoints += status.points
                strategy.addAppResult(
                    AppCheckResult(
                        packageName = result.packageName,
                        appName = label,
                        status = status,
                        latencyMs = result.latencyMs,
                        deep = result.deep,
                    )
                )
                publish(strategies)
            }
        }
    }

    private val appLabels = mutableMapOf<String, String>()

    private fun appLabel(packageName: String): String = synchronized(appLabels) {
        appLabels.getOrPut(packageName) {
            try {
                val info = packageManager.getApplicationInfo(packageName, 0)
                packageManager.getApplicationLabel(info).toString()
            } catch (_: Exception) {
                packageName
            }
        }
    }

    private suspend fun startEngine(configuration: Configuration): NativeEngine {
        if (state.value.isStopping) throw CancellationException("Proxy test stopped")
        val current = NativeEngine(applicationContext, Mode.Proxy, foreground = false)
        engine = current
        current.start(configuration)
        return current
    }

    private suspend fun stopEngine() = withContext(NonCancellable) {
        engineMutex.withLock {
            val current = engine ?: return@withLock
            var stopFailed = false
            while (true) {
                try {
                    current.stop()
                    engine = null
                    break
                } catch (e: Exception) {
                    if (!stopFailed) Log.e(TAG, "Failed to stop proxy test engine", e)
                    stopFailed = true
                    delay(500)
                }
            }
        }
    }

    private fun loadSettings(): TestSettings {
        val prefs = getPreferences()
        val userCommands = prefs.getBoolean("byedpi_proxytest_usercommands", false)
        val sniValue = prefs.getStringNotNull("byedpi_proxytest_sni", "google.com")
        val content = if (userCommands) prefs.getStringNotNull("byedpi_proxytest_commands", "")
        else assets.open("proxytest_strategies.list").bufferedReader().use { it.readText() }
        val lines = content.replace("{sni}", "\"${sniValue}\"").lines().map { it.trim() }.filter { it.isNotEmpty() }
        val auto = autoRun
        val commands = if (auto) AutoTuneUtils.candidates(this, lines) else lines
        val host = prefs.getStringNotNull("byedpi_proxy_ip", "127.0.0.1")
        val port = prefs.getIntStringNotNull("byedpi_proxy_port", 1080)
        val delaySec = prefs.getIntStringNotNull("byedpi_proxytest_delay", 1).coerceAtLeast(0)
        val requestsCount = prefs.getIntStringNotNull("byedpi_proxytest_requests", 1).coerceAtLeast(1)
        val requestTimeout = prefs.getLongStringNotNull("byedpi_proxytest_timeout", 5).coerceAtLeast(1)
        val requestLimit = prefs.getIntStringNotNull("byedpi_proxytest_limit", 20).coerceAtLeast(1)
        DomainListUtils.syncLists(this)
        val sites = DomainListUtils.getActiveDomains(this).toList()
        val appPackages = if (auto) whitelistedApps() else emptyList()
        return TestSettings(sites, commands, host, port, delaySec, requestsCount, requestTimeout,
            requestLimit, appPackages)
    }

    /** Apps that ByeDPI processes (whitelist) are checked with every auto candidate. */
    private fun whitelistedApps(): List<String> {
        val prefs = getPreferences()
        if (prefs.getStringNotNull("applist_type", "blacklist") != "whitelist") return emptyList()
        return prefs.getStringSet("selected_apps", emptySet())
            .orEmpty()
            .filter { it != packageName }
            .sorted()
            .take(MAX_AUTO_APPS)
    }

    private fun resetStrategyResult(strategy: StrategyResult, settings: TestSettings) {
        strategy.successCount = 0
        strategy.currentProgress = 0
        strategy.siteResults.clear()
        strategy.siteResults.addAll(settings.sites.map { SiteResult(it, 0, settings.requestsCount) })
        strategy.clearAppResults()
        strategy.appPoints = 0
        strategy.appMaxPoints = 0
    }

    /**
     * Re-runs the best automatic candidate before applying it. A single lucky pass should not
     * silently replace a working bypass; the second pass uses at least two attempts per site.
     */
    private suspend fun verifyBest(strategies: MutableList<StrategyResult>, settings: TestSettings) {
        val best = AutoTuneUtils.pickBest(strategies) ?: return

        val configuration = try {
            testConfiguration(this, best.command, settings.host, settings.port)
        } catch (e: Exception) {
            Log.e(TAG, "Cannot prepare strategy verification", e)
            return
        }

        val verifySettings = settings.copy(requestsCount = maxOf(2, settings.requestsCount))
        val firstScore = best.weightedPercentage
        resetStrategyResult(best, verifySettings)
        best.isCompleted = false
        publish(strategies)
        val verified = checkStrategy(best, strategies, configuration, verifySettings)
        if (!verified) resetStrategyResult(best, settings)
        best.isCompleted = true
        publish(strategies)
        saveResults(strategies)
        stopEngine()
        Log.i(TAG, "Verified best strategy: $firstScore -> ${best.weightedPercentage}")
    }

    private fun finishAuto(strategies: List<StrategyResult>, apply: Boolean) {
        val app = applicationContext
        val resume = resumeMode
        autoRun = false
        resumeMode = null
        val best = if (apply) AutoTuneUtils.pickBest(strategies) else null
        if (best != null) {
            app.getPreferences().edit(commit = true) { putString("byedpi_cmd_args", best.command) }
            HistoryUtils(app).addCommand(best.command)
            AutoTuneUtils.saveProfile(app, AutoTuneUtils.networkKey(app), best.command)
            Toast.makeText(app, R.string.auto_strategy_switched, Toast.LENGTH_LONG).show()
        } else if (apply) {
            Toast.makeText(app, R.string.auto_strategy_not_found, Toast.LENGTH_LONG).show()
        }
        HealthMonitor.onTuneFinished(best != null)
        // Never leave the user without the bypass they had running before the test.
        resume?.let { ServiceManager.restart(app, it) }
    }

    private fun snapshot(strategies: List<StrategyResult>): List<StrategyResult> =
        strategies.map {
            it.copy(
                siteResults = it.siteResults.toMutableList(),
                appResults = it.appList.toMutableList(),
            )
        }

    private suspend fun saveResults(strategies: List<StrategyResult>) {
        val results = snapshot(strategies)
        withContext(Dispatchers.IO) {
            synchronized(resultsLock) {
                val file = AtomicFile(File(filesDir, RESULTS_FILE))
                val output = file.startWrite()
                try {
                    output.write(gson.toJson(results).toByteArray(Charsets.UTF_8))
                    file.finishWrite(output)
                } catch (e: Exception) {
                    file.failWrite(output)
                    throw e
                }
            }
        }
    }

    private fun publish(strategies: List<StrategyResult>, currentStrategy: Int = state.value.currentStrategy) {
        mutableState.value = state.value.copy(isLoaded = true, strategies = snapshot(strategies), currentStrategy = currentStrategy)
        val completed = strategies.count { it.isCompleted }
        if (!destroyed && (notifiedStrategy != currentStrategy || notifiedCompleted != completed)) {
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIFICATION_ID, notification())
            notifiedStrategy = currentStrategy
            notifiedCompleted = completed
        }
    }

    private fun notification(): Notification {
        val value = state.value
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.title_test))
            .setContentText(getString(R.string.test_process, value.currentStrategy, value.strategies.size))
            .setProgress(value.strategies.size, value.strategies.count { it.isCompleted }, value.currentStrategy == 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, TestActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
            .addAction(0, getString(R.string.test_stop), PendingIntent.getService(this, 0,
                Intent(this, TestService::class.java).setAction(STOP_ACTION), PendingIntent.FLAG_IMMUTABLE))
            .build()
    }

    private fun startForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification())
        }
        notifiedStrategy = state.value.currentStrategy
        notifiedCompleted = state.value.strategies.count { it.isCompleted }
    }
}
