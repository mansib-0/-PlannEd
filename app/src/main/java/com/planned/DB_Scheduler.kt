package com.planned

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import java.time.LocalDate
import java.time.LocalTime

/**
Ordering Factors:
0) Dependency
1) Urgency (Deadline)
2) Category Score (ATI)
3) Event Score (ATI)
4) Date Created (Task ID)
 **/

/* Available time slots for task scheduling */
data class AvailableTimeSlot(
    val date: LocalDate,
    val startTime: LocalTime,
    val endTime: LocalTime
) {
    @RequiresApi(Build.VERSION_CODES.O)
    fun durationMinutes(): Int {
        val startMinutes = startTime.hour * 60 + startTime.minute
        val endMinutes = endTime.hour * 60 + endTime.minute
        return endMinutes - startMinutes
    }
}

/* Ordering auto-scheduled tasks */
data class OrderedTask(
    val masterTask: MasterTask,
    var remainingDuration: Int
)

/* Clear task intervals and regenerate */
@RequiresApi(Build.VERSION_CODES.O)
suspend fun generateTaskIntervals(context: Context, db: AppDatabase) {
    // Clear task intervals, reset noIntervals
    db.taskDao().getAllIntervals().forEach { interval ->
        db.taskDao().deleteInterval(interval.id)
    }
    val allMasterTasks = db.taskDao()
        .getAllMasterTasks()
        .filter { it.status != 3 && it.allDay == null }
    allMasterTasks.forEach { task ->
        db.taskDao().update(task.copy(noIntervals = 0))
    }

    // Set manually scheduled tasks
    val manualTasks = allMasterTasks.filter { it.startDate != null && it.startTime != null }
    val autoTasks = allMasterTasks.filter { it.startDate == null || it.startTime == null }

    processManuallyScheduledTasks(db, manualTasks)

    // Order auto-scheduled tasks by heuristic
    val orderedAutoTasks = orderAutoScheduledTasks(db, autoTasks)

    // Get available time slots from task buckets
    val availableSlots = getAvailableTimeSlots(db)

    // Assign auto-scheduled tasks into available slots
    assignAutoScheduledTasks(db, orderedAutoTasks, availableSlots)

    // Reschedule task notifications now that intervals are fresh
    NotificationScheduler.cancelAllTaskNotifications(context, db)
    NotificationScheduler.scheduleTaskNotifications(context, db)
}

/* Set manually scheduled tasks */
@RequiresApi(Build.VERSION_CODES.O)
private suspend fun processManuallyScheduledTasks(db: AppDatabase, manualTasks: List<MasterTask>) {
    for (task in manualTasks) {
        val startTime = task.startTime!!
        val startDate = task.startDate!!
        val durationMinutes = task.predictedDuration

        // Calculate end time
        val atiPaddingEnabled = SettingsManager.settings?.atiPaddingEnabled ?: true
        val atiPadding = if (atiPaddingEnabled) calculateTaskPadding(db, task) else 0

        val endTime = startTime.plusMinutes((durationMinutes + atiPadding).toLong())

        // Create single interval
        val interval = TaskInterval(
            masterTaskId = task.id,
            intervalNo = 1,
            notes = task.notes,
            occurDate = startDate,
            startTime = startTime,
            endTime = endTime,
            status = 1,
            timeLeft = durationMinutes,
            overTime = 0,
            atiPadding = atiPadding
        )

        db.taskDao().insertInterval(interval)

        // Update master task
        db.taskDao().update(task.copy(noIntervals = 1))
    }
}

