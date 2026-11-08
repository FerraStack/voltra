package dev.voltra

import android.Manifest
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
    private fun prefs(context: Context) = context.getSharedPreferences("battery", Context.MODE_PRIVATE)
    fun enabled(context: Context) = prefs(context).getBoolean("charge_alert", false)
    fun threshold(context: Context) = prefs(context).getInt("alert_threshold", 80).coerceIn(60, 100)
    fun setThreshold(context: Context, value: Int) { prefs(context).edit().putInt("alert_threshold", value.coerceIn(60, 100)).remove("alert_sent").apply() }
    fun setEnabled(context: Context, value: Boolean) { prefs(context).edit().putBoolean("charge_alert", value).remove("alert_sent").apply() }
    @Synchronized fun onReading(context: Context, reading: Reading) {
        val prefs = prefs(context)
        if (!reading.plugged) { if (prefs.getBoolean("alert_sent", false)) prefs.edit().putBoolean("alert_sent", false).apply(); return }
        if (!enabled(context) || reading.level < threshold(context) || prefs.getBoolean("alert_sent", false)) return
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!manager.areNotificationsEnabled()) return
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(NotificationChannel(CHANNEL, "Напоминания о зарядке", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = if (Build.VERSION.SDK_INT >= 26) android.app.Notification.Builder(context, CHANNEL) else android.app.Notification.Builder(context)
        val notification = builder.setSmallIcon(android.R.drawable.ic_lock_idle_charging)
            .setContentTitle("Заряд достиг ${reading.level}%")
            .setContentText("Можно отключить зарядное устройство. Voltra не управляет зарядкой.")
            .setContentIntent(open).setAutoCancel(true).build()
        manager.notify(80, notification)
        prefs.edit().putBoolean("alert_sent", true).apply()
    }
}
