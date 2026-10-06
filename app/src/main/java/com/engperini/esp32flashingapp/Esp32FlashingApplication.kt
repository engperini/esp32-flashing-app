package com.engperini.esp32flashingapp

import android.app.Application
import com.engperini.esp32flashingapp.runtime.PersistentDiagnosticLog

class Esp32FlashingApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        PersistentDiagnosticLog.append(this, "APP_CREATE")
        Thread.setDefaultUncaughtExceptionHandler(PersistentCrashHandler(this, Thread.getDefaultUncaughtExceptionHandler()))
    }
}

private class PersistentCrashHandler(
    private val app: Application,
    private val previous: Thread.UncaughtExceptionHandler?
) : Thread.UncaughtExceptionHandler {
    override fun uncaughtException(thread: Thread, error: Throwable) {
        PersistentDiagnosticLog.append(app, "UNCAUGHT_EXCEPTION", "thread=" + thread.name + "\n" + error.stackTraceToString().takeLast(16000))
        previous?.uncaughtException(thread, error)
    }
}
