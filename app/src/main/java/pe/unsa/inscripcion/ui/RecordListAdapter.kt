package pe.unsa.inscripcion.ui

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import pe.unsa.inscripcion.R

internal enum class RecordStatus(val code: String, val label: String, val color: Int, val fill: Int) {
    ACTIVE("A", "Activo", R.color.app_positive, R.color.app_positive_bg),
    INACTIVE("I", "Inactivo", R.color.app_warning, R.color.app_warning_bg),
    DELETED("*", "Eliminado", R.color.app_danger, R.color.app_danger_bg);

    companion object {
        fun from(code: String) = entries.first { it.code == code }
    }
}

internal data class ListRow(
    val code: String,
    val title: String,
    val metadata: String,
    val status: RecordStatus? = null,
    val checked: Boolean = false,
)

internal class RecordListAdapter(private val context: Context) : BaseAdapter() {
    private var rows = emptyList<ListRow>()

    fun submit(items: List<ListRow>) {
        rows = items
        notifyDataSetChanged()
    }

    override fun getCount() = rows.size
    override fun getItem(position: Int) = rows[position]
    override fun getItemId(position: Int) = position.toLong()

    private class RowView(context: Context) : LinearLayout(context) {
        val title = TextView(context)
        val metadata = TextView(context)
        val badge = TextView(context)
        private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

        init {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(68)
            title.textSize = 17f
            title.setTypeface(null, Typeface.BOLD)
            title.setTextColor(context.getColor(R.color.app_text))
            metadata.textSize = 14f
            metadata.setTextColor(context.getColor(R.color.app_muted))
            metadata.setPadding(0, dp(3), 0, 0)
            addView(LinearLayout(context).apply {
                orientation = VERTICAL
                addView(title)
                addView(metadata)
            }, LayoutParams(0, -2, 1f))
            badge.textSize = 12f
            badge.gravity = Gravity.CENTER
            badge.setTypeface(null, Typeface.BOLD)
            badge.maxWidth = dp(98)
            badge.setPadding(dp(8), dp(4), dp(8), dp(4))
            addView(badge, LayoutParams(-2, -2).apply { marginStart = dp(8) })
            if (android.os.Build.VERSION.SDK_INT >= 28) isScreenReaderFocusable = true
        }

        fun bind(row: ListRow) {
            title.text = row.title
            metadata.text = row.metadata
            setBackgroundResource(if (row.checked) R.drawable.selected else R.drawable.card)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            val status = row.status
            badge.visibility = if (status == null) View.GONE else View.VISIBLE
            if (status != null) {
                badge.text = status.label
                badge.setTextColor(context.getColor(status.color))
                badge.background = GradientDrawable().apply {
                    setColor(context.getColor(status.fill))
                    cornerRadius = dp(12).toFloat()
                }
            }
        }
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
        (convertView as? RowView ?: RowView(context)).apply { bind(getItem(position)) }
}

internal fun EditText.onTextEdited(callback: () -> Unit) {
    addTextChangedListener(object : TextWatcher {
        override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) = callback()
        override fun afterTextChanged(text: Editable?) = Unit
    })
}
