package io.github.romanvht.byedpi.activities

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import io.github.romanvht.byedpi.R
import io.github.romanvht.byedpi.adapters.StrategyResultAdapter
import io.github.romanvht.byedpi.data.AppCheckStatus
import io.github.romanvht.byedpi.data.AppStatus
import io.github.romanvht.byedpi.data.Mode
import io.github.romanvht.byedpi.data.StrategyResult
import io.github.romanvht.byedpi.data.TestState
import io.github.romanvht.byedpi.services.HealthMonitor
import io.github.romanvht.byedpi.services.ServiceManager
import io.github.romanvht.byedpi.services.TestService
import io.github.romanvht.byedpi.services.appStatus
import io.github.romanvht.byedpi.utility.HistoryUtils
import io.github.romanvht.byedpi.utility.getCmdEnable
import io.github.romanvht.byedpi.utility.getPreferences
import io.github.romanvht.byedpi.utility.mode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TestActivity : BaseActivity() {

    private lateinit var strategiesRecyclerView: RecyclerView
    private lateinit var progressTextView: TextView
    private lateinit var testProgressBar: ProgressBar
    private lateinit var summaryTextView: TextView
    private lateinit var disclaimerTextView: TextView
    private lateinit var startStopButton: MaterialButton
    private lateinit var strategyAdapter: StrategyResultAdapter
    private lateinit var cmdHistoryUtils: HistoryUtils

    private val strategies = mutableListOf<StrategyResult>()
    private var renderedRunId = -1L
    private val isTesting: Boolean get() = TestService.isRunning
    private val prefs by lazy { getPreferences() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ServiceManager.refresh(this)
        setContentView(R.layout.activity_proxy_test)
        setupToolbar()
        cmdHistoryUtils = HistoryUtils(this)
        strategiesRecyclerView = findViewById(R.id.strategiesRecyclerView)
        startStopButton = findViewById(R.id.startStopButton)
        progressTextView = findViewById(R.id.progressTextView)
        testProgressBar = findViewById(R.id.testProgressBar)
        summaryTextView = findViewById(R.id.summaryTextView)
        disclaimerTextView = findViewById(R.id.disclaimerTextView)

        strategyAdapter = StrategyResultAdapter(this,
            onApply = { command ->
                addToHistory(command)
            }
        )

        strategiesRecyclerView.layoutManager = LinearLayoutManager(this)
        strategiesRecyclerView.adapter = strategyAdapter

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                TestService.state.collect { state ->
                    renderState(state)
                    if (!state.isLoaded && !state.isRunning) TestService.loadResults(applicationContext)
                }
            }
        }

        startStopButton.setOnClickListener {
            startStopButton.isClickable = false

            if (isTesting) TestService.stop() else TestService.start(this)

            startStopButton.postDelayed({ startStopButton.isClickable = true }, 1000)
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!isTesting && appStatus.first == AppStatus.Running) {
                    val intent = Intent(this@TestActivity, MainActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    startActivity(intent)
                }
                finish()
            }
        })

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_test, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_copy_log -> {
                copyLog()
                true
            }
            R.id.action_settings -> {
                if (!isTesting) {
                    val intent = Intent(this, TestSettingsActivity::class.java)
                    startActivity(intent)
                } else {
                    Toast.makeText(this, R.string.test_unavailable, Toast.LENGTH_SHORT).show()
                }
                true
            }
            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun renderState(state: TestState) {
        startStopButton.text = getString(if (state.isRunning) R.string.test_stop else R.string.test_start)
        startStopButton.isEnabled = !state.isStopping
        if (state.isRunning) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        progressTextView.text = when {
            state.isRunning && state.currentStrategy > 0 -> getString(R.string.test_process, state.currentStrategy, state.strategies.size)
            state.isRunning -> ""
            state.failed || state.wasInterrupted -> getString(R.string.test_proxy_error)
            state.strategies.isNotEmpty() -> getString(R.string.test_complete)
            else -> ""
        }
        disclaimerTextView.visibility = if (state.wasInterrupted || (!state.isRunning && state.strategies.isEmpty())) View.VISIBLE else View.GONE
        disclaimerTextView.text = getString(if (state.wasInterrupted) R.string.test_crash else R.string.test_disclaimer)

        if (state.isRunning && state.strategies.isNotEmpty()) {
            testProgressBar.visibility = View.VISIBLE
            testProgressBar.max = state.strategies.size
            testProgressBar.progress = state.currentStrategy
        } else {
            testProgressBar.visibility = View.GONE
        }

        val best = state.strategies
            .filter { it.isCompleted && it.totalRequests > 0 }
            .maxByOrNull { it.weightedPercentage }
        summaryTextView.visibility = if (!state.isRunning && best != null) View.VISIBLE else View.GONE
        if (best != null) {
            summaryTextView.text = getString(
                R.string.test_best_short,
                best.successCount,
                best.totalRequests,
                best.weightedPercentage
            ) + if (best.appList.isNotEmpty()) {
                val appsOk = best.appList.count { it.status != AppCheckStatus.FAILED }
                getString(R.string.test_best_apps, appsOk, best.appList.size)
            } else {
                ""
            }
        }

        strategyAdapter.setTestingState(state.isRunning)
        val replace = renderedRunId != state.runId || strategies.map { it.command } != state.strategies.map { it.command }
        val reorder = replace || strategies.indices.any { strategies[it].isCompleted != state.strategies[it].isCompleted }
        if (replace) {
            strategies.clear()
            strategies.addAll(
                state.strategies.map {
                    it.copy(
                        siteResults = it.siteResults.toMutableList(),
                        appResults = it.appList.toMutableList(),
                    )
                }
            )
            renderedRunId = state.runId
        } else {
            strategies.forEachIndexed { index, strategy ->
                val value = state.strategies[index]
                if (strategy.copy(isExpanded = value.isExpanded) != value) {
                    strategy.successCount = value.successCount
                    strategy.totalRequests = value.totalRequests
                    strategy.currentProgress = value.currentProgress
                    strategy.isCompleted = value.isCompleted
                    strategy.siteResults.clear()
                    strategy.siteResults.addAll(value.siteResults)
                    strategy.appPoints = value.appPoints
                    strategy.appMaxPoints = value.appMaxPoints
                    strategy.appResults = value.appList.toMutableList()
                    if (!reorder) strategyAdapter.updateStrategy(strategy)
                }
            }
        }
        if (reorder) strategyAdapter.updateStrategies(strategies)
    }

    private fun addToHistory(command: String) {
        if (isTesting) return

        prefs.edit(commit = true) { putString("byedpi_cmd_args", command) }
        lifecycleScope.launch(Dispatchers.IO) {
            cmdHistoryUtils.addCommand(command)

            val mode = prefs.mode()
            if (mode == Mode.VPN && VpnService.prepare(this@TestActivity) != null) return@launch

            val toastText = withContext(Dispatchers.Main) {
                if (!isTesting && appStatus.first == AppStatus.Running) {
                    ServiceManager.restart(this@TestActivity, mode)
                    R.string.service_restart
                } else {
                    R.string.cmd_history_applied
                }
            }

            withContext(Dispatchers.Main) {
                Toast.makeText(this@TestActivity, toastText, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun copyLog() {
        val completeStrategies = strategies.filter { it.isCompleted }

        if (completeStrategies.isEmpty()) {
            Toast.makeText(this, R.string.toast_copied, Toast.LENGTH_SHORT).show()
            return
        }

        val sb = StringBuilder()

        completeStrategies.forEach { strategy ->
            sb.appendLine("${strategy.command}\n")

            strategy.siteResults.forEach { site ->
                sb.appendLine("${site.site} - ${site.successCount}/${site.totalCount}")
            }

            strategy.appList.forEach { app ->
                sb.appendLine("${app.appName} (${app.packageName}) - ${app.status.name}" +
                    if (app.status == AppCheckStatus.FAILED) "" else " · ${app.latencyMs} ms")
            }

            sb.appendLine("\n${strategy.successCount}/${strategy.totalRequests}")
            sb.appendLine("-------------")
        }

        val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("proxy_test_log", sb.toString())
        clipboard.setPrimaryClip(clip)

        Toast.makeText(this, R.string.toast_copied, Toast.LENGTH_SHORT).show()
    }
}
