package com.divit.abesclient

import android.animation.ArgbEvaluator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Build
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.animation.PathInterpolator
import android.widget.*
import androidx.core.view.WindowCompat

/**
 * The app's design system. Neutral screens; the only strong colour is the Wi-Fi status, carried by
 * the status card. Type: IBM Plex Sans at four sizes (32/20/16/13) and two weights (400/600).
 * Motion: one easing curve everywhere, no bounce. Plain Android views, no UI library.
 */
class Ui(private val ctx: Context) {
    val dark = (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    private fun c(light: Long, night: Long) = (if (dark) night else light).toInt()
    val bg = c(0xFFF2F3F5, 0xFF0E0F11)
    val surface = c(0xFFFFFFFF, 0xFF17191C)
    val surface2 = c(0xFFECEEF1, 0xFF202327)  // inputs, icon tiles
    val ink = c(0xFF121417, 0xFFF2F3F5)
    val muted = c(0xFF656A72, 0xFF9AA3A8)
    val line = c(0xFFE4E6EA, 0xFF26292E)
    val press = c(0x0F121417, 0x12FFFFFF)

    /** A status colour. */
    class Tone(val color: Int)
    val ok = Tone(c(0xFF1E7F4F, 0xFF4CC38A))
    val warn = Tone(c(0xFFA35F00, 0xFFF0A63A))
    val bad = Tone(c(0xFFC2362B, 0xFFFF7A6E))
    val busy = Tone(c(0xFF2A5BD7, 0xFF7EA2FF))
    val idle = Tone(muted)

    val regular: Typeface = runCatching { ctx.resources.getFont(R.font.plex_regular) }.getOrDefault(Typeface.DEFAULT)
    val semibold: Typeface = runCatching { ctx.resources.getFont(R.font.plex_semibold) }.getOrDefault(Typeface.DEFAULT_BOLD)

    /** The one easing curve: fast out, gentle settle. */
    val ease = PathInterpolator(0.2f, 0.8f, 0.2f, 1f)

    fun dp(v: Int) = (v * ctx.resources.displayMetrics.density).toInt()
    fun dpf(v: Float) = v * ctx.resources.displayMetrics.density
    fun tint(color: Int, alpha: Float) = Color.argb((alpha * 255).toInt(), Color.red(color), Color.green(color), Color.blue(color))
    /** [color] laid over [base] at [amount] (0..1), as an opaque colour. */
    fun mix(color: Int, base: Int, amount: Float) = ArgbEvaluator().evaluate(amount, base, color) as Int

    fun rounded(color: Int, radius: Int, stroke: Int? = null, strokeWidth: Float = 1f) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat()
        stroke?.let { setStroke(dpf(strokeWidth).toInt().coerceAtLeast(1), it) }
    }

