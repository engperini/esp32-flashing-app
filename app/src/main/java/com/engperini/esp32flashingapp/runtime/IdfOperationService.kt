package com.engperini.esp32flashingapp.runtime

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import com.engperini.esp32flashingapp.flash.FlashPlanLoader
import androidx.core.app.NotificationCompat
import com.engperini.esp32flashingapp.core.AppState
import com.engperini.esp32flashingapp.core.OperationState
import com.engperini.esp32flashingapp.project.ProjectManager
import kotlinx.coroutines.*

class IdfOperationService : Service() {
    companion object {
        private const val CHANNEL = "idf_operations"
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_BUILD = "build"
        private const val ACTION_SETUP = "setup"
        private const val ACTION_DOCTOR = "doctor"
        private const val ACTION_FULL_CLEAN = "full_clean"
        private const val ACTION_BUILD_FLASH = "build_flash"
        private const val EXTRA_TARGET = "target"

        fun build(context: Context, target: String) = start(context, ACTION_BUILD, target)
        fun setup(context: Context, target: String) = start(context, ACTION_SETUP, target)
        fun doctor(context: Context, target: String) = start(context, ACTION_DOCTOR, target)
        fun fullClean(context: Context, target: String) = start(context, ACTION_FULL_CLEAN, target)
        fun buildAndFlash(context: Context, target: String) = start(context, ACTION_BUILD_FLASH, target)
        private fun start(context: Context, action: String, target: String) {
            val i = Intent(context, IdfOperationService::class.java).setAction(action).putExtra(EXTRA_TARGET, target)
            androidx.core.content.ContextCompat.startForegroundService(context, i)
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var running = false
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "ESP-IDF operations", NotificationManager.IMPORTANCE_LOW))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, notification("Starting ESP-IDF operation…"))
        if (wakeLock?.isHeld != true) {
            wakeLock = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, packageName + ":idf-operation").apply { acquire(60 * 60 * 1000L) }
        }
        if (running) return START_NOT_STICKY
        running = true
        val target = intent?.getStringExtra(EXTRA_TARGET) ?: return START_NOT_STICKY
        scope.launch {
            try {
                when (intent?.action) {
                    ACTION_BUILD -> runBuild(target)
                    ACTION_SETUP -> runSetup(target)
                    ACTION_DOCTOR -> runDoctor(target)
                    ACTION_FULL_CLEAN -> runFullClean(target)
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
        AppState.operation(OperationState.BUILDING, "Building $target firmware…")
        update("Building $target firmware…")
        runCatching { IdfBuildExecutor(applicationContext).buildPrepared(projects.projectDir, target) { BuildState.output(it) } }
            .onSuccess {
                FlashPlanLoader.promoteLastGood(projects.projectDir)
                BuildState.success(it)
                AppState.operation(OperationState.BUILD_SUCCESS, "Firmware built successfully • last-good artifacts saved")
                update(if (requestFlash) "Build completed — reopen app to start Flash" else "Build completed")
                if (requestFlash) AppState.operation(OperationState.BUILD_SUCCESS, "Build completed — tap Flash to continue safely")
            }.onFailure {
                val message = it.message ?: "Build failed"
                BuildState.error(message)
                AppState.operation(OperationState.BUILD_ERROR, "Build failed — see Build details")
                update("Build failed")
            }
    }

    private suspend fun runFullClean(target: String) {
        val projects = ProjectManager(applicationContext)
        BuildState.open()
        AppState.operation(OperationState.PREPARING_BUILD, "Cleaning ESP-IDF build cache…")
        update("Running ESP-IDF Full Clean…")
        runCatching { IdfBuildExecutor(applicationContext).fullClean(projects.projectDir, target) { BuildState.output(it) } }
            .onSuccess { BuildState.success(it); AppState.operation(OperationState.IDLE, "Full Clean completed — project ready for a fresh Build"); update("Full Clean completed") }
            .onFailure { val message = it.message ?: "Full Clean failed"; BuildState.error(message); AppState.operation(OperationState.BUILD_ERROR, "Full Clean failed — see details"); update("Full Clean failed") }
    }

    private suspend fun runSetup(target: String) {
        SetupState.open()
        AppState.operation(OperationState.PREPARING_BUILD, "Configuring ESP-IDF 5.5 for $target…")
        update("Configuring ESP-IDF 5.5…")
        runCatching {
            val projects = ProjectManager(applicationContext)
            val executor = IdfBuildExecutor(applicationContext)
            executor.prepare(target) { SetupState.progress(it) }
            executor.setProjectTarget(projects.projectDir, target) { SetupState.progress(SetupProgress(SetupStep.TOOLCHAIN, "Applying project target $target…", it)) }
        }.onSuccess { SetupState.success(); AppState.operation(OperationState.IDLE, "ESP-IDF 5.5 / $target ready"); update("ESP-IDF ready") }
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

    override fun onDestroy() { if (wakeLock?.isHeld == true) wakeLock?.release(); wakeLock = null; scope.cancel(); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null
}
