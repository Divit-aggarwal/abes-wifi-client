package com.divit.abesclient

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.*
import androidx.core.view.WindowCompat

/** The app's colours, sizes and small view builders. Plain Android views, no UI library. */
class Ui(private val ctx: Context) {
    val dark = (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    private fun c(light: Long, night: Long) = (if (dark) night else light).toInt()
    val bg = c(0xFFF4F6FA, 0xFF0E1116)
    val surface = c(0xFFFFFFFF, 0xFF181C22)
    val outline = c(0xFFE4E8EF, 0xFF2A2F37)
    val text = c(0xFF101828, 0xFFE8EAED)
    val muted = c(0xFF5D6B7E, 0xFF9AA3AE)
    val brand = c(0xFF1E5AA8, 0xFF8AB4F8)   // ABES blue
    val onBrand = c(0xFFFFFFFF, 0xFF0A1A33)
    val green = c(0xFF16803C, 0xFF7DD3A0)
    val amber = c(0xFFB25E00, 0xFFF6C26B)
    val red = c(0xFFC22B22, 0xFFF28B82)

    fun dp(v: Int) = (v * ctx.resources.displayMetrics.density).toInt()
    fun tint(color: Int, alpha: Float) = Color.argb((alpha * 255).toInt(), Color.red(color), Color.green(color), Color.blue(color))

    fun rounded(color: Int, radius: Int, stroke: Int? = null) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat()
        stroke?.let { setStroke(dp(1), it) }
    }

    /** Light status/nav bar icons on light backgrounds, and the app background behind everything. */
    fun applyWindow(activity: Activity) {
        activity.window.decorView.setBackgroundColor(bg)
        @Suppress("DEPRECATION") run { // ignored on Android 15+, which is always edge-to-edge
            activity.window.statusBarColor = bg
            activity.window.navigationBarColor = bg
        }
        WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }

    fun label(s: CharSequence, size: Float, color: Int = text, weight: Int = 400) = TextView(ctx).apply {
        text = s; textSize = size; setTextColor(color)
        typeface = when {
            weight >= 700 -> Typeface.create("sans-serif", Typeface.BOLD)
            weight >= 500 -> Typeface.create("sans-serif-medium", Typeface.NORMAL)
            else -> Typeface.create("sans-serif", Typeface.NORMAL)
        }
        setLineSpacing(0f, 1.15f)
    }

    fun icon(res: Int, color: Int, size: Int = 24) = ImageView(ctx).apply {
        setImageResource(res); imageTintList = ColorStateList.valueOf(color)
        layoutParams = LinearLayout.LayoutParams(dp(size), dp(size))
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun card() = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(surface, 20, outline)
        setPadding(dp(20), dp(20), dp(20), dp(20))
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(16) }
    }

    fun sectionTitle(s: String) = label(s.uppercase(), 12f, muted, 500).apply {
        letterSpacing = 0.08f
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(28); marginStart = dp(4) }
    }

    enum class Style { PRIMARY, TONAL, TEXT }

    fun button(s: String, style: Style = Style.PRIMARY, onClick: () -> Unit) = Button(ctx).apply {
        text = s; isAllCaps = false; textSize = 15f; stateListAnimator = null
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        minHeight = dp(52); minimumHeight = dp(52)
        val (fill, fg) = when (style) {
            Style.PRIMARY -> brand to onBrand
            Style.TONAL -> tint(brand, if (dark) 0.18f else 0.10f) to brand
            Style.TEXT -> Color.TRANSPARENT to brand
        }
        setTextColor(fg)
        background = RippleDrawable(ColorStateList.valueOf(tint(fg, 0.2f)), rounded(fill, 14), rounded(Color.BLACK, 14))
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(12) }
    }

    fun field(hint: String, secret: Boolean = false) = EditText(ctx).apply {
        this.hint = hint; textSize = 16f; setTextColor(this@Ui.text); setHintTextColor(muted); isSingleLine = true
        inputType = InputType.TYPE_CLASS_TEXT or
            (if (secret) InputType.TYPE_TEXT_VARIATION_PASSWORD else InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS)
        typeface = Typeface.create("sans-serif", Typeface.NORMAL) // password fields default to monospace
        background = rounded(tint(this@Ui.text, if (dark) 0.06f else 0.035f), 14, outline)
        setPadding(dp(16), dp(14), dp(16), dp(14))
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(12) }
    }

    /** A tappable settings-style row: icon, title + subtitle, and a trailing chip or chevron. */
    fun row(iconRes: Int, title: String, subtitle: String, chip: Pair<String, Int>?, onClick: () -> Unit) =
        LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(12), dp(4), dp(12)); minimumHeight = dp(56)
            background = RippleDrawable(ColorStateList.valueOf(tint(text, 0.08f)), null, rounded(Color.BLACK, 12))
            isClickable = true; setOnClickListener { onClick() }
            addView(icon(iconRes, muted, 22))
            addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), 0, dp(12), 0)
                addView(label(title, 16f, text, 500))
                addView(label(subtitle, 13f, muted))
            }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            if (chip != null) addView(label(chip.first, 13f, chip.second, 500).apply {
                background = rounded(tint(chip.second, if (dark) 0.18f else 0.12f), 999)
                setPadding(dp(12), dp(5), dp(12), dp(5))
            }) else addView(icon(R.drawable.ic_chevron, muted, 20))
        }

    /** Back arrow + title, used by secondary screens. */
    fun topBar(title: String, onBack: () -> Unit) = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(4), dp(4), dp(16), dp(4))
        addView(ImageView(ctx).apply {
            setImageResource(R.drawable.ic_back); imageTintList = ColorStateList.valueOf(text)
            setPadding(dp(12), dp(12), dp(12), dp(12)); contentDescription = "Back"
            background = RippleDrawable(ColorStateList.valueOf(tint(text, 0.1f)), null, null)
            setOnClickListener { onBack() }
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        addView(label(title, 18f, text, 500).apply { setPadding(dp(8), 0, 0, 0) })
    }

    fun divider() = View(ctx).apply {
        setBackgroundColor(outline)
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, dp(1)).apply { marginStart = dp(42) }
    }
}
