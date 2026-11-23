package com.marco90.mlinfotainment.core

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.marco90.mlinfotainment.MainActivity
import com.marco90.mlinfotainment.data.CarSettings

/** Apre la schermata auto quando un segnale dice che stai guidando. */
object AutoLauncher {
    private const val CHANNEL = "auto_open"
    private const val NOTIF_ID = 21

    fun launch(ctx: Context, reason: String) {
        val app = ctx.applicationContext
        val s = CarSettings.get(app)
        if (!s.autoStart) return
        if (AppState.visible) return
        val now = System.currentTimeMillis()
        if (now - s.lastAutoLaunch < s.cooldownMin * 60_000L) return
        s.lastAutoLaunch = now

        val intent = Intent(app, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            .putExtra("reason", reason)

        // Con "Mostra sopra altre app" la schermata si apre direttamente
        if (Settings.canDrawOverlays(app)) {
            try {
                app.startActivity(intent)
                return
            } catch (e: Exception) {
                // si ripiega sulla notifica
            }
        }
        notifyOpen(app, intent)
    }

    private fun notifyOpen(app: Context, intent: Intent) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val nm = app.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Apertura automatica", NotificationManager.IMPORTANCE_HIGH))
        }
        val pi = PendingIntent.getActivity(app, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(app, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_directions)
            .setContentTitle("Stai guidando?")
            .setContentText("Tocca per aprire MLinfotainment")
            .setContentIntent(pi)
            .setFullScreenIntent(pi, true)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        try {
            nm.notify(NOTIF_ID, n)
        } catch (e: SecurityException) {
            // notifiche non consentite
        }
    }
}
