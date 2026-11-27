package com.divit.abesclient

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.divit.abesclient.Ui.Style
import com.divit.abesclient.Ui.Tone
import com.divit.abesclient.SignalView.Mode

class MainActivity : Activity() {
    private lateinit var ui: Ui
    private lateinit var host: FrameLayout
    private lateinit var scroll: ScrollView
    private lateinit var statusCard: LinearLayout
    private lateinit var statusFill: GradientDrawable
    private lateinit var statusContent: LinearLayout
    private lateinit var signal: SignalView
    private lateinit var accountCard: LinearLayout
    private lateinit var checksCard: LinearLayout
    private var shownState: String? = null
    private var shownTone: Tone? = null
    private var editing = false
    private var lastChecks: Pair<Boolean, Boolean>? = null
    private val running = mutableListOf<ValueAnimator>()
    private val onStatus: () -> Unit = { renderStatus() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.getBooleanExtra("open_portal", false)) {
            startActivity(Intent(this, PortalActivity::class.java)); finish(); return
        }
        ui = Ui(this)
        ui.applyWindow(this)
        editing = !hasCredentials()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(20), ui.dp(4), ui.dp(20), ui.dp(40))
            isFocusableInTouchMode = true // takes first focus, so the screen opens at the top, not at a field
        }
        val head = header()
        root.addView(head)
        statusCard = buildStatusCard()
        root.addView(statusCard)
        accountCard = ui.card()
        val account = group(ui.sectionTitle("Account"), accountCard)
        root.addView(account)
        checksCard = ui.card()
        val checks = group(ui.sectionTitle("Keep it working"), checksCard, footer())
        root.addView(checks)
        // targetSdk 35: Android 15 draws behind the status/nav bars; pad content clear of them.
        scroll = ScrollView(this).apply { fitsSystemWindows = true; isFillViewport = true; addView(root) }
        host = FrameLayout(this).apply { addView(scroll) }
        setContentView(host)

        renderAccount()
        ui.enter(head, statusCard, account, checks)
        if (hasCredentials()) WifiMonitorService.start(this) // keep monitoring running whenever the app is opened
    }

    override fun onResume() {
        super.onResume()
        MonitorStatus.listen(onStatus)
        renderStatus()
        renderChecks() // settings may have changed while we were away
    }

    override fun onPause() {
        MonitorStatus.unlisten(onStatus)
        super.onPause()
    }

    override fun onDestroy() {
        running.forEach { it.cancel() }
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra("open_portal", false)) openPortal()
    }

    private fun group(vararg views: View) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        views.forEach { addView(it) }
    }

    // ---------- Header ----------

    private fun header() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        minimumHeight = ui.dp(56)
        setPadding(ui.dp(4), 0, 0, 0)
        addView(ui.label(getString(R.string.app_name), 20f, ui.ink, bold = true), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        addView(ui.iconButton(R.drawable.ic_info, "About the app's makers") {
            startActivity(Intent(this@MainActivity, AboutActivity::class.java))
        }.apply { (layoutParams as LinearLayout.LayoutParams).marginEnd = -ui.dp(10) })
    }

    // ---------- Status card ----------

    private class Status(
        val tone: Tone, val signal: SignalView.Mode, val line: String, val title: String, val body: String,
        val portal: String? = null, val busy: Boolean = false, val peak: Boolean = false,
        val actions: List<Triple<String, Style, (Ui.Btn) -> Unit>> = emptyList()
    )

    private fun buildStatusCard() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        statusFill = ui.rounded(ui.surface, 24)
        background = statusFill
        setPadding(ui.dp(20), ui.dp(24), ui.dp(20), ui.dp(20))
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = ui.dp(8) }
        signal = SignalView(this@MainActivity)
        statusContent = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
        addView(statusContent)
    }

    private fun currentStatus(): Status {
        val usePage = Triple<String, Style, (Ui.Btn) -> Unit>("Use the login page instead", Style.QUIET) { openPortal() }
        val waiting = "You'll be signed in automatically as soon as you join campus Wi-Fi."
        val portal = "Portal ${Portal.HOST}"
        if (!hasCredentials()) return Status(ui.idle, Mode.OFF, "Set up", "Sign in once",
            "Save your college login below. After that, the app signs you in every time you join campus Wi-Fi.")
        return when (MonitorStatus.state) {
            "signed_in" -> Status(ui.ok, Mode.ON, "Connected", "You're online", "Signed in to campus Wi-Fi as ${SecurePrefs.username(this)}.",
                portal = "$portal · certificate verified", peak = true,
                actions = listOf(Triple("Sign out", Style.SECONDARY) { b -> b.busy("Signing out"); signOut() }))
            "online" -> Status(ui.ok, Mode.ON, "Connected", "You're online", "Signed in to campus Wi-Fi as ${SecurePrefs.username(this)}.",
                portal = portal, peak = true,
                actions = listOf(Triple("Sign out", Style.SECONDARY) { b -> b.busy("Signing out"); signOut() }))
            "signing_in" -> Status(ui.busy, Mode.SEARCHING, "Signing in", "Signing you in",
                "Sending your saved login to the campus login portal.", portal = portal, busy = true)
            "wrong_password" -> Status(ui.warn, Mode.BROKEN, "Action needed", "Wrong password",
                "The campus portal didn't accept your college ID or password. The app won't try again until you update them.",
                actions = listOf(Triple("Update login details", Style.PRIMARY) { _ -> startEditing() }, usePage))
            "failed", "needs_login" -> {
                val detail = MonitorStatus.message.substringAfter("failed (", "").substringBefore(")")
                Status(ui.warn, Mode.BROKEN, "Action needed", "Couldn't sign you in",
                    if (detail.isNotBlank()) "The login page said: ${detail.replaceFirstChar { it.uppercase() }}." else "The login page didn't respond as expected.",
                    actions = listOf(Triple("Try again", Style.PRIMARY) { b -> b.busy("Starting"); retry() }, usePage))
            }
            "signout_failed" -> Status(ui.warn, Mode.BROKEN, "Action needed", "Couldn't sign out",
                "Make sure you're connected to campus Wi-Fi, then try again.",
                actions = listOf(Triple("Try again", Style.PRIMARY) { b -> b.busy("Signing out"); signOut() }))
            "untrusted" -> Status(ui.bad, Mode.BROKEN, "Blocked", "Fake login page",
                "This network answers at the campus portal's address, but its security certificate doesn't match. Your password was not sent.",
                portal = "Certificate mismatch at ${Portal.HOST}")
            "signed_out" -> Status(ui.idle, Mode.OFF, "Signed out", "You're signed out",
                "You'll be signed in again the next time you connect.",
                actions = listOf(Triple("Sign in now", Style.PRIMARY) { b -> b.busy("Starting"); retry() }))
            "other" -> Status(ui.idle, Mode.OFF, "Waiting", "Not on campus Wi-Fi", waiting)
            "lost" -> Status(ui.idle, Mode.OFF, "Offline", "Wi-Fi is off", waiting)
            else -> Status(ui.idle, Mode.SEARCHING, "Waiting", "Looking for campus Wi-Fi", waiting)
        }
    }

    /** Redraws the status card. A change of state cross-fades the card's tint and swaps the text. */
    private fun renderStatus() {
        val s = currentStatus()
        val key = s.title + s.body + s.actions.size
        val first = shownState == null
        if (key == shownState) return
        val previous = shownState
        shownState = key

        val target = ui.mix(s.tone.color, ui.surface, if (ui.dark) 0.12f else 0.09f)
        val from = shownTone?.let { ui.mix(it.color, ui.surface, if (ui.dark) 0.12f else 0.09f) } ?: target
        if (first) statusFill.setColor(target) else ui.fadeFill(statusFill, from, target)
        shownTone = s.tone
        signal.color = s.tone.color
        signal.show(s.signal, animate = !first)

        if (first) fillStatus(s) else ui.swap(statusContent) { fillStatus(s) }
        // a small confirmation when the user's own action (or a sign-in) finishes while they watch
        if (previous != null) when (MonitorStatus.state) {
            "signed_in" -> ui.toast(host, "Signed in to campus Wi-Fi")
            "signed_out" -> ui.toast(host, "Signed out of campus Wi-Fi", R.drawable.ic_logout)
        }
    }

    private fun fillStatus(s: Status) {
        running.forEach { it.cancel() }; running.clear()
        statusContent.removeAllViews()
        (signal.parent as? android.view.ViewGroup)?.removeView(signal)
        statusContent.addView(LinearLayout(this).apply { // status tag on the left, the signal on the right
            orientation = LinearLayout.HORIZONTAL
            addView(statusTag(s), LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { gravity = Gravity.TOP })
            addView(Space(this@MainActivity), LinearLayout.LayoutParams(0, 0, 1f))
            addView(signal, LinearLayout.LayoutParams(ui.dp(72), ui.dp(72)).apply { marginStart = ui.dp(16) })
        })
        statusContent.addView(ui.label(s.title, 32f, ui.ink, bold = true).apply {
            setPadding(0, ui.dp(12), 0, 0)
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE // read out status changes
        })
        statusContent.addView(ui.label(s.body, 16f, ui.muted).apply { setPadding(0, ui.dp(8), 0, 0) })
        if (s.busy) statusContent.addView(progressLine(s.tone))
        s.portal?.let { p ->
            statusContent.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(0, ui.dp(16), 0, 0)
                addView(ui.icon(R.drawable.ic_lock, ui.muted, 14))
                addView(ui.label(p, 13f, ui.muted).apply { setPadding(ui.dp(6), 0, 0, 0) })
            })
        }
        if (s.actions.isNotEmpty()) statusContent.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, ui.dp(24), 0, 0)
            s.actions.forEachIndexed { i, (text, style, action) ->
                addView(ui.button(text, style) { action(it) }.apply {
                    if (i > 0) (layoutParams as LinearLayout.LayoutParams).topMargin = ui.dp(8)
                })
            }
        })
    }

    /** "Connected" with a dot (or a check that draws in when you come online), on a soft tint. */
    private fun statusTag(s: Status) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        background = ui.rounded(ui.tint(s.tone.color, if (ui.dark) 0.18f else 0.12f), 8)
        setPadding(ui.dp(10), ui.dp(6), ui.dp(10), ui.dp(6))
        val mark = FrameLayout(this@MainActivity)
        if (s.peak) {
            val ring = View(this@MainActivity).apply { background = ui.oval(android.graphics.Color.TRANSPARENT, s.tone.color, 2f); alpha = 0f }
            val check = ui.icon(R.drawable.ic_check, s.tone.color, 14).apply { scaleX = 0.4f; scaleY = 0.4f; alpha = 0f }
            mark.addView(ring, FrameLayout.LayoutParams(ui.dp(14), ui.dp(14), Gravity.CENTER))
            mark.addView(check, FrameLayout.LayoutParams(ui.dp(14), ui.dp(14), Gravity.CENTER))
            check.animate().scaleX(1f).scaleY(1f).alpha(1f).setStartDelay(150).setDuration(450).setInterpolator(ui.ease).start()
            ring.scaleX = 0.6f; ring.scaleY = 0.6f; ring.alpha = 0.6f
            ring.animate().scaleX(1.9f).scaleY(1.9f).alpha(0f).setStartDelay(100).setDuration(800).setInterpolator(ui.ease).start()
        } else {
            val dot = View(this@MainActivity).apply { background = ui.oval(s.tone.color) }
            mark.addView(dot, FrameLayout.LayoutParams(ui.dp(8), ui.dp(8), Gravity.CENTER))
            if (s.busy) running += ObjectAnimator.ofFloat(dot, View.ALPHA, 1f, 0.25f).apply {
                duration = 500; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.REVERSE; start()
            }
        }
        addView(mark, LinearLayout.LayoutParams(ui.dp(14), ui.dp(14)).apply { marginEnd = ui.dp(8) })
        addView(ui.label(s.line, 13f, s.tone.color, bold = true))
    }

    /** A thin bar with a segment sliding across, while signing in. */
    private fun progressLine(tone: Tone) = FrameLayout(this).apply {
        background = ui.rounded(ui.tint(tone.color, 0.2f), 2)
        clipToOutline = true
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, ui.dp(3)).apply { topMargin = ui.dp(16) }
        val seg = View(this@MainActivity).apply { background = ui.rounded(tone.color, 2) }
        addView(seg, FrameLayout.LayoutParams(0, MATCH_PARENT))
        post {
            val w = width.toFloat()
            seg.layoutParams.width = (w * 0.35f).toInt(); seg.requestLayout()
            running += ObjectAnimator.ofFloat(seg, View.TRANSLATION_X, -w * 0.35f, w).apply {
                duration = 1200; repeatCount = ValueAnimator.INFINITE; interpolator = ui.ease; start()
            }
        }
    }

    // ---------- Account ----------

    private fun renderAccount() {
        accountCard.removeAllViews()
        if (!editing) {
            accountCard.addView(ui.row(R.drawable.ic_lock, SecurePrefs.username(this), "Password saved on this phone",
                trailing = ui.textButton("Change") { startEditing() }, onClick = null))
            return
        }
        val firstTime = !hasCredentials()
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(16), ui.dp(24), ui.dp(16), ui.dp(16))
        }
        form.addView(ui.label(if (firstTime) "Your college login" else "Update login details", 20f, ui.ink, bold = true))
        form.addView(ui.label("The same college ID and password you type on the campus Wi-Fi page.", 13f, ui.muted)
            .apply { setPadding(0, ui.dp(4), 0, ui.dp(4)) })
        val user = ui.field("College ID").apply { setText(SecurePrefs.username(this@MainActivity)) }
        val pass = ui.field("Password", secret = true).apply { setText(SecurePrefs.password(this@MainActivity)) }
        val userErr = errorLabel(); val passErr = errorLabel()
        val eye = ImageView(this).apply {
            setImageResource(R.drawable.ic_eye); imageTintList = android.content.res.ColorStateList.valueOf(ui.muted)
            setPadding(ui.dp(12), ui.dp(12), ui.dp(12), ui.dp(12))
            background = ui.ripple(null, 10)
            contentDescription = "Show password"
            setOnClickListener {
                val hidden = pass.inputType and InputType.TYPE_TEXT_VARIATION_PASSWORD == InputType.TYPE_TEXT_VARIATION_PASSWORD
                pass.inputType = InputType.TYPE_CLASS_TEXT or
                    (if (hidden) InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD else InputType.TYPE_TEXT_VARIATION_PASSWORD)
                pass.typeface = ui.regular
                pass.setSelection(pass.text.length)
                contentDescription = if (hidden) "Hide password" else "Show password"
                animate().alpha(0f).setDuration(90).withEndAction { // the icon swaps with a quick fade
                    setImageResource(if (hidden) R.drawable.ic_eye_off else R.drawable.ic_eye)
                    animate().alpha(1f).setDuration(140).start()
                }.start()
            }
            ui.pressable(this, 0.9f)
        }
        form.addView(fieldBox(user)); form.addView(userErr)
        form.addView(fieldBox(pass, eye)); form.addView(passErr)
        listOf(user to userErr, pass to passErr).forEach { (f, err) ->
            f.addTextChangedListener(object : android.text.TextWatcher {
                override fun afterTextChanged(e: android.text.Editable?) { if (err.visibility == View.VISIBLE) { err.visibility = View.GONE; ui.setFieldState(f, false) } }
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            })
        }
        form.addView(ui.button(if (firstTime) "Save and turn on" else "Save") { btn ->
            val u = user.text.toString().trim()
            val p = pass.text.toString()
            var missing = false
            for ((f, err, msg) in listOf(Triple(user, userErr, "Enter your college ID"), Triple(pass, passErr, "Enter your password"))) {
                if (f.text.toString().isBlank()) {
                    missing = true; err.text = msg; err.visibility = View.VISIBLE
                    ui.setFieldState(f, true); ui.shake(f)
                }
            }
            if (missing) { (if (u.isEmpty()) user else pass).requestFocus(); return@button }
            btn.busy("Saving")
            SecurePrefs.save(this, u, p)
            hideKeyboard()
            btn.postDelayed({
                editing = false
                renderAccount(); renderStatus()
                requestNotificationsThenStart()
            }, 350)
        }.apply { (layoutParams as LinearLayout.LayoutParams).topMargin = ui.dp(24) })
        if (!firstTime) form.addView(ui.button("Cancel", Style.QUIET) {
            editing = false; hideKeyboard(); renderAccount()
        }.apply { (layoutParams as LinearLayout.LayoutParams).topMargin = ui.dp(4) })
        accountCard.addView(form)
    }

    private fun fieldBox(field: EditText, trailing: View? = null) = FrameLayout(this).apply {
        addView(field)
        trailing?.let { addView(it, FrameLayout.LayoutParams(ui.dp(44), ui.dp(44), Gravity.END or Gravity.CENTER_VERTICAL).apply { marginEnd = ui.dp(4) }) }
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = ui.dp(12) }
    }

    private fun errorLabel() = ui.label("", 13f, ui.bad.color).apply {
        visibility = View.GONE; setPadding(ui.dp(4), ui.dp(6), 0, 0)
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
    }

    private fun startEditing() {
        editing = true
        renderAccount()
        accountCard.post { scroll.smoothScrollTo(0, (accountCard.parent as View).top) }
    }

    // ---------- Keep it working ----------

    private fun renderChecks() {
        checksCard.removeAllViews()
        val notifOn = NotificationManagerCompat.from(this).areNotificationsEnabled()
        val bgOn = (getSystemService(POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(packageName)
        val on = { ui.stateTag("On", ui.ok, dot = true) }
        val fix = { ui.stateTag("Fix", ui.warn, dot = false) }
        checksCard.addView(ui.row(R.drawable.ic_bell, "Notifications",
            if (notifOn) "Shows when you're signed in" else "Turn on to see when you're signed in",
            if (notifOn) on() else fix()) { openNotificationSettings() })
        checksCard.addView(ui.divider())
        checksCard.addView(ui.row(R.drawable.ic_battery, "Run in background",
            if (bgOn) "Keeps automatic sign-in working" else "Your phone may stop automatic sign-in",
            if (bgOn) on() else fix()) { if (bgOn) openAppSettings() else askToRunInBackground() })
        if (Build.MANUFACTURER.lowercase() in AUTOSTART_BRANDS) {
            checksCard.addView(ui.divider())
            checksCard.addView(ui.row(R.drawable.ic_power, "Autostart",
                "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} phones need this after a restart") { openAppSettings() })
        }
        checksCard.addView(ui.divider())
        checksCard.addView(ui.row(R.drawable.ic_open, "Open login page", "Sign in by hand if needed", onClick = ::openPortal))
        // came back from Settings with something fixed: say so
        lastChecks?.let { (n, b) ->
            if (!b && bgOn) ui.toast(host, "Background running allowed")
            else if (!n && notifOn) ui.toast(host, "Notifications turned on")
        }
        lastChecks = notifOn to bgOn
    }

    // ---------- Footer ----------

    private fun footer() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(ui.dp(8), ui.dp(40), ui.dp(8), 0)
        addView(ui.label("Your password is encrypted on this phone and only sent to the campus portal.", 13f, ui.muted))
        addView(ui.textButton("Made by ${People.DEVELOPER.name} and ${People.DESIGNER.name}", 13f) {
            startActivity(Intent(this@MainActivity, AboutActivity::class.java))
        }.apply { setPadding(0, ui.dp(10), ui.dp(8), ui.dp(10)) }, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { topMargin = ui.dp(4) })
    }

    // ---------- Actions ----------

    private fun hasCredentials() = SecurePrefs.username(this).isNotEmpty() && SecurePrefs.password(this).isNotEmpty()
    private fun retry() = WifiMonitorService.start(this, WifiMonitorService.ACTION_RETRY)
    private fun signOut() = WifiMonitorService.start(this, WifiMonitorService.ACTION_SIGN_OUT)
    private fun openPortal() = startActivity(Intent(this, PortalActivity::class.java))

    private fun requestNotificationsThenStart() {
        val needsNotif = Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsNotif) ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 7)
        else startMonitor()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 7) startMonitor() // monitoring works even if notifications were declined
    }

    private fun startMonitor() {
        retry() // new credentials: try signing in now
        askToRunInBackground()
    }

    /**
     * Many phones (Xiaomi, Oppo, Vivo, Samsung…) kill background apps to save battery, which would stop
     * auto-login. Ask once to be exempt; some phones don't offer this screen, so fall back to the list.
     */
    @SuppressLint("BatteryLife") // sideloaded app; Play Store policy would need review
    private fun askToRunInBackground() {
        if ((getSystemService(POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(packageName)) return
        runCatching { startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))) }
            .recoverCatching { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
    }

    private fun openNotificationSettings() {
        runCatching { startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)) }
            .recoverCatching { openAppSettings() }
    }

    private fun openAppSettings() {
        runCatching { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) }
    }

    private fun hideKeyboard() {
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(window.decorView.windowToken, 0)
    }

    companion object {
        /** Brands whose battery savers block apps from starting after a reboot unless "Autostart" is on. */
        private val AUTOSTART_BRANDS = setOf("xiaomi", "redmi", "poco", "oppo", "vivo", "iqoo", "realme", "oneplus", "huawei", "honor", "asus", "meizu", "letv")
    }
}
