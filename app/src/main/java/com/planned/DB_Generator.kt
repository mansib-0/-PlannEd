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
import java.time.LocalTime

/* RECURRENCE LOGIC */
//<editor-fold desc="Recurrence">

enum class RecurrenceFrequency {
    NONE, DAILY, WEEKLY, MONTHLY, YEARLY
}
data class RecurrenceRule(
    val daysOfWeek: List<Int>? = null,      // 1 = Mon - 7 = Sun
    val daysOfMonth: List<Int>? = null,     // 1 - 31
    val monthAndDay: Pair<Int, Int>? = null // DD-MM
)
//</editor-fold>

/* Generate EventOccurrences from MasterEvent */
@RequiresApi(Build.VERSION_CODES.O)
fun generateEventOccurrences(master: MasterEvent): List<EventOccurrence> {
    val today = LocalDate.now()
    val occurrences = mutableListOf<EventOccurrence>()

    // Calculate symmetric window
    val startLimit = today.minusMonths(generationMonths.toLong())
    val endLimit = master.endDate?.let {
        if (it.isBefore(today.plusMonths(generationMonths.toLong()))) it
        else today.plusMonths(generationMonths.toLong())
    } ?: today.plusMonths(generationMonths.toLong())

    // Start from the later of
    var current = if (master.startDate.isBefore(startLimit)) startLimit else master.startDate

    while (!current.isAfter(endLimit)) {
        val matchesRule = when (master.recurFreq) {
            RecurrenceFrequency.NONE -> current == master.startDate
            RecurrenceFrequency.DAILY -> true
            RecurrenceFrequency.WEEKLY -> master.recurRule.daysOfWeek?.contains(current.dayOfWeek.value) ?: true
            RecurrenceFrequency.MONTHLY -> master.recurRule.daysOfMonth?.contains(current.dayOfMonth) ?: true
            RecurrenceFrequency.YEARLY -> master.recurRule.monthAndDay?.let { it.first == current.dayOfMonth && it.second == current.monthValue } ?: true
        }

        if (matchesRule) {
            occurrences.add(
                EventOccurrence(
                    masterEventId = master.id,
                    notes = master.notes,
                    occurDate = current,
                    startTime = master.startTime,
                    endTime = master.endTime
                )
            )
        }

        // Increment current date based on frequency
        current = when (master.recurFreq) {
            RecurrenceFrequency.NONE,
            RecurrenceFrequency.DAILY,
            RecurrenceFrequency.WEEKLY,
            RecurrenceFrequency.MONTHLY -> current.plusDays(1)
            RecurrenceFrequency.YEARLY -> current.plusYears(1)
        }
    }

    return occurrences
}

