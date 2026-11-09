package io.github.romanvht.byedpi.utility

import androidx.appcompat.app.AppCompatDelegate
import io.github.romanvht.byedpi.R

/** Selectable visual styles stored in the existing "app_theme" preference. */
object AppStyles {
    private class Style(val overlay: Int, val dark: Boolean)

    private val styles = mapOf(
        "minimal" to Style(R.style.ThemeOverlay_ByeDPI_Minimal, true),
        "terminal" to Style(R.style.ThemeOverlay_ByeDPI_Terminal, true),
        "bento" to Style(R.style.ThemeOverlay_ByeDPI_Bento, false),
        "aurora" to Style(R.style.ThemeOverlay_ByeDPI_Aurora, true),
        "brutal" to Style(R.style.ThemeOverlay_ByeDPI_Brutal, false),
        "soft" to Style(R.style.ThemeOverlay_ByeDPI_Soft, false),
        "gauge" to Style(R.style.ThemeOverlay_ByeDPI_Gauge, true),
        "dotmatrix" to Style(R.style.ThemeOverlay_ByeDPI_DotMatrix, true),
        "flow" to Style(R.style.ThemeOverlay_ByeDPI_Flow, true),
        "orbit" to Style(R.style.ThemeOverlay_ByeDPI_Orbit, true),
        "stamp" to Style(R.style.ThemeOverlay_ByeDPI_Stamp, false),
        "metro" to Style(R.style.ThemeOverlay_ByeDPI_Metro, false),
        "tuner" to Style(R.style.ThemeOverlay_ByeDPI_Tuner, true),
        "bricks" to Style(R.style.ThemeOverlay_ByeDPI_Bricks, false),
        "pixel" to Style(R.style.ThemeOverlay_ByeDPI_Pixel, true),
        "doge" to Style(R.style.ThemeOverlay_ByeDPI_Doge, false),
        "win98" to Style(R.style.ThemeOverlay_ByeDPI_Win98, false),
        "impact" to Style(R.style.ThemeOverlay_ByeDPI_Impact, true),
        "sigma" to Style(R.style.ThemeOverlay_ByeDPI_Sigma, true),
    )

    /** Theme overlay for a custom style, or null for system/light/dark. */
    fun overlay(name: String): Int? = styles[name]?.overlay

    /** Night mode a custom style needs, or null if [name] is not a custom style. */
    fun nightMode(name: String): Int? = styles[name]?.let {
        if (it.dark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
    }
}
