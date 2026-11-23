package com.divit.abesclient

import android.app.*
import android.content.*
import android.net.*
import android.os.Build
import androidx.core.app.NotificationCompat
import java.net.InetSocketAddress
import java.util.concurrent.Executors

/**
 * Detects the ABES network by checking whether the portal host is reachable on the Wi-Fi network,
 * instead of reading the SSID. Since Android 12, apps running in the background get the SSID as
 * "<unknown ssid>" unless they hold background location, so SSID matching silently never fired.
 */
class WifiMonitorService : Service() {
    private lateinit var cm: ConnectivityManager
    private val channelId = "abes_wifi_status"
    private val io = Executors.newSingleThreadExecutor()
    @Volatile private var lastState = ""

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = check(network)
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = check(network, caps)
        override fun onLost(network: Network) = setState("lost", "Wi-Fi disconnected", false)
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIF_ID, notification("Waiting for ABES Wi-Fi", false))
        cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val request = NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build()
        cm.registerNetworkCallback(request, callback)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY

    override fun onDestroy() {
        runCatching { cm.unregisterNetworkCallback(callback) }
        io.shutdownNow()
        super.onDestroy()
    }

    private fun check(network: Network, caps: NetworkCapabilities? = null) {
        io.execute {
            val c = caps ?: cm.getNetworkCapabilities(network) ?: return@execute
            if (!c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return@execute
            if (!portalReachable(network)) {
                setState("other", "Connected to a non-ABES Wi-Fi", false)
            } else if (c.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
                setState("online", "ABES Wi-Fi: signed in and online", false)
            } else {
                setState("needs_login", "ABES Wi-Fi needs sign-in. Tap to log in.", true)
            }
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
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIF_ID, notification(message, open))
    }

    private fun notification(message: String, open: Boolean): Notification {
        val intent = Intent(this, MainActivity::class.java).putExtra("open_portal", open)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(this, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("ABES Wi-Fi Client").setContentText(message)
            .setContentIntent(pending).setOngoing(true).setOnlyAlertOnce(!open)
            .setPriority(if (open) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_LOW).build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(channelId, "ABES Wi-Fi status", NotificationManager.IMPORTANCE_DEFAULT)
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?) = null

    companion object { private const val NOTIF_ID = 41 }
}
