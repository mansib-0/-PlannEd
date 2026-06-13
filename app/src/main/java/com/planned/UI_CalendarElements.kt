package com.planned

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import androidx.compose.foundation.border

/* CALENDAR FILTERS */
var showEvents by mutableStateOf(true)
var showDeadlines by mutableStateOf(true)
var showTaskBuckets by mutableStateOf(true)
var showTasks by mutableStateOf(true)
var showReminders by mutableStateOf(true)

data class EventBlock(
    val occurrence: EventOccurrence,
    val master: MasterEvent,
    val color: Color,
    val stripeColor: Color,
    val title: String,
    val startTime: LocalTime,
    val endTime: LocalTime
)
data class TaskBucketBlock(
    val occurrence: TaskBucketOccurrence,
    val startTime: LocalTime,
    val endTime: LocalTime
)
data class TaskBlock(
    val interval: TaskInterval,
    val master: MasterTask,
    val color: Color,
    val title: String,
    val startTime: LocalTime,
    val endTime: LocalTime
)

/* FETCH DATA FOR GIVEN DATE */

@RequiresApi(Build.VERSION_CODES.O)
suspend fun getEventsForDate(db: AppDatabase, date: LocalDate): List<EventBlock> {
    if (!showEvents) return emptyList()

    val occurrences = db.eventDao().getAllOccurrences().filter { it.occurDate == date }
    val masters = db.eventDao().getAllMasterEvents()
    val categories = db.categoryDao().getAll()

    return occurrences.mapNotNull { occurrence ->
        val master = masters.find { it.id == occurrence.masterEventId } ?: return@mapNotNull null

        // Determine block fill color
        val color = when {
            master.color != null -> Converters.toColor(master.color)
            master.categoryId != null -> {
                categories.find { it.id == master.categoryId }?.color?.let { Converters.toColor(it) } ?: Preset1
            }
            else -> Preset1
        }

        // Determine stripe color: always category color, falls back to StripeColor if no category
        val stripeColor = (master.categoryId?.let { catId ->
            categories.find { it.id == catId }?.color?.let { Converters.toColor(it) }
        } ?: Preset1).let { base ->
            Color(red = base.red * 0.8f, green = base.green * 0.8f, blue = base.blue * 0.8f, alpha = base.alpha)
        }

        EventBlock(
            occurrence = occurrence,
            master = master,
            color = color,
            stripeColor = stripeColor,
            title = master.title,
            startTime = occurrence.startTime,
            endTime = occurrence.endTime
        )
    }
}

@RequiresApi(Build.VERSION_CODES.O)
suspend fun getTaskBucketsForDate(db: AppDatabase, date: LocalDate): List<TaskBucketBlock> {
    if (!showTaskBuckets) return emptyList()

    val occurrences = db.taskBucketDao().getOccurrencesByDate(date)

    return occurrences.map { occurrence ->
        TaskBucketBlock(
            occurrence = occurrence,
            startTime = occurrence.startTime,
            endTime = occurrence.endTime
        )
    }
}

@RequiresApi(Build.VERSION_CODES.O)
suspend fun getTasksForDate(db: AppDatabase, date: LocalDate): List<TaskBlock> {
    if (!showTasks) return emptyList()

    val intervals = db.taskDao().getAllIntervals().filter { it.occurDate == date }
    val masters = db.taskDao().getAllMasterTasks()
    val events = db.eventDao().getAllMasterEvents()
    val categories = db.categoryDao().getAll()

    return intervals.mapNotNull { interval ->
        val master = masters.find { it.id == interval.masterTaskId } ?: return@mapNotNull null

        // Determine fill color
        val color = when {
            master.eventId != null -> {
                val event = events.find { it.id == master.eventId }
                when {
                    event?.color != null -> Converters.toColor(event.color)
                    event?.categoryId != null -> {
                        categories.find { it.id == event.categoryId }?.color?.let { Converters.toColor(it) } ?: Preset1
                    }
                    else -> Color.LightGray
                }
            }
            master.categoryId != null -> {
                categories.find { it.id == master.categoryId }?.color?.let { Converters.toColor(it) } ?: Preset1
            }
            else -> Color.LightGray
        }

        val borderColor = color.let { base ->
            Color(red = base.red * 0.8f, green = base.green * 0.8f, blue = base.blue * 0.8f, alpha = base.alpha)
        }

        TaskBlock(
            interval = interval,
            master = master,
            color = borderColor,
            title = master.title,
            startTime = interval.startTime,
            endTime = interval.endTime
        )
    }
}

