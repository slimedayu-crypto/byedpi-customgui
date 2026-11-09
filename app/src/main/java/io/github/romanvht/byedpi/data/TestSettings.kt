package io.github.romanvht.byedpi.data

data class TestSettings(
    val sites: List<String>,
    val commands: List<String>,
    val host: String,
    val port: Int,
    val delaySec: Int,
    val requestsCount: Int,
    val requestTimeout: Long,
    val requestLimit: Int,
    /** Whitelisted apps that must be checked through each candidate strategy. */
    val appPackages: List<String> = emptyList(),
)
