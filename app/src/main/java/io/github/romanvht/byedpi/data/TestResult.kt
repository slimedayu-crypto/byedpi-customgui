package io.github.romanvht.byedpi.data

enum class AppCheckStatus(val points: Int) {
    /** App content was loaded through the proxy - the app really works. */
    WORKING(2),

    /** The server answered, but app content was not verified. */
    PARTIAL(1),

    /** No valid response. */
    FAILED(0),
}

data class AppCheckResult(
    val packageName: String,
    val appName: String,
    val status: AppCheckStatus,
    val latencyMs: Long,
    val deep: Boolean,
)

data class StrategyResult(
    val command: String,
    var successCount: Int = 0,
    var totalRequests: Int = 0,
    var currentProgress: Int = 0,
    var isCompleted: Boolean = false,
    val siteResults: MutableList<SiteResult> = mutableListOf(),
    var isExpanded: Boolean = false,
    var appPoints: Int = 0,
    var appMaxPoints: Int = 0,
    // Nullable so old saved results (without this field) stay readable.
    var appResults: MutableList<AppCheckResult>? = null,
) {
    val successPercentage: Int
        get() = if (totalRequests > 0) (successCount * 100) / totalRequests else 0

    /** Apps are weighted more than sites: they are what the user actually uses. */
    val appSuccessPercentage: Int
        get() = if (appMaxPoints > 0) (appPoints * 100) / appMaxPoints else 0

    val weightedPercentage: Int
        get() = if (appMaxPoints > 0) {
            (successPercentage * SITE_WEIGHT + appSuccessPercentage * APP_WEIGHT) /
                (SITE_WEIGHT + APP_WEIGHT)
        } else {
            successPercentage
        }

    val appList: List<AppCheckResult>
        get() = appResults ?: emptyList()

    fun addAppResult(result: AppCheckResult) {
        val list = appResults ?: mutableListOf<AppCheckResult>().also { appResults = it }
        list.removeAll { it.packageName == result.packageName }
        list.add(result)
    }

    fun clearAppResults() {
        appResults = null
    }

    companion object {
        const val SITE_WEIGHT = 2
        const val APP_WEIGHT = 3
    }
}

data class SiteResult(
    val site: String,
    val successCount: Int,
    val totalCount: Int
)
