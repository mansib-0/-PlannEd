package com.planned

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/* SETTINGS */
object SettingsManager {
    var settings by mutableStateOf<AppSetting?>(null)
        private set

    // Load settings from DB
    suspend fun load(db: AppDatabase) {
        var s = db.settingsDao().getAll()

        // If null, insert defaults
        if (s == null) {
            s = AppSetting(
                id = 0,
                startWeekOnMonday = false,
                primaryColor = Converters.fromColor(Preset23),
                breakDuration = 5,
                breakEvery = 25,
                atiPaddingEnabled = true
            )
            db.settingsDao().insert(s)
        }
        settings = s

        // Seed None ATI records if absent — these are permanent like the settings row
        if (db.categoryATIDao().getById(0) == null) {
            db.categoryATIDao().insert(CategoryATI(categoryId = 0))
        }
        if (db.eventATIDao().getById(0) == null) {
            db.eventATIDao().insert(EventATI(eventId = 0))
        }
    }

    // Update fields
    suspend fun setPrimaryColor(db: AppDatabase, color: Color) {
        val hex = Converters.fromColor(color)
        db.settingsDao().updatePrimaryColor(hex)
        settings = settings?.copy(primaryColor = hex)
    }
    suspend fun setStartWeek(db: AppDatabase, monday: Boolean) {
        db.settingsDao().updateStartWeekOnMonday(monday)
        settings = settings?.copy(startWeekOnMonday = monday)
    }
    suspend fun setBreakDuration(db: AppDatabase, minutes: Int) {
        db.settingsDao().updateBreakDuration(minutes)
        settings = settings?.copy(breakDuration = minutes)
    }
    suspend fun setBreakEvery(db: AppDatabase, minutes: Int) {
        db.settingsDao().updateBreakEvery(minutes)
        settings = settings?.copy(breakEvery = minutes)
    }
    suspend fun setAtiPaddingEnabled(db: AppDatabase, enabled: Boolean) {
        db.settingsDao().updateAtiPaddingEnabled(enabled)
        settings = settings?.copy(atiPaddingEnabled = enabled)
    }
    suspend fun setNotifTasksEnabled(db: AppDatabase, value: Boolean) {
        db.settingsDao().updateNotifTasksEnabled(value)
        settings = settings?.copy(notifTasksEnabled = value)
    }
    suspend fun setNotifEventsEnabled(db: AppDatabase, value: Boolean) {
        db.settingsDao().updateNotifEventsEnabled(value)
        settings = settings?.copy(notifEventsEnabled = value)
    }
    suspend fun setNotifEventLeadMinutes(db: AppDatabase, minutes: Int) {
        db.settingsDao().updateNotifEventLeadMinutes(minutes)
        settings = settings?.copy(notifEventLeadMinutes = minutes)
    }
    suspend fun setNotifRemindersEnabled(db: AppDatabase, value: Boolean) {
        db.settingsDao().updateNotifRemindersEnabled(value)
        settings = settings?.copy(notifRemindersEnabled = value)
    }
    suspend fun setNotifReminderAllDayTime(db: AppDatabase, seconds: Int) {
        db.settingsDao().updateNotifReminderAllDayTime(seconds)
        settings = settings?.copy(notifReminderAllDayTime = seconds)
    }
    suspend fun setNotifTaskAllDayTime(db: AppDatabase, seconds: Int) {
        db.settingsDao().updateNotifTaskAllDayTime(seconds)
        settings = settings?.copy(notifTaskAllDayTime = seconds)
    }
    suspend fun setNotifDeadlinesEnabled(db: AppDatabase, value: Boolean) {
        db.settingsDao().updateNotifDeadlinesEnabled(value)
        settings = settings?.copy(notifDeadlinesEnabled = value)
    }
    suspend fun setNotifDeadlineTiming(db: AppDatabase, timing: String) {
        db.settingsDao().updateNotifDeadlineTiming(timing)
        settings = settings?.copy(notifDeadlineTiming = timing)
    }
    suspend fun setNotifDeadlineLeadMinutes(db: AppDatabase, minutes: Int) {
        db.settingsDao().updateNotifDeadlineLeadMinutes(minutes)
        settings = settings?.copy(notifDeadlineLeadMinutes = minutes)
    }
    suspend fun setNotifDeadlineTime(db: AppDatabase, seconds: Int) {
        db.settingsDao().updateNotifDeadlineTime(seconds)
        settings = settings?.copy(notifDeadlineTime = seconds)
    }
}

