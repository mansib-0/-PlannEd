package com.planned

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Timer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.runtime.getValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Check

/* VARIABLES */
//<editor-fold desc="Variables">
var currentScreen by mutableStateOf("Calendars")
var currentCalendarView by mutableStateOf("Month")
var calendarResetTrigger by mutableIntStateOf(0)

// Task
var selectedTaskForInfo by mutableStateOf<MasterTask?>(null)
var navUpdateFormData by mutableStateOf<UpdateFormData?>(null)
var taskInfoReturnScreen by mutableStateOf("Calendars")

// Event
var selectedEventForInfo by mutableStateOf<MasterEvent?>(null)
var navEventUpdateFormData by mutableStateOf<EventUpdateFormData?>(null)
var eventInfoReturnScreen by mutableStateOf("Calendars")
var selectedEventOccurrenceForInfo by mutableStateOf<EventOccurrence?>(null)

// Reminder
var selectedReminderForInfo by mutableStateOf<MasterReminder?>(null)
var navReminderUpdateFormData by mutableStateOf<ReminderUpdateFormData?>(null)
var selectedReminderOccurrenceForInfo by mutableStateOf<ReminderOccurrence?>(null)

// Deadline
var selectedDeadlineForInfo by mutableStateOf<Deadline?>(null)
var navDeadlineUpdateFormData by mutableStateOf<DeadlineUpdateFormData?>(null)
var deadlineInfoReturnScreen by mutableStateOf("Calendars")

// Task Bucket
var selectedBucketForInfo by mutableStateOf<MasterTaskBucket?>(null)
var selectedBucketOccurrenceForInfo by mutableStateOf<TaskBucketOccurrence?>(null)

// All-Day Task
var selectedAllDayTaskForInfo by mutableStateOf<MasterTask?>(null)

// Pomodoro
var pomodoroReturnScreen by mutableStateOf("Calendars")
// Saved state restored when returning from timer-icon-triggered pomodoro
var previousTaskForInfo by mutableStateOf<MasterTask?>(null)
var previousTaskInfoReturnScreen by mutableStateOf("Calendars")
var previousAllDayTaskForInfo by mutableStateOf<MasterTask?>(null)

// Internal Tasks screen state
var tasksCurrentView by mutableStateOf("main")
var tasksSelectedTask by mutableStateOf<MasterTask?>(null)
var tasksSelectedInterval by mutableStateOf<TaskInterval?>(null)
var tasksUpdateFormData by mutableStateOf<UpdateFormData?>(null)

// Internal Categories screen state
var categoriesCurrentView by mutableStateOf("list")
var categoriesSelectedCategory by mutableStateOf<Category?>(null)

// Internal Events screen state
var eventsCurrentView by mutableStateOf("list")
var eventsSelectedEvent by mutableStateOf<MasterEvent?>(null)
var eventsUpdateFormData by mutableStateOf<EventUpdateFormData?>(null)

// Internal Deadlines screen state
var deadlinesCurrentView by mutableStateOf("list")
var deadlinesSelectedDeadline by mutableStateOf<Deadline?>(null)
var deadlinesUpdateFormData by mutableStateOf<DeadlineUpdateFormData?>(null)

// Internal Reminders screen state
var remindersCurrentView by mutableStateOf("list")
var remindersSelectedReminder by mutableStateOf<MasterReminder?>(null)
var remindersUpdateFormData by mutableStateOf<ReminderUpdateFormData?>(null)

// Internal TaskBuckets screen state
var taskBucketsCurrentView by mutableStateOf("list")
var taskBucketsSelectedBucket by mutableStateOf<MasterTaskBucket?>(null)

// Internal Settings screen state
var settingsCurrentView by mutableStateOf("main")
//</editor-fold>

/* POMODORO STATE */
object PomodoroState {
    var activeTaskId by mutableStateOf<Int?>(null)
    var activeTaskTitle by mutableStateOf("")
    var isAllDay by mutableStateOf(false)
    var elapsedSeconds by mutableStateOf(0)
    var sessionSeconds by mutableStateOf(0)
    var isRunning by mutableStateOf(false)
    var breakEvery by mutableStateOf(30)
    var breakDuration by mutableStateOf(5)

