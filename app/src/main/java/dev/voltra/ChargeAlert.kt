package dev.voltra

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

/** Best-effort reminder based on observations. It never controls the charger. */
object ChargeAlert {
    private const val CHANNEL = "charge_reminders"
    internal const val NOTIFICATION_ID = 80

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences("battery", Context.MODE_PRIVATE)

    fun enabled(context: Context): Boolean = prefs(context).getBoolean("charge_alert", false)

    fun threshold(context: Context): Int = prefs(context).getInt("alert_threshold", 80).coerceIn(60, 100)

    fun setThreshold(context: Context, value: Int) {
        prefs(context).edit().putInt("alert_threshold", value.coerceIn(60, 100)).remove("alert_sent").apply()
    }

    fun setEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean("charge_alert", value).remove("alert_sent").apply()
    }

    internal fun wasSent(context: Context): Boolean = prefs(context).getBoolean("alert_sent", false)

    @Synchronized
    fun onReading(context: Context, reading: Reading): Boolean {
        val store = prefs(context)
        if (!reading.plugged) {
            if (store.getBoolean("alert_sent", false)) {
                store.edit().putBoolean("alert_sent", false).apply()
            }
            return false
        }
        if (!enabled(context) || reading.level < threshold(context) || store.getBoolean("alert_sent", false)) {
            return false
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!manager.areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL,
                    context.getString(R.string.channel_charge_alert),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
            )
        }
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(context, CHANNEL)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }
        val notification = builder
            .setSmallIcon(R.drawable.ic_stat_voltra)
            .setContentTitle(context.getString(R.string.notif_charge_title, reading.level))
            .setContentText(context.getString(R.string.notif_charge_body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
        store.edit().putBoolean("alert_sent", true).apply()
        return true
    }
}