/* Generate TaskBucketOccurrences from MasterTaskBucket, merging overlaps */
@RequiresApi(Build.VERSION_CODES.O)
suspend fun generateTaskBucketOccurrences(
    master: MasterTaskBucket,
    db: AppDatabase
): List<TaskBucketOccurrence> {
    val today = LocalDate.now()
    val newOccurrences = mutableListOf<TaskBucketOccurrence>()

    // Calculate symmetric window
    val startLimit = today.minusMonths(generationMonths.toLong())
    val endLimit = master.endDate?.let {
        if (it.isBefore(today.plusMonths(generationMonths.toLong()))) it
        else today.plusMonths(generationMonths.toLong())
    } ?: today.plusMonths(generationMonths.toLong())

    // Start from the later of
    var current = if (master.startDate.isBefore(startLimit)) startLimit else master.startDate

    while (!current.isAfter(endLimit)) {
        val matchesRule = when (master.recurFreq) {
            RecurrenceFrequency.NONE -> current == master.startDate
            RecurrenceFrequency.DAILY -> true
            RecurrenceFrequency.WEEKLY -> master.recurRule.daysOfWeek?.contains(current.dayOfWeek.value) ?: true
            RecurrenceFrequency.MONTHLY -> master.recurRule.daysOfMonth?.contains(current.dayOfMonth) ?: true
            RecurrenceFrequency.YEARLY -> master.recurRule.monthAndDay?.let { it.first == current.dayOfMonth && it.second == current.monthValue } ?: true
        }

        if (matchesRule) {
            // Create new occurrence
            val newOccurrence = TaskBucketOccurrence(
                masterBucketId = master.id,
                occurDate = current,
                startTime = master.startTime,
                endTime = master.endTime
            )

            // Get all existing occurrences on this date, excluding this master's
            val existingOccurrencesOnDate = db.taskBucketDao()
                .getOccurrencesByDate(current)
                .filter { it.masterBucketId != master.id }
                .toMutableList()

            // Find all overlapping occurrences, merge them
            val overlappingOccurrences = mutableListOf<TaskBucketOccurrence>()

            for (existing in existingOccurrencesOnDate) {
                if (doTimeRangesOverlapOrTouch(
                        newOccurrence.startTime, newOccurrence.endTime,
                        existing.startTime, existing.endTime
                    )) {
                    overlappingOccurrences.add(existing)
                }
            }

            // Merge overlapping occurrences into one
            if (overlappingOccurrences.isNotEmpty()) {
                var mergedStartTime = newOccurrence.startTime
                var mergedEndTime = newOccurrence.endTime

                for (existing in overlappingOccurrences) {
                    if (existing.startTime.isBefore(mergedStartTime)) {
                        mergedStartTime = existing.startTime
                    }
                    if (existing.endTime.isAfter(mergedEndTime)) {
                        mergedEndTime = existing.endTime
                    }
                }

                // Keep first overlapping occurrence, update with merged times
                val keepOccurrence = overlappingOccurrences.first()
                db.taskBucketDao().updateBucketOccurrence(
                    keepOccurrence.copy(
                        startTime = mergedStartTime,
                        endTime = mergedEndTime,
                        isException = true
                    )
                )

                // Delete all other overlapping occurrences
                for (i in 1 until overlappingOccurrences.size) {
                    db.taskBucketDao().deleteOccurrence(overlappingOccurrences[i].id)
                }

            } else {
                // No overlaps found, add as new occurrence
                newOccurrences.add(newOccurrence)
            }
        }

        // Increment current date based on frequency
        current = when (master.recurFreq) {
            RecurrenceFrequency.NONE,
            RecurrenceFrequency.DAILY,
            RecurrenceFrequency.WEEKLY,
            RecurrenceFrequency.MONTHLY -> current.plusDays(1)
            RecurrenceFrequency.YEARLY -> current.plusYears(1)
        }
    }

    return newOccurrences
}

/* Check if bucket time ranges overlap or touch */
@RequiresApi(Build.VERSION_CODES.O)
private fun doTimeRangesOverlapOrTouch(
    start1: LocalTime, end1: LocalTime,
    start2: LocalTime, end2: LocalTime
): Boolean {return !start1.isAfter(end2) && !end1.isBefore(start2)}

/* Generate ReminderOccurrences from MasterReminder */
@RequiresApi(Build.VERSION_CODES.O)
fun generateReminderOccurrences(master: MasterReminder): List<ReminderOccurrence> {
    val today = LocalDate.now()
    val occurrences = mutableListOf<ReminderOccurrence>()

    // Calculate symmetric window
    val startLimit = today.minusMonths(generationMonths.toLong())
    val endLimit = master.endDate?.let {
        if (it.isBefore(today.plusMonths(generationMonths.toLong()))) it
        else today.plusMonths(generationMonths.toLong())
    } ?: today.plusMonths(generationMonths.toLong())

    var current = if (master.startDate.isBefore(startLimit)) startLimit else master.startDate

    while (!current.isAfter(endLimit)) {
        val matchesRule = when (master.recurFreq) {
            RecurrenceFrequency.NONE -> current == master.startDate
            RecurrenceFrequency.DAILY -> true
            RecurrenceFrequency.WEEKLY -> master.recurRule.daysOfWeek?.contains(current.dayOfWeek.value) ?: true
            RecurrenceFrequency.MONTHLY -> master.recurRule.daysOfMonth?.contains(current.dayOfMonth) ?: true
            RecurrenceFrequency.YEARLY -> master.recurRule.monthAndDay?.let { it.first == current.dayOfMonth && it.second == current.monthValue } ?: true
        }

        if (matchesRule) {
            occurrences.add(
                ReminderOccurrence(
                    masterReminderId = master.id,
                    notes = master.notes,
                    occurDate = current,
                    time = master.time,
                    allDay = master.allDay
                )
            )
        }

        // Increment current date based on frequency
        current = when (master.recurFreq) {
            RecurrenceFrequency.NONE,
            RecurrenceFrequency.DAILY,
            RecurrenceFrequency.WEEKLY,
            RecurrenceFrequency.MONTHLY -> current.plusDays(1)
            RecurrenceFrequency.YEARLY -> current.plusYears(1)
        }
    }

    return occurrences
}

