package io.github.romanvht.byedpi.utility

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.telephony.TelephonyManager
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.github.romanvht.byedpi.R
import io.github.romanvht.byedpi.data.StrategyResult

/**
 * Helpers for automatic strategy selection: preference keys, per-network profiles
 * and ranking of test results.
 */
object AutoTuneUtils {
    const val KEY_ENABLED = "auto_strategy_enable"
    const val KEY_INTERVAL = "auto_strategy_interval"
    const val KEY_NETWORK_PROFILES = "byedpi_network_profiles"

    const val DEFAULT_INTERVAL_SEC = 60
    const val MIN_INTERVAL_SEC = 15

    /** Strategy counts as "good enough" to stop the auto test early. */
    const val GOOD_ENOUGH_PERCENT = 90

    /** Below this success rate a strategy is not applied automatically. */
    const val MIN_APPLY_PERCENT = 40

    /** When whitelisted apps are checked, at least this many app points must be scored. */
    const val MIN_APP_PERCENT = 50

    /** A verified strategy may lose at most this many points compared to the first pass. */
    const val VERIFY_TOLERANCE_PERCENT = 15

    private const val MAX_CANDIDATES = 16
    private const val MAX_TEST_SITES = 12
    private val gson = Gson()

    fun isEnabled(context: Context): Boolean =
        context.getPreferences().getBoolean(KEY_ENABLED, false)

    fun intervalSec(context: Context): Int =
        context.getPreferences().getIntStringNotNull(KEY_INTERVAL, DEFAULT_INTERVAL_SEC)
            .coerceAtLeast(MIN_INTERVAL_SEC)

    /**
     * Key describing the current physical network (VPN transport is ignored, since our own
     * VPN is usually the active network while the service is running).
     */
    @SuppressLint("MissingPermission", "HardwareIds")
    @Suppress("DEPRECATION")
    fun networkKey(context: Context): String {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val caps = cm.allNetworks.mapNotNull { cm.getNetworkCapabilities(it) }.firstOrNull {
                !it.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
                    it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            } ?: return "none"
            when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                    val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                    val ssid = wifi?.connectionInfo?.ssid?.trim('"')
                        ?.takeIf { it.isNotBlank() && it != "<unknown ssid>" }
                    "wifi:${ssid ?: "any"}"
                }
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                    val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                    "cell:${tm?.networkOperator?.takeIf { it.isNotBlank() } ?: "any"}"
                }
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
                else -> "other"
            }
        } catch (_: Exception) {
            "unknown"
        }
    }

    /** True if there is a physical network with validated internet access. */
    @Suppress("DEPRECATION")
    fun hasInternet(context: Context): Boolean = try {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        cm.allNetworks.mapNotNull { cm.getNetworkCapabilities(it) }.any {
            !it.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
                it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }
    } catch (_: Exception) {
        true
    }

    private fun loadProfiles(context: Context): MutableMap<String, String> {
        val json = context.getPreferences().getString(KEY_NETWORK_PROFILES, null) ?: return mutableMapOf()
        return try {
            gson.fromJson<MutableMap<String, String>>(
                json, object : TypeToken<MutableMap<String, String>>() {}.type
            ) ?: mutableMapOf()
        } catch (_: Exception) {
            mutableMapOf()
        }
    }

    fun getProfile(context: Context, key: String): String? = loadProfiles(context)[key]

    /** Human-readable name of the current network for the main screen. */
    fun networkLabel(context: Context): String {
        val key = networkKey(context)
        return when {
            key.startsWith("wifi:") -> {
                val ssid = key.removePrefix("wifi:")
                if (ssid == "any") context.getString(R.string.auto_network_wifi)
                else context.getString(R.string.auto_network_wifi_named, ssid)
            }
            key.startsWith("cell:") -> {
                val operator = key.removePrefix("cell:")
                if (operator == "any") context.getString(R.string.auto_network_cell)
                else context.getString(R.string.auto_network_cell_named, operator)
            }
            key == "ethernet" -> context.getString(R.string.auto_network_ethernet)
            else -> context.getString(R.string.auto_network_unknown)
        }
    }

    fun profileCount(context: Context): Int = loadProfiles(context).size

    fun clearProfiles(context: Context) {
        context.getPreferences().edit { remove(KEY_NETWORK_PROFILES) }
    }

    fun saveProfile(context: Context, key: String, command: String) {
        if (key == "none" || key == "unknown") return
        val profiles = loadProfiles(context)
        profiles[key] = command
        context.getPreferences().edit { putString(KEY_NETWORK_PROFILES, gson.toJson(profiles)) }
    }

    /** Sites used by the quick auto test: an evenly spread sample so every service is represented. */
    fun sampleSites(sites: List<String>, limit: Int = MAX_TEST_SITES): List<String> {
        if (sites.size <= limit) return sites
        val step = sites.size.toDouble() / limit
        return List(limit) { sites[(it * step).toInt()] }
    }

    /**
     * Ordered candidate list for the auto test: current strategy, strategy remembered for this
     * network, pinned and recent history, then the bundled list.
     */
    fun candidates(context: Context, bundled: List<String>): List<String> {
        val prefs = context.getPreferences()
        val history = HistoryUtils(context)
        return buildList {
            add(prefs.getCmdArgs())
            getProfile(context, networkKey(context))?.let { add(it) }
            history.getPinnedHistory().forEach { add(it.text) }
            history.getHistory().take(5).forEach { add(it.text) }
            addAll(bundled)
        }.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(MAX_CANDIDATES)
    }

    /**
     * Best completed strategy. Sites and whitelisted apps are combined with apps weighted
     * higher, so a strategy that actually works in the apps the user selected wins.
     * Earlier candidate wins ties.
     */
    fun pickBest(results: List<StrategyResult>): StrategyResult? {
        val completed = results.filter { it.isCompleted && it.totalRequests > 0 }
        if (completed.isEmpty()) return null

        // If apps were checked, only compare strategies that were checked against them too.
        val checked = completed.filter { it.appMaxPoints > 0 }
        val pool = if (checked.isNotEmpty()) checked else completed

        return pool.withIndex()
            .maxWithOrNull(
                compareBy(
                    { it.value.weightedPercentage },
                    { it.value.appSuccessPercentage },
                    { -it.index }
                )
            )
            ?.value
            ?.takeIf {
                it.weightedPercentage >= MIN_APPLY_PERCENT &&
                    (it.appMaxPoints == 0 || it.appSuccessPercentage >= MIN_APP_PERCENT)
            }
    }
}
