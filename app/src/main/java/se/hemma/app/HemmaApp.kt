package se.hemma.app

import android.app.Application
import se.hemma.app.work.RefreshWorker

class HemmaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Hämtar priser i bakgrunden och håller widgeten uppdaterad.
        RefreshWorker.schedule(this)
    }
}
