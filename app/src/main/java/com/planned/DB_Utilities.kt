package com.planned

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.toColorInt
import androidx.room.TypeConverter
import com.google.gson.Gson
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/* TYPE CONVERTERS */
object Converters {
    @RequiresApi(Build.VERSION_CODES.O)
    @TypeConverter
    // LocalDate -> Long
    fun fromLocalDate(date: LocalDate?): Long? = date?.toEpochDay()

    @RequiresApi(Build.VERSION_CODES.O)
    @TypeConverter
    // Long -> LocalDate
    fun toLocalDate(days: Long?): LocalDate? = days?.let { LocalDate.ofEpochDay(it) }

    @RequiresApi(Build.VERSION_CODES.O)
    @TypeConverter
    // LocalTime -> Int
    fun fromLocalTime(time: LocalTime?): Int? = time?.toSecondOfDay()

    @RequiresApi(Build.VERSION_CODES.O)
    @TypeConverter
    // Int -> LocalTime
    fun toLocalTime(seconds: Int?): LocalTime? = seconds?.let { LocalTime.ofSecondOfDay(it.toLong()) }

    @RequiresApi(Build.VERSION_CODES.O)
    @TypeConverter
    // LocalDateTime -> Long (epoch seconds UTC)
    fun fromLocalDateTime(dateTime: LocalDateTime?): Long? = dateTime?.toEpochSecond(ZoneOffset.UTC)

    @RequiresApi(Build.VERSION_CODES.O)
    @TypeConverter
    // Long -> LocalDateTime
    fun toLocalDateTime(seconds: Long?): LocalDateTime? = seconds?.let { LocalDateTime.ofEpochSecond(it, 0, ZoneOffset.UTC) }

    @TypeConverter
    // RecurrenceFrequency -> String
    fun fromRecurrenceFrequency(freq: RecurrenceFrequency): String = freq.name

    @TypeConverter
    // String -> RecurrenceFrequency
    fun toRecurrenceFrequency(value: String): RecurrenceFrequency = RecurrenceFrequency.valueOf(value)

    @TypeConverter
    // RecurrenceRule -> JSON String
    fun fromRecurrenceRule(rule: RecurrenceRule): String = Gson().toJson(rule)

    @TypeConverter
    // JSON String -> RecurrenceRule
    fun toRecurrenceRule(value: String): RecurrenceRule = Gson().fromJson(value, RecurrenceRule::class.java)

    @TypeConverter
    // Color -> String
    fun fromColor(color: Color): String {
        return "#${color.toArgb().toUInt().toString(16).padStart(8, '0')}"
    }

    @TypeConverter
    // String -> Color
    fun toColor(value: String): Color {
        return try {
            Color(value.toColorInt())
        } catch (_: Exception) {
            Color.LightGray
        }
    }
}

data class OverlapInfo(
    val hasOverlap: Boolean,
    val conflictType: String = "",
    val conflictDate: LocalDate? = null,
    val conflictStartTime: LocalTime? = null,
    val conflictEndTime: LocalTime? = null
)

/* Check if two time ranges overlap */
@RequiresApi(Build.VERSION_CODES.O)
fun doTimeRangesOverlap(
    start1: LocalTime, end1: LocalTime,
    start2: LocalTime, end2: LocalTime
): Boolean {return start1.isBefore(end2) && end1.isAfter(start2)}