    fun oval(color: Int, stroke: Int? = null, strokeWidth: Float = 1f) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL; setColor(color)
        stroke?.let { setStroke(dpf(strokeWidth).toInt().coerceAtLeast(1), it) }
    }

    fun ripple(content: android.graphics.drawable.Drawable?, radius: Int, color: Int = tint(ink, 0.10f)) =
        RippleDrawable(ColorStateList.valueOf(color), content, rounded(Color.BLACK, radius))

    /** App background behind everything, and status/nav bar icons readable on it. */
    fun applyWindow(activity: Activity) {
        val window = activity.window
        WindowCompat.setDecorFitsSystemWindows(window, false) // screens pad themselves with fitsSystemWindows
        window.decorView.setBackgroundColor(bg)
        @Suppress("DEPRECATION") run { // ignored on Android 15+, which is always edge-to-edge
            window.statusBarColor = Color.TRANSPARENT
            window.navigationBarColor = Color.TRANSPARENT
        }
        if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }

    // ---------- Type ----------

    /** Text in the type scale: use 32, 20, 16 or 13 for [size]; [bold] is the 600 weight. */
    fun label(s: CharSequence, size: Float, color: Int = ink, bold: Boolean = false) = TextView(ctx).apply {
        text = s; textSize = size; setTextColor(color)
        typeface = if (bold) semibold else regular
        if (size >= 32f) letterSpacing = -0.015f
        setLineSpacing(0f, if (size >= 20f) 1.15f else 1.3f)
        includeFontPadding = false
    }

    fun icon(res: Int, color: Int, size: Int = 24) = ImageView(ctx).apply {
        setImageResource(res); imageTintList = ColorStateList.valueOf(color)
        layoutParams = LinearLayout.LayoutParams(dp(size), dp(size))
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    /** A small neutral tile holding an icon, at the start of a list row. */
    fun iconTile(res: Int, size: Int = 36) = FrameLayout(ctx).apply {
        background = rounded(surface2, 10)
        addView(icon(res, ink, 20), FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER))
        layoutParams = LinearLayout.LayoutParams(dp(size), dp(size))
    }

    // ---------- Motion ----------

    /** Press feedback: the view (or [target]) shrinks a little while held. Keeps normal clicks working. */
    @SuppressLint("ClickableViewAccessibility")
    fun pressable(v: View, scale: Float = 0.97f, target: View = v) {
        v.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> target.animate().scaleX(scale).scaleY(scale).setDuration(120).setInterpolator(ease).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> target.animate().scaleX(1f).scaleY(1f).setDuration(180).setInterpolator(ease).start()
            }
            false
        }
    }

    /** The screen's load sequence: each group rises 12dp and fades in, one after another, once. */
    fun enter(vararg groups: View) = groups.forEachIndexed { i, v ->
        v.alpha = 0f; v.translationY = dpf(12f)
        v.animate().alpha(1f).translationY(0f).setStartDelay(60L + i * 80L).setDuration(500).setInterpolator(ease).start()
    }

    /** Fades [v]'s content out, runs [change], then fades it back in: used when the status changes. */
    fun swap(v: View, change: () -> Unit) {
        v.animate().cancel()
        v.animate().alpha(0f).translationY(dpf(6f)).setDuration(160).setInterpolator(ease).withEndAction {
            change()
            v.animate().alpha(1f).translationY(0f).setDuration(220).setInterpolator(ease).start()
        }.start()
    }

    /** A short side-to-side shake, for a field left empty. */
    fun shake(v: View) = ObjectAnimator.ofFloat(v, View.TRANSLATION_X, 0f, -dpf(5f), dpf(5f), -dpf(3f), 0f).apply {
        duration = 300; interpolator = ease; start()
    }

    // ---------- Surfaces ----------

    fun card() = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(surface, 20)
        clipToOutline = true
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
    }

    fun sectionTitle(s: String) = label(s, 13f, muted, bold = true).apply {
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
            topMargin = dp(40); bottomMargin = dp(8); marginStart = dp(8)
        }
    }

    // ---------- Buttons ----------

    enum class Style { PRIMARY, SECONDARY, QUIET }

    /** A button that can show a spinner while its action runs (see [busy]). */
    inner class Btn(text: String, val style: Style, small: Boolean) : LinearLayout(ctx) {
        val text: TextView = label(text, if (small) 13f else 16f, fg(), bold = true)
        private val spinner = ProgressBar(ctx).apply {
            isIndeterminate = true; indeterminateTintList = ColorStateList.valueOf(fg()); visibility = GONE
        }
        init {
            orientation = HORIZONTAL; gravity = Gravity.CENTER
            val radius = if (small) 10 else 14
            minimumHeight = dp(if (small) 40 else if (style == Style.QUIET) 44 else 52)
            setPadding(dp(if (small) 14 else 20), 0, dp(if (small) 14 else 20), 0)
            val shape = when (style) {
                Style.PRIMARY -> rounded(ink, radius)
                Style.SECONDARY -> rounded(surface, radius, line)
                Style.QUIET -> null
            }
            background = ripple(shape, radius, tint(if (style == Style.PRIMARY) bg else ink, 0.16f))
            if (style == Style.PRIMARY) { elevation = dpf(2f); if (Build.VERSION.SDK_INT >= 28) outlineSpotShadowColor = ink }
            addView(spinner, LayoutParams(dp(18), dp(18)).apply { marginEnd = dp(10) })
            addView(this.text)
            isClickable = true; isFocusable = true
            contentDescription = text
            pressable(this)
        }
        private fun fg() = if (style == Style.PRIMARY) bg else ink

        /** Show a spinner and [label] until the screen redraws this button. */
        fun busy(label: String) {
            text.text = label; spinner.visibility = VISIBLE; isEnabled = false
            text.alpha = 0f; text.animate().alpha(1f).setDuration(160).start()
        }
    }

    fun button(s: String, style: Style = Style.PRIMARY, small: Boolean = false, onClick: (Btn) -> Unit) =
        Btn(s, style, small).apply {
            setOnClickListener { onClick(this) }
            layoutParams = LinearLayout.LayoutParams(if (small) WRAP_CONTENT else MATCH_PARENT, WRAP_CONTENT)
        }

    /** A round icon button with no fill, e.g. Back and About. */
    fun iconButton(res: Int, description: String, onClick: () -> Unit) = ImageView(ctx).apply {
        setImageResource(res); imageTintList = ColorStateList.valueOf(ink)
        setPadding(dp(11), dp(11), dp(11), dp(11)); contentDescription = description
        background = ripple(null, 12)
        setOnClickListener { onClick() }
        pressable(this, 0.92f)
        layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
    }

    /** A text link that shades and shrinks a little when pressed, e.g. "Change". */
    fun textButton(s: String, size: Float = 16f, onClick: () -> Unit) = label(s, size, ink, bold = true).apply {
        setPadding(dp(12), dp(10), dp(12), dp(10))
        background = ripple(null, 10)
        isClickable = true; setOnClickListener { onClick() }
        pressable(this, 0.95f)
    }

    // ---------- Inputs ----------

    fun field(hint: String, secret: Boolean = false) = EditText(ctx).apply {
        this.hint = hint; textSize = 16f; setTextColor(ink); setHintTextColor(muted); isSingleLine = true
        inputType = InputType.TYPE_CLASS_TEXT or
            (if (secret) InputType.TYPE_TEXT_VARIATION_PASSWORD else InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS)
        typeface = regular // password fields default to monospace
        minimumHeight = dp(52)
        setPadding(dp(16), dp(14), dp(if (secret) 56 else 16), dp(14))
        highlightColor = tint(busy.color, 0.3f)
        if (Build.VERSION.SDK_INT >= 29) textCursorDrawable = rounded(ink, 1).apply { setSize(dp(2), dp(20)) }
        setFieldState(this, error = false)
        layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
    }

    /** Normal fill, or a red edge for a field left empty. Focus shows an ink edge. */
    fun setFieldState(f: EditText, error: Boolean) {
        f.background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), rounded(surface, 12, if (error) bad.color else ink, 1.5f))
            addState(intArrayOf(), rounded(surface2, 12, if (error) bad.color else null, 1.5f))
        }
    }

    // ---------- Rows ----------

    /** A small status tag for a list row: "On" in green with a dot, or "Fix" in amber. */
    fun stateTag(s: String, tone: Tone, dot: Boolean) = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        background = rounded(tint(tone.color, if (dark) 0.16f else 0.12f), 6)
        setPadding(dp(8), dp(4), dp(8), dp(4))
        if (dot) addView(View(ctx).apply { background = oval(tone.color) }, LinearLayout.LayoutParams(dp(6), dp(6)).apply { marginEnd = dp(6) })
        addView(label(s, 13f, tone.color, bold = true))
    }

    /** A tappable list row: icon tile, title + subtitle, and a trailing tag or arrow. */
    fun row(iconRes: Int, title: String, subtitle: String?, trailing: View? = null, trailIcon: Int = R.drawable.ic_chevron,
            onClick: (() -> Unit)?) = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(16), dp(12), dp(16), dp(12)); minimumHeight = dp(64)
        val tile = iconTile(iconRes)
        addView(tile)
        addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), 0, dp(10), 0)
            addView(label(title, 16f, ink))
            subtitle?.let { addView(label(it, 13f, muted).apply { setPadding(0, dp(2), 0, 0) }) }
        }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        val arrow = if (trailing == null && onClick != null) icon(trailIcon, muted, 18) else null
        (trailing ?: arrow)?.let { addView(it) }
        if (onClick != null) {
            background = ripple(null, 0)
            isClickable = true; setOnClickListener { onClick() }
            @SuppressLint("ClickableViewAccessibility")
            setOnTouchListener { _, e -> // the tile squeezes and the arrow nudges right while held
                val down = e.actionMasked == MotionEvent.ACTION_DOWN
                if (down || e.actionMasked == MotionEvent.ACTION_UP || e.actionMasked == MotionEvent.ACTION_CANCEL) {
                    tile.animate().scaleX(if (down) 0.92f else 1f).scaleY(if (down) 0.92f else 1f).setDuration(140).setInterpolator(ease).start()
                    arrow?.animate()?.translationX(if (down) dpf(3f) else 0f)?.setDuration(200)?.setInterpolator(ease)?.start()
                }
                false
            }
        }
    }

    /** The thin line between rows, inset past the icon tile. */
    fun divider() = View(ctx).apply {
        setBackgroundColor(line)
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, dp(1)).apply { marginStart = dp(66); marginEnd = dp(16) }
    }

    /** Back arrow + title, used by secondary screens. */
    fun topBar(title: String, onBack: () -> Unit) = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(8), dp(4), dp(20), dp(4))
        addView(iconButton(R.drawable.ic_back, "Back", onBack))
        addView(label(title, 20f, ink, bold = true).apply { setPadding(dp(4), 0, 0, 0) })
    }

    // ---------- Feedback ----------

    /** A dark pill that slides up from the bottom of [host] for two seconds. */
    fun toast(host: FrameLayout, message: String, iconRes: Int = R.drawable.ic_check) {
        val pill = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = rounded(ink, 14)
            setPadding(dp(16), dp(14), dp(16), dp(14))
            elevation = dpf(6f)
            addView(icon(iconRes, bg, 18))
            addView(label(message, 13f, bg, bold = true).apply { setPadding(dp(10), 0, 0, 0) })
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        val bottomInset = host.rootWindowInsets?.systemWindowInsetBottom ?: 0
        host.addView(pill, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.BOTTOM).apply {
            setMargins(dp(20), 0, dp(20), dp(24) + bottomInset)
        })
        pill.alpha = 0f; pill.translationY = dpf(80f)
        pill.animate().alpha(1f).translationY(0f).setDuration(350).setInterpolator(ease).start()
        pill.postDelayed({
            pill.animate().alpha(0f).translationY(dpf(80f)).setDuration(300).setInterpolator(ease)
                .withEndAction { (pill.parent as? ViewGroup)?.removeView(pill) }.start()
        }, 2000)
    }

    /** Animates a GradientDrawable's fill from its current colour to [to]. */
    fun fadeFill(d: GradientDrawable, from: Int, to: Int, ms: Long = 450) =
        ValueAnimator.ofObject(ArgbEvaluator(), from, to).apply {
            duration = ms; interpolator = ease
            addUpdateListener { d.setColor(it.animatedValue as Int) }
            start()
        }
}

