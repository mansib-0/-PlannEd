package com.planned

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// ID ranges — keeps each entity type's alarm IDs non-overlapping
private const val TASK_ID_BASE     = 0
private const val EVENT_ID_BASE    = 100_000
private const val REMINDER_ID_BASE = 200_000
private const val DEADLINE_ID_BASE = 300_000

@RequiresApi(Build.VERSION_CODES.O)
object NotificationScheduler {
    private val timeFormatter = DateTimeFormatter.ofPattern("h:mm a")

    // ── Helpers ──────────────────────────────────────────────────────────────
    private fun toEpochMillis(date: LocalDate, time: LocalTime): Long =
        LocalDateTime.of(date, time)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    private fun makePendingIntent(context: Context, notifId: Int, title: String, message: String, entityType: String = "", entityId: Int = -1): PendingIntent {
        val intent = Intent(context, NotificationReceiver::class.java).apply {
            putExtra("title",      title)
            putExtra("message",    message)
            putExtra("notifId",    notifId)
            putExtra("entityType", entityType)
            putExtra("entityId",   entityId)
        }
        return PendingIntent.getBroadcast(
            context, notifId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
    private fun schedule(context: Context, triggerMs: Long, notifId: Int, title: String, message: String, entityType: String = "", entityId: Int = -1) {
        if (triggerMs <= System.currentTimeMillis()) return
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pending = makePendingIntent(context, notifId, title, message, entityType, entityId)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pending)
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pending)
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pending)
            }
        } catch (e: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pending)
        }
    }
    private fun cancel(context: Context, notifId: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pending = makePendingIntent(context, notifId, "", "")
        alarmManager.cancel(pending)
    }

    // ── Tasks ─────────────────────────────────────────────────────────────────
    // One alarm per TaskInterval — fires at interval start time.
    // All-day tasks fire at the user-configured all-day time.

    @RequiresApi(Build.VERSION_CODES.O)
    fun scheduleTaskNotifications(context: Context, db: AppDatabase) {
        val settings = SettingsManager.settings ?: return
        if (!settings.notifTasksEnabled) return

        val allDayTime = LocalTime.ofSecondOfDay(settings.notifTaskAllDayTime.toLong())

        kotlinx.coroutines.runBlocking {
            // Scheduled tasks — one notif per interval
            val intervals = db.taskDao().getAllIntervals()
            intervals.forEach { interval ->
                val task = db.taskDao().getMasterTaskById(interval.masterTaskId) ?: return@forEach
                val notifId = TASK_ID_BASE + interval.id
                val triggerMs = toEpochMillis(interval.occurDate, interval.startTime)
                schedule(context, triggerMs, notifId, task.title, "Scheduled now", "task", task.id)
            }

            // All-day tasks — fire at configured all-day time on their date
            val allDayTasks = db.taskDao().getAllMasterTasks()
                .filter { it.allDay != null && it.status != 3 }
            allDayTasks.forEach { task ->
                val notifId = TASK_ID_BASE + 50_000 + task.id  // separate range from intervals
                val triggerMs = toEpochMillis(task.allDay!!, allDayTime)
                schedule(context, triggerMs, notifId, task.title, "Scheduled today", "task_allday", task.id)
            }
        }
    }

    fun cancelAllTaskNotifications(context: Context, db: AppDatabase) {
        kotlinx.coroutines.runBlocking {
            db.taskDao().getAllIntervals().forEach { cancel(context, TASK_ID_BASE + it.id) }
            db.taskDao().getAllMasterTasks()
                .filter { it.allDay != null }
                .forEach { cancel(context, TASK_ID_BASE + 50_000 + it.id) }
        }
    }

    // ── Events ────────────────────────────────────────────────────────────────
    // One alarm per EventOccurrence — fires leadMinutes before start.

    @RequiresApi(Build.VERSION_CODES.O)
    fun scheduleEventNotifications(context: Context, db: AppDatabase) {
        val settings = SettingsManager.settings ?: return
        if (!settings.notifEventsEnabled) return

        val leadMinutes = settings.notifEventLeadMinutes.toLong()

        kotlinx.coroutines.runBlocking {
            val occurrences = db.eventDao().getAllOccurrences()
            occurrences.forEach { occ ->
                val event = db.eventDao().getMasterEventById(occ.masterEventId) ?: return@forEach
                val notifId = EVENT_ID_BASE + occ.id
                val triggerMs = LocalDateTime.of(occ.occurDate, occ.startTime)
                    .minusMinutes(leadMinutes)
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
                val message = if (leadMinutes == 0L) "Starting now"
                else "Starting in ${leadMinutes} min"
                schedule(context, triggerMs, notifId, event.title, message, "event", event.id)
            }
        }
    }

    fun cancelAllEventNotifications(context: Context, db: AppDatabase) {
        kotlinx.coroutines.runBlocking {
            db.eventDao().getAllOccurrences().forEach { cancel(context, EVENT_ID_BASE + it.id) }
        }
    }

    fun cancelEventNotifications(context: Context, eventId: Int, db: AppDatabase) {
        kotlinx.coroutines.runBlocking {
            db.eventDao().getAllOccurrences()
                .filter { it.masterEventId == eventId }
                .forEach { cancel(context, EVENT_ID_BASE + it.id) }
        }
    }

    // ── Reminders ─────────────────────────────────────────────────────────────
    // Timed reminders fire at their occurrence time.
    // All-day reminders fire at the user-configured all-day time.

    @RequiresApi(Build.VERSION_CODES.O)
    fun scheduleReminderNotifications(context: Context, db: AppDatabase) {
        val settings = SettingsManager.settings ?: return
        if (!settings.notifRemindersEnabled) return

        val allDayTime = LocalTime.ofSecondOfDay(settings.notifReminderAllDayTime.toLong())

        kotlinx.coroutines.runBlocking {
            val occurrences = db.reminderDao().getAllOccurrences()
            occurrences.forEach { occ ->
                val reminder = db.reminderDao().getMasterReminderById(occ.masterReminderId) ?: return@forEach
                val notifId = REMINDER_ID_BASE + occ.id
                val fireTime = if (occ.allDay) allDayTime else (occ.time ?: allDayTime)
                val triggerMs = toEpochMillis(occ.occurDate, fireTime)
                schedule(context, triggerMs, notifId, reminder.title, "Reminder", "reminder", reminder.id)
            }
        }
    }

    fun cancelAllReminderNotifications(context: Context, db: AppDatabase) {
        kotlinx.coroutines.runBlocking {
            db.reminderDao().getAllOccurrences().forEach { cancel(context, REMINDER_ID_BASE + it.id) }
        }
    }

    fun cancelReminderNotifications(context: Context, reminderId: Int, db: AppDatabase) {
        kotlinx.coroutines.runBlocking {
            db.reminderDao().getAllOccurrences()
                .filter { it.masterReminderId == reminderId }
                .forEach { cancel(context, REMINDER_ID_BASE + it.id) }
        }
    }

    // ── Deadlines ─────────────────────────────────────────────────────────────
    // TIME_OF  → fires leadMinutes before deadline date+time
    // DAY_OF   → fires at user-configured time on the deadline date
    // DAY_BEFORE → fires at user-configured time the day before

    @RequiresApi(Build.VERSION_CODES.O)
    fun scheduleDeadlineNotifications(context: Context, db: AppDatabase) {
        val settings = SettingsManager.settings ?: return
        if (!settings.notifDeadlinesEnabled) return

        val timing      = settings.notifDeadlineTiming
        val configTime  = LocalTime.ofSecondOfDay(settings.notifDeadlineTime.toLong())
        val leadMinutes = settings.notifDeadlineLeadMinutes.toLong()

        kotlinx.coroutines.runBlocking {
            db.deadlineDao().getAll().forEach { deadline ->
                val notifId = DEADLINE_ID_BASE + deadline.id
                val triggerMs = when (timing) {
                    "DAY_OF"     -> toEpochMillis(deadline.date, configTime)
                    "DAY_BEFORE" -> toEpochMillis(deadline.date.minusDays(1), configTime)
                    else         -> LocalDateTime.of(deadline.date, deadline.time)
                        .minusMinutes(leadMinutes)
                        .atZone(ZoneId.systemDefault())
                        .toInstant()
                        .toEpochMilli()
                }
                val formattedTime = deadline.time.format(timeFormatter)
                val message = when (timing) {
                    "DAY_OF"     -> "Due today at $formattedTime"
                    "DAY_BEFORE" -> "Due tomorrow at $formattedTime"
                    else         -> if (leadMinutes == 0L) "Due now" else "Due in ${leadMinutes} min"
                }
                schedule(context, triggerMs, notifId, deadline.title, message, "deadline", deadline.id)
            }
        }
    }

    fun cancelAllDeadlineNotifications(context: Context, db: AppDatabase) {
        kotlinx.coroutines.runBlocking {
            db.deadlineDao().getAll().forEach { cancel(context, DEADLINE_ID_BASE + it.id) }
        }
    }

    fun cancelDeadlineNotification(context: Context, deadlineId: Int) {
        cancel(context, DEADLINE_ID_BASE + deadlineId)
    }

    // ── Reschedule everything ─────────────────────────────────────────────────
    // Called on boot and app start.

    @RequiresApi(Build.VERSION_CODES.O)
    fun rescheduleAll(context: Context, db: AppDatabase) {
        scheduleTaskNotifications(context, db)
        scheduleEventNotifications(context, db)
        scheduleReminderNotifications(context, db)
        scheduleDeadlineNotifications(context, db)
    }
}