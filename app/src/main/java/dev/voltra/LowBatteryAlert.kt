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

object LowBatteryAlert {
    private const val CHANNEL = "low_battery_alerts"
    internal const val NOTIFICATION_ID = 83
    const val HYSTERESIS_PERCENT = 3

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences("battery", Context.MODE_PRIVATE)

    fun enabled(context: Context): Boolean = prefs(context).getBoolean("low_alert", false)

    fun threshold(context: Context): Int = prefs(context).getInt("low_threshold", 20).coerceIn(5, 30)

    fun setEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean("low_alert", value).remove("low_alert_sent").apply()
    }

    fun setThreshold(context: Context, value: Int) {
        prefs(context).edit().putInt("low_threshold", value.coerceIn(5, 30)).remove("low_alert_sent").apply()
    }

    internal fun wasSent(context: Context): Boolean = prefs(context).getBoolean("low_alert_sent", false)

    @Synchronized
    fun onReading(context: Context, reading: Reading): Boolean {
        val store = prefs(context)
        val limit = threshold(context)
        if (reading.plugged || reading.level > limit + HYSTERESIS_PERCENT) {
            if (store.getBoolean("low_alert_sent", false)) {
                store.edit().putBoolean("low_alert_sent", false).apply()
            }
            return false
        }
        if (!enabled(context) || reading.level < 0 || reading.level > limit || store.getBoolean("low_alert_sent", false)) {
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
                    context.getString(R.string.channel_low_alert),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
            )
        }
        val open = PendingIntent.getActivity(
            context,
            5,
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
            .setSmallIcon(R.drawable.ic_stat_low_battery)
            .setContentTitle(context.getString(R.string.notif_low_title, reading.level))
            .setContentText(context.getString(R.string.notif_low_body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
        store.edit().putBoolean("low_alert_sent", true).apply()
        return true
    }
}
