package io.github.romanvht.byedpi.adapters

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.Filter
import android.widget.Filterable
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import io.github.romanvht.byedpi.R
import io.github.romanvht.byedpi.data.AppInfo
import io.github.romanvht.byedpi.utility.AppCheckUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppFilterAdapter(
    context: Context,
    allApps: List<AppInfo>,
    private val onSelectionChanged: () -> Unit,
    private val onFilteredCountChanged: (Int) -> Unit,
) : RecyclerView.Adapter<AppFilterAdapter.ViewHolder>(), Filterable {

    private val appContext = context.applicationContext
    private val pm = appContext.packageManager
    private val originalApps: List<AppInfo> = allApps
    private val filteredApps: MutableList<AppInfo> = allApps.toMutableList()
    private val adapterScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val results = mutableMapOf<String, AppCheckUtils.Result>()

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val appIcon: ImageView = view.findViewById(R.id.appIcon)
        val appName: TextView = view.findViewById(R.id.appName)
        val appPackageName: TextView = view.findViewById(R.id.appPackageName)
        val appCheckBox: CheckBox = view.findViewById(R.id.appCheckBox)
        val statusRow: View = view.findViewById(R.id.statusRow)
        val statusText: TextView = view.findViewById(R.id.checkStatus)
        val statusLatency: TextView = view.findViewById(R.id.checkLatency)
        var iconLoadJob: Job? = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app_filter, parent, false)
        val holder = ViewHolder(view)

        holder.itemView.setOnClickListener {
            val position = holder.bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION) {
                val app = filteredApps[position]
                app.isSelected = !app.isSelected
                notifyItemChanged(position)
                onSelectionChanged()
            }
        }

        return holder
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val app = filteredApps[position]
        val result = results[app.packageName]
        val colorPrimary = ContextCompat.getColor(appContext, R.color.green_active)
        val colorOk = ContextCompat.getColor(appContext, R.color.score_ok)
        val colorBad = ContextCompat.getColor(appContext, R.color.score_bad)

        if (app.appName == app.packageName) {
            holder.appName.text = app.packageName
            holder.appPackageName.visibility = View.GONE
        } else {
            holder.appName.text = app.appName
            holder.appPackageName.text = app.packageName
            holder.appPackageName.visibility = View.VISIBLE
        }

        holder.appCheckBox.isChecked = app.isSelected

        if (result == null) {
            holder.statusRow.visibility = View.GONE
        } else {
            holder.statusRow.visibility = View.VISIBLE
            when (result.status) {
                AppCheckUtils.Status.WORKING -> {
                    holder.statusText.setText(R.string.app_check_ok)
                    holder.statusText.setTextColor(colorPrimary)
                }

                AppCheckUtils.Status.PARTIAL -> {
                    holder.statusText.setText(R.string.app_check_partial)
                    holder.statusText.setTextColor(colorOk)
                }

                AppCheckUtils.Status.FAILED -> {
                    holder.statusText.setText(R.string.app_check_fail)
                    holder.statusText.setTextColor(colorBad)
                }
            }

            holder.statusLatency.text = if (result.status == AppCheckUtils.Status.FAILED) {
                ""
            } else {
                val latency = appContext.getString(R.string.app_check_latency, result.latencyMs)
                val type = appContext.getString(
                    if (result.deep) R.string.app_check_deep else R.string.app_check_basic
                )
                "$latency · $type"
            }
        }

        holder.iconLoadJob?.cancel()

        if (app.icon != null) {
            holder.appIcon.setImageDrawable(app.icon)
        } else {
            holder.appIcon.setImageDrawable(pm.defaultActivityIcon)

            holder.iconLoadJob = adapterScope.launch {
                val icon = withContext(Dispatchers.IO) {
                    try {
                        pm.getApplicationIcon(app.packageName)
                    } catch (_: Exception) {
                        null
                    }
                }

                if (icon != null) {
                    app.icon = icon
                    holder.appIcon.setImageDrawable(icon)
                }
            }
        }
    }

    override fun getItemCount(): Int = filteredApps.size

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        holder.iconLoadJob?.cancel()
    }

    override fun getFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                val query = constraint?.toString()?.trim()?.lowercase().orEmpty()
                val filteredList = if (query.isEmpty()) {
                    originalApps
                } else {
                    originalApps.filter {
                        it.appName.lowercase().contains(query) ||
                            it.packageName.lowercase().contains(query)
                    }
                }

                return FilterResults().apply { values = filteredList }
            }

            @SuppressLint("NotifyDataSetChanged")
            override fun publishResults(constraint: CharSequence, filterResults: FilterResults) {
                val newList = (filterResults.values as List<*>).filterIsInstance<AppInfo>()
                filteredApps.clear()
                filteredApps.addAll(newList)
                notifyDataSetChanged()
                onFilteredCountChanged(filteredApps.size)
            }
        }
    }

    fun selectedPackages(): List<String> =
        originalApps.filter { it.isSelected }.map { it.packageName }

    fun setResult(result: AppCheckUtils.Result) {
        results[result.packageName] = result
        val index = filteredApps.indexOfFirst { it.packageName == result.packageName }
        if (index != RecyclerView.NO_POSITION && index >= 0) {
            notifyItemChanged(index)
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun clearResults() {
        results.clear()
        notifyDataSetChanged()
    }
}
