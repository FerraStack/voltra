package dev.voltra

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Handles the notification "Stop" action via broadcast so a stale notification
 * left behind after process death never attempts to start the service or crash
 * with IllegalStateException.
 */
class MonitorStopReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != MonitorService.ACTION_STOP) return
        runCatching {
            MonitorService.stop(context)
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.cancel(MonitorService.NOTIFICATION_ID)
        }.onFailure { error ->
            Log.w("VoltraMonitor", "Failed to stop monitor from notification", error)
        }
    }
}

/** Explicit, visible monitoring without a wake lock. Android can still suspend or stop it. */
class MonitorService : Service() {
    private lateinit var worker: HandlerThread
    private lateinit var handler: Handler
    private lateinit var repo: BatteryRepository
    private val alive = AtomicBoolean(false)
    private var lastBatteryEvent = 0L

    private val eventReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action ?: return
            val isPowerTransition =
                action == Intent.ACTION_POWER_CONNECTED || action == Intent.ACTION_POWER_DISCONNECTED
            if (action == Intent.ACTION_BATTERY_CHANGED) {
                val now = SystemClock.elapsedRealtime()
                if (now - lastBatteryEvent < 15_000L) return
                lastBatteryEvent = now
            }
            handler.post {
                if (!alive.get()) return@post
                runCatching {
                    val reading = repo.sample()
                    if (isPowerTransition) {
                        updateNotification(reading)
                    }
                }.onFailure { error ->
                    Log.w(TAG, "Event-triggered sample failed (action=$action)", error)
                }
            }
        }
    }

    private val sampleTask = object : Runnable {
        override fun run() {
            if (!alive.get()) return
            try {
                val reading = repo.sample()
                updateNotification(reading)
            } catch (error: Exception) {
                Log.e(TAG, "Monitoring stopped after a sampling error", error)
                alive.set(false)
                _isRunning.value = false
                stopSelf()
            } finally {
                if (alive.get()) handler.postDelayed(this, 30_000L)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL,
                    getString(R.string.channel_monitor),
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
        }
        repo = BatteryRepository.getInstance(this)
        worker = HandlerThread("voltra-monitor").apply { start() }
        handler = Handler(worker.looper)
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(eventReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(eventReceiver, filter)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            alive.set(false)
            _isRunning.value = false
            stopSelf()
            return START_NOT_STICKY
        }
        val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(getString(R.string.notif_monitor_active)),
            serviceType,
        )
        if (alive.compareAndSet(false, true)) {
            _isRunning.value = true
            handler.post(sampleTask)
        }
        return START_NOT_STICKY
    }

    private fun updateNotification(reading: Reading) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val level = if (reading.level >= 0) "${reading.level}%" else getString(R.string.placeholder_dash)
        val tempUnit = AppPreferences.temperatureUnit(this)
        val temperature = reading.temperature?.let { " · ${tempUnit.format(this, it)}" } ?: ""
        val text = getString(R.string.notif_monitor_status, level, temperature)
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun buildNotification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this,
            2,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getBroadcast(
            this,
            3,
            Intent(this, MonitorStopReceiver::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, CHANNEL)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        val stopAction = Notification.Action.Builder(
            null,
            getString(R.string.notif_action_stop),
            stop,
        ).build()
        return builder
            .setSmallIcon(R.drawable.ic_stat_voltra)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(stopAction)
            .build()
    }

    override fun onDestroy() {
        alive.set(false)
        _isRunning.value = false
        runCatching { unregisterReceiver(eventReceiver) }.onFailure { error ->
            Log.w(TAG, "unregisterReceiver failed", error)
        }
        handler.removeCallbacks(sampleTask)
        handler.post { worker.quitSafely() }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "VoltraMonitor"
        private const val CHANNEL = "battery_monitor"
        internal const val NOTIFICATION_ID = 81
        internal const val ACTION_STOP = "dev.voltra.STOP_MONITOR"

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        fun start(context: Context) {
            val intent = Intent(context, MonitorService::class.java)
            if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            _isRunning.value = false
            context.stopService(Intent(context, MonitorService::class.java))
        }
    }
}