/* Check if a new Event would overlap with existing Events */
@RequiresApi(Build.VERSION_CODES.O)
suspend fun checkEventOverlapWithEvents(
    db: AppDatabase,
    startDate: LocalDate,
    endDate: LocalDate?,
    startTime: LocalTime,
    endTime: LocalTime,
    recurFreq: RecurrenceFrequency,
    recurRule: RecurrenceRule,
    excludeEventId: Int? = null
): OverlapInfo {
    val today = LocalDate.now()

    // Calculate end limit for checking
    val endLimit = endDate?.let {
        if (it.isBefore(today.plusMonths(generationMonths.toLong()))) it
        else today.plusMonths(generationMonths.toLong())
    } ?: today.plusMonths(generationMonths.toLong())

    var current = if (startDate.isBefore(today)) today else startDate

    // Get all event occurrences, excluding the event being updated
    val allEventOccurrences = db.eventDao().getAllOccurrences()
        .filter { excludeEventId == null || it.masterEventId != excludeEventId }

    // Check each date where the event would occur
    while (!current.isAfter(endLimit)) {
        val matchesRule = when (recurFreq) {
            RecurrenceFrequency.NONE -> current == startDate
            RecurrenceFrequency.DAILY -> true
            RecurrenceFrequency.WEEKLY -> recurRule.daysOfWeek?.contains(current.dayOfWeek.value) ?: true
            RecurrenceFrequency.MONTHLY -> recurRule.daysOfMonth?.contains(current.dayOfMonth) ?: true
            RecurrenceFrequency.YEARLY -> recurRule.monthAndDay?.let {
                it.first == current.dayOfMonth && it.second == current.monthValue
            } ?: true
        }

        if (matchesRule) {
            // Filter events that occur on this date
            val eventsOnDate = allEventOccurrences.filter { it.occurDate == current }

            // Check for time overlap with any event
            for (event in eventsOnDate) {
                if (doTimeRangesOverlap(startTime, endTime, event.startTime, event.endTime)) {
                    return OverlapInfo(
                        hasOverlap = true,
                        conflictType = "Event",
                        conflictDate = current,
                        conflictStartTime = event.startTime,
                        conflictEndTime = event.endTime
                    )
                }
            }
        }

        // Increment current date
        current = when (recurFreq) {
            RecurrenceFrequency.NONE,
            RecurrenceFrequency.DAILY,
            RecurrenceFrequency.WEEKLY,
            RecurrenceFrequency.MONTHLY -> current.plusDays(1)
            RecurrenceFrequency.YEARLY -> current.plusYears(1)
        }
    }

    return OverlapInfo(hasOverlap = false)
}

/* Check if a new Event would overlap with existing Task Buckets */
@RequiresApi(Build.VERSION_CODES.O)
suspend fun checkEventOverlapWithBuckets(
    db: AppDatabase,
    startDate: LocalDate,
    endDate: LocalDate?,
    startTime: LocalTime,
    endTime: LocalTime,
    recurFreq: RecurrenceFrequency,
    recurRule: RecurrenceRule
): OverlapInfo {
    val today = LocalDate.now()

    // Calculate end limit for checking
    val endLimit = endDate?.let {
        if (it.isBefore(today.plusMonths(generationMonths.toLong()))) it
        else today.plusMonths(generationMonths.toLong())
    } ?: today.plusMonths(generationMonths.toLong())

    var current = if (startDate.isBefore(today)) today else startDate

    // Check each date where the event would occur
    while (!current.isAfter(endLimit)) {
        val matchesRule = when (recurFreq) {
            RecurrenceFrequency.NONE -> current == startDate
            RecurrenceFrequency.DAILY -> true
            RecurrenceFrequency.WEEKLY -> recurRule.daysOfWeek?.contains(current.dayOfWeek.value) ?: true
            RecurrenceFrequency.MONTHLY -> recurRule.daysOfMonth?.contains(current.dayOfMonth) ?: true
            RecurrenceFrequency.YEARLY -> recurRule.monthAndDay?.let {
                it.first == current.dayOfMonth && it.second == current.monthValue
            } ?: true
        }

        if (matchesRule) {
            // Get all task bucket occurrences on this date
            val bucketsOnDate = db.taskBucketDao().getOccurrencesByDate(current)

            // Check for time overlap with any bucket
            for (bucket in bucketsOnDate) {
                if (doTimeRangesOverlap(startTime, endTime, bucket.startTime, bucket.endTime)) {
                    return OverlapInfo(
                        hasOverlap = true,
                        conflictType = "Task Bucket",
                        conflictDate = current,
                        conflictStartTime = bucket.startTime,
                        conflictEndTime = bucket.endTime
                    )
                }
            }
        }

        // Increment current date
        current = when (recurFreq) {
            RecurrenceFrequency.NONE,
            RecurrenceFrequency.DAILY,
            RecurrenceFrequency.WEEKLY,
            RecurrenceFrequency.MONTHLY -> current.plusDays(1)
            RecurrenceFrequency.YEARLY -> current.plusYears(1)
        }
    }

    return OverlapInfo(hasOverlap = false)
}

