package com.planned

/**
Adaptive Time Intelligence:
1) Dynamic Scoring — tracks deadline miss rate and avg overtime per category/event
2) Automatic Time Padding — linear regression predicts overtime from predictedDuration
 **/

// Scoring weights
private const val WEIGHT_DEADLINE_MISS = 0.6f
private const val WEIGHT_AVG_OVERTIME  = 0.4f
private const val ROLLING_WINDOW       = 10

/* Round a float number of minutes up to the nearest 5 */
fun roundUpToNearest5(minutes: Float): Int {
    if (minutes <= 0f) return 0
    val intMinutes = kotlin.math.ceil(minutes).toInt()
    val remainder = intMinutes % 5
    return if (remainder == 0) intMinutes else intMinutes + (5 - remainder)
}

/* Result of linear regression for padding prediction */
data class PaddingResult(val slope: Float, val intercept: Float, val predictedPadding: Int)

/* Linear regression: given a list of completed tasks, predict overtime for a new task
 * X = predictedDuration, Y = overTime
 * Returns slope, intercept, and predicted padding evaluated at avgX (rounded up to nearest 5, maximum 1 hour) */
fun calculatePadding(tasks: List<MasterTask>): PaddingResult {
    if (tasks.size < 2) return PaddingResult(0f, 0f, 0)

    val n = tasks.size.toFloat()
    val xs = tasks.map { it.predictedDuration.toFloat() }
    val ys = tasks.map { (it.overTime ?: 0).toFloat() }

    val sumX  = xs.sum()
    val sumY  = ys.sum()
    val sumXY = xs.zip(ys).sumOf { (x, y) -> (x * y).toDouble() }.toFloat()
    val sumX2 = xs.sumOf { (it * it).toDouble() }.toFloat()

    val denominator = n * sumX2 - sumX * sumX

    // All X values identical — slope undefined, use average Y instead
    if (denominator == 0f) {
        val avgY = sumY / n
        return PaddingResult(0f, avgY, roundUpToNearest5(avgY).coerceAtMost(60))
    }

    val slope     = (n * sumXY - sumX * sumY) / denominator
    val intercept = (sumY - slope * sumX) / n
    val avgX      = sumX / n
    val predicted = slope * avgX + intercept

    return PaddingResult(slope, intercept, roundUpToNearest5(predicted).coerceAtMost(60))
}

/* Weighted scoring formula.
 * deadlineMissCount: how many of last 10 completed tasks missed their deadline (0-10)
 * avgOvertime: average overtime in minutes across last 10 completed tasks
 * Returns a score float — higher means more struggle, needs earlier scheduling */
fun calculateScore(deadlineMissCount: Int, avgOvertime: Float): Float {
    val missRate    = deadlineMissCount / ROLLING_WINDOW.toFloat()
    val overtimeNorm = (avgOvertime / 180f).coerceIn(0f, 1f)
    return (WEIGHT_DEADLINE_MISS * missRate) + (WEIGHT_AVG_OVERTIME * overtimeNorm)
}

/* Main ATI update function — called every time a task is marked as complete.
 * Updates ATI records then prunes the completed task history for that event group.
 * Uses categoryId 0 for "None" category, eventId 0 for "None" event. */
suspend fun updateATIOnTaskComplete(db: AppDatabase, task: MasterTask) {
    val categoryTarget = task.categoryId ?: 0
    val eventTarget    = task.eventId    ?: 0
    updateCategoryATI(db, categoryTarget)
    updateEventATI(db, eventTarget)
    pruneCompletedTasks(db, eventTarget)
}

/* Prune completed tasks for the given event group.
 * Keeps only the last ROLLING_WINDOW completed non-allDay tasks per event group
 * (eventId 0 = None). Deletes any older ones beyond that window.
 * Called after ATI has already been updated, so the model is never affected. */
suspend fun pruneCompletedTasks(db: AppDatabase, eventId: Int) {
    val completed = db.taskDao().getAllMasterTasks()
        .filter {
            (if (eventId == 0) it.eventId == null else it.eventId == eventId)
                    && it.status == 3
                    && it.allDay == null
        }
        .sortedBy { it.completedAt }

    // Delete everything beyond the last ROLLING_WINDOW tasks
    val toDelete = completed.dropLast(ROLLING_WINDOW)
    toDelete.forEach { db.taskDao().deleteMasterTask(it.id) }
}

