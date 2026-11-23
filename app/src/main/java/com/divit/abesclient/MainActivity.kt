package com.divit.abesclient

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : Activity() {
    private lateinit var user: EditText
    private lateinit var pass: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.getBooleanExtra("open_portal", false)) {
            startActivity(Intent(this, PortalActivity::class.java)); finish(); return
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(20.dp, 24.dp, 20.dp, 16.dp)
        }
        root.addView(TextView(this).apply { text = "ABES Wi-Fi Client"; textSize = 26f })
        root.addView(TextView(this).apply {
            text = "Save your college credentials once. Whenever you join ABES Wi-Fi, the app signs you in automatically."
            textSize = 14f; setPadding(0, 8.dp, 0, 12.dp)
        })
        user = field("College username").also { root.addView(it) }
        pass = field("College password", true).also { root.addView(it) }
        user.setText(SecurePrefs.username(this)); pass.setText(SecurePrefs.password(this))
        if (user.text.isNotEmpty()) WifiMonitorService.start(this) // keep monitoring running whenever the app is opened
        root.addView(Button(this).apply {
            text = "Save credentials & enable monitoring"
            setOnClickListener {
                if (user.text.isBlank() || pass.text.isBlank()) { toast("Enter username and password first"); return@setOnClickListener }
                SecurePrefs.save(this@MainActivity, user.text.toString().trim(), pass.text.toString())
                requestPermissionsIfNeeded()
                toast("Credentials saved on this device")
            }
        })
        root.addView(Button(this).apply {
            text = "Sign out of ABES Wi-Fi"
            setOnClickListener {
                if (SecurePrefs.username(this@MainActivity).isEmpty()) { toast("Save your credentials first"); return@setOnClickListener }
                WifiMonitorService.start(this@MainActivity, WifiMonitorService.ACTION_SIGN_OUT)
                toast("Signing out… check the notification")
            }
        })
        root.addView(Button(this).apply {
            text = "Open login portal"
            setOnClickListener { startActivity(Intent(this@MainActivity, PortalActivity::class.java)) }
        })
        root.addView(TextView(this).apply {
            text = "Portal: ${Portal.URL}\n\nIf it stops working after a while, open Settings > Apps > ABES Wi-Fi Client: set Battery to Unrestricted and, on Xiaomi/Oppo/Vivo/Realme phones, turn on Autostart."
            textSize = 12f; setPadding(0, 12.dp, 0, 0)
        })
        // targetSdk 35: Android 15 draws behind the status/nav bars; pad content clear of them.
        setContentView(ScrollView(this).apply { fitsSystemWindows = true; addView(root) })
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra("open_portal", false)) startActivity(Intent(this, PortalActivity::class.java))
    }

    private fun field(hintText: String, secret: Boolean = false) = EditText(this).apply {
        hint = hintText; textSize = 16f
        if (secret) inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        else inputType = android.text.InputType.TYPE_CLASS_TEXT
    }

    private fun requestPermissionsIfNeeded() {
        val needsNotif = Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsNotif) ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 7)
        else startMonitor()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != 7) return
        if (grantResults.firstOrNull() != PackageManager.PERMISSION_GRANTED) toast("Notifications are off, so you won't see the sign-in alert")
        startMonitor()
    }
    private fun startMonitor() {
        WifiMonitorService.start(this, WifiMonitorService.ACTION_RETRY) // new credentials: try signing in now
        toast("Wi-Fi monitoring enabled")
        askToRunInBackground()
    }

    /**
     * Many phones (Xiaomi, Oppo, Vivo, Samsung…) kill background apps to save battery, which would stop
     * auto-login. Ask once to be exempt; some phones don't offer this screen, so fall back to the list.
     */
    @android.annotation.SuppressLint("BatteryLife") // sideloaded app; Play Store policy would need review
    private fun askToRunInBackground() {
        if ((getSystemService(POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(packageName)) return
        val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
        runCatching { startActivity(direct) }
            .recoverCatching { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
    }

    private val Int.dp get() = (this * resources.displayMetrics.density).toInt()
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()
}