/* CATEGORY MANAGER */
object CategoryManager {
    // Insert new category
    suspend fun insert(
        db: AppDatabase,
        title: String,
        notes: String?,
        color: Color
    ): Int {
        val category = Category(
            title = title,
            notes = notes,
            color = Converters.fromColor(color)
        )
        val insertedId = db.categoryDao().insert(category).toInt()

        // Create initialized ATI record
        db.categoryATIDao().insert(CategoryATI(categoryId = insertedId))

        return insertedId
    }

    // Get all categories
    suspend fun getAll(db: AppDatabase): List<Category> {
        return db.categoryDao().getAll()
    }

    // Update category
    suspend fun update(db: AppDatabase, category: Category) {
        db.categoryDao().update(category)
    }

    // Delete category and cascade null-setting
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun delete(context: Context, db: AppDatabase, categoryId: Int) {
        onCategoryDeleted(context, db, categoryId)
        db.categoryATIDao().deleteById(categoryId)
        db.categoryDao().deleteById(categoryId)
    }
}

/* EVENT MANAGER */
object EventManager {
    // Insert new master event
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun insert(
        context: Context,
        db: AppDatabase,
        title: String,
        notes: String?,
        color: Color?,
        startDate: LocalDate,
        endDate: LocalDate?,
        startTime: LocalTime,
        endTime: LocalTime,
        recurFreq: RecurrenceFrequency,
        recurRule: RecurrenceRule,
        categoryId: Int?
    ): Int {
        val event = MasterEvent(
            title = title,
            notes = notes,
            color = color?.let { Converters.fromColor(it) },
            startDate = startDate,
            endDate = endDate,
            startTime = startTime,
            endTime = endTime,
            recurFreq = recurFreq,
            recurRule = recurRule,
            categoryId = categoryId
        )

        // Insert master event and get ID
        val insertedId = db.eventDao().insert(event).toInt()

        // Create a copy with ID
        val insertedEvent = event.copy(id = insertedId)

        // Generate occurrences
        val occurrences = generateEventOccurrences(insertedEvent)

        // Insert generated occurrences
        occurrences.forEach { occurrence ->
            db.eventDao().insertOccurrence(occurrence)
        }

        // Create initialized ATI record
        db.eventATIDao().insert(EventATI(eventId = insertedId))

        // Rerun scheduler since event times affect available slots
        onTaskChanged(context, db)

        // Schedule notifications for new occurrences
        NotificationScheduler.cancelAllEventNotifications(context, db)
        NotificationScheduler.scheduleEventNotifications(context, db)

        return insertedId
    }

    // Get all master events
    suspend fun getAll(db: AppDatabase): List<MasterEvent> {
        return db.eventDao().getAllMasterEvents()
    }

    // Update master event
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun update(context: Context, db: AppDatabase, event: MasterEvent) {
        db.eventDao().update(event)

        // Delete old occurrences
        val oldOccurrences = db.eventDao().getAllOccurrences()
            .filter { it.masterEventId == event.id }
        oldOccurrences.forEach { db.eventDao().deleteOccurrence(it.id) }

        // Regenerate occurrences
        val occurrences = generateEventOccurrences(event)
        occurrences.forEach { occurrence ->
            db.eventDao().insertOccurrence(occurrence)
        }

        // Rerun scheduler since event times affect available slots
        onTaskChanged(context, db)

        // Reschedule notifications for this event
        NotificationScheduler.cancelEventNotifications(context, event.id, db)
        NotificationScheduler.scheduleEventNotifications(context, db)
    }

    // Delete master event and cascade null-setting
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun delete(context: Context, db: AppDatabase, eventId: Int) {
        // Null-set references in deadlines and tasks first
        val deadlines = db.deadlineDao().getAll()
        deadlines.filter { it.eventId == eventId }.forEach { deadline ->
            db.deadlineDao().update(deadline.copy(eventId = null))
        }
        val tasks = db.taskDao().getAllMasterTasks()
        tasks.filter { it.eventId == eventId }.forEach { task ->
            db.taskDao().update(task.copy(eventId = null))
        }

        // Delete master event (cascades to occurrences)
        db.eventATIDao().deleteById(eventId)
        db.eventDao().deleteMasterEvent(eventId)

        // Rerun scheduler now that event occurrences are gone
        generateTaskIntervals(context, db)

        // Cancel notifications for deleted event
        NotificationScheduler.cancelEventNotifications(context, eventId, db)
    }
}

