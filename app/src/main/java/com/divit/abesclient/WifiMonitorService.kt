package com.divit.abesclient

import android.app.*
import android.content.*
import android.net.*
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors

/**
 * Detects the campus network by checking that the portal address presents the pinned campus certificate,
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
    private val throttle = LoginThrottle()
    private var probe: Triple<Network, Boolean, Long>? = null // (network, isCampus, when); io thread only
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
        current = notification("Waiting for campus Wi-Fi", false)
        startForeground(NOTIF_ID, current!!)
        cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val request = NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build()
        cm.registerNetworkCallback(request, callback)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Every startForegroundService() call must be answered with startForeground(), even when the
        // service is already running, or Android kills the app (seen on Android 15 after tapping Save).
        startForeground(NOTIF_ID, current ?: notification("Waiting for campus Wi-Fi", false))
        when (intent?.action) {
            ACTION_SIGN_OUT -> io.execute { signOut() }
            // The user asked to sign in (Save, "Sign in now", or the login page finished): send a real login
            // now. Don't trust Android's "online" flag here: some phones (seen on Android 17) keep it for a
            // while after a sign-out. If already signed in, the portal just confirms it.
            ACTION_RETRY -> io.execute {
                lastChecked = null
                triedNetwork = null
                signedOut = null
                lastState = ""
                val network = wifi ?: return@execute
                if (portalReachable(network)) autoLogin(network, manual = true) else check(network)
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
            if (!cachedProbe(network)) {
                setState("other", "Connected to a Wi-Fi that isn't campus Wi-Fi", false)
            } else if (c.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
                triedNetwork = null // online: if the session expires later on this connection, sign in again
                setState("online", "Campus Wi-Fi: signed in and online", false)
            } else {
                autoLogin(network)
            }
        }
    }

    /** Tries the direct login once per Wi-Fi connection; the notification falls back to the login page. */
    private fun autoLogin(network: Network, manual: Boolean = false) {
        val user = SecurePrefs.username(this)
        val pass = SecurePrefs.password(this)
        if (user.isEmpty() || pass.isEmpty()) return setState("needs_login", "Campus Wi-Fi needs sign-in. Tap to log in.", true)
        // onCapabilitiesChanged fires often (e.g. signal strength). Retrying a wrong password could lock the account.
        if (triedNetwork == network) return
        if (!manual && !throttle.allow()) {
            android.util.Log.w("WifiMonitor", "Login skipped: too many recent attempts")
            return
        }
        triedNetwork = network
        setState("signing_in", "Signing in to campus Wi-Fi…", false)
        val result = DirectLogin.login(network, user, pass)
        when (result.outcome) {
            DirectLogin.Outcome.SIGNED_IN -> {
                setState("signed_in", "Signed in to campus Wi-Fi", false)
                cm.reportNetworkConnectivity(network, true) // ask Android to re-check internet now
            }
            DirectLogin.Outcome.WRONG_PASSWORD ->
                setState("wrong_password", "Wrong username or password. Open the app to fix it, or tap to open the login page.", true)
            DirectLogin.Outcome.UNTRUSTED -> // e.g. a fake hotspot imitating the portal address
                setState("untrusted", "This Wi-Fi's login page is not the real campus one (security certificate mismatch). Your password was not sent.", false)
            DirectLogin.Outcome.FAILED ->
                setState("failed", "Automatic sign-in failed (${result.message}). Tap to open the login page.", true)
        }
    }

    private fun signOut() {
        val network = wifi
        if (network == null || !portalReachable(network)) return setState("signout_failed", "Not connected to campus Wi-Fi", false)
        if (DirectLogin.logout(network, SecurePrefs.username(this))) {
            signedOut = network
            setState("signed_out", "Signed out of campus Wi-Fi. You'll be signed in again next time you connect.", false)
            cm.reportNetworkConnectivity(network, false) // let Android notice there's no internet now
        } else {
            setState("signout_failed", "Sign-out failed. Tap to open the login page.", true)
        }
    }

    /** Automatic re-checks reuse a recent answer for 60 s, so a flapping connection doesn't re-probe the portal. */
    private fun cachedProbe(network: Network): Boolean {
        val p = probe
        if (p != null && p.first == network && System.currentTimeMillis() - p.third < 60_000) return p.second
        return portalReachable(network).also { probe = Triple(network, it, System.currentTimeMillis()) }
    }

    /** True only on the real campus Wi-Fi (pinned portal certificate); on any other Wi-Fi the app stays asleep. */
    private fun portalReachable(network: Network) = DirectLogin.isCampusPortal(network)

    private fun setState(state: String, message: String, open: Boolean) {
        if (state == lastState) return
        lastState = state
        MonitorStatus.publish(state, message)
        val n = notification(message, open).also { current = it }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIF_ID, n)
    }

    private fun notification(message: String, open: Boolean): Notification {
        val intent = Intent(this, MainActivity::class.java).putExtra("open_portal", open)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(this, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_wifi).setColor(0xFF1E7F4F.toInt())
            .setContentTitle(getString(R.string.app_name)).setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(pending).setOngoing(true).setOnlyAlertOnce(!open)
            .setPriority(if (open) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_LOW).build()
    }

    private fun createChannel() {
        val channel = NotificationChannel(channelId, "Wi-Fi status", NotificationManager.IMPORTANCE_DEFAULT)
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

/** The monitor's latest state, so the open app screen can show it live (same process). */
object MonitorStatus {
    @Volatile var state = ""; private set
    @Volatile var message = ""; private set
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private val listeners = java.util.concurrent.CopyOnWriteArraySet<() -> Unit>()

    fun publish(state: String, message: String) {
        this.state = state; this.message = message
        main.post { listeners.forEach { it() } }
    }
    fun listen(l: () -> Unit) = listeners.add(l)
    fun unlisten(l: () -> Unit) = listeners.remove(l)
}

/** Restarts monitoring after a reboot or an app update, if credentials are saved. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        if (SecurePrefs.username(context).isNotEmpty()) WifiMonitorService.start(context)
    }
}
