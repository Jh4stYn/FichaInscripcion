package pe.unsa.inscripcion.ui

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import pe.unsa.inscripcion.R
import pe.unsa.inscripcion.data.Database
import pe.unsa.inscripcion.data.Repository
import kotlin.math.roundToInt

open class BaseActivity : Activity() {
    protected lateinit var repository: Repository
    protected lateinit var body: LinearLayout
    protected lateinit var stage: FrameLayout
    protected lateinit var footer: LinearLayout

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = Repository(Database(this))
        setContentView(R.layout.shell)
        body = findViewById(R.id.content)
        stage = findViewById(R.id.stage)
        footer = findViewById(R.id.footer)
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        val root = findViewById<View>(R.id.root)
        root.setOnApplyWindowInsetsListener { view, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val padding = insets.getInsets(
                    WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout() or WindowInsets.Type.ime()
                )
                val gutter = ((view.width - padding.left - padding.right - dp(680)) / 2).coerceAtLeast(0)
                view.setPadding(padding.left + gutter, padding.top, padding.right + gutter, padding.bottom)
            } else {
                val left = insets.systemWindowInsetLeft
                val right = insets.systemWindowInsetRight
                val gutter = ((view.width - left - right - dp(680)) / 2).coerceAtLeast(0)
                view.setPadding(left + gutter, insets.systemWindowInsetTop, right + gutter, insets.systemWindowInsetBottom)
            }
            insets
        }
        root.addOnLayoutChangeListener { view, left, _, right, _, oldLeft, _, oldRight, _ ->
            if (right - left != oldRight - oldLeft) view.requestApplyInsets()
        }
        findViewById<View>(R.id.back).setOnClickListener { onBackPressed() }
    }

    protected fun heading(title: String, subtitle: String = "") {
        findViewById<TextView>(R.id.title).text = title
        findViewById<TextView>(R.id.subtitle).apply {
            text = subtitle
            visibility = if (subtitle.isEmpty()) View.GONE else View.VISIBLE
        }
    }

    protected fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()

    protected fun label(text: String, size: Float = 16f, color: Int = R.color.app_text, bold: Boolean = false) =
        TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(getColor(color))
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        }

    protected fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundResource(R.drawable.card)
        setPadding(dp(16), dp(16), dp(16), dp(16))
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) }
    }

    protected fun action(text: String, primary: Boolean = false) = Button(this, null, 0).apply {
        this.text = text
        isAllCaps = false
        textSize = 15f
        minHeight = dp(48)
        gravity = Gravity.CENTER
        setTextColor(getColor(if (primary) R.color.app_surface else R.color.app_accent))
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        background = actionBackground(primary)
        setPadding(dp(12), dp(9), dp(12), dp(9))
        layoutParams = LinearLayout.LayoutParams(-1, -2)
    }

    protected fun actionBackground(primary: Boolean): RippleDrawable {
        val base = if (primary) R.color.app_accent else R.color.app_accent_light
        val focus = if (primary) R.color.app_on_hero else R.color.app_accent
        return controlBackground(base, focus, 12)
    }

    protected fun controlBackground(base: Int, focus: Int, radius: Int): RippleDrawable {
        fun shape(focused: Boolean) = GradientDrawable().apply {
            setColor(getColor(base))
            cornerRadius = dp(radius).toFloat()
            if (focused) setStroke(dp(2), getColor(focus))
        }
        val states = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), shape(true))
            addState(intArrayOf(), shape(false))
        }
        return RippleDrawable(ColorStateList.valueOf(0x2450338C), states, null)
    }

    protected fun searchField(hint: String, focusResults: () -> Unit) = EditText(this).apply {
        id = R.id.search
        isSaveEnabled = false
        this.hint = hint
        setSingleLine()
        textSize = 16f
        minHeight = dp(48)
        setBackgroundResource(R.drawable.field)
        imeOptions = EditorInfo.IME_ACTION_SEARCH
        setOnEditorActionListener { _, action, _ ->
            if (action != EditorInfo.IME_ACTION_SEARCH) return@setOnEditorActionListener false
            hideKeyboard()
            focusResults()
            true
        }
    }

    protected fun setFooter(vararg buttons: Button) {
        footer.removeAllViews()
        val visibility = if (buttons.isEmpty()) View.GONE else View.VISIBLE
        footer.visibility = visibility
        findViewById<View>(R.id.footer_divider).visibility = visibility
        buttons.forEachIndexed { index, button ->
            footer.addView(button, LinearLayout.LayoutParams(0, -2, 1f).apply {
                if (index > 0) marginStart = dp(8)
            })
        }
    }

    protected fun hideKeyboard() {
        val field = currentFocus ?: return
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(field.windowToken, 0)
        field.clearFocus()
    }

    override fun onDestroy() {
        repository.helper.close()
        super.onDestroy()
    }
}
