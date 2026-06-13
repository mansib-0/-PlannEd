package com.planned

import android.annotation.SuppressLint
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.runBlocking
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/* DAY VIEW */
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun DayView(
    db: AppDatabase,
    displayedDate: LocalDate,
    pagerState: androidx.compose.foundation.pager.PagerState,
    initialScrollDone: Boolean,
    onScrollDone: () -> Unit,
    currentTime: LocalTime
) {
    val hours = (0..23).toList()

    // Auto scroll
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    val totalHeight = HourHeight.value * 24
    val yOffset = (currentTime.hour * 60 + currentTime.minute) * (totalHeight / 1440)

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val viewportHeightPx = with(density) { maxHeight.toPx() }
        val blueLinePositionPx = with(density) { (yOffset.dp + 8.dp).toPx() }

        LaunchedEffect(displayedDate, initialScrollDone, scrollState.maxValue) {
            if (displayedDate == LocalDate.now() && !initialScrollDone && scrollState.maxValue > 0) {
                val targetScroll = (blueLinePositionPx - viewportHeightPx / 2f)
                    .coerceIn(0f, scrollState.maxValue.toFloat())
                scrollState.scrollTo(targetScroll.toInt())
                onScrollDone()
            }
        }

        Column {
            Spacer(modifier = Modifier.height(10.dp))

            Row {
                // Hour labels
                Column(modifier = Modifier.verticalScroll(scrollState).padding(top = 10.dp)) {
                    hours.forEach { hour ->
                        val displayHour =
                            "${if (hour % 12 == 0) 12 else hour % 12} ${if (hour < 12) "AM" else "PM"}"
                        Box(
                            modifier = Modifier.width(60.dp).height(HourHeight)
                                .padding(start = 8.dp),
                            contentAlignment = Alignment.TopStart
                        ) {
                            Text(
                                displayHour,
                                fontSize = 14.sp,
                                color = Color.Black,
                                modifier = Modifier.offset(y = (-7).dp)
                            )
                        }
                    }
                }

                // Hour grid
                HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth()) { page ->
                    val pageDate = LocalDate.now().plusDays((page - INITIAL_PAGE).toLong())
                    Box(modifier = Modifier.verticalScroll(scrollState).fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(
                                start = 10.dp,
                                end = 10.dp,
                                top = 10.dp
                            )
                        ) {
                            hours.forEach { _ ->
                                Box(
                                    modifier = Modifier.height(HourHeight).fillMaxWidth()
                                        .border(0.5.dp, Color.LightGray)
                                )
                            }
                        }


                        // Render events, task buckets, and tasks
                        RenderDayViewItems(
                            db = db,
                            date = pageDate,
                            hourHeight = HourHeight,
                            modifier = Modifier.padding(top = 10.dp)
                        )

                        // Current time line (on top)
                        if (pageDate == LocalDate.now()) {
                            Box(
                                modifier = Modifier
                                    .offset(y = yOffset.dp + 10.dp)
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .padding(horizontal = 10.dp)
                                    .background(PrimaryColor)
                            )
                        }
                    }
                }
            }
        }
    }
}

