package com.marco90.mlinfotainment.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

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

    suspend fun refresh(lat: Double, lon: Double) {
        val now = System.currentTimeMillis()
        if (now - lastFetch < 15 * 60_000L && state.value.temp != null) return
        lastFetch = now
        val result = withContext(Dispatchers.IO) {
            try { fetch(lat, lon)?.copy(place = placeName(lat, lon)) } catch (e: Exception) { null }
        }
        if (result != null) {
            state.value = result
        } else {
            lastFetch = 0L
            state.value = state.value.copy(failed = true)
        }
    }

    private var placeKey = ""
    private var placeCache: String? = null

    /** Nome della località (città), da un servizio gratuito senza chiave; resta quello vecchio se non risponde. */
    private fun placeName(lat: Double, lon: Double): String? {
        val key = "${Math.round(lat * 20)}:${Math.round(lon * 20)}"
        if (key == placeKey && placeCache != null) return placeCache
        return try {
            val url = URL(
                "https://api.bigdatacloud.net/data/reverse-geocode-client?latitude=$lat&longitude=$lon&localityLanguage=it"
            )
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 6000
            conn.readTimeout = 6000
            try {
                val j = JSONObject(conn.inputStream.bufferedReader().readText())
                val name = j.optString("city").ifBlank { j.optString("locality") }.ifBlank { j.optString("principalSubdivision") }
                if (name.isBlank()) placeCache else {
                    placeKey = key
                    placeCache = name
                    name
                }
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            placeCache
        }
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
