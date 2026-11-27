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

/** Who made the app and how to reach them. Edit here; the About screen and the main footer read it. */
data class Person(val name: String, val role: String, val photo: Int, val linkedin: String, val github: String)

object People {
    val DEVELOPER = Person("Divit Aggarwal", "Developer", R.drawable.photo_divit,
        "https://www.linkedin.com/in/divit-aggarwal-/", "https://github.com/Divit-aggarwal")
    val DESIGNER = Person("Divy547", "Designer", R.drawable.photo_divy,
        "https://www.linkedin.com/in/divyx/", "https://github.com/Divy547")
    const val BIO = "We build apps that make everyday student life a little easier. " +
        "This one means you never type your Wi-Fi password again. Ideas, bugs, or just want to connect? Reach out."
    const val REPO = "https://github.com/Divit-aggarwal/abes-wifi-client"
}

class AboutActivity : Activity() {
    private lateinit var ui: Ui

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = Ui(this).also { it.applyWindow(this) }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(20), 0, ui.dp(20), ui.dp(40))
        }
        val intro = ui.label(People.BIO, 16f, ui.muted).apply { setPadding(ui.dp(8), ui.dp(16), ui.dp(8), 0) }
        val makers = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(ui.sectionTitle("Made by"))
            addView(personCard(People.DEVELOPER))
            addView(personCard(People.DESIGNER).apply { (layoutParams as LinearLayout.LayoutParams).topMargin = ui.dp(8) })
        }
        val app = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(ui.sectionTitle("This app"))
            addView(ui.card().apply {
                addView(ui.row(R.drawable.ic_code, "Source code", "Open source on GitHub", trailIcon = R.drawable.ic_external) { open(People.REPO) })
                addView(ui.divider())
                addView(ui.row(R.drawable.ic_bug, "Report a problem", "Tell us what went wrong", trailIcon = R.drawable.ic_external) { open("${People.REPO}/issues/new") })
                addView(ui.divider())
                addView(ui.row(R.drawable.ic_share, "Share with friends", "Help classmates skip the login page", onClick = ::share))
            })
        }
        val version = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: ""
        val privacy = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(ui.sectionTitle("Privacy"))
            addView(ui.label("No ads, no tracking, no accounts.", 16f, ui.ink).apply { setPadding(ui.dp(8), 0, ui.dp(8), 0) })
            addView(ui.label("Your password stays encrypted on your phone and is only sent to the real campus login page, " +
                "checked against its security certificate.", 13f, ui.muted).apply { setPadding(ui.dp(8), ui.dp(4), ui.dp(8), 0) })
            addView(ui.label("${getString(R.string.app_name)} $version", 13f, ui.muted).apply { setPadding(ui.dp(8), ui.dp(40), ui.dp(8), 0) })
        }
        listOf(intro, makers, app, privacy).forEach { content.addView(it) }

        val bar = ui.topBar("About") { finish() }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; fitsSystemWindows = true
            addView(bar)
            addView(ScrollView(this@AboutActivity).apply { addView(content) }, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        })
        ui.enter(bar, intro, makers, app, privacy)
    }

    /** Photo, name, role, and LinkedIn / GitHub buttons. */
    private fun personCard(p: Person) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.TOP
        background = ui.rounded(ui.surface, 20)
        setPadding(ui.dp(20), ui.dp(20), ui.dp(20), ui.dp(20))
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        addView(ImageView(this@AboutActivity).apply {
            setImageResource(p.photo); scaleType = ImageView.ScaleType.CENTER_CROP
            background = ui.oval(ui.surface2); clipToOutline = true
            contentDescription = "Portrait of ${p.name}"
        }, LinearLayout.LayoutParams(ui.dp(64), ui.dp(64)))
        addView(LinearLayout(this@AboutActivity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(16), ui.dp(2), 0, 0)
            addView(ui.label(p.name, 16f, ui.ink, bold = true))
            addView(ui.label(p.role, 13f, ui.muted).apply { setPadding(0, ui.dp(2), 0, 0) })
            addView(LinearLayout(this@AboutActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, ui.dp(12), 0, 0)
                addView(ui.button("LinkedIn", Style.PRIMARY, small = true) { open(p.linkedin) }.apply { contentDescription = "${p.name} on LinkedIn" })
                addView(ui.button("GitHub", Style.SECONDARY, small = true) { open(p.github) }.apply {
                    contentDescription = "${p.name} on GitHub"
                    (layoutParams as LinearLayout.LayoutParams).marginStart = ui.dp(8)
                })
            })
        }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
    }

    private fun open(url: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure { Toast.makeText(this, "No browser found to open $url", Toast.LENGTH_LONG).show() }
    }

    private fun share() {
        val name = getString(R.string.app_name)
        val text = "$name signs you in to campus Wi-Fi automatically. Free and open source: ${People.REPO}"
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share $name"))
    }
}
