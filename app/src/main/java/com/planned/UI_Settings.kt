package com.planned

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.ui.graphics.graphicsLayer
import androidx.core.graphics.withSave
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState

/* GLOBAL SETTINGS */
val startWeekOnMonday: Boolean
    get() = SettingsManager.settings?.startWeekOnMonday ?: false
val PrimaryColor: Color
    get() = SettingsManager.settings?.let {
        Converters.toColor(it.primaryColor)
    } ?: Preset19
val atiPaddingEnabled: Boolean
    get() = SettingsManager.settings?.atiPaddingEnabled ?: true
val colorPresets = listOf(
    Preset22, Preset13, Preset14, Preset15,
    Preset16, Preset17, Preset18, Preset19,
    Preset20, Preset21, Preset23, Preset24
)

// Occurrence generation months
var generationMonths by mutableIntStateOf(1)

data class ScheduleOrderRow(
    val masterTaskId: Int,
    val title: String,
    val dependencyTaskId: Int?,
    val urgency: Int?,
    val categoryScore: Float,
    val eventScore: Float
)

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun Settings(db: AppDatabase) {
    val context = LocalContext.current
    val db = remember { AppDatabaseProvider.getDatabase(context) }
    val scope = rememberCoroutineScope()

    // Sub-page navigation
    // Uses globally hoisted state so navigation away and back preserves subpage position

    // Shared DB state for sub-pages
    var categories by remember { mutableStateOf(listOf<Category>()) }
    var masterEvents by remember { mutableStateOf(listOf<MasterEvent>()) }
    var eventOccurrences by remember { mutableStateOf(listOf<EventOccurrence>()) }
    var deadlines by remember { mutableStateOf(listOf<Deadline>()) }
    var masterBuckets by remember { mutableStateOf(listOf<MasterTaskBucket>()) }
    var bucketOccurrences by remember { mutableStateOf(listOf<TaskBucketOccurrence>()) }
    var masterTasks by remember { mutableStateOf(listOf<MasterTask>()) }
    var taskIntervals by remember { mutableStateOf(listOf<TaskInterval>()) }
    var masterReminders by remember { mutableStateOf(listOf<MasterReminder>()) }
    var reminderOccurrences by remember { mutableStateOf(listOf<ReminderOccurrence>()) }
    var dbSettings by remember { mutableStateOf(listOf<AppSetting>()) }
    var categoryATIList by remember { mutableStateOf(listOf<CategoryATI>()) }
    var eventATIList by remember { mutableStateOf(listOf<EventATI>()) }
    var scheduleOrderRows by remember { mutableStateOf(listOf<ScheduleOrderRow>()) }

    fun refreshData() {
        CoroutineScope(Dispatchers.IO).launch {
            categories = db.categoryDao().getAll()
            masterEvents = db.eventDao().getAllMasterEvents()
            eventOccurrences = db.eventDao().getAllOccurrences()
            deadlines = db.deadlineDao().getAll()
            masterBuckets = db.taskBucketDao().getAllMasterBuckets()
            bucketOccurrences = db.taskBucketDao().getAllBucketOccurrences()
            masterTasks = db.taskDao().getAllMasterTasks()
            taskIntervals = db.taskDao().getAllIntervals()
            masterReminders = db.reminderDao().getAllMasterReminders()
            reminderOccurrences = db.reminderDao().getAllOccurrences()
            db.settingsDao().getAll()?.let { dbSettings = listOf(it) } ?: run { dbSettings = emptyList() }
            categoryATIList = db.categoryATIDao().getAll()
            eventATIList = db.eventATIDao().getAll()

            val today = java.time.LocalDateTime.now()
            val allDeadlines = db.deadlineDao().getAll()
            val autoTasks = db.taskDao().getAllMasterTasks().filter {
                it.status != 3 && it.allDay == null && it.startDate == null && it.startTime == null
            }

            // Pre-fetch None scores for schedule order display
            val noneCategoryScore = categoryATIList.find { it.categoryId == 0 }?.score ?: 0f
            val noneEventScore    = eventATIList.find    { it.eventId    == 0 }?.score ?: 0f

            val rows = mutableListOf<ScheduleOrderRow>()
            for (task in autoTasks) {
                val deadline      = task.deadlineId?.let { id -> allDeadlines.find { it.id == id } }
                val urgency = deadline?.let {
                    java.time.temporal.ChronoUnit.MINUTES.between(
                        today,
                        java.time.LocalDateTime.of(it.date, it.time)
                    ).toInt()
                }
                val categoryScore = task.categoryId
                    ?.let { id -> categoryATIList.find { it.categoryId == id }?.score }
                    ?: noneCategoryScore
                val eventScore    = task.eventId
                    ?.let { id -> eventATIList.find { it.eventId == id }?.score }
                    ?: noneEventScore
                rows.add(ScheduleOrderRow(
                    masterTaskId = task.id,
                    title = task.title,
                    dependencyTaskId = task.dependencyTaskId,
                    urgency = urgency,
                    categoryScore = categoryScore,
                    eventScore = eventScore
                ))
            }
            val sorted = rows.sortedWith(compareBy(
                { it.urgency ?: Int.MAX_VALUE },
                { -(it.categoryScore) },
                { -(it.eventScore) },
                { it.masterTaskId }
            )).toMutableList()

            val orderedForDep = sorted.map { row ->
                OrderedTask(
                    masterTask = MasterTask(
                        id = row.masterTaskId,
                        title = row.title,
                        dependencyTaskId = row.dependencyTaskId,
                        noIntervals = 0,
                        predictedDuration = 0
                    ),
                    remainingDuration = 0
                )
            }.toMutableList()

            resolveDependencyChains(orderedForDep)

            scheduleOrderRows = orderedForDep.map { ordered ->
                sorted.first { it.masterTaskId == ordered.masterTask.id }
            }
        }
    }
    LaunchedEffect(Unit) { refreshData() }

    when (settingsCurrentView) {
        "ati" -> {
            LaunchedEffect(Unit) { refreshData() }
            ATIPage(
                db = db,
                categories = categories,
                masterEvents = masterEvents,
                onBack = { settingsCurrentView = "main" }
            )
            return
        }
        "database" -> {
            DatabasePage(
                db = db,
                categories = categories,
                masterEvents = masterEvents,
                eventOccurrences = eventOccurrences,
                deadlines = deadlines,
                masterBuckets = masterBuckets,
                bucketOccurrences = bucketOccurrences,
                masterTasks = masterTasks,
                taskIntervals = taskIntervals,
                masterReminders = masterReminders,
                reminderOccurrences = reminderOccurrences,
                categoryATIList = categoryATIList,
                eventATIList = eventATIList,
                settings = dbSettings,
                onBack = { settingsCurrentView = "main" },
                onRefresh = { refreshData() }
            )
            return
        }
        "schedule" -> {
            SchedulePage(
                scheduleOrderRows = scheduleOrderRows,
                onBack = { settingsCurrentView = "main" }
            )
            return
        }
    }

    // Derived state from SettingsManager
    val settings = SettingsManager.settings

    var showColorPicker by remember { mutableStateOf(false) }

    // Local state for UI
    settings?.startWeekOnMonday ?: false
    val localAtiPadding = settings?.atiPaddingEnabled ?: true
    val localPrimary    = settings?.let { Converters.toColor(it.primaryColor) } ?: Preset19
    val scrollState     = androidx.compose.foundation.rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor)
            .verticalScroll(scrollState)
            .padding(12.dp)
    ) {
        // Refresh Schedule
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(CardColor), RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = true)
                ) {
                    scope.launch {
                        generateTaskIntervals(context, db)
                    }
                }
                .padding(16.dp)
        ) {
            Text("Refresh Schedule", fontSize = 16.sp, fontWeight = FontWeight.Medium)
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Calendar",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Gray,
            modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 12.dp)
        )

        // Week start switch
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(CardColor), RoundedCornerShape(12.dp))
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("First Day of Week", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.weight(1f))
                WeekButton(
                    label = "Sun",
                    selected = !startWeekOnMonday,
                    color = PrimaryColor
                ) {
                    scope.launch {
                        SettingsManager.setStartWeek(db, false)
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                WeekButton(
                    label = "Mon",
                    selected = startWeekOnMonday,
                    color = PrimaryColor
                ) {
                    scope.launch {
                        SettingsManager.setStartWeek(db, true)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Occurrence Window
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(CardColor), RoundedCornerShape(12.dp))
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Generate Months", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.weight(1f))
                Button(
                    onClick = { if (generationMonths > 1) generationMonths-- },
                    shape = androidx.compose.foundation.shape.CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryColor,
                        contentColor = BackgroundColor
                    ),
                    modifier = Modifier.size(40.dp),
                    contentPadding = PaddingValues(0.dp)
                ) { Text("-", fontSize = 20.sp) }
                Spacer(modifier = Modifier.width(12.dp))
                Box(
                    modifier = Modifier
                        .width(50.dp)
                        .background(BackgroundColor, RoundedCornerShape(8.dp))
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(generationMonths.toString(), fontSize = 18.sp)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Button(
                    onClick = { if (generationMonths < 6) generationMonths++ },
                    shape = androidx.compose.foundation.shape.CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryColor,
                        contentColor = BackgroundColor
                    ),
                    modifier = Modifier.size(40.dp),
                    contentPadding = PaddingValues(0.dp)
                ) { Text("+", fontSize = 20.sp) }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Timer",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Gray,
            modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 12.dp)
        )

        // Break Every, Break Duration
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(CardColor), RoundedCornerShape(12.dp))
                .padding(16.dp)
        ) {
            Column {
                // Break Every
                var breakEveryHours by remember { mutableIntStateOf((settings?.breakEvery ?: 25) / 60) }
                var breakEveryMinutes by remember { mutableIntStateOf((settings?.breakEvery ?: 25) % 60) }
                var showBreakEveryPicker by remember { mutableStateOf(false) }
                var tempBreakEveryH by remember { mutableIntStateOf(breakEveryHours) }
                var tempBreakEveryM by remember { mutableIntStateOf(breakEveryMinutes) }

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Break Every", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.weight(1f))
                    Button(
                        onClick = { tempBreakEveryH = breakEveryHours; tempBreakEveryM = breakEveryMinutes; showBreakEveryPicker = true },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor)
                    ) { Text("${breakEveryHours}h ${breakEveryMinutes}m") }
                }
                if (showBreakEveryPicker) {
                    AlertDialog(
                        onDismissRequest = { showBreakEveryPicker = false },
                        confirmButton = {
                            TextButton(onClick = {
                                breakEveryHours = tempBreakEveryH
                                breakEveryMinutes = tempBreakEveryM
                                showBreakEveryPicker = false
                                scope.launch { SettingsManager.setBreakEvery(db, tempBreakEveryH * 60 + tempBreakEveryM) }
                            }) { Text("OK", color = Color.Black, fontSize = 16.sp) }
                        },
                        dismissButton = {
                            TextButton(onClick = { showBreakEveryPicker = false }) { Text("Cancel", color = Color.Black, fontSize = 16.sp) }
                        },
                        containerColor = BackgroundColor,
                        text = {
                            Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Hours", fontSize = 12.sp)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(onClick = { tempBreakEveryH++ }, colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor)) { Text("▲") }
                                    Text(tempBreakEveryH.toString(), fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
                                    Button(onClick = { if (tempBreakEveryH > 0 && !(tempBreakEveryH == 1 && tempBreakEveryM == 0)) tempBreakEveryH-- }, colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor)) { Text("▼") }
                                }
                                Spacer(modifier = Modifier.width(32.dp))
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Minutes", fontSize = 12.sp)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(onClick = { val n = (tempBreakEveryM + 5) % 60; if (!(tempBreakEveryH == 0 && n == 0)) tempBreakEveryM = n }, colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor)) { Text("▲") }
                                    Text(tempBreakEveryM.toString(), fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
                                    Button(onClick = { val n = if (tempBreakEveryM - 5 < 0) 55 else tempBreakEveryM - 5; if (!(tempBreakEveryH == 0 && n == 0)) tempBreakEveryM = n }, colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor)) { Text("▼") }
                                }
                            }
                        }
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), thickness = 1.dp, color = Color.LightGray)

                // Break Duration
                var breakDurationHours by remember { mutableIntStateOf((settings?.breakDuration ?: 5) / 60) }
                var breakDurationMinutes by remember { mutableIntStateOf((settings?.breakDuration ?: 5) % 60) }
                var showBreakDurationPicker by remember { mutableStateOf(false) }
                var tempBreakDurH by remember { mutableIntStateOf(breakDurationHours) }
                var tempBreakDurM by remember { mutableIntStateOf(breakDurationMinutes) }

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Break Duration", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.weight(1f))
                    Button(
                        onClick = { tempBreakDurH = breakDurationHours; tempBreakDurM = breakDurationMinutes; showBreakDurationPicker = true },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor)
                    ) { Text("${breakDurationHours}h ${breakDurationMinutes}m") }
                }
                if (showBreakDurationPicker) {
                    AlertDialog(
                        onDismissRequest = { showBreakDurationPicker = false },
                        confirmButton = {
                            TextButton(onClick = {
                                breakDurationHours = tempBreakDurH
                                breakDurationMinutes = tempBreakDurM
                                showBreakDurationPicker = false
                                scope.launch { SettingsManager.setBreakDuration(db, tempBreakDurH * 60 + tempBreakDurM) }
                            }) { Text("OK", color = Color.Black, fontSize = 16.sp) }
                        },
                        dismissButton = {
                            TextButton(onClick = { showBreakDurationPicker = false }) { Text("Cancel", color = Color.Black, fontSize = 16.sp) }
                        },
                        containerColor = BackgroundColor,
                        text = {
                            Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Hours", fontSize = 12.sp)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(onClick = { tempBreakDurH++ }, colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor)) { Text("▲") }
                                    Text(tempBreakDurH.toString(), fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
                                    Button(onClick = { if (tempBreakDurH > 0 && !(tempBreakDurH == 1 && tempBreakDurM == 0)) tempBreakDurH-- }, colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor)) { Text("▼") }
                                }
                                Spacer(modifier = Modifier.width(32.dp))
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Minutes", fontSize = 12.sp)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(onClick = { val n = (tempBreakDurM + 5) % 60; if (!(tempBreakDurH == 0 && n == 0)) tempBreakDurM = n }, colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor)) { Text("▲") }
                                    Text(tempBreakDurM.toString(), fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
                                    Button(onClick = { val n = if (tempBreakDurM - 5 < 0) 55 else tempBreakDurM - 5; if (!(tempBreakDurH == 0 && n == 0)) tempBreakDurM = n }, colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor)) { Text("▼") }
                                }
                            }
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "App",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Gray,
            modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 12.dp)
        )

        // App accent
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(CardColor), RoundedCornerShape(12.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { showColorPicker = !showColorPicker }
                .padding(16.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Accent", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.weight(1f))
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(localPrimary)
                    )
                }

                // Color picker
                AnimatedVisibility(
                    visible = showColorPicker,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .height(182.dp)
                    ) {
                        items(colorPresets.size) { i ->
                            val c = colorPresets[i]
                            Box(
                                modifier = Modifier
                                    .padding(8.dp)
                                    .size(45.dp)
                                    .clip(CircleShape)
                                    .background(c)
                                    .clickable {
                                        scope.launch {
                                            SettingsManager.setPrimaryColor(db, c)
                                        }
                                        showColorPicker = false
                                    }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ATI padding switch
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(CardColor), RoundedCornerShape(12.dp))
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Predict Overtime", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.weight(1f))

                Switch(
                    checked = localAtiPadding,
                    onCheckedChange = {
                        scope.launch {
                            SettingsManager.setAtiPaddingEnabled(db, it)
                            generateTaskIntervals(context, db)
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = BackgroundColor,
                        checkedTrackColor = localPrimary,
                        uncheckedTrackColor = Color.LightGray,
                        uncheckedThumbColor = BackgroundColor
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Notifications",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Gray,
            modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 12.dp)
        )

        val notifTimeFormatter = java.time.format.DateTimeFormatter.ofPattern("h:mm a")

        // ── Tasks ────────────────────────────────────────────────────────────
        val tasksNotifEnabled = settings?.notifTasksEnabled ?: true
        var taskAllDayTime by remember {
            mutableStateOf(java.time.LocalTime.ofSecondOfDay((settings?.notifTaskAllDayTime ?: 25200).toLong()))
        }
        var showTaskTimePicker by remember { mutableStateOf(false) }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(CardColor), RoundedCornerShape(12.dp))
                .padding(16.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Tasks", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.weight(1f))
                    Switch(
                        checked = tasksNotifEnabled,
                        onCheckedChange = { scope.launch {
                            SettingsManager.setNotifTasksEnabled(db, it)
                            NotificationScheduler.cancelAllTaskNotifications(context, db)
                            if (it) NotificationScheduler.scheduleTaskNotifications(context, db)
                        } },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = BackgroundColor,
                            checkedTrackColor = localPrimary,
                            uncheckedTrackColor = Color.LightGray,
                            uncheckedThumbColor = BackgroundColor
                        )
                    )
                }
                AnimatedVisibility(
                    visible = tasksNotifEnabled,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), thickness = 1.dp, color = Color.LightGray)
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text("All-Day Tasks", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                            Spacer(modifier = Modifier.weight(1f))
                            Button(
                                onClick = { showTaskTimePicker = true },
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor)
                            ) { Text(taskAllDayTime.format(notifTimeFormatter)) }
                        }
                        if (showTaskTimePicker) {
                            val taskTimePickerState = rememberTimePickerState(
                                initialHour = taskAllDayTime.hour,
                                initialMinute = taskAllDayTime.minute,
                                is24Hour = false
                            )
                            AlertDialog(
                                onDismissRequest = { showTaskTimePicker = false },
                                confirmButton = {
                                    TextButton(onClick = {
                                        taskAllDayTime = java.time.LocalTime.of(taskTimePickerState.hour, taskTimePickerState.minute, 0, 0)
                                        showTaskTimePicker = false
                                        scope.launch {
                                            SettingsManager.setNotifTaskAllDayTime(db, taskAllDayTime.toSecondOfDay())
                                            NotificationScheduler.cancelAllTaskNotifications(context, db)
                                            NotificationScheduler.scheduleTaskNotifications(context, db)
                                        }
                                    }) { Text("OK", color = Color.Black, fontSize = 16.sp) }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showTaskTimePicker = false }) { Text("Cancel", color = Color.Black, fontSize = 16.sp) }
                                },
                                containerColor = BackgroundColor,
                                text = {
                                    TimePicker(
                                        state = taskTimePickerState,
                                        colors = TimePickerDefaults.colors(
                                            clockDialColor = BackgroundColor,
                                            selectorColor = PrimaryColor,
                                            containerColor = BackgroundColor,
                                            periodSelectorBorderColor = Color.LightGray,
                                            periodSelectorSelectedContainerColor = PrimaryColor,
                                            periodSelectorUnselectedContainerColor = BackgroundColor,
                                            periodSelectorSelectedContentColor = BackgroundColor,
                                            periodSelectorUnselectedContentColor = Color.Black,
                                            timeSelectorSelectedContainerColor = PrimaryColor,
                                            timeSelectorUnselectedContainerColor = BackgroundColor,
                                            timeSelectorSelectedContentColor = BackgroundColor,
                                            timeSelectorUnselectedContentColor = Color.Black
                                        )
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── Reminders ────────────────────────────────────────────────────────
        val remindersNotifEnabled = settings?.notifRemindersEnabled ?: true
        var reminderAllDayTime by remember {
            mutableStateOf(java.time.LocalTime.ofSecondOfDay((settings?.notifReminderAllDayTime ?: 25200).toLong()))
        }
        var showReminderTimePicker by remember { mutableStateOf(false) }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(CardColor), RoundedCornerShape(12.dp))
                .padding(16.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Reminders", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.weight(1f))
                    Switch(
                        checked = remindersNotifEnabled,
                        onCheckedChange = { scope.launch {
                            SettingsManager.setNotifRemindersEnabled(db, it)
                            NotificationScheduler.cancelAllReminderNotifications(context, db)
                            if (it) NotificationScheduler.scheduleReminderNotifications(context, db)
                        } },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = BackgroundColor,
                            checkedTrackColor = localPrimary,
                            uncheckedTrackColor = Color.LightGray,
                            uncheckedThumbColor = BackgroundColor
                        )
                    )
                }
                AnimatedVisibility(
                    visible = remindersNotifEnabled,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), thickness = 1.dp, color = Color.LightGray)
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text("All-Day Reminders", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                            Spacer(modifier = Modifier.weight(1f))
                            Button(
                                onClick = { showReminderTimePicker = true },
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor)
                            ) { Text(reminderAllDayTime.format(notifTimeFormatter)) }
                        }
                        if (showReminderTimePicker) {
                            val reminderTimePickerState = rememberTimePickerState(
                                initialHour = reminderAllDayTime.hour,
                                initialMinute = reminderAllDayTime.minute,
                                is24Hour = false
                            )
                            AlertDialog(
                                onDismissRequest = { showReminderTimePicker = false },
                                confirmButton = {
                                    TextButton(onClick = {
                                        reminderAllDayTime = java.time.LocalTime.of(reminderTimePickerState.hour, reminderTimePickerState.minute, 0, 0)
                                        showReminderTimePicker = false
                                        scope.launch {
                                            SettingsManager.setNotifReminderAllDayTime(db, reminderAllDayTime.toSecondOfDay())
                                            NotificationScheduler.cancelAllReminderNotifications(context, db)
                                            NotificationScheduler.scheduleReminderNotifications(context, db)
                                        }
                                    }) { Text("OK", color = Color.Black, fontSize = 16.sp) }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showReminderTimePicker = false }) { Text("Cancel", color = Color.Black, fontSize = 16.sp) }
                                },
                                containerColor = BackgroundColor,
                                text = {
                                    TimePicker(
                                        state = reminderTimePickerState,
                                        colors = TimePickerDefaults.colors(
                                            clockDialColor = BackgroundColor,
                                            selectorColor = PrimaryColor,
                                            containerColor = BackgroundColor,
                                            periodSelectorBorderColor = Color.LightGray,
                                            periodSelectorSelectedContainerColor = PrimaryColor,
                                            periodSelectorUnselectedContainerColor = BackgroundColor,
                                            periodSelectorSelectedContentColor = BackgroundColor,
                                            periodSelectorUnselectedContentColor = Color.Black,
                                            timeSelectorSelectedContainerColor = PrimaryColor,
                                            timeSelectorUnselectedContainerColor = BackgroundColor,
                                            timeSelectorSelectedContentColor = BackgroundColor,
                                            timeSelectorUnselectedContentColor = Color.Black
                                        )
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── Deadlines ────────────────────────────────────────────────────────
        val deadlinesNotifEnabled = settings?.notifDeadlinesEnabled ?: true
        val deadlineTimingOptions = listOf("Time Of", "Day Of", "Day Before")
        var deadlineTimingIndex by remember {
            mutableIntStateOf(when (settings?.notifDeadlineTiming) {
                "DAY_OF" -> 1; "DAY_BEFORE" -> 2; else -> 0
            })
        }
        var deadlineNotifTime by remember {
            mutableStateOf(java.time.LocalTime.ofSecondOfDay((settings?.notifDeadlineTime ?: 25200).toLong()))
        }
        var showDeadlineTimePicker by remember { mutableStateOf(false) }
        var showDeadlineDropdown by remember { mutableStateOf(false) }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(CardColor), RoundedCornerShape(12.dp))
                .padding(16.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Deadlines", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.weight(1f))
                    Switch(
                        checked = deadlinesNotifEnabled,
                        onCheckedChange = { scope.launch {
                            SettingsManager.setNotifDeadlinesEnabled(db, it)
                            NotificationScheduler.cancelAllDeadlineNotifications(context, db)
                            if (it) NotificationScheduler.scheduleDeadlineNotifications(context, db)
                        } },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = BackgroundColor,
                            checkedTrackColor = localPrimary,
                            uncheckedTrackColor = Color.LightGray,
                            uncheckedThumbColor = BackgroundColor
                        )
                    )
                }
                AnimatedVisibility(
                    visible = deadlinesNotifEnabled,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), thickness = 1.dp, color = Color.LightGray)

                        // Timing dropdown — same style as dropdownField() in creation
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(BackgroundColor, RoundedCornerShape(8.dp))
                                .border(1.dp, Color.LightGray, RoundedCornerShape(8.dp))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { showDeadlineDropdown = !showDeadlineDropdown }
                                .padding(12.dp)
                        ) {
                            Text(deadlineTimingOptions[deadlineTimingIndex], fontSize = 16.sp, color = Color.Black)
                        }
                        AnimatedVisibility(
                            visible = showDeadlineDropdown,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(top = 8.dp)
                                    .heightIn(max = 180.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                deadlineTimingOptions.forEachIndexed { index, label ->
                                    val isSelected = deadlineTimingIndex == index
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                            .background(
                                                if (isSelected) PrimaryColor else Color.LightGray,
                                                RoundedCornerShape(8.dp)
                                            )
                                            .clickable {
                                                deadlineTimingIndex = index
                                                showDeadlineDropdown = false
                                                val timing = when (index) { 1 -> "DAY_OF"; 2 -> "DAY_BEFORE"; else -> "TIME_OF" }
                                                scope.launch {
                                                    SettingsManager.setNotifDeadlineTiming(db, timing)
                                                    NotificationScheduler.cancelAllDeadlineNotifications(context, db)
                                                    NotificationScheduler.scheduleDeadlineNotifications(context, db)
                                                }
                                            }
                                            .padding(12.dp)
                                    ) {
                                        Text(
                                            label,
                                            fontSize = 16.sp,
                                            color = if (isSelected) BackgroundColor else Color.Black,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                }
                            }
                        }

                        // Time picker — only for Day of / Day before
                        // Duration picker — only for Time of
                        AnimatedVisibility(
                            visible = deadlineTimingIndex == 0,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Column {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), thickness = 1.dp, color = Color.LightGray)
                                var deadlineLeadH by remember { mutableIntStateOf((settings?.notifDeadlineLeadMinutes ?: 0) / 60) }
                                var deadlineLeadM by remember { mutableIntStateOf((settings?.notifDeadlineLeadMinutes ?: 0) % 60) }
                                var tempDeadlineLeadH by remember { mutableIntStateOf(deadlineLeadH) }
                                var tempDeadlineLeadM by remember { mutableIntStateOf(deadlineLeadM) }
                                var showDeadlineLeadPicker by remember { mutableStateOf(false) }
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                    Text("Notify Before", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                                    Spacer(modifier = Modifier.weight(1f))
                                    Button(
                                        onClick = {
                                            tempDeadlineLeadH = deadlineLeadH
                                            tempDeadlineLeadM = deadlineLeadM
                                            showDeadlineLeadPicker = true
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor)
                                    ) { Text("${deadlineLeadH}h ${deadlineLeadM}m") }
                                }
                                if (showDeadlineLeadPicker) {
                                    AlertDialog(
                                        onDismissRequest = { showDeadlineLeadPicker = false },
                                        confirmButton = {
                                            TextButton(onClick = {
                                                deadlineLeadH = tempDeadlineLeadH
                                                deadlineLeadM = tempDeadlineLeadM
                                                showDeadlineLeadPicker = false
                                                scope.launch {
                                                    SettingsManager.setNotifDeadlineLeadMinutes(db, tempDeadlineLeadH * 60 + tempDeadlineLeadM)
                                                    NotificationScheduler.cancelAllDeadlineNotifications(context, db)
                                                    NotificationScheduler.scheduleDeadlineNotifications(context, db)
                                                }
                                            }) { Text("OK", color = Color.Black, fontSize = 16.sp) }
                                        },
                                        dismissButton = {
                                            TextButton(onClick = { showDeadlineLeadPicker = false }) { Text("Cancel", color = Color.Black, fontSize = 16.sp) }
                                        },
                                        containerColor = BackgroundColor,
                                        text = {
                                            Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Text("Hours", fontSize = 12.sp)
                                                    Spacer(modifier = Modifier.height(8.dp))
                                                    Button(onClick = { tempDeadlineLeadH++ }, colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor)) { Text("▲") }
                                                    Text(tempDeadlineLeadH.toString(), fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
                                                    Button(onClick = { if (tempDeadlineLeadH > 0) tempDeadlineLeadH-- }, colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor)) { Text("▼") }
                                                }
                                                Spacer(modifier = Modifier.width(32.dp))
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Text("Minutes", fontSize = 12.sp)
                                                    Spacer(modifier = Modifier.height(8.dp))
                                                    Button(onClick = { tempDeadlineLeadM = (tempDeadlineLeadM + 5) % 60 }, colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor)) { Text("▲") }
                                                    Text(tempDeadlineLeadM.toString(), fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
                                                    Button(onClick = { tempDeadlineLeadM = if (tempDeadlineLeadM - 5 < 0) 55 else tempDeadlineLeadM - 5 }, colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor)) { Text("▼") }
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }

                        // Time picker — only for Day of / Day before
                        AnimatedVisibility(
                            visible = deadlineTimingIndex != 0,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Column {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), thickness = 1.dp, color = Color.LightGray)
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                    Text("Time", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                                    Spacer(modifier = Modifier.weight(1f))
                                    Button(
                                        onClick = { showDeadlineTimePicker = true },
                                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor)
                                    ) { Text(deadlineNotifTime.format(notifTimeFormatter)) }
                                }
                                if (showDeadlineTimePicker) {
                                    val deadlineTimePickerState = rememberTimePickerState(
                                        initialHour = deadlineNotifTime.hour,
                                        initialMinute = deadlineNotifTime.minute,
                                        is24Hour = false
                                    )
                                    AlertDialog(
                                        onDismissRequest = { showDeadlineTimePicker = false },
                                        confirmButton = {
                                            TextButton(onClick = {
                                                deadlineNotifTime = java.time.LocalTime.of(deadlineTimePickerState.hour, deadlineTimePickerState.minute, 0, 0)
                                                showDeadlineTimePicker = false
                                                scope.launch {
                                                    SettingsManager.setNotifDeadlineTime(db, deadlineNotifTime.toSecondOfDay())
                                                    NotificationScheduler.cancelAllDeadlineNotifications(context, db)
                                                    NotificationScheduler.scheduleDeadlineNotifications(context, db)
                                                }
                                            }) { Text("OK", color = Color.Black, fontSize = 16.sp) }
                                        },
                                        dismissButton = {
                                            TextButton(onClick = { showDeadlineTimePicker = false }) { Text("Cancel", color = Color.Black, fontSize = 16.sp) }
                                        },
                                        containerColor = BackgroundColor,
                                        text = {
                                            TimePicker(
                                                state = deadlineTimePickerState,
                                                colors = TimePickerDefaults.colors(
                                                    clockDialColor = BackgroundColor,
                                                    selectorColor = PrimaryColor,
                                                    containerColor = BackgroundColor,
                                                    periodSelectorBorderColor = Color.LightGray,
                                                    periodSelectorSelectedContainerColor = PrimaryColor,
                                                    periodSelectorUnselectedContainerColor = BackgroundColor,
                                                    periodSelectorSelectedContentColor = BackgroundColor,
                                                    periodSelectorUnselectedContentColor = Color.Black,
                                                    timeSelectorSelectedContainerColor = PrimaryColor,
                                                    timeSelectorUnselectedContainerColor = BackgroundColor,
                                                    timeSelectorSelectedContentColor = BackgroundColor,
                                                    timeSelectorUnselectedContentColor = Color.Black
                                                )
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── Events ───────────────────────────────────────────────────────────
        val eventsNotifEnabled = settings?.notifEventsEnabled ?: true
        var eventLeadH by remember { mutableIntStateOf((settings?.notifEventLeadMinutes ?: 10) / 60) }
        var eventLeadM by remember { mutableIntStateOf((settings?.notifEventLeadMinutes ?: 10) % 60) }
        var tempEventLeadH by remember { mutableIntStateOf(eventLeadH) }
        var tempEventLeadM by remember { mutableIntStateOf(eventLeadM) }
        var showEventLeadPicker by remember { mutableStateOf(false) }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(CardColor), RoundedCornerShape(12.dp))
                .padding(16.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Events", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.weight(1f))
                    Switch(
                        checked = eventsNotifEnabled,
                        onCheckedChange = { scope.launch {
                            SettingsManager.setNotifEventsEnabled(db, it)
                            NotificationScheduler.cancelAllEventNotifications(context, db)
                            if (it) NotificationScheduler.scheduleEventNotifications(context, db)
                        } },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = BackgroundColor,
                            checkedTrackColor = localPrimary,
                            uncheckedTrackColor = Color.LightGray,
                            uncheckedThumbColor = BackgroundColor
                        )
                    )
                }
                AnimatedVisibility(
                    visible = eventsNotifEnabled,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), thickness = 1.dp, color = Color.LightGray)
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text("Notify Before", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                            Spacer(modifier = Modifier.weight(1f))
                            Button(
                                onClick = {
                                    tempEventLeadH = eventLeadH
                                    tempEventLeadM = eventLeadM
                                    showEventLeadPicker = true
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor)
                            ) { Text("${eventLeadH}h ${eventLeadM}m") }
                        }
                        if (showEventLeadPicker) {
                            AlertDialog(
                                onDismissRequest = { showEventLeadPicker = false },
                                confirmButton = {
                                    TextButton(onClick = {
                                        eventLeadH = tempEventLeadH
                                        eventLeadM = tempEventLeadM
                                        showEventLeadPicker = false
                                        scope.launch {
                                            SettingsManager.setNotifEventLeadMinutes(db, tempEventLeadH * 60 + tempEventLeadM)
                                            NotificationScheduler.cancelAllEventNotifications(context, db)
                                            NotificationScheduler.scheduleEventNotifications(context, db)
                                        }
                                    }) { Text("OK", color = Color.Black, fontSize = 16.sp) }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showEventLeadPicker = false }) { Text("Cancel", color = Color.Black, fontSize = 16.sp) }
                                },
                                containerColor = BackgroundColor,
                                text = {
                                    Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("Hours", fontSize = 12.sp)
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Button(onClick = { tempEventLeadH++ }, colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor)) { Text("▲") }
                                            Text(tempEventLeadH.toString(), fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
                                            Button(onClick = { if (tempEventLeadH > 0) tempEventLeadH-- }, colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor)) { Text("▼") }
                                        }
                                        Spacer(modifier = Modifier.width(32.dp))
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("Minutes", fontSize = 12.sp)
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Button(onClick = { tempEventLeadM = (tempEventLeadM + 5) % 60 }, colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor)) { Text("▲") }
                                            Text(tempEventLeadM.toString(), fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
                                            Button(onClick = { tempEventLeadM = if (tempEventLeadM - 5 < 0) 55 else tempEventLeadM - 5 }, colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor)) { Text("▼") }
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Analytics",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Gray,
            modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 12.dp)
        )

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SettingsNavItem(label = "Overtime", onClick = { settingsCurrentView = "ati" })
            SettingsNavItem(label = "Database",  onClick = { settingsCurrentView = "database" })
            SettingsNavItem(label = "Schedule",  onClick = { settingsCurrentView = "schedule" })
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
fun WeekButton(label: String, selected: Boolean, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) color else Color.LightGray)
            .clickable { onClick() }
            .padding(horizontal = 18.dp, vertical = 10.dp)
    ) {
        Text(
            text = label,
            color = if (selected) BackgroundColor else Color.Black,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

/* Settings Nav Item */
@Composable
fun SettingsNavItem(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(CardColor))
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color.Black
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = Color.Gray,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/* ATI Model Page */
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun ATIPage(
    db: AppDatabase,
    categories: List<Category>,
    masterEvents: List<MasterEvent>,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor)
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
            contentDescription = "Back",
            tint = PrimaryColor,
            modifier = Modifier
                .padding(16.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onBack() }
                .size(40.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            ATIScatterPlot(
                db           = db,
                categories   = categories,
                masterEvents = masterEvents
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            BaseModelGraph()
        }
    }
}

@Composable
fun BaseModelGraph() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, bottom = 16.dp, top = 8.dp)
    ) {
        Text(
            "Base Neural Network Model",
            style = MaterialTheme.typography.titleMedium,
            color = Color.Black
        )
        Spacer(Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(CardColor), RoundedCornerShape(12.dp))
                .padding(16.dp)
        ) {
            val maxX = 120f
            val maxY = 45f
            val axisColor = Color.DarkGray
            val gridColor = Color(0xFFE0E0E0)
            val lineColor = PrimaryColor
            val mL = 124f
            val mB = 64f
            val mT = 12f
            val mR = 12f

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .background(BackgroundColor)
            ) {
                val W = size.width
                val H = size.height
                val w = W - mL - mR
                val h = H - mB - mT

                fun px(dataVal: Float) = mL + dataVal / maxX * w
                fun py(dataVal: Float) = mT + h - dataVal / maxY * h

                for (i in 1..4) {
                    val gy = mT + h * (1f - i / 4f)
                    drawLine(gridColor, Offset(mL, gy), Offset(mL + w, gy), strokeWidth = 1f)
                    val gx = mL + w * i / 4f
                    drawLine(gridColor, Offset(gx, mT), Offset(gx, mT + h), strokeWidth = 1f)
                }

                drawLine(axisColor, Offset(mL, mT + h), Offset(mL + w, mT + h), strokeWidth = 2f)
                drawLine(axisColor, Offset(mL, mT), Offset(mL, mT + h), strokeWidth = 2f)

                drawContext.canvas.nativeCanvas.apply {
                    val textPx     = 14.dp.toPx()
                    val tickPaint  = android.graphics.Paint().apply {
                        color     = android.graphics.Color.DKGRAY
                        textSize  = textPx
                        textAlign = android.graphics.Paint.Align.CENTER
                    }
                    val labelPaint = android.graphics.Paint().apply {
                        color     = android.graphics.Color.DKGRAY
                        textSize  = textPx
                        textAlign = android.graphics.Paint.Align.CENTER
                    }

                    val xTickY = mT + h + textPx + 6f
                    for (i in 0..4) {
                        val xVal = (maxX * i / 4f).toInt()
                        drawText(xVal.toString(), mL + w * i / 4f, xTickY, tickPaint)
                    }
                    drawText("Task Duration (min)", mL + w / 2f, xTickY + textPx + 8f, labelPaint)

                    tickPaint.textAlign = android.graphics.Paint.Align.RIGHT
                    for (i in 0..4) {
                        val yVal = (maxY * i / 4f).toInt()
                        drawText(yVal.toString(), mL - 28f, mT + h * (1f - i / 4f) + textPx / 2f, tickPaint)
                    }

                    withSave {
                        val pivotX = 16f
                        rotate(-90f, pivotX, mT + h / 2f)
                        labelPaint.textAlign = android.graphics.Paint.Align.CENTER
                        drawText("Predicted Padding (min)", pivotX, mT + h / 2f + textPx / 2f, labelPaint)
                    }
                }

                val path = androidx.compose.ui.graphics.Path()
                var first = true
                for (x in 0..120 step 2) {
                    val y = BaseModel.predictPadding(x)
                    val canvasX = px(x.toFloat())
                    val canvasY = py(y.toFloat().coerceIn(0f, maxY))
                    if (first) {
                        path.moveTo(canvasX, canvasY)
                        first = false
                    } else {
                        path.lineTo(canvasX, canvasY)
                    }
                }
                
                drawPath(
                    path = path,
                    color = lineColor,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f)
                )
            }
        }
    }
}

