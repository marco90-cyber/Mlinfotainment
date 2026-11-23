package com.marco90.mlinfotainment.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Priority
import kotlinx.coroutines.flow.MutableStateFlow

data class SpeedState(val speedKmh: Float? = null, val lat: Double? = null, val lon: Double? = null)

/** Posizione e velocità dal GPS, finché la schermata è visibile. */
object SpeedRepo {
    val state = MutableStateFlow(SpeedState())
    private var client: FusedLocationProviderClient? = null
    private var callback: LocationCallback? = null

    fun hasPermission(ctx: Context): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun start(ctx: Context) {
        if (callback != null || !hasPermission(ctx)) return
        val c = LocationServices.getFusedLocationProviderClient(ctx.applicationContext)
        val cb = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val l = result.lastLocation ?: return
                val sp = if (l.hasSpeed()) l.speed * 3.6f else null
                state.value = SpeedState(sp, l.latitude, l.longitude)
            }
        }
        try {
            c.requestLocationUpdates(LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L).build(), cb, Looper.getMainLooper())
            client = c
            callback = cb
            c.lastLocation.addOnSuccessListener { l ->
                if (l != null && state.value.lat == null) {
                    state.value = state.value.copy(lat = l.latitude, lon = l.longitude)
                }
            }
        } catch (e: SecurityException) {
            // permesso tolto mentre l'app era aperta
        }
    }

    fun stop() {
        val cb = callback
        if (cb != null) client?.removeLocationUpdates(cb)
        callback = null
        client = null
    }
}