/* WEEK VIEW */
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun WeekView(
    db: AppDatabase,
    displayedDate: LocalDate,
    pagerState: androidx.compose.foundation.pager.PagerState,
    initialScrollDone: Boolean,
    onScrollDone: () -> Unit,
    currentTime: LocalTime,
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit
) {
    val hours = (0..23).toList()
    val today = LocalDate.now()
    val scrollState = rememberScrollState()
    val density = LocalDensity.current

    // Header height
    var headerHeightPx by remember { mutableFloatStateOf(0f) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val totalHeight = HourHeight.value * 24
        val yOffset = (currentTime.hour * 60 + currentTime.minute) * (totalHeight / 1440)
        val viewportHeightPx = with(density) { maxHeight.toPx() }
        val linePositionPx = with(density) { yOffset.dp.toPx() }

        // Auto scroll
        LaunchedEffect(displayedDate, initialScrollDone, headerHeightPx, scrollState.maxValue) {
            if (!initialScrollDone && headerHeightPx > 0 && scrollState.maxValue > 0) {
                val startOfWeek = displayedDate.with(TemporalAdjusters.previousOrSame(getFirstDayOfWeek()))
                val endOfWeek = displayedDate.with(TemporalAdjusters.nextOrSame(getLastDayOfWeek()))
                if (!today.isBefore(startOfWeek) && !today.isAfter(endOfWeek)) {
                    val targetScroll = (linePositionPx - viewportHeightPx / 2f + headerHeightPx)
                        .coerceIn(0f, scrollState.maxValue.toFloat())
                    scrollState.scrollTo(targetScroll.toInt())
                    onScrollDone()
                }
            }
        }

        Column(modifier = Modifier.fillMaxSize()) {
            // Weekday headers
            val startOfWeek = displayedDate.with(TemporalAdjusters.previousOrSame(getFirstDayOfWeek()))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .onGloballyPositioned { coords -> headerHeightPx = coords.size.height.toFloat() }
            ) {
                Spacer(modifier = Modifier.width(60.dp))
                (0..6).forEach { i ->
                    val day = startOfWeek.plusDays(i.toLong())
                    val isToday = day == today
                    val isSelected = day == selectedDate

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onDateSelected(day) }
                    ) {
                        Text(
                            day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                            fontSize = 14.sp,
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                            color = if (isToday) PrimaryColor else Color.Black
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(
                                    if (isSelected) PrimaryColor else Color.Transparent,
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                day.dayOfMonth.toString(),
                                fontSize = 14.sp,
                                fontWeight = if (isSelected || (isToday)) FontWeight.Bold else FontWeight.Normal,
                                color = when {
                                    isSelected -> BackgroundColor
                                    isToday -> PrimaryColor
                                    else -> Color.Black
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(5.dp))

                        // Indicators row
                        val hasRemindersWeek = remember(day, showReminders, calendarResetTrigger) {
                            runBlocking { hasRemindersForDate(db, day) }
                        }
                        val hasDeadlinesWeek = remember(day, showDeadlines, calendarResetTrigger) {
                            runBlocking { hasDeadlinesForDate(db, day) }
                        }
                        val hasTasksWeek = remember(day, showTasks, calendarResetTrigger) {
                            runBlocking { hasTasksForDate(db, day) }
                        }

                        val weekIndicatorColor = PrimaryColor

                        Row(
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.height(6.dp)
                        ) {
                            if (hasTasksWeek) {
                                Canvas(modifier = Modifier.size(6.dp)) {
                                    drawCircle(color = weekIndicatorColor)
                                }
                                if (hasRemindersWeek || hasDeadlinesWeek) Spacer(modifier = Modifier.width(2.dp))
                            }
                            if (hasRemindersWeek) {
                                Canvas(modifier = Modifier.size(6.dp)) {
                                    val path = Path().apply {
                                        moveTo(size.width / 2, 0f)
                                        lineTo(size.width, size.height)
                                        lineTo(0f, size.height)
                                        close()
                                    }
                                    drawPath(path, color = weekIndicatorColor)
                                }
                                if (hasDeadlinesWeek) Spacer(modifier = Modifier.width(2.dp))
                            }
                            if (hasDeadlinesWeek) {
                                Canvas(modifier = Modifier.size(6.dp)) {
                                    drawRect(color = weekIndicatorColor)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Hour labels
            Row(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier.width(60.dp).verticalScroll(scrollState).padding(top = 10.dp)
                ) {
                    hours.forEach { hour ->
                        val displayHour = "${if (hour % 12 == 0) 12 else hour % 12} ${if (hour < 12) "AM" else "PM"}"
                        Box(
                            modifier = Modifier.height(HourHeight).fillMaxWidth().padding(start = 8.dp),
                            contentAlignment = Alignment.TopStart
                        ) {
                            Text(displayHour, fontSize = 14.sp, color = Color.Black, modifier = Modifier.offset(y = (-7).dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Hour grid
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxWidth().padding(end = 10.dp)
                ) { page ->
                    val pageDate = LocalDate.now().plusWeeks((page - INITIAL_PAGE).toLong())
                    val pageStartOfWeek = pageDate.with(TemporalAdjusters.previousOrSame(getFirstDayOfWeek()))
                    val todayIndex = ((today.dayOfWeek.value - getFirstDayOfWeek().value + 7) % 7)

                    Row(modifier = Modifier.verticalScroll(scrollState).fillMaxWidth().padding(top = 10.dp)) {
                        (0..6).forEachIndexed { index, _ ->
                            Box(modifier = Modifier.weight(1f)) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    hours.forEach { _ ->
                                        Box(
                                            modifier = Modifier.height(HourHeight).fillMaxWidth().border(0.5.dp, Color.LightGray)
                                        )
                                    }
                                }


                                // Render events, task buckets, and tasks
                                val dayDate = pageStartOfWeek.plusDays(index.toLong())
                                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                                    RenderWeekViewItems(
                                        db = db,
                                        date = dayDate,
                                        hourHeight = HourHeight,
                                        columnWidth = maxWidth
                                    )
                                }

                                // Current time line
                                Canvas(modifier = Modifier.matchParentSize()) {
                                    val minuteHeightPx = size.height / 1440f
                                    if (index == todayIndex && pageStartOfWeek.plusDays(index.toLong()) == today) {
                                        val lineY = (currentTime.hour * 60 + currentTime.minute) * minuteHeightPx
                                        drawLine(
                                            color = PrimaryColor,
                                            start = Offset(0f, lineY),
                                            end = Offset(size.width, lineY),
                                            strokeWidth = 4.dp.toPx()
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/* MONTH VIEW */
@RequiresApi(Build.VERSION_CODES.O)
@SuppressLint("UnusedContentLambdaTargetStateParameter")
@OptIn(ExperimentalAnimationApi::class, ExperimentalFoundationApi::class)
@Composable
fun MonthView(
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    pagerState: androidx.compose.foundation.pager.PagerState,
    db: AppDatabase
) {
    val today = LocalDate.now()
    val scrollState = rememberScrollState()

    // Load task intervals for selected date
    var taskIntervals by remember { mutableStateOf<List<TaskInterval>>(emptyList()) }
    var masterTasks by remember { mutableStateOf<List<MasterTask>>(emptyList()) }

    LaunchedEffect(selectedDate, calendarResetTrigger) {
        taskIntervals = db.taskDao().getAllIntervals()
            .filter { it.occurDate == selectedDate }
            .sortedBy { it.startTime }
        masterTasks = db.taskDao().getAllMasterTasks()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 10.dp)
    ) {

        // Weekday labels
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
            val daysOfWeek = (0..6).map { i ->
                DayOfWeek.of(((getFirstDayOfWeek().value - 1 + i) % 7 + 1))
            }
            daysOfWeek.forEach { day ->
                Box(modifier = Modifier.weight(1f).height(40.dp), contentAlignment = Alignment.Center) {
                    Text(day.getDisplayName(TextStyle.SHORT, Locale.getDefault()), fontSize = 14.sp)
                }
            }
        }

        // Month grid
        Box(modifier = Modifier.fillMaxWidth().height(360.dp)) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val pageDate = LocalDate.now().plusMonths((page - INITIAL_PAGE).toLong())
                val firstDayOfMonth = pageDate.withDayOfMonth(1)
                val daysToSubtract = (firstDayOfMonth.dayOfWeek.value - getFirstDayOfWeek().value + 7) % 7
                var currentDate = firstDayOfMonth.minusDays(daysToSubtract.toLong())

                val weeks = List(6) {
                    List(7) { currentDate.also { currentDate = currentDate.plusDays(1) } }
                }

                Column(modifier = Modifier.fillMaxSize()) {
                    HorizontalDivider(thickness = 1.dp, color = Color.LightGray)
                    weeks.forEach { week ->
                        Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                            week.forEach { date ->
                                val isCurrentMonth = date.month == pageDate.month
                                val isSelected = date == selectedDate
                                val isToday = date == today
                                val textColor = when {
                                    isSelected -> BackgroundColor
                                    isToday && !isSelected -> PrimaryColor
                                    !isCurrentMonth -> Color.Gray
                                    else -> Color.Black
                                }
                                val textWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) { onDateSelected(date) }
                                        .padding(5.dp),
                                    contentAlignment = Alignment.TopCenter
                                ) {
                                    // Load indicators for this date
                                    val hasReminders = remember(date, showReminders, calendarResetTrigger) {
                                        runBlocking { hasRemindersForDate(db, date) }
                                    }
                                    val hasDeadlines = remember(date, showDeadlines, calendarResetTrigger) {
                                        runBlocking { hasDeadlinesForDate(db, date) }
                                    }
                                    val hasTasks = remember(date, showTasks, calendarResetTrigger) {
                                        runBlocking { hasTasksForDate(db, date) }
                                    }

                                    val indicatorColor = if (!isCurrentMonth) Color.LightGray else PrimaryColor

                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .background(
                                                    if (isSelected) PrimaryColor else Color.Transparent,
                                                    shape = CircleShape
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                date.dayOfMonth.toString(),
                                                fontWeight = textWeight,
                                                color = textColor,
                                                fontSize = 14.sp
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(5.dp))

                                        // Indicators row
                                        Row(
                                            horizontalArrangement = Arrangement.Center,
                                            modifier = Modifier.height(6.dp)
                                        ) {
                                            if (hasTasks) {
                                                Canvas(modifier = Modifier.size(6.dp)) {
                                                    drawCircle(color = indicatorColor)
                                                }
                                                if (hasReminders || hasDeadlines) Spacer(modifier = Modifier.width(2.dp))
                                            }
                                            if (hasReminders) {
                                                Canvas(modifier = Modifier.size(6.dp)) {
                                                    val path = Path().apply {
                                                        moveTo(size.width / 2, 0f)
                                                        lineTo(size.width, size.height)
                                                        lineTo(0f, size.height)
                                                        close()
                                                    }
                                                    drawPath(path, color = indicatorColor)
                                                }
                                                if (hasDeadlines) Spacer(modifier = Modifier.width(2.dp))
                                            }
                                            if (hasDeadlines) {
                                                Canvas(modifier = Modifier.size(6.dp)) {
                                                    drawRect(color = indicatorColor)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        HorizontalDivider(thickness = 1.dp, color = Color.LightGray)
                    }
                }
            }
        }

        // Selected date
        Text(
            text = selectedDate.format(DateTimeFormatter.ofPattern("EEEE, MMM d")),
            modifier = Modifier.padding(top = 16.dp),
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = PrimaryColor
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Task Intervals for selected date
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp)
        ) {
            if (taskIntervals.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No tasks scheduled for this day",
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                }
            } else {
                taskIntervals.forEach { interval ->
                    val masterTask = masterTasks.find { it.id == interval.masterTaskId }

                    // Circle color
                    val circleColor: Color = remember(masterTask) {
                        runBlocking {
                            when {
                                masterTask?.eventId != null -> {
                                    val event = db.eventDao().getAllMasterEvents().find { it.id == masterTask.eventId }
                                    event?.color?.let { Converters.toColor(it) } ?: Color.LightGray
                                }
                                masterTask?.categoryId != null -> {
                                    val category = db.categoryDao().getAll().find { it.id == masterTask.categoryId }
                                    category?.color?.let { Converters.toColor(it) } ?: Color.LightGray
                                }
                                else -> Color.LightGray
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .background(Color(CardColor), RoundedCornerShape(8.dp))
                            .clickable {
                                masterTask?.let {
                                    selectedTaskForInfo = it
                                    currentScreen = "TaskInfo"
                                }
                            }
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(INNER_CIRCLE_SIZE)
                                        .background(circleColor, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    text = masterTask?.title ?: "Task",
                                    fontSize = 16.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "${interval.startTime.format(DateTimeFormatter.ofPattern("h:mm a"))} - ${interval.endTime.format(DateTimeFormatter.ofPattern("h:mm a"))}",
                                fontSize = 14.sp,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }
        }
    }
}