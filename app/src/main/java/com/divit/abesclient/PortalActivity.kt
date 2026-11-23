package com.divit.abesclient

import android.app.Activity
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.*
import android.widget.*

object Portal {
    const val HOST = "192.168.1.254"
    const val PORT = 8090
    const val URL = "https://$HOST:$PORT/httpclient.html"
}

class PortalActivity : Activity() {
    private var submitted = false
    private lateinit var web: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; fitsSystemWindows = true }
        val status = TextView(this).apply { text = "Opening ABES login portal…"; setPadding(16, 12, 16, 12) }
        web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = object : WebViewClient() {
                // The campus firewall serves a self-signed certificate. Accept it ONLY for the exact
                // portal host:port on the private LAN; every other site keeps normal certificate checks.
                override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                    val uri = Uri.parse(error.url)
                    if (uri.host == Portal.HOST && uri.port == Portal.PORT) handler.proceed()
                    else { handler.cancel(); status.text = "Blocked an untrusted certificate from ${uri.host}" }
                }

                // The portal only navigates away from itself after a successful login (to the page you
                // originally wanted, often plain-http google.com). Treat that as "signed in" and close.
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    if (request.url.host == Portal.HOST) return false
                    if (submitted) {
                        Toast.makeText(this@PortalActivity, "Signed in to ABES Wi-Fi", Toast.LENGTH_SHORT).show()
                        WifiMonitorService.start(this@PortalActivity, WifiMonitorService.ACTION_RETRY) // refresh status
                        finish()
                    }
                    return true
                }

                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    if (request.isForMainFrame && request.url.host == Portal.HOST) status.text = "Portal not reachable (${error.description}). Are you on ABES Wi-Fi?"
                }

                override fun onPageFinished(view: WebView, url: String) {
                    // onPageFinished can fire several times (redirects, reloads). Submit once only,
                    // otherwise a wrong password triggers an endless submit loop.
                    // After login the portal redirects (e.g. to Google). Only ever fill the portal's own page.
                    if (submitted || Uri.parse(url).host != Portal.HOST) return
                    val u = org.json.JSONObject.quote(SecurePrefs.username(this@PortalActivity))
                    val p = org.json.JSONObject.quote(SecurePrefs.password(this@PortalActivity))
                    val js = "(function(){var u=document.getElementById('username');var p=document.getElementById('password');" +
                        "if(!u||!p)return 'FIELDS_NOT_FOUND';u.value=$u;p.value=$p;" +
                        "var b=document.getElementById('loginbutton');if(b){b.click();return 'SUBMITTED';}return 'BUTTON_NOT_FOUND';})();"
                    evaluateJavascript(js) { result ->
                        if (result?.contains("SUBMITTED") == true) submitted = true
                        status.text = when {
                            result?.contains("SUBMITTED") == true -> "Credentials submitted. Check the portal response below."
                            result?.contains("FIELDS_NOT_FOUND") == true -> "Login fields not found on this page."
                            result?.contains("BUTTON_NOT_FOUND") == true -> "Filled your details, but no login button was found. Tap sign in yourself."
                            else -> "Portal loaded. Sign in manually if needed."
                        }
                    }
                }
            }
        }
        layout.addView(status)
        layout.addView(web, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(layout)
        web.loadUrl(Portal.URL)
    }

    override fun onDestroy() {
        web.destroy()
        super.onDestroy()
    }
}
