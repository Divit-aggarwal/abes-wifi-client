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

/** The app's design system: colours, type, and small view builders. Plain Android views, no UI library. */
class Ui(private val ctx: Context) {
    val dark = (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    private fun c(light: Long, night: Long) = (if (dark) night else light).toInt()
    val bg = c(0xFFF2F4F8, 0xFF0B0D12)
    val surface = c(0xFFFFFFFF, 0xFF151922)
    val surface2 = c(0xFFEEF1F6, 0xFF1E2430)  // inputs, tonal buttons
    val outline = c(0xFFE3E7EE, 0xFF232A36)
    val text = c(0xFF0F172A, 0xFFF1F5F9)
    val muted = c(0xFF64748B, 0xFF94A3B8)
    val brand = c(0xFF1F5FD1, 0xFF6EA8FF)    // links, accents

    /** A status colour: [light] is the bright top of the gradient, [deep] the bottom, [ink] for text on bg. */
    class Tone(val light: Int, val deep: Int, val ink: Int)
    private fun tone(light: Long, deep: Long, inkLight: Long, inkDark: Long) =
        Tone(light.toInt(), deep.toInt(), c(inkLight, inkDark))
    val blue = tone(0xFF4F8CFF, 0xFF1E4FD8, 0xFF1D4ED8, 0xFF8AB4FF)
    val green = tone(0xFF34D399, 0xFF059669, 0xFF047857, 0xFF6EE7B7)
    val amber = tone(0xFFFBBF24, 0xFFD97706, 0xFFB45309, 0xFFFCD34D)
    val red = tone(0xFFF87171, 0xFFDC2626, 0xFFB91C1C, 0xFFFCA5A5)
    val gray = tone(0xFF94A3B8, 0xFF475569, 0xFF475569, 0xFFCBD5E1)

    fun dp(v: Int) = (v * ctx.resources.displayMetrics.density).toInt()
    fun tint(color: Int, alpha: Float) = Color.argb((alpha * 255).toInt(), Color.red(color), Color.green(color), Color.blue(color))

    fun rounded(color: Int, radius: Int, stroke: Int? = null) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat()
        stroke?.let { setStroke(dp(1), it) }
    }

    fun gradient(top: Int, bottom: Int, radius: Int = 999, oval: Boolean = false,
                 orientation: GradientDrawable.Orientation = GradientDrawable.Orientation.TL_BR) =
        GradientDrawable(orientation, intArrayOf(top, bottom)).apply {
            if (oval) shape = GradientDrawable.OVAL else cornerRadius = dp(radius).toFloat()
        }

    fun oval(color: Int) = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }

    /** App background behind everything, and status/nav bar icons readable on it. */
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
        setLineSpacing(0f, 1.2f)
    }

    fun icon(res: Int, color: Int, size: Int = 24) = ImageView(ctx).apply {
        setImageResource(res); imageTintList = ColorStateList.valueOf(color)
        layoutParams = LinearLayout.LayoutParams(dp(size), dp(size))
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    /** A rounded icon tile, e.g. at the start of a list row. */
    fun iconTile(res: Int, tone: Tone, size: Int = 40) = FrameLayout(ctx).apply {
        background = rounded(tint(tone.light, if (dark) 0.16f else 0.14f), 12)
        addView(icon(res, tone.ink, size / 2), FrameLayout.LayoutParams(dp(size / 2), dp(size / 2), Gravity.CENTER))
        layoutParams = LinearLayout.LayoutParams(dp(size), dp(size))
    }

    fun card() = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(surface, 24, if (dark) null else outline)
        setPadding(dp(20), dp(20), dp(20), dp(20))
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(12) }
    }

    fun sectionTitle(s: String) = label(s, 15f, muted, 500).apply {
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(28); marginStart = dp(6) }
    }

    enum class Style { PRIMARY, TONAL, TEXT }

    /** Pill button. PRIMARY is a blue gradient with white text; [fill]/[fg] override for brand buttons. */
    fun button(s: String, style: Style = Style.PRIMARY, fill: Int? = null, fg: Int? = null, onClick: () -> Unit) =
        Button(ctx).apply {
            text = s; isAllCaps = false; textSize = 16f; stateListAnimator = null; letterSpacing = 0.01f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            minHeight = dp(56); minimumHeight = dp(56)
            val shape = when {
                fill != null -> rounded(fill, 28)
                style == Style.PRIMARY -> gradient(blue.light, blue.deep, 28, orientation = GradientDrawable.Orientation.LEFT_RIGHT)
                style == Style.TONAL -> if (dark) rounded(surface2, 28) else rounded(surface, 28, outline)
                else -> rounded(Color.TRANSPARENT, 28)
            }
            val ink = fg ?: when (style) { Style.PRIMARY -> Color.WHITE; Style.TONAL -> this@Ui.text; Style.TEXT -> brand }
            setTextColor(ink)
            background = RippleDrawable(ColorStateList.valueOf(tint(if (style == Style.PRIMARY || fill != null) Color.WHITE else this@Ui.text, 0.18f)),
                shape, rounded(Color.BLACK, 28))
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(12) }
        }

    fun field(hint: String, secret: Boolean = false) = EditText(ctx).apply {
        this.hint = hint; textSize = 16f; setTextColor(this@Ui.text); setHintTextColor(muted); isSingleLine = true
        inputType = InputType.TYPE_CLASS_TEXT or
            (if (secret) InputType.TYPE_TEXT_VARIATION_PASSWORD else InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS)
        typeface = Typeface.create("sans-serif", Typeface.NORMAL) // password fields default to monospace
        background = rounded(surface2, 16)
        setPadding(dp(18), dp(16), dp(18), dp(16))
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(12) }
    }

    /** A tappable list row: icon tile, title + subtitle, and a trailing status chip or chevron. */
    fun row(iconRes: Int, tone: Tone, title: String, subtitle: String, chip: Pair<String, Tone>?, onClick: () -> Unit) =
        LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12)); minimumHeight = dp(64)
            background = RippleDrawable(ColorStateList.valueOf(tint(text, 0.08f)), null, rounded(Color.BLACK, 16))
            isClickable = true; setOnClickListener { onClick() }
            addView(iconTile(iconRes, tone))
            addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), 0, dp(10), 0)
                addView(label(title, 16f, text, 500))
                addView(label(subtitle, 13f, muted))
            }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            if (chip != null) addView(label(chip.first, 12f, chip.second.ink, 700).apply {
                background = rounded(tint(chip.second.light, if (dark) 0.16f else 0.16f), 999)
                setPadding(dp(12), dp(6), dp(12), dp(6))
            }) else addView(icon(R.drawable.ic_chevron, muted, 20))
        }

    /** Back arrow + title, used by secondary screens. */
    fun topBar(title: String, onBack: () -> Unit) = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(8), dp(8), dp(16), dp(8))
        addView(ImageView(ctx).apply {
            setImageResource(R.drawable.ic_back); imageTintList = ColorStateList.valueOf(text)
            setPadding(dp(12), dp(12), dp(12), dp(12)); contentDescription = "Back"
            background = RippleDrawable(ColorStateList.valueOf(tint(text, 0.1f)), oval(surface), null)
            setOnClickListener { onBack() }
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        addView(label(title, 20f, text, 700).apply { setPadding(dp(14), 0, 0, 0) })
    }

    fun divider() = View(ctx).apply {
        setBackgroundColor(outline)
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, dp(1)).apply { marginStart = dp(66); marginEnd = dp(12) }
    }
}