/* Trim past occurrences outside the window and extend future ones not yet generated */
@RequiresApi(Build.VERSION_CODES.O)
suspend fun trimAndExtendOccurrences(context: Context, db: AppDatabase) {
    val today = LocalDate.now()
    val startLimit = today.minusMonths(generationMonths.toLong())
    val endLimit = today.plusMonths(generationMonths.toLong())

    // --- Trim occurrences outside the window ---

    db.eventDao().getAllOccurrences()
        .filter { it.occurDate.isBefore(startLimit) || it.occurDate.isAfter(endLimit) }
        .forEach { db.eventDao().deleteOccurrence(it.id) }

    db.reminderDao().getAllOccurrences()
        .filter { it.occurDate.isBefore(startLimit) || it.occurDate.isAfter(endLimit) }
        .forEach { db.reminderDao().deleteOccurrence(it.id) }

    db.taskBucketDao().getAllBucketOccurrences()
        .filter { it.occurDate.isBefore(startLimit) || it.occurDate.isAfter(endLimit) }
        .forEach { db.taskBucketDao().deleteOccurrence(it.id) }

    db.taskDao().getAllIntervals()
        .filter { it.occurDate.isBefore(startLimit) || it.occurDate.isAfter(endLimit) }
        .forEach { db.taskDao().deleteInterval(it.id) }

    // --- Extend: generate only dates not yet covered ---

    // Events
    val allEventOccurrences = db.eventDao().getAllOccurrences()
    db.eventDao().getAllMasterEvents().forEach { event ->
        if (event.recurFreq == RecurrenceFrequency.NONE) return@forEach

        val existingDates = allEventOccurrences
            .filter { it.masterEventId == event.id }
            .map { it.occurDate }
            .toSet()

        val maxExisting = existingDates.maxOrNull()
        val generateFrom = if (maxExisting != null) maxExisting.plusDays(1) else startLimit

        if (!generateFrom.isAfter(endLimit)) {
            val extendedMaster = event.copy(startDate = maxOfDate(event.startDate, generateFrom))
            val newOccurrences = generateEventOccurrences(extendedMaster)
                .filter { it.occurDate !in existingDates }
            newOccurrences.forEach { db.eventDao().insertOccurrence(it) }
        }
    }

    // Reminders
    val allReminderOccurrences = db.reminderDao().getAllOccurrences()
    db.reminderDao().getAllMasterReminders().forEach { reminder ->
        if (reminder.recurFreq == RecurrenceFrequency.NONE) return@forEach

        val existingDates = allReminderOccurrences
            .filter { it.masterReminderId == reminder.id }
            .map { it.occurDate }
            .toSet()

        val maxExisting = existingDates.maxOrNull()
        val generateFrom = if (maxExisting != null) maxExisting.plusDays(1) else startLimit

        if (!generateFrom.isAfter(endLimit)) {
            val extendedReminder = reminder.copy(startDate = maxOfDate(reminder.startDate, generateFrom))
            val newOccurrences = generateReminderOccurrences(extendedReminder)
                .filter { it.occurDate !in existingDates }
            newOccurrences.forEach { db.reminderDao().insertOccurrence(it) }
        }
    }

    // Task Buckets
    val allBucketOccurrences = db.taskBucketDao().getAllBucketOccurrences()
    db.taskBucketDao().getAllMasterBuckets().forEach { bucket ->
        if (bucket.recurFreq == RecurrenceFrequency.NONE) return@forEach

        val existingDates = allBucketOccurrences
            .filter { it.masterBucketId == bucket.id }
            .map { it.occurDate }
            .toSet()

        val maxExisting = existingDates.maxOrNull()
        val generateFrom = if (maxExisting != null) maxExisting.plusDays(1) else startLimit

        if (!generateFrom.isAfter(endLimit)) {
            val extendedBucket = bucket.copy(startDate = maxOfDate(bucket.startDate, generateFrom))
            val newOccurrences = generateTaskBucketOccurrences(extendedBucket, db)
                .filter { it.occurDate !in existingDates }
            newOccurrences.forEach { db.taskBucketDao().insertOccurrence(it) }
        }
    }

    // Task intervals: only regenerate if there are none (scheduler handles the rest on create/update)
    if (db.taskDao().getAllIntervals().isEmpty()) {
        generateTaskIntervals(context, db)
    }
}

