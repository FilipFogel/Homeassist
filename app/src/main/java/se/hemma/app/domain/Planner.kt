package se.hemma.app.domain

import se.hemma.app.data.Machine
import se.hemma.app.data.PricePoint
import se.hemma.app.data.PriceSettings
import java.time.Instant
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

data class HourSlot(val start: ZonedDateTime, val end: ZonedDateTime, val avgSpot: Double)

data class Window(val start: ZonedDateTime, val end: ZonedDateTime, val cost: Double)

/** En sammanhängande period, t.ex. flera billiga timmar i rad. */
data class TimeRange(val start: ZonedDateTime, val end: ZonedDateTime)

enum class PriceLevel(val label: String) {
    CHEAP("Billigt"),
    NORMAL("Normalt"),
    EXPENSIVE("Dyrt"),
    UNKNOWN("–"),
}

data class MachinePlan(
    val machine: Machine,
    val kwh: Double,
    val minutes: Int,
    /** Billigaste starttid inom tillgänglig prisdata (och före deadline). */
    val best: Window?,
    /** Om man startar direkt. */
    val startNow: Window?,
    val deadline: ZonedDateTime?,
    /** Morgondagens priser saknas och kan ge en billigare tid. */
    val mightGetCheaper: Boolean,
) {
    val saving: Double?
        get() = if (best != null && startNow != null) startNow.cost - best.cost else null

    /** Bästa tiden är (nästan) nu. */
    val startNowIsBest: Boolean
        get() = best != null && startNow != null && best.start.toEpochSecond() - startNow.start.toEpochSecond() < 15 * 60
}

object Planner {

    /** Slår ihop kvartspriser till timmedel. */
    fun hourly(points: List<PricePoint>): List<HourSlot> =
        points.groupBy { it.start.truncatedTo(ChronoUnit.HOURS) }
            .map { (hour, ps) ->
                HourSlot(hour, ps.maxOf { it.end }, ps.map { it.spot }.average())
            }
            .sortedBy { it.start.toEpochSecond() }

    /** Slår ihop intilliggande timmar till perioder: [02, 03, 04, 14] → 02–05, 14–15. */
    fun ranges(slots: List<HourSlot>): List<TimeRange> {
        val out = ArrayList<TimeRange>()
        for (s in slots.sortedBy { it.start.toEpochSecond() }) {
            val last = out.lastOrNull()
            if (last != null && last.end.toEpochSecond() == s.start.toEpochSecond()) {
                out[out.lastIndex] = TimeRange(last.start, s.end)
            } else {
                out.add(TimeRange(s.start, s.end))
            }
        }
        return out
    }

    /** De [count] billigaste timmarna, sorterade i tidsordning. */
    fun cheapestHours(points: List<PricePoint>, count: Int): List<HourSlot> =
        hourly(points)
            .sortedBy { it.avgSpot }
            .take(count)
            .sortedBy { it.start.toEpochSecond() }

    /**
     * Kostnad i kr för att förbruka [kwh] jämnt utspritt från [startEpoch] i [durationSec] sekunder.
     * Returnerar null om prisdata saknas för någon del av perioden.
     */
    fun windowCost(
        points: List<PricePoint>,
        startEpoch: Long,
        durationSec: Long,
        kwh: Double,
        settings: PriceSettings,
    ): Double? {
        if (durationSec <= 0) return 0.0
        val endEpoch = startEpoch + durationSec
        var covered = 0L
        var cost = 0.0
        for (p in points) {
            val ps = p.start.toEpochSecond()
            val pe = p.end.toEpochSecond()
            if (ps >= endEpoch) break
            val a = maxOf(ps, startEpoch)
            val b = minOf(pe, endEpoch)
            if (b > a) {
                val sec = b - a
                covered += sec
                cost += kwh * (sec.toDouble() / durationSec) * settings.totalPrice(p.spot)
            }
        }
        return if (covered >= durationSec) cost else null
    }

    /** Nästa gång klockan är [hour]:00 efter [now]. */
    fun nextOccurrence(now: ZonedDateTime, hour: Int): ZonedDateTime {
        val t = now.toLocalDate().atTime(hour.coerceIn(0, 23), 0).atZone(now.zone)
        return if (t.isAfter(now)) t else t.plusDays(1)
    }

    fun plan(
        machine: Machine,
        points: List<PricePoint>,
        settings: PriceSettings,
        now: ZonedDateTime,
        tomorrowKnown: Boolean,
    ): MachinePlan {
        val kwh = machine.gridKwh()
        val minutes = machine.runMinutes()
        val dur = minutes * 60L
        val nowEpoch = now.toEpochSecond()
        val sorted = points.sortedBy { it.start.toEpochSecond() }

        // Hinner den inte bli klar till närmsta deadline gäller nästa dygns deadline.
        val deadline = machine.readyByHour?.let { h ->
            val d = nextOccurrence(now, h)
            if (d.toEpochSecond() - nowEpoch < dur) d.plusDays(1) else d
        }
        val deadlineEpoch = deadline?.toEpochSecond()

        val candidates = ArrayList<Long>()
        candidates.add(nowEpoch)
        for (p in sorted) {
            val s = p.start.toEpochSecond()
            if (s > nowEpoch) candidates.add(s)
        }

        var bestStart = -1L
        var bestCost = Double.MAX_VALUE
        for (c in candidates) {
            if (deadlineEpoch != null && c + dur > deadlineEpoch) break
            val cost = windowCost(sorted, c, dur, kwh, settings) ?: continue
            if (cost < bestCost - 1e-9) {
                bestCost = cost
                bestStart = c
            }
        }

        val best = if (bestStart >= 0) Window(at(bestStart, now), at(bestStart + dur, now), bestCost) else null
        val nowCost = windowCost(sorted, nowEpoch, dur, kwh, settings)
        val startNow = nowCost?.let { Window(now, at(nowEpoch + dur, now), it) }

        val dataEnd = sorted.lastOrNull()?.end?.toEpochSecond() ?: nowEpoch
        val mightGetCheaper = !tomorrowKnown && (deadlineEpoch == null || deadlineEpoch > dataEnd)

        return MachinePlan(machine, kwh, minutes, best, startNow, deadline, mightGetCheaper)
    }

    /** Var i dagens prisspann ligger [value]? Nedre tredjedel = billigt, övre = dyrt. */
    fun level(value: Double, reference: List<Double>): PriceLevel {
        if (reference.isEmpty()) return PriceLevel.UNKNOWN
        val min = reference.min()
        val max = reference.max()
        if (max - min < 0.02) return PriceLevel.NORMAL
        // Percentil med mittrang, så att lika priser hamnar i mitten av sin grupp.
        val below = reference.count { it < value - 1e-6 }
        val equal = reference.count { kotlin.math.abs(it - value) <= 1e-6 }
        val frac = (below + equal / 2.0) / reference.size
        return when {
            frac < 1.0 / 3.0 -> PriceLevel.CHEAP
            frac >= 2.0 / 3.0 -> PriceLevel.EXPENSIVE
            else -> PriceLevel.NORMAL
        }
    }

    private fun at(epoch: Long, ref: ZonedDateTime): ZonedDateTime =
        Instant.ofEpochSecond(epoch).atZone(ref.zone)
}
