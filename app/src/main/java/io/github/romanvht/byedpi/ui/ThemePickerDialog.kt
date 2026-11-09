package io.github.romanvht.byedpi.ui

import android.graphics.drawable.GradientDrawable
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.widget.GridLayout
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import io.github.romanvht.byedpi.R
import io.github.romanvht.byedpi.utility.AppStyles
import io.github.romanvht.byedpi.utility.SettingsUtils
import io.github.romanvht.byedpi.utility.ThemeFx
import io.github.romanvht.byedpi.utility.getPreferences
import io.github.romanvht.byedpi.utility.getStringNotNull

/** Style picker: system/light/dark plus every custom visual style with a live colour preview. */
class ThemePickerDialog(
    private val fragment: Fragment,
    private val current: String,
    private val onApplied: (String) -> Unit
) {
    private val context = fragment.requireContext()
    private val prefs = context.getPreferences()
    private var pending = current
    private var updatingTabs = false

    private val values = context.resources.getStringArray(R.array.themes_entries)
    private val names = context.resources.getStringArray(R.array.themes_short)
    private val styleValues = values.filter { AppStyles.overlay(it) != null }

    fun show() {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_theme_picker, null)
        val grid = view.findViewById<GridLayout>(R.id.styles_grid)
        val tabs = view.findViewById<MaterialButtonToggleGroup>(R.id.theme_tabs)
        val apply = view.findViewById<MaterialButton>(R.id.apply_theme)
        val animations = view.findViewById<MaterialSwitch>(R.id.animations_switch)

        view.findViewById<TextView>(R.id.styles_label).text =
            context.getString(R.string.theme_all_styles, styleValues.size)

        animations.isChecked = prefs.getBoolean(ThemeFx.KEY_ANIMATIONS, true)
        animations.setOnCheckedChangeListener { _, checked ->
            prefs.edit { putBoolean(ThemeFx.KEY_ANIMATIONS, checked) }
        }

        val dialog = MaterialAlertDialogBuilder(context).setView(view).create()
        val cards = mutableListOf<Pair<MaterialCardView, String>>()

        for (value in styleValues) {
            val item = LayoutInflater.from(context)
                .inflate(R.layout.item_style_preview, grid, false) as MaterialCardView
            item.findViewById<TextView>(R.id.style_name).text = nameOf(value)

            val preview = item.findViewById<View>(R.id.style_preview)
            val dot = item.findViewById<View>(R.id.style_dot)
            val (background, accent) = previewColors(value)
            preview.background = GradientDrawable().apply {
                cornerRadius = dp(8f)
                setColor(background)
            }
            dot.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(accent)
            }

            item.setOnClickListener {
                pending = value
                selectTab(tabs, R.id.tab_styles)
                refresh(cards, apply, tabs)
            }

            cards += item to value
            grid.addView(item)
        }

        tabs.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked || updatingTabs) return@addOnButtonCheckedListener
            when (checkedId) {
                R.id.tab_styles -> if (AppStyles.overlay(pending) == null) {
                    pending = styleValues.firstOrNull() ?: pending
                }
                R.id.tab_system -> pending = "system"
                R.id.tab_light -> pending = "light"
                R.id.tab_dark -> pending = "dark"
            }
            refresh(cards, apply, tabs)
        }

        val initialTab = when {
            AppStyles.overlay(current) != null -> R.id.tab_styles
            current == "light" -> R.id.tab_light
            current == "dark" -> R.id.tab_dark
            else -> R.id.tab_system
        }
        selectTab(tabs, initialTab)
        refresh(cards, apply, tabs)

        apply.setOnClickListener {
            val value = pending
            dialog.dismiss()
            if (value == current) return@setOnClickListener
            prefs.edit { putString("app_theme", value) }
            SettingsUtils.setTheme(value)
            onApplied(value)
        }

        dialog.setOnShowListener {
            val dm = context.resources.displayMetrics
            dialog.window?.setLayout(
                (dm.widthPixels - dp(24f)).toInt(),
                (dm.heightPixels * 0.9f).toInt()
            )
        }
        dialog.show()
    }

    private fun selectTab(tabs: MaterialButtonToggleGroup, id: Int) {
        updatingTabs = true
        tabs.check(id)
        updatingTabs = false
    }

    private fun refresh(
        cards: List<Pair<MaterialCardView, String>>,
        apply: MaterialButton,
        tabs: MaterialButtonToggleGroup
    ) {
        val accent = MaterialColors.getColor(apply, androidx.appcompat.R.attr.colorPrimary)
        val outline = MaterialColors.getColor(apply, com.google.android.material.R.attr.colorOutlineVariant)
        cards.forEach { (card, value) ->
            val selected = value == pending
            card.strokeColor = if (selected) accent else outline
            card.strokeWidth = dp(if (selected) 2f else 1f).toInt()
        }

        val onAccent = MaterialColors.getColor(apply, com.google.android.material.R.attr.colorOnPrimary)
        val inactiveBg = MaterialColors.getColor(
            apply,
            com.google.android.material.R.attr.colorSurfaceContainerHigh
        )
        val inactiveText = MaterialColors.getColor(
            apply,
            com.google.android.material.R.attr.colorOnSurfaceVariant
        )
        val checkedId = tabs.checkedButtonId
        for (index in 0 until tabs.childCount) {
            val button = tabs.getChildAt(index) as? MaterialButton ?: continue
            val active = button.id == checkedId
            button.backgroundTintList =
                ColorStateList.valueOf(if (active) accent else inactiveBg)
            button.setTextColor(if (active) onAccent else inactiveText)
            button.strokeWidth = 0
        }

        apply.text = context.getString(R.string.theme_apply, nameOf(pending))
    }

    private fun nameOf(value: String): String {
        val index = values.indexOf(value)
        return if (index >= 0) names[index] else value
    }

    private fun previewColors(value: String): Pair<Int, Int> {
        val overlay = AppStyles.overlay(value)
        if (overlay == null) {
            return when (value) {
                "light" -> 0xFFF3F1F8.toInt() to 0xFF4F5BD5.toInt()
                "dark" -> 0xFF121216.toInt() to 0xFF8B93FF.toInt()
                else -> 0xFF17171D.toInt() to 0xFF9AA0FF.toInt()
            }
        }

        val themed = ContextThemeWrapper(context, R.style.Theme_ByeDPI)
        themed.theme.applyStyle(overlay, true)
        val attrs = themed.obtainStyledAttributes(
            intArrayOf(
                android.R.attr.colorBackground,
                androidx.appcompat.R.attr.colorPrimary,
                com.google.android.material.R.attr.colorSurfaceContainer
            )
        )
        val background = attrs.getColor(2, attrs.getColor(0, 0xFF101014.toInt()))
        val accent = attrs.getColor(1, 0xFFFF2D2D.toInt())
        attrs.recycle()
        return background to accent
    }

    private fun dp(value: Float): Float =
        value * context.resources.displayMetrics.density

    companion object {
        fun currentTheme(context: android.content.Context): String =
            context.getPreferences().getStringNotNull("app_theme", "system")
    }
}
