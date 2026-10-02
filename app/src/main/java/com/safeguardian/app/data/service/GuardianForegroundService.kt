package com.safeguardian.app.data.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import com.safeguardian.app.MainActivity
import com.safeguardian.app.R
import com.safeguardian.app.SafeGuardianApp
import com.safeguardian.app.domain.model.PipelineStage
import com.safeguardian.app.domain.model.SafetyState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest

/**
 * Foreground service for continuous safety monitoring.
 * Required by Android to keep sensor monitoring alive in the background.
 *
 * Uses FOREGROUND_SERVICE_HEALTH type as the primary service type.
 * Shows a persistent notification as required by Android.
 */
class GuardianForegroundService : LifecycleService() {

    companion object {
        const val CHANNEL_ID_GUARDIAN = "guardian_monitoring"
        const val CHANNEL_ID_EMERGENCY = "emergency_alerts"
        const val NOTIFICATION_ID_GUARDIAN = 1001
        const val NOTIFICATION_ID_EMERGENCY = 1002

        const val ACTION_START = "com.safeguardian.ACTION_START"
        const val ACTION_STOP = "com.safeguardian.ACTION_STOP"

        fun startService(context: Context) {
            val intent = Intent(context, GuardianForegroundService::class.java).apply {
                action = ACTION_START
            }
            context.startForegroundService(intent)
        }

        fun stopService(context: Context) {
            val intent = Intent(context, GuardianForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var isMonitoring = false

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START -> startMonitoring()
            ACTION_STOP -> stopMonitoring()
        }
        return START_STICKY
    }

    private fun startMonitoring() {
        if (isMonitoring) return

        val notification = createGuardianNotification("Monitoring your safety")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID_GUARDIAN,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
            )
        } else {
            startForeground(NOTIFICATION_ID_GUARDIAN, notification)
        }

        isMonitoring = true

        val app = application as SafeGuardianApp

        // Start motion sensor monitoring
        app.motionSensorManager.start()

        // Start motion processing loop
        serviceScope.launch {
            app.motionSensorManager.latestFeatures.collectLatest { features ->
                if (features.timestamp > 0) {
                    app.stateEngine.processMotionData(features)
                }
            }
        }

        // Monitor state changes for notifications
        serviceScope.launch {
            app.stateEngine.safetyState.collectLatest { state ->
                updateNotificationForState(state)
            }
        }

        // Trigger on-demand audio investigation when pipeline requests it
        serviceScope.launch {
            app.stateEngine.pipelineStage.collectLatest { stage ->
                if (stage == PipelineStage.AUDIO_ANALYSIS || stage == PipelineStage.MULTIMODAL_FUSION) {
                    app.audioManager.startInvestigation(app.stateEngine)
                } else if (stage == PipelineStage.IDLE || stage == PipelineStage.MOTION_ANALYSIS) {
                    app.audioManager.stopInvestigation()
                }

                // Trigger on-demand vision investigation (Phase 4)
                if (stage == PipelineStage.VISION_ANALYSIS || stage == PipelineStage.MULTIMODAL_FUSION) {
                    app.cameraManager.startInvestigation(this@GuardianForegroundService, app.stateEngine)
                } else if (stage == PipelineStage.IDLE || stage == PipelineStage.MOTION_ANALYSIS || stage == PipelineStage.AUDIO_ANALYSIS) {
                    app.cameraManager.stopInvestigation()
                }
            }
        }
    }

    private fun stopMonitoring() {
        isMonitoring = false
        val app = application as SafeGuardianApp
        app.motionSensorManager.stop()
        app.audioManager.stopInvestigation()
        app.cameraManager.stopInvestigation()
        serviceScope.coroutineContext.cancelChildren()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannels() {
        val guardianChannel = NotificationChannel(
            CHANNEL_ID_GUARDIAN,
            getString(R.string.notification_channel_guardian),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows when SafeGuardian is actively monitoring"
            setShowBadge(false)
        }

        val emergencyChannel = NotificationChannel(
            CHANNEL_ID_EMERGENCY,
            getString(R.string.notification_channel_emergency),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Critical emergency detection alerts"
            enableVibration(true)
            setShowBadge(true)
        }

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(guardianChannel)
        notificationManager.createNotificationChannel(emergencyChannel)
    }

    private fun createGuardianNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID_GUARDIAN)
            .setContentTitle(getString(R.string.notification_guardian_title))
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun updateNotificationForState(state: SafetyState) {
        val notificationManager = getSystemService(NotificationManager::class.java)

        val text = when (state) {
            SafetyState.NORMAL -> "Monitoring your safety"
            SafetyState.ANOMALY_CANDIDATE -> "Analyzing motion anomaly..."
            SafetyState.INVESTIGATING -> "Investigating potential event..."
            SafetyState.SUSPICIOUS -> "Suspicious activity detected"
            SafetyState.HIGH_CONFIDENCE_EMERGENCY -> "⚠️ Emergency detected!"
            SafetyState.ACTIVE_EMERGENCY -> "🚨 ACTIVE EMERGENCY"
            SafetyState.RESOLVED -> "Emergency resolved"
        }

        val notification = createGuardianNotification(text)
        notificationManager.notify(NOTIFICATION_ID_GUARDIAN, notification)

        // Emergency notification
        if (state == SafetyState.ACTIVE_EMERGENCY) {
            val emergencyNotification = NotificationCompat.Builder(this, CHANNEL_ID_EMERGENCY)
                .setContentTitle("🚨 Emergency Detected")
                .setContentText("SafeGuardian has detected a potential emergency. Tap to view details.")
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(false)
                .setContentIntent(
                    PendingIntent.getActivity(
                        this, 1,
                        Intent(this, MainActivity::class.java),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                )
                .build()
            notificationManager.notify(NOTIFICATION_ID_EMERGENCY, emergencyNotification)
        } else if (state == SafetyState.NORMAL || state == SafetyState.RESOLVED) {
            notificationManager.cancel(NOTIFICATION_ID_EMERGENCY)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        val app = application as? SafeGuardianApp
        app?.motionSensorManager?.stop()
    }
}
