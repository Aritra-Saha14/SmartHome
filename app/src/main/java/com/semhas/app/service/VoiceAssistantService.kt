package com.semhas.app.service

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
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.semhas.app.MainActivity
import com.semhas.app.R
import com.semhas.app.SemhasApplication
import com.semhas.app.voice.VoiceAssistantState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class VoiceAssistantService : Service() {

    companion object {
        const val ACTION_START = "com.semhas.app.action.START_VOICE_ASSISTANT"
        const val ACTION_STOP = "com.semhas.app.action.STOP_VOICE_ASSISTANT"
        const val CHANNEL_ID = "semhas_voice_service_channel"
        const val NOTIFICATION_ID = 4101
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakeLock()
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = powerManager?.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "SEMHAS:VoiceAssistantServiceWakeLock"
            )?.apply {
                setReferenceCounted(false)
                acquire(12 * 60 * 60 * 1000L) // 12-hour safe upper bound
            }
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {}
        wakeLock = null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopVoiceService()
                return START_NOT_STICKY
            }
            else -> {
                startForegroundWithNotification()
                observeAssistantState()
            }
        }
        return START_STICKY
    }

    private fun startForegroundWithNotification() {
        val initialNotification = buildNotification("Listening for \"Hey Jarvis\"...")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                initialNotification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, initialNotification)
        }
    }

    private fun observeAssistantState() {
        val manager = SemhasApplication.instance.voiceAssistantManager
        serviceScope.launch {
            manager.state.collectLatest { state ->
                val notificationText = when (state) {
                    VoiceAssistantState.WAITING_FOR_WAKE -> "Listening for \"Hey Jarvis\"..."
                    VoiceAssistantState.WAKE_DETECTED -> "\"Hey Jarvis\" detected..."
                    VoiceAssistantState.LISTENING -> "Listening for your command..."
                    VoiceAssistantState.PROCESSING -> "Processing command..."
                    VoiceAssistantState.SPEAKING -> manager.lastSpokenResponse.value ?: "Assistant responding..."
                    VoiceAssistantState.COMPLETED -> manager.lastSpokenResponse.value ?: manager.lastExecutedAction.value ?: "Appliance updated"
                    VoiceAssistantState.UNSUPPORTED_COMMAND -> manager.lastSpokenResponse.value ?: manager.lastExecutedAction.value ?: "Unsupported command"
                    VoiceAssistantState.PERMISSION_REQUIRED -> "Microphone permission required"
                    VoiceAssistantState.BACKGROUND_UNAVAILABLE -> "Background voice unavailable"
                    VoiceAssistantState.READY -> "Ready for voice commands"
                    VoiceAssistantState.OFF -> "Voice Assistant stopped"
                }

                updateNotification(notificationText)

                if (state == VoiceAssistantState.OFF) {
                    stopVoiceService()
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "SEMHAS Voice Assistant Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the SEMHAS Voice Assistant active for \"Hey Jarvis\" wake phrase"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(contentText: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, VoiceAssistantService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SEMHAS Voice Assistant")
            .setContentText(contentText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(openAppPendingIntent)
            .setOngoing(true)
            .addAction(0, "Turn OFF", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun updateNotification(contentText: String) {
        val notification = buildNotification(contentText)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun stopVoiceService() {
        releaseWakeLock()
        SemhasApplication.instance.voiceAssistantManager.toggleAssistant(false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseWakeLock()
        serviceScope.cancel()
    }
}
