package com.divit.abesclient

import android.annotation.SuppressLint
import android.net.Network
import android.util.Log
import java.net.URL
import java.net.URLEncoder
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/**
 * Talks to the Sophos portal's login.xml / logout.xml directly, without opening the login page.
 * PortalActivity (the WebView) stays as the fallback when this doesn't work.
 */
object DirectLogin {
    enum class Outcome { SIGNED_IN, WRONG_PASSWORD, FAILED }
    data class Result(val outcome: Outcome, val message: String)

    private const val TAG = "DirectLogin"

    /** Signs in over [network] (the ABES Wi-Fi), even if Android routes other traffic over mobile data. */
    fun login(network: Network, username: String, password: String): Result = try {
        parse(post(network, "login.xml", "mode" to "191", "username" to username, "password" to password))
            .also { Log.i(TAG, "Login result: ${it.outcome}") }
    } catch (e: Exception) {
        Log.w(TAG, "Login request failed: ${e.javaClass.simpleName}") // never log the body: it holds the password
        Result(Outcome.FAILED, "couldn't reach the login page")
    }

    /** Signs out over [network]. True when the portal confirms (it replies with status LOGIN). */
    fun logout(network: Network, username: String): Boolean = try {
        (tag(post(network, "logout.xml", "mode" to "193", "username" to username), "status") == "LOGIN")
            .also { Log.i(TAG, "Logout confirmed: $it") }
    } catch (e: Exception) {
        Log.w(TAG, "Logout request failed: ${e.javaClass.simpleName}")
        false
    }

    /** Reads Sophos's reply, e.g. <status><![CDATA[LIVE]]></status><message><![CDATA[You are signed in as ...]]></message>. */
    fun parse(xml: String): Result {
        val status = tag(xml, "status")
        val message = tag(xml, "message")
        return when {
            status.equals("LIVE", true) || message.contains("signed in", true) -> Result(Outcome.SIGNED_IN, message)
            message.contains("invalid", true) || message.contains("password", true) -> Result(Outcome.WRONG_PASSWORD, message)
            else -> Result(Outcome.FAILED, message.ifBlank { "unexpected reply from the login page" })
        }
    }

    private fun post(network: Network, path: String, vararg fields: Pair<String, String>): String {
        val conn = network.openConnection(URL("https://${Portal.HOST}:${Portal.PORT}/$path")) as HttpsURLConnection
        conn.sslSocketFactory = portalOnlySsl.socketFactory
        conn.hostnameVerifier = HostnameVerifier { host, _ -> host == Portal.HOST }
        conn.instanceFollowRedirects = false
        conn.connectTimeout = 5000
        conn.readTimeout = 5000
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        val body = form(*fields, "a" to System.currentTimeMillis().toString(), "producttype" to "0")
        try {
            conn.outputStream.use { it.write(body.toByteArray()) }
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun tag(xml: String, name: String) =
        Regex("<$name>\\s*(?:<!\\[CDATA\\[)?(.*?)(?:]]>)?\\s*</$name>", RegexOption.DOT_MATCHES_ALL)
            .find(xml)?.groupValues?.get(1)?.trim() ?: ""

    private fun form(vararg fields: Pair<String, String>) =
        fields.joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, "UTF-8")}" }

    // The portal's certificate is self-signed, so no normal check can pass. This accept-anything
    // trust manager is attached only to the portal connections above (never installed globally),
    // so every other HTTPS connection in the app keeps normal certificate checks.
    // ponytail: trusts any cert at 192.168.1.254:8090; pin the cert on first login if spoofing matters.
    @SuppressLint("CustomX509TrustManager", "TrustAllX509TrustManager")
    private val portalOnlySsl: SSLContext = SSLContext.getInstance("TLS").apply {
        init(null, arrayOf(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }), null)
    }
}
