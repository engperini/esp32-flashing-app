package com.engperini.esp32flashingapp.runtime

import android.app.*
import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.os.BatteryManager
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
        private const val ACTION_CANCEL_BUILD = "cancel_build"
        private const val EXTRA_TARGET = "target"

        fun build(context: Context, target: String) = start(context, ACTION_BUILD, target)
        fun setup(context: Context, target: String) = start(context, ACTION_SETUP, target)
        fun doctor(context: Context, target: String) = start(context, ACTION_DOCTOR, target)
        fun fullClean(context: Context, target: String) = start(context, ACTION_FULL_CLEAN, target)
        fun buildAndFlash(context: Context, target: String) = start(context, ACTION_BUILD_FLASH, target)
        fun cancelBuild(context: Context) {
            val i = Intent(context, IdfOperationService::class.java).setAction(ACTION_CANCEL_BUILD)
            context.startService(i)
        }
        private fun start(context: Context, action: String, target: String) {
            val i = Intent(context, IdfOperationService::class.java).setAction(action).putExtra(EXTRA_TARGET, target)
            androidx.core.content.ContextCompat.startForegroundService(context, i)
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var running = false
    @Volatile private var activeBuildExecutor: IdfBuildExecutor? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var powerUsbReceiverRegistered = false

    private val powerUsbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val event = when (intent.action) {
                Intent.ACTION_POWER_CONNECTED -> "POWER_CONNECTED"
                Intent.ACTION_POWER_DISCONNECTED -> "POWER_DISCONNECTED"
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> "USB_DEVICE_ATTACHED"
                UsbManager.ACTION_USB_DEVICE_DETACHED -> "USB_DEVICE_DETACHED"
                else -> return
            }
            PersistentDiagnosticLog.append(
                applicationContext,
                event,
                PersistentDiagnosticLog.deviceState(applicationContext, intent)
            )
        }
    }

    override fun onCreate() {
        super.onCreate()
        PersistentDiagnosticLog.append(applicationContext, "SERVICE_CREATE", PersistentDiagnosticLog.deviceState(applicationContext))
        registerPowerUsbDiagnostics()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "ESP-IDF operations", NotificationManager.IMPORTANCE_LOW))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        PersistentDiagnosticLog.append(applicationContext, "SERVICE_START", "action=${intent?.action} flags=$flags startId=$startId")
        if (intent?.action == ACTION_CANCEL_BUILD) {
            activeBuildExecutor?.cancelCurrentBuild()
            BuildState.cancelling()
            update("Cancelling Build…")
            return START_NOT_STICKY
        }
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
        val executor = IdfBuildExecutor(applicationContext)
        activeBuildExecutor = executor
        PersistentDiagnosticLog.append(applicationContext, "BUILD_START", "target=$target")
        runCatching { executor.buildPrepared(projects.projectDir, target) { BuildState.output(it, "Compiling firmware…"); PersistentDiagnosticLog.appendBuildOutput(applicationContext, it) } }
.onSuccess {
                PersistentDiagnosticLog.append(applicationContext, "BUILD_SUCCESS")
                FlashPlanLoader.promoteLastGood(projects.projectDir)
                BuildState.success(it)
                AppState.operation(OperationState.BUILD_SUCCESS, "Firmware built successfully • last-good artifacts saved")
                update(if (requestFlash) "Build completed — reopen app to start Flash" else "Build completed")
                if (requestFlash) AppState.operation(OperationState.BUILD_SUCCESS, "Build completed — tap Flash to continue safely")
            }.onFailure {
                PersistentDiagnosticLog.append(applicationContext, "BUILD_FAILURE", it.stackTraceToString().takeLast(12000))
                if (it is IdfBuildExecutor.BuildCancelledException) {
                    BuildState.cancelled()
                    AppState.operation(OperationState.IDLE, "Build cancelled • partial build preserved")
                    update("Build cancelled")
                } else {
                    val message = it.message ?: "Build failed"
                    BuildState.error(message)
                    AppState.operation(OperationState.BUILD_ERROR, "Build failed — see Build details")
                    update("Build failed")
                }
            }
        activeBuildExecutor = null
    }

    private suspend fun runFullClean(target: String) {
        val projects = ProjectManager(applicationContext)
        BuildState.open("ESP-IDF Full Clean", "Cleaning build output…")
        AppState.operation(OperationState.PREPARING_BUILD, "Cleaning ESP-IDF build cache…")
        update("Running ESP-IDF Full Clean…")
        runCatching { IdfBuildExecutor(applicationContext).fullClean(projects.projectDir, target) { BuildState.output(it, "Cleaning build output…") } }
            .onSuccess { BuildState.success(it, "Full Clean completed"); AppState.operation(OperationState.IDLE, "Full Clean completed — project ready for a fresh Build"); update("Full Clean completed") }
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
        }.onSuccess { SetupState.progress(SetupProgress(SetupStep.READY, "ESP-IDF 5.5 / $target ready", fraction = 1f, completed = true)); SetupState.success(); AppState.operation(OperationState.IDLE, "ESP-IDF 5.5 / $target ready"); update("ESP-IDF ready") }
            .onFailure {
                val message = it.message ?: "ESP-IDF setup failed"
                SetupState.error(message); AppState.operation(OperationState.BUILD_ERROR, message); update("ESP-IDF setup failed")
            }
    }

    private suspend fun runDoctor(target: String) {
        BuildState.open("ESP-IDF Doctor", "Checking environment…")
        AppState.operation(OperationState.PREPARING_BUILD, "Checking ESP-IDF 5.5 environment…")
        update("Checking ESP-IDF environment…")
        runCatching { IdfBuildExecutor(applicationContext).doctor(target) { BuildState.output(it, "Checking environment…") } }
            .onSuccess { BuildState.success(it, "Environment healthy"); AppState.operation(OperationState.IDLE, "ESP-IDF environment healthy"); update("ESP-IDF environment healthy") }
            .onFailure {
                val message = it.message ?: "ESP-IDF Doctor failed"
                BuildState.error(message); AppState.operation(OperationState.BUILD_ERROR, "ESP-IDF repair needed"); update("ESP-IDF Doctor failed")
            }
    }

    private fun registerPowerUsbDiagnostics() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        registerReceiver(powerUsbReceiver, filter)
        powerUsbReceiverRegistered = true
        PersistentDiagnosticLog.append(applicationContext, "POWER_USB_MONITOR_READY", PersistentDiagnosticLog.deviceState(applicationContext))
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

    override fun onDestroy() {
        PersistentDiagnosticLog.append(applicationContext, "SERVICE_DESTROY", PersistentDiagnosticLog.deviceState(applicationContext))
        if (powerUsbReceiverRegistered) {
            runCatching { unregisterReceiver(powerUsbReceiver) }
            powerUsbReceiverRegistered = false
        }
        if (wakeLock?.isHeld == true) wakeLock?.release()
        wakeLock = null
        scope.cancel()
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
}
