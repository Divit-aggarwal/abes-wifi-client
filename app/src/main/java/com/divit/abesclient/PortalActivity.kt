package com.divit.abesclient

import android.app.Activity
import android.net.Uri
import android.net.http.SslCertificate
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.*
import android.widget.*
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Base64

object Portal {
    const val HOST = "192.168.1.254"
    const val PORT = 8090
    const val URL = "https://$HOST:$PORT/httpclient.html"
}

/**
 * The real ABES portal's certificate (Sophos "Appliance_Certificate_1SyA7o7oLpUKbPe", self-signed,
 * valid until 2036). Anyone can run a Wi-Fi with a server at 192.168.1.254:8090, so the password is
 * only ever sent to a server presenting THIS key. If the college replaces the certificate, update
 * [PIN] with the output of:
 *   openssl s_client -connect 192.168.1.254:8090 </dev/null | openssl x509 -pubkey -noout \
 *     | openssl pkey -pubin -outform der | openssl dgst -sha256 -binary | base64
 */
object PortalCert {
    const val PIN = "SSIFdi4Ic8rEw9NUOAzmAx+RfYF4HYlr5UBrpMnoOeA=" // SHA-256 of the public key, base64

    fun pinOf(cert: X509Certificate): String =
        Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(cert.publicKey.encoded))

    fun isPortal(cert: X509Certificate?) = cert != null && pinOf(cert) == PIN
}

class PortalActivity : Activity() {
    private var submitted = false
    private var blocked = false // certificate rejected: never fill anything in on this screen
    private lateinit var web: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; fitsSystemWindows = true }
        val status = TextView(this).apply { text = "Opening ABES login portal…"; val pad = (12 * resources.displayMetrics.density).toInt(); setPadding(pad, pad, pad, pad) }
        web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = object : WebViewClient() {
                // The campus firewall's certificate is self-signed, so the WebView always reports an error.
                // Proceed ONLY for the portal address presenting the pinned ABES certificate.
                override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                    val uri = Uri.parse(error.url)
                    val isPortalAddress = uri.host == Portal.HOST && uri.port == Portal.PORT
                    if (isPortalAddress && PortalCert.isPortal(x509(error.certificate))) return handler.proceed()
                    handler.cancel()
                    blocked = true
                    status.text = if (isPortalAddress) "This is not the real ABES login page (its security certificate doesn't match). Blocked to protect your password."
                        else "Blocked an untrusted certificate from ${uri.host}"
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
                    if (submitted || blocked || Uri.parse(url).host != Portal.HOST) return
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

    private fun x509(cert: SslCertificate): X509Certificate? =
        if (Build.VERSION.SDK_INT >= 29) cert.x509Certificate
        else SslCertificate.saveState(cert).getByteArray("x509-certificate")?.let { // what x509Certificate does on 29+
            runCatching { CertificateFactory.getInstance("X.509").generateCertificate(it.inputStream()) as X509Certificate }.getOrNull()
        }

    override fun onDestroy() {
        web.destroy()
        super.onDestroy()
    }
}
