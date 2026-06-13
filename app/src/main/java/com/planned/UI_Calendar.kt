package com.planned

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

/* WEEK START LOGIC */
@RequiresApi(Build.VERSION_CODES.O)
fun getFirstDayOfWeek(): DayOfWeek {
    return if (startWeekOnMonday) DayOfWeek.MONDAY else DayOfWeek.SUNDAY
}
@RequiresApi(Build.VERSION_CODES.O)
fun getLastDayOfWeek(): DayOfWeek {
    return getFirstDayOfWeek().minus(1)
}

/* CALENDARS */
@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun Calendars(db: AppDatabase) {
    // Global states
    val currentView = currentCalendarView
    val today = LocalDate.now()
    var selectedDate by remember { mutableStateOf(today) }
    var selectedDateForWeek by remember { mutableStateOf(today) }
    val currentTime by produceState(initialValue = LocalTime.now()) {
        while (true) { value = LocalTime.now(); delay(60_000) }
    }
    var dayInitialScrollDone by remember { mutableStateOf(false) }
    var weekInitialScrollDone by remember { mutableStateOf(false) }
    val pagerState = rememberPagerState(initialPage = INITIAL_PAGE, pageCount = { TOTAL_PAGES })
    val coroutineScope = rememberCoroutineScope()
    val displayedDate by remember(pagerState.currentPage, currentView) {
        derivedStateOf {
            when (currentView) {
                "Day" -> today.plusDays((pagerState.currentPage - INITIAL_PAGE).toLong())
                "Week" -> today.plusWeeks((pagerState.currentPage - INITIAL_PAGE).toLong())
                "Month" -> today.plusMonths((pagerState.currentPage - INITIAL_PAGE).toLong())
                else -> today
            }
        }
    }

    // Date picker state
    var showDatePicker by remember { mutableStateOf(false) }

    // Reset scroll states
    LaunchedEffect(calendarResetTrigger) {
        dayInitialScrollDone = false
        weekInitialScrollDone = false
        selectedDate = today
        selectedDateForWeek = today
        pagerState.scrollToPage(INITIAL_PAGE)
    }

    LaunchedEffect(pagerState.currentPage, currentView) {
        if (currentView == "Week") {
            val startOfWeek = displayedDate.with(TemporalAdjusters.previousOrSame(getFirstDayOfWeek()))
            val endOfWeek = displayedDate.with(TemporalAdjusters.nextOrSame(getLastDayOfWeek()))
            if (selectedDateForWeek.isBefore(startOfWeek) || selectedDateForWeek.isAfter(endOfWeek)) {
                selectedDateForWeek = if (!today.isBefore(startOfWeek) && !today.isAfter(endOfWeek)) {
                    today
                } else {
                    startOfWeek
                }
            }
        }
    }

    Column(modifier = Modifier
        .fillMaxSize()
        .background(BackgroundColor)
    ) {
        // Date header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 15.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .background(Color(CardColor), RoundedCornerShape(12.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { showDatePicker = true }
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = when (currentView) {
                        "Day" -> displayedDate.format(DateTimeFormatter.ofPattern("EEEE, MMM d, yyyy"))
                        "Week" -> {
                            val startOfWeek = displayedDate.with(TemporalAdjusters.previousOrSame(getFirstDayOfWeek()))
                            val endOfWeek = displayedDate.with(TemporalAdjusters.nextOrSame(getLastDayOfWeek()))
                            val startMonth = startOfWeek.format(DateTimeFormatter.ofPattern("MMM"))
                            val endMonth = endOfWeek.format(DateTimeFormatter.ofPattern("MMM"))
                            if (startOfWeek.month == endOfWeek.month)
                                "$startMonth ${startOfWeek.dayOfMonth} - ${endOfWeek.dayOfMonth}, ${endOfWeek.year}"
                            else
                                "$startMonth ${startOfWeek.dayOfMonth} - $endMonth ${endOfWeek.dayOfMonth}, ${endOfWeek.year}"
                        }
                        "Month" -> displayedDate.format(DateTimeFormatter.ofPattern("MMMM, yyyy"))
                        else -> ""
                    },
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center
                )
            }
        }

        // Date picker dialog
        if (showDatePicker) {
            val datePickerState = rememberDatePickerState(
                initialSelectedDateMillis = when (currentView) {
                    "Day" -> displayedDate.toEpochDay() * 86400000L
                    "Week" -> selectedDateForWeek.toEpochDay() * 86400000L
                    "Month" -> selectedDate.toEpochDay() * 86400000L
                    else -> today.toEpochDay() * 86400000L
                }
            )
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(
                        onClick = {
                            datePickerState.selectedDateMillis?.let { millis ->
                                val selectedDateFromPicker = LocalDate.ofEpochDay(millis / 86400000L)

                                // Calculate offset and auto scroll
                                when (currentView) {
                                    "Day" -> {
                                        val dayOffset = java.time.temporal.ChronoUnit.DAYS.between(today, selectedDateFromPicker)
                                        coroutineScope.launch {
                                            pagerState.scrollToPage((INITIAL_PAGE + dayOffset).toInt())
                                        }
                                    }
                                    "Week" -> {
                                        val weekOffset = java.time.temporal.ChronoUnit.WEEKS.between(today, selectedDateFromPicker)
                                        selectedDateForWeek = selectedDateFromPicker
                                        coroutineScope.launch {
                                            pagerState.scrollToPage((INITIAL_PAGE + weekOffset).toInt())
                                        }
                                    }
                                    "Month" -> {
                                        val monthOffset = java.time.temporal.ChronoUnit.MONTHS.between(
                                            today.withDayOfMonth(1),
                                            selectedDateFromPicker.withDayOfMonth(1)
                                        )
                                        selectedDate = selectedDateFromPicker
                                        coroutineScope.launch {
                                            pagerState.scrollToPage((INITIAL_PAGE + monthOffset).toInt())
                                        }
                                    }
                                }
                            }
                            showDatePicker = false
                        }
                    ) {
                        Text("OK", color = Color.Black, fontSize = 16.sp)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDatePicker = false }) {
                        Text("Cancel", color = Color.Black, fontSize = 16.sp)
                    }
                },
                colors = DatePickerDefaults.colors(
                    containerColor = BackgroundColor
                )
            ) {
                DatePicker(
                    state = datePickerState,
                    showModeToggle = false,
                    title = null,
                    headline = null,
                    colors = DatePickerDefaults.colors(
                        containerColor = BackgroundColor,
                        selectedDayContainerColor = PrimaryColor,
                        todayDateBorderColor = PrimaryColor,
                        todayContentColor = PrimaryColor,
                        selectedYearContainerColor = PrimaryColor
                    )
                )
            }
        }

        // Reminder and Deadline indicators
        ReminderDeadlineIndicators(
            db = db,
            date = when (currentView) {
                "Day" -> displayedDate
                "Week" -> selectedDateForWeek
                "Month" -> selectedDate
                else -> today
            }
        )

        // Calendar views
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (currentView) {
                "Day" -> DayView(db, displayedDate, pagerState, dayInitialScrollDone, onScrollDone = { dayInitialScrollDone = true }, currentTime)
                "Week" -> WeekView(
                    db = db,
                    displayedDate = displayedDate,
                    pagerState = pagerState,
                    initialScrollDone = weekInitialScrollDone,
                    onScrollDone = { weekInitialScrollDone = true },
                    currentTime = currentTime,
                    selectedDate = selectedDateForWeek,
                    onDateSelected = { selectedDateForWeek = it }
                )
                "Month" -> MonthView(selectedDate, { selectedDate = it }, pagerState, db)
            }
        }

        // Navigation buttons
        Row(
            modifier = Modifier.fillMaxWidth().padding(all = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(PrimaryColor, shape = CircleShapePrimary)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous ${currentView.lowercase()}", tint = BackgroundColor) }

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(PrimaryColor, shape = CircleShapePrimary)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next ${currentView.lowercase()}", tint = BackgroundColor) }
        }
    }
}

