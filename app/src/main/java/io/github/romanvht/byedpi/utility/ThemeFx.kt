package io.github.romanvht.byedpi.utility

import android.animation.Animator
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.BounceInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.view.animation.PathInterpolator
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import io.github.romanvht.byedpi.R
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * Per-style motion: entrance of cards/buttons/list rows, touch feedback and an idle
 * animation on the power button. Does nothing for system/light/dark and when the
 * system "animator duration scale" is 0.
 */
object ThemeFx {
    private enum class Enter { FADE, DOTS, FLOW, POP, SLAM, TRAIN, SWING, DROP, STEPS, WOBBLE, WINDOW, ZOOM, SLOW }
    private enum class Idle { NONE, BLINK, FLOAT, ORBIT, SWAY, HOP, WOBBLE, ZOOM, BREATHE }
    private enum class Tap { NONE, SCALE, SQUISH, STAMP, STEP }

    private class Fx(val enter: Enter, val idle: Idle, val tap: Tap, val stagger: Long = 55L)

    private val styles = mapOf(
        "minimal" to Fx(Enter.FADE, Idle.BREATHE, Tap.SCALE),
        "terminal" to Fx(Enter.DOTS, Idle.BLINK, Tap.STEP),
        "bento" to Fx(Enter.POP, Idle.NONE, Tap.SQUISH),
        "aurora" to Fx(Enter.FLOW, Idle.FLOAT, Tap.SCALE),
        "brutal" to Fx(Enter.SLAM, Idle.NONE, Tap.STAMP),
        "soft" to Fx(Enter.FADE, Idle.BREATHE, Tap.SCALE),
        "gauge" to Fx(Enter.SWING, Idle.SWAY, Tap.SCALE),
        "dotmatrix" to Fx(Enter.DOTS, Idle.BLINK, Tap.STEP, 70L),
        "flow" to Fx(Enter.FLOW, Idle.FLOAT, Tap.SCALE, 80L),
        "orbit" to Fx(Enter.POP, Idle.ORBIT, Tap.SCALE),
        "stamp" to Fx(Enter.SLAM, Idle.NONE, Tap.STAMP, 90L),
        "metro" to Fx(Enter.TRAIN, Idle.NONE, Tap.STEP, 60L),
        "tuner" to Fx(Enter.SWING, Idle.SWAY, Tap.SCALE),
        "bricks" to Fx(Enter.DROP, Idle.NONE, Tap.SQUISH, 80L),
        "pixel" to Fx(Enter.STEPS, Idle.HOP, Tap.STEP, 70L),
        "doge" to Fx(Enter.WOBBLE, Idle.WOBBLE, Tap.SQUISH),
        "win98" to Fx(Enter.WINDOW, Idle.NONE, Tap.STEP, 90L),
        "impact" to Fx(Enter.ZOOM, Idle.ZOOM, Tap.STAMP, 50L),
        "sigma" to Fx(Enter.SLOW, Idle.BREATHE, Tap.NONE, 120L),
    )

    private var current: Fx? = null
    private const val INITIAL_ROWS = 14

    private class Steps(private val n: Int) : TimeInterpolator {
        override fun getInterpolation(t: Float): Float =
            if (t >= 1f) 1f else floor((t * n).toDouble()).toFloat() / n
    }

    private fun enabled(ctx: Context): Boolean =
        Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f

    private fun dp(v: View, x: Float) = x * v.resources.displayMetrics.density

    // ------------------------------------------------------------------ install