/* Order auto-scheduled tasks */
@RequiresApi(Build.VERSION_CODES.O)
private suspend fun orderAutoScheduledTasks(db: AppDatabase, autoTasks: List<MasterTask>): MutableList<OrderedTask> {
    val today = java.time.LocalDateTime.now()
    val allDeadlines = db.deadlineDao().getAll()

    // Pre-fetch None ATI records once
    val noneCategoryScore = db.categoryATIDao().getById(0)?.score ?: 0f
    val noneEventScore    = db.eventATIDao().getById(0)?.score    ?: 0f

    return autoTasks.map { task ->
        val deadline = task.deadlineId?.let { deadlineId ->
            allDeadlines.find { it.id == deadlineId }
        }

        // Calculate urgency
        val urgency = deadline?.let {
            java.time.temporal.ChronoUnit.MINUTES.between(
                today,
                java.time.LocalDateTime.of(it.date, it.time)
            ).toInt()
        }

        // ATI scores — use None record (id=0) when category/event is null
        val categoryScore = task.categoryId
            ?.let { db.categoryATIDao().getById(it)?.score }
            ?: noneCategoryScore
        val eventScore = task.eventId
            ?.let { db.eventATIDao().getById(it)?.score }
            ?: noneEventScore

        Triple(task, urgency, Triple(categoryScore, eventScore, task.id))
    }.sortedWith(compareBy(
        { it.second ?: Int.MAX_VALUE },   // 1) Urgency ascending (no deadline = last)
        { -(it.third.first) },            // 2) Category score descending (higher = earlier)
        { -(it.third.second) },           // 3) Event score descending (higher = earlier)
        { it.third.third }                // 4) Task ID ascending (tie breaker)
    )).map {
        OrderedTask(it.first, it.first.predictedDuration)
    }.toMutableList().also { orderedList ->
        // Dependency chain ordering
        resolveDependencyChains(orderedList)
    }
}

/* Resolve dependency chains */
@RequiresApi(Build.VERSION_CODES.O)
fun resolveDependencyChains(orderedTasks: MutableList<OrderedTask>) {
    // Map task ID to index
    val taskIndexMap = mutableMapOf<Int, Int>()
    orderedTasks.forEachIndexed { index, orderedTask ->
        taskIndexMap[orderedTask.masterTask.id] = index
    }

    // Track tasks that have been moved to avoid infinite loops
    val movedTasks = mutableSetOf<Int>()

    // Iterate through tasks, check dependencies
    var i = 0
    while (i < orderedTasks.size) {
        val currentTask = orderedTasks[i]
        val dependencyId = currentTask.masterTask.dependencyTaskId

        if (dependencyId != null) {
            val dependencyIndex = taskIndexMap[dependencyId]

            // If dependency exists and is after current task
            if (dependencyIndex != null && dependencyIndex > i) {
                // Check if already moved this dependency
                if (!movedTasks.contains(dependencyId)) {
                    // Move dependency task to right before current task
                    val dependencyTask = orderedTasks.removeAt(dependencyIndex)
                    orderedTasks.add(i, dependencyTask)

                    // Update index map
                    taskIndexMap[dependencyId] = i
                    orderedTasks.forEachIndexed { idx, task ->
                        taskIndexMap[task.masterTask.id] = idx
                    }

                    // Mark this task as moved
                    movedTasks.add(dependencyId)

                    continue
                }
            }
        }

        i++
    }
}

/* Get all available time slots */
@RequiresApi(Build.VERSION_CODES.O)
private suspend fun getAvailableTimeSlots(db: AppDatabase): MutableList<AvailableTimeSlot> {
    // Get all task bucket occurrences - today and future only
    val today = LocalDate.now()
    val now = LocalTime.now()

    val bucketOccurrences = db.taskBucketDao().getAllBucketOccurrences()
        .filter { it.occurDate >= today }

    // Get all manually scheduled task intervals
    val manualIntervals = db.taskDao().getAllIntervals()

    // Get all event occurrences - today and future only
    val eventOccurrences = db.eventDao().getAllOccurrences()
        .filter { it.occurDate >= today }

    // Convert bucket occurrences to available slots
    val availableSlots = mutableListOf<AvailableTimeSlot>()

    for (bucket in bucketOccurrences) {
        // For today, clip start time to now if needed
        val effectiveStart = if (bucket.occurDate == today && bucket.startTime.isBefore(now)) {
            now
        } else {
            bucket.startTime
        }

        // Skip if the clipped slot is already over
        if (bucket.occurDate == today && !effectiveStart.isBefore(bucket.endTime)) continue

        // Start with the full bucket time range
        var currentSlots = mutableListOf(
            AvailableTimeSlot(
                date = bucket.occurDate,
                startTime = effectiveStart,
                endTime = bucket.endTime
            )
        )

        // Subtract all manual intervals
        val manualIntervalsOnDate = manualIntervals.filter { it.occurDate == bucket.occurDate }

        for (manualInterval in manualIntervalsOnDate) {
            val newSlots = mutableListOf<AvailableTimeSlot>()

            for (slot in currentSlots) {
                if (doTimeRangesOverlap(
                        slot.startTime, slot.endTime,
                        manualInterval.startTime, manualInterval.endTime
                    )) {
                    newSlots.addAll(subtractTimeRange(slot, manualInterval.startTime, manualInterval.endTime))
                } else {
                    newSlots.add(slot)
                }
            }

            currentSlots = newSlots
        }

        // Subtract all event occurrences
        val eventsOnDate = eventOccurrences.filter { it.occurDate == bucket.occurDate }

        for (event in eventsOnDate) {
            val newSlots = mutableListOf<AvailableTimeSlot>()

            for (slot in currentSlots) {
                if (doTimeRangesOverlap(
                        slot.startTime, slot.endTime,
                        event.startTime, event.endTime
                    )) {
                    newSlots.addAll(subtractTimeRange(slot, event.startTime, event.endTime))
                } else {
                    newSlots.add(slot)
                }
            }

            currentSlots = newSlots
        }

        availableSlots.addAll(currentSlots)
    }

    // Sort by date and time
    return availableSlots.sortedWith(compareBy({ it.date }, { it.startTime })).toMutableList()
}

