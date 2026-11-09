package io.github.romanvht.byedpi.ui

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.widget.EditText
import android.widget.TextView
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.google.android.material.card.MaterialCardView
import io.github.romanvht.byedpi.R

/**
 * Header of the settings list: search field and the current theme card.
 * Kept as a preference so it scrolls together with the rest of the settings.
 */
class SettingsHeaderPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : Preference(context, attrs) {

    var onQueryChanged: ((String) -> Unit)? = null
    var onThemeClick: (() -> Unit)? = null

    var themeName: String = ""
        set(value) {
            field = value
            notifyChanged()
        }

    var animationsOn: Boolean = true
        set(value) {
            field = value
            notifyChanged()
        }

    /** Current search text, kept so the field survives RecyclerView rebinds. */
    var query: String = ""

    init {
        layoutResource = R.layout.pref_settings_header
        isPersistent = false
        isSelectable = false
        isIconSpaceReserved = false
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val root = holder.itemView

        val search = root.findViewById<EditText>(R.id.settings_search)
        (search.tag as? TextWatcher)?.let(search::removeTextChangedListener)
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                query = s?.toString().orEmpty()
                onQueryChanged?.invoke(query)
            }
        }
        if (search.text.toString() != query) {
            search.setText(query)
            search.setSelection(search.text.length)
        }
        search.addTextChangedListener(watcher)
        search.tag = watcher

        root.findViewById<TextView>(R.id.theme_title).text = themeName
        root.findViewById<TextView>(R.id.theme_subtitle).text = context.getString(
            if (animationsOn) R.string.settings_theme_subtitle_on
            else R.string.settings_theme_subtitle_off
        )
        root.findViewById<MaterialCardView>(R.id.theme_card).setOnClickListener {
            onThemeClick?.invoke()
        }
    }
}
