package pe.unsa.inscripcion.ui

import android.content.Intent
import android.database.sqlite.SQLiteException
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import pe.unsa.inscripcion.R
import pe.unsa.inscripcion.data.Module

class MainActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        findViewById<View>(R.id.back).visibility = View.GONE
        heading("Inscripción deportiva")
    }

    override fun onResume() {
        super.onResume()
        showModules()
    }

    private fun showModules() {
        body.removeAllViews()
        val counts = try {
            repository.counts()
        } catch (_: SQLiteException) {
            emptyMap()
        }
        val registration = card().apply {
            background = controlBackground(R.color.app_hero, R.color.app_on_hero, 20)
            setPadding(dp(20), dp(20), dp(20), dp(20))
        }
        registration.addView(label("Inscripciones", 27f, R.color.app_surface, bold = true))
        counts[Module.FICHA]?.let { count ->
            registration.addView(label(if (count == 1) "1 ficha activa" else "$count fichas activas",
                15f, R.color.app_on_hero), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
        }
        registration.addView(action("+ Nueva inscripción").apply {
            background = controlBackground(R.color.app_surface, R.color.app_accent, 12)
            setOnClickListener { open(Module.FICHA, create = true) }
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) })
        registration.addView(action("Ver inscripciones").apply {
            background = controlBackground(R.color.app_hero, R.color.app_on_hero, 12)
            setTextColor(getColor(R.color.app_surface))
            setOnClickListener { open(Module.FICHA) }
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) })
        body.addView(registration)
        body.addView(label("Catálogos", 19f, bold = true).apply {
            if (Build.VERSION.SDK_INT >= 28) isAccessibilityHeading = true
            setPadding(dp(2), dp(4), 0, dp(10))
        })

        val catalogs = card().apply { setPadding(0, 0, 0, 0) }
        for ((index, module) in listOf(Module.SOCIO, Module.TIPO, Module.DEPORTE).withIndex()) {
            if (index > 0) catalogs.addView(View(this).apply {
                setBackgroundColor(getColor(R.color.app_outline))
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(-1, dp(1)).apply { marginStart = dp(16); marginEnd = dp(16) })
            val count = counts[module]
            val details = count?.let { if (it == 1) "1 registro activo" else "$it registros activos" }
            catalogs.addView(Button(this, null, 0).apply {
                text = if (details == null) module.title else "${module.title}\n$details"
                isAllCaps = false
                textSize = 16f
                minHeight = dp(68)
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                setPadding(dp(16), dp(9), dp(16), dp(9))
                setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.ic_chevron, 0)
                compoundDrawablePadding = dp(12)
                setTextColor(getColor(R.color.app_text))
                if (details != null) {
                    text = android.text.SpannableString(text).apply {
                        setSpan(android.text.style.StyleSpan(Typeface.BOLD), 0, module.title.length, 0)
                        setSpan(android.text.style.ForegroundColorSpan(getColor(R.color.app_muted)),
                            module.title.length + 1, length, 0)
                        setSpan(android.text.style.RelativeSizeSpan(0.875f), module.title.length + 1, length, 0)
                    }
                }
                background = controlBackground(R.color.app_surface, R.color.app_accent, 14)
                setOnClickListener { open(module) }
            }, LinearLayout.LayoutParams(-1, -2))
        }
        body.addView(catalogs)
    }

    private fun open(module: Module, create: Boolean = false) {
        startActivity(Intent(this, MaintenanceActivity::class.java)
            .putExtra("module", module.name).putExtra("create", create))
    }
}
