package com.marco90.mlinfotainment.data

import android.content.Context
import android.location.Geocoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

data class WeatherState(
    val temp: Int? = null,
    val code: Int? = null,
    val min: Int? = null,
    val max: Int? = null,
    val place: String? = null,
    val failed: Boolean = false
)

/** Meteo attuale da Open-Meteo (gratuito, senza chiave). */
object WeatherRepo {
    val state = MutableStateFlow(WeatherState())
    private var lastFetch = 0L

    suspend fun refresh(ctx: Context, lat: Double, lon: Double) {
        // la località si riprova a ogni giro finché non c'è; il meteo ogni 15 minuti
        val place = withContext(Dispatchers.IO) { placeName(ctx, lat, lon) }
        if (place != null && place != state.value.place) state.value = state.value.copy(place = place)
        val now = System.currentTimeMillis()
        if (now - lastFetch < 15 * 60_000L && state.value.temp != null) return
        lastFetch = now
        val result = withContext(Dispatchers.IO) {
            try { fetch(lat, lon) } catch (e: Exception) { null }
        }
        if (result != null) {
            state.value = result.copy(place = state.value.place)
        } else {
            lastFetch = 0L
            state.value = state.value.copy(failed = true)
        }
    }

    private var placeKey = ""
    private var placeCache: String? = null

    /** Nome della località (città), da un servizio gratuito senza chiave; resta quello vecchio se non risponde. */
    private fun placeName(ctx: Context, lat: Double, lon: Double): String? {
        val key = "${Math.round(lat * 20)}:${Math.round(lon * 20)}"
        if (key == placeKey && placeCache != null) return placeCache
        // prima il servizio del telefono, poi uno gratuito online
        try {
            if (Geocoder.isPresent()) {
                @Suppress("DEPRECATION")
                val a = Geocoder(ctx.applicationContext, Locale.getDefault()).getFromLocation(lat, lon, 1)?.firstOrNull()
                val n = a?.locality ?: a?.subAdminArea ?: a?.adminArea
                if (!n.isNullOrBlank()) {
                    placeKey = key
                    placeCache = n
                    return n
                }
            }
        } catch (e: Exception) { }
        val name = osmName(lat, lon) ?: bigDataName(lat, lon)
        if (name != null) {
            placeKey = key
            placeCache = name
        }
        return name ?: placeCache
    }

    private fun getJson(u: String): JSONObject? = try {
        val conn = URL(u).openConnection() as HttpURLConnection
        conn.connectTimeout = 6000
        conn.readTimeout = 6000
        conn.setRequestProperty("User-Agent", "MLinfotainment/1.0 (Android)")
        try {
            JSONObject(conn.inputStream.bufferedReader().readText())
        } finally {
            conn.disconnect()
        }
    } catch (e: Exception) {
        null
    }

    private fun osmName(lat: Double, lon: Double): String? {
        val j = getJson(
            "https://nominatim.openstreetmap.org/reverse?format=jsonv2&zoom=12&accept-language=it&lat=$lat&lon=$lon"
        ) ?: return null
        val a = j.optJSONObject("address") ?: return null
        for (k in listOf("city", "town", "village", "municipality", "hamlet", "suburb", "county")) {
            val v = a.optString(k)
            if (v.isNotBlank()) return v
        }
        return null
    }

    private fun bigDataName(lat: Double, lon: Double): String? {
        val j = getJson(
            "https://api.bigdatacloud.net/data/reverse-geocode-client?latitude=$lat&longitude=$lon&localityLanguage=it"
        ) ?: return null
        return j.optString("city").ifBlank { j.optString("locality") }.ifBlank { j.optString("principalSubdivision") }
            .takeIf { it.isNotBlank() }
    }

    private fun fetch(lat: Double, lon: Double): WeatherState {
        val url = URL(
            "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                "&current=temperature_2m,weather_code&daily=temperature_2m_max,temperature_2m_min" +
                "&forecast_days=1&timezone=auto"
        )
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        try {
            val json = JSONObject(conn.inputStream.bufferedReader().readText())
            val cur = json.getJSONObject("current")
            val day = json.getJSONObject("daily")
            return WeatherState(
                temp = Math.round(cur.getDouble("temperature_2m")).toInt(),
                code = cur.getInt("weather_code"),
                min = Math.round(day.getJSONArray("temperature_2m_min").getDouble(0)).toInt(),
                max = Math.round(day.getJSONArray("temperature_2m_max").getDouble(0)).toInt()
            )
        } finally {
            conn.disconnect()
        }
    }
}
