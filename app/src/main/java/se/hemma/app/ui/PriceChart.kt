package se.hemma.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.unit.dp
import se.hemma.app.data.PricePoint
import se.hemma.app.data.PriceSettings
import se.hemma.app.domain.Fmt
import se.hemma.app.domain.HourSlot
import se.hemma.app.ui.theme.LevelColors
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * Stapeldiagram över ett dygns priser (totalpris per kWh).
 * Grönt = de billigaste timmarna, rött = dyraste tredjedelen, grått = övrigt.
 */
@Composable
fun PriceChart(
    points: List<PricePoint>,
    cheap: List<HourSlot>,
    settings: PriceSettings,
    now: ZonedDateTime,
    modifier: Modifier = Modifier,
) {
    val normalColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
    val cheapColor = LevelColors.Cheap
    val expensiveColor = LevelColors.Expensive.copy(alpha = 0.8f)
    val nowColor = MaterialTheme.colorScheme.onSurface
    val gridColor = MaterialTheme.colorScheme.outlineVariant

    val values = points.map { settings.totalPrice(it.spot) }
    val maxV = maxOf(values.maxOrNull() ?: 0.0, 0.0)
    val minV = minOf(values.minOrNull() ?: 0.0, 0.0)
    val avg = if (values.isEmpty()) 0.0 else values.average()

    Column(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("max ${Fmt.perKwh(maxV)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("streck = snitt ${Fmt.num(avg)} kr", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(150.dp)
                .padding(top = 6.dp),
        ) {
            if (points.isEmpty()) return@Canvas
            val range = (maxV - minV).let { if (it < 1e-9) 1.0 else it }
            fun y(v: Double): Float = (size.height * ((maxV - v) / range)).toFloat()

            val n = points.size
            val gap = if (n > 48) 1.dp.toPx() else 3.dp.toPx()
            val slot = size.width / n
            val barW = (slot - gap).coerceAtLeast(1f)
            val nowEpoch = now.toEpochSecond()
            val cheapStarts = cheap.map { it.start.toEpochSecond() }.toHashSet()
            val sortedVals = values.sorted()
            val hiThreshold = sortedVals[minOf(n - 1, n * 2 / 3)]

            points.forEachIndexed { i, p ->
                val v = values[i]
                val hourStart = p.start.truncatedTo(ChronoUnit.HOURS).toEpochSecond()
                val base = when {
                    cheapStarts.contains(hourStart) -> cheapColor
                    v >= hiThreshold && v > sortedVals.first() -> expensiveColor
                    else -> normalColor
                }
                val past = p.end.toEpochSecond() <= nowEpoch
                val color = if (past) base.copy(alpha = base.alpha * 0.35f) else base
                val top = y(maxOf(v, 0.0))
                val bottom = maxOf(y(minOf(v, 0.0)), top + 2f)
                drawRoundRect(
                    color = color,
                    topLeft = Offset(i * slot, top),
                    size = Size(barW, bottom - top),
                    cornerRadius = CornerRadius(barW / 3f, barW / 3f),
                )
            }

            // Snittlinje
            val ay = y(avg)
            drawLine(
                color = gridColor,
                start = Offset(0f, ay),
                end = Offset(size.width, ay),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)),
            )
            // Nollinje vid negativa priser
            if (minV < 0) {
                val zy = y(0.0)
                drawLine(gridColor, Offset(0f, zy), Offset(size.width, zy), strokeWidth = 1.dp.toPx())
            }
            // Just nu
            val first = points.first().start.toEpochSecond()
            val last = points.last().end.toEpochSecond()
            if (nowEpoch in first until last) {
                val x = ((nowEpoch - first).toFloat() / (last - first).toFloat()) * size.width
                drawLine(nowColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 2.dp.toPx())
                drawCircle(nowColor, radius = 4.dp.toPx(), center = Offset(x, 4.dp.toPx()))
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            listOf("00", "06", "12", "18", "24").forEach {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
