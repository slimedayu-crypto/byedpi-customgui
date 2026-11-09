package io.github.romanvht.byedpi.utility

/**
 * Known per-app endpoints that are used for deep checks.
 *
 * A deep check loads a real page/API of the app through the ByeDPI proxy and looks
 * for content markers, so it verifies that menus, feeds and other data actually work
 * instead of checking a generic 204 response.
 */
data class AppEndpoint(
    val url: String,
    val markers: List<String> = emptyList(),
)

object AppCheckProfiles {

    private fun https(host: String, path: String = "/", vararg markers: String) =
        AppEndpoint("https://$host$path", markers.toList())

    /**
     * Endpoints fallback for apps without a known profile. They only prove that the
     * proxy is alive and do not verify app-specific content.
     */
    val genericTargets: List<AppEndpoint> = listOf(
        https("connectivitycheck.gstatic.com", "/generate_204"),
        https("www.gstatic.com", "/generate_204"),
    )

    private val profiles: Map<String, AppEndpoint> = mapOf(
        // Video / music
        "com.google.android.youtube" to https("m.youtube.com", "/", "ytinitialdata"),
        "com.google.android.apps.youtube.music" to https("music.youtube.com", "/", "ytinitialdata"),
        "com.google.android.apps.youtube.kids" to https("www.youtubekids.com", "/", "youtube"),
        "com.google.android.apps.youtube.creator" to https("studio.youtube.com", "/", "youtube"),
        "com.google.android.apps.youtube.vr" to https("m.youtube.com", "/", "ytinitialdata"),
        "com.zhiliaoapp.musically" to https("www.tiktok.com", "/", "tiktok"),
        "com.zhiliaoapp.musically.go" to https("www.tiktok.com", "/", "tiktok"),
        "com.ss.android.ugc.trill" to https("www.tiktok.com", "/", "tiktok"),
        "com.netflix.mediaclient" to https("www.netflix.com", "/", "netflix"),
        "com.amazon.avod.thirdpartyclient" to https("www.primevideo.com", "/", "prime video", "amazon"),
        "com.spotify.music" to https("open.spotify.com", "/", "spotify"),
        "com.soundcloud.android" to https("soundcloud.com", "/", "soundcloud"),
        "ru.yandex.music" to https("music.yandex.ru", "/", "yandex", "яндекс"),

        // Messengers / social
        "com.discord" to https("discord.com", "/api/v10/gateway", "\"url\""),
        "org.telegram.messenger" to https("web.telegram.org", "/", "telegram"),
        "org.telegram.messenger.web" to https("web.telegram.org", "/", "telegram"),
        "org.telegram.plus" to https("web.telegram.org", "/", "telegram"),
        "org.thunderdog.challegram" to https("web.telegram.org", "/", "telegram"),
        "com.whatsapp" to https("web.whatsapp.com", "/", "whatsapp"),
        "com.whatsapp.w4b" to https("web.whatsapp.com", "/", "whatsapp"),
        "com.instagram.android" to https("www.instagram.com", "/", "instagram"),
        "com.facebook.katana" to https("m.facebook.com", "/", "facebook"),
        "com.facebook.lite" to https("m.facebook.com", "/", "facebook"),
        "com.facebook.orca" to https("m.facebook.com", "/", "facebook"),
        "com.vkontakte.android" to https("m.vk.com", "/", "vk.com", "вконтакте"),
        "com.twitter.android" to https("x.com", "/", "twitter"),
        "com.reddit.frontpage" to https("www.reddit.com", "/", "reddit"),
        "com.pinterest" to https("www.pinterest.com", "/", "pinterest"),
        "com.linkedin.android" to https("www.linkedin.com", "/", "linkedin"),
        "com.snapchat.android" to https("www.snapchat.com", "/", "snapchat"),

        // Google
        "com.google.android.gm" to https("mail.google.com", "/", "gmail"),
        "com.google.android.apps.maps" to https("maps.google.com", "/", "maps"),
        "com.google.android.apps.photos" to https("photos.google.com", "/", "photos"),
        "com.google.android.apps.docs" to https("drive.google.com", "/", "drive"),
        "com.google.android.calendar" to https("calendar.google.com", "/", "calendar"),
        "com.google.android.keep" to https("keep.google.com", "/", "keep"),
        "com.google.android.googlequicksearchbox" to https("www.google.com", "/", "google"),
        "com.android.chrome" to https("www.google.com", "/", "google"),
        "com.android.vending" to https("play.google.com", "/", "google play"),

        // Other popular services
        "com.openai.chatgpt" to https("chatgpt.com", "/", "chatgpt"),
        "com.anthropic.claude" to https("claude.ai", "/", "claude"),
        "com.github.android" to https("github.com", "/", "github"),
        "com.dropbox.android" to https("www.dropbox.com", "/", "dropbox"),
        "com.microsoft.teams" to https("teams.microsoft.com", "/", "teams"),
        "com.microsoft.office.outlook" to https("outlook.live.com", "/", "outlook"),
        "ru.yandex.searchplugin" to https("yandex.ru", "/", "yandex", "яндекс"),
        "com.yandex.browser" to https("yandex.ru", "/", "yandex", "яндекс"),
        "com.kinopoisk" to https("www.kinopoisk.ru", "/", "kinopoisk", "кинопоиск"),
        "org.mozilla.firefox" to https("www.mozilla.org", "/", "mozilla"),
    )

    fun endpointFor(packageName: String): AppEndpoint? = profiles[packageName]
}
