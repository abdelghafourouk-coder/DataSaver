package com.example.datasaver

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ScreenService : Service() {

    private lateinit var prefs: Prefs
    private lateinit var exec: ExecutorService
    private val handler = Handler(Looper.getMainLooper())
    private var wakeLock: PowerManager.WakeLock? = null

    private val applyRunnable = Runnable {
        exec.execute { applyOff() }
        releaseWakeLock()
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> scheduleApply()
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> {
                    handler.removeCallbacks(applyRunnable)
                    releaseWakeLock()
                    exec.execute { restore() }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        exec = Executors.newSingleThreadExecutor()
        createChannel()
        val n = buildNotification()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, n)
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        try {
            unregisterReceiver(receiver)
        } catch (_: Exception) {
        }
        handler.removeCallbacks(applyRunnable)
        releaseWakeLock()
        exec.execute { restore() }
        exec.shutdown()
        super.onDestroy()
    }

    private fun scheduleApply() {
        handler.removeCallbacks(applyRunnable)
        val delayMs = prefs.delaySec * 1000L
        if (delayMs > 0) {
            val pm = getSystemService(PowerManager::class.java)
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "datasaver:delay").apply {
                acquire(delayMs + 10_000L)
            }
        }
        handler.postDelayed(applyRunnable, delayMs)
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let { if (it.isHeld) it.release() }
        } catch (_: Exception) {
        }
        wakeLock = null
    }

    private fun applyOff() {
        if (!ShellRunner.granted()) return

        val am = getSystemService(AudioManager::class.java)
        if (prefs.skipMusic && am.isMusicActive) return
        if (prefs.skipCharging && isCharging()) return

        var did = false

        if (prefs.data && isDataOn() && !prefs.offData) {
            if (ShellRunner.run("svc data disable")) {
                prefs.offData = true
                did = true
            }
        }
        if (prefs.wifi && isWifiOn() && !prefs.offWifi) {
            if (ShellRunner.run("svc wifi disable")) {
                prefs.offWifi = true
                did = true
            }
        }
        if (prefs.bluetooth && isBluetoothOn() && !prefs.offBluetooth) {
            if (ShellRunner.run("svc bluetooth disable")) {
                prefs.offBluetooth = true
                did = true
            }
        }
        if (prefs.silent && !prefs.offSilent) {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm.isNotificationPolicyAccessGranted) {
                prefs.prevRinger = am.ringerMode
                am.ringerMode = AudioManager.RINGER_MODE_SILENT
                prefs.offSilent = true
                did = true
            }
        }

        if (did) prefs.count = prefs.count + 1
    }

    private fun restore() {
        if (prefs.offData) {
            if (ShellRunner.run("svc data enable")) prefs.offData = false
        }
        if (prefs.offWifi) {
            if (ShellRunner.run("svc wifi enable")) prefs.offWifi = false
        }
        if (prefs.offBluetooth) {
            if (ShellRunner.run("svc bluetooth enable")) prefs.offBluetooth = false
        }
        if (prefs.offSilent) {
            try {
                val am = getSystemService(AudioManager::class.java)
                am.ringerMode = prefs.prevRinger
            } catch (_: Exception) {
            }
            prefs.offSilent = false
        }
    }

    private fun isDataOn(): Boolean =
        try {
            Settings.Global.getInt(contentResolver, "mobile_data", 1) == 1
        } catch (_: Exception) {
            true
        }

    private fun isWifiOn(): Boolean =
        try {
            applicationContext.getSystemService(WifiManager::class.java).isWifiEnabled
        } catch (_: Exception) {
            false
        }

    private fun isBluetoothOn(): Boolean =
        try {
            getSystemService(BluetoothManager::class.java).adapter?.isEnabled == true
        } catch (_: Exception) {
            false
        }

    private fun isCharging(): Boolean =
        try {
            getSystemService(BatteryManager::class.java).isCharging
        } catch (_: Exception) {
            false
        }

    private fun createChannel() {
        val ch = NotificationChannel(
            CHANNEL_ID,
            "خدمة التوفير",
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    private fun buildNotification(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("مُوفر البيانات يعمل")
            .setContentText("يراقب قفل الشاشة وفتحها")
            .setSmallIcon(android.R.drawable.ic_lock_idle_low_battery)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "datasaver"
        private const val NOTIF_ID = 1
    }
}