/* Database Page */
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun DatabasePage(
    db: AppDatabase,
    categories: List<Category>,
    masterEvents: List<MasterEvent>,
    eventOccurrences: List<EventOccurrence>,
    deadlines: List<Deadline>,
    masterBuckets: List<MasterTaskBucket>,
    bucketOccurrences: List<TaskBucketOccurrence>,
    masterTasks: List<MasterTask>,
    taskIntervals: List<TaskInterval>,
    masterReminders: List<MasterReminder>,
    reminderOccurrences: List<ReminderOccurrence>,
    categoryATIList: List<CategoryATI>,
    eventATIList: List<EventATI>,
    settings: List<AppSetting>,
    onBack: () -> Unit,
    onRefresh: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scrollV = rememberScrollState()
    val scrollH = rememberScrollState()
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor)
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
            contentDescription = "Back",
            tint = PrimaryColor,
            modifier = Modifier
                .padding(16.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onBack() }
                .size(40.dp)
        )

        // Database State
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .background(Color(CardColor), RoundedCornerShape(12.dp))
                .padding(16.dp)
        ) {
            Column {
                Text("Database State", fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Color.Black)
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Button(
                        onClick = {
                            CoroutineScope(Dispatchers.IO).launch {
                                db.reminderDao().getAllMasterReminders()
                                    .forEach { db.reminderDao().deleteMasterReminder(it.id) }
                                db.taskDao().getAllMasterTasks()
                                    .forEach { db.taskDao().deleteMasterTask(it.id) }
                                db.taskBucketDao().getAllMasterBuckets()
                                    .forEach { db.taskBucketDao().deleteMasterBucket(it.id) }
                                db.deadlineDao().getAll()
                                    .forEach { db.deadlineDao().deleteById(it.id) }
                                db.eventDao().getAllMasterEvents()
                                    .forEach { db.eventDao().deleteMasterEvent(it.id) }
                                db.categoryDao().getAll()
                                    .forEach { db.categoryDao().deleteById(it.id) }
                                // Skip id=0 — None records are permanent like the settings row
                                db.categoryATIDao().getAll()
                                    .filter { it.categoryId != 0 }
                                    .forEach { db.categoryATIDao().deleteById(it.categoryId) }
                                db.eventATIDao().getAll()
                                    .filter { it.eventId != 0 }
                                    .forEach { db.eventATIDao().deleteById(it.eventId) }
                                // Reset None records to defaults instead of deleting them
                                db.categoryATIDao().update(CategoryATI(categoryId = 0))
                                db.eventATIDao().update(EventATI(eventId = 0))
                                onRefresh()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Gray),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(16.dp)
                    ) { Text("Clear", fontSize = 16.sp) }

                    Spacer(modifier = Modifier.width(12.dp))

                    Button(
                        onClick = {
                            CoroutineScope(Dispatchers.IO).launch {
                                runSample(context, db)
                                trimAndExtendOccurrences(context, db)
                                onRefresh()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(16.dp)
                    ) { Text("Sample", fontSize = 16.sp) }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollV)
                .horizontalScroll(scrollH)
                .padding(8.dp)
        ) {
            fun formatRecurrenceRule(rule: RecurrenceRule, freq: RecurrenceFrequency): String {
                return when (freq) {
                    RecurrenceFrequency.NONE, RecurrenceFrequency.DAILY -> ""
                    RecurrenceFrequency.WEEKLY  -> rule.daysOfWeek?.joinToString(", ") ?: ""
                    RecurrenceFrequency.MONTHLY -> rule.daysOfMonth?.joinToString(", ") ?: ""
                    RecurrenceFrequency.YEARLY  -> rule.monthAndDay?.let { "${it.first}/${it.second}" } ?: ""
                }
            }

            @Composable
            fun <T> Table(
                title: String,
                data: List<T>,
                headers: List<String>,
                rowContent: (T) -> List<String>
            ) {
                var expanded by remember { mutableStateOf(false) }
                val chevronRotation by animateFloatAsState(
                    targetValue = if (expanded) 180f else 0f,
                    animationSpec = tween(durationMillis = 300),
                    label = "chevron_$title"
                )

                // Title row with chevron toggle
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { expanded = !expanded }
                        .padding(vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        tint = Color.Gray,
                        modifier = Modifier
                            .size(20.dp)
                            .graphicsLayer { rotationZ = chevronRotation }
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(title, style = MaterialTheme.typography.titleMedium, color = Color.Black)
                }
                Spacer(Modifier.height(8.dp))
                Column(modifier = Modifier.border(1.dp, GRID_COLOR)) {
                    // Header row — always visible
                    Row(
                        modifier = Modifier
                            .background(HEADER_BG)
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min)
                    ) {
                        headers.forEachIndexed { index, header ->
                            Box(
                                modifier = Modifier
                                    .width(COLUMN_WIDTH)
                                    .fillMaxHeight()
                                    .drawWithContent {
                                        drawContent()
                                        if (index < headers.lastIndex) drawLine(
                                            color       = GRID_COLOR,
                                            start       = Offset(size.width, 0f),
                                            end         = Offset(size.width, size.height),
                                            strokeWidth = 1.dp.toPx()
                                        )
                                    }
                                    .padding(8.dp)
                            ) { Text(header, color = Color.Black) }
                        }
                    }
                    // Data rows — collapsible with smooth animation
                    AnimatedVisibility(
                        visible = expanded,
                        enter = expandVertically(animationSpec = tween(durationMillis = 300)),
                        exit  = shrinkVertically(animationSpec = tween(durationMillis = 300))
                    ) {
                        Column {
                            val rows = data.ifEmpty { listOf(null) }
                            rows.forEach { row ->
                                val values = row?.let { rowContent(it) } ?: headers.map { "" }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(IntrinsicSize.Min)
                                        .drawWithContent {
                                            drawContent()
                                            drawLine(
                                                color       = GRID_COLOR,
                                                start       = Offset(0f, size.height),
                                                end         = Offset(size.width, size.height),
                                                strokeWidth = 1.dp.toPx()
                                            )
                                        }
                                ) {
                                    values.forEachIndexed { index, value ->
                                        Box(
                                            modifier = Modifier
                                                .width(COLUMN_WIDTH)
                                                .fillMaxHeight()
                                                .drawWithContent {
                                                    drawContent()
                                                    if (index < values.lastIndex) drawLine(
                                                        color       = GRID_COLOR,
                                                        start       = Offset(size.width, 0f),
                                                        end         = Offset(size.width, size.height),
                                                        strokeWidth = 1.dp.toPx()
                                                    )
                                                }
                                                .padding(8.dp)
                                        ) { Text(value, color = Color.Black) }
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            Table(
                title   = "Categories",
                data    = categories,
                headers = listOf("ID", "Title", "Notes", "Color")
            ) { c -> listOf(c.id.toString(), c.title, c.notes ?: "", c.color) }

            Table(
                title   = "Master Events",
                data    = masterEvents,
                headers = listOf(
                    "ID", "Title", "Notes", "Color",
                    "Start Date", "End Date", "Start Time", "End Time",
                    "Frequency", "Rule", "Category ID"
                )
            ) { e ->
                listOf(
                    e.id.toString(), e.title, e.notes ?: "", e.color ?: "",
                    e.startDate.toString(), e.endDate?.toString() ?: "",
                    e.startTime.toString(), e.endTime.toString(),
                    e.recurFreq.name,
                    formatRecurrenceRule(e.recurRule, e.recurFreq),
                    e.categoryId?.toString() ?: ""
                )
            }

            Table(
                title   = "Event Occurrences",
                data    = eventOccurrences,
                headers = listOf("ID", "Master Event ID", "Notes", "Occur Date", "Start Time", "End Time")
            ) { o ->
                listOf(
                    o.id.toString(), o.masterEventId.toString(),
                    o.notes ?: "", o.occurDate.toString(),
                    o.startTime.toString(), o.endTime.toString()
                )
            }

            Table(
                title   = "Deadlines",
                data    = deadlines,
                headers = listOf("ID", "Title", "Notes", "Date", "Time", "Category ID", "Event ID")
            ) { d ->
                listOf(
                    d.id.toString(), d.title, d.notes ?: "",
                    d.date.toString(), d.time.toString(),
                    d.categoryId?.toString() ?: "", d.eventId?.toString() ?: ""
                )
            }

            Table(
                title   = "Master Task Buckets",
                data    = masterBuckets,
                headers = listOf("ID", "Start Date", "End Date", "Start Time", "End Time", "Frequency", "Rule")
            ) { b ->
                listOf(
                    b.id.toString(),
                    b.startDate.toString(), b.endDate?.toString() ?: "",
                    b.startTime.toString(), b.endTime.toString(),
                    b.recurFreq.name,
                    formatRecurrenceRule(b.recurRule, b.recurFreq)
                )
            }

            Table(
                title   = "Task Bucket Occurrences",
                data    = bucketOccurrences,
                headers = listOf("ID", "Master Bucket ID", "Occur Date", "Start Time", "End Time", "Is Exception")
            ) { o ->
                listOf(
                    o.id.toString(), o.masterBucketId.toString(),
                    o.occurDate.toString(), o.startTime.toString(),
                    o.endTime.toString(), o.isException.toString()
                )
            }

            Table(
                title   = "Master Tasks",
                data    = masterTasks,
                headers = listOf(
                    "ID", "Title", "Notes", "All Day", "Breakable", "No. Intervals",
                    "Start Date", "Start Time", "Duration", "Actual Duration",
                    "Status", "Time Left", "Overtime", "Deadline Missed", "Completed At",
                    "Category ID", "Event ID", "Deadline ID", "Dependency Task ID"
                )
            ) { t ->
                listOf(
                    t.id.toString(), t.title, t.notes ?: "",
                    t.allDay?.toString() ?: "", t.breakable?.toString() ?: "",
                    t.noIntervals.toString(),
                    t.startDate?.toString() ?: "", t.startTime?.toString() ?: "",
                    t.predictedDuration.toString(), t.actualDuration?.toString() ?: "",
                    t.status?.toString() ?: "", t.timeLeft?.toString() ?: "",
                    t.overTime?.toString() ?: "", t.deadlineMissed.toString(),
                    t.completedAt?.toString() ?: "",
                    t.categoryId?.toString() ?: "", t.eventId?.toString() ?: "",
                    t.deadlineId?.toString() ?: "", t.dependencyTaskId?.toString() ?: ""
                )
            }

            Table(
                title   = "Task Intervals",
                data    = taskIntervals,
                headers = listOf(
                    "ID", "Master Task ID", "Interval No.", "Notes",
                    "Occur Date", "Start Time", "End Time",
                    "Status", "Time Left", "Overtime", "ATI Padding"
                )
            ) { i ->
                listOf(
                    i.id.toString(), i.masterTaskId.toString(),
                    i.intervalNo.toString(), i.notes ?: "",
                    i.occurDate.toString(), i.startTime.toString(), i.endTime.toString(),
                    i.status?.toString() ?: "", i.timeLeft?.toString() ?: "",
                    i.overTime?.toString() ?: "", i.atiPadding.toString()
                )
            }

            Table(
                title   = "Master Reminders",
                data    = masterReminders,
                headers = listOf(
                    "ID", "Title", "Notes", "Start Date", "End Date",
                    "Time", "All Day", "Frequency", "Rule", "Category ID"
                )
            ) { r ->
                listOf(
                    r.id.toString(), r.title, r.notes ?: "",
                    r.startDate.toString(), r.endDate?.toString() ?: "",
                    r.time?.toString() ?: "", r.allDay.toString(),
                    r.recurFreq.name,
                    formatRecurrenceRule(r.recurRule, r.recurFreq),
                    r.categoryId?.toString() ?: ""
                )
            }

            Table(
                title   = "Reminder Occurrences",
                data    = reminderOccurrences,
                headers = listOf("ID", "Master Reminder ID", "Notes", "Occur Date", "Time", "All Day")
            ) { o ->
                listOf(
                    o.id.toString(), o.masterReminderId.toString(),
                    o.notes ?: "", o.occurDate.toString(),
                    o.time?.toString() ?: "", o.allDay.toString()
                )
            }

            Table(
                title   = "Category ATI",
                data    = categoryATIList,
                headers = listOf(
                    "Category ID", "Title", "Score", "Deadline Misses",
                    "Avg Overtime", "Tasks Completed", "Avg Padding", "Slope", "Intercept"
                )
            ) { a ->
                val categoryTitle = if (a.categoryId == 0) "None"
                else categories.find { it.id == a.categoryId }?.title ?: ""
                listOf(
                    a.categoryId.toString(), categoryTitle, "%.3f".format(a.score),
                    a.deadlineMissCount.toString(), "%.1f".format(a.avgOvertime),
                    a.tasksCompleted.toString(), a.predictedPadding.toString(),
                    "%.3f".format(a.paddingSlope), "%.3f".format(a.paddingIntercept)
                )
            }

            Table(
                title   = "Event ATI",
                data    = eventATIList,
                headers = listOf(
                    "Event ID", "Title", "Score", "Deadline Misses",
                    "Avg Overtime", "Tasks Completed", "Avg Padding", "Slope", "Intercept"
                )
            ) { a ->
                val eventTitle = if (a.eventId == 0) "None"
                else masterEvents.find { it.id == a.eventId }?.title ?: ""
                listOf(
                    a.eventId.toString(), eventTitle, "%.3f".format(a.score),
                    a.deadlineMissCount.toString(), "%.1f".format(a.avgOvertime),
                    a.tasksCompleted.toString(), a.predictedPadding.toString(),
                    "%.3f".format(a.paddingSlope), "%.3f".format(a.paddingIntercept)
                )
            }

            Table(
                title   = "Settings",
                data    = settings,
                headers = listOf(
                    "ID", "Start Week On Monday", "Primary Color",
                    "Break Duration", "Break Every"
                )
            ) { s ->
                listOf(
                    s.id.toString(), s.startWeekOnMonday.toString(),
                    s.primaryColor,
                    s.breakDuration.toString(), s.breakEvery.toString()
                )
            }
        }
    }
}

/* Schedule Page */
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun SchedulePage(
    scheduleOrderRows: List<ScheduleOrderRow>,
    onBack: () -> Unit
) {
    val scrollV = rememberScrollState()
    val scrollH = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor)
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
            contentDescription = "Back",
            tint = PrimaryColor,
            modifier = Modifier
                .padding(16.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onBack() }
                .size(40.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollV)
                .horizontalScroll(scrollH)
                .padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
        ) {
            Text(
                "Task Schedule Order",
                style = MaterialTheme.typography.titleMedium,
                color = Color.Black
            )
            Spacer(Modifier.height(8.dp))

            Column(modifier = Modifier.border(1.dp, GRID_COLOR)) {
                Row(
                    modifier = Modifier
                        .background(HEADER_BG)
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min)
                ) {
                    listOf("Task ID", "Title", "Dependency", "Urgency", "Cat. Score", "Event Score")
                        .forEachIndexed { index, header ->
                            Box(
                                modifier = Modifier
                                    .width(COLUMN_WIDTH)
                                    .fillMaxHeight()
                                    .drawWithContent {
                                        drawContent()
                                        if (index < 5) drawLine(
                                            color       = GRID_COLOR,
                                            start       = Offset(size.width, 0f),
                                            end         = Offset(size.width, size.height),
                                            strokeWidth = 1.dp.toPx()
                                        )
                                    }
                                    .padding(8.dp)
                            ) { Text(header, color = Color.Black) }
                        }
                }
                val displayRows = scheduleOrderRows.ifEmpty { listOf(null) }
                displayRows.forEach { row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min)
                            .drawWithContent {
                                drawContent()
                                drawLine(
                                    color       = GRID_COLOR,
                                    start       = Offset(0f, size.height),
                                    end         = Offset(size.width, size.height),
                                    strokeWidth = 1.dp.toPx()
                                )
                            }
                    ) {
                        val values = if (row != null) listOf(
                            row.masterTaskId.toString(),
                            row.title,
                            row.dependencyTaskId?.toString() ?: "",
                            row.urgency?.toString() ?: "",
                            "%.3f".format(row.categoryScore),
                            "%.3f".format(row.eventScore)
                        ) else List(6) { "" }

                        values.forEachIndexed { index, value ->
                            Box(
                                modifier = Modifier
                                    .width(COLUMN_WIDTH)
                                    .fillMaxHeight()
                                    .drawWithContent {
                                        drawContent()
                                        if (index < 5) drawLine(
                                            color       = GRID_COLOR,
                                            start       = Offset(size.width, 0f),
                                            end         = Offset(size.width, size.height),
                                            strokeWidth = 1.dp.toPx()
                                        )
                                    }
                                    .padding(8.dp)
                            ) { Text(value, color = Color.Black) }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/* ATI Scatter Plot */
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun ATIScatterPlot(
    db: AppDatabase,
    categories: List<Category>,
    masterEvents: List<MasterEvent>
) {
    val scope = rememberCoroutineScope()

    var selectedType        by remember { mutableStateOf("Category") }
    val types               = listOf("Category", "Event")
    var selectedEntityIndex by remember { mutableIntStateOf(0) }
    var dataPoints          by remember { mutableStateOf(listOf<Pair<Float, Float>>()) }
    var atiRecord           by remember { mutableStateOf("") }
    var slope               by remember { mutableFloatStateOf(0f) }
    var intercept           by remember { mutableFloatStateOf(0f) }

    // Index 0 is always "None"; real entities start at index 1
    val entityNames = if (selectedType == "Category")
        listOf("None") + categories.map { it.title }
    else
        listOf("None") + masterEvents.map { it.title }

    fun loadData() {
        scope.launch(Dispatchers.IO) {
            val allTasks = db.taskDao().getAllMasterTasks()
            val points   = mutableListOf<Pair<Float, Float>>()

            if (selectedType == "Category") {
                val ati: CategoryATI?
                val filteredTasks: List<MasterTask>

                if (selectedEntityIndex == 0) {
                    // None record — tasks with no category
                    ati           = db.categoryATIDao().getById(0)
                    filteredTasks = allTasks.filter { it.categoryId == null && it.status == 3 && it.allDay == null }
                } else {
                    val cat = categories.getOrNull(selectedEntityIndex - 1) ?: return@launch
                    ati           = db.categoryATIDao().getById(cat.id)
                    filteredTasks = allTasks.filter { it.categoryId == cat.id && it.status == 3 && it.allDay == null }
                }

                filteredTasks
                    .sortedBy { it.completedAt }
                    .takeLast(10)
                    .forEach { t -> points.add(Pair(t.predictedDuration.toFloat(), (t.overTime ?: 0).toFloat())) }

                atiRecord =
                    "Priority Score: ${"%.3f".format(ati?.score ?: 0f)}\n" +
                            "Deadline Misses: ${ati?.deadlineMissCount ?: 0}\n" +
                            "Avg Overtime: ${"%.1f".format(ati?.avgOvertime ?: 0f)} min\n" +
                            "Avg Padding: ${ati?.predictedPadding ?: 0} min\n" +
                            "Slope: ${"%.3f".format(ati?.paddingSlope ?: 0f)}\n" +
                            "Intercept: ${"%.3f".format(ati?.paddingIntercept ?: 0f)}\n" +
                            "Tasks: ${points.size}"
                slope     = ati?.paddingSlope ?: 0f
                intercept = ati?.paddingIntercept ?: 0f

            } else {
                // Event branch
                val ati: EventATI?
                val filteredTasks: List<MasterTask>

                if (selectedEntityIndex == 0) {
                    // None record — tasks with no event
                    ati           = db.eventATIDao().getById(0)
                    filteredTasks = allTasks.filter { it.eventId == null && it.status == 3 && it.allDay == null }
                } else {
                    val evt = masterEvents.getOrNull(selectedEntityIndex - 1) ?: return@launch
                    ati           = db.eventATIDao().getById(evt.id)
                    filteredTasks = allTasks.filter { it.eventId == evt.id && it.status == 3 && it.allDay == null }
                }

                filteredTasks
                    .sortedBy { it.completedAt }
                    .takeLast(10)
                    .forEach { t -> points.add(Pair(t.predictedDuration.toFloat(), (t.overTime ?: 0).toFloat())) }

                atiRecord =
                    "Priority Score: ${"%.3f".format(ati?.score ?: 0f)}\n" +
                            "Deadline Misses: ${ati?.deadlineMissCount ?: 0}\n" +
                            "Avg Overtime: ${"%.1f".format(ati?.avgOvertime ?: 0f)} min\n" +
                            "Avg Padding: ${ati?.predictedPadding ?: 0} min\n" +
                            "Slope: ${"%.3f".format(ati?.paddingSlope ?: 0f)}\n" +
                            "Intercept: ${"%.3f".format(ati?.paddingIntercept ?: 0f)}\n" +
                            "Tasks: ${points.size}"
                slope     = ati?.paddingSlope ?: 0f
                intercept = ati?.paddingIntercept ?: 0f
            }

            dataPoints = points
        }
    }

    LaunchedEffect(selectedType, selectedEntityIndex, categories, masterEvents) { loadData() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, bottom = 8.dp, top = 0.dp)
    ) {
        Text(
            "Overtime Regression Model",
            style = MaterialTheme.typography.titleMedium,
            color = Color.Black
        )
        Spacer(Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(CardColor), RoundedCornerShape(12.dp))
                .padding(16.dp)
        ) {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    types.forEach { type ->
                        WeekButton(
                            label    = type,
                            selected = selectedType == type,
                            color    = PrimaryColor
                        ) {
                            selectedType        = type
                            selectedEntityIndex = 0
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // entityNames always has at least "None", so the list is never empty
                var showDropdown by remember(selectedType) { mutableStateOf(false) }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication        = null
                        ) { showDropdown = !showDropdown }
                ) {
                    Column {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(BackgroundColor, RoundedCornerShape(8.dp))
                                .border(1.dp, Color.LightGray, RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        ) {
                            Text(entityNames.getOrNull(selectedEntityIndex) ?: "None", fontSize = 14.sp, color = Color.Black)
                        }
                        AnimatedVisibility(
                            visible = showDropdown,
                            enter   = fadeIn() + expandVertically(),
                            exit    = fadeOut() + shrinkVertically()
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(top = 8.dp)
                                    .heightIn(max = 180.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                entityNames.forEachIndexed { index, name ->
                                    val isSelected = selectedEntityIndex == index
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                            .background(
                                                if (isSelected) PrimaryColor else Color.LightGray,
                                                RoundedCornerShape(8.dp)
                                            )
                                            .clickable {
                                                selectedEntityIndex = index
                                                showDropdown        = false
                                            }
                                            .padding(12.dp)
                                    ) {
                                        Text(
                                            name,
                                            fontSize = 14.sp,
                                            color = if (isSelected) BackgroundColor else Color.Black,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        val maxX      = (dataPoints.maxOfOrNull { it.first } ?: 0f).let { (it * 1.2f).coerceAtLeast(120f) }
        val maxY      = (dataPoints.maxOfOrNull { it.second } ?: 0f).let { (it * 1.2f).coerceAtLeast(60f) }
        val dotColor  = Color.Gray
        val lineColor = PrimaryColor
        val axisColor = Color.DarkGray
        val gridColor = Color(0xFFE0E0E0)
        val mL = 124f
        val mB = 64f
        val mT = 12f
        val mR = 12f

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .background(BackgroundColor)
        ) {
            val W = size.width
            val H = size.height
            val w = W - mL - mR
            val h = H - mB - mT

            fun px(dataVal: Float) = mL + dataVal / maxX * w
            fun py(dataVal: Float) = mT + h - dataVal / maxY * h

            for (i in 1..4) {
                val gy = mT + h * (1f - i / 4f)
                drawLine(gridColor, Offset(mL, gy), Offset(mL + w, gy), strokeWidth = 1f)
                val gx = mL + w * i / 4f
                drawLine(gridColor, Offset(gx, mT), Offset(gx, mT + h), strokeWidth = 1f)
            }

            drawLine(axisColor, Offset(mL, mT + h), Offset(mL + w, mT + h), strokeWidth = 2f)
            drawLine(axisColor, Offset(mL, mT), Offset(mL, mT + h), strokeWidth = 2f)

            drawContext.canvas.nativeCanvas.apply {
                val textPx     = 14.dp.toPx()
                val tickPaint  = android.graphics.Paint().apply {
                    color     = android.graphics.Color.DKGRAY
                    textSize  = textPx
                    textAlign = android.graphics.Paint.Align.CENTER
                }
                val labelPaint = android.graphics.Paint().apply {
                    color     = android.graphics.Color.DKGRAY
                    textSize  = textPx
                    textAlign = android.graphics.Paint.Align.CENTER
                }

                val xTickY = mT + h + textPx + 6f
                for (i in 0..4) {
                    val xVal = (maxX * i / 4f).toInt()
                    drawText(xVal.toString(), mL + w * i / 4f, xTickY, tickPaint)
                }
                drawText("Duration (min)", mL + w / 2f, xTickY + textPx + 8f, labelPaint)

                tickPaint.textAlign = android.graphics.Paint.Align.RIGHT
                for (i in 0..4) {
                    val yVal = (maxY * i / 4f).toInt()
                    drawText(yVal.toString(), mL - 28f, mT + h * (1f - i / 4f) + textPx / 2f, tickPaint)
                }

                withSave {
                    val pivotX = 16f
                    rotate(-90f, pivotX, mT + h / 2f)
                    labelPaint.textAlign = android.graphics.Paint.Align.CENTER
                    drawText("Overtime (min)", pivotX, mT + h / 2f + textPx / 2f, labelPaint)
                }
            }

            if (dataPoints.size >= 2 && (slope != 0f || intercept != 0f)) {
                val startX = if (intercept < 0f && slope > 0f) -intercept / slope else 0f
                val rawEndX = if (slope < 0f && intercept > 0f) intercept / (-slope) else maxX
                val endX = rawEndX.coerceAtMost(maxX)
                val endY = slope * endX + intercept
                if (startX < endX) {
                    drawLine(
                        lineColor,
                        Offset(px(startX), py((slope * startX + intercept).coerceAtMost(maxY))),
                        Offset(px(endX), py(endY.coerceIn(0f, maxY))),
                        strokeWidth = 2f
                    )
                }
            }

            dataPoints.forEach { (x, y) ->
                drawCircle(dotColor, radius = 8f, center = Offset(px(x), py(y)))
            }
        }

        Spacer(Modifier.height(24.dp))

        if (atiRecord.isEmpty()) {
            Text("Stats Unavailable", fontSize = 14.sp, color = Color.DarkGray)
        } else {
            val rows = atiRecord.lines().filter { it.isNotBlank() }.map { line ->
                val colon = line.indexOf(':')
                if (colon >= 0) line.substring(0, colon + 1) to line.substring(colon + 1).trim()
                else line to ""
            }
            Row(
                modifier = Modifier.padding(start = 44.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column {
                    rows.forEach { (label, _) ->
                        Text(label, fontSize = 14.sp, color = Color.DarkGray)
                    }
                }
                Column {
                    rows.forEach { (_, value) ->
                        Text(value, fontSize = 14.sp, color = Color.DarkGray)
                    }
                }
            }
        }
    }
}