/**
 * The Wi-Fi signal on the status card: three arcs and a dot, drawn in the status colour.
 * [Mode.ON] fills the arcs one by one, [Mode.SEARCHING] pulses them in turn, [Mode.BROKEN] strikes
 * them through, [Mode.OFF] leaves them dim.
 */
class SignalView(ctx: Context) : View(ctx) {
    enum class Mode { OFF, SEARCHING, ON, BROKEN }

    private val ease = PathInterpolator(0.2f, 0.8f, 0.2f, 1f)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val arc = floatArrayOf(0.18f, 0.18f, 0.18f) // opacity of each arc, inner to outer
    private var slash = 0f                               // 0..1, how much of the strike line is drawn
    private var anims = mutableListOf<ValueAnimator>()
    var color = Color.GRAY
        set(v) { field = v; invalidate() }
    var mode = Mode.OFF
        private set

    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }

    fun show(next: Mode, animate: Boolean = true) {
        if (next == mode && anims.isNotEmpty()) return
        mode = next
        anims.forEach { it.cancel() }; anims.clear()
        val target = when (next) { Mode.ON -> 1f; Mode.BROKEN -> 0.35f; else -> 0.18f }
        for (i in 0..2) {
            if (next == Mode.SEARCHING) anims += ValueAnimator.ofFloat(0.18f, 1f, 0.18f).apply {
                duration = 1200; startDelay = i * 150L; repeatCount = ValueAnimator.INFINITE; interpolator = ease
                addUpdateListener { arc[i] = it.animatedValue as Float; invalidate() }
            }
            else anims += ValueAnimator.ofFloat(arc[i], target).apply {
                duration = if (animate) 400 else 0; startDelay = if (next == Mode.ON && animate) 50L + i * 100L else 0; interpolator = ease
                addUpdateListener { arc[i] = it.animatedValue as Float; invalidate() }
            }
        }
        anims += ValueAnimator.ofFloat(slash, if (next == Mode.BROKEN) 1f else 0f).apply {
            duration = if (animate) 400 else 0; interpolator = ease
            addUpdateListener { slash = it.animatedValue as Float; invalidate() }
        }
        anims.forEach { it.start() }
    }

    override fun onDetachedFromWindow() { anims.forEach { it.cancel() }; super.onDetachedFromWindow() }

    override fun onDraw(canvas: Canvas) {
        val s = width / 72f // drawn on a 72-unit grid
        paint.strokeWidth = 5f * s
        val radii = floatArrayOf(11f, 24f, 37f)
        for (i in 0..2) {
            paint.color = color; paint.alpha = (arc[i] * 255).toInt()
            val r = radii[i] * s
            // arcs centred on the dot, opening upwards
            canvas.drawArc(RectF(36f * s - r, 57f * s - r, 36f * s + r, 57f * s + r), 225f, 90f, false, paint)
        }
        fill.color = color
        canvas.drawCircle(36f * s, 57f * s, 3.5f * s, fill)
        if (slash > 0f) {
            paint.color = color; paint.alpha = 255
            canvas.drawLine(14f * s, 14f * s, (14f + 44f * slash) * s, (14f + 44f * slash) * s, paint)
        }
    }
}
