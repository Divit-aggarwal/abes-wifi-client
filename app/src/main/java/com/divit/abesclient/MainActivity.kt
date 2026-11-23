package com.divit.abesclient

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
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

class MainActivity : Activity() {
    private lateinit var ui: Ui
    private lateinit var scroll: ScrollView
    private lateinit var heroCard: LinearLayout
    private lateinit var heroBadge: FrameLayout
    private lateinit var heroIcon: ImageView
    private lateinit var heroSpinner: ProgressBar
    private lateinit var heroTitle: TextView
    private lateinit var heroBody: TextView
    private lateinit var heroActions: LinearLayout
    private lateinit var accountCard: LinearLayout
    private lateinit var checksCard: LinearLayout
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
            setPadding(ui.dp(20), ui.dp(12), ui.dp(20), ui.dp(32))
        }
        root.addView(header())
        root.addView(buildHero())
        root.addView(ui.sectionTitle("Your account"))
        accountCard = ui.card().also { root.addView(it) }
        root.addView(ui.sectionTitle("Keep it working"))
        checksCard = ui.card().apply { setPadding(ui.dp(12), ui.dp(6), ui.dp(12), ui.dp(6)) }.also { root.addView(it) }
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
        super.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra("open_portal", false)) openPortal()
    }

    // ---------- Header ----------

    private fun header() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        setPadding(ui.dp(4), ui.dp(12), 0, ui.dp(4))
        addView(ImageView(this@MainActivity).apply {
            setImageResource(R.mipmap.ic_launcher)
            background = ui.rounded(ui.brand, 14); clipToOutline = true
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(ui.dp(44), ui.dp(44)))
        addView(LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL; setPadding(ui.dp(14), 0, 0, 0)
            addView(ui.label("ABES Wi-Fi", 22f, ui.text, 700))
            addView(ui.label("Automatic campus sign-in", 14f, ui.muted))
        }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        addView(ImageView(this@MainActivity).apply { // About the developer
            setImageResource(R.drawable.ic_info); imageTintList = ColorStateList.valueOf(ui.muted)
            setPadding(ui.dp(12), ui.dp(12), ui.dp(12), ui.dp(12))
            background = android.graphics.drawable.RippleDrawable(ColorStateList.valueOf(ui.tint(ui.text, 0.1f)), null, null)
            contentDescription = "About the developer"
            setOnClickListener { startActivity(Intent(this@MainActivity, AboutActivity::class.java)) }
        }, LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)))
    }

    // ---------- Status card ----------

    private class Hero(
        val color: Int, val icon: Int, val title: String, val body: String, val busy: Boolean = false,
        val actions: List<Triple<String, Style, () -> Unit>> = emptyList()
    )

    private fun buildHero(): LinearLayout {
        heroCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(22), ui.dp(22), ui.dp(22), ui.dp(18))
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = ui.dp(20) }
        }
        heroIcon = ImageView(this).apply { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }
        heroSpinner = ProgressBar(this).apply { isIndeterminate = true }
        heroBadge = FrameLayout(this).apply {
            addView(heroIcon, FrameLayout.LayoutParams(ui.dp(28), ui.dp(28), Gravity.CENTER))
            addView(heroSpinner, FrameLayout.LayoutParams(ui.dp(30), ui.dp(30), Gravity.CENTER))
        }
        heroCard.addView(heroBadge, LinearLayout.LayoutParams(ui.dp(56), ui.dp(56)))
        heroTitle = ui.label("", 24f, ui.text, 700).apply {
            setPadding(0, ui.dp(16), 0, 0)
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE // read out status changes
        }
        heroBody = ui.label("", 15f, ui.muted).apply { setPadding(0, ui.dp(6), 0, 0) }
        heroActions = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, ui.dp(6), 0, 0) }
        heroCard.addView(heroTitle); heroCard.addView(heroBody); heroCard.addView(heroActions)
        return heroCard
    }

    private fun currentHero(): Hero {
        val usePage = Triple("Use the login page instead", Style.TEXT, ::openPortal)
        val waiting = "You'll be signed in automatically as soon as you join ABES Wi-Fi."
        if (!hasCredentials()) return Hero(ui.brand, R.drawable.ic_lock, "Set up automatic sign-in",
            "Enter your ABES login once below. After that, the app signs you in every time you join campus Wi-Fi.")
        return when (MonitorStatus.state) {
            "online", "signed_in" -> Hero(ui.green, R.drawable.ic_check_circle, "You're online",
                "Signed in to ABES Wi-Fi as ${SecurePrefs.username(this)}.",
                actions = listOf(Triple("Sign out", Style.TONAL, ::signOut)))
            "signing_in" -> Hero(ui.brand, R.drawable.ic_wifi, "Signing you in…", "Talking to the ABES login page.", busy = true)
            "wrong_password" -> Hero(ui.amber, R.drawable.ic_error, "Wrong username or password",
                "The ABES login page didn't accept your details. Check them and try again.",
                actions = listOf(Triple("Update login details", Style.PRIMARY, ::startEditing), usePage))
            "failed", "needs_login" -> {
                val detail = MonitorStatus.message.substringAfter("failed (", "").substringBefore(")")
                Hero(ui.amber, R.drawable.ic_error, "Couldn't sign you in",
                    if (detail.isNotBlank()) "The login page said: ${detail.replaceFirstChar { it.uppercase() }}." else "The login page didn't respond as expected.",
                    actions = listOf(Triple("Try again", Style.PRIMARY, ::retry), usePage))
            }
            "signout_failed" -> Hero(ui.amber, R.drawable.ic_error, "Couldn't sign out",
                "Make sure you're connected to ABES Wi-Fi, then try again.",
                actions = listOf(Triple("Try again", Style.PRIMARY, ::signOut)))
            "untrusted" -> Hero(ui.red, R.drawable.ic_shield, "This isn't the real ABES login page",
                "This Wi-Fi is imitating the ABES login page, so your password was not sent. Connect to the official campus Wi-Fi.")
            "signed_out" -> Hero(ui.muted, R.drawable.ic_logout, "Signed out",
                "You'll be signed in again the next time you connect.",
                actions = listOf(Triple("Sign in now", Style.PRIMARY, ::retry)))
            "other" -> Hero(ui.muted, R.drawable.ic_wifi, "Not on ABES Wi-Fi", waiting)
            "lost" -> Hero(ui.muted, R.drawable.ic_wifi, "Wi-Fi disconnected", waiting)
            else -> Hero(ui.muted, R.drawable.ic_wifi, "Waiting for Wi-Fi", waiting)
        }
    }

    private fun renderHero() {
        val h = currentHero()
        heroCard.background = ui.rounded(ui.tint(h.color, if (ui.dark) 0.14f else 0.08f), 24, ui.tint(h.color, 0.22f))
        heroBadge.background = ui.rounded(ui.tint(h.color, if (ui.dark) 0.24f else 0.14f), 999)
        heroIcon.setImageResource(h.icon)
        heroIcon.imageTintList = ColorStateList.valueOf(h.color)
        heroIcon.visibility = if (h.busy) View.INVISIBLE else View.VISIBLE
        heroSpinner.visibility = if (h.busy) View.VISIBLE else View.GONE
        heroSpinner.indeterminateTintList = ColorStateList.valueOf(h.color)
        heroTitle.text = h.title
        heroBody.text = h.body
        heroActions.removeAllViews()
        h.actions.forEach { (text, style, action) -> heroActions.addView(ui.button(text, style) { action() }) }
    }

    // ---------- Account ----------

    private fun renderAccount() {
        accountCard.removeAllViews()
        if (!editing) {
            accountCard.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                addView(LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(ui.label("College ID", 13f, ui.muted))
                    addView(ui.label(SecurePrefs.username(this@MainActivity), 18f, ui.text, 500))
                    addView(ui.label("Password saved securely", 13f, ui.muted).apply { setPadding(0, ui.dp(2), 0, 0) })
                }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                addView(ui.button("Change", Style.TONAL, ::startEditing).apply {
                    layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT)
                    minWidth = ui.dp(96); setPadding(ui.dp(20), 0, ui.dp(20), 0)
                })
            })
            return
        }
        val firstTime = !hasCredentials()
        accountCard.addView(ui.label(if (firstTime) "Your ABES login" else "Update login details", 18f, ui.text, 500))
        accountCard.addView(ui.label("The same username and password you use on the campus Wi-Fi page.", 14f, ui.muted)
            .apply { setPadding(0, ui.dp(4), 0, ui.dp(4)) })
        val user = ui.field("College ID / username").apply { setText(SecurePrefs.username(this@MainActivity)) }
        val pass = ui.field("Password", secret = true).apply {
            setText(SecurePrefs.password(this@MainActivity))
            setPadding(ui.dp(16), ui.dp(14), ui.dp(76), ui.dp(14))
            layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        }
        val toggle = ui.label("Show", 14f, ui.brand, 500).apply {
            setPadding(ui.dp(16), ui.dp(12), ui.dp(16), ui.dp(12))
            contentDescription = "Show password"
            setOnClickListener {
                val hidden = pass.inputType and InputType.TYPE_TEXT_VARIATION_PASSWORD == InputType.TYPE_TEXT_VARIATION_PASSWORD
                pass.inputType = InputType.TYPE_CLASS_TEXT or
                    (if (hidden) InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD else InputType.TYPE_TEXT_VARIATION_PASSWORD)
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
        accountCard.post { scroll.smoothScrollTo(0, accountCard.top - ui.dp(48)) }
    }

    // ---------- Keep it working ----------

    private fun renderChecks() {
        checksCard.removeAllViews()
        val notifOn = NotificationManagerCompat.from(this).areNotificationsEnabled()
        checksCard.addView(ui.row(R.drawable.ic_bell, "Notifications",
            if (notifOn) "Shows your Wi-Fi sign-in status" else "Turn on to see when you're signed in",
            if (notifOn) "On" to ui.green else "Fix" to ui.amber) { openNotificationSettings() })
        checksCard.addView(ui.divider())
        val bgOn = (getSystemService(POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(packageName)
        checksCard.addView(ui.row(R.drawable.ic_battery, "Run in background",
            if (bgOn) "Allowed, so sign-in keeps working" else "Your phone may stop automatic sign-in",
            if (bgOn) "On" to ui.green else "Fix" to ui.amber) { if (bgOn) openAppSettings() else askToRunInBackground() })
        if (Build.MANUFACTURER.lowercase() in AUTOSTART_BRANDS) {
            checksCard.addView(ui.divider())
            checksCard.addView(ui.row(R.drawable.ic_power, "Autostart",
                "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} phones need this on to keep working after a restart",
                "Open" to ui.brand) { openAppSettings() })
        }
        checksCard.addView(ui.divider())
        checksCard.addView(ui.row(R.drawable.ic_open, "Open ABES login page", "Sign in by hand if something goes wrong", null, ::openPortal))
    }

    // ---------- Footer ----------

    private fun footer() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
        setPadding(ui.dp(12), ui.dp(28), ui.dp(12), 0)
        addView(LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            addView(ui.icon(R.drawable.ic_lock, ui.muted, 14))
            addView(ui.label("Your password is encrypted on this phone and only sent to the verified ABES login page.",
                12f, ui.muted).apply { setPadding(ui.dp(8), 0, 0, 0) })
        })
        addView(ui.label("Made by ${Developer.NAME} · About", 13f, ui.brand, 500).apply {
            setPadding(ui.dp(12), ui.dp(14), ui.dp(12), ui.dp(4))
            setOnClickListener { startActivity(Intent(this@MainActivity, AboutActivity::class.java)) }
        })
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
