package se.hemma.app.domain

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Svensk formatering av priser och tider. */
object Fmt {
    private val sv: Locale = Locale.forLanguageTag("sv-SE")
    private val hm: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private val hh: DateTimeFormatter = DateTimeFormatter.ofPattern("HH")

    fun kr(v: Double): String = String.format(sv, "%.2f kr", v)

    fun perKwh(v: Double): String = String.format(sv, "%.2f kr/kWh", v)

    fun ore(v: Double): String = String.format(sv, "%.0f öre", v * 100)

    fun num(v: Double, decimals: Int = 2): String = String.format(sv, "%.${decimals}f", v)

    fun time(z: ZonedDateTime): String = z.format(hm)

    /** "14:15", "imorgon 02:00" eller "sön 02:00". */
    fun dayTime(z: ZonedDateTime, now: ZonedDateTime): String {
        val days = z.toLocalDate().toEpochDay() - now.toLocalDate().toEpochDay()
        return when (days) {
            0L -> time(z)
            1L -> "imorgon ${time(z)}"
            else -> z.format(DateTimeFormatter.ofPattern("EEE HH:mm", sv))
        }
    }

    fun duration(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h == 0 -> "$m min"
            m == 0 -> "$h h"
            else -> "$h h $m min"
        }
    }

    /** Timme som "HH", där midnatt vid periodens slut skrivs "24". */
    private fun endHour(start: ZonedDateTime, end: ZonedDateTime): String =
        if (end.hour == 0 && end.toLocalDate().isAfter(start.toLocalDate())) "24" else end.format(hh)

    /** Slår ihop intilliggande timmar: [02, 03, 04, 14] → "02–05, 14–15". */
    fun hourRanges(slots: List<HourSlot>): String {
        if (slots.isEmpty()) return "–"
        val sorted = slots.sortedBy { it.start.toEpochSecond() }
        val parts = ArrayList<String>()
        var rangeStart = sorted.first()
        var rangeEnd = sorted.first()
        for (s in sorted.drop(1)) {
            if (s.start.toEpochSecond() == rangeEnd.end.toEpochSecond()) {
                rangeEnd = s
            } else {
                parts.add("${rangeStart.start.format(hh)}–${endHour(rangeStart.start, rangeEnd.end)}")
                rangeStart = s
                rangeEnd = s
            }
        }
        parts.add("${rangeStart.start.format(hh)}–${endHour(rangeStart.start, rangeEnd.end)}")
        return parts.joinToString(", ")
    }

    /** "02–03" för en enskild timme. */
    fun hourSlot(slot: HourSlot): String = "${slot.start.format(hh)}–${endHour(slot.start, slot.end)}"

    /** Tolkar "1,5" eller "1.5". */
    fun parse(text: String): Double? = text.trim().replace(',', '.').replace(" ", "").toDoubleOrNull()

    /** Talet som redigerbar text: 36.0 → "36", 1.1 → "1,1". */
    fun input(v: Double): String {
        val s = if (v == Math.floor(v) && !v.isInfinite()) v.toLong().toString() else v.toString()
        return s.replace('.', ',')
    }
}
