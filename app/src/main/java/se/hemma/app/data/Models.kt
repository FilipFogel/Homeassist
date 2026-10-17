package se.hemma.app.data

import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.ceil

val STOCKHOLM: ZoneId = ZoneId.of("Europe/Stockholm")

/** Ett prisintervall (kvart, eller timme för äldre data). */
data class PricePoint(
    val start: ZonedDateTime,
    val end: ZonedDateTime,
    /** Spotpris i SEK/kWh, exkl. moms och avgifter (Nord Pool via elprisetjustnu.se). */
    val spot: Double,
)

data class PriceData(
    val area: String,
    val today: List<PricePoint>,
    val tomorrow: List<PricePoint>,
    val fetchedAtMillis: Long,
) {
    val all: List<PricePoint> get() = today + tomorrow

    companion object {
        val EMPTY = PriceData("", emptyList(), emptyList(), 0L)
    }
}

enum class MachineType(val emoji: String, val label: String) {
    WASHER("🧺", "Tvättmaskin"),
    DRYER("🌀", "Torktumlare"),
    CAR("🚗", "Elbil"),
    DISHWASHER("🍽️", "Diskmaskin"),
    OTHER("🔌", "Övrigt"),
}

/**
 * En maskin/ett program. För elbil är [energyKwh] energin som ska in i batteriet
 * och [powerKw] laddboxens effekt – tiden räknas ut automatiskt.
 */
data class Machine(
    val id: String,
    val name: String,
    val type: MachineType,
    val model: String = "",
    val energyKwh: Double,
    val durationMinutes: Int,
    val powerKw: Double? = null,
    /** Måste vara klar senast denna timme (0–23), eller null om det inte spelar någon roll. */
    val readyByHour: Int? = null,
    val showOnWidget: Boolean = true,
) {
    val isCar: Boolean get() = type == MachineType.CAR

    /** Energi som tas från elnätet för en körning/laddning (inkl. laddförluster för bil). */
    fun gridKwh(): Double = if (isCar) energyKwh / CHARGE_EFFICIENCY else energyKwh

    fun runMinutes(): Int {
        val kw = powerKw ?: 0.0
        return if (isCar && kw > 0.0) {
            ceil(energyKwh / (kw * CHARGE_EFFICIENCY) * 60.0).toInt().coerceAtLeast(1)
        } else {
            durationMinutes.coerceAtLeast(1)
        }
    }

    companion object {
        /** Ungefärlig verkningsgrad vid AC-laddning. */
        const val CHARGE_EFFICIENCY = 0.9
    }
}

data class PriceSettings(
    val area: String = "SE3",
    /** Elhandlarens påslag, öre/kWh exkl. moms. */
    val supplierMarkupOre: Double = 5.0,
    /** Energiskatt, öre/kWh exkl. moms (36,0 öre under 2026). */
    val energyTaxOre: Double = 36.0,
    /** Elnätets rörliga överföringsavgift, öre/kWh exkl. moms. */
    val gridFeeOre: Double = 25.0,
    val vatPercent: Double = 25.0,
    /** Räkna med skatt och avgifter (annars bara spotpris + moms). */
    val includeFees: Boolean = true,
    val cheapHoursCount: Int = 4,
) {
    fun feesSek(): Double = (supplierMarkupOre + energyTaxOre + gridFeeOre) / 100.0

    /** Vad en kWh faktiskt kostar dig, inkl. moms. */
    fun totalPrice(spot: Double): Double {
        val vat = 1.0 + vatPercent / 100.0
        return if (includeFees) (spot + feesSek()) * vat else spot * vat
    }
}

object Defaults {
    /** Typvärden – byt mot värden för dina egna modeller. */
    fun machines(): List<Machine> = listOf(
        Machine("wash60", "Tvätt 60°", MachineType.WASHER, energyKwh = 1.1, durationMinutes = 150),
        Machine("dryer", "Torktumlare", MachineType.DRYER, model = "Värmepump", energyKwh = 1.8, durationMinutes = 150),
        Machine(
            "car", "Elbil", MachineType.CAR,
            energyKwh = 40.0, durationMinutes = 0, powerKw = 11.0, readyByHour = 7,
        ),
        Machine("dish", "Diskmaskin (Eco)", MachineType.DISHWASHER, energyKwh = 0.9, durationMinutes = 210, showOnWidget = false),
    )

    /** (kWh, minuter, kW) att fylla i när man väljer typ för en ny maskin. */
    fun forType(type: MachineType): Triple<Double, Int, Double?> = when (type) {
        MachineType.WASHER -> Triple(1.1, 150, null)
        MachineType.DRYER -> Triple(1.8, 150, null)
        MachineType.CAR -> Triple(40.0, 0, 11.0)
        MachineType.DISHWASHER -> Triple(0.9, 210, null)
        MachineType.OTHER -> Triple(1.0, 60, null)
    }
}