/* Check if a new Task Bucket would overlap with existing Task Buckets */
@RequiresApi(Build.VERSION_CODES.O)
suspend fun checkBucketOverlapWithBuckets(
    db: AppDatabase,
    startDate: LocalDate,
    endDate: LocalDate?,
    startTime: LocalTime,
    endTime: LocalTime,
    recurFreq: RecurrenceFrequency,
    recurRule: RecurrenceRule
): OverlapInfo {
    val today = LocalDate.now()

    // Calculate end limit for checking
    val endLimit = endDate?.let {
        if (it.isBefore(today.plusMonths(generationMonths.toLong()))) it
        else today.plusMonths(generationMonths.toLong())
    } ?: today.plusMonths(generationMonths.toLong())

    var current = if (startDate.isBefore(today)) today else startDate

    // Check each date where the bucket would occur
    while (!current.isAfter(endLimit)) {
        val matchesRule = when (recurFreq) {
            RecurrenceFrequency.NONE -> current == startDate
            RecurrenceFrequency.DAILY -> true
            RecurrenceFrequency.WEEKLY -> recurRule.daysOfWeek?.contains(current.dayOfWeek.value) ?: true
            RecurrenceFrequency.MONTHLY -> recurRule.daysOfMonth?.contains(current.dayOfMonth) ?: true
            RecurrenceFrequency.YEARLY -> recurRule.monthAndDay?.let {
                it.first == current.dayOfMonth && it.second == current.monthValue
            } ?: true
        }

        if (matchesRule) {
            // Get all task bucket occurrences on this date
            val bucketsOnDate = db.taskBucketDao().getOccurrencesByDate(current)

            // Check for time overlap with any bucket
            for (bucket in bucketsOnDate) {
                if (doTimeRangesOverlap(startTime, endTime, bucket.startTime, bucket.endTime)) {
                    return OverlapInfo(
                        hasOverlap = true,
                        conflictType = "Task Bucket",
                        conflictDate = current,
                        conflictStartTime = bucket.startTime,
                        conflictEndTime = bucket.endTime
                    )
                }
            }
        }

        // Increment current date
        current = when (recurFreq) {
            RecurrenceFrequency.NONE,
            RecurrenceFrequency.DAILY,
            RecurrenceFrequency.WEEKLY,
            RecurrenceFrequency.MONTHLY -> current.plusDays(1)
            RecurrenceFrequency.YEARLY -> current.plusYears(1)
        }
    }

    return OverlapInfo(hasOverlap = false)
}

/* Check if a new Task Bucket would overlap with existing Events */
@RequiresApi(Build.VERSION_CODES.O)
suspend fun checkBucketOverlapWithEvents(
    db: AppDatabase,
    startDate: LocalDate,
    endDate: LocalDate?,
    startTime: LocalTime,
    endTime: LocalTime,
    recurFreq: RecurrenceFrequency,
    recurRule: RecurrenceRule
): OverlapInfo {
    val today = LocalDate.now()

    // Calculate end limit for checking
    val endLimit = endDate?.let {
        if (it.isBefore(today.plusMonths(generationMonths.toLong()))) it
        else today.plusMonths(generationMonths.toLong())
    } ?: today.plusMonths(generationMonths.toLong())

    var current = if (startDate.isBefore(today)) today else startDate

    // Get all event occurrences
    val allEventOccurrences = db.eventDao().getAllOccurrences()

    // Check each date where the bucket would occur
    while (!current.isAfter(endLimit)) {
        val matchesRule = when (recurFreq) {
            RecurrenceFrequency.NONE -> current == startDate
            RecurrenceFrequency.DAILY -> true
            RecurrenceFrequency.WEEKLY -> recurRule.daysOfWeek?.contains(current.dayOfWeek.value) ?: true
            RecurrenceFrequency.MONTHLY -> recurRule.daysOfMonth?.contains(current.dayOfMonth) ?: true
            RecurrenceFrequency.YEARLY -> recurRule.monthAndDay?.let {
                it.first == current.dayOfMonth && it.second == current.monthValue
            } ?: true
        }

        if (matchesRule) {
            // Filter events that occur on this date
            val eventsOnDate = allEventOccurrences.filter { it.occurDate == current }

            // Check for time overlap with any event
            for (event in eventsOnDate) {
                if (doTimeRangesOverlap(startTime, endTime, event.startTime, event.endTime)) {
                    return OverlapInfo(
                        hasOverlap = true,
                        conflictType = "Event",
                        conflictDate = current,
                        conflictStartTime = event.startTime,
                        conflictEndTime = event.endTime
                    )
                }
            }
        }

        // Increment current date
        current = when (recurFreq) {
            RecurrenceFrequency.NONE,
            RecurrenceFrequency.DAILY,
            RecurrenceFrequency.WEEKLY,
            RecurrenceFrequency.MONTHLY -> current.plusDays(1)
            RecurrenceFrequency.YEARLY -> current.plusYears(1)
        }
    }

    return OverlapInfo(hasOverlap = false)
}

