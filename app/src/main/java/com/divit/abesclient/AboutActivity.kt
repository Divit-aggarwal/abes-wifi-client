package com.divit.abesclient

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.*
import com.divit.abesclient.Ui.Style

/** Who made the app and how to reach them. Edit here; the About screen and main footer read it. */
object Developer {
    const val NAME = "Divit Aggarwal"
    const val TITLE = "Student developer · ABES Engineering College"
    const val LINKEDIN = "https://www.linkedin.com/in/divit-aggarwal-/"
    const val GITHUB = "https://github.com/Divit-aggarwal"
    const val REPO = "https://github.com/Divit-aggarwal/abes-wifi-client"
}

class AboutActivity : Activity() {
    private lateinit var ui: Ui

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = Ui(this).also { it.applyWindow(this) }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(20), 0, ui.dp(20), ui.dp(32))
        }
        content.addView(profileCard())
        content.addView(ui.sectionTitle("This app"))
        content.addView(ui.card().apply {
            setPadding(ui.dp(12), ui.dp(6), ui.dp(12), ui.dp(6))
            addView(ui.row(R.drawable.ic_code, "Source code", "Open source on GitHub. Stars welcome!", null) { open(Developer.REPO) })
            addView(ui.divider())
            addView(ui.row(R.drawable.ic_bug, "Report a problem", "Tell me what went wrong", null) { open("${Developer.REPO}/issues/new") })
            addView(ui.divider())
            addView(ui.row(R.drawable.ic_share, "Share with friends", "Help other ABES students skip the login page", null, ::share))
        })
        content.addView(ui.sectionTitle("Privacy"))
        content.addView(ui.card().apply {
            addView(LinearLayout(this@AboutActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(ui.icon(R.drawable.ic_shield, ui.green, 22))
                addView(ui.label("No ads, no tracking, no accounts. Your password is encrypted on your phone and is only " +
                    "ever sent to the real ABES login page, which the app verifies by its security certificate.", 14f, ui.muted)
                    .apply { setPadding(ui.dp(14), 0, 0, 0) })
            })
        })
        val version = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: ""
        content.addView(ui.label("ABES Wi-Fi $version\nMade with care at ABES Engineering College", 12f, ui.muted).apply {
            gravity = Gravity.CENTER; setPadding(0, ui.dp(28), 0, 0)
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        })

        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; fitsSystemWindows = true
            addView(ui.topBar("About") { finish() })
            addView(ScrollView(this@AboutActivity).apply { addView(content) }, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        })
    }

    private fun profileCard() = ui.card().apply {
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(ui.dp(24), ui.dp(28), ui.dp(24), ui.dp(24))
        (layoutParams as LinearLayout.LayoutParams).topMargin = ui.dp(8)
        val initials = Developer.NAME.split(" ").mapNotNull { it.firstOrNull()?.uppercase() }.take(2).joinToString("")
        addView(ui.label(initials, 30f, ui.onBrand, 700).apply {
            gravity = Gravity.CENTER
            background = ui.rounded(ui.brand, 999)
            importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(ui.dp(88), ui.dp(88)))
        addView(ui.label(Developer.NAME, 24f, ui.text, 700).apply { gravity = Gravity.CENTER; setPadding(0, ui.dp(16), 0, 0) })
        addView(ui.label(Developer.TITLE, 14f, ui.muted).apply { gravity = Gravity.CENTER; setPadding(0, ui.dp(4), 0, 0) })
        addView(ui.label("I built this so ABES students never have to type their Wi-Fi password again. " +
            "Ideas, bugs or just want to connect? Reach out!", 15f, ui.text).apply {
            gravity = Gravity.CENTER; setPadding(0, ui.dp(16), 0, ui.dp(8))
        })
        addView(ui.button("Connect on LinkedIn", Style.PRIMARY) { open(Developer.LINKEDIN) })
        addView(ui.button("Follow on GitHub", Style.TONAL) { open(Developer.GITHUB) }
            .apply { (layoutParams as LinearLayout.LayoutParams).topMargin = ui.dp(10) })
    }

    private fun open(url: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure { Toast.makeText(this, "No browser found to open $url", Toast.LENGTH_LONG).show() }
    }

    private fun share() {
        val text = "ABES Wi-Fi: signs you in to campus Wi-Fi automatically. Free and open source: ${Developer.REPO}"
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share ABES Wi-Fi"))
    }
}
