package app.sip.water

import android.app.Application
import app.sip.water.data.Settings
import app.sip.water.data.WaterRepository
import app.sip.water.reminders.Notifications
import app.sip.water.reminders.ReminderScheduler
import app.sip.water.widget.WaterWidget

class SipApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Graph.init(this)
        Notifications.createChannels(this)
        // Any change to the log changes the widget and when the next reminder is due.
        Graph.repository.addListener {
            WaterWidget.updateAll(this)
            ReminderScheduler.reschedule(this)
        }
        Thread { ReminderScheduler.reschedule(this) }.start()
    }
}

/** The app's two long-lived objects, shared by the UI, receivers and widget. */
object Graph {
    lateinit var repository: WaterRepository
        private set
    lateinit var settings: Settings
        private set

    fun init(app: Application) {
        if (::repository.isInitialized) return
        settings = Settings(app)
        repository = WaterRepository(app)
    }
}
