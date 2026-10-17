package se.hemma.app.domain

import se.hemma.app.data.Machine
import se.hemma.app.data.PriceData
import se.hemma.app.data.PricePoint
import se.hemma.app.data.PriceSettings
import java.time.ZonedDateTime

/** Allt som översikten och widgeten visar, uträknat för en viss tidpunkt. */
data class Dashboard(
    val now: ZonedDateTime,
    val area: String,
    val today: List<PricePoint>,
    val tomorrow: List<PricePoint>,
    val current: PricePoint?,
    val currentTotal: Double?,
    val level: PriceLevel,
    val cheapToday: List<HourSlot>,
    val cheapTomorrow: List<HourSlot>,
    val todayAvgTotal: Double?,
    val todayMinTotal: Double?,
    val todayMaxTotal: Double?,
    val plans: List<MachinePlan>,
) {
    /** Från aktuell kvart och 24 timmar framåt (så långt det finns data). */
    fun next24h(): List<PricePoint> {
        val from = (current?.start ?: now).toEpochSecond()
        val to = from + 24 * 3600
        return (today + tomorrow).filter {
            val s = it.start.toEpochSecond()
            s >= from && s < to
        }
    }

    companion object {
        fun build(
            data: PriceData,
            settings: PriceSettings,
            machines: List<Machine>,
            now: ZonedDateTime,
        ): Dashboard {
            val all = (data.today + data.tomorrow)
                .distinctBy { it.start.toEpochSecond() }
                .sortedBy { it.start.toEpochSecond() }
            val todayDate = now.toLocalDate()
            val today = all.filter { it.start.toLocalDate() == todayDate }
            val tomorrow = all.filter { it.start.toLocalDate() == todayDate.plusDays(1) }
            val relevant = today + tomorrow

            val nowEpoch = now.toEpochSecond()
            val current = today.firstOrNull {
                it.start.toEpochSecond() <= nowEpoch && nowEpoch < it.end.toEpochSecond()
            }
            val todaySpots = today.map { it.spot }
            val todayTotals = todaySpots.map { settings.totalPrice(it) }
            val count = settings.cheapHoursCount.coerceIn(1, 12)

            return Dashboard(
                now = now,
                area = settings.area,
                today = today,
                tomorrow = tomorrow,
                current = current,
                currentTotal = current?.let { settings.totalPrice(it.spot) },
                level = current?.let { Planner.level(it.spot, todaySpots) } ?: PriceLevel.UNKNOWN,
                cheapToday = Planner.cheapestHours(today, count),
                cheapTomorrow = Planner.cheapestHours(tomorrow, count),
                todayAvgTotal = todayTotals.takeIf { it.isNotEmpty() }?.average(),
                todayMinTotal = todayTotals.minOrNull(),
                todayMaxTotal = todayTotals.maxOrNull(),
                plans = machines.map { Planner.plan(it, relevant, settings, now, tomorrow.isNotEmpty()) },
            )
        }
    }
}
