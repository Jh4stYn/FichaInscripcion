package pe.unsa.inscripcion.ui

import android.content.Intent
import android.database.sqlite.SQLiteException
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import pe.unsa.inscripcion.R
import pe.unsa.inscripcion.data.Module
import pe.unsa.inscripcion.data.SearchText

class ReferenceActivity : BaseActivity() {
    private lateinit var module: Module
    private lateinit var search: EditText
    private lateinit var list: ListView
    private lateinit var guidance: TextView
    private lateinit var rows: RecordListAdapter
    private var catalog = emptyList<Pair<ListRow, String>>()
    private var current: String? = null
    private var loadFailed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        module = Module.from(intent.getStringExtra("module"))
        require(module != Module.FICHA)
        current = intent.getStringExtra("current")
        heading("Seleccionar ${module.singular.lowercase()}", "Registros activos · Orden alfabético")
        findViewById<View>(R.id.scroll).visibility = View.GONE

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            isFocusableInTouchMode = true
            setPadding(dp(16), dp(14), dp(16), dp(8))
        }
        search = searchField(getString(R.string.search)) { list.requestFocus() }
        guidance = label("", 14f, R.color.app_muted).apply {
            setPadding(dp(2), dp(10), dp(2), dp(10))
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        rows = RecordListAdapter(this)
        list = ListView(this).apply {
            id = R.id.records
            isSaveEnabled = false
            setDrawSelectorOnTop(true)
            adapter = rows
            divider = ColorDrawable(Color.TRANSPARENT)
            dividerHeight = dp(8)
            setOnItemClickListener { _, _, position, _ -> choose(rows.getItem(position).code) }
        }
        panel.addView(search)
        panel.addView(guidance)
        panel.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        stage.addView(panel, FrameLayout.LayoutParams(-1, -1))
        search.setText(savedInstanceState?.getString("query").orEmpty())
        loadRecords()
        search.onTextEdited {
            render()
            list.setSelection(0)
        }
        savedInstanceState?.let { list.setSelectionFromTop(it.getInt("position"), it.getInt("offset")) }
    }

    private fun loadRecords() {
        try {
            catalog = repository.list(module, state = "A", alphabetical = true).map {
                val isCurrent = it.code == current
                val metadata = if (isCurrent) "${it.code} · Selección actual" else it.code
                ListRow(it.code, it.name, metadata, checked = isCurrent) to SearchText.normalize("${it.code} ${it.name}")
            }
            loadFailed = false
        } catch (_: SQLiteException) {
            catalog = emptyList()
            loadFailed = true
        }
        render()
        val cancel = action("Cancelar").apply { setOnClickListener { finish() } }
        if (loadFailed) {
            setFooter(cancel, action("Reintentar", true).apply { setOnClickListener { loadRecords() } })
        } else setFooter(cancel)
    }

    private fun render() {
        val query = SearchText.normalize(search.text.toString().trim())
        val items = catalog.filter { it.second.contains(query) }.map { it.first }
        rows.submit(items)
        guidance.text = when {
            loadFailed -> "No se pudieron cargar los registros. Vuelve a intentarlo."
            catalog.isEmpty() -> "No hay registros activos disponibles."
            items.isEmpty() -> "No hay coincidencias con la búsqueda."
            else -> "Toca un registro para usarlo en la ficha."
        }
    }

    private fun choose(code: String) {
        val active = try {
            repository.get(module, code)?.state == "A"
        } catch (_: SQLiteException) {
            guidance.text = "No se pudo comprobar el registro. Vuelve a tocarlo."
            return
        }
        if (!active) {
            loadRecords()
            if (!loadFailed) guidance.text = "Ese registro cambió de estado. Elige otro registro activo."
            return
        }
        hideKeyboard()
        setResult(RESULT_OK, Intent().putExtra("code", code))
        finish()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("query", search.text.toString())
        outState.putInt("position", list.firstVisiblePosition)
        outState.putInt("offset", list.getChildAt(0)?.top ?: 0)
    }
}
