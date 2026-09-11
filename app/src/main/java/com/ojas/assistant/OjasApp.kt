package com.ojas.assistant

import android.app.Application
import android.os.StrictMode
import androidx.work.Configuration
import com.ojas.assistant.alarm.NotificationCenter
import com.ojas.assistant.di.AppContainer
import com.ojas.assistant.screentime.MaintenanceWorker
import kotlinx.coroutines.launch

class OjasApp : Application(), Configuration.Provider {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) enableStrictMode()

        container = AppContainer(this)
        NotificationCenter.ensureChannels(this)

        // Re-arming touches the database, so it is deliberately off the main thread and
        // off the critical path to the first frame.
        container.appScope.launch {
            container.alarms.rescheduleAll()
            container.reminders.rescheduleAll()
            container.calendar.rescheduleAll()
            container.water.rescheduleNudge()
        }

        MaintenanceWorker.enqueue(this)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(if (BuildConfig.DEBUG) android.util.Log.DEBUG else android.util.Log.ERROR)
            .build()

    private fun enableStrictMode() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build()
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedClosableObjects()
                .detectLeakedSqlLiteObjects()
                .penaltyLog()
                .build()
        )
    }
}
