package io.github.romanvht.byedpi.activities

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.color.DynamicColors
import io.github.romanvht.byedpi.R
import io.github.romanvht.byedpi.data.AppStatus
import io.github.romanvht.byedpi.services.TestService
import io.github.romanvht.byedpi.services.appStatus
import io.github.romanvht.byedpi.ui.BottomNavBar
import io.github.romanvht.byedpi.utility.AppStyles
import io.github.romanvht.byedpi.utility.SettingsUtils
import io.github.romanvht.byedpi.utility.ThemeFx
import io.github.romanvht.byedpi.utility.getPreferences
import io.github.romanvht.byedpi.utility.getStringNotNull

abstract class BaseActivity : AppCompatActivity() {

    private var fxTheme: String = "system"
    private var navBar: BottomNavBar? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        val prefs = getPreferences()

        val lang = prefs.getStringNotNull("language", "system")
        SettingsUtils.setLang(lang)

        val appTheme = prefs.getStringNotNull("app_theme", "system")
        SettingsUtils.setTheme(appTheme)
        fxTheme = appTheme

        val overlay = AppStyles.overlay(appTheme)
        if (overlay != null) {
            theme.applyStyle(overlay, true)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            DynamicColors.applyToActivityIfAvailable(this)
        }

        super.onCreate(savedInstanceState)
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        ThemeFx.install(this, fxTheme)
    }

    // ---- bottom navigation (Home / Strategies / Apps / Settings) ------------------------

    private fun navTab(): Int = when (this) {
        is MainActivity -> 0
        is TestActivity -> 1
        is AppFilterActivity -> 2
        is SettingsActivity -> 3
        else -> -1
    }

    override fun setContentView(layoutResID: Int) {
        setContentView(layoutInflater.inflate(layoutResID, null))
    }

    override fun setContentView(view: View) {
        super.setContentView(wrapWithNav(view))
    }

    override fun setContentView(view: View, params: ViewGroup.LayoutParams?) {
        super.setContentView(wrapWithNav(view))
    }

    private fun wrapWithNav(content: View): View {
        val tab = navTab()
        if (tab < 0) return content

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(content, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val bar = BottomNavBar(this)
        root.addView(
            bar,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )
        bar.setup(tab, !intent.hasExtra(BottomNavBar.EXTRA_FROM)) { target -> navigate(target) }
        navBar = bar
        return root
    }

    private fun navigate(target: Int) {
        val cur = navTab()
        if (target == cur) return

        if (target == 3) {
            if (TestService.isRunning) {
                Toast.makeText(this, R.string.test_unavailable, Toast.LENGTH_SHORT).show()
                return
            }
            if (appStatus.first == AppStatus.Running) {
                Toast.makeText(this, R.string.settings_unavailable, Toast.LENGTH_SHORT).show()
                return
            }
        }

        val go = {
            val cls = when (target) {
                0 -> MainActivity::class.java
                1 -> TestActivity::class.java
                2 -> AppFilterActivity::class.java
                else -> SettingsActivity::class.java
            }
            val intent = Intent(this, cls).putExtra(BottomNavBar.EXTRA_FROM, cur)
            if (target == 0) {
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            startActivity(intent)
            if (target != 0 && cur != 0) finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        val bar = navBar
        if (bar != null && ThemeFx.animationsEnabled(this)) {
            bar.select(target, true)
            bar.postDelayed(Runnable { go() }, 200L)
        } else {
            go()
        }
    }

    protected fun setupToolbar() {
        val toolbar: MaterialToolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
    }

}
