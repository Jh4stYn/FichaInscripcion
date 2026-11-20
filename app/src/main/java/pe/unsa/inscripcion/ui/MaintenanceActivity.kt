package pe.unsa.inscripcion.ui

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Intent
import android.database.sqlite.SQLiteException
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.*
import pe.unsa.inscripcion.R
import pe.unsa.inscripcion.data.*
import java.time.LocalDate

class MaintenanceActivity : BaseActivity() {
    private data class StateAction(val title: String, val target: String)
    private data class CatalogRow(val row: ListRow, val keywords: String)

    lateinit var module: Module
        private set
    var mode = EditMode.VIEW
        private set
    var selected: Record? = null
        private set
    var discardDialog: AlertDialog? = null
        private set

    private lateinit var listPanel: LinearLayout
    private lateinit var list: ListView
    private lateinit var rows: RecordListAdapter
    private lateinit var search: EditText
    private lateinit var filter: Spinner
    private lateinit var results: TextView
    private lateinit var retry: Button
    private lateinit var code: EditText
    private lateinit var name: EditText
    private lateinit var date: EditText
    private lateinit var amount: EditText
    private lateinit var feedback: TextView
    private lateinit var fieldErrors: Map<EditText, TextView>
    private val references = linkedMapOf<Module, String>()
    private val referenceButtons = linkedMapOf<Module, Button>()
    private val referenceErrors = linkedMapOf<Module, TextView>()
    private var catalog = emptyList<CatalogRow>()
    private var catalogDirty = true
    private var loadFailed = false
    private var baseline = emptyList<String>()
    private var listPosition = 0
    private var listOffset = 0