    fun clear() {
        activeTaskId = null
        activeTaskTitle = ""
        isAllDay = false
        elapsedSeconds = 0
        sessionSeconds = 0
        isRunning = false
    }
}

/* APP NAVIGATION */
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun AppNavigation(db: AppDatabase) {
    var isDrawerOpen by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // Start/stop the foreground service in sync with PomodoroState.isRunning
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(PomodoroState.isRunning) {
        val intent = android.content.Intent(context, TimerForegroundService::class.java)
        if (PomodoroState.isRunning) {
            intent.action = TimerForegroundService.ACTION_START
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        } else {
            intent.action = TimerForegroundService.ACTION_STOP
            context.startService(intent)
        }
    }

    NavigationDrawer(
        isDrawerOpen = isDrawerOpen,
        onDrawerToggle = { isDrawerOpen = !isDrawerOpen }
    ) {
        Scaffold(
            containerColor = BackgroundColor,
            topBar = {
                Header(
                    db = db,
                    currentView = currentCalendarView,
                    onViewSelected = { view ->
                        currentCalendarView = view
                        currentScreen = "Calendars"
                        if (isDrawerOpen) isDrawerOpen = false
                    },
                    onMenuClick = { isDrawerOpen = !isDrawerOpen },
                    onCreateClick = {
                        currentScreen = "Creation"
                        if (isDrawerOpen) isDrawerOpen = false
                    }
                )
            },
            content = { padding ->
                Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                    when (currentScreen) {
                        "Calendars" -> Calendars(db)
                        "Categories" -> Categories(db)
                        "Events" -> Events(db)
                        "Deadlines" -> Deadlines(db)
                        "TaskBuckets" -> TaskBuckets(db)
                        "Tasks" -> Tasks(db)
                        "Reminders" -> Reminders(db)
                        "Settings" -> Settings(db)
                        "Creation" -> Creation(db)

                        // ── Task ──────────────────────────────────────────────────────────
                        "TaskInfo" -> selectedTaskForInfo?.let { task ->
                            TaskInfoPage(
                                db = db,
                                task = task,
                                onBack = {
                                    val returnTo = taskInfoReturnScreen
                                    taskInfoReturnScreen = "Calendars"
                                    navUpdateFormData = null
                                    selectedTaskForInfo = null
                                    currentScreen = returnTo
                                },
                                onUpdateDataReady = { data -> navUpdateFormData = data },
                                onUpdate = { currentScreen = "TaskUpdate" },
                                onPlay = {
                                    pomodoroReturnScreen = "TaskInfo"
                                    currentScreen = "TaskPomodoro"
                                }
                            )
                        }
                        "TaskUpdate" -> selectedTaskForInfo?.let { task ->
                            navUpdateFormData?.let { formData ->
                                TaskUpdateForm(
                                    db = db,
                                    task = task,
                                    preloadedData = formData,
                                    onBack = { currentScreen = "TaskInfo" },
                                    onSaveSuccess = { updatedTask ->
                                        selectedTaskForInfo = updatedTask
                                        navUpdateFormData = null
                                        currentScreen = "TaskInfo"
                                    }
                                )
                            }
                        }
                        "TaskPomodoro" -> selectedTaskForInfo?.let { task ->
                            PomodoroPage(
                                db = db,
                                task = task,
                                onBack = {
                                    val returnTo = pomodoroReturnScreen
                                    pomodoroReturnScreen = "Calendars"
                                    // Restore previous task only if timer icon was used
                                    // (previousTaskForInfo set), or if returning somewhere
                                    // other than TaskInfo (so selectedTaskForInfo isn't needed)
                                    if (previousTaskForInfo != null || returnTo != "TaskInfo") {
                                        selectedTaskForInfo = previousTaskForInfo
                                        taskInfoReturnScreen = previousTaskInfoReturnScreen
                                    }
                                    previousTaskForInfo = null
                                    previousTaskInfoReturnScreen = "Calendars"
                                    currentScreen = returnTo
                                },
                                onComplete = {
                                    PomodoroState.clear()
                                    val returnTo = taskInfoReturnScreen
                                    taskInfoReturnScreen = "Calendars"
                                    navUpdateFormData = null
                                    selectedTaskForInfo = null
                                    previousTaskForInfo = null
                                    previousTaskInfoReturnScreen = "Calendars"
                                    pomodoroReturnScreen = "Calendars"
                                    currentScreen = returnTo
                                }
                            )
                        }

                        // ── All-Day Task ──────────────────────────────────────────────────
                        "AllDayTaskInfo" -> selectedAllDayTaskForInfo?.let { task ->
                            AllDayTaskInfoPage(
                                db = db,
                                task = task,
                                onBack = {
                                    currentScreen = "Calendars"
                                    selectedAllDayTaskForInfo = null
                                },
                                onUpdate = { currentScreen = "AllDayTaskUpdate" },
                                onPlay = {
                                    pomodoroReturnScreen = "AllDayTaskInfo"
                                    currentScreen = "AllDayTaskPomodoro"
                                }
                            )
                        }
                        "AllDayTaskUpdate" -> selectedAllDayTaskForInfo?.let { task ->
                            AllDayTaskUpdateForm(
                                db = db,
                                task = task,
                                onBack = { currentScreen = "AllDayTaskInfo" },
                                onSaveSuccess = { updatedTask ->
                                    selectedAllDayTaskForInfo = updatedTask
                                    currentScreen = "AllDayTaskInfo"
                                }
                            )
                        }
                        "AllDayTaskPomodoro" -> selectedAllDayTaskForInfo?.let { task ->
                            AllDayPomodoroPage(
                                db = db,
                                task = task,
                                onBack = {
                                    val returnTo = pomodoroReturnScreen
                                    pomodoroReturnScreen = "Calendars"
                                    if (previousAllDayTaskForInfo != null || returnTo != "AllDayTaskInfo") {
                                        selectedAllDayTaskForInfo = previousAllDayTaskForInfo
                                    }
                                    previousAllDayTaskForInfo = null
                                    currentScreen = returnTo
                                },
                                onComplete = {
                                    PomodoroState.clear()
                                    selectedAllDayTaskForInfo = null
                                    previousAllDayTaskForInfo = null
                                    pomodoroReturnScreen = "Calendars"
                                    currentScreen = "Calendars"
                                }
                            )
                        }

                        // ── Event ─────────────────────────────────────────────────────────
                        "EventInfo" -> selectedEventForInfo?.let { event ->
                            EventInfoPage(
                                db = db,
                                event = event,
                                occurrence = selectedEventOccurrenceForInfo,
                                onBack = {
                                    val returnTo = eventInfoReturnScreen
                                    eventInfoReturnScreen = "Calendars"
                                    navEventUpdateFormData = null
                                    selectedEventForInfo = null
                                    selectedEventOccurrenceForInfo = null
                                    if (returnTo == "Events") {
                                        eventsCurrentView = "list"
                                        eventsSelectedEvent = null
                                    }
                                    currentScreen = returnTo
                                },
                                onUpdateDataReady = { data -> navEventUpdateFormData = data },
                                onUpdate = { currentScreen = "EventUpdate" },
                                eventReturnScreen = eventInfoReturnScreen
                            )
                        }
                        "EventUpdate" -> selectedEventForInfo?.let { event ->
                            navEventUpdateFormData?.let { formData ->
                                EventUpdateForm(
                                    db = db,
                                    event = event,
                                    preloadedData = formData,
                                    onBack = { currentScreen = "EventInfo" },
                                    onSaveSuccess = { updatedEvent ->
                                        selectedEventForInfo = updatedEvent
                                        navEventUpdateFormData = null
                                        currentScreen = "EventInfo"
                                    }
                                )
                            }
                        }

                        // ── Reminder ──────────────────────────────────────────────────────
                        "ReminderInfo" -> selectedReminderForInfo?.let { reminder ->
                            ReminderInfoView(
                                db = db,
                                reminder = reminder,
                                occurrence = selectedReminderOccurrenceForInfo,
                                onBack = {
                                    currentScreen = "Calendars"
                                    selectedReminderForInfo = null
                                    selectedReminderOccurrenceForInfo = null
                                    navReminderUpdateFormData = null
                                },
                                onUpdateDataReady = { data -> navReminderUpdateFormData = data },
                                onUpdate = { currentScreen = "ReminderUpdate" }
                            )
                        }
                        "ReminderUpdate" -> selectedReminderForInfo?.let { reminder ->
                            navReminderUpdateFormData?.let { formData ->
                                ReminderUpdateView(
                                    db = db,
                                    reminder = reminder,
                                    preloadedData = formData,
                                    onBack = { currentScreen = "ReminderInfo" },
                                    onSaveSuccess = { updatedReminder ->
                                        selectedReminderForInfo = updatedReminder
                                        navReminderUpdateFormData = null
                                        currentScreen = "ReminderInfo"
                                    }
                                )
                            }
                        }

                        // ── Deadline ──────────────────────────────────────────────────────
                        "DeadlineInfo" -> selectedDeadlineForInfo?.let { deadline ->
                            DeadlineInfoView(
                                db = db,
                                deadline = deadline,
                                onBack = {
                                    val returnTo = deadlineInfoReturnScreen
                                    deadlineInfoReturnScreen = "Calendars"
                                    navDeadlineUpdateFormData = null
                                    selectedDeadlineForInfo = null
                                    if (returnTo == "Deadlines") {
                                        deadlinesCurrentView = "list"
                                        deadlinesSelectedDeadline = null
                                    }
                                    currentScreen = returnTo
                                },
                                onUpdateDataReady = { data -> navDeadlineUpdateFormData = data },
                                onUpdate = { currentScreen = "DeadlineUpdate" },
                                deadlineReturnScreen = deadlineInfoReturnScreen
                            )
                        }
                        "DeadlineUpdate" -> selectedDeadlineForInfo?.let { deadline ->
                            navDeadlineUpdateFormData?.let { formData ->
                                DeadlineUpdateView(
                                    db = db,
                                    deadline = deadline,
                                    preloadedData = formData,
                                    onBack = { currentScreen = "DeadlineInfo" },
                                    onSaveSuccess = { updatedDeadline ->
                                        selectedDeadlineForInfo = updatedDeadline
                                        navDeadlineUpdateFormData = null
                                        currentScreen = "DeadlineInfo"
                                    }
                                )
                            }
                        }

                        // ── Task Bucket ───────────────────────────────────────────────────
                        "BucketInfo" -> selectedBucketForInfo?.let { bucket ->
                            TaskBucketInfoPage(
                                db = db,
                                bucket = bucket,
                                occurrence = selectedBucketOccurrenceForInfo,
                                onBack = {
                                    currentScreen = "Calendars"
                                    selectedBucketForInfo = null
                                    selectedBucketOccurrenceForInfo = null
                                },
                                onUpdate = { currentScreen = "BucketUpdate" }
                            )
                        }
                        "BucketUpdate" -> selectedBucketForInfo?.let { bucket ->
                            TaskBucketUpdateForm(
                                db = db,
                                bucket = bucket,
                                onBack = { currentScreen = "BucketInfo" },
                                onSaveSuccess = { updatedBucket ->
                                    selectedBucketForInfo = updatedBucket
                                    currentScreen = "BucketInfo"
                                }
                            )
                        }
                    }
                }
            }
        )
    }
}

