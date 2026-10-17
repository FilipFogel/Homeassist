package se.hemma.app.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import se.hemma.app.work.RefreshWorker

class PriceWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        // Rita direkt från cachen, och be bakgrundsjobbet hämta det som saknas.
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                WidgetUpdater.renderAll(context)
            } catch (e: Exception) {
                // Widgeten visar gammal data tills nästa uppdatering.
            } finally {
                pending.finish()
            }
        }
        RefreshWorker.runOnce(context)
    }

    override fun onEnabled(context: Context) {
        RefreshWorker.schedule(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_REFRESH) {
            RefreshWorker.runOnce(context)
            return
        }
        super.onReceive(context, intent)
    }

    companion object {
        const val ACTION_REFRESH = "se.hemma.app.widget.REFRESH"
    }
}
