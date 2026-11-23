package com.marco90.mlinfotainment.core

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.DetectedActivity
import com.marco90.mlinfotainment.data.CarSettings

/** Accende o spegne i rilevatori in base alle scelte e ai permessi. */
object Triggers {
    fun syncAll(ctx: Context, fromBackground: Boolean = false) {
        try { syncActivity(ctx) } catch (e: Exception) { }
        try { DriveMonitorService.sync(ctx, fromBackground) } catch (e: Exception) { }
    }

    private fun pending(app: Context): PendingIntent {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
        return PendingIntent.getBroadcast(app, 7, Intent(app, DrivingTransitionReceiver::class.java), flags)
    }

    private fun syncActivity(ctx: Context) {
        val app = ctx.applicationContext
        val s = CarSettings.get(app)
        val permitted = Build.VERSION.SDK_INT < 29 ||
            ContextCompat.checkSelfPermission(app, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
        val client = ActivityRecognition.getClient(app)
        try {
            if (s.autoStart && s.trigActivity && permitted) {
                val t = ActivityTransition.Builder()
                    .setActivityType(DetectedActivity.IN_VEHICLE)
                    .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                    .build()
                client.requestActivityTransitionUpdates(ActivityTransitionRequest(listOf(t)), pending(app))
            } else {
                client.removeActivityTransitionUpdates(pending(app))
            }
        } catch (e: SecurityException) {
            // permesso mancante
        }
    }
}