/* CALCULATE POSITION AND SIZE */

@RequiresApi(Build.VERSION_CODES.O)
fun calculateYOffset(startTime: LocalTime, hourHeight: Dp): Dp {
    val totalMinutes = startTime.hour * 60 + startTime.minute
    val totalHeightValue = hourHeight.value * 24
    return (totalMinutes * (totalHeightValue / 1440)).dp
}

@RequiresApi(Build.VERSION_CODES.O)
fun calculateHeight(startTime: LocalTime, endTime: LocalTime, hourHeight: Dp): Dp {
    val startMinutes = startTime.hour * 60 + startTime.minute
    val endMinutes = endTime.hour * 60 + endTime.minute
    val durationMinutes = endMinutes - startMinutes
    val totalHeightValue = hourHeight.value * 24
    return (durationMinutes * (totalHeightValue / 1440)).dp
}

/* RENDER ITEMS FOR DAY VIEW */

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun RenderDayViewItems(
    db: AppDatabase,
    date: LocalDate,
    hourHeight: Dp,
    modifier: Modifier = Modifier
) {
    val events = remember(date, showEvents, calendarResetTrigger) {
        runBlocking { getEventsForDate(db, date) }
    }

    val buckets = remember(date, showTaskBuckets, calendarResetTrigger) {
        runBlocking { getTaskBucketsForDate(db, date) }
    }

    val tasks = remember(date, showTasks, calendarResetTrigger) {
        runBlocking { getTasksForDate(db, date) }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Render task buckets
        buckets.forEach { bucket ->
            RenderTaskBucketBlock(
                db = db,
                bucket = bucket,
                hourHeight = hourHeight,
                modifier = Modifier.padding(horizontal = (10 + ELEMENT_HORIZONTAL_PADDING).dp)
            )
        }

        // Render events
        events.forEach { event ->
            RenderEventBlock(
                event = event,
                hourHeight = hourHeight,
                modifier = Modifier.padding(horizontal = (10 + ELEMENT_HORIZONTAL_PADDING).dp)
            )
        }

        // Render tasks
        tasks.forEach { task ->
            RenderTaskBlock(
                task = task,
                hourHeight = hourHeight,
                modifier = Modifier.padding(horizontal = (10 + ELEMENT_HORIZONTAL_PADDING).dp)
            )
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun RenderEventBlock(
    event: EventBlock,
    hourHeight: Dp,
    modifier: Modifier = Modifier
) {
    val yOffset = calculateYOffset(event.startTime, hourHeight)
    val height = calculateHeight(event.startTime, event.endTime, hourHeight)

    val timeFormatter = DateTimeFormatter.ofPattern("h:mm a")
    val timeText = "${event.startTime.format(timeFormatter)} - ${event.endTime.format(timeFormatter)}"

    // Check duration for text visibility
    val startMinutes = event.startTime.hour * 60 + event.startTime.minute
    val endMinutes = event.endTime.hour * 60 + event.endTime.minute
    val durationMinutes = endMinutes - startMinutes
    val showText = durationMinutes >= 30
    val showTime = durationMinutes >= 50

    Row(
        modifier = modifier
            .offset(y = yOffset)
            .height(height)
            .padding(bottom = ELEMENT_VERTICAL_PADDING.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(ELEMENT_CORNER_RADIUS_DAY.dp))
            .background(event.color)
            .clickable {
                com.planned.selectedEventForInfo = event.master
                com.planned.selectedEventOccurrenceForInfo = event.occurrence
                com.planned.currentScreen = "EventInfo"
            }
    ) {
        Box(
            modifier = Modifier
                .width(10.dp)
                .fillMaxHeight()
                .background(event.stripeColor)
        )
        Box(modifier = Modifier.padding(ELEMENT_TEXT_PADDING.dp)) {
            if (showText) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Top
                ) {
                    Text(
                        text = event.title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.DarkGray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (showTime) {
                        Text(
                            text = timeText,
                            fontSize = 16.sp,
                            color = Color.DarkGray,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun RenderTaskBucketBlock(
    db: AppDatabase,
    bucket: TaskBucketBlock,
    hourHeight: Dp,
    modifier: Modifier = Modifier
) {
    val yOffset = calculateYOffset(bucket.startTime, hourHeight)
    val height = calculateHeight(bucket.startTime, bucket.endTime, hourHeight)

    Box(
        modifier = modifier
            .offset(y = yOffset)
            .height(height)
            .padding(bottom = ELEMENT_VERTICAL_PADDING.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(ELEMENT_CORNER_RADIUS_DAY.dp))
            .background(Color(CardColor))
            .clickable {
                val occ = bucket.occurrence
                com.planned.selectedBucketOccurrenceForInfo = occ
                com.planned.selectedBucketForInfo = kotlinx.coroutines.runBlocking {
                    db.taskBucketDao().getMasterBucketById(occ.masterBucketId)
                }
                com.planned.currentScreen = "BucketInfo"
            }
    )
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun RenderTaskBlock(
    task: TaskBlock,
    hourHeight: Dp,
    modifier: Modifier = Modifier
) {
    val yOffset = calculateYOffset(task.startTime, hourHeight)
    val height = calculateHeight(task.startTime, task.endTime, hourHeight)

    val timeFormatter = DateTimeFormatter.ofPattern("h:mm a")
    val timeText = "${task.startTime.format(timeFormatter)} - ${task.endTime.format(timeFormatter)}"

    // Check duration for text visibility
    val startMinutes = task.startTime.hour * 60 + task.startTime.minute
    val endMinutes = task.endTime.hour * 60 + task.endTime.minute
    val durationMinutes = endMinutes - startMinutes
    val showText = durationMinutes >= 30
    val showTime = durationMinutes >= 50

    Box(
        modifier = modifier
            .offset(y = yOffset)
            .height(height)
            .padding(bottom = ELEMENT_VERTICAL_PADDING.dp)
            .fillMaxWidth()
            .padding(end = 20.dp)
            .border(4.dp, task.color, RoundedCornerShape(ELEMENT_CORNER_RADIUS_DAY.dp))
            .clip(RoundedCornerShape(ELEMENT_CORNER_RADIUS_DAY.dp))
            .background(BackgroundColor)
            .clickable {
                com.planned.selectedTaskForInfo = task.master
                com.planned.currentScreen = "TaskInfo"
            }
            .padding(vertical = ELEMENT_TEXT_PADDING.dp, horizontal = 10.dp)
    ) {
        if (showText) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Top
            ) {
                Text(
                    text = task.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.DarkGray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (showTime) {
                    Text(
                        text = timeText,
                        fontSize = 16.sp,
                        color = Color.DarkGray,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/* RENDER ITEMS FOR WEEK VIEW */

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun RenderWeekViewItems(
    db: AppDatabase,
    date: LocalDate,
    hourHeight: Dp,
    columnWidth: Dp,
    modifier: Modifier = Modifier
) {
    val events = remember(date, showEvents, calendarResetTrigger) {
        runBlocking { getEventsForDate(db, date) }
    }

    val buckets = remember(date, showTaskBuckets, calendarResetTrigger) {
        runBlocking { getTaskBucketsForDate(db, date) }
    }

    val tasks = remember(date, showTasks, calendarResetTrigger) {
        runBlocking { getTasksForDate(db, date) }
    }

    Box(
        modifier = modifier
            .width(columnWidth)
            .fillMaxHeight()
    ) {
        // Render task buckets
        buckets.forEach { bucket ->
            RenderTaskBucketBlockWeek(
                db = db,
                bucket = bucket,
                hourHeight = hourHeight
            )
        }

        // Render events
        events.forEach { event ->
            RenderEventBlockWeek(
                event = event,
                hourHeight = hourHeight
            )
        }

        // Render tasks
        tasks.forEach { task ->
            RenderTaskBlockWeek(
                task = task,
                hourHeight = hourHeight
            )
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun RenderEventBlockWeek(
    event: EventBlock,
    hourHeight: Dp,
    modifier: Modifier = Modifier
) {
    val yOffset = calculateYOffset(event.startTime, hourHeight)
    val height = calculateHeight(event.startTime, event.endTime, hourHeight)

    val startMinutes = event.startTime.hour * 60 + event.startTime.minute
    val endMinutes = event.endTime.hour * 60 + event.endTime.minute
    val durationMinutes = endMinutes - startMinutes
    val showWeekText = durationMinutes >= 20
    val showWeekTime = durationMinutes >= 30

    val timeFormatter = DateTimeFormatter.ofPattern("h:mm a")

    Row(
        modifier = modifier
            .offset(y = yOffset)
            .height(height)
            .padding(end = 4.dp, start = 0.dp, top = ELEMENT_VERTICAL_PADDING.dp, bottom = ELEMENT_VERTICAL_PADDING.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(ELEMENT_CORNER_RADIUS_WEEK.dp))
            .background(event.color)
            .clickable {
                com.planned.selectedEventForInfo = event.master
                com.planned.selectedEventOccurrenceForInfo = event.occurrence
                com.planned.currentScreen = "EventInfo"
            }
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(event.stripeColor)
        )
        Box(modifier = Modifier.padding(4.dp)) {
            if (showWeekText) {
                Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    Text(
                        text = event.title,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 12.sp,
                        color = Color.DarkGray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (showWeekTime) {
                        Text(
                            text = event.startTime.format(timeFormatter),
                            fontSize = 12.sp,
                            lineHeight = 12.sp,
                            color = Color.DarkGray,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun RenderTaskBucketBlockWeek(
    db: AppDatabase,
    bucket: TaskBucketBlock,
    hourHeight: Dp,
    modifier: Modifier = Modifier
) {
    val yOffset = calculateYOffset(bucket.startTime, hourHeight)
    val height = calculateHeight(bucket.startTime, bucket.endTime, hourHeight)

    Box(
        modifier = modifier
            .offset(y = yOffset)
            .height(height)
            .padding(end = 4.dp, start = 0.dp, top = ELEMENT_VERTICAL_PADDING.dp, bottom = ELEMENT_VERTICAL_PADDING.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(ELEMENT_CORNER_RADIUS_WEEK.dp))
            .background(Color(CardColor))
            .clickable {
                val occ = bucket.occurrence
                com.planned.selectedBucketOccurrenceForInfo = occ
                com.planned.selectedBucketForInfo = runBlocking {
                    db.taskBucketDao().getMasterBucketById(occ.masterBucketId)
                }
                com.planned.currentScreen = "BucketInfo"
            }
    )
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun RenderTaskBlockWeek(
    task: TaskBlock,
    hourHeight: Dp,
    modifier: Modifier = Modifier
) {
    val yOffset = calculateYOffset(task.startTime, hourHeight)
    val height = calculateHeight(task.startTime, task.endTime, hourHeight)

    val startMinutes = task.startTime.hour * 60 + task.startTime.minute
    val endMinutes = task.endTime.hour * 60 + task.endTime.minute
    val durationMinutes = endMinutes - startMinutes
    val showWeekText = durationMinutes >= 20
    val showWeekTime = durationMinutes >= 30

    val timeFormatter = DateTimeFormatter.ofPattern("h:mm a")

    Box(
        modifier = modifier
            .offset(y = yOffset)
            .height(height)
            .padding(end = 4.dp, start = 0.dp, top = ELEMENT_VERTICAL_PADDING.dp, bottom = ELEMENT_VERTICAL_PADDING.dp)
            .fillMaxWidth()
            .padding(end = 6.dp)
            .border(2.dp, task.color, RoundedCornerShape(ELEMENT_CORNER_RADIUS_WEEK.dp))
            .clip(RoundedCornerShape(ELEMENT_CORNER_RADIUS_WEEK.dp))
            .background(BackgroundColor)
            .clickable {
                com.planned.selectedTaskForInfo = task.master
                com.planned.currentScreen = "TaskInfo"
            }
            .padding(4.dp)
    ) {
        if (showWeekText) {
            Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                Text(
                    text = task.title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 12.sp,
                    color = Color.DarkGray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (showWeekTime) {
                    Text(
                        text = task.startTime.format(timeFormatter),
                        fontSize = 12.sp,
                        lineHeight = 12.sp,
                        color = Color.DarkGray,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/* MONTH VIEW INDICATORS */
@RequiresApi(Build.VERSION_CODES.O)
suspend fun hasRemindersForDate(db: AppDatabase, date: LocalDate): Boolean {
    if (!showReminders) return false
    val reminders = db.reminderDao().getAllOccurrences().filter { it.occurDate == date }
    return reminders.isNotEmpty()
}

@RequiresApi(Build.VERSION_CODES.O)
suspend fun hasDeadlinesForDate(db: AppDatabase, date: LocalDate): Boolean {
    if (!showDeadlines) return false
    val deadlines = db.deadlineDao().getAll().filter { it.date == date }
    return deadlines.isNotEmpty()
}

@RequiresApi(Build.VERSION_CODES.O)
suspend fun hasTasksForDate(db: AppDatabase, date: LocalDate): Boolean {
    if (!showTasks) return false
    val intervals = db.taskDao().getAllIntervals().filter { it.occurDate == date }
    if (intervals.isNotEmpty()) return true
    val allDayTasks = db.taskDao().getAllMasterTasks().filter { it.allDay == date && it.status != 3 }
    return allDayTasks.isNotEmpty()
}