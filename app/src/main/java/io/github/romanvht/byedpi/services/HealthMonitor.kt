package io.github.romanvht.byedpi.services

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import androidx.core.content.edit
import io.github.romanvht.byedpi.data.AppStatus
import io.github.romanvht.byedpi.utility.*
import kotlinx.coroutines.*

/**
 * Watches the running bypass and reacts when it stops working:
 *  - periodically probes a few sites through the local proxy;
 *  - after several consecutive failures starts an automatic strategy test (TestService.startAuto);
 *  - on a network change applies the strategy remembered for that network, or probes it right away.
 *
 * All state is touched from the main thread only.
 */
object HealthMonitor {
    private const val TAG = "HealthMonitor"
    private const val STARTUP_DELAY_MS = 15_000L
    private const val NETWORK_SETTLE_MS = 3_000L
    private const val FAILURES_TO_TUNE = 3
    private const val COOLDOWN_AFTER_SUCCESS_MS = 10 * 60_000L
    private const val COOLDOWN_AFTER_FAILURE_MS = 15 * 60_000L
    private const val PROBE_SITES = 8
    private const val PROBE_TIMEOUT_SEC = 6L
    private val FALLBACK_SITES = listOf("www.youtube.com", "discord.com", "web.telegram.org")

    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate + CoroutineExceptionHandler { _, e ->
            Log.e(TAG, "Health monitor failed", e)
        }
    )
    private var loopJob: Job? = null
    private var networkJob: Job? = null
    private var callback: ConnectivityManager.NetworkCallback? = null
    private var lastNetworkKey: String? = null
    private var cooldownUntil = 0L

    /** Called by ServiceManager whenever the service status is published. */
    fun onStatusChanged(context: Context, status: AppStatus) {
        val app = context.applicationContext
        if (status == AppStatus.Running && AutoTuneUtils.isEnabled(app)) start(app) else stop(app)
    }

    /** Re-reads the current status, e.g. after the auto-strategy switch was toggled. */
    fun refresh(context: Context) {
        onStatusChanged(context, appStatus.first)
    }

    /** Called by TestService when an automatic test has finished. */
    fun onTuneFinished(success: Boolean) {
        cooldownUntil = now() + if (success) COOLDOWN_AFTER_SUCCESS_MS else COOLDOWN_AFTER_FAILURE_MS
    }

    private fun now() = android.os.SystemClock.elapsedRealtime()

    private fun active(app: Context): Boolean =
        AutoTuneUtils.isEnabled(app) && app.getPreferences().getCmdEnable() && !TestService.isRunning

    private fun start(app: Context) {
        if (loopJob?.isActive == true) return
        lastNetworkKey = AutoTuneUtils.networkKey(app)
        registerCallback(app)
        loopJob = scope.launch {
            delay(STARTUP_DELAY_MS)
            var failures = 0
            while (isActive) {
                if (active(app)) {
                    when (probe(app)) {
                        true -> {
                            failures = 0
                            rememberWorking(app)
                        }
                        false -> failures++
                        null -> failures = 0 // no network: not the strategy's fault
                    }
                    if (failures >= FAILURES_TO_TUNE && now() >= cooldownUntil) {
                        Log.w(TAG, "Strategy stopped working, starting auto tune")
                        triggerTune(app)
                        return@launch
                    }
                }
                delay(AutoTuneUtils.intervalSec(app) * 1000L)
            }
        }
    }

    private fun stop(app: Context) {
        loopJob?.cancel()
        loopJob = null
        networkJob?.cancel()
        networkJob = null
        unregisterCallback(app)
    }

    private fun triggerTune(app: Context) {
        // Pessimistic until TestService reports the result.
        cooldownUntil = now() + COOLDOWN_AFTER_FAILURE_MS
        TestService.startAuto(app)
    }

    private fun rememberWorking(app: Context) {
        val key = AutoTuneUtils.networkKey(app)
        val current = app.getPreferences().getCmdArgs()
        // Keep the profile in sync when the user selects a working strategy manually.
        if (AutoTuneUtils.getProfile(app, key) != current) {
            AutoTuneUtils.saveProfile(app, key, current)
        }
    }

    /** true = proxy works, false = looks broken, null = no network to judge by. */
    private suspend fun probe(app: Context): Boolean? {
        if (!AutoTuneUtils.hasInternet(app)) return null
        val (host, port) = try {
            val configuration = NativeEngine.configuration(app)
            val probeHost = when (configuration.host) {
                "0.0.0.0" -> "127.0.0.1"
                "::", "[::]" -> "::1"
                else -> configuration.host
            }
            probeHost to configuration.port
        } catch (e: Exception) {
            Log.e(TAG, "Cannot read proxy configuration", e)
            return null
        }
        val sites = withContext(Dispatchers.IO) {
            DomainListUtils.getActiveDomains(app).ifEmpty { FALLBACK_SITES }
        }
        val sample = AutoTuneUtils.sampleSites(sites, PROBE_SITES)
        val results = SiteCheckUtils(host, port).checkSitesAsync(
            sites = sample,
            requestsCount = 1,
            requestTimeout = PROBE_TIMEOUT_SEC,
            concurrentRequests = PROBE_SITES,
            fullLog = false,
        )
        if (results.isEmpty()) return null
        val ok = results.count { it.second > 0 }
        // At least 70% of the sample must work. A dead domain in the sample no longer
        // makes a healthy strategy look broken.
        return ok * 10 >= results.size * 7
    }

    private fun registerCallback(app: Context) {
        if (callback != null) return
        try {
            val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                .build()
            val created = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    scope.launch { onNetworkChanged(app) }
                }
            }
            cm.registerNetworkCallback(request, created)
            callback = created
        } catch (e: Exception) {
            Log.e(TAG, "Cannot register network callback", e)
        }
    }

    private fun unregisterCallback(app: Context) {
        val current = callback ?: return
        callback = null
        try {
            (app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager)
                .unregisterNetworkCallback(current)
        } catch (_: Exception) {
        }
    }

    private fun onNetworkChanged(app: Context) {
        networkJob?.cancel()
        networkJob = scope.launch {
            delay(NETWORK_SETTLE_MS)
            if (!active(app)) return@launch
            val key = AutoTuneUtils.networkKey(app)
            if (key == lastNetworkKey || key == "none" || key == "unknown") return@launch
            lastNetworkKey = key

            val prefs = app.getPreferences()
            val profile = AutoTuneUtils.getProfile(app, key)
            if (profile != null) {
                if (profile != prefs.getCmdArgs()) {
                    Log.i(TAG, "Network changed to $key, applying remembered strategy")
                    prefs.edit(commit = true) { putString("byedpi_cmd_args", profile) }
                    ServiceManager.restart(app, appStatus.second)
                }
                return@launch
            }
            // Unknown network: keep the current strategy if it works, tune if it doesn't.
            when (probe(app)) {
                true -> rememberWorking(app)
                false -> {
                    if (now() >= cooldownUntil) triggerTune(app)
                }
                null -> Unit
            }
        }
    }
}
