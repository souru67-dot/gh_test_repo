package com.souru.koyomi.data.weather

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.souru.koyomi.data.dataStore
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** A place chosen for the forecast (no device location involved). */
data class WeatherPlace(
    val name: String,
    val latitude: Double,
    val longitude: Double,
)

data class DailyWeather(
    val date: LocalDate,
    /** WMO weather interpretation code. */
    val code: Int,
    val maxC: Double,
    val minC: Double,
) {
    val emoji: String get() = weatherEmoji(code)
    val tempLabel: String
        get() = "${maxC.toInt()}°/${minC.toInt()}°"
}

/** WMO code → a compact glyph that stays legible at small sizes. */
fun weatherEmoji(code: Int): String = when (code) {
    0 -> "☀"
    1, 2 -> "🌤"
    3 -> "☁"
    45, 48 -> "🌫"
    in 51..57, in 61..67, in 80..82 -> "☂"
    in 71..77, 85, 86 -> "☃"
    95, 96, 99 -> "⚡"
    else -> "☁"
}

/**
 * Two-week daily forecast from Open-Meteo (keyless, free for non-commercial
 * volumes). The place is picked by name via the geocoding API — the app
 * itself never touches device location.
 */
class WeatherRepository(private val context: Context) {

    private object Keys {
        val PLACE_NAME = stringPreferencesKey("weather_place_name")
        val PLACE_LAT = stringPreferencesKey("weather_place_lat")
        val PLACE_LON = stringPreferencesKey("weather_place_lon")
        val CACHE_JSON = stringPreferencesKey("weather_cache_json")
        val CACHED_AT = stringPreferencesKey("weather_cached_at")
    }

    val place: Flow<WeatherPlace?> = context.dataStore.data.map { prefs ->
        val name = prefs[Keys.PLACE_NAME] ?: return@map null
        val lat = prefs[Keys.PLACE_LAT]?.toDoubleOrNull() ?: return@map null
        val lon = prefs[Keys.PLACE_LON]?.toDoubleOrNull() ?: return@map null
        WeatherPlace(name, lat, lon)
    }

    suspend fun setPlace(place: WeatherPlace?) {
        context.dataStore.edit { prefs ->
            if (place == null) {
                prefs.remove(Keys.PLACE_NAME)
                prefs.remove(Keys.PLACE_LAT)
                prefs.remove(Keys.PLACE_LON)
            } else {
                prefs[Keys.PLACE_NAME] = place.name
                prefs[Keys.PLACE_LAT] = place.latitude.toString()
                prefs[Keys.PLACE_LON] = place.longitude.toString()
            }
            prefs.remove(Keys.CACHE_JSON)
            prefs.remove(Keys.CACHED_AT)
        }
    }

    /** Cached forecast by date; empty until a place is set and fetched. */
    val forecastByDay: Flow<Map<LocalDate, DailyWeather>> =
        context.dataStore.data.map { prefs ->
            parseForecast(prefs[Keys.CACHE_JSON] ?: return@map emptyMap())
        }

    /** Re-fetches when the cache is older than [maxAgeMillis]. Silent on failure. */
    suspend fun refreshIfStale(maxAgeMillis: Long = 3 * 60 * 60 * 1000L) {
        val place = place.first() ?: return
        val cachedAt = context.dataStore.data.first()[Keys.CACHED_AT]?.toLongOrNull() ?: 0L
        if (System.currentTimeMillis() - cachedAt < maxAgeMillis) return
        val json = runCatching { fetchForecastJson(place) }.getOrNull() ?: return
        // Only store parseable payloads so the cache never wedges the UI.
        if (runCatching { parseForecast(json) }.getOrNull().isNullOrEmpty()) return
        context.dataStore.edit { prefs ->
            prefs[Keys.CACHE_JSON] = json
            prefs[Keys.CACHED_AT] = System.currentTimeMillis().toString()
        }
    }

    /** Name search via the Open-Meteo geocoding API (Japanese labels). */
    suspend fun searchPlaces(query: String): List<WeatherPlace> =
        withContext(Dispatchers.IO) {
            if (query.isBlank()) return@withContext emptyList()
            val url = "https://geocoding-api.open-meteo.com/v1/search" +
                "?name=${URLEncoder.encode(query.trim(), "UTF-8")}" +
                "&count=8&language=ja&format=json"
            val body = runCatching { httpGet(url) }.getOrNull()
                ?: return@withContext emptyList()
            runCatching {
                val results = JSONObject(body).optJSONArray("results")
                    ?: return@withContext emptyList()
                buildList {
                    for (i in 0 until results.length()) {
                        val o = results.getJSONObject(i)
                        val admin = o.optString("admin1")
                        val label = if (admin.isNotBlank() && admin != o.getString("name")) {
                            "$admin ${o.getString("name")}"
                        } else {
                            o.getString("name")
                        }
                        add(
                            WeatherPlace(
                                name = label,
                                latitude = o.getDouble("latitude"),
                                longitude = o.getDouble("longitude"),
                            ),
                        )
                    }
                }
            }.getOrDefault(emptyList())
        }

    private suspend fun fetchForecastJson(place: WeatherPlace): String =
        withContext(Dispatchers.IO) {
            httpGet(
                "https://api.open-meteo.com/v1/forecast" +
                    "?latitude=${place.latitude}&longitude=${place.longitude}" +
                    "&daily=weather_code,temperature_2m_max,temperature_2m_min" +
                    "&timezone=auto&forecast_days=14",
            )
        }

    private fun parseForecast(json: String): Map<LocalDate, DailyWeather> {
        val daily = JSONObject(json).optJSONObject("daily") ?: return emptyMap()
        val dates = daily.optJSONArray("time") ?: return emptyMap()
        val codes = daily.optJSONArray("weather_code") ?: return emptyMap()
        val maxes = daily.optJSONArray("temperature_2m_max") ?: return emptyMap()
        val mins = daily.optJSONArray("temperature_2m_min") ?: return emptyMap()
        val result = mutableMapOf<LocalDate, DailyWeather>()
        for (i in 0 until dates.length()) {
            if (codes.isNull(i) || maxes.isNull(i) || mins.isNull(i)) continue
            val date = runCatching { LocalDate.parse(dates.getString(i)) }.getOrNull() ?: continue
            result[date] = DailyWeather(
                date = date,
                code = codes.getInt(i),
                maxC = maxes.getDouble(i),
                minC = mins.getDouble(i),
            )
        }
        return result
    }

    private fun httpGet(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
