package com.planned

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@RequiresApi(Build.VERSION_CODES.O)
class TimerForegroundService : Service() {

    private val scope = CoroutineScope(Dispatchers.Default)
    private var tickJob: Job? = null

    companion object {
        const val CHANNEL_ID  = "planned_timer"
        const val NOTIF_ID    = 999_999
        const val ACTION_START = "com.planned.TIMER_START"
        const val ACTION_STOP  = "com.planned.TIMER_STOP"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startTicking()
            ACTION_STOP  -> stopSelf()
        }
        return START_STICKY
    }

    private fun startTicking() {
        createChannel()
        startForeground(NOTIF_ID, buildNotification())

        tickJob?.cancel()
        tickJob = scope.launch {
            while (PomodoroState.isRunning) {
                delay(1000L)
                PomodoroState.elapsedSeconds++
                PomodoroState.sessionSeconds++
                // Refresh notification so elapsed time stays current
                updateNotification()
            }
            // Timer was paused/stopped externally — shut down
            stopSelf()
        }
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Timer",
            NotificationManager.IMPORTANCE_LOW  // Low = no sound, no pop-up, but always visible
        ).apply {
            setShowBadge(false)
        }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    private fun buildNotification() = run {
        // Tap notification -> open app and navigate to the running pomodoro page
        val tapIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            putExtra("entityType", if (PomodoroState.isAllDay) "pomodoro_allday" else "pomodoro")
            putExtra("entityId",   PomodoroState.activeTaskId ?: -1)
            flags = android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingTap = PendingIntent.getActivity(
            this, 0, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val elapsed  = PomodoroState.elapsedSeconds
        val h        = elapsed / 3600
        val m        = (elapsed % 3600) / 60
        val s        = elapsed % 60
        val timeStr  = if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)

        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(PomodoroState.activeTaskTitle)
            .setContentText(timeStr)
            .setContentIntent(pendingTap)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .build()
    }

    private fun updateNotification() {
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIF_ID, buildNotification())
    }

    override fun onDestroy() {
        tickJob?.cancel()
        super.onDestroy()
    }
}