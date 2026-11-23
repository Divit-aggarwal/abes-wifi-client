package com.divit.abesclient

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.RippleDrawable
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

class MainActivity : Activity() {
    private lateinit var ui: Ui
    private lateinit var scroll: ScrollView
    private lateinit var glowOuter: View
    private lateinit var glowInner: View
    private lateinit var core: FrameLayout
    private lateinit var coreIcon: ImageView
    private lateinit var coreSpinner: ProgressBar
    private lateinit var chip: TextView
    private lateinit var heroTitle: TextView
    private lateinit var heroBody: TextView
    private lateinit var heroActions: LinearLayout
    private lateinit var accountCard: LinearLayout
    private lateinit var checksCard: LinearLayout
    private var pulse: ObjectAnimator? = null
    private var editing = false
    private val onStatus: () -> Unit = { renderHero() }

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
            setPadding(ui.dp(20), ui.dp(8), ui.dp(20), ui.dp(36))
        }
        root.addView(header())
        root.addView(buildHero())
        root.addView(ui.sectionTitle("Account"))
        accountCard = ui.card().also { root.addView(it) }
        root.addView(ui.sectionTitle("Keep it working"))
        checksCard = ui.card().apply { setPadding(ui.dp(8), ui.dp(8), ui.dp(8), ui.dp(8)) }.also { root.addView(it) }
        root.addView(footer())
        // targetSdk 35: Android 15 draws behind the status/nav bars; pad content clear of them.
        scroll = ScrollView(this).apply { fitsSystemWindows = true; isFillViewport = true; addView(root) }
        setContentView(scroll)

        renderAccount()
        if (hasCredentials()) WifiMonitorService.start(this) // keep monitoring running whenever the app is opened
    }

    override fun onResume() {
        super.onResume()
        MonitorStatus.listen(onStatus)
        renderHero()
        renderChecks() // settings may have changed while we were away
    }

    override fun onPause() {
        MonitorStatus.unlisten(onStatus)
        pulse?.pause()
        super.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra("open_portal", false)) openPortal()
    }

    // ---------- Header ----------

    private fun header() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        setPadding(ui.dp(4), ui.dp(12), 0, 0)
        addView(ui.label("ABES Wi-Fi", 24f, ui.text, 700), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        addView(ImageView(this@MainActivity).apply { // About the developer
            setImageResource(R.drawable.ic_info); imageTintList = ColorStateList.valueOf(ui.text)
            setPadding(ui.dp(11), ui.dp(11), ui.dp(11), ui.dp(11))
            background = RippleDrawable(ColorStateList.valueOf(ui.tint(ui.text, 0.12f)), ui.oval(ui.surface), null)
            contentDescription = "About the developer"
            setOnClickListener { startActivity(Intent(this@MainActivity, AboutActivity::class.java)) }
        }, LinearLayout.LayoutParams(ui.dp(44), ui.dp(44)))
    }

    // ---------- Status orb ----------

    private class Hero(
        val tone: Tone, val icon: Int, val chip: String, val title: String, val body: String, val busy: Boolean = false,
        val actions: List<Triple<String, Style, () -> Unit>> = emptyList()
    )

    private fun buildHero() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
        setPadding(0, ui.dp(28), 0, ui.dp(4))
        glowOuter = View(this@MainActivity)
        glowInner = View(this@MainActivity)
        coreIcon = ImageView(this@MainActivity).apply {
            imageTintList = ColorStateList.valueOf(Color.WHITE)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        coreSpinner = ProgressBar(this@MainActivity).apply {
            isIndeterminate = true; indeterminateTintList = ColorStateList.valueOf(Color.WHITE)
        }
        core = FrameLayout(this@MainActivity).apply {
            elevation = ui.dp(10).toFloat()
            addView(coreIcon, FrameLayout.LayoutParams(ui.dp(60), ui.dp(60), Gravity.CENTER))
            addView(coreSpinner, FrameLayout.LayoutParams(ui.dp(64), ui.dp(64), Gravity.CENTER))
        }
        addView(FrameLayout(this@MainActivity).apply {
            addView(glowOuter, FrameLayout.LayoutParams(ui.dp(232), ui.dp(232), Gravity.CENTER))
            addView(glowInner, FrameLayout.LayoutParams(ui.dp(180), ui.dp(180), Gravity.CENTER))
            addView(core, FrameLayout.LayoutParams(ui.dp(132), ui.dp(132), Gravity.CENTER))
        }, LinearLayout.LayoutParams(ui.dp(232), ui.dp(232)))
        pulse = ObjectAnimator.ofPropertyValuesHolder(glowOuter,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 0.86f, 1f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.86f, 1f),
            PropertyValuesHolder.ofFloat(View.ALPHA, 1f, 0.35f)).apply {
            duration = 1100; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.REVERSE
        }
        chip = ui.label("", 12f, ui.text, 700).apply {
            letterSpacing = 0.12f; setPadding(ui.dp(14), ui.dp(6), ui.dp(14), ui.dp(6))
        }
        addView(chip, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { topMargin = ui.dp(20) })
        heroTitle = ui.label("", 28f, ui.text, 700).apply {
            gravity = Gravity.CENTER; setPadding(0, ui.dp(12), 0, 0)
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE // read out status changes
        }
        heroBody = ui.label("", 15f, ui.muted).apply { gravity = Gravity.CENTER; setPadding(ui.dp(16), ui.dp(8), ui.dp(16), 0) }
        heroActions = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL; setPadding(0, ui.dp(12), 0, 0) }
        addView(heroTitle, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        addView(heroBody, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        addView(heroActions, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
    }

    private fun currentHero(): Hero {
        val usePage = Triple("Use the login page instead", Style.TEXT, ::openPortal)
        val waiting = "You'll be signed in automatically as soon as you join ABES Wi-Fi."
        if (!hasCredentials()) return Hero(ui.blue, R.drawable.ic_lock, "SET UP", "Sign in once",
            "Save your ABES login below. After that, the app signs you in every time you join campus Wi-Fi.")
        return when (MonitorStatus.state) {
            "online", "signed_in" -> Hero(ui.green, R.drawable.ic_check_circle, "CONNECTED", "You're online",
                "Signed in to ABES Wi-Fi as ${SecurePrefs.username(this)}",
                actions = listOf(Triple("Sign out", Style.TONAL, ::signOut)))
            "signing_in" -> Hero(ui.blue, R.drawable.ic_wifi, "SIGNING IN", "Signing you in…",
                "Talking to the ABES login page", busy = true)
            "wrong_password" -> Hero(ui.amber, R.drawable.ic_error, "ACTION NEEDED", "Wrong password",
                "The ABES login page didn't accept your username or password.",
                actions = listOf(Triple("Update login details", Style.PRIMARY, ::startEditing), usePage))
            "failed", "needs_login" -> {
                val detail = MonitorStatus.message.substringAfter("failed (", "").substringBefore(")")
                Hero(ui.amber, R.drawable.ic_error, "ACTION NEEDED", "Couldn't sign you in",
                    if (detail.isNotBlank()) "The login page said: ${detail.replaceFirstChar { it.uppercase() }}." else "The login page didn't respond as expected.",
                    actions = listOf(Triple("Try again", Style.PRIMARY, ::retry), usePage))
            }
            "signout_failed" -> Hero(ui.amber, R.drawable.ic_error, "ACTION NEEDED", "Couldn't sign out",
                "Make sure you're connected to ABES Wi-Fi, then try again.",
                actions = listOf(Triple("Try again", Style.PRIMARY, ::signOut)))
            "untrusted" -> Hero(ui.red, R.drawable.ic_shield, "BLOCKED", "Fake login page",
                "This Wi-Fi is imitating the ABES login page, so your password was not sent.")
            "signed_out" -> Hero(ui.gray, R.drawable.ic_logout, "SIGNED OUT", "You're signed out",
                "You'll be signed in again the next time you connect.",
                actions = listOf(Triple("Sign in now", Style.PRIMARY, ::retry)))
            "other" -> Hero(ui.gray, R.drawable.ic_wifi, "NOT ON CAMPUS", "Not on ABES Wi-Fi", waiting)
            "lost" -> Hero(ui.gray, R.drawable.ic_wifi, "OFFLINE", "Wi-Fi is off", waiting)
            else -> Hero(ui.gray, R.drawable.ic_wifi, "WAITING", "Looking for ABES Wi-Fi", waiting)
        }
    }

    private fun renderHero() {
        val h = currentHero()
        glowOuter.background = ui.oval(ui.tint(h.tone.light, if (ui.dark) 0.10f else 0.14f))
        glowInner.background = ui.oval(ui.tint(h.tone.light, if (ui.dark) 0.18f else 0.22f))
        core.background = ui.gradient(h.tone.light, h.tone.deep, oval = true)
        coreIcon.setImageResource(h.icon)
        coreIcon.visibility = if (h.busy) View.INVISIBLE else View.VISIBLE
        coreSpinner.visibility = if (h.busy) View.VISIBLE else View.GONE
        if (h.busy) { if (pulse?.isStarted == true) pulse?.resume() else pulse?.start() }
        else { pulse?.cancel(); glowOuter.scaleX = 1f; glowOuter.scaleY = 1f; glowOuter.alpha = 1f }
        chip.text = h.chip
        chip.setTextColor(h.tone.ink)
        chip.background = ui.rounded(ui.tint(h.tone.light, 0.16f), 999)
        heroTitle.text = h.title
        heroBody.text = h.body
        heroActions.removeAllViews()
        h.actions.forEach { (text, style, action) -> heroActions.addView(ui.button(text, style) { action() }) }
    }

    // ---------- Account ----------

    private fun renderAccount() {
        accountCard.removeAllViews()
        if (!editing) {
            accountCard.setPadding(ui.dp(16), ui.dp(16), ui.dp(12), ui.dp(16))
            accountCard.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                addView(ui.iconTile(R.drawable.ic_lock, ui.blue, 44))
                addView(LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL; setPadding(ui.dp(14), 0, ui.dp(8), 0)
                    addView(ui.label(SecurePrefs.username(this@MainActivity), 17f, ui.text, 500))
                    addView(ui.label("Password saved securely", 13f, ui.muted))
                }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                addView(ui.label("Change", 15f, ui.brand, 500).apply {
                    setPadding(ui.dp(14), ui.dp(10), ui.dp(14), ui.dp(10))
                    background = RippleDrawable(ColorStateList.valueOf(ui.tint(ui.brand, 0.15f)), null, ui.rounded(Color.BLACK, 999))
                    setOnClickListener { startEditing() }
                })
            })
            return
        }
        accountCard.setPadding(ui.dp(20), ui.dp(20), ui.dp(20), ui.dp(16))
        val firstTime = !hasCredentials()
        accountCard.addView(ui.label(if (firstTime) "Your ABES login" else "Update login details", 18f, ui.text, 700))
        accountCard.addView(ui.label("Same username and password as the campus Wi-Fi page.", 14f, ui.muted)
            .apply { setPadding(0, ui.dp(4), 0, ui.dp(4)) })
        val user = ui.field("College ID").apply { setText(SecurePrefs.username(this@MainActivity)) }
        val pass = ui.field("Password", secret = true).apply {
            setText(SecurePrefs.password(this@MainActivity))
            setPadding(ui.dp(18), ui.dp(16), ui.dp(76), ui.dp(16))
            layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        }
        val toggle = ui.label("Show", 14f, ui.brand, 500).apply {
            setPadding(ui.dp(16), ui.dp(12), ui.dp(18), ui.dp(12))
            contentDescription = "Show password"
            setOnClickListener {
                val hidden = pass.inputType and InputType.TYPE_TEXT_VARIATION_PASSWORD == InputType.TYPE_TEXT_VARIATION_PASSWORD
                pass.inputType = InputType.TYPE_CLASS_TEXT or
                    (if (hidden) InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD else InputType.TYPE_TEXT_VARIATION_PASSWORD)
                pass.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
                pass.setSelection(pass.text.length)
                text = if (hidden) "Hide" else "Show"
                contentDescription = if (hidden) "Hide password" else "Show password"
            }
        }
        accountCard.addView(user)
        accountCard.addView(FrameLayout(this).apply {
            addView(pass)
            addView(toggle, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.END or Gravity.CENTER_VERTICAL))
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = ui.dp(12) }
        })
        accountCard.addView(ui.button(if (firstTime) "Save and turn on" else "Save", Style.PRIMARY) {
            val u = user.text.toString().trim()
            val p = pass.text.toString()
            if (u.isEmpty()) { user.error = "Enter your college ID"; user.requestFocus(); return@button }
            if (p.isEmpty()) { pass.error = "Enter your password"; pass.requestFocus(); return@button }
            SecurePrefs.save(this, u, p)
            editing = false
            hideKeyboard()
            renderAccount(); renderHero()
            requestNotificationsThenStart()
        }.apply { (layoutParams as LinearLayout.LayoutParams).topMargin = ui.dp(20) })
        if (!firstTime) accountCard.addView(ui.button("Cancel", Style.TEXT) {
            editing = false; hideKeyboard(); renderAccount()
        }.apply { (layoutParams as LinearLayout.LayoutParams).topMargin = ui.dp(4) })
    }

    private fun startEditing() {
        editing = true
        renderAccount()
        accountCard.post { scroll.smoothScrollTo(0, accountCard.top - ui.dp(56)) }
    }

    // ---------- Keep it working ----------

    private fun renderChecks() {
        checksCard.removeAllViews()
        val on = "On" to ui.green
        val fix = "Fix" to ui.amber
        val notifOn = NotificationManagerCompat.from(this).areNotificationsEnabled()
        checksCard.addView(ui.row(R.drawable.ic_bell, ui.blue, "Notifications",
            if (notifOn) "Shows your sign-in status" else "Turn on to see when you're signed in",
            if (notifOn) on else fix) { openNotificationSettings() })
        checksCard.addView(ui.divider())
        val bgOn = (getSystemService(POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(packageName)
        checksCard.addView(ui.row(R.drawable.ic_battery, ui.green, "Run in background",
            if (bgOn) "Keeps automatic sign-in working" else "Your phone may stop automatic sign-in",
            if (bgOn) on else fix) { if (bgOn) openAppSettings() else askToRunInBackground() })
        if (Build.MANUFACTURER.lowercase() in AUTOSTART_BRANDS) {
            checksCard.addView(ui.divider())
            checksCard.addView(ui.row(R.drawable.ic_power, ui.amber, "Autostart",
                "Needed on ${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} phones after a restart", null) { openAppSettings() })
        }
        checksCard.addView(ui.divider())
        checksCard.addView(ui.row(R.drawable.ic_open, ui.gray, "Open login page", "Sign in by hand if needed", null, ::openPortal))
    }

    // ---------- Footer ----------

    private fun footer() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
        setPadding(ui.dp(16), ui.dp(32), ui.dp(16), 0)
        addView(LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            addView(ui.icon(R.drawable.ic_shield, ui.muted, 14))
            addView(ui.label("Encrypted on your phone · sent only to ABES", 12f, ui.muted)
                .apply { setPadding(ui.dp(6), 0, 0, 0) })
        })
        addView(ui.label("Made by ${Developer.NAME}", 13f, ui.brand, 500).apply {
            gravity = Gravity.CENTER
            setPadding(ui.dp(16), ui.dp(12), ui.dp(16), ui.dp(12))
            background = RippleDrawable(ColorStateList.valueOf(ui.tint(ui.brand, 0.15f)), null, ui.rounded(Color.BLACK, 999))
            setOnClickListener { startActivity(Intent(this@MainActivity, AboutActivity::class.java)) }
        }, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { topMargin = ui.dp(6) })
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
