package io.github.romanvht.byedpi.adapters

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.github.romanvht.byedpi.R
import io.github.romanvht.byedpi.data.AppCheckStatus
import io.github.romanvht.byedpi.data.StrategyResult
import io.github.romanvht.byedpi.utility.ClipboardUtils

class StrategyResultAdapter(
    private val context: Context,
    private val onApply: (String) -> Unit
) : RecyclerView.Adapter<StrategyResultAdapter.StrategyViewHolder>() {

    private var isTesting = false
    private val strategies = mutableListOf<StrategyResult>()

    class StrategyViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val commandTextView: TextView = view.findViewById(R.id.commandTextView)
        val scoreTextView: TextView = view.findViewById(R.id.scoreTextView)
        val progressLayout: LinearLayout = view.findViewById(R.id.progressLayout)
        val progressBar: ProgressBar = view.findViewById(R.id.progressBar)
        val progressTextView: TextView = view.findViewById(R.id.progressTextView)
        val expandButton: LinearLayout = view.findViewById(R.id.expandButton)
        val expandTextView: TextView = view.findViewById(R.id.expandTextView)
        val expandIcon: ImageView = view.findViewById(R.id.expandIcon)
        val sitesRecyclerView: RecyclerView = view.findViewById(R.id.sitesRecyclerView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StrategyViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_strategy_result, parent, false)
        val holder = StrategyViewHolder(view)

        holder.commandTextView.setOnClickListener {
            val position = holder.bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION) {
                showCommandMenu(strategies[position].command)
            }
        }

        holder.expandButton.setOnClickListener {
            val position = holder.bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION) {
                strategies[position].isExpanded = !strategies[position].isExpanded
                notifyItemChanged(position)
            }
        }

        return holder
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: StrategyViewHolder, position: Int) {
        val strategy = strategies[position]

        holder.commandTextView.text = strategy.command
        bindScore(holder, strategy)

        if (strategy.totalRequests > 0) {
            holder.progressLayout.visibility = View.VISIBLE
            holder.progressTextView.visibility = View.VISIBLE

            val isProgress = isTesting && !strategy.isCompleted

            if (isProgress) {
                holder.progressBar.isIndeterminate = true
                holder.progressTextView.text = progressText(strategy)
            } else {
                holder.progressBar.isIndeterminate = false
                holder.progressBar.max = strategy.totalRequests
                holder.progressBar.progress = strategy.successCount
                holder.progressTextView.text = progressText(strategy)
            }
        } else {
            holder.progressLayout.visibility = View.GONE
        }

        if (strategy.siteResults.isNotEmpty()) {
            holder.expandButton.visibility = View.VISIBLE

            if (strategy.isExpanded) {
                holder.expandTextView.text = context.getString(R.string.test_hide_details)
                holder.expandIcon.rotation = 180f
                holder.sitesRecyclerView.visibility = View.VISIBLE

                val siteAdapter = SiteResultAdapter()
                holder.sitesRecyclerView.layoutManager = LinearLayoutManager(context)
                holder.sitesRecyclerView.adapter = siteAdapter
                siteAdapter.updateSites(strategy.siteResults, appRows(strategy))
            } else {
                holder.expandTextView.text = context.getString(R.string.test_show_details)
                holder.expandIcon.rotation = 0f
                holder.sitesRecyclerView.visibility = View.GONE
            }
        } else {
            holder.expandButton.visibility = View.GONE
            holder.sitesRecyclerView.visibility = View.GONE
        }
    }

    @SuppressLint("SetTextI18n")
    private fun bindScore(holder: StrategyViewHolder, strategy: StrategyResult) {
        if (strategy.totalRequests <= 0) {
            holder.scoreTextView.isGone = true
            return
        }

        holder.scoreTextView.isVisible = true
        val percent = strategy.weightedPercentage
        holder.scoreTextView.text = when {
            isTesting && !strategy.isCompleted && strategy.siteResults.isEmpty() -> "…"
            else -> "$percent%"
        }

        val foregroundRes = when {
            percent >= 90 -> R.color.score_good
            percent >= 60 -> R.color.score_ok
            else -> R.color.score_bad
        }
        val backgroundRes = when {
            percent >= 90 -> R.color.score_good_bg
            percent >= 60 -> R.color.score_ok_bg
            else -> R.color.score_bad_bg
        }

        val foreground = ContextCompat.getColor(context, foregroundRes)
        holder.scoreTextView.setTextColor(foreground)
        holder.scoreTextView.setBackgroundResource(R.drawable.score_badge)
        holder.scoreTextView.backgroundTintList = ColorStateList.valueOf(
            ContextCompat.getColor(context, backgroundRes)
        )
        holder.progressBar.progressTintList = ColorStateList.valueOf(foreground)
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: StrategyViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads)
        } else {
            val strategy = strategies[position]
            bindScore(holder, strategy)

            if (strategy.totalRequests > 0) {
                holder.progressLayout.visibility = View.VISIBLE
                holder.progressTextView.visibility = View.VISIBLE

                val isProgress = isTesting && !strategy.isCompleted

                if (isProgress) {
                    holder.progressBar.isIndeterminate = true
                    holder.progressTextView.text = progressText(strategy)
                } else {
                    holder.progressBar.isIndeterminate = false
                    holder.progressBar.max = strategy.totalRequests
                    holder.progressBar.progress = strategy.successCount
                    holder.progressTextView.text = progressText(strategy)
                }
            }

            if (strategy.siteResults.isNotEmpty() && holder.expandButton.isGone) {
                holder.expandButton.visibility = View.VISIBLE
            }

            if (strategy.isExpanded && holder.sitesRecyclerView.isVisible) {
                val adapter = holder.sitesRecyclerView.adapter
                if (adapter is SiteResultAdapter) {
                    adapter.updateSites(strategy.siteResults)
                }
            }
        }
    }

    override fun getItemCount(): Int {
        return strategies.size
    }

    fun updateStrategy(strategy: StrategyResult) {
        val position = strategies.indexOfFirst { it === strategy }
        if (position != -1) notifyItemChanged(position, "progress")
    }

    @SuppressLint("NotifyDataSetChanged")
    fun setTestingState(testing: Boolean) {
        if (isTesting == testing) return
        isTesting = testing
        notifyDataSetChanged()
    }

    @SuppressLint("NotifyDataSetChanged")
    fun updateStrategies(newStrategies: List<StrategyResult>) {
        strategies.clear()

        val sorted = newStrategies.sortedWith(compareByDescending<StrategyResult> { it.isCompleted }
            .thenByDescending { it.weightedPercentage }
            .thenByDescending { it.successPercentage }
            .thenByDescending { it.successCount })

        strategies.addAll(sorted)
        notifyDataSetChanged()
    }

    private fun progressText(strategy: StrategyResult): String {
        val base = "${strategy.successCount}/${strategy.totalRequests}"
        val apps = strategy.appList
        if (apps.isEmpty()) return base
        val ok = apps.count { it.status != AppCheckStatus.FAILED }
        return "$base · " + context.getString(R.string.test_apps_progress, ok, apps.size)
    }

    private fun appRows(strategy: StrategyResult): List<Pair<String, String>> =
        strategy.appList.map { app ->
            val status = context.getString(
                when (app.status) {
                    AppCheckStatus.WORKING -> R.string.app_check_ok
                    AppCheckStatus.PARTIAL -> R.string.app_check_partial
                    AppCheckStatus.FAILED -> R.string.app_check_fail
                }
            )
            val value = if (app.status == AppCheckStatus.FAILED) {
                status
            } else {
                "$status · " + context.getString(R.string.app_check_latency, app.latencyMs)
            }
            app.appName to value
        }

    private fun showCommandMenu(command: String) {
        val menuItems = arrayOf(
            context.getString(R.string.cmd_history_apply),
            context.getString(R.string.cmd_history_copy)
        )

        AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.cmd_history_menu))
            .setItems(menuItems) { _, which ->
                when (which) {
                    0 -> onApply(command)
                    1 -> ClipboardUtils.copy(context, command, "command")
                }
            }
            .show()
    }
}
