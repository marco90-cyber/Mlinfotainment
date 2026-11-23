package com.marco90.mlinfotainment.core

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.marco90.mlinfotainment.MainActivity
import com.marco90.mlinfotainment.data.CarSettings

/** Controlla la velocità GPS in sottofondo e apre la schermata auto quando superi la soglia. */
class DriveMonitorService : Service() {
    private var client: FusedLocationProviderClient? = null
    private var hits = 0

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val l = result.lastLocation ?: return
            if (!l.hasSpeed()) return
            val s = CarSettings.get(applicationContext)
            if (l.speed * 3.6f >= s.gpsThreshold) {
                hits++
                if (hits >= 2) {
                    hits = 0
                    AutoLauncher.launch(applicationContext, "gps")
                }
            } else {
                hits = 0
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Controllo guida", NotificationManager.IMPORTANCE_LOW))
        }
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("MLinfotainment")
            .setContentText("In ascolto: si apre da solo quando guidi")
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        try {
            val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
            ServiceCompat.startForeground(this, 11, n, type)
            val c = LocationServices.getFusedLocationProviderClient(this)
            client = c
            c.requestLocationUpdates(
                LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000L).build(),
                callback,
                Looper.getMainLooper()
            )
        } catch (e: Exception) {
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        client?.removeLocationUpdates(callback)
        client = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL = "drive_monitor"

        fun sync(ctx: Context) {
            val app = ctx.applicationContext
            val s = CarSettings.get(app)
            val permitted = ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val intent = Intent(app, DriveMonitorService::class.java)
            if (s.autoStart && s.trigGps && permitted) {
                ContextCompat.startForegroundService(app, intent)
            } else {
                app.stopService(intent)
            }
        }
    }
}