/* HEADER */
@OptIn(ExperimentalMaterial3Api::class)
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun Header(
    db: AppDatabase,
    currentView: String,
    onViewSelected: (String) -> Unit,
    onMenuClick: () -> Unit,
    onCreateClick: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()

    // Pulse animation for the timer icon
    val infiniteTransition = rememberInfiniteTransition(label = "timerPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Column(modifier = Modifier.background(BackgroundColor)) {
        CenterAlignedTopAppBar(
            navigationIcon = {
                CompositionLocalProvider(LocalRippleConfiguration provides null) {
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Filled.Menu, contentDescription = "Menu", modifier = Modifier.size(32.dp))
                    }
                }
            },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CompositionLocalProvider(LocalRippleConfiguration provides null) {
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1.8f)) {
                            val options = listOf("Month", "Week", "Day")
                            options.forEachIndexed { index, view ->
                                SegmentedButton(
                                    selected = currentView == view && currentScreen == "Calendars",
                                    onClick = {
                                        currentScreen = "Calendars"
                                        currentCalendarView = view
                                        calendarResetTrigger++
                                        onViewSelected(view)
                                    },
                                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                                    icon = {},
                                    border = BorderStroke(0.dp, Color.Transparent),
                                    colors = SegmentedButtonDefaults.colors(
                                        activeContainerColor = PrimaryColor,
                                        activeContentColor = BackgroundColor,
                                        inactiveContainerColor = Color(CardColor),
                                        inactiveContentColor = Color.Black,
                                    ),
                                    interactionSource = remember { MutableInteractionSource() },
                                    label = {
                                        Text(
                                            view,
                                            fontSize = 14.sp,
                                            fontWeight = if (currentView == view && currentScreen == "Calendars") FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                )
                            }
                        }
                    }

                    // Pulsing timer icon — visible only when a task is running
                    AnimatedVisibility(
                        visible = PomodoroState.isRunning,
                        enter = expandHorizontally(),
                        exit = shrinkHorizontally()
                    ) {
                        Row {
                            Spacer(modifier = Modifier.width(8.dp))
                            CompositionLocalProvider(LocalRippleConfiguration provides null) {
                                IconButton(
                                    onClick = {
                                        val capturedScreen = currentScreen
                                        coroutineScope.launch {
                                            val taskId = PomodoroState.activeTaskId ?: return@launch
                                            val task = db.taskDao().getMasterTaskById(taskId) ?: return@launch
                                            pomodoroReturnScreen = capturedScreen
                                            if (PomodoroState.isAllDay) {
                                                previousAllDayTaskForInfo = selectedAllDayTaskForInfo
                                                selectedAllDayTaskForInfo = task
                                                currentScreen = "AllDayTaskPomodoro"
                                            } else {
                                                previousTaskForInfo = selectedTaskForInfo
                                                previousTaskInfoReturnScreen = taskInfoReturnScreen
                                                selectedTaskForInfo = task
                                                currentScreen = "TaskPomodoro"
                                            }
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Timer,
                                        contentDescription = "Running task",
                                        tint = PrimaryColor,
                                        modifier = Modifier
                                            .size(28.dp)
                                            .graphicsLayer { alpha = pulseAlpha }
                                    )
                                }
                            }
                        }
                    }

                    // ── Voice command mic button ──────────────────────────────────
                    VoiceMicButton(
                        db       = db,
                        modifier = Modifier.padding(end = 2.dp)
                    )

                    // Create (+) button
                    CompositionLocalProvider(LocalRippleConfiguration provides null) {
                        IconButton(onClick = onCreateClick) {
                            Icon(Icons.Filled.Add, contentDescription = "Create", modifier = Modifier.size(32.dp))
                        }
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundColor),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(10.dp))
        HorizontalDivider(thickness = 1.dp, color = Color.LightGray)
    }
}

