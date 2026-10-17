package se.hemma.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import se.hemma.app.data.PriceRepository
import se.hemma.app.data.SettingsStore
import se.hemma.app.widget.WidgetUpdater
import java.util.concurrent.TimeUnit

/** Hämtar saknade priser (t.ex. morgondagens efter kl 13) och ritar om widgeten. */
class RefreshWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val settings = SettingsStore(applicationContext).loadSettings()
            val res = PriceRepository(applicationContext).load(settings.area, allowNetwork = true)
            WidgetUpdater.renderAll(applicationContext, res.data)
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.success()
        }
    }

    companion object {
        private const val PERIODIC = "hemma_periodic_refresh"
        private const val NOW = "hemma_refresh_now"

        /** Var 15:e minut (minsta intervallet Android tillåter) – widgeten visar priset för aktuell kvart. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<RefreshWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun runOnce(context: Context) {
            WorkManager.getInstance(context)
                .enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<RefreshWorker>().build())
        }
    }
}
