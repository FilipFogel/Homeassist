package se.hemma.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.widget.RemoteViews
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import se.hemma.app.MainActivity
import se.hemma.app.R
import se.hemma.app.data.PriceData
import se.hemma.app.data.PricePoint
import se.hemma.app.data.PriceRepository
import se.hemma.app.data.PriceSettings
import se.hemma.app.data.STOCKHOLM
import se.hemma.app.data.SettingsStore
import se.hemma.app.domain.Dashboard
import se.hemma.app.domain.Fmt
import se.hemma.app.domain.HourSlot
import se.hemma.app.domain.PriceLevel
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

object WidgetUpdater {

    /** Ritar om alla widgetar. Hämtar aldrig från nätet själv – använder [prices] eller cachen. */
    suspend fun renderAll(context: Context, prices: PriceData? = null): Unit = withContext(Dispatchers.IO) {
        try {
            render(context, prices)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Ett fel i widgeten får aldrig krascha appen; nästa uppdatering försöker igen.
        }
    }

    private suspend fun render(context: Context, prices: PriceData?) {
        val app = context.applicationContext
        val mgr = AppWidgetManager.getInstance(app)
        val ids = mgr.getAppWidgetIds(ComponentName(app, PriceWidgetProvider::class.java))
        if (ids == null || ids.isEmpty()) return

        val store = SettingsStore(app)
        val settings = store.loadSettings()
        val machines = store.loadMachines()
        val data = prices?.takeIf { it.area == settings.area && it.today.isNotEmpty() }
            ?: PriceRepository(app).load(settings.area, allowNetwork = false).data
        val dash = Dashboard.build(data, settings, machines, ZonedDateTime.now(STOCKHOLM))

        mgr.updateAppWidget(ids, buildViews(app, dash, settings))
    }

    private fun buildViews(ctx: Context, dash: Dashboard, settings: PriceSettings): RemoteViews {
        val rv = RemoteViews(ctx.packageName, R.layout.widget_price)

        rv.setTextViewText(R.id.w_price, dash.currentTotal?.let { Fmt.perKwh(it) } ?: "Inga priser än")
        rv.setTextViewText(R.id.w_level, if (dash.level == PriceLevel.UNKNOWN) "" else "● ${dash.level.label}")
        rv.setTextColor(R.id.w_level, levelColor(dash.level))

        val sub = buildString {
            append(settings.area)
            dash.current?.let { append(" · ${Fmt.time(it.start)}–${Fmt.time(it.end)}") }
            dash.todayAvgTotal?.let { append(" · snitt idag ${Fmt.num(it)} kr") }
        }
        rv.setTextViewText(R.id.w_sub, sub)

        rv.setImageViewBitmap(
            R.id.w_chart,
            ChartBitmap.render(dash.next24h(), dash.cheapToday + dash.cheapTomorrow, settings, dash.now),
        )

        val cheap = buildString {
            if (dash.cheapToday.isNotEmpty()) append("Billigast idag: ${Fmt.hourRanges(dash.cheapToday)}")
            if (dash.cheapTomorrow.isNotEmpty()) {
                if (isNotEmpty()) append("\n")
                append("Imorgon: ${Fmt.hourRanges(dash.cheapTomorrow)}")
            }
        }
        rv.setTextViewText(R.id.w_cheap, cheap)

        val lines = dash.plans
            .filter { it.machine.showOnWidget }
            .take(4)
            .joinToString("\n") { p ->
                val head = "${p.machine.type.emoji} ${p.machine.name}"
                val best = p.best
                when {
                    best == null -> "$head: –"
                    p.startNowIsBest -> "$head: starta nu · ${Fmt.kr(best.cost)}"
                    else -> "$head: ${Fmt.dayTime(best.start, dash.now)} · ${Fmt.kr(best.cost)}"
                }
            }
        rv.setTextViewText(R.id.w_machines, lines)

        rv.setOnClickPendingIntent(R.id.w_root, openAppIntent(ctx))
        rv.setOnClickPendingIntent(R.id.w_refresh, refreshIntent(ctx))
        return rv
    }

    private fun levelColor(level: PriceLevel): Int = when (level) {
        PriceLevel.CHEAP -> 0xFF1E9E5A.toInt()
        PriceLevel.NORMAL -> 0xFFD39200.toInt()
        PriceLevel.EXPENSIVE -> 0xFFD64545.toInt()
        PriceLevel.UNKNOWN -> 0xFF8A9692.toInt()
    }

    private fun openAppIntent(ctx: Context): PendingIntent {
        val intent = Intent(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(ctx, 1, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun refreshIntent(ctx: Context): PendingIntent {
        val intent = Intent(ctx, PriceWidgetProvider::class.java).setAction(PriceWidgetProvider.ACTION_REFRESH)
        return PendingIntent.getBroadcast(ctx, 2, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
}

/** Ritar widgetens stapeldiagram som en bitmap (RemoteViews kan inte rita själv). */
object ChartBitmap {
    private const val CHEAP = 0xFF1E9E5A.toInt()
    private const val EXPENSIVE = 0xCCD64545.toInt()
    private const val NORMAL = 0x668A9692
    private const val CURRENT = 0xFF0F7A6C.toInt()
    private const val LABEL = 0xFF8A9692.toInt()
    private val HH: DateTimeFormatter = DateTimeFormatter.ofPattern("HH")

    fun render(
        points: List<PricePoint>,
        cheap: List<HourSlot>,
        settings: PriceSettings,
        now: ZonedDateTime,
        width: Int = 720,
        height: Int = 150,
    ): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        if (points.isEmpty()) return bmp
        val canvas = Canvas(bmp)

        val values = points.map { settings.totalPrice(it.spot) }
        val maxV = maxOf(values.max(), 0.0)
        val minV = minOf(values.min(), 0.0)
        val range = (maxV - minV).let { if (it < 1e-9) 1.0 else it }
        val labelH = 24f
        val chartH = height - labelH
        fun y(v: Double): Float = (chartH * (maxV - v) / range).toFloat()

        val cheapStarts = cheap.map { it.start.toEpochSecond() }.toHashSet()
        val sorted = values.sorted()
        val hi = sorted[minOf(sorted.size - 1, sorted.size * 2 / 3)]
        val n = points.size
        val slot = width.toFloat() / n
        val gap = if (n > 48) 1.5f else 3f
        val nowEpoch = now.toEpochSecond()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        points.forEachIndexed { i, p ->
            val v = values[i]
            val isNow = p.start.toEpochSecond() <= nowEpoch && nowEpoch < p.end.toEpochSecond()
            val hourStart = p.start.truncatedTo(ChronoUnit.HOURS).toEpochSecond()
            paint.color = when {
                isNow -> CURRENT
                cheapStarts.contains(hourStart) -> CHEAP
                v >= hi && v > sorted.first() -> EXPENSIVE
                else -> NORMAL
            }
            val left = i * slot
            val top = y(maxOf(v, 0.0))
            val bottom = maxOf(y(minOf(v, 0.0)), top + 3f)
            canvas.drawRoundRect(RectF(left, top, left + (slot - gap).coerceAtLeast(1f), bottom), 3f, 3f, paint)
        }

        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = LABEL
            textSize = 20f
        }
        points.forEachIndexed { i, p ->
            val x = i * slot
            if (p.start.minute == 0 && p.start.hour % 6 == 0 && x < width - 28f) {
                canvas.drawText(p.start.format(HH), x, height - 4f, text)
            }
        }
        return bmp
    }
}
