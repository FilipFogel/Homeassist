package se.hemma.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import se.hemma.app.MainActivity
import se.hemma.app.R
import se.hemma.app.data.PriceData
import se.hemma.app.data.PriceRepository
import se.hemma.app.data.STOCKHOLM
import se.hemma.app.data.SettingsStore
import se.hemma.app.domain.Dashboard
import se.hemma.app.domain.Fmt
import se.hemma.app.domain.Planner
import se.hemma.app.domain.PriceLevel
import java.time.ZonedDateTime

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

        mgr.updateAppWidget(ids, buildViews(app, dash))
    }

    private val MACHINE_ROWS = listOf(
        intArrayOf(R.id.w_m1, R.id.w_m1_name, R.id.w_m1_time, R.id.w_m1_cost),
        intArrayOf(R.id.w_m2, R.id.w_m2_name, R.id.w_m2_time, R.id.w_m2_cost),
        intArrayOf(R.id.w_m3, R.id.w_m3_name, R.id.w_m3_time, R.id.w_m3_cost),
    )

    private fun buildViews(ctx: Context, dash: Dashboard): RemoteViews {
        val rv = RemoteViews(ctx.packageName, R.layout.widget_price)

        rv.setTextViewText(R.id.w_price, dash.currentTotal?.let { Fmt.perKwh(it) } ?: "Inga priser än")
        rv.setTextViewText(R.id.w_level, if (dash.level == PriceLevel.UNKNOWN) "" else "● ${dash.level.label}")
        rv.setTextColor(R.id.w_level, levelColor(dash.level))

        val cheap = cheapText(dash)
        rv.setTextViewText(R.id.w_cheap, cheap)
        rv.setViewVisibility(R.id.w_cheap, if (cheap.isEmpty()) View.GONE else View.VISIBLE)

        val plans = dash.plans.filter { it.machine.showOnWidget }
        MACHINE_ROWS.forEachIndexed { i, ids ->
            val p = plans.getOrNull(i)
            if (p == null) {
                rv.setViewVisibility(ids[0], View.GONE)
                return@forEachIndexed
            }
            val best = p.best
            rv.setViewVisibility(ids[0], View.VISIBLE)
            rv.setTextViewText(ids[1], "${p.machine.type.emoji} ${p.machine.name}")
            rv.setTextViewText(
                ids[2],
                when {
                    best == null -> "–"
                    p.startNowIsBest -> "nu"
                    else -> Fmt.dayTime(best.start, dash.now)
                },
            )
            rv.setTextViewText(ids[3], best?.let { Fmt.kr(it.cost) } ?: "")
        }

        rv.setOnClickPendingIntent(R.id.w_root, openAppIntent(ctx))
        rv.setOnClickPendingIntent(R.id.w_refresh, refreshIntent(ctx))
        return rv
    }

    /** "Billigast 22–24 · imorgon 00–04" – bara perioder som inte redan passerat. */
    private fun cheapText(dash: Dashboard): String {
        if (dash.today.isEmpty()) return ""
        val today = Planner.ranges(dash.cheapToday).filter { it.end.isAfter(dash.now) }
        val tomorrow = Planner.ranges(dash.cheapTomorrow)
        val parts = ArrayList<String>()
        if (today.isNotEmpty()) parts.add(today.joinToString(", ") { Fmt.range(it) })
        if (tomorrow.isNotEmpty()) parts.add("imorgon " + tomorrow.joinToString(", ") { Fmt.range(it) })
        return if (parts.isEmpty()) "Inga fler billiga timmar idag" else "Billigast " + parts.joinToString(" · ")
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