/* Subtract time range from a slot, return remaining slots */
@RequiresApi(Build.VERSION_CODES.O)
private fun subtractTimeRange(
    slot: AvailableTimeSlot,
    subtractStart: LocalTime,
    subtractEnd: LocalTime
): List<AvailableTimeSlot> {
    val result = mutableListOf<AvailableTimeSlot>()

    // If the subtraction completely covers slot, return empty
    if (!subtractStart.isAfter(slot.startTime) && !subtractEnd.isBefore(slot.endTime)) {
        return result
    }

    // If there's a slot before the subtraction
    if (slot.startTime.isBefore(subtractStart) && subtractStart.isBefore(slot.endTime)) {
        result.add(
            AvailableTimeSlot(
                date = slot.date,
                startTime = slot.startTime,
                endTime = minOf(subtractStart, slot.endTime)
            )
        )
    }

    // If there's a slot after the subtraction
    if (slot.startTime.isBefore(subtractEnd) && subtractEnd.isBefore(slot.endTime)) {
        result.add(
            AvailableTimeSlot(
                date = slot.date,
                startTime = maxOf(subtractEnd, slot.startTime),
                endTime = slot.endTime
            )
        )
    }

    return result
}

/* Compare LocalTime */
@RequiresApi(Build.VERSION_CODES.O)
private fun minOf(time1: LocalTime, time2: LocalTime): LocalTime {
    return if (time1.isBefore(time2)) time1 else time2
}

/* Compare LocalTime */
@RequiresApi(Build.VERSION_CODES.O)
private fun maxOf(time1: LocalTime, time2: LocalTime): LocalTime {
    return if (time1.isAfter(time2)) time1 else time2
}