/* DEADLINE MANAGER */
object DeadlineManager {
    // Insert new deadline
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun insert(
        context: Context,
        db: AppDatabase,
        title: String,
        notes: String?,
        date: LocalDate,
        time: LocalTime,
        categoryId: Int?,
        eventId: Int?
    ): Int {
        val deadline = Deadline(
            title = title,
            notes = notes,
            date = date,
            time = time,
            categoryId = categoryId,
            eventId = eventId
        )
        val insertedId = db.deadlineDao().insert(deadline).toInt()

        // Regenerate task intervals
        onTaskChanged(context, db)

        // Schedule notification for new deadline
        NotificationScheduler.cancelAllDeadlineNotifications(context, db)
        NotificationScheduler.scheduleDeadlineNotifications(context, db)

        return insertedId
    }

    // Get all deadlines
    suspend fun getAll(db: AppDatabase): List<Deadline> {
        return db.deadlineDao().getAll()
    }

    // Update deadline
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun update(context: Context, db: AppDatabase, deadline: Deadline) {
        db.deadlineDao().update(deadline)

        // Regenerate task intervals
        onTaskChanged(context, db)

        // Reschedule notification for this deadline
        NotificationScheduler.cancelDeadlineNotification(context, deadline.id)
        NotificationScheduler.scheduleDeadlineNotifications(context, db)
    }

    // Delete deadline and cascade null-setting
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun delete(context: Context, db: AppDatabase, deadlineId: Int) {
        onDeadlineDeleted(context, db, deadlineId)
        db.deadlineDao().deleteById(deadlineId)

        // Cancel notification for deleted deadline
        NotificationScheduler.cancelDeadlineNotification(context, deadlineId)
    }
}

/* TASK BUCKET MANAGER */
object TaskBucketManager {
    // Insert new master task bucket
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun insert(
        context: Context,
        db: AppDatabase,
        startDate: LocalDate,
        endDate: LocalDate?,
        startTime: LocalTime,
        endTime: LocalTime,
        recurFreq: RecurrenceFrequency,
        recurRule: RecurrenceRule
    ) {
        val bucket = MasterTaskBucket(
            startDate = startDate,
            endDate = endDate,
            startTime = startTime,
            endTime = endTime,
            recurFreq = recurFreq,
            recurRule = recurRule
        )

        // Insert master bucket and get ID
        val insertedId = db.taskBucketDao().insert(bucket).toInt()

        // Create a copy with ID
        val insertedBucket = bucket.copy(id = insertedId)

        // Generate occurrences with merging
        val occurrences = generateTaskBucketOccurrences(insertedBucket, db)

        // Insert all generated occurrences
        occurrences.forEach { occurrence ->
            db.taskBucketDao().insertOccurrence(occurrence)
        }

        // Regenerate task intervals
        onTaskBucketDeleted(context, db)
    }

    // Get all master buckets
    suspend fun getAll(db: AppDatabase): List<MasterTaskBucket> {
        return db.taskBucketDao().getAllMasterBuckets()
    }

    // Update master bucket
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun update(context: Context, db: AppDatabase, bucket: MasterTaskBucket) {
        db.taskBucketDao().update(bucket)

        // Delete old occurrences
        val oldOccurrences = db.taskBucketDao().getAllBucketOccurrences()
            .filter { it.masterBucketId == bucket.id }
        oldOccurrences.forEach { db.taskBucketDao().deleteOccurrence(it.id) }

        // Regenerate occurrences
        val occurrences = generateTaskBucketOccurrences(bucket, db)
        occurrences.forEach { occurrence ->
            db.taskBucketDao().insertOccurrence(occurrence)
        }

        // Regenerate task intervals with updated bucket times
        onTaskBucketDeleted(context, db)
    }

    // Delete master bucket
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun delete(context: Context, db: AppDatabase, bucketId: Int) {
        db.taskBucketDao().deleteMasterBucket(bucketId)

        // Regenerate task intervals
        onTaskBucketDeleted(context, db)
    }
}

