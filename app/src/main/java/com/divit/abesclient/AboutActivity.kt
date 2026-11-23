package com.divit.abesclient

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.*
import com.divit.abesclient.Ui.Style

/** Who made the app and how to reach them. Edit here; the About screen and main footer read it. */
object Developer {
    const val NAME = "Divit Aggarwal"
    const val TITLE = "Student developer"
    const val BIO = "I build apps that make everyday student life a little easier. " +
        "This one means you never type your Wi-Fi password again. Ideas, bugs, or just want to connect? Reach out!"
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
            setPadding(ui.dp(20), ui.dp(4), ui.dp(20), ui.dp(36))
        }
        content.addView(profileCard())
        content.addView(ui.sectionTitle("This app"))
        content.addView(ui.card().apply {
            setPadding(ui.dp(8), ui.dp(8), ui.dp(8), ui.dp(8))
            addView(ui.row(R.drawable.ic_code, ui.blue, "Source code", "Open source on GitHub. Stars welcome!", null) { open(Developer.REPO) })
            addView(ui.divider())
            addView(ui.row(R.drawable.ic_bug, ui.amber, "Report a problem", "Tell me what went wrong", null) { open("${Developer.REPO}/issues/new") })
            addView(ui.divider())
            addView(ui.row(R.drawable.ic_share, ui.green, "Share with friends", "Help classmates skip the login page", null, ::share))
        })
        content.addView(ui.sectionTitle("Privacy"))
        content.addView(ui.card().apply {
            setPadding(ui.dp(16), ui.dp(16), ui.dp(20), ui.dp(16))
            addView(LinearLayout(this@AboutActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(ui.iconTile(R.drawable.ic_shield, ui.green))
                addView(LinearLayout(this@AboutActivity).apply {
                    orientation = LinearLayout.VERTICAL; setPadding(ui.dp(14), 0, 0, 0)
                    addView(ui.label("No ads. No tracking. No accounts.", 16f, ui.text, 500))
                    addView(ui.label("Your password stays encrypted on your phone and is only sent to the real ABES " +
                        "login page, verified by its security certificate.", 13f, ui.muted).apply { setPadding(0, ui.dp(4), 0, 0) })
                }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            })
        })
        val version = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: ""
        content.addView(ui.label("ABES Wi-Fi $version · Made with care by ${Developer.NAME}", 12f, ui.muted).apply {
            gravity = Gravity.CENTER; setPadding(0, ui.dp(28), 0, 0)
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        })

        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; fitsSystemWindows = true
            addView(ui.topBar("About") { finish() })
            addView(ScrollView(this@AboutActivity).apply { addView(content) }, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        })
    }

    /** Gradient banner, photo in a ring overlapping it, name, title, bio, and the two contact buttons. */
    private fun profileCard() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
        background = ui.rounded(ui.surface, 28, if (ui.dark) null else ui.outline)
        clipToOutline = true
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = ui.dp(8) }

        addView(FrameLayout(this@AboutActivity).apply {
            addView(View(this@AboutActivity).apply {
                background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    intArrayOf(0xFF4F8CFF.toInt(), 0xFF1E4FD8.toInt(), 0xFF7C3AED.toInt()))
            }, FrameLayout.LayoutParams(MATCH_PARENT, ui.dp(120)))
            addView(avatar(), FrameLayout.LayoutParams(ui.dp(116), ui.dp(116), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL))
        }, LinearLayout.LayoutParams(MATCH_PARENT, ui.dp(178)))

        addView(LinearLayout(this@AboutActivity).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            setPadding(ui.dp(24), ui.dp(14), ui.dp(24), ui.dp(24))
            addView(ui.label(Developer.NAME, 26f, ui.text, 700).apply { gravity = Gravity.CENTER })
            addView(ui.label(Developer.TITLE, 15f, ui.brand, 500).apply { gravity = Gravity.CENTER; setPadding(0, ui.dp(4), 0, 0) })
            addView(ui.label(Developer.BIO, 15f, ui.muted).apply { gravity = Gravity.CENTER; setPadding(0, ui.dp(14), 0, ui.dp(10)) })
            addView(ui.button("Connect on LinkedIn", fill = 0xFF0A66C2.toInt(), fg = Color.WHITE) { open(Developer.LINKEDIN) })
            addView(ui.button("Follow on GitHub", fill = if (ui.dark) 0xFFF1F5F9.toInt() else 0xFF0F172A.toInt(),
                fg = if (ui.dark) 0xFF0F172A.toInt() else Color.WHITE) { open(Developer.GITHUB) }
                .apply { (layoutParams as LinearLayout.LayoutParams).topMargin = ui.dp(10) })
        })
    }

    /** The developer photo (res/drawable-nodpi/developer_photo.jpg) in a ring; initials until it's added. */
    @SuppressLint("DiscouragedApi") // optional resource: the app must build with or without the photo
    private fun avatar(): View {
        val ring = FrameLayout(this).apply {
            background = ui.oval(ui.surface)
            setPadding(ui.dp(5), ui.dp(5), ui.dp(5), ui.dp(5))
            elevation = ui.dp(6).toFloat()
        }
        val photo = resources.getIdentifier("developer_photo", "drawable", packageName)
        ring.addView(if (photo != 0) ImageView(this).apply {
            setImageResource(photo); scaleType = ImageView.ScaleType.CENTER_CROP
            background = ui.oval(ui.surface2); clipToOutline = true
            contentDescription = "Photo of ${Developer.NAME}"
        } else ui.label(Developer.NAME.split(" ").mapNotNull { it.firstOrNull()?.uppercase() }.take(2).joinToString(""),
            36f, Color.WHITE, 700).apply {
            gravity = Gravity.CENTER
            background = ui.gradient(0xFF4F8CFF.toInt(), 0xFF7C3AED.toInt(), oval = true)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        return ring
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
