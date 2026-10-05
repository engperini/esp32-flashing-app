package com.engperini.esp32flashingapp.runtime

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.engperini.esp32flashingapp.core.AppState
import com.engperini.esp32flashingapp.core.OperationState
import com.engperini.esp32flashingapp.project.ProjectManager
import com.engperini.esp32flashingapp.flash.FlashPlanLoader
import kotlinx.coroutines.*

class IdfOperationService : Service() {
    companion object {
        private const val CHANNEL = "idf_operations"
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_BUILD = "build"
        private const val ACTION_SETUP = "setup"
        private const val ACTION_DOCTOR = "doctor"
        private const val ACTION_BUILD_FLASH = "build_flash"
        private const val EXTRA_TARGET = "target"

        fun build(context: Context, target: String = "esp32s3") = start(context, ACTION_BUILD, target)
        fun setup(context: Context, target: String = "esp32s3") = start(context, ACTION_SETUP, target)
        fun doctor(context: Context, target: String = "esp32s3") = start(context, ACTION_DOCTOR, target)
        fun buildAndFlash(context: Context, target: String = "esp32s3") = start(context, ACTION_BUILD_FLASH, target)
        private fun start(context: Context, action: String, target: String) {
            val i = Intent(context, IdfOperationService::class.java).setAction(action).putExtra(EXTRA_TARGET, target)
            androidx.core.content.ContextCompat.startForegroundService(context, i)
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var running = false

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "ESP-IDF operations", NotificationManager.IMPORTANCE_LOW))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, notification("Starting ESP-IDF operation…"))
        if (running) return START_NOT_STICKY
        running = true
        val target = intent?.getStringExtra(EXTRA_TARGET) ?: "esp32s3"
        scope.launch {
            try {
                when (intent?.action) {
                    ACTION_BUILD -> runBuild(target)
                    ACTION_SETUP -> runSetup(target)
                    ACTION_DOCTOR -> runDoctor(target)
                    ACTION_BUILD_FLASH -> runBuild(target, requestFlash = true)
                }
            } finally {
                running = false
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    private suspend fun runBuild(target: String, requestFlash: Boolean = false) {
        val projects = ProjectManager(applicationContext)
        BuildState.open()
        AppState.operation(OperationState.BUILDING, "Building ESP32-S3 firmware…")
        update("Building ESP32-S3 firmware…")
        runCatching { IdfBuildExecutor(applicationContext).buildPrepared(projects.projectDir, target) { BuildState.output(it) } }
            .onSuccess {
                FlashPlanLoader.promoteLastGood(projects.projectDir)
                BuildState.success(it)
                AppState.operation(OperationState.BUILD_SUCCESS, "Firmware built successfully")
                update(if (requestFlash) "Build completed — reopen app to start Flash" else "Build completed")
                if (requestFlash) AppState.operation(OperationState.BUILD_SUCCESS, "Build completed — tap Flash to continue safely")
            }.onFailure {
                val message = it.message ?: "Build failed"
                BuildState.error(message)
                AppState.operation(OperationState.BUILD_ERROR, "Build failed — see Build details")
                update("Build failed")
            }
    }

    private suspend fun runSetup(target: String) {
        SetupState.open()
        AppState.operation(OperationState.PREPARING_BUILD, "Configuring ESP-IDF 5.5 for ESP32-S3…")
        update("Configuring ESP-IDF 5.5…")
        runCatching { IdfBuildExecutor(applicationContext).prepare(target) { SetupState.progress(it) } }
            .onSuccess { AppState.operation(OperationState.IDLE, "ESP-IDF 5.5 / $target ready"); update("ESP-IDF ready") }
            .onFailure {
                val message = it.message ?: "ESP-IDF setup failed"
                SetupState.error(message); AppState.operation(OperationState.BUILD_ERROR, message); update("ESP-IDF setup failed")
            }
    }

    private suspend fun runDoctor(target: String) {
        BuildState.open()
        AppState.operation(OperationState.PREPARING_BUILD, "Checking ESP-IDF 5.5 environment…")
        update("Checking ESP-IDF environment…")
        runCatching { IdfBuildExecutor(applicationContext).doctor(target) { BuildState.output(it) } }
            .onSuccess { BuildState.success(it); AppState.operation(OperationState.IDLE, "ESP-IDF environment healthy"); update("ESP-IDF environment healthy") }
            .onFailure {
                val message = it.message ?: "ESP-IDF Doctor failed"
                BuildState.error(message); AppState.operation(OperationState.BUILD_ERROR, "ESP-IDF repair needed"); update("ESP-IDF Doctor failed")
            }
    }

    private fun update(text: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(text))
    }

    private fun notification(text: String): Notification {
        val pending = PendingIntent.getActivity(this, 0, Intent(this, com.engperini.esp32flashingapp.MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("ESP32 Flashing App")
            .setContentText(text)
            .setContentIntent(pending)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(0, 0, true)
            .build()
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null
}
