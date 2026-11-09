package io.github.romanvht.byedpi.utility

import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import java.util.Locale

/**
 * Checks how selected apps actually behave through the ByeDPI SOCKS proxy.
 *
 * For known apps a real endpoint is loaded and verified by content markers
 * (deep check). Unknown apps fall back to a basic reachability check.
 */
class AppCheckUtils(
    private val proxyIp: String,
    private val proxyPort: Int,
    private val timeoutMs: Int = 8000,
    private val retryFailed: Boolean = true,
    private val bodyLimitBytes: Int = MAX_BODY_BYTES,
) {
    enum class Status {
        /** Content marker matched - the app's real page/API works. */
        WORKING,

        /** Server answered, but app-specific content was not verified. */
        PARTIAL,

        /** No valid response. */
        FAILED,
    }

    data class Result(
        val packageName: String,
        val status: Status,
        val latencyMs: Long,
        val deep: Boolean,
        val host: String?,
    )

    suspend fun checkApps(
        packageNames: List<String>,
        concurrency: Int = 3,
        onResult: (suspend (Result) -> Unit)? = null,
    ): List<Result> = coroutineScope {
        val semaphore = Semaphore(concurrency)
        packageNames.map { packageName ->
            async(Dispatchers.IO) {
                semaphore.withPermit {
                    val result = checkApp(packageName)
                    onResult?.invoke(result)
                    result
                }
            }
        }.awaitAll()
    }

    private suspend fun checkApp(packageName: String): Result = withContext(Dispatchers.IO) {
        val profile = AppCheckProfiles.endpointFor(packageName)
        val targets = if (profile != null) listOf(profile) else AppCheckProfiles.genericTargets

        var lastResult: Result? = null
        for (target in targets) {
            var result = request(packageName, target)
            if (result.status == Status.FAILED && retryFailed) {
                // Transient timeouts are the main source of false negatives.
                delay(200)
                result = request(packageName, target)
            }
            lastResult = result
            if (result.status != Status.FAILED) return@withContext result
        }

        lastResult ?: Result(packageName, Status.FAILED, 0, false, null)
    }

    private fun request(packageName: String, target: AppEndpoint): Result {
        val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress(proxyIp, proxyPort))
        var connection: HttpURLConnection? = null
        val started = System.currentTimeMillis()

        try {
            val url = URL(target.url)
            connection = url.openConnection(proxy) as HttpURLConnection
            connection.connectTimeout = timeoutMs
            connection.readTimeout = timeoutMs
            connection.instanceFollowRedirects = true
            connection.useCaches = false
            connection.setRequestProperty("Connection", "close")
            connection.setRequestProperty("Accept-Encoding", "identity")
            connection.setRequestProperty("User-Agent", USER_AGENT)

            val responseCode = connection.responseCode
            if (responseCode !in 200..399) {
                return Result(
                    packageName,
                    Status.FAILED,
                    System.currentTimeMillis() - started,
                    false,
                    url.host
                )
            }

            if (target.markers.isEmpty()) {
                // Basic check: the server answered through the proxy.
                return Result(
                    packageName,
                    Status.PARTIAL,
                    System.currentTimeMillis() - started,
                    false,
                    url.host
                )
            }

            val body = readBody(connection, bodyLimitBytes).lowercase(Locale.ROOT)
            val latency = System.currentTimeMillis() - started
            val matched = target.markers.any { marker ->
                body.contains(marker.lowercase(Locale.ROOT))
            }

            return Result(
                packageName,
                if (matched) Status.WORKING else Status.PARTIAL,
                latency,
                matched,
                url.host
            )
        } catch (e: Exception) {
            Log.d(TAG, "Check failed for $packageName (${target.url}): ${e.message}")
            return Result(
                packageName,
                Status.FAILED,
                System.currentTimeMillis() - started,
                false,
                null
            )
        } finally {
            connection?.disconnect()
        }
    }

    private fun readBody(connection: HttpURLConnection, limit: Int): String {
        val stream = connection.inputStream ?: return ""
        stream.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var total = 0

            while (total < limit) {
                val read = input.read(buffer, 0, minOf(buffer.size, limit - total))
                if (read == -1) break
                output.write(buffer, 0, read)
                total += read
            }

            return output.toString("UTF-8")
        }
    }

    companion object {
        private const val TAG = "AppCheck"
        private const val MAX_BODY_BYTES = 512 * 1024

        private val USER_AGENT =
            "Mozilla/5.0 (Linux; Android ${Build.VERSION.RELEASE}) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"
    }
}