/* Format overlap info */
@RequiresApi(Build.VERSION_CODES.O)
fun formatOverlapMessage(overlapInfo: OverlapInfo): String {
    if (!overlapInfo.hasOverlap) return ""

    val dateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")
    val timeFormatter = DateTimeFormatter.ofPattern("h:mm a")

    val dateStr = overlapInfo.conflictDate?.format(dateFormatter) ?: ""
    val timeStr = overlapInfo.conflictStartTime?.format(timeFormatter) ?: ""

    return "Timing clashes with ${overlapInfo.conflictType} \non $dateStr at $timeStr"
}

/* Get maximum bucket duration */
@RequiresApi(Build.VERSION_CODES.O)
suspend fun getMaxBucketDurationMinutes(db: AppDatabase): Int? {
    val allBucketOccurrences = db.taskBucketDao().getAllBucketOccurrences()

    if (allBucketOccurrences.isEmpty()) {
        return null
    }

    return allBucketOccurrences.maxOfOrNull { occurrence ->
        val startMinutes = occurrence.startTime.hour * 60 + occurrence.startTime.minute
        val endMinutes = occurrence.endTime.hour * 60 + occurrence.endTime.minute
        endMinutes - startMinutes
    }
}

/* CASCADE NULL-SETTING FUNCTIONS */

/* Handle category deletion */
@RequiresApi(Build.VERSION_CODES.O)
suspend fun onCategoryDeleted(context: Context, db: AppDatabase, categoryId: Int) {
    // Set categoryId to null in MasterEvent
    val events = db.eventDao().getAllMasterEvents()
    events.filter { it.categoryId == categoryId }.forEach { event ->
        db.eventDao().update(event.copy(categoryId = null))
    }

    // Set categoryId to null in Deadline
    val deadlines = db.deadlineDao().getAll()
    deadlines.filter { it.categoryId == categoryId }.forEach { deadline ->
        db.deadlineDao().update(deadline.copy(categoryId = null))
    }

    // Set categoryId to null in MasterTask
    val tasks = db.taskDao().getAllMasterTasks()
    tasks.filter { it.categoryId == categoryId }.forEach { task ->
        db.taskDao().update(task.copy(categoryId = null))
    }

    // Set categoryId to null in MasterReminder
    val reminders = db.reminderDao().getAllMasterReminders()
    reminders.filter { it.categoryId == categoryId }.forEach { reminder ->
        db.reminderDao().update(reminder.copy(categoryId = null))
    }

    // Regenerate task intervals
    generateTaskIntervals(context, db)
}

/* Handle event deletion */
@RequiresApi(Build.VERSION_CODES.O)
suspend fun onEventDeleted(context: Context, db: AppDatabase, eventId: Int) {
    // Set eventId to null in Deadline
    val deadlines = db.deadlineDao().getAll()
    deadlines.filter { it.eventId == eventId }.forEach { deadline ->
        db.deadlineDao().update(deadline.copy(eventId = null))
    }

    // Set eventId to null in MasterTask
    val tasks = db.taskDao().getAllMasterTasks()
    tasks.filter { it.eventId == eventId }.forEach { task ->
        db.taskDao().update(task.copy(eventId = null))
    }

    // Regenerate task intervals
    generateTaskIntervals(context, db)
}

/* Handle deadline deletion */
@RequiresApi(Build.VERSION_CODES.O)
suspend fun onDeadlineDeleted(context: Context, db: AppDatabase, deadlineId: Int) {
    // Set deadlineId to null in MasterTask
    val tasks = db.taskDao().getAllMasterTasks()
    tasks.filter { it.deadlineId == deadlineId }.forEach { task ->
        db.taskDao().update(task.copy(deadlineId = null))
    }

    // Regenerate task intervals
    generateTaskIntervals(context, db)
}

/* Handle task bucket deletion */
@RequiresApi(Build.VERSION_CODES.O)
suspend fun onTaskBucketDeleted(context: Context, db: AppDatabase) {
    // Regenerate task intervals
    generateTaskIntervals(context, db)
}

/* Handle task updated/created/deleted */
@RequiresApi(Build.VERSION_CODES.O)
suspend fun onTaskChanged(context: Context, db: AppDatabase) {
    generateTaskIntervals(context, db)
}

/* Handle task deletion */
@RequiresApi(Build.VERSION_CODES.O)
suspend fun onTaskDeleted(context: Context, db: AppDatabase, taskId: Int) {
    // Set dependencyTaskId to null
    val tasks = db.taskDao().getAllMasterTasks()
    tasks.filter { it.dependencyTaskId == taskId }.forEach { task ->
        db.taskDao().update(task.copy(dependencyTaskId = null))
    }
}