/* TASK MANAGER */
object TaskManager {
    // Insert new master task
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun insert(
        context: Context,
        db: AppDatabase,
        title: String,
        notes: String?,
        allDay: java.time.LocalDate?,
        breakable: Boolean,
        startDate: LocalDate?,
        startTime: LocalTime?,
        predictedDuration: Int,
        categoryId: Int?,
        eventId: Int?,
        deadlineId: Int?,
        dependencyTaskId: Int?
    ): Int {
        val task = MasterTask(
            title = title,
            notes = notes,
            allDay = allDay,
            breakable = breakable,
            noIntervals = 0,
            startDate = startDate,
            startTime = startTime,
            predictedDuration = predictedDuration,
            categoryId = categoryId,
            eventId = eventId,
            deadlineId = deadlineId,
            dependencyTaskId = dependencyTaskId
        )
        val insertedId = db.taskDao().insert(task).toInt()

        // Regenerate task intervals (task notifications handled inside generateTaskIntervals)
        onTaskChanged(context, db)

        return insertedId
    }

    // Get all master tasks
    suspend fun getAll(db: AppDatabase): List<MasterTask> {
        return db.taskDao().getAllMasterTasks()
    }

    // Update master task
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun update(context: Context, db: AppDatabase, task: MasterTask) {
        // Stamp completedAt when task is being marked complete
        val taskToWrite = if (task.status == 3 && task.completedAt == null)
            task.copy(completedAt = LocalDateTime.now())
        else
            task
        db.taskDao().update(taskToWrite)

        // Delete all-day tasks immediately on completion — they have no ATI use
        if (taskToWrite.allDay != null && taskToWrite.status == 3) {
            onTaskDeleted(context, db, taskToWrite.id)
            db.taskDao().deleteMasterTask(taskToWrite.id)
            return
        }

        // Regenerate task intervals (task notifications handled inside generateTaskIntervals)
        onTaskChanged(context, db)
    }

    // Delete master task
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun delete(context: Context, db: AppDatabase, taskId: Int) {
        // Handle dependency
        onTaskDeleted(context, db, taskId)

        db.taskDao().deleteMasterTask(taskId)

        // Reschedule remaining tasks into freed slots (task notifications handled inside generateTaskIntervals)
        onTaskChanged(context, db)
    }
}

/* REMINDER MANAGER */
object ReminderManager {
    // Insert new master reminder
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun insert(
        context: Context,
        db: AppDatabase,
        title: String,
        notes: String?,
        startDate: LocalDate,
        endDate: LocalDate?,
        time: LocalTime?,
        allDay: Boolean,
        recurFreq: RecurrenceFrequency,
        recurRule: RecurrenceRule,
        categoryId: Int?
    ) {
        val reminder = MasterReminder(
            title = title,
            notes = notes,
            startDate = startDate,
            endDate = endDate,
            time = time,
            allDay = allDay,
            recurFreq = recurFreq,
            recurRule = recurRule,
            categoryId = categoryId
        )

        // Insert master reminder and get ID
        val insertedId = db.reminderDao().insert(reminder).toInt()

        // Create a copy with ID
        val insertedReminder = reminder.copy(id = insertedId)

        // Generate occurrences
        val occurrences = generateReminderOccurrences(insertedReminder)

        // Insert all generated occurrences
        occurrences.forEach { occurrence ->
            db.reminderDao().insertOccurrence(occurrence)
        }

        // Schedule notifications for new occurrences
        NotificationScheduler.cancelAllReminderNotifications(context, db)
        NotificationScheduler.scheduleReminderNotifications(context, db)
    }

    // Get all master reminders
    suspend fun getAll(db: AppDatabase): List<MasterReminder> {
        return db.reminderDao().getAllMasterReminders()
    }

    // Update master reminder
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun update(context: Context, db: AppDatabase, reminder: MasterReminder) {
        db.reminderDao().update(reminder)

        // Delete old occurrences
        val oldOccurrences = db.reminderDao().getAllOccurrences()
            .filter { it.masterReminderId == reminder.id }
        oldOccurrences.forEach { db.reminderDao().deleteOccurrence(it.id) }

        // Regenerate occurrences
        val occurrences = generateReminderOccurrences(reminder)
        occurrences.forEach { occurrence ->
            db.reminderDao().insertOccurrence(occurrence)
        }

        // Reschedule notifications for this reminder
        NotificationScheduler.cancelReminderNotifications(context, reminder.id, db)
        NotificationScheduler.scheduleReminderNotifications(context, db)
    }

    // Delete master reminder
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun delete(context: Context, db: AppDatabase, reminderId: Int) {
        db.reminderDao().deleteMasterReminder(reminderId)

        // Cancel notifications for deleted reminder
        NotificationScheduler.cancelReminderNotifications(context, reminderId, db)
    }
}