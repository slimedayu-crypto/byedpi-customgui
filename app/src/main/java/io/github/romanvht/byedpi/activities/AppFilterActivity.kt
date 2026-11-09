package io.github.romanvht.byedpi.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.content.edit
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textview.MaterialTextView
import io.github.romanvht.byedpi.R
import io.github.romanvht.byedpi.adapters.AppFilterAdapter
import io.github.romanvht.byedpi.data.AppInfo
import io.github.romanvht.byedpi.data.AppStatus
import io.github.romanvht.byedpi.data.Mode
import io.github.romanvht.byedpi.services.ServiceManager
import io.github.romanvht.byedpi.services.appStatus
import io.github.romanvht.byedpi.utility.AppCheckUtils
import io.github.romanvht.byedpi.utility.getPreferences
import io.github.romanvht.byedpi.utility.getStringNotNull
import io.github.romanvht.byedpi.utility.getProxyIpAndPort
import io.github.romanvht.byedpi.utility.mode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppFilterActivity : BaseActivity() {

    private lateinit var modeGroup: MaterialButtonToggleGroup
    private lateinit var modeDescription: MaterialTextView
    private lateinit var selectedCount: MaterialTextView
    private lateinit var whitelistWarning: MaterialTextView
    private lateinit var checkSummary: MaterialTextView
    private lateinit var searchInput: TextInputEditText
    private lateinit var checkButton: MaterialButton
    private lateinit var applyButton: MaterialButton
    private lateinit var checkProgress: LinearProgressIndicator
    private lateinit var emptyView: MaterialTextView
    private lateinit var recyclerView: RecyclerView

    private var adapter: AppFilterAdapter? = null
    private var checkJob: Job? = null
    private var checking = false
    private var updatingMode = false
    private var resultsLoaded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ServiceManager.refresh(this)
        setContentView(R.layout.activity_app_filter)
        setupToolbar()

        supportActionBar?.title = getString(R.string.app_filter_title)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        modeGroup = findViewById(R.id.mode_group)
        modeDescription = findViewById(R.id.mode_description)
        selectedCount = findViewById(R.id.selected_count)
        whitelistWarning = findViewById(R.id.whitelist_warning)
        checkSummary = findViewById(R.id.check_summary)
        searchInput = findViewById(R.id.search_input)
        checkButton = findViewById(R.id.check_button)
        applyButton = findViewById(R.id.apply_button)
        checkProgress = findViewById(R.id.check_progress)
        emptyView = findViewById(R.id.empty_view)
        recyclerView = findViewById(R.id.apps_recycler_view)

        recyclerView.layoutManager = LinearLayoutManager(this)

        setupModeSelector()
        setupSearch()
        setupButtons()
        loadApps()
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onDestroy() {
        checkJob?.cancel()
        super.onDestroy()
    }

    private fun setupModeSelector() {
        val type = getPreferences().getStringNotNull("applist_type", "blacklist")
        updatingMode = true
        modeGroup.check(
            if (type == "whitelist") R.id.mode_applies else R.id.mode_not_applies
        )
        updatingMode = false

        modeGroup.addOnButtonCheckedListener { _, _, isChecked ->
            if (!isChecked || updatingMode) return@addOnButtonCheckedListener
            resetCheckResults()
            updateModeUi()
        }

        updateModeUi()
    }

    private fun setupSearch() {
        searchInput.doAfterTextChanged { text ->
            adapter?.filter?.filter(text?.toString().orEmpty())
        }
    }

    private fun setupButtons() {
        checkButton.setOnClickListener { startCheck() }

        applyButton.setOnClickListener {
            applyFilter()
        }
    }

    private fun loadApps() {
        val prefs = getPreferences()
        val selected = prefs.getStringSet("selected_apps", emptySet()) ?: emptySet()

        lifecycleScope.launch {
            val apps = withContext(Dispatchers.IO) {
                loadInstalledApps(selected)
            }

            adapter = AppFilterAdapter(
                context = this@AppFilterActivity,
                allApps = apps,
                onSelectionChanged = {
                    resetCheckResults()
                    updateModeUi()
                },
                onFilteredCountChanged = { count ->
                    emptyView.visibility = if (count == 0) View.VISIBLE else View.GONE
                }
            )

            recyclerView.adapter = adapter
            emptyView.visibility = if (apps.isEmpty()) View.VISIBLE else View.GONE
            updateModeUi()
        }
    }

    private fun loadInstalledApps(selected: Set<String>): List<AppInfo> {
        val pm = packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val launcherPackages = pm.queryIntentActivities(launcherIntent, 0)
            .mapNotNull { it.activityInfo?.packageName }
            .toSet()

        return pm.getInstalledApplications(0)
            .filter { it.packageName != packageName && it.packageName in launcherPackages }
            .map { appInfo ->
                val label = try {
                    pm.getApplicationLabel(appInfo).toString()
                } catch (_: Exception) {
                    appInfo.packageName
                }

                AppInfo(
                    appName = label,
                    packageName = appInfo.packageName,
                    isSelected = selected.contains(appInfo.packageName)
                )
            }
            .sortedBy { it.appName.lowercase() }
    }

    private fun currentMode(): String = when (modeGroup.checkedButtonId) {
        R.id.mode_applies -> "whitelist"
        else -> "blacklist"
    }

    private fun updateModeUi() {
        val mode = currentMode()
        val count = adapter?.selectedPackages()?.size ?: 0

        modeDescription.setText(
            if (mode == "whitelist") {
                R.string.app_filter_desc_whitelist
            } else {
                R.string.app_filter_desc_blacklist
            }
        )

        selectedCount.text = getString(R.string.app_filter_selected_count, count)
        whitelistWarning.visibility =
            if (mode == "whitelist" && count == 0) View.VISIBLE else View.GONE

        checkButton.visibility = if (mode == "whitelist") View.VISIBLE else View.GONE
        checkButton.isEnabled = count > 0 && !checking
        if (mode != "whitelist") {
            checkSummary.visibility = View.GONE
        } else if (resultsLoaded) {
            checkSummary.visibility = View.VISIBLE
        }
    }

    private fun startCheck() {
        if (checking) return

        val selected = adapter?.selectedPackages().orEmpty()
        if (selected.isEmpty()) {
            Toast.makeText(this, R.string.app_filter_need_selection, Toast.LENGTH_SHORT).show()
            return
        }

        ServiceManager.refresh(this)
        if (appStatus.first != AppStatus.Running) {
            Toast.makeText(this, R.string.app_check_not_running, Toast.LENGTH_SHORT).show()
            return
        }

        val (ip, port) = getPreferences().getProxyIpAndPort()
        val utils = AppCheckUtils(ip, port.toIntOrNull() ?: 1080)

        checking = true
        resultsLoaded = false
        adapter?.clearResults()
        checkSummary.visibility = View.VISIBLE
        checkSummary.text = getString(R.string.app_filter_checking)
        checkProgress.visibility = View.VISIBLE
        checkButton.isEnabled = false
        applyButton.isEnabled = false

        var done = 0
        var working = 0
        val total = selected.size

        checkJob = lifecycleScope.launch {
            utils.checkApps(selected, concurrency = 3) { result ->
                withContext(Dispatchers.Main) {
                    done++
                    if (result.status != AppCheckUtils.Status.FAILED) working++

                    adapter?.setResult(result)
                    checkSummary.text = getString(
                        R.string.app_check_progress,
                        done,
                        total
                    )
                }
            }

            checking = false
            resultsLoaded = true
            checkProgress.visibility = View.GONE
            checkSummary.text = getString(R.string.app_filter_check_summary, working, total)
            checkButton.isEnabled = true
            applyButton.isEnabled = true
        }
    }

    private fun resetCheckResults() {
        checkJob?.cancel()
        checkJob = null
        checking = false
        resultsLoaded = false
        adapter?.clearResults()
        checkProgress.visibility = View.GONE
        checkSummary.visibility = View.GONE
        checkButton.isEnabled = (adapter?.selectedPackages()?.size ?: 0) > 0
        applyButton.isEnabled = true
    }

    private fun applyFilter() {
        if (checking) return

        val mode = currentMode()
        val selected = adapter?.selectedPackages().orEmpty()

        if (mode == "whitelist" && selected.isEmpty()) {
            Toast.makeText(this, R.string.app_filter_need_selection, Toast.LENGTH_SHORT).show()
            return
        }

        val prefs = getPreferences()
        prefs.edit {
            putString("applist_type", mode)
            putStringSet("selected_apps", selected.toSet())
        }

        ServiceManager.refresh(this)
        if (appStatus.first == AppStatus.Running && prefs.mode() == Mode.VPN) {
            ServiceManager.restart(this, Mode.VPN)
        }

        Toast.makeText(this, R.string.app_filter_applied, Toast.LENGTH_SHORT).show()
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        )
        finish()
    }
}
