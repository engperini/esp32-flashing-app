package com.engperini.esp32flashingapp

import android.app.ActivityManager
import android.app.Application
import android.os.Build
import com.engperini.esp32flashingapp.runtime.PersistentDiagnosticLog

class Esp32FlashingApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        PersistentDiagnosticLog.append(this, "APP_CREATE", PersistentDiagnosticLog.deviceState(this))
        appendPreviousExitReason()
        Thread.setDefaultUncaughtExceptionHandler(PersistentCrashHandler(this, Thread.getDefaultUncaughtExceptionHandler()))
    }

    private fun appendPreviousExitReason() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        runCatching {
            val am = getSystemService(ActivityManager::class.java)
            val exit = am.getHistoricalProcessExitReasons(packageName, 0, 1).firstOrNull() ?: return
            PersistentDiagnosticLog.append(
                this,
                "PREVIOUS_PROCESS_EXIT",
                "reason=${exit.reason} status=${exit.status} importance=${exit.importance} " +
                    "pssKB=${exit.pss} rssKB=${exit.rss} timestamp=${exit.timestamp} description=${exit.description.orEmpty()}"
            )
        }.onFailure {
            PersistentDiagnosticLog.append(this, "PREVIOUS_PROCESS_EXIT_READ_FAILED", it.toString())
        }
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