    fun install(activity: Activity, styleName: String) {
        val fx = styles[styleName]
        current = fx
        if (fx == null || !enabled(activity)) {
            current = null
            return
        }
        val root = activity.findViewById<ViewGroup>(android.R.id.content) ?: return

        val targets = collectTargets(root)
        walk(root) { v -> if (v.isClickable && v !is RecyclerView) bindTap(v, fx) }
        walk(root) { v -> if (v is RecyclerView) animateList(v, fx) }

        // Lists inside fragments (settings screens) appear after install
        (activity as? FragmentActivity)?.supportFragmentManager?.registerFragmentLifecycleCallbacks(
            object : FragmentManager.FragmentLifecycleCallbacks() {
                override fun onFragmentViewCreated(fm: FragmentManager, f: Fragment, v: View, savedInstanceState: Bundle?) {
                    walk(v) { if (it is RecyclerView) animateList(it, fx) }
                }
            }, true
        )
        var hasFragments = false
        walk(root) { if (it is FragmentContainerView) hasFragments = true }

        root.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (root.viewTreeObserver.isAlive) root.viewTreeObserver.removeOnPreDrawListener(this)
                if (targets.isEmpty()) {
                    if (!hasFragments) enter(root, fx, 0)
                } else {
                    targets.forEachIndexed { i, v -> enter(v, fx, i) }
                }
                return true
            }
        })
    }

    private fun walk(v: View, f: (View) -> Unit) {
        f(v)
        if (v is ViewGroup && v !is RecyclerView) {
            for (i in 0 until v.childCount) walk(v.getChildAt(i), f)
        }
    }

    private fun collectTargets(root: View): List<View> {
        val out = ArrayList<View>()
        fun go(v: View) {
            if (v is RecyclerView) return
            if ((v is MaterialCardView || v is MaterialButton) && v.visibility == View.VISIBLE) {
                out.add(v)
                return
            }
            if (v is ViewGroup) for (i in 0 until v.childCount) go(v.getChildAt(i))
        }
        go(root)
        return out
    }

    // ------------------------------------------------------------------ entrance

    private fun reset(v: View) {
        v.alpha = 1f
        v.translationX = 0f
        v.translationY = 0f
        v.scaleX = 1f
        v.scaleY = 1f
        v.rotation = 0f
    }

    private fun enter(v: View, fx: Fx, index: Int) {
        v.animate().cancel()
        v.alpha = 0f
        v.post {
            val d = dp(v, 1f)
            var duration = 380L
            var interp: TimeInterpolator = DecelerateInterpolator(2f)
            v.pivotX = v.width / 2f
            v.pivotY = v.height / 2f
            when (fx.enter) {
                Enter.FADE -> v.translationY = 14 * d
                Enter.DOTS -> {
                    duration = 440L
                    interp = Steps(4)
                }
                Enter.FLOW -> {
                    v.translationX = -48 * d
                    v.translationY = 16 * d
                    duration = 680L
                    interp = PathInterpolator(0.2f, 0.8f, 0.2f, 1f)
                }
                Enter.POP -> {
                    v.scaleX = 0.6f; v.scaleY = 0.6f
                    interp = OvershootInterpolator(1.6f)
                }
                Enter.SLAM -> {
                    v.scaleX = 1.5f; v.scaleY = 1.5f; v.rotation = -4f
                    duration = 240L
                    interp = DecelerateInterpolator(1.6f)
                }
                Enter.TRAIN -> {
                    v.translationX = v.rootView.width.toFloat()
                    duration = 320L
                    interp = DecelerateInterpolator(1.4f)
                }
                Enter.SWING -> {
                    v.pivotX = 0f; v.pivotY = 0f
                    v.rotation = -7f; v.translationY = 18 * d
                    duration = 520L
                    interp = OvershootInterpolator(2.2f)
                }
                Enter.DROP -> {
                    v.translationY = -90 * d
                    duration = 560L
                    interp = BounceInterpolator()
                }
                Enter.STEPS -> {
                    v.translationY = 18 * d
                    duration = 360L
                    interp = Steps(6)
                }
                Enter.WOBBLE -> {
                    v.rotation = -10f; v.scaleX = 0.8f; v.scaleY = 0.8f
                    duration = 520L
                    interp = OvershootInterpolator(3f)
                }
                Enter.WINDOW -> {
                    v.scaleX = 0.92f; v.scaleY = 0.92f
                    duration = 160L
                    interp = Steps(2)
                }
                Enter.ZOOM -> {
                    v.scaleX = 1.45f; v.scaleY = 1.45f
                    duration = 230L
                    interp = DecelerateInterpolator(1.2f)
                }
                Enter.SLOW -> {
                    v.translationY = 10 * d
                    duration = 820L
                }
            }
            v.animate()
                .alpha(1f).translationX(0f).translationY(0f)
                .scaleX(1f).scaleY(1f).rotation(0f)
                .setDuration(duration)
                .setStartDelay(index * fx.stagger)
                .setInterpolator(interp)
                .start()
        }
    }

    private fun animateList(rv: RecyclerView, fx: Fx) {
        if (rv.getTag(R.id.fx_tap) != null) return
        rv.setTag(R.id.fx_tap, true)
        rv.itemAnimator = null
        val seen = HashSet<Int>()
        rv.addOnChildAttachStateChangeListener(object : RecyclerView.OnChildAttachStateChangeListener {
            override fun onChildViewAttachedToWindow(view: View) {
                bindTap(view, fx)
                val pos = rv.getChildAdapterPosition(view)
                if (pos in 0 until INITIAL_ROWS && seen.add(pos)) {
                    enter(view, fx, pos)
                }
            }

            override fun onChildViewDetachedFromWindow(view: View) {
                view.animate().cancel()
                reset(view)
            }
        })
    }

    // ------------------------------------------------------------------ touch

    @SuppressLint("ClickableViewAccessibility")
    private fun bindTap(v: View, fx: Fx) {
        if (fx.tap == Tap.NONE || v.getTag(R.id.fx_tap) != null) return
        v.setTag(R.id.fx_tap, true)
        v.setOnTouchListener { view, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> press(view, fx.tap)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> release(view, fx.tap)
            }
            false
        }
    }

    private fun press(v: View, tap: Tap) {
        val a = v.animate().setStartDelay(0L)
        when (tap) {
            Tap.SCALE -> a.scaleX(0.95f).scaleY(0.95f).setDuration(90L).setInterpolator(DecelerateInterpolator())
            Tap.SQUISH -> a.scaleX(1.05f).scaleY(0.92f).setDuration(90L).setInterpolator(DecelerateInterpolator())
            Tap.STAMP -> a.scaleX(0.9f).scaleY(0.9f).rotation(-2f).setDuration(70L).setInterpolator(DecelerateInterpolator())
            Tap.STEP -> a.scaleX(0.96f).scaleY(0.96f).setDuration(80L).setInterpolator(Steps(2))
            Tap.NONE -> return
        }
        a.start()
    }

    private fun release(v: View, tap: Tap) {
        val a = v.animate().setStartDelay(0L).scaleX(1f).scaleY(1f).rotation(0f)
        if (tap == Tap.STEP) a.setDuration(80L).setInterpolator(Steps(2))
        else a.setDuration(220L).setInterpolator(OvershootInterpolator(2.6f))
        a.start()
    }

    // ------------------------------------------------------------------ bottom bar

    fun animationsEnabled(ctx: Context): Boolean = enabled(ctx)

    /** Motion curve for the sliding pill of the bottom bar, depends on the style. */
    fun navInterpolator(): TimeInterpolator = when (current?.enter) {
        Enter.DOTS, Enter.STEPS, Enter.WINDOW -> Steps(5)
        Enter.DROP, Enter.WOBBLE -> OvershootInterpolator(3f)
        Enter.SLOW -> DecelerateInterpolator(2f)
        Enter.TRAIN -> LinearInterpolator()
        else -> OvershootInterpolator(1.3f)
    }

    fun navDuration(): Long = when (current?.enter) {
        Enter.SLOW -> 600L
        Enter.DOTS, Enter.STEPS, Enter.WINDOW -> 320L
        Enter.TRAIN -> 260L
        else -> 420L
    }

    // ------------------------------------------------------------------ power button

    /** Called by MainActivity whenever the connection state is (re)drawn. */
    fun status(card: View, icon: View, active: Boolean) {
        val fx = current ?: return
        if (!enabled(icon.context)) return

        val last = icon.getTag(R.id.fx_state) as? Boolean
        icon.setTag(R.id.fx_state, active)
        (icon.getTag(R.id.fx_idle) as? Animator)?.cancel()
        icon.setTag(R.id.fx_idle, null)
        icon.alpha = 1f
        icon.translationX = 0f
        icon.translationY = 0f
        icon.rotation = 0f
        icon.scaleX = 1f
        icon.scaleY = 1f

        if (last != null && last != active) burst(card, fx, active)
        if (active) startIdle(icon, fx.idle)
    }

    private fun burst(card: View, fx: Fx, active: Boolean) {
        card.animate().cancel()
        val d = dp(card, 1f)
        when {
            fx.enter == Enter.WINDOW && !active -> {
                ObjectAnimator.ofFloat(card, View.TRANSLATION_X, 0f, -12 * d, 12 * d, -8 * d, 8 * d, 0f)
                    .setDuration(360L).start()
            }
            fx.enter == Enter.SLAM || fx.enter == Enter.ZOOM -> {
                card.scaleX = 1.4f; card.scaleY = 1.4f; card.rotation = if (active) -6f else 6f
                card.animate().scaleX(1f).scaleY(1f).rotation(0f)
                    .setDuration(300L).setInterpolator(OvershootInterpolator(1.4f)).start()
            }
            else -> {
                ObjectAnimator.ofPropertyValuesHolder(
                    card,
                    PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.14f, 1f),
                    PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.14f, 1f),
                ).setDuration(340L).apply { interpolator = OvershootInterpolator(2f) }.start()
            }
        }
    }

    private fun startIdle(icon: View, idle: Idle) {
        val d = dp(icon, 1f)
        val anim: Animator = when (idle) {
            Idle.NONE -> return
            Idle.BLINK -> ObjectAnimator.ofFloat(icon, View.ALPHA, 1f, 0.25f).apply {
                duration = 900L; interpolator = Steps(2)
            }
            Idle.FLOAT -> ObjectAnimator.ofFloat(icon, View.TRANSLATION_Y, -5 * d, 5 * d).apply {
                duration = 2200L; interpolator = AccelerateDecelerateInterpolator()
            }
            Idle.SWAY -> ObjectAnimator.ofFloat(icon, View.ROTATION, -10f, 10f).apply {
                duration = 900L; interpolator = AccelerateDecelerateInterpolator()
            }
            Idle.HOP -> ObjectAnimator.ofFloat(icon, View.TRANSLATION_Y, 0f, -6 * d).apply {
                duration = 500L; interpolator = Steps(2)
            }
            Idle.WOBBLE -> ObjectAnimator.ofPropertyValuesHolder(
                icon,
                PropertyValuesHolder.ofFloat(View.ROTATION, -7f, 7f),
                PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.1f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.1f),
            ).apply { duration = 450L; interpolator = AccelerateDecelerateInterpolator() }
            Idle.ZOOM -> ObjectAnimator.ofPropertyValuesHolder(
                icon,
                PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.12f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.12f),
            ).apply { duration = 700L; interpolator = AccelerateDecelerateInterpolator() }
            Idle.BREATHE -> ObjectAnimator.ofPropertyValuesHolder(
                icon,
                PropertyValuesHolder.ofFloat(View.ALPHA, 0.7f, 1f),
                PropertyValuesHolder.ofFloat(View.SCALE_X, 0.96f, 1.04f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.96f, 1.04f),
            ).apply { duration = 2600L; interpolator = AccelerateDecelerateInterpolator() }
            Idle.ORBIT -> ValueAnimator.ofFloat(0f, (2 * Math.PI).toFloat()).apply {
                duration = 3600L
                interpolator = LinearInterpolator()
                addUpdateListener {
                    val a = it.animatedValue as Float
                    icon.translationX = 4 * d * cos(a)
                    icon.translationY = 4 * d * sin(a)
                }
            }
        }
        if (anim is ValueAnimator) {
            anim.repeatCount = ValueAnimator.INFINITE
            if (idle != Idle.ORBIT) anim.repeatMode = ValueAnimator.REVERSE
        }
        icon.setTag(R.id.fx_idle, anim)
        icon.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {}
            override fun onViewDetachedFromWindow(v: View) {
                anim.cancel()
                v.removeOnAttachStateChangeListener(this)
            }
        })
        anim.start()
    }
}
