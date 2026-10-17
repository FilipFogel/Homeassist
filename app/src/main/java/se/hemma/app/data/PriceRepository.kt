package se.hemma.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.util.Locale

data class LoadResult(val data: PriceData, val error: String?)

/**
 * Hämtar Nord Pools day-ahead-priser via elprisetjustnu.se (gratis, ingen API-nyckel)
 * och cachar varje dygn som en fil. Publicerade priser ändras inte, så ett dygn hämtas bara en gång.
 */
class PriceRepository(context: Context) {

    private val cacheDir = File(context.applicationContext.filesDir, "prices").apply { mkdirs() }

    suspend fun load(area: String, allowNetwork: Boolean = true): LoadResult = withContext(Dispatchers.IO) {
        val now = ZonedDateTime.now(STOCKHOLM)
        val todayDate = now.toLocalDate()
        val tomorrowDate = todayDate.plusDays(1)
        val errors = ArrayList<String>()

        val today = getDay(area, todayDate, allowNetwork, errors, isToday = true)
        // Morgondagens priser publiceras runt kl 13.
        val tryTomorrow = now.hour >= 13 || cacheFile(area, tomorrowDate).exists()
        val tomorrow = if (tryTomorrow) getDay(area, tomorrowDate, allowNetwork, errors, isToday = false) else emptyList()

        cleanup(todayDate)
        LoadResult(PriceData(area, today, tomorrow, System.currentTimeMillis()), errors.firstOrNull())
    }

    private fun getDay(
        area: String,
        date: LocalDate,
        allowNetwork: Boolean,
        errors: MutableList<String>,
        isToday: Boolean,
    ): List<PricePoint> {
        val file = cacheFile(area, date)
        if (file.exists()) {
            val cached = runCatching { parse(file.readText()) }.getOrNull()
            if (cached != null && cached.size >= MIN_POINTS) return cached
        }
        if (!allowNetwork) return emptyList()

        return try {
            val (code, body) = httpGet(url(area, date))
            when {
                code == 200 && body != null -> {
                    val points = parse(body)
                    if (points.size >= MIN_POINTS) {
                        val tmp = File(cacheDir, file.name + ".tmp")
                        tmp.writeText(body)
                        if (!tmp.renameTo(file)) {
                            file.writeText(body)
                            tmp.delete()
                        }
                    }
                    points
                }
                code == 404 -> {
                    if (isToday) errors.add("Dagens priser finns inte för $area än")
                    emptyList()
                }
                else -> {
                    errors.add("Kunde inte hämta elpriser (HTTP $code)")
                    emptyList()
                }
            }
        } catch (e: Exception) {
            errors.add("Ingen kontakt med elprisservern")
            emptyList()
        }
    }

    private fun cacheFile(area: String, date: LocalDate) = File(cacheDir, "${area}_$date.json")

    private fun cleanup(today: LocalDate) {
        val oldest = today.minusDays(2)
        cacheDir.listFiles()?.forEach { f ->
            val datePart = f.name.substringAfter('_').substringBefore(".json")
            val d = runCatching { LocalDate.parse(datePart) }.getOrNull()
            if (d == null || d.isBefore(oldest)) f.delete()
        }
    }

    private fun httpGet(url: String): Pair<Int, String?> {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        conn.setRequestProperty("Accept", "application/json")
        conn.setRequestProperty("User-Agent", "Hemma-Android/0.1 (privat app)")
        return try {
            val code = conn.responseCode
            val body = if (code == 200) conn.inputStream.bufferedReader().use { it.readText() } else null
            code to body
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        private const val MIN_POINTS = 20

        fun url(area: String, date: LocalDate): String = String.format(
            Locale.ROOT,
            "https://www.elprisetjustnu.se/api/v1/prices/%d/%02d-%02d_%s.json",
            date.year, date.monthValue, date.dayOfMonth, area,
        )

        /** Format: [{"SEK_per_kWh":0.42,"EUR_per_kWh":0.038,"EXR":11.0,"time_start":"…+02:00","time_end":"…"}] */
        fun parse(json: String): List<PricePoint> {
            val arr = JSONArray(json)
            val out = ArrayList<PricePoint>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(
                    PricePoint(
                        start = OffsetDateTime.parse(o.getString("time_start")).atZoneSameInstant(STOCKHOLM),
                        end = OffsetDateTime.parse(o.getString("time_end")).atZoneSameInstant(STOCKHOLM),
                        spot = o.getDouble("SEK_per_kWh"),
                    ),
                )
            }
            return out.sortedBy { it.start.toEpochSecond() }
        }
    }
}
