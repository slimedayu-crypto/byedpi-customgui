package io.github.romanvht.byedpi.ui

import android.animation.ArgbEvaluator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.widget.TextViewCompat
import com.google.android.material.color.MaterialColors
import io.github.romanvht.byedpi.R
import io.github.romanvht.byedpi.utility.ThemeFx
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Bottom navigation: Home / Strategies / Apps / Settings.
 * One pill slides (with a liquid stretch) behind the selected icon; colours, radius and
 * stroke follow the active app style.
 */
class BottomNavBar(context: Context) : FrameLayout(context) {

    companion object {
        const val EXTRA_FROM = "nav_from_tab"
    }

    private class Item(val root: LinearLayout, val icon: ImageView, val label: TextView) {
        var iconColor = 0
        var labelColor = 0
    }

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    private val pillW = dp(64f).toInt()
    private val pillH = dp(32f).toInt()
    private val sidePad = dp(8f)
    private val topPad = dp(12f)

    private val items = ArrayList<Item>()
    private val pill = View(context)
    private val row = LinearLayout(context)

    private var selected = -1
    private var initial = 0
    private var entrance = false
    private var ready = false
    private var onSelect: ((Int) -> Unit)? = null

    private val colOnPrimary = MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnPrimary, 0xFFFFFFFF.toInt())
    private val colInactive = MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurfaceVariant, 0xFF888888.toInt())
    private val colLabelActive = MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurface, 0xFFFFFFFF.toInt())

    private fun dimAttr(attr: Int): Float {
        val tv = TypedValue()
        return if (context.theme.resolveAttribute(attr, tv, true)) tv.getDimension(resources.displayMetrics) else 0f
    }

    init {
        val surface = MaterialColors.getColor(context, com.google.android.material.R.attr.colorSurfaceContainer, 0xFF1A1A22.toInt())
        val primary = MaterialColors.getColor(context, androidx.appcompat.R.attr.colorPrimary, 0xFF4F5BD5.toInt())
        val strokeColor = MaterialColors.getColor(context, R.attr.appCardStrokeColor, 0)
        val strokeWidth = dimAttr(R.attr.appCardStrokeWidth).toInt()
        val r = min(dimAttr(R.attr.appTileRadius), dp(28f))

        background = GradientDrawable().apply {
            setColor(surface)
            cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
            if (strokeWidth > 0) setStroke(strokeWidth, strokeColor)
        }

        pill.background = GradientDrawable().apply {
            setColor(primary)
            cornerRadius = min(dimAttr(R.attr.appButtonRadius), pillH / 2f)
        }
        addView(pill, LayoutParams(pillW, pillH, Gravity.TOP or Gravity.START))
        pill.translationY = topPad

        row.orientation = LinearLayout.HORIZONTAL
        row.setPadding(sidePad.toInt(), topPad.toInt(), sidePad.toInt(), dp(12f).toInt())
        addView(row, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        addItem(R.drawable.ic_nav_home, R.string.nav_home, 0)
        addItem(R.drawable.ic_nav_strategies, R.string.nav_strategies, 1)
        addItem(R.drawable.ic_nav_apps, R.string.nav_apps, 2)
        addItem(R.drawable.ic_nav_settings, R.string.nav_settings, 3)
    }

    private fun addItem(iconRes: Int, labelRes: Int, index: Int) {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            isClickable = true
            isFocusable = true
            contentDescription = context.getString(labelRes)
        }
        val box = FrameLayout(context)
        root.addView(box, LinearLayout.LayoutParams(pillW, pillH))

        val icon = ImageView(context).apply { setImageResource(iconRes) }
        box.addView(icon, LayoutParams(dp(24f).toInt(), dp(24f).toInt(), Gravity.CENTER))

        val label = TextView(context).apply {
            setText(labelRes)
            textSize = 12f
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        root.addView(label, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = dp(4f).toInt()
            leftMargin = dp(2f).toInt()
            rightMargin = dp(2f).toInt()
        })
        TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
            label, 8, 12, 1, TypedValue.COMPLEX_UNIT_SP
        )

        row.addView(root, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.setOnClickListener { onSelect?.invoke(index) }

        items.add(Item(root, icon, label).also {
            it.iconColor = colInactive
            it.labelColor = colInactive
            icon.imageTintList = ColorStateList.valueOf(colInactive)
            label.setTextColor(colInactive)
        })
    }

    /** [tab] is the screen shown now; [entrance] slides the bar in from the bottom. */
    fun setup(tab: Int, entrance: Boolean, onSelect: (Int) -> Unit) {
        this.initial = tab
        this.entrance = entrance
        this.onSelect = onSelect
        if (ready) start()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && !ready) {
            ready = true
            start()
        } else if (selected >= 0) {
            pill.translationX = pillX(selected)
        }
    }

    private fun start() {
        selected = initial
        pill.translationX = pillX(initial)
        paint(initial, false)
        if (entrance && ThemeFx.animationsEnabled(context)) {
            translationY = max(height, dp(80f).toInt()).toFloat()
            animate().translationY(0f).setStartDelay(120L).setDuration(460L)
                .setInterpolator(DecelerateInterpolator(2f)).start()
        }
    }

    private fun pillX(i: Int): Float {
        val inner = width - 2 * sidePad
        return sidePad + (i + 0.5f) * inner / items.size - pillW / 2f
    }

    /** Move the pill to [i]; with [animate] it stretches and the icon bounces. */
    fun select(i: Int, animate: Boolean) {
        if (i == selected || i !in items.indices) return
        selected = i
        val tx = pillX(i)
        pill.animate().cancel()
        val animated = animate && ThemeFx.animationsEnabled(context)
        if (!animated) {
            pill.translationX = tx
            pill.scaleX = 1f
            paint(i, false)
            return
        }
        val from = pill.translationX
        val dur = ThemeFx.navDuration()
        ObjectAnimator.ofFloat(pill, View.TRANSLATION_X, from, tx).apply {
            duration = dur
            interpolator = ThemeFx.navInterpolator()
        }.start()
        val stretch = 1f + min(abs(tx - from) / max(width, 1) * 1.6f, 0.6f)
        ObjectAnimator.ofFloat(pill, View.SCALE_X, 1f, stretch, 1f).apply {
            duration = dur
            interpolator = DecelerateInterpolator()
        }.start()
        paint(i, true)
        val ic = items[i].icon
        ic.scaleX = 0.6f
        ic.scaleY = 0.6f
        ic.translationY = -dp(6f)
        ic.animate().scaleX(1f).scaleY(1f).translationY(0f).setDuration(460L)
            .setInterpolator(OvershootInterpolator(2.2f)).start()
    }

    private fun paint(sel: Int, animate: Boolean) {
        items.forEachIndexed { idx, it ->
            val active = idx == sel
            val toIcon = if (active) colOnPrimary else colInactive
            val toLabel = if (active) colLabelActive else colInactive
            it.label.setTypeface(null, if (active) Typeface.BOLD else Typeface.NORMAL)
            if (!animate) {
                it.iconColor = toIcon
                it.labelColor = toLabel
                it.icon.imageTintList = ColorStateList.valueOf(toIcon)
                it.label.setTextColor(toLabel)
            } else {
                val fi = it.iconColor
                val fl = it.labelColor
                it.iconColor = toIcon
                it.labelColor = toLabel
                ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = 260L
                    addUpdateListener { a ->
                        val f = a.animatedFraction
                        it.icon.imageTintList = ColorStateList.valueOf(ArgbEvaluator().evaluate(f, fi, toIcon) as Int)
                        it.label.setTextColor(ArgbEvaluator().evaluate(f, fl, toLabel) as Int)
                    }
                }.start()
            }
        }
    }
}
