package com.planned

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi

class NotificationReceiver : BroadcastReceiver() {
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onReceive(context: Context, intent: Intent) {
        val title   = intent.getStringExtra("title")   ?: return
        val message = intent.getStringExtra("message") ?: ""
        val notifId = intent.getIntExtra("notifId", 0)
        val channelId = "planned_main"

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(channelId, "Planned", NotificationManager.IMPORTANCE_HIGH)
        manager.createNotificationChannel(channel)

        // Tap notification -> open app and deep-link to the relevant entity
        val entityType = intent.getStringExtra("entityType") ?: ""
        val entityId   = intent.getIntExtra("entityId", -1)
        val tapIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            putExtra("entityType", entityType)
            putExtra("entityId",   entityId)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingTap = PendingIntent.getActivity(
            context, notifId, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = androidx.core.app.NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingTap)
            .setAutoCancel(true)
            .build()

        manager.notify(notifId, notification)
    }
}