/* REMINDER, DEADLINE, TO DO INDICATORS */
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun ReminderDeadlineIndicators(db: AppDatabase, date: LocalDate) {
    var reminders by remember { mutableStateOf<List<ReminderOccurrence>>(emptyList()) }
    var deadlines by remember { mutableStateOf<List<Deadline>>(emptyList()) }
    var todos by remember { mutableStateOf<List<MasterTask>>(emptyList()) }
    var showReminders by remember { mutableStateOf(false) }
    var showDeadlines by remember { mutableStateOf(false) }
    var showTodos by remember { mutableStateOf(false) }

    LaunchedEffect(date, calendarResetTrigger) {
        reminders = db.reminderDao().getAllOccurrences()
            .filter { it.occurDate == date }
            .sortedWith(compareBy({ !it.allDay }, { it.time }))
        deadlines = db.deadlineDao().getAll()
            .filter { it.date == date }
            .sortedBy { it.time }
        todos = db.taskDao().getAllMasterTasks()
            .filter { it.allDay == date && it.status != 3 }
            .sortedBy { it.title }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 15.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Indicator row
        Row(
            modifier = Modifier.wrapContentWidth().padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // To-Dos
            Box(
                modifier = Modifier
                    .background(BackgroundColor, RoundedCornerShape(12.dp))
                    .border(
                        width = 2.dp,
                        color = if (showTodos) PrimaryColor else Color.LightGray,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { showTodos = !showTodos }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(
                                if (todos.isNotEmpty()) PrimaryColor else Color.LightGray,
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = todos.size.toString(),
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = if (todos.isNotEmpty()) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "To Do", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }

            // Reminders
            Box(
                modifier = Modifier
                    .background(BackgroundColor, RoundedCornerShape(12.dp))
                    .border(
                        width = 2.dp,
                        color = if (showReminders) PrimaryColor else Color.LightGray,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { showReminders = !showReminders }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(
                                if (reminders.isNotEmpty()) PrimaryColor else Color.LightGray,
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = reminders.size.toString(),
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = if (reminders.isNotEmpty()) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Reminders", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }

            // Deadlines
            Box(
                modifier = Modifier
                    .background(BackgroundColor, RoundedCornerShape(12.dp))
                    .border(
                        width = 2.dp,
                        color = if (showDeadlines) PrimaryColor else Color.LightGray,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { showDeadlines = !showDeadlines }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(
                                if (deadlines.isNotEmpty()) PrimaryColor else Color.LightGray,
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = deadlines.size.toString(),
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = if (deadlines.isNotEmpty()) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Deadlines", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }
        }

        // Expanded To-Do list
        AnimatedVisibility(
            visible = showTodos,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                if (todos.isEmpty()) {
                    Text(
                        text = "No all-day tasks for this day",
                        fontSize = 14.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(8.dp)
                    )
                } else {
                    todos.forEach { todo ->
                        val todoColor = remember(todo) {
                            runBlocking {
                                when {
                                    todo.eventId != null -> {
                                        val event = db.eventDao().getAllMasterEvents().find { it.id == todo.eventId }
                                        event?.color?.let { Converters.toColor(it) }
                                            ?: event?.categoryId?.let { catId -> db.categoryDao().getAll().find { it.id == catId }?.color?.let { Converters.toColor(it) } }
                                            ?: todo.categoryId?.let { catId -> db.categoryDao().getAll().find { it.id == catId }?.color?.let { Converters.toColor(it) } }
                                            ?: Color.LightGray
                                    }
                                    todo.categoryId != null -> {
                                        val category = db.categoryDao().getAll().find { it.id == todo.categoryId }
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
                                    com.planned.selectedAllDayTaskForInfo = todo
                                    com.planned.currentScreen = "AllDayTaskInfo"
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
                                            .background(todoColor, CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Text(
                                        text = todo.title,
                                        fontSize = 16.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "All Day",
                                    fontSize = 14.sp,
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                }
            }
        }

        // Expanded reminder list
        AnimatedVisibility(
            visible = showReminders,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                if (reminders.isEmpty()) {
                    Text(
                        text = "No reminders for this day",
                        fontSize = 14.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(8.dp)
                    )
                } else {
                    reminders.forEach { reminder ->
                        val masterReminder = remember(reminder.masterReminderId) {
                            runBlocking { db.reminderDao().getAllMasterReminders().find { it.id == reminder.masterReminderId } }
                        }
                        val reminderColor = remember(masterReminder) {
                            runBlocking {
                                when {
                                    masterReminder?.categoryId != null -> {
                                        val category = db.categoryDao().getAll().find { it.id == masterReminder.categoryId }
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
                                    masterReminder?.let {
                                        com.planned.selectedReminderForInfo = it
                                        com.planned.selectedReminderOccurrenceForInfo = reminder
                                        com.planned.currentScreen = "ReminderInfo"
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
                                            .background(reminderColor, CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Text(
                                        text = masterReminder?.title ?: "Reminder",
                                        fontSize = 16.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = if (reminder.allDay) "All Day" else reminder.time?.format(DateTimeFormatter.ofPattern("h:mm a")) ?: "",
                                    fontSize = 14.sp,
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                }
            }
        }

        // Expanded deadline list
        AnimatedVisibility(
            visible = showDeadlines,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                if (deadlines.isEmpty()) {
                    Text(
                        text = "No deadlines for this day",
                        fontSize = 14.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(8.dp)
                    )
                } else {
                    deadlines.forEach { deadline ->
                        val deadlineColor = remember(deadline) {
                            runBlocking {
                                when {
                                    deadline.eventId != null -> {
                                        val event = db.eventDao().getAllMasterEvents().find { it.id == deadline.eventId }
                                        event?.color?.let { Converters.toColor(it) }
                                            ?: event?.categoryId?.let { catId -> db.categoryDao().getAll().find { it.id == catId }?.color?.let { Converters.toColor(it) } }
                                            ?: deadline.categoryId?.let { catId -> db.categoryDao().getAll().find { it.id == catId }?.color?.let { Converters.toColor(it) } }
                                            ?: Color.LightGray
                                    }
                                    deadline.categoryId != null -> {
                                        val category = db.categoryDao().getAll().find { it.id == deadline.categoryId }
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
                                    com.planned.selectedDeadlineForInfo = deadline
                                    com.planned.currentScreen = "DeadlineInfo"
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
                                            .background(deadlineColor, CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Text(
                                        text = deadline.title,
                                        fontSize = 16.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = deadline.time.format(DateTimeFormatter.ofPattern("h:mm a")),
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
}