/* NAVIGATION DRAWER */
@Composable
fun NavigationDrawer(
    isDrawerOpen: Boolean,
    onDrawerToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    val offsetX by animateDpAsState(
        targetValue = if (isDrawerOpen) 0.dp else -DrawerWidth,
        animationSpec = tween(durationMillis = AnimationDuration)
    )

    Box(modifier = Modifier.fillMaxSize()) {
        content()

        if (isDrawerOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 128.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onDrawerToggle() }
            )
        }

        Box(
            modifier = Modifier
                .offset(x = offsetX)
                .fillMaxHeight()
                .padding(top = 128.dp)
                .width(DrawerWidth)
                .background(BackgroundColor)
                .border(
                    width = 1.dp,
                    color = Color.LightGray,
                    shape = RoundedCornerShape(topEnd = 0.dp, bottomEnd = 0.dp)
                )
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {

                @Composable
                fun DrawerItem(label: String, onClick: () -> Unit) {
                    CompositionLocalProvider(LocalRippleConfiguration provides null) {
                        TextButton(
                            onClick = onClick,
                            modifier = Modifier.fillMaxWidth(),
                            interactionSource = remember { MutableInteractionSource() }
                        ) {
                            Text(
                                label,
                                color = Color.Black,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    HorizontalDivider(thickness = 1.dp, color = Color.LightGray)
                }

                @Composable
                fun CheckableBox(isChecked: Boolean, onToggle: () -> Unit) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .border(1.dp, Color.Black, RoundedCornerShape(4.dp))
                            .background(BackgroundColor)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onToggle() },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isChecked) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = "Checked",
                                tint = PrimaryColor,
                                modifier = Modifier
                                    .size(16.dp)
                                    .graphicsLayer {
                                        scaleX = 1.6f
                                        scaleY = 1.6f
                                    }
                            )
                        }
                    }
                }

                @Composable
                fun DrawerRow(
                    label: String,
                    icon: @Composable () -> Unit,
                    onClick: () -> Unit
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                            icon()
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        DrawerItem(label, onClick)
                    }
                }

                @Composable
                fun CheckableDrawerRow(
                    label: String,
                    isChecked: Boolean,
                    onCheckToggle: () -> Unit,
                    onClick: () -> Unit
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                            CheckableBox(isChecked = isChecked, onToggle = onCheckToggle)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        DrawerItem(label, onClick)
                    }
                }

                @Composable
                fun CalendarIcon() {
                    Icon(
                        Icons.Filled.CalendarToday,
                        contentDescription = "Calendar",
                        modifier = Modifier.size(24.dp),
                        tint = PrimaryColor
                    )
                }
                @Composable
                fun GearIcon() {
                    Icon(
                        Icons.Filled.Settings,
                        contentDescription = "Settings",
                        modifier = Modifier.size(24.dp),
                        tint = PrimaryColor
                    )
                }


                DrawerRow("Categories", { CalendarIcon() }) {
                    categoriesCurrentView = "list"
                    categoriesSelectedCategory = null
                    currentScreen = "Categories"
                    onDrawerToggle()
                }
                CheckableDrawerRow("Events", showEvents, {
                    showEvents = !showEvents
                }) {
                    eventsCurrentView = "list"
                    eventsSelectedEvent = null
                    eventsUpdateFormData = null
                    currentScreen = "Events"
                    onDrawerToggle()
                }
                CheckableDrawerRow("Deadlines", showDeadlines, {
                    showDeadlines = !showDeadlines
                }) {
                    deadlinesCurrentView = "list"
                    deadlinesSelectedDeadline = null
                    deadlinesUpdateFormData = null
                    currentScreen = "Deadlines"
                    onDrawerToggle()
                }
                CheckableDrawerRow("Reminders", showReminders, {
                    showReminders = !showReminders
                }) {
                    remindersCurrentView = "list"
                    remindersSelectedReminder = null
                    remindersUpdateFormData = null
                    currentScreen = "Reminders"
                    onDrawerToggle()
                }
                CheckableDrawerRow("Tasks", showTasks, {
                    showTasks = !showTasks
                }) {
                    tasksCurrentView = "main"
                    tasksSelectedTask = null
                    tasksSelectedInterval = null
                    tasksUpdateFormData = null
                    currentScreen = "Tasks"
                    onDrawerToggle()
                }
                CheckableDrawerRow("Task Buckets", showTaskBuckets, {
                    showTaskBuckets = !showTaskBuckets
                }) {
                    taskBucketsCurrentView = "list"
                    taskBucketsSelectedBucket = null
                    currentScreen = "TaskBuckets"
                    onDrawerToggle()
                }
                DrawerRow("Settings", { GearIcon() }) {
                    settingsCurrentView = "main"
                    currentScreen = "Settings"
                    onDrawerToggle()
                }
            }
        }
    }
}
