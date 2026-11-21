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

object TemperatureAlert {
    private const val CHANNEL = "temperature_alerts"
    internal const val NOTIFICATION_ID = 82
    const val HYSTERESIS_C = 2f

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences("battery", Context.MODE_PRIVATE)

    fun enabled(context: Context): Boolean = prefs(context).getBoolean("temp_alert", false)

    fun threshold(context: Context): Int = prefs(context).getInt("temp_threshold", 40).coerceIn(35, 50)

    fun setEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean("temp_alert", value).remove("temp_alert_sent").apply()
    }

    fun setThreshold(context: Context, value: Int) {
        prefs(context).edit().putInt("temp_threshold", value.coerceIn(35, 50)).remove("temp_alert_sent").apply()
    }

    internal fun wasSent(context: Context): Boolean = prefs(context).getBoolean("temp_alert_sent", false)

    @Synchronized
    fun onReading(context: Context, reading: Reading): Boolean {
        val temperature = reading.temperature ?: return false
        val store = prefs(context)
        val limit = threshold(context)
        if (temperature <= limit - HYSTERESIS_C) {
            if (store.getBoolean("temp_alert_sent", false)) {
                store.edit().putBoolean("temp_alert_sent", false).apply()
            }
            return false
        }
        if (!enabled(context) || temperature < limit || store.getBoolean("temp_alert_sent", false)) {
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
                    context.getString(R.string.channel_temp_alert),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
            )
        }
        val open = PendingIntent.getActivity(
            context,
            4,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(context, CHANNEL)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }
        val formattedTemp = AppPreferences.temperatureUnit(context).format(context, temperature)
        val notification = builder
            .setSmallIcon(R.drawable.ic_stat_temp)
            .setContentTitle(context.getString(R.string.notif_temp_title, formattedTemp))
            .setContentText(context.getString(R.string.notif_temp_body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
        store.edit().putBoolean("temp_alert_sent", true).apply()
        return true
    }
}