/* Assign auto-scheduled tasks into available slots based on heuristic order */
@RequiresApi(Build.VERSION_CODES.O)
private suspend fun assignAutoScheduledTasks(
    db: AppDatabase,
    orderedTasks: MutableList<OrderedTask>,
    availableSlots: MutableList<AvailableTimeSlot>
) {
    // Pre-fetch None ATI records once
    val noneCategoryATI = db.categoryATIDao().getById(0)
    val noneEventATI    = db.eventATIDao().getById(0)

    val todayDateTime = java.time.LocalDateTime.now()
    val allDeadlines = db.deadlineDao().getAll()
    val allPendingTasks = db.taskDao().getAllMasterTasks().filter { it.status != 3 }
    val delayedTasksCount = allPendingTasks.count { pt ->
        val deadline = pt.deadlineId?.let { id -> allDeadlines.find { it.id == id } }
        deadline != null && java.time.LocalDateTime.of(deadline.date, deadline.time).isBefore(todayDateTime)
    }
    val procrastinationPadding = delayedTasksCount * 3

    val tasksToProcess = orderedTasks.toMutableList()

    while (tasksToProcess.isNotEmpty() && availableSlots.isNotEmpty()) {
        val currentTask = tasksToProcess.first()
        val task = currentTask.masterTask
        val durationNeeded = currentTask.remainingDuration

        // Find slot that fits this task
        var assigned = false

        // Look up ATI padding once per task — event padding takes priority over category padding;
        val atiPaddingEnabled = SettingsManager.settings?.atiPaddingEnabled ?: true
        var atiPadding = if (atiPaddingEnabled) calculateTaskPadding(db, task) else 0
        atiPadding += procrastinationPadding

        for (i in availableSlots.indices) {
            val slot = availableSlots[i]
            val slotDuration = slot.durationMinutes()

            if (slotDuration >= durationNeeded) {
                // Slot fits the task — add as much padding as the slot allows
                val actualPadding = minOf(atiPadding, slotDuration - durationNeeded)
                val endTime = slot.startTime.plusMinutes((durationNeeded + actualPadding).toLong())

                // Get current interval count for this task
                val currentIntervals = db.taskDao().getIntervalsForTask(task.id)
                val intervalNo = currentIntervals.size + 1

                // Create interval
                val interval = TaskInterval(
                    masterTaskId = task.id,
                    intervalNo = intervalNo,
                    notes = task.notes,
                    occurDate = slot.date,
                    startTime = slot.startTime,
                    endTime = endTime,
                    status = 1,
                    timeLeft = durationNeeded,
                    overTime = 0,
                    atiPadding = actualPadding
                )

                db.taskDao().insertInterval(interval)
                db.taskDao().update(task.copy(noIntervals = intervalNo))

                // Update slot — consume task duration + padding
                val totalConsumed = durationNeeded + actualPadding
                if (slotDuration == totalConsumed) {
                    availableSlots.removeAt(i)
                } else {
                    availableSlots[i] = AvailableTimeSlot(
                        date = slot.date,
                        startTime = endTime,
                        endTime = slot.endTime
                    )
                }

                tasksToProcess.removeAt(0)
                assigned = true
                break

            } else if (task.breakable == true && slotDuration >= MIN_INTERVAL_SIZE) {
                // Partial interval — fill slot completely, no padding on partial chunks
                val endTime = slot.endTime

                val currentIntervals = db.taskDao().getIntervalsForTask(task.id)
                val intervalNo = currentIntervals.size + 1

                val interval = TaskInterval(
                    masterTaskId = task.id,
                    intervalNo = intervalNo,
                    notes = task.notes,
                    occurDate = slot.date,
                    startTime = slot.startTime,
                    endTime = endTime,
                    status = 1,
                    timeLeft = slotDuration,
                    overTime = 0,
                    atiPadding = 0
                )

                db.taskDao().insertInterval(interval)

                // Update master task noIntervals
                db.taskDao().update(task.copy(noIntervals = intervalNo))

                // Remove this slot (completely used)
                availableSlots.removeAt(i)

                // Update task's remaining duration
                currentTask.remainingDuration -= slotDuration

                // Task is partially assigned, continue to next slot
                assigned = true
                break
            }
        }

        if (!assigned) {
            // No suitable slot found, task remains unassigned, remove from list
            tasksToProcess.removeAt(0)
        }
    }
}

/* Helper to compute dynamic padding mathematically */
@RequiresApi(Build.VERSION_CODES.O)
suspend fun calculateTaskPadding(db: AppDatabase, task: MasterTask): Int {
    val basePadding = BaseModel.predictPadding(task.predictedDuration).toFloat()
    val dur = task.predictedDuration.toFloat()

    val eventATIObj = task.eventId?.let { db.eventATIDao().getById(it) } ?: db.eventATIDao().getById(0)
    val categoryATIObj = task.categoryId?.let { db.categoryATIDao().getById(it) } ?: db.categoryATIDao().getById(0)

    val paddingResultValue = if (eventATIObj != null) {
        val factor = (eventATIObj.tasksCompleted * 0.1f).coerceIn(0f, 1f)
        val regressionPadding = (eventATIObj.paddingSlope * dur + eventATIObj.paddingIntercept)
        var combined = (factor * regressionPadding) + (1f - factor) * basePadding
        if (eventATIObj.avgOvertime < 0) combined += eventATIObj.avgOvertime
        combined
    } else if (categoryATIObj != null) {
        val factor = (categoryATIObj.tasksCompleted * 0.1f).coerceIn(0f, 1f)
        val regressionPadding = (categoryATIObj.paddingSlope * dur + categoryATIObj.paddingIntercept)
        var combined = (factor * regressionPadding) + (1f - factor) * basePadding
        if (categoryATIObj.avgOvertime < 0) combined += categoryATIObj.avgOvertime
        combined
    } else {
        basePadding
    }

    return roundUpToNearest5(paddingResultValue).coerceAtMost(60).coerceAtLeast(0)
}