/* Helper: return the later of two LocalDates */
@RequiresApi(Build.VERSION_CODES.O)
private fun maxOfDate(a: LocalDate, b: LocalDate): LocalDate = if (a.isAfter(b)) a else b

/* Regenerate all occurrences */
@RequiresApi(Build.VERSION_CODES.O)
suspend fun regenerateAllOccurrences(context: Context, db: AppDatabase) {
    val today = LocalDate.now()
    val startLimit = today.minusMonths(generationMonths.toLong())
    val endLimit = today.plusMonths(generationMonths.toLong())

    // Delete occurrences outside window
    deleteOccurrencesOutsideWindow(db, startLimit, endLimit)

    // Regenerate all master events
    val allEvents = db.eventDao().getAllMasterEvents()
    allEvents.forEach { event ->
        // Delete old occurrences
        val oldOccurrences = db.eventDao().getAllOccurrences()
            .filter { it.masterEventId == event.id }
        oldOccurrences.forEach { db.eventDao().deleteOccurrence(it.id) }

        // Generate new occurrences
        val newOccurrences = generateEventOccurrences(event)
        newOccurrences.forEach { db.eventDao().insertOccurrence(it) }
    }

    // Regenerate all master reminders
    val allReminders = db.reminderDao().getAllMasterReminders()
    allReminders.forEach { reminder ->
        // Delete old occurrences
        val oldOccurrences = db.reminderDao().getAllOccurrences()
            .filter { it.masterReminderId == reminder.id }
        oldOccurrences.forEach { db.reminderDao().deleteOccurrence(it.id) }

        // Generate new occurrences
        val newOccurrences = generateReminderOccurrences(reminder)
        newOccurrences.forEach { db.reminderDao().insertOccurrence(it) }
    }

    // Regenerate all master task buckets
    val allBuckets = db.taskBucketDao().getAllMasterBuckets()
    allBuckets.forEach { bucket ->
        // Delete old occurrences
        val oldOccurrences = db.taskBucketDao().getAllBucketOccurrences()
            .filter { it.masterBucketId == bucket.id }
        oldOccurrences.forEach { db.taskBucketDao().deleteOccurrence(it.id) }

        // Generate new occurrences
        val newOccurrences = generateTaskBucketOccurrences(bucket, db)
        newOccurrences.forEach { db.taskBucketDao().insertOccurrence(it) }
    }

    // Regenerate task intervals
    generateTaskIntervals(context, db)
}

/* Delete occurrences outside the generation window */
@RequiresApi(Build.VERSION_CODES.O)
suspend fun deleteOccurrencesOutsideWindow(db: AppDatabase, startLimit: LocalDate, endLimit: LocalDate) {
    // Event occurrences
    db.eventDao().getAllOccurrences()
        .filter { it.occurDate.isBefore(startLimit) || it.occurDate.isAfter(endLimit) }
        .forEach { db.eventDao().deleteOccurrence(it.id) }

    // Reminder occurrences
    db.reminderDao().getAllOccurrences()
        .filter { it.occurDate.isBefore(startLimit) || it.occurDate.isAfter(endLimit) }
        .forEach { db.reminderDao().deleteOccurrence(it.id) }

    // Task bucket occurrences
    db.taskBucketDao().getAllBucketOccurrences()
        .filter { it.occurDate.isBefore(startLimit) || it.occurDate.isAfter(endLimit) }
        .forEach { db.taskBucketDao().deleteOccurrence(it.id) }

    // Task intervals
    db.taskDao().getAllIntervals()
        .filter { it.occurDate.isBefore(startLimit) || it.occurDate.isAfter(endLimit) }
        .forEach { db.taskDao().deleteInterval(it.id) }
}