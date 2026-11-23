package com.divit.abesclient

import android.app.*
import android.content.*
import android.net.*
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.net.InetSocketAddress
import java.util.concurrent.Executors

/**
 * Detects the ABES network by checking whether the portal host is reachable on the Wi-Fi network,
 * instead of reading the SSID. Since Android 12, apps running in the background get the SSID as
 * "<unknown ssid>" unless they hold background location, so SSID matching silently never fired.
 *
 * All checks and portal requests run one at a time on [io], so they never race each other.
 */
class WifiMonitorService : Service() {
    private lateinit var cm: ConnectivityManager
    private val channelId = "abes_wifi_status"
    private val io = Executors.newSingleThreadExecutor()
    @Volatile private var lastState = ""
    @Volatile private var current: Notification? = null // what the status notification shows now
    @Volatile private var wifi: Network? = null         // the current Wi-Fi connection, if any
    @Volatile private var triedNetwork: Network? = null // auto-login already attempted on this connection
    @Volatile private var signedOut: Network? = null    // user signed out: stay out until the next connection
    private var lastChecked: Triple<Network, Boolean, Boolean>? = null // (network, online, portal-flagged); io thread only

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            wifi = network
            check(network)
        }
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = check(network, caps)
        override fun onLost(network: Network) {
            io.execute { lastChecked = null }
            if (wifi == network) wifi = null
            triedNetwork = null
            signedOut = null
            setState("lost", "Wi-Fi disconnected", false)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        current = notification("Waiting for ABES Wi-Fi", false)
        startForeground(NOTIF_ID, current!!)
        cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val request = NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build()
        cm.registerNetworkCallback(request, callback)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Every startForegroundService() call must be answered with startForeground(), even when the
        // service is already running, or Android kills the app (seen on Android 15 after tapping Save).
        startForeground(NOTIF_ID, current ?: notification("Waiting for ABES Wi-Fi", false))
        when (intent?.action) {
            ACTION_SIGN_OUT -> io.execute { signOut() }
            // New credentials saved, or signed in via the login page: re-check and allow one fresh attempt now.
            ACTION_RETRY -> io.execute {
                lastChecked = null
                triedNetwork = null
                signedOut = null
                lastState = ""
                wifi?.let {
                    cm.reportNetworkConnectivity(it, true) // ask Android to re-check internet now
                    check(it)
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        runCatching { cm.unregisterNetworkCallback(callback) }
        io.shutdownNow()
        super.onDestroy()
    }

    private fun check(network: Network, caps: NetworkCapabilities? = null) {
        io.execute {
            val c = caps ?: cm.getNetworkCapabilities(network) ?: return@execute
            if (!c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return@execute
            // Capabilities change constantly (e.g. signal strength). Only re-check when the network, its
            // "online" status or Android's "login required" flag changes, to save battery.
            val key = Triple(network,
                c.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
                c.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL))
            if (key == lastChecked) return@execute
            lastChecked = key
            android.util.Log.i("WifiMonitor", "Checking Wi-Fi: online=${key.second} loginRequired=${key.third}")
            if (network == signedOut) return@execute // keep showing "Signed out"
            if (!portalReachable(network)) {
                setState("other", "Connected to a non-ABES Wi-Fi", false)
            } else if (c.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
                triedNetwork = null // online: if the session expires later on this connection, sign in again
                setState("online", "ABES Wi-Fi: signed in and online", false)
            } else {
                autoLogin(network)
            }
        }
    }

    /** Tries the direct login once per Wi-Fi connection; the notification falls back to the login page. */
    private fun autoLogin(network: Network) {
        val user = SecurePrefs.username(this)
        val pass = SecurePrefs.password(this)
        if (user.isEmpty() || pass.isEmpty()) return setState("needs_login", "ABES Wi-Fi needs sign-in. Tap to log in.", true)
        // onCapabilitiesChanged fires often (e.g. signal strength). Retrying a wrong password could lock the account.
        if (triedNetwork == network) return
        triedNetwork = network
        setState("signing_in", "Signing in to ABES Wi-Fi…", false)
        val result = DirectLogin.login(network, user, pass)
        when (result.outcome) {
            DirectLogin.Outcome.SIGNED_IN -> {
                setState("signed_in", "Signed in to ABES Wi-Fi", false)
                cm.reportNetworkConnectivity(network, true) // ask Android to re-check internet now
            }
            DirectLogin.Outcome.WRONG_PASSWORD ->
                setState("wrong_password", "Wrong username or password. Open the app to fix it, or tap to open the login page.", true)
            DirectLogin.Outcome.UNTRUSTED -> // e.g. a fake hotspot imitating the portal address
                setState("untrusted", "This Wi-Fi's login page is not the real ABES one (security certificate mismatch). Your password was not sent.", false)
            DirectLogin.Outcome.FAILED ->
                setState("failed", "Automatic sign-in failed (${result.message}). Tap to open the login page.", true)
        }
    }

    private fun signOut() {
        val network = wifi
        if (network == null || !portalReachable(network)) return setState("signout_failed", "Not connected to ABES Wi-Fi", false)
        if (DirectLogin.logout(network, SecurePrefs.username(this))) {
            signedOut = network
            setState("signed_out", "Signed out of ABES Wi-Fi. You'll be signed in again next time you connect.", false)
            cm.reportNetworkConnectivity(network, false) // let Android notice there's no internet now
        } else {
            setState("signout_failed", "Sign-out failed. Tap to open the login page.", true)
        }
    }

    /** True if the portal's host:port accepts a TCP connection over this specific Wi-Fi network. */
    private fun portalReachable(network: Network): Boolean = runCatching {
        network.socketFactory.createSocket().use {
            it.connect(InetSocketAddress(Portal.HOST, Portal.PORT), 1500)
        }
        true
    }.getOrDefault(false)

    private fun setState(state: String, message: String, open: Boolean) {
        if (state == lastState) return
        lastState = state
        val n = notification(message, open).also { current = it }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIF_ID, n)
    }

    private fun notification(message: String, open: Boolean): Notification {
        val intent = Intent(this, MainActivity::class.java).putExtra("open_portal", open)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(this, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_wifi)
            .setContentTitle("ABES Wi-Fi Client").setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(pending).setOngoing(true).setOnlyAlertOnce(!open)
            .setPriority(if (open) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_LOW).build()
    }

    private fun createChannel() {
        val channel = NotificationChannel(channelId, "ABES Wi-Fi status", NotificationManager.IMPORTANCE_DEFAULT)
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
    }

    override fun onBind(intent: Intent?) = null

    companion object {
        private const val NOTIF_ID = 41
        const val ACTION_SIGN_OUT = "com.divit.abesclient.SIGN_OUT"
        const val ACTION_RETRY = "com.divit.abesclient.RETRY"

        fun start(context: Context, action: String? = null) =
            ContextCompat.startForegroundService(context, Intent(context, WifiMonitorService::class.java).setAction(action))
    }
}

/** Restarts monitoring after a reboot or an app update, if credentials are saved. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        if (SecurePrefs.username(context).isNotEmpty()) WifiMonitorService.start(context)
    }
}
