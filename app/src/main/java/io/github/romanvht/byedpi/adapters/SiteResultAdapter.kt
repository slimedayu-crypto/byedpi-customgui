package io.github.romanvht.byedpi.adapters

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import io.github.romanvht.byedpi.R
import io.github.romanvht.byedpi.data.SiteResult

class SiteResultAdapter : RecyclerView.Adapter<SiteResultAdapter.SiteViewHolder>() {

    private data class Row(val name: String, val value: String)

    private val rows = mutableListOf<Row>()

    class SiteViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val siteTextView: TextView = view.findViewById(R.id.siteTextView)
        val resultTextView: TextView = view.findViewById(R.id.siteResultTextView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SiteViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_site_result, parent, false)
        return SiteViewHolder(view)
    }

    override fun onBindViewHolder(holder: SiteViewHolder, position: Int) {
        val row = rows[position]
        holder.siteTextView.text = row.name
        holder.resultTextView.text = row.value
    }

    override fun getItemCount(): Int {
        return rows.size
    }

    @SuppressLint("NotifyDataSetChanged")
    fun updateSites(
        newSites: List<SiteResult>,
        appRows: List<Pair<String, String>> = emptyList(),
    ) {
        rows.clear()
        // Apps first: they matter more than sites when picking a strategy.
        appRows.forEach { rows.add(Row(it.first, it.second)) }
        newSites.forEach { rows.add(Row(it.site, "${it.successCount}/${it.totalCount}")) }
        notifyDataSetChanged()
    }
}
