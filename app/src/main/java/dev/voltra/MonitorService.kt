package dev.voltra

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import java.util.concurrent.atomic.AtomicBoolean

/** Explicit, visible monitoring without a wake lock. Android can still suspend or stop it. */
class MonitorService : Service() {
    private lateinit var worker: HandlerThread
    private lateinit var handler: Handler
    private lateinit var repo: BatteryRepository
    private val alive = AtomicBoolean(false)
    private var lastBatteryEvent = 0L
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_BATTERY_CHANGED) {
                val now = android.os.SystemClock.elapsedRealtime()
                if (now - lastBatteryEvent < 15_000) return
                lastBatteryEvent = now
            }
            handler.post {
                if (alive.get()) try { repo.sample() } catch (error: Exception) {
                    android.util.Log.e("VoltraMonitor", "Screen-state sample failed", error)
                }
            }
        }
    }
    private val sample = object : Runnable {
        override fun run() {
            if (!alive.get()) return
            try {
                val reading = repo.sample()
                val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val level = if (reading.level >= 0) "${reading.level}%" else "—"
                val temperature = reading.temperature?.let { String.format(java.util.Locale.getDefault(), " · %.1f °C", it) } ?: ""
                manager.notify(NOTIFICATION_ID, notification("Заряд $level$temperature · мониторинг активен"))
            } catch (error: Exception) {
                android.util.Log.e("VoltraMonitor", "Monitoring stopped after a sampling error", error)
                alive.set(false); stopSelf()
            } finally { if (alive.get()) handler.postDelayed(this, 30_000) }
        }
    }
    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(NotificationChannel(CHANNEL, "Мониторинг батареи", NotificationManager.IMPORTANCE_LOW))
        repo = BatteryRepository(this)
        worker = HandlerThread("voltra-monitor").apply { start() }
        handler = Handler(worker.looper)
        val filter = IntentFilter().apply { addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_BATTERY_CHANGED) }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED) else registerReceiver(screenReceiver, filter)
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) { stopSelf(); return START_NOT_STICKY }
        startForeground(NOTIFICATION_ID, notification("Мониторинг активен"))
        if (alive.compareAndSet(false, true)) { running = true; handler.post(sample) }
        return START_STICKY
    }
    private fun notification(text: String): Notification {
        val open = PendingIntent.getActivity(this, 2, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 3, Intent(this, MonitorService::class.java).setAction(STOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL) else Notification.Builder(this)
        return builder.setSmallIcon(android.R.drawable.ic_lock_idle_charging).setContentTitle("Voltra").setContentText(text)
            .setContentIntent(open).setOngoing(true).addAction(android.R.drawable.ic_media_pause, "Остановить", stop).build()
    }
    override fun onDestroy() {
        alive.set(false); running = false
        unregisterReceiver(screenReceiver)
        handler.removeCallbacks(sample)
        handler.post { repo.close(); worker.quitSafely() }
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
    companion object {
        private const val CHANNEL = "battery_monitor"
        private const val NOTIFICATION_ID = 81
        private const val STOP = "dev.voltra.STOP_MONITOR"
        @Volatile var running = false
            private set
        fun start(context: Context) {
            val intent = Intent(context, MonitorService::class.java)
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent) else context.startService(intent)
        }
        fun stop(context: Context) { context.stopService(Intent(context, MonitorService::class.java)) }
    }
}