/* Update CategoryATI for a given category (0 = None) */
private suspend fun updateCategoryATI(db: AppDatabase, categoryId: Int) {
    val allTasks = db.taskDao().getAllMasterTasks()

    // For the None record (id=0), match tasks where categoryId IS null
    val completedTasks = allTasks
        .filter {
            (if (categoryId == 0) it.categoryId == null else it.categoryId == categoryId)
                    && it.status == 3 && it.allDay == null
        }
        .sortedBy { it.completedAt }
        .takeLast(ROLLING_WINDOW)

    if (completedTasks.isEmpty()) return

    val avgOvertime       = completedTasks.map { (it.overTime ?: 0).toFloat() }.average().toFloat()
    val deadlineMissCount = countDeadlineMisses(completedTasks)
    val paddingResult     = calculatePadding(completedTasks)
    val score             = calculateScore(deadlineMissCount, avgOvertime)
    val tasksCompleted    = allTasks.count {
        (if (categoryId == 0) it.categoryId == null else it.categoryId == categoryId)
                && it.status == 3 && it.allDay == null
    }

    val existing = db.categoryATIDao().getById(categoryId)
    if (existing != null) {
        db.categoryATIDao().update(existing.copy(
            score             = score,
            deadlineMissCount = deadlineMissCount,
            avgOvertime       = avgOvertime,
            tasksCompleted    = tasksCompleted,
            predictedPadding  = paddingResult.predictedPadding,
            paddingSlope      = paddingResult.slope,
            paddingIntercept  = paddingResult.intercept
        ))
    } else {
        db.categoryATIDao().insert(CategoryATI(
            categoryId        = categoryId,
            score             = score,
            deadlineMissCount = deadlineMissCount,
            avgOvertime       = avgOvertime,
            tasksCompleted    = tasksCompleted,
            predictedPadding  = paddingResult.predictedPadding,
            paddingSlope      = paddingResult.slope,
            paddingIntercept  = paddingResult.intercept
        ))
    }
}

/* Update EventATI for a given event (0 = None) */
private suspend fun updateEventATI(db: AppDatabase, eventId: Int) {
    val allTasks = db.taskDao().getAllMasterTasks()

    // For the None record (id=0), match tasks where eventId IS null
    val completedTasks = allTasks
        .filter {
            (if (eventId == 0) it.eventId == null else it.eventId == eventId)
                    && it.status == 3 && it.allDay == null
        }
        .sortedBy { it.completedAt }
        .takeLast(ROLLING_WINDOW)

    if (completedTasks.isEmpty()) return

    val avgOvertime       = completedTasks.map { (it.overTime ?: 0).toFloat() }.average().toFloat()
    val deadlineMissCount = countDeadlineMisses(completedTasks)
    val paddingResult     = calculatePadding(completedTasks)
    val score             = calculateScore(deadlineMissCount, avgOvertime)
    val tasksCompleted    = allTasks.count {
        (if (eventId == 0) it.eventId == null else it.eventId == eventId)
                && it.status == 3 && it.allDay == null
    }

    val existing = db.eventATIDao().getById(eventId)
    if (existing != null) {
        db.eventATIDao().update(existing.copy(
            score             = score,
            deadlineMissCount = deadlineMissCount,
            avgOvertime       = avgOvertime,
            tasksCompleted    = tasksCompleted,
            predictedPadding  = paddingResult.predictedPadding,
            paddingSlope      = paddingResult.slope,
            paddingIntercept  = paddingResult.intercept
        ))
    } else {
        db.eventATIDao().insert(EventATI(
            eventId           = eventId,
            score             = score,
            deadlineMissCount = deadlineMissCount,
            avgOvertime       = avgOvertime,
            tasksCompleted    = tasksCompleted,
            predictedPadding  = paddingResult.predictedPadding,
            paddingSlope      = paddingResult.slope,
            paddingIntercept  = paddingResult.intercept
        ))
    }
}

/* Count how many tasks in the window missed their deadline */
private fun countDeadlineMisses(tasks: List<MasterTask>): Int {
    return tasks.count { it.deadlineMissed }
}