    private val editorScroll get() = findViewById<ScrollView>(R.id.scroll)
    private val editing get() = mode != EditMode.VIEW

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        module = Module.from(intent.getStringExtra("module"))
        if (Build.VERSION.SDK_INT >= 33) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT
            ) { onBackPressed() }
        }

        bindList()
        bindForm()
        val state = savedInstanceState
        state?.let {
            search.setText(it.getString("query").orEmpty())
            filter.setSelection(it.getInt("filter"))
            listPosition = it.getInt("position")
            listOffset = it.getInt("offset")
        }
        val previousCode = state?.getString("selected")
        val previous = try {
            previousCode?.let { repository.get(module, it) }
        } catch (_: SQLiteException) {
            null
        }
        val restoredMode = EditMode.valueOf(state?.getString("mode") ?: EditMode.VIEW.name)
        when {
            state != null && restoredMode != EditMode.VIEW -> {
                selected = previous
                mode = restoredMode
                fillInputs(state.getStringArrayList("draft").orEmpty())
                baseline = state.getStringArrayList("baseline") ?: snapshot()
                showForm(state.getInt("form_scroll"))
            }
            previous != null -> showDetail(previous)
            state == null && intent.getBooleanExtra("create", false) -> beginAdd()
            else -> showList()
        }
    }

    private fun bindList() {
        listPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(8))
            isFocusableInTouchMode = true
        }
        search = searchField(if (module == Module.FICHA) "Buscar por número o socio" else getString(R.string.search)) {
            list.requestFocus()
        }
        listPanel.addView(search)
        val filterRow = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, 0)
        }
        filter = Spinner(this).apply {
            id = R.id.filter
            isSaveEnabled = false
            minimumHeight = dp(48)
            adapter = ArrayAdapter(this@MaintenanceActivity, android.R.layout.simple_spinner_item,
                listOf("Todos", "Activos", "Inactivos", "Eliminados")).also {
                it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
        }
        filterRow.addView(label("Estado", 14f, R.color.app_muted, bold = true).apply {
            labelFor = R.id.filter
            setPadding(dp(2), 0, dp(8), 0)
        })
        filterRow.addView(filter, LinearLayout.LayoutParams(0, -2, 1f))
        listPanel.addView(filterRow)

        results = label("", 14f, R.color.app_muted).apply {
            setPadding(dp(2), dp(4), dp(2), dp(10))
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        listPanel.addView(results)
        retry = action("Reintentar").apply { setOnClickListener { reloadList() } }
        listPanel.addView(retry)
        rows = RecordListAdapter(this)
        list = ListView(this).apply {
            id = R.id.records
            isSaveEnabled = false
            setDrawSelectorOnTop(true)
            adapter = rows
            divider = ColorDrawable(Color.TRANSPARENT)
            dividerHeight = dp(8)
            setOnItemClickListener { _, _, position, _ -> selectCode(rows.getItem(position).code) }
        }
        listPanel.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        stage.addView(listPanel, FrameLayout.LayoutParams(-1, -1))
        search.onTextEdited {
            filterList()
            list.setSelection(0)
        }
        filter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) = filterList()
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun bindForm() {
        layoutInflater.inflate(R.layout.maintenance, body, true)
        code = findViewById(R.id.code)
        name = findViewById(R.id.name)
        date = findViewById(R.id.date)
        amount = findViewById(R.id.amount)
        feedback = findViewById(R.id.feedback)
        fieldErrors = mapOf(code to findViewById(R.id.code_error), name to findViewById(R.id.name_error),
            date to findViewById(R.id.date_error), amount to findViewById(R.id.amount_error))
        for ((field, message) in fieldErrors) {
            field.isSaveEnabled = false
            field.onTextEdited { message.visibility = View.GONE }
            field.accessibilityDelegate = object : View.AccessibilityDelegate() {
                override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    if (message.visibility == View.VISIBLE) {
                        info.isContentInvalid = true
                        info.error = message.text
                    }
                }
            }
        }
        amount.setRawInputType(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
        findViewById<TextView>(R.id.code_label).setText(if (module == Module.FICHA) R.string.number else R.string.code)
        findViewById<View>(R.id.catalog_fields).visibility = if (module == Module.FICHA) View.GONE else View.VISIBLE
        findViewById<View>(R.id.ficha_fields).visibility = if (module == Module.FICHA) View.VISIBLE else View.GONE
        if (module == Module.FICHA) bindReferences()

        findViewById<View>(R.id.calendar).apply {
            background = actionBackground(false)
            setOnClickListener {
                hideKeyboard()
                val current = runCatching { LocalDate.parse(date.text) }.getOrDefault(LocalDate.now())
                DatePickerDialog(this@MaintenanceActivity, { _, year, month, day ->
                    date.setText(LocalDate.of(year, month + 1, day).toString())
                }, current.year, current.monthValue - 1, current.dayOfMonth).show()
            }
        }
    }

    private fun bindReferences() {
        val fields = findViewById<LinearLayout>(R.id.references)
        for ((index, reference) in listOf(Module.SOCIO, Module.TIPO, Module.DEPORTE).withIndex()) {
            references[reference] = ""
            val title = label(reference.singular, 14f, R.color.app_muted).apply {
                setPadding(0, if (index == 0) 0 else dp(10), 0, dp(4))
            }
            fields.addView(title)
            val button = action("").apply {
                id = View.generateViewId()
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.ic_chevron, 0)
                compoundDrawablePadding = dp(10)
                setOnClickListener { openReference(reference) }
            }
            title.labelFor = button.id
            referenceButtons[reference] = button
            fields.addView(button)

            val error = label("", 14f, R.color.app_danger).apply {
                setPadding(0, dp(5), 0, 0)
                visibility = View.GONE
                accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            }
            referenceErrors[reference] = error
            fields.addView(error)
        }
    }

    @Suppress("DEPRECATION")
    private fun openReference(module: Module) {
        hideKeyboard()
        startActivityForResult(Intent(this, ReferenceActivity::class.java)
            .putExtra("module", module.name).putExtra("current", references[module]), 100 + module.ordinal)
    }

    private fun reloadList() {
        try {
            catalog = repository.list(module).map { item ->
                val status = RecordStatus.from(item.state)
                val metadata = if (module == Module.FICHA) "${item.code} · ${item.sportName}" else item.code
                CatalogRow(ListRow(item.code, item.name, metadata, status), SearchText.normalize("${item.code} ${item.name}"))
            }
            loadFailed = false
            catalogDirty = false
        } catch (_: SQLiteException) {
            catalog = emptyList()
            loadFailed = true
            catalogDirty = true
        }
        filterList()
    }

    private fun filterList() {
        val status = RecordStatus.entries.getOrNull(filter.selectedItemPosition - 1)
        val query = SearchText.normalize(search.text.toString().trim())
        val matches = catalog.filter { (row, keywords) ->
            (status == null || row.status == status) && keywords.contains(query)
        }
        rows.submit(matches.map { it.row })
        retry.visibility = if (loadFailed) View.VISIBLE else View.GONE
        results.text = when {
            loadFailed -> "No se pudieron cargar los registros. Vuelve a intentarlo."
            catalog.isEmpty() -> "Todavía no hay registros."
            matches.isEmpty() && query.isNotEmpty() -> "No hay coincidencias. Revisa la búsqueda o cambia el estado."
            matches.isEmpty() -> "No hay registros con este estado."
            matches.size == 1 -> "1 registro"
            else -> "${matches.size} registros"
        }
    }

    private fun showList() {
        mode = EditMode.VIEW
        selected = null
        heading(module.title)
        editorScroll.visibility = View.GONE
        listPanel.visibility = View.VISIBLE
        if (catalogDirty) reloadList()
        setFooter(action(newLabel(), true).apply { id = R.id.add; setOnClickListener { beginAdd() } })
        list.post { list.setSelectionFromTop(listPosition, listOffset) }
        listPanel.requestFocus()
    }

    private fun newLabel() = when (module) {
        Module.FICHA -> "+ Nueva inscripción"
        Module.SOCIO -> "+ Nuevo socio"
        Module.TIPO -> "+ Nuevo tipo de socio"
        Module.DEPORTE -> "+ Nuevo deporte"
    }

    private fun rememberListPosition() {
        if (listPanel.visibility == View.VISIBLE) {
            listPosition = list.firstVisiblePosition
            listOffset = list.getChildAt(0)?.top ?: 0
        }
    }

    fun selectCode(value: String) {
        protectChanges {
            rememberListPosition()
            hideKeyboard()
            val record = try {
                repository.get(module, value)
            } catch (_: SQLiteException) {
                results.text = "No se pudo abrir el registro. Vuelve a intentarlo."
                return@protectChanges
            }
            if (record != null) showDetail(record) else reloadList()
        }
    }

    private fun showDetail(record: Record, message: String = "", error: Boolean = false) {
        mode = EditMode.VIEW
        selected = record
        heading(if (module == Module.FICHA) "Ficha ${record.code}" else module.singular, module.title)
        listPanel.visibility = View.GONE
        editorScroll.visibility = View.VISIBLE
        formCard().visibility = View.GONE
        removeDetail()
        val status = RecordStatus.from(record.state)
        val summary = card().apply { tag = "detail" }
        summary.addView(label(record.name, 25f, bold = true))
        summary.addView(label(status.label, 13f, status.color, bold = true).apply {
            background = GradientDrawable().apply {
                setColor(getColor(status.fill))
                cornerRadius = dp(12).toFloat()
            }
            setPadding(dp(10), dp(4), dp(10), dp(4))
        }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(8) })
        if (message.isNotEmpty()) {
            summary.addView(label(message, 15f, if (error) R.color.app_danger else R.color.app_positive, bold = true).apply {
                setPadding(0, dp(12), 0, 0)
                accessibilityLiveRegion = if (error) View.ACCESSIBILITY_LIVE_REGION_ASSERTIVE
                    else View.ACCESSIBILITY_LIVE_REGION_POLITE
            })
        }
        if (module == Module.FICHA) {
            addDetail(summary, "Código del socio", record.socio)
            addDetail(summary, "Tipo de socio", "${record.tipo} · ${record.typeName}")
            addDetail(summary, "Deporte", "${record.deporte} · ${record.sportName}")
            addDetail(summary, "Fecha", record.date)
            addDetail(summary, "Monto", "S/ ${repository.money(record.cents)}")
        } else addDetail(summary, "Código", record.code)
        body.addView(summary)

        val more = action("Más acciones").apply { id = R.id.more; setOnClickListener { openActions(record) } }
        val main = if (record.state == "A") action("Editar", true).apply {
            id = R.id.edit
            setOnClickListener { beginEdit() }
        } else action("Reactivar", true).apply {
            id = R.id.reactivate
            setOnClickListener { changeState("A") }
        }
        if (record.state == "*") setFooter(main) else setFooter(more, main)
        editorScroll.scrollTo(0, 0)
        body.requestFocus()
    }

    private fun addDetail(card: LinearLayout, title: String, value: String) {
        card.addView(label(title, 14f, R.color.app_muted).apply { setPadding(0, dp(13), 0, dp(3)) })
        card.addView(label(value, 17f))
    }

    private fun openActions(record: Record) {
        val actions = buildList {
            if (record.state == "A") add(StateAction("Inactivar", "I"))
            if (record.state != "*") add(StateAction("Eliminar…", "*"))
        }
        AlertDialog.Builder(this).setTitle("Acciones de ${record.code}")
            .setItems(actions.map { it.title }.toTypedArray()) { _, index ->
                val action = actions[index]
                if (action.target == "*") confirmDelete(record) else changeState(action.target)
            }
            .setNegativeButton(R.string.cancel, null).show()
    }

    private fun confirmDelete(record: Record) {
        AlertDialog.Builder(this).setTitle("¿Eliminar ${record.code}?")
            .setMessage("El registro quedará en estado Eliminado. Podrás reactivarlo.")
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ -> changeState("*") }.show()
    }

    fun beginAdd() {
        rememberListPosition()
        selected = null
        mode = EditMode.ADD
        fillInputs(listOf("", "", if (module == Module.FICHA) LocalDate.now().toString() else "", ""))
        baseline = snapshot()
        showForm()
    }

    fun beginEdit() {
        val record = selected?.takeIf { it.state == "A" } ?: return
        mode = EditMode.EDIT
        fillInputs(listOf(record.code, record.name, record.date,
            if (module == Module.FICHA) repository.money(record.cents) else "",
            record.socio, record.tipo, record.deporte))
        baseline = snapshot()
        showForm()
    }

    private fun showForm(scrollY: Int = 0) {
        removeDetail()
        heading(if (mode == EditMode.ADD) newLabel().removePrefix("+ ") else "Editar ${code.text}", module.title)
        listPanel.visibility = View.GONE
        editorScroll.visibility = View.VISIBLE
        formCard().visibility = View.VISIBLE
        code.isEnabled = mode == EditMode.ADD
        for (reference in references.keys) updateReference(reference)
        clearErrors()
        val cancel = action("Cancelar").apply { id = R.id.cancel; setOnClickListener { cancelEdit() } }
        val save = action("Guardar", true).apply { id = R.id.save; setOnClickListener { save() } }
        setFooter(cancel, save)
        body.requestFocus()
        editorScroll.post { if (editing) editorScroll.scrollTo(0, scrollY) }
    }

    private fun formCard() = findViewById<View>(R.id.editor_card)

    private fun removeDetail() {
        body.findViewWithTag<View>("detail")?.let(body::removeView)
    }

    private fun snapshot() = listOf(code.text.toString(), name.text.toString(),
        date.text.toString(), amount.text.toString()) + references.values

    private fun fillInputs(fields: List<String>) {
        listOf(code, name, date, amount).forEachIndexed { index, input -> input.setText(fields.getOrNull(index).orEmpty()) }
        references.keys.toList().forEachIndexed { index, module -> references[module] = fields.getOrNull(4 + index).orEmpty() }
    }

    private fun updateReference(reference: Module, knownName: String? = null) {
        val button = referenceButtons.getValue(reference)
        val code = references.getValue(reference)
        val description = if (code.isEmpty()) {
            "Elegir ${reference.singular.lowercase()}"
        } else {
            val name = knownName ?: try {
                repository.get(reference, code)?.name
            } catch (_: SQLiteException) {
                null
            }
            if (name == null) code else "$code · $name"
        }
        button.text = description
        button.contentDescription = "Consultar ${reference.singular}. $description"
    }

    fun hasChanges() = editing && snapshot() != baseline

    private fun protectChanges(next: () -> Unit) {
        if (!hasChanges()) {
            next()
            return
        }
        if (discardDialog?.isShowing == true) return
        hideKeyboard()
        discardDialog = AlertDialog.Builder(this).setTitle("Cambios sin guardar")
            .setMessage("¿Quieres descartar los cambios de este formulario?")
            .setNegativeButton("Seguir editando", null)
            .setPositiveButton("Descartar") { _, _ -> next() }.show()
    }

    fun cancelEdit() = protectChanges {
        hideKeyboard()
        val previous = selected
        if (previous == null) showList() else showDetail(previous)
    }

    @Deprecated("Platform Activity navigation for API 26–32")
    override fun onBackPressed() {
        when {
            editing -> cancelEdit()
            selected != null -> showList()
            else -> finish()
        }
    }

    fun save() {
        if (!editing) return
        clearErrors()
        val saved = try {
            repository.save(module, code.text.toString(), name.text.toString(),
                references[Module.SOCIO].orEmpty(), references[Module.TIPO].orEmpty(),
                references[Module.DEPORTE].orEmpty(), date.text.toString(), amount.text.toString(),
                mode == EditMode.EDIT)
        } catch (error: DomainError) {
            showFormError(error)
            return
        } catch (_: SQLiteException) {
            showFormError(DomainError("storage", "No se pudo guardar. Vuelve a intentarlo."))
            return
        }
        hideKeyboard()
        catalogDirty = true
        showDetail(saved, "${saved.code} se guardó correctamente.")
    }

    private fun clearErrors() {
        fieldErrors.values.forEach { it.visibility = View.GONE }
        referenceErrors.values.forEach { it.visibility = View.GONE }
        feedback.visibility = View.GONE
    }

    private fun showFormError(error: DomainError) {
        val field = when (error.field) {
            "code" -> code
            "name" -> name
            "date" -> date
            "amount" -> amount
            else -> null
        }
        val reference = referenceErrors.entries.firstOrNull { it.key.name == error.field }
        val message = when {
            field != null -> fieldErrors.getValue(field).also { field.requestFocus() }
            reference != null -> reference.value.also {
                hideKeyboard()
                referenceButtons.getValue(reference.key).requestFocus()
            }
            else -> feedback
        }
        message.text = error.message
        message.visibility = View.VISIBLE
        message.post { message.requestRectangleOnScreen(Rect(0, 0, message.width, message.height), true) }
    }

    fun changeState(target: String) {
        val record = selected ?: return
        val changed = try {
            repository.changeState(module, record.code, target)
        } catch (error: DomainError) {
            showDetail(record, error.message, error = true)
            return
        } catch (_: SQLiteException) {
            showDetail(record, "No se pudo cambiar el estado. Vuelve a intentarlo.", error = true)
            return
        }
        catalogDirty = true
        showDetail(changed, "${record.code} ahora figura como ${RecordStatus.from(target).label.lowercase()}.")
    }

    @Deprecated("Platform Activity result API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || !editing) return
        val reference = references.keys.firstOrNull { 100 + it.ordinal == requestCode } ?: return
        val code = data?.getStringExtra("code") ?: return
        val record = try {
            repository.get(reference, code)
        } catch (_: SQLiteException) {
            showFormError(DomainError(reference.name, "No se pudo comprobar el registro. Inténtalo otra vez."))
            return
        }
        if (record?.state != "A") {
            showFormError(DomainError(reference.name, "Este registro ya no está activo. Elige otro."))
            return
        }
        references[reference] = code
        referenceErrors.getValue(reference).visibility = View.GONE
        updateReference(reference, record.name)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        rememberListPosition()
        outState.putString("selected", selected?.code)
        outState.putString("mode", mode.name)
        outState.putString("query", search.text.toString())
        outState.putInt("filter", filter.selectedItemPosition)
        outState.putInt("position", listPosition)
        outState.putInt("offset", listOffset)
        if (editing) {
            outState.putStringArrayList("draft", ArrayList(snapshot()))
            outState.putStringArrayList("baseline", ArrayList(baseline))
            outState.putInt("form_scroll", editorScroll.scrollY)
        }
        super.onSaveInstanceState(outState)
    }
}
