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
    private fun prefs(context: Context) = context.getSharedPreferences("battery", Context.MODE_PRIVATE)
    fun enabled(context: Context) = prefs(context).getBoolean("low_alert", false)
    fun threshold(context: Context) = prefs(context).getInt("low_threshold", 20).coerceIn(5, 30)
    fun setEnabled(context: Context, value: Boolean) { prefs(context).edit().putBoolean("low_alert", value).remove("low_alert_sent").apply() }
    fun setThreshold(context: Context, value: Int) { prefs(context).edit().putInt("low_threshold", value.coerceIn(5, 30)).remove("low_alert_sent").apply() }
    @Synchronized fun onReading(context: Context, reading: Reading) {
        val store = prefs(context)
        if (reading.plugged || reading.level > threshold(context) + 3) {
            if (store.getBoolean("low_alert_sent", false)) store.edit().putBoolean("low_alert_sent", false).apply()
            return
        }
        if (!enabled(context) || reading.level < 0 || reading.level > threshold(context) || store.getBoolean("low_alert_sent", false)) return
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!manager.areNotificationsEnabled()) return
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(NotificationChannel(CHANNEL, "Низкий заряд", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(context, 5, Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(context, CHANNEL) else Notification.Builder(context)
        manager.notify(83, builder.setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Низкий заряд: ${reading.level}%").setContentText("Достигнут установленный порог.")
            .setContentIntent(open).setAutoCancel(true).build())
        store.edit().putBoolean("low_alert_sent", true).apply()
    }
}
