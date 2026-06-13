package com.planned

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class UpdateFormData(
    val categories: List<Category>,
    val events: List<MasterEvent>,
    val deadlines: List<Deadline>,
    val dependencyTasks: List<MasterTask>,
    val selectedCategory: Int?,
    val selectedEvent: Int?,
    val selectedDeadline: Int?,
    val selectedDependencyTask: Int?
)

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun Tasks(db: AppDatabase) {
    // Uses globally hoisted state so navigation away and back preserves position

    when (tasksCurrentView) {
        "main" -> TasksMainView(
            db = db,
            onUnscheduledClick = { tasksCurrentView = "unscheduled" },
            onScheduledClick = { tasksCurrentView = "scheduled" },
            onAllDayClick = { tasksCurrentView = "allday" }
        )
        "unscheduled" -> UnscheduledTasksList(
            db = db,
            onBack = { tasksCurrentView = "main" },
            onTaskClick = { task ->
                tasksSelectedTask = task
                tasksCurrentView = "info"
            }
        )
        "scheduled" -> ScheduledTasksList(
            db = db,
            onBack = { tasksCurrentView = "main" },
            onTaskClick = { interval, task ->
                tasksSelectedInterval = interval
                tasksSelectedTask = task
                tasksCurrentView = "info"
            }
        )
        "allday" -> AllDayTasksList(
            db = db,
            onBack = { tasksCurrentView = "main" },
            onTaskClick = { task ->
                tasksSelectedTask = task
                tasksCurrentView = "alldayinfo"
            }
        )
        "info" -> tasksSelectedTask?.let { task ->
            TaskInfoPage(
                db = db,
                task = task,
                onBack = {
                    tasksCurrentView = if (tasksSelectedInterval != null) "scheduled" else "unscheduled"
                    tasksSelectedTask = null
                    tasksSelectedInterval = null
                    tasksUpdateFormData = null
                },
                onUpdateDataReady = { data -> tasksUpdateFormData = data },
                onUpdate = { tasksCurrentView = "update" },
                onPlay = {
                    pomodoroReturnScreen = "info"
                    tasksCurrentView = "pomodoro"
                }
            )
        }
        "alldayinfo" -> tasksSelectedTask?.let { task ->
            AllDayTaskInfoPage(
                db = db,
                task = task,
                onBack = {
                    tasksCurrentView = "allday"
                    tasksSelectedTask = null
                    tasksUpdateFormData = null
                },
                onUpdate = { tasksCurrentView = "alldayupdate" },
                onPlay = {
                    pomodoroReturnScreen = "alldayinfo"
                    tasksCurrentView = "alldaypomodoro"
                }
            )
        }
        "update" -> tasksSelectedTask?.let { task ->
            tasksUpdateFormData?.let { formData ->
                TaskUpdateForm(
                    db = db,
                    task = task,
                    preloadedData = formData,
                    onBack = { tasksCurrentView = "info" },
                    onSaveSuccess = { updatedTask ->
                        tasksSelectedTask = updatedTask
                        tasksUpdateFormData = null
                        tasksCurrentView = "info"
                    }
                )
            }
        }
        "alldayupdate" -> tasksSelectedTask?.let { task ->
            AllDayTaskUpdateForm(
                db = db,
                task = task,
                onBack = { tasksCurrentView = "alldayinfo" },
                onSaveSuccess = { updatedTask ->
                    tasksSelectedTask = updatedTask
                    tasksCurrentView = "alldayinfo"
                }
            )
        }
        "pomodoro" -> tasksSelectedTask?.let { task ->
            PomodoroPage(
                db = db,
                task = task,
                onBack = {
                    val returnTo = pomodoroReturnScreen
                    pomodoroReturnScreen = "Calendars"
                    tasksCurrentView = when (returnTo) {
                        "info" -> "info"
                        "scheduled" -> "scheduled"
                        "unscheduled" -> "unscheduled"
                        else -> if (tasksSelectedInterval != null) "scheduled" else "unscheduled"
                    }
                },
                onComplete = {
                    PomodoroState.clear()
                    val returnTo = if (tasksSelectedInterval != null) "scheduled" else "unscheduled"
                    tasksSelectedTask = null
                    tasksSelectedInterval = null
                    tasksUpdateFormData = null
                    pomodoroReturnScreen = "Calendars"
                    tasksCurrentView = returnTo
                }
            )
        }
        "alldaypomodoro" -> tasksSelectedTask?.let { task ->
            AllDayPomodoroPage(
                db = db,
                task = task,
                onBack = {
                    val returnTo = pomodoroReturnScreen
                    pomodoroReturnScreen = "Calendars"
                    tasksCurrentView = if (returnTo == "alldayinfo") "alldayinfo" else "allday"
                },
                onComplete = {
                    PomodoroState.clear()
                    tasksSelectedTask = null
                    tasksUpdateFormData = null
                    pomodoroReturnScreen = "Calendars"
                    tasksCurrentView = "allday"
                }
            )
        }
    }
}

/* MAIN VIEW */
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun TasksMainView(
    db: AppDatabase,
    onUnscheduledClick: () -> Unit,
    onScheduledClick: () -> Unit,
    onAllDayClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor)
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TaskCategoryBox(
                db = db,
                title = "Unscheduled \nTasks",
                modifier = Modifier.weight(1f),
                onClick = onUnscheduledClick
            )
            TaskCategoryBox(
                db = db,
                title = "Scheduled \nTasks",
                modifier = Modifier.weight(1f),
                onClick = onScheduledClick
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            TaskCategoryBox(
                db = db,
                title = "All-Day \nTasks",
                modifier = Modifier.weight(1f),
                onClick = onAllDayClick
            )
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun TaskCategoryBox(
    db: AppDatabase,
    title: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var taskCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        val tasks = db.taskDao().getAllMasterTasks().filter { it.status != 3 }
        taskCount = when {
            title.contains("Unscheduled") -> tasks.filter { it.noIntervals == 0 && it.allDay == null }.size
            title.contains("Scheduled") -> tasks.filter { it.noIntervals > 0 && it.allDay == null }.size
            title.contains("All-Day") -> tasks.filter { it.allDay != null }.size
            else -> 0
        }
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(CardColor))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Top
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (taskCount > 0) PrimaryColor else Color.LightGray),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = taskCount.toString(),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = Color.Black
            )
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun UnscheduledTasksList(
    db: AppDatabase,
    onBack: () -> Unit,
    onTaskClick: (MasterTask) -> Unit
) {
    var tasks by remember { mutableStateOf<List<MasterTask>>(emptyList()) }
    var categories by remember { mutableStateOf<List<Category>>(emptyList()) }

    LaunchedEffect(Unit) {
        tasks = db.taskDao().getAllMasterTasks().filter { it.noIntervals == 0 && it.status != 3 && it.allDay == null }
        categories = CategoryManager.getAll(db)
    }

    val grouped = tasks.groupBy { it.categoryId }
    val sortedCategoryIds = categories.map { it.id as Int? }.filter { grouped.containsKey(it) } +
            if (grouped.containsKey(null)) listOf(null) else emptyList()

    Column(modifier = Modifier.fillMaxSize().background(BackgroundColor)) {
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

        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            Text("Unscheduled Tasks", fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp, top = 4.dp))

            if (tasks.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = "No unscheduled tasks", fontSize = 18.sp, color = Color.Gray)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    sortedCategoryIds.forEach { catId ->
                        val categoryName = if (catId == null) "No Category"
                        else categories.find { it.id == catId }?.title ?: "No Category"
                        val categoryTasks = grouped[catId] ?: emptyList()
                        item {
                            Text(
                                text = categoryName,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Gray,
                                modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp)
                            )
                        }
                        items(categoryTasks) { task ->
                            UnscheduledTaskItem(db = db, task = task, onClick = { onTaskClick(task) })
                        }
                    }
                }
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun UnscheduledTaskItem(
    db: AppDatabase,
    task: MasterTask,
    onClick: () -> Unit
) {
    var category by remember { mutableStateOf<Category?>(null) }
    var eventColor by remember { mutableStateOf<Color?>(null) }

    LaunchedEffect(task.eventId, task.categoryId) {
        if (task.eventId != null) {
            val event = db.eventDao().getAllMasterEvents().find { it.id == task.eventId }
            eventColor = event?.color?.let { Converters.toColor(it) }
                ?: event?.categoryId?.let { catId -> db.categoryDao().getAll().find { it.id == catId }?.color?.let { Converters.toColor(it) } }
        }
        category = task.categoryId?.let { db.categoryDao().getCategoryById(it) }
    }

    val innerColor = eventColor ?: category?.let { Converters.toColor(it.color) } ?: Color.LightGray

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(CardColor))
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(INNER_CIRCLE_SIZE).clip(CircleShape).background(innerColor))
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = task.title,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = Color.Gray,
            modifier = Modifier.size(20.dp)
        )
    }
}

/* SCHEDULED TASKS LIST */
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun ScheduledTasksList(
    db: AppDatabase,
    onBack: () -> Unit,
    onTaskClick: (TaskInterval, MasterTask) -> Unit
) {
    var intervals by remember { mutableStateOf<List<TaskInterval>>(emptyList()) }
    var masterTasks by remember { mutableStateOf<Map<Int, MasterTask>>(emptyMap()) }
    var categories by remember { mutableStateOf<List<Category>>(emptyList()) }

    LaunchedEffect(Unit) {
        intervals = db.taskDao().getAllIntervals()
        val tasks = db.taskDao().getAllMasterTasks()
        masterTasks = tasks.associateBy { it.id }
        categories = CategoryManager.getAll(db)
    }

    val groupedByTask = intervals.groupBy { it.masterTaskId }
    val grouped = groupedByTask.entries
        .mapNotNull { (masterTaskId, taskIntervals) ->
            masterTasks[masterTaskId]?.let { it.categoryId to Pair(masterTaskId, taskIntervals) }
        }
        .groupBy({ it.first }, { it.second })

    val sortedCategoryIds = categories.map { it.id as Int? }.filter { grouped.containsKey(it) } +
            if (grouped.containsKey(null)) listOf(null) else emptyList()

    Column(modifier = Modifier.fillMaxSize().background(BackgroundColor)) {
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

        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            Text("Scheduled Tasks", fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp, top = 4.dp))

            if (groupedByTask.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = "No scheduled tasks", fontSize = 18.sp, color = Color.Gray)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    sortedCategoryIds.forEach { catId ->
                        val categoryName = if (catId == null) "No Category"
                        else categories.find { it.id == catId }?.title ?: "No Category"
                        val categoryEntries = grouped[catId] ?: emptyList()
                        item {
                            Text(
                                text = categoryName,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Gray,
                                modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp)
                            )
                        }
                        categoryEntries.forEach { (masterTaskId, taskIntervals) ->
                            item {
                                masterTasks[masterTaskId]?.let { masterTask ->
                                    ScheduledTaskItem(
                                        db = db,
                                        masterTask = masterTask,
                                        intervals = taskIntervals,
                                        onClick = { onTaskClick(taskIntervals.first(), masterTask) }
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

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun ScheduledTaskItem(
    db: AppDatabase,
    masterTask: MasterTask,
    intervals: List<TaskInterval>,
    onClick: () -> Unit
) {
    var category by remember { mutableStateOf<Category?>(null) }
    var eventColor by remember { mutableStateOf<Color?>(null) }

    LaunchedEffect(masterTask.eventId, masterTask.categoryId) {
        if (masterTask.eventId != null) {
            val event = db.eventDao().getAllMasterEvents().find { it.id == masterTask.eventId }
            eventColor = event?.color?.let { Converters.toColor(it) }
                ?: event?.categoryId?.let { catId -> db.categoryDao().getAll().find { it.id == catId }?.color?.let { Converters.toColor(it) } }
        }
        category = masterTask.categoryId?.let { db.categoryDao().getCategoryById(it) }
    }

    val innerColor = eventColor ?: category?.let { Converters.toColor(it.color) } ?: Color.LightGray

    val dateFormatter = DateTimeFormatter.ofPattern("MMMM d, yyyy")
    val timeFormatter = DateTimeFormatter.ofPattern("h:mm a")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(CardColor))
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(INNER_CIRCLE_SIZE).clip(CircleShape).background(innerColor))
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = masterTask.title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color.Black,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(8.dp))
            intervals.sortedBy { it.intervalNo }.forEach { interval ->
                Text(
                    text = "${interval.startTime.format(timeFormatter)} - ${interval.endTime.format(timeFormatter)}, ${interval.occurDate.format(dateFormatter)}",
                    fontSize = 14.sp,
                    color = Color.Gray
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = Color.Gray,
            modifier = Modifier.size(20.dp)
        )
    }
}

/* TASK INFO PAGE */
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun TaskInfoPage(
    db: AppDatabase,
    task: MasterTask,
    onBack: () -> Unit,
    onUpdateDataReady: (UpdateFormData) -> Unit,
    onUpdate: () -> Unit,
    onPlay: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    var category by remember { mutableStateOf<Category?>(null) }
    var event by remember { mutableStateOf<MasterEvent?>(null) }
    var deadline by remember { mutableStateOf<Deadline?>(null) }
    var dependencyTask by remember { mutableStateOf<MasterTask?>(null) }
    var intervals by remember { mutableStateOf<List<TaskInterval>>(emptyList()) }
    var currentTask by remember { mutableStateOf(task) }
    var updateDataReady by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var estimatedPadding by remember { mutableIntStateOf(0) }

    LaunchedEffect(task.id) {
        currentTask = db.taskDao().getMasterTaskById(task.id) ?: task
        category = currentTask.categoryId?.let { db.categoryDao().getCategoryById(it) }
        event = currentTask.eventId?.let { db.eventDao().getMasterEventById(it) }
        deadline = currentTask.deadlineId?.let { db.deadlineDao().getDeadlineById(it) }
        dependencyTask = currentTask.dependencyTaskId?.let { db.taskDao().getMasterTaskById(it) }
        intervals = db.taskDao().getIntervalsForTask(currentTask.id).sortedBy { it.intervalNo }

        val settings = db.settingsDao().getAll()
        if (settings?.atiPaddingEnabled == true) {
            estimatedPadding = calculateTaskPadding(db, currentTask)
        }

        val categories = CategoryManager.getAll(db)
        val events = EventManager.getAll(db)
        val deadlines = DeadlineManager.getAll(db)
        val dependencyTasks = TaskManager.getAll(db).sortedBy { it.title }.filter {
            it.status == 1 && it.startDate == null && it.startTime == null && it.allDay == null && it.id != currentTask.id
        }
        val selectedCategory = currentTask.categoryId?.let { catId ->
            categories.indexOfFirst { it.id == catId }.takeIf { it >= 0 }
        }
        val selectedEvent = currentTask.eventId?.let { evId ->
            events.indexOfFirst { it.id == evId }.takeIf { it >= 0 }
        }
        val selectedDeadline = currentTask.deadlineId?.let { dlId ->
            deadlines.indexOfFirst { it.id == dlId }.takeIf { it >= 0 }
        }
        val selectedDependencyTask = currentTask.dependencyTaskId?.let { depId ->
            dependencyTasks.indexOfFirst { it.id == depId }.takeIf { it >= 0 }
        }
        onUpdateDataReady(
            UpdateFormData(
                categories = categories,
                events = events,
                deadlines = deadlines,
                dependencyTasks = dependencyTasks,
                selectedCategory = selectedCategory,
                selectedEvent = selectedEvent,
                selectedDeadline = selectedDeadline,
                selectedDependencyTask = selectedDependencyTask
            )
        )
        updateDataReady = true
    }

    val dateFormatter = DateTimeFormatter.ofPattern("MMMM d, yyyy")
    val timeFormatter = DateTimeFormatter.ofPattern("h:mm a")
    val isOtherTaskRunning = PomodoroState.activeTaskId != null && PomodoroState.activeTaskId != currentTask.id
    val isThisTaskRunning = PomodoroState.activeTaskId == currentTask.id && PomodoroState.isRunning

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "Back",
                tint = PrimaryColor,
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onBack() }
                    .size(40.dp)
            )
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (isOtherTaskRunning) Color.LightGray else PrimaryColor)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = !isOtherTaskRunning
                    ) { onPlay() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Start",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Column(modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(scrollState)) {
            Box(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
                Text(text = currentTask.title, fontSize = 20.sp, fontWeight = FontWeight.Medium)
            }

            if (!currentTask.notes.isNullOrBlank()) {
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Text(text = currentTask.notes!!, fontSize = 16.sp)
                }
                Spacer(modifier = Modifier.height(18.dp))
            } else {
                Spacer(modifier = Modifier.height(18.dp))
            }

            if (intervals.isNotEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    intervals.forEach { interval ->
                        var localPadding by remember { mutableIntStateOf(interval.atiPadding) }
                        var showEditDialog by remember { mutableStateOf(false) }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${interval.startTime.format(timeFormatter)} - ${interval.endTime.format(timeFormatter)}, ${interval.occurDate.format(dateFormatter)}",
                                fontSize = 16.sp,
                                color = Color.Gray
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(PrimaryColor.copy(alpha = 0.15f))
                                    .clickable { showEditDialog = true }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("+${localPadding}m pad", fontSize = 12.sp, color = PrimaryColor, fontWeight = FontWeight.Bold)
                            }
                        }

                        if (showEditDialog) {
                            var tempPad by remember { mutableStateOf(localPadding.toString()) }
                            AlertDialog(
                                onDismissRequest = { showEditDialog = false },
                                containerColor = BackgroundColor,
                                title = { Text("Edit Padding (minutes)", fontSize = 16.sp) },
                                text = {
                                    OutlinedTextField(
                                        value = tempPad,
                                        onValueChange = { tempPad = it },
                                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                                    )
                                },
                                confirmButton = {
                                    TextButton(onClick = {
                                        val newPad = tempPad.toIntOrNull() ?: localPadding
                                        localPadding = newPad
                                        showEditDialog = false
                                        scope.launch {
                                            db.taskDao().updateInterval(interval.copy(atiPadding = newPad))
                                        }
                                    }) { Text("Save", color = PrimaryColor) }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showEditDialog = false }) { Text("Cancel", color = Color.Gray) }
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(18.dp))
            }

            val hours = currentTask.predictedDuration / 60
            val minutes = currentTask.predictedDuration % 60
            val durationText = when {
                hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
                hours > 0 -> "${hours}h"
                else -> "${minutes}m"
            }
            InfoCard(listOf(
                "Duration" to durationText,
                "Auto Padding" to if (estimatedPadding > 0) "+${estimatedPadding}m" else "0m",
                "Prerequisite Task" to (dependencyTask?.title ?: "None"),
                "Deadline" to (deadline?.title ?: "None"),
                "Event" to (event?.title ?: "None"),
                "Category" to (category?.title ?: "None"),
                "Breakable" to (if (currentTask.breakable == true) "Yes" else "No")
            ))
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = { if (!isThisTaskRunning) showDeleteDialog = true },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isThisTaskRunning) Color.LightGray else Color.Gray
                ),
                contentPadding = PaddingValues(16.dp)
            ) {
                Text("Delete", fontSize = 16.sp, color = Color.White)
            }
            Button(
                onClick = { if (updateDataReady && !isThisTaskRunning) onUpdate() },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isThisTaskRunning || !updateDataReady) Color.LightGray else PrimaryColor
                ),
                contentPadding = PaddingValues(16.dp)
            ) {
                Text("Update", fontSize = 16.sp, color = Color.White)
            }
        }

        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                containerColor = BackgroundColor,
                title = null,
                text = { Text("Delete this task?", fontSize = 16.sp) },
                confirmButton = {},
                dismissButton = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                showDeleteDialog = false
                                scope.launch {
                                    TaskManager.delete(context, db, currentTask.id)
                                    onBack()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Gray),
                            contentPadding = PaddingValues(12.dp)
                        ) { Text("Delete", fontSize = 12.sp, color = Color.White) }
                        Button(
                            onClick = { showDeleteDialog = false },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor),
                            contentPadding = PaddingValues(12.dp)
                        ) { Text("Cancel", fontSize = 12.sp, color = Color.White) }
                    }
                }
            )
        }
    }
}

@Composable
fun InfoCard(fields: List<Pair<String, String>>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(CardColor), RoundedCornerShape(12.dp))
    ) {
        fields.forEachIndexed { index, (label, value) ->
            Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                Text(text = label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.Gray)
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = value, fontSize = 16.sp)
            }
            if (index < fields.lastIndex) {
                HorizontalDivider(thickness = 1.dp, color = Color.LightGray)
            }
        }
    }
}

/* TASK UPDATE FORM */
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun TaskUpdateForm(
    db: AppDatabase,
    task: MasterTask,
    preloadedData: UpdateFormData,
    onBack: () -> Unit,
    onSaveSuccess: (MasterTask) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var title by remember { mutableStateOf(task.title) }
    var notes by remember { mutableStateOf(task.notes ?: "") }
    var isBreakable by remember { mutableStateOf(task.breakable ?: false) }
    var isAutoSchedule by remember { mutableStateOf(task.startDate == null || task.startTime == null) }
    var startDate by remember { mutableStateOf(task.startDate) }
    var startTime by remember { mutableStateOf(task.startTime) }
    var durationHours by remember { mutableIntStateOf(task.predictedDuration / 60) }
    var durationMinutes by remember { mutableIntStateOf(task.predictedDuration % 60) }
    var breakableLockedByDuration by remember { mutableStateOf(false) }

    val categories = preloadedData.categories
    val events = preloadedData.events
    val deadlines = preloadedData.deadlines
    val dependencyTasks = preloadedData.dependencyTasks

    var selectedCategory by remember { mutableStateOf(preloadedData.selectedCategory) }
    var selectedEvent by remember { mutableStateOf(preloadedData.selectedEvent) }
    var selectedDeadline by remember { mutableStateOf(preloadedData.selectedDeadline) }
    var selectedDependencyTask by remember { mutableStateOf(preloadedData.selectedDependencyTask) }

    var resetTrigger by remember { mutableIntStateOf(0) }
    var showNotification by remember { mutableStateOf(false) }

    fun clearForm() {
        title = task.title
        notes = task.notes ?: ""
        isBreakable = task.breakable ?: false
        isAutoSchedule = task.startDate == null || task.startTime == null
        startDate = task.startDate
        startTime = task.startTime
        durationHours = task.predictedDuration / 60
        durationMinutes = task.predictedDuration % 60
        selectedCategory = preloadedData.selectedCategory
        selectedEvent = preloadedData.selectedEvent
        selectedDeadline = preloadedData.selectedDeadline
        selectedDependencyTask = preloadedData.selectedDependencyTask
        resetTrigger++
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundColor)
                .padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "Back",
                tint = PrimaryColor,
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onBack() }
                    .size(40.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))

            Column(modifier = Modifier.weight(1f).verticalScroll(scrollState)) {
                TaskForm(
                    db = db,
                    title = title,
                    onTitleChange = { title = it },
                    notes = notes,
                    onNotesChange = { notes = it },
                    isAllDay = false,
                    onAllDayChange = { },
                    allDayDate = java.time.LocalDate.now(),
                    onAllDayDateChange = { },
                    isBreakable = isBreakable,
                    onBreakableChange = { isBreakable = it },
                    isAutoSchedule = isAutoSchedule,
                    startDate = startDate,
                    startTime = startTime,
                    onScheduleChange = { auto, date, time ->
                        isAutoSchedule = auto
                        startDate = date
                        startTime = time
                    },
                    durationHours = durationHours,
                    durationMinutes = durationMinutes,
                    onDurationChange = { h, m ->
                        durationHours = h
                        durationMinutes = m
                    },
                    selectedCategory = selectedCategory,
                    onCategoryChange = { selectedCategory = it },
                    selectedEvent = selectedEvent,
                    onEventChange = { selectedEvent = it },
                    selectedDeadline = selectedDeadline,
                    onDeadlineChange = { selectedDeadline = it },
                    selectedDependencyTask = selectedDependencyTask,
                    onDependencyTaskChange = { selectedDependencyTask = it },
                    breakableLockedByDuration = breakableLockedByDuration,
                    onBreakableLockedByDurationChange = { breakableLockedByDuration = it },
                    resetTrigger = resetTrigger,
                    currentTaskId = task.id
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Button(
                        onClick = { clearForm() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Gray),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(16.dp)
                    ) {
                        Text("Reset", fontSize = 16.sp)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = {
                            if (title.isBlank()) {
                                scope.launch {
                                    showNotification = true
                                    delay(3000)
                                    showNotification = false
                                }
                                return@Button
                            }
                            scope.launch {
                                val durationInMinutes = (durationHours * 60) + durationMinutes
                                val updatedTask = task.copy(
                                    title = title,
                                    notes = notes.ifBlank { null },
                                    breakable = isBreakable,
                                    predictedDuration = durationInMinutes,
                                    startDate = if (isAutoSchedule) null else startDate,
                                    startTime = if (isAutoSchedule) null else startTime,
                                    categoryId = selectedCategory?.let { categories.getOrNull(it)?.id },
                                    eventId = selectedEvent?.let { events.getOrNull(it)?.id },
                                    deadlineId = selectedDeadline?.let { deadlines.getOrNull(it)?.id },
                                    dependencyTaskId = selectedDependencyTask?.let { dependencyTasks.getOrNull(it)?.id }
                                )
                                TaskManager.update(context, db, updatedTask)
                                val refreshedTask = db.taskDao().getMasterTaskById(task.id) ?: updatedTask
                                onSaveSuccess(refreshedTask)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(16.dp)
                    ) {
                        Text("Save", fontSize = 16.sp)
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showNotification,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            val dragOffset = remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
            Box(
                modifier = Modifier
                    .offset(y = dragOffset.floatValue.coerceAtMost(0f).dp)
                    .draggable(
                        orientation = androidx.compose.foundation.gestures.Orientation.Vertical,
                        state = androidx.compose.foundation.gestures.rememberDraggableState { delta ->
                            dragOffset.floatValue += delta
                            if (dragOffset.floatValue < -80f) showNotification = false
                        },
                        onDragStopped = { dragOffset.floatValue = 0f }
                    )
            ) {
                Surface(
                    color = PrimaryColor,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    shadowElevation = 8.dp,
                    shape = MaterialTheme.shapes.medium
                ) {
                    Box(modifier = Modifier.padding(16.dp), contentAlignment = Alignment.Center) {
                        Text("Title is required", color = BackgroundColor, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun PomodoroPage(
    db: AppDatabase,
    task: MasterTask,
    onBack: () -> Unit,
    onComplete: () -> Unit = onBack
) {
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var intervals by remember { mutableStateOf<List<TaskInterval>>(emptyList()) }
    var currentTask by remember { mutableStateOf(task) }
    var isCompleted by remember { mutableStateOf(false) }

    val breakEvery = SettingsManager.settings?.breakEvery ?: 30
    val breakDuration = SettingsManager.settings?.breakDuration ?: 5

    LaunchedEffect(Unit) {
        intervals = db.taskDao().getIntervalsForTask(task.id).sortedBy { it.intervalNo }
        currentTask = db.taskDao().getMasterTaskById(task.id) ?: task
        // If this task is already running in PomodoroState, restore — otherwise start fresh
        if (PomodoroState.activeTaskId != task.id) {
            PomodoroState.elapsedSeconds = 0
            PomodoroState.sessionSeconds = 0
        }
    }

    // Save progress to DB when navigating away while running (banner tap or back while running)
    DisposableEffect(Unit) {
        onDispose {
            if (PomodoroState.isRunning && !isCompleted) {
                kotlinx.coroutines.runBlocking {
                    updateTaskProgress(db, currentTask, intervals, PomodoroState.sessionSeconds / 60)
                }
            }
        }
    }

    val sessionMinutes = PomodoroState.sessionSeconds / 60
    val totalActualDuration = (currentTask.actualDuration ?: 0) + sessionMinutes
    val currentIntervalData = getCurrentIntervalData(intervals, totalActualDuration, currentTask)

    Column(modifier = Modifier.fillMaxSize().background(BackgroundColor).padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "Back",
                tint = PrimaryColor,
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onBack() }
                    .size(40.dp)
            )
        }

        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
                Text(text = currentTask.title, fontSize = 20.sp, fontWeight = FontWeight.Medium)
            }

            if (!currentTask.notes.isNullOrBlank()) {
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Text(text = currentTask.notes!!, fontSize = 16.sp)
                }
                Spacer(modifier = Modifier.height(36.dp))
            } else {
                Spacer(modifier = Modifier.height(24.dp))
            }

            PomodoroTimer(
                elapsedSeconds = PomodoroState.elapsedSeconds,
                isRunning = PomodoroState.isRunning,
                breakEveryMinutes = breakEvery,
                breakDurationMinutes = breakDuration
            )
            Spacer(modifier = Modifier.height(24.dp))

            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    Text(
                        text = "Current Interval: ${currentIntervalData.currentIntervalNo}/${currentTask.noIntervals}",
                        fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Color.Gray
                    )
                    Text(
                        text = "Time Left: ${formatDuration(currentIntervalData.timeLeft)}",
                        fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Color.Gray
                    )
                    Text(
                        text = "Overtime: ${formatDuration(currentIntervalData.overtime)}",
                        fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Color.Gray
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = {
                    scope.launch {
                        if (PomodoroState.isRunning) {
                            // PAUSE: save progress, clear PomodoroState
                            updateTaskProgress(db, currentTask, intervals, PomodoroState.sessionSeconds / 60)
                            PomodoroState.isRunning = false
                            PomodoroState.elapsedSeconds = 0
                            PomodoroState.sessionSeconds = 0
                            PomodoroState.activeTaskId = null
                            currentTask = db.taskDao().getMasterTaskById(task.id) ?: task
                            intervals = db.taskDao().getIntervalsForTask(task.id).sortedBy { it.intervalNo }
                        } else {
                            // START: mark task in-progress, set PomodoroState
                            if (currentTask.status == 1) {
                                intervals.firstOrNull()?.let { firstInterval ->
                                    db.taskDao().updateInterval(firstInterval.copy(status = 2))
                                }
                                db.taskDao().update(currentTask.copy(status = 2))
                                currentTask = currentTask.copy(status = 2)
                            }
                            PomodoroState.activeTaskId = task.id
                            PomodoroState.activeTaskTitle = currentTask.title
                            PomodoroState.isAllDay = false
                            PomodoroState.breakEvery = breakEvery
                            PomodoroState.breakDuration = breakDuration
                            PomodoroState.isRunning = true
                        }
                    }
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Gray),
                contentPadding = PaddingValues(16.dp)
            ) {
                Text(if (PomodoroState.isRunning) "Pause" else "Start", fontSize = 16.sp, color = Color.White)
            }

            Button(
                onClick = {
                    scope.launch {
                        if (PomodoroState.isRunning) {
                            updateTaskProgress(db, currentTask, intervals, PomodoroState.sessionSeconds / 60)
                        }
                        intervals.forEach { interval -> db.taskDao().deleteInterval(interval.id) }

                        // Check deadline miss at completion time
                        val missedDeadline = currentTask.deadlineId?.let { deadlineId ->
                            val deadline = db.deadlineDao().getDeadlineById(deadlineId)
                            val now = java.time.LocalDate.now()
                            val nowTime = java.time.LocalTime.now()
                            deadline?.let {
                                now.isAfter(it.date) ||
                                        (now == it.date && nowTime.isAfter(it.time))
                            }
                        } ?: false

                        db.taskDao().update(currentTask.copy(status = 3, noIntervals = 0, deadlineMissed = missedDeadline, completedAt = LocalDateTime.now()))
                        updateATIOnTaskComplete(db, currentTask)
                        isCompleted = true
                        PomodoroState.clear()
                        onComplete()
                    }
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor),
                contentPadding = PaddingValues(16.dp)
            ) {
                Text("Complete", fontSize = 16.sp, color = Color.White)
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun PomodoroTimer(
    elapsedSeconds: Int,
    isRunning: Boolean,
    breakEveryMinutes: Int,
    breakDurationMinutes: Int
) {
    val totalMinutes = elapsedSeconds / 60
    val seconds = elapsedSeconds % 60

    // Determine current phase
    val cycleLength = breakEveryMinutes + breakDurationMinutes
    val positionInCycle = totalMinutes % cycleLength
    val isBreak = positionInCycle >= breakEveryMinutes

    // Seconds elapsed within current phase
    val phaseMinutes = if (isBreak) positionInCycle - breakEveryMinutes else positionInCycle
    val phaseSeconds = phaseMinutes * 60 + seconds
    val phaseDurationSeconds = if (isBreak) breakDurationMinutes * 60 else breakEveryMinutes * 60

    // Progress sweeps full circle over the current phase duration
    val progress = (phaseSeconds.toFloat() / phaseDurationSeconds.toFloat()).coerceIn(0f, 1f)

    // Countdown display
    val remainingSeconds = maxOf(0, phaseDurationSeconds - phaseSeconds)
    val displayHours = remainingSeconds / 3600
    val displayMinutes = (remainingSeconds % 3600) / 60
    val displaySeconds = remainingSeconds % 60

    Box(modifier = Modifier.size(280.dp), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            drawArc(
                color = Color.Gray,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 12.dp.toPx())
            )
        }
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            drawArc(
                color = BackgroundColor,
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 14.dp.toPx())
            )
        }
        Box(
            modifier = Modifier.size(230.dp).clip(CircleShape)
                .background(if (isRunning) PrimaryColor else Color.LightGray),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (isBreak) "Break" else "Task",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = String.format(Locale.US, "%02d:%02d:%02d", displayHours, displayMinutes, displaySeconds),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

/* ALL-DAY TASKS LIST */
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun AllDayTasksList(
    db: AppDatabase,
    onBack: () -> Unit,
    onTaskClick: (MasterTask) -> Unit
) {
    var tasks by remember { mutableStateOf<List<MasterTask>>(emptyList()) }
    var categories by remember { mutableStateOf<List<Category>>(emptyList()) }

    LaunchedEffect(Unit) {
        tasks = db.taskDao().getAllMasterTasks().filter { it.allDay != null && it.status != 3 }
        categories = CategoryManager.getAll(db)
    }

    val grouped = tasks.groupBy { it.categoryId }
    val sortedCategoryIds = categories.map { it.id as Int? }.filter { grouped.containsKey(it) } +
            if (grouped.containsKey(null)) listOf(null) else emptyList()

    Column(modifier = Modifier.fillMaxSize().background(BackgroundColor)) {
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

        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            Text("All-Day Tasks", fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp, top = 4.dp))

            if (tasks.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = "No all-day tasks", fontSize = 18.sp, color = Color.Gray)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    sortedCategoryIds.forEach { catId ->
                        val categoryName = if (catId == null) "No Category"
                        else categories.find { it.id == catId }?.title ?: "No Category"
                        val categoryTasks = grouped[catId] ?: emptyList()
                        item {
                            Text(
                                text = categoryName,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Gray,
                                modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp)
                            )
                        }
                        items(categoryTasks) { task ->
                            AllDayTaskItem(db = db, task = task, onClick = { onTaskClick(task) })
                        }
                    }
                }
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun AllDayTaskItem(
    db: AppDatabase,
    task: MasterTask,
    onClick: () -> Unit
) {
    var category by remember { mutableStateOf<Category?>(null) }
    var eventColor by remember { mutableStateOf<Color?>(null) }
    val dateFormatter = DateTimeFormatter.ofPattern("MMMM d, yyyy")

    LaunchedEffect(task.eventId, task.categoryId) {
        if (task.eventId != null) {
            val event = db.eventDao().getAllMasterEvents().find { it.id == task.eventId }
            eventColor = event?.color?.let { Converters.toColor(it) }
                ?: event?.categoryId?.let { catId -> db.categoryDao().getAll().find { it.id == catId }?.color?.let { Converters.toColor(it) } }
        }
        category = task.categoryId?.let { db.categoryDao().getCategoryById(it) }
    }

    val circleColor = eventColor ?: category?.let { Converters.toColor(it.color) } ?: Color.LightGray

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(CardColor))
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(INNER_CIRCLE_SIZE).clip(CircleShape).background(circleColor))
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = task.title, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = task.allDay?.format(dateFormatter) ?: "",
                fontSize = 14.sp,
                color = Color.Gray
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = Color.Gray,
            modifier = Modifier.size(20.dp)
        )
    }
}

/* ALL-DAY TASK INFO PAGE */
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun AllDayTaskInfoPage(
    db: AppDatabase,
    task: MasterTask,
    onBack: () -> Unit,
    onUpdate: () -> Unit,
    onPlay: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val dateFormatter = DateTimeFormatter.ofPattern("MMMM d, yyyy")

    var currentTask by remember { mutableStateOf(task) }
    var category by remember { mutableStateOf<Category?>(null) }
    var event by remember { mutableStateOf<MasterEvent?>(null) }
    var deadline by remember { mutableStateOf<Deadline?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(task.id) {
        currentTask = db.taskDao().getMasterTaskById(task.id) ?: task
        category = currentTask.categoryId?.let { db.categoryDao().getCategoryById(it) }
        event = currentTask.eventId?.let { db.eventDao().getMasterEventById(it) }
        deadline = currentTask.deadlineId?.let { db.deadlineDao().getDeadlineById(it) }
    }

    val isOtherTaskRunning2 = PomodoroState.activeTaskId != null && PomodoroState.activeTaskId != currentTask.id
    val isThisTaskRunning2 = PomodoroState.activeTaskId == currentTask.id && PomodoroState.isRunning

    Column(
        modifier = Modifier.fillMaxSize().background(BackgroundColor).padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "Back",
                tint = PrimaryColor,
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onBack() }
                    .size(40.dp)
            )
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (isOtherTaskRunning2) Color.LightGray else PrimaryColor)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = !isOtherTaskRunning2
                    ) { onPlay() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Start",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Column(modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(scrollState)) {
            Box(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
                Text(text = currentTask.title, fontSize = 20.sp, fontWeight = FontWeight.Medium)
            }

            if (!currentTask.notes.isNullOrBlank()) {
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Text(text = currentTask.notes!!, fontSize = 16.sp)
                }
                Spacer(modifier = Modifier.height(18.dp))
            } else {
                Spacer(modifier = Modifier.height(18.dp))
            }

            InfoCard(listOf(
                "Date" to (currentTask.allDay?.format(dateFormatter) ?: ""),
                "Deadline" to (deadline?.title ?: "None"),
                "Event" to (event?.title ?: "None"),
                "Category" to (category?.title ?: "None")
            ))
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = { if (!isThisTaskRunning2) showDeleteDialog = true },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isThisTaskRunning2) Color.LightGray else Color.Gray
                ),
                contentPadding = PaddingValues(16.dp)
            ) {
                Text("Delete", fontSize = 16.sp, color = Color.White)
            }
            Button(
                onClick = { if (!isThisTaskRunning2) onUpdate() },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isThisTaskRunning2) Color.LightGray else PrimaryColor
                ),
                contentPadding = PaddingValues(16.dp)
            ) {
                Text("Update", fontSize = 16.sp, color = Color.White)
            }
        }

        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                containerColor = BackgroundColor,
                title = null,
                text = { Text("Delete this task?", fontSize = 16.sp) },
                confirmButton = {},
                dismissButton = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                showDeleteDialog = false
                                scope.launch {
                                    TaskManager.delete(context, db, currentTask.id)
                                    onBack()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Gray),
                            contentPadding = PaddingValues(12.dp)
                        ) { Text("Delete", fontSize = 12.sp, color = Color.White) }
                        Button(
                            onClick = { showDeleteDialog = false },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor),
                            contentPadding = PaddingValues(12.dp)
                        ) { Text("Cancel", fontSize = 12.sp, color = Color.White) }
                    }
                }
            )
        }
    }
}

/* ALL-DAY TASK UPDATE FORM */
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun AllDayTaskUpdateForm(
    db: AppDatabase,
    task: MasterTask,
    onBack: () -> Unit,
    onSaveSuccess: (MasterTask) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var title by remember { mutableStateOf(task.title) }
    var notes by remember { mutableStateOf(task.notes ?: "") }
    var allDayDate by remember { mutableStateOf(task.allDay ?: java.time.LocalDate.now()) }
    var selectedCategory by remember { mutableStateOf<Int?>(null) }
    var selectedEvent by remember { mutableStateOf<Int?>(null) }
    var selectedDeadline by remember { mutableStateOf<Int?>(null) }
    var categories by remember { mutableStateOf<List<Category>>(emptyList()) }
    var events by remember { mutableStateOf<List<MasterEvent>>(emptyList()) }
    var deadlines by remember { mutableStateOf<List<Deadline>>(emptyList()) }
    var resetTrigger by remember { mutableIntStateOf(0) }
    var showNotification by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        categories = CategoryManager.getAll(db)
        events = EventManager.getAll(db)
        deadlines = DeadlineManager.getAll(db)
        selectedCategory = task.categoryId?.let { catId -> categories.indexOfFirst { it.id == catId }.takeIf { it >= 0 } }
        selectedEvent = task.eventId?.let { evId -> events.indexOfFirst { it.id == evId }.takeIf { it >= 0 } }
        selectedDeadline = task.deadlineId?.let { dlId -> deadlines.indexOfFirst { it.id == dlId }.takeIf { it >= 0 } }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().background(BackgroundColor).padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "Back",
                tint = PrimaryColor,
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onBack() }
                    .size(40.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Column(modifier = Modifier.weight(1f).verticalScroll(scrollState)) {
                AllDayTaskForm(
                    title = title,
                    onTitleChange = { title = it },
                    notes = notes,
                    onNotesChange = { notes = it },
                    allDayDate = allDayDate,
                    onAllDayDateChange = { allDayDate = it },
                    selectedCategory = selectedCategory,
                    onCategoryChange = { selectedCategory = it },
                    selectedEvent = selectedEvent,
                    onEventChange = { selectedEvent = it },
                    selectedDeadline = selectedDeadline,
                    onDeadlineChange = { selectedDeadline = it },
                    categories = categories,
                    events = events,
                    deadlines = deadlines,
                    resetTrigger = resetTrigger
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Button(
                        onClick = {
                            title = task.title
                            notes = task.notes ?: ""
                            allDayDate = task.allDay ?: java.time.LocalDate.now()
                            resetTrigger++
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Gray),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(16.dp)
                    ) {
                        Text("Reset", fontSize = 16.sp)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = {
                            if (title.isBlank()) {
                                scope.launch {
                                    showNotification = true
                                    delay(3000)
                                    showNotification = false
                                }
                                return@Button
                            }
                            scope.launch {
                                val updatedTask = task.copy(
                                    title = title,
                                    notes = notes.ifBlank { null },
                                    allDay = allDayDate,
                                    categoryId = selectedCategory?.let { categories.getOrNull(it)?.id },
                                    eventId = selectedEvent?.let { events.getOrNull(it)?.id },
                                    deadlineId = selectedDeadline?.let { deadlines.getOrNull(it)?.id }
                                )
                                TaskManager.update(context, db, updatedTask)
                                val refreshed = db.taskDao().getMasterTaskById(task.id) ?: updatedTask
                                onSaveSuccess(refreshed)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(16.dp)
                    ) {
                        Text("Save", fontSize = 16.sp)
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showNotification,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            val dragOffset = remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
            Box(
                modifier = Modifier
                    .offset(y = dragOffset.floatValue.coerceAtMost(0f).dp)
                    .draggable(
                        orientation = androidx.compose.foundation.gestures.Orientation.Vertical,
                        state = androidx.compose.foundation.gestures.rememberDraggableState { delta ->
                            dragOffset.floatValue += delta
                            if (dragOffset.floatValue < -80f) showNotification = false
                        },
                        onDragStopped = { dragOffset.floatValue = 0f }
                    )
            ) {
                Surface(
                    color = PrimaryColor,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    shadowElevation = 8.dp,
                    shape = MaterialTheme.shapes.medium
                ) {
                    Box(modifier = Modifier.padding(16.dp), contentAlignment = Alignment.Center) {
                        Text("Title is required", color = BackgroundColor, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/* ALL-DAY TASK FORM (inline, used by update form) */
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun AllDayTaskForm(
    title: String,
    onTitleChange: (String) -> Unit,
    notes: String,
    onNotesChange: (String) -> Unit,
    allDayDate: java.time.LocalDate,
    onAllDayDateChange: (java.time.LocalDate) -> Unit,
    selectedCategory: Int?,
    onCategoryChange: (Int?) -> Unit,
    selectedEvent: Int?,
    onEventChange: (Int?) -> Unit,
    selectedDeadline: Int?,
    onDeadlineChange: (Int?) -> Unit,
    categories: List<Category>,
    events: List<MasterEvent>,
    deadlines: List<Deadline>,
    resetTrigger: Int
) {
    Column {
        val titleValue = textInputField(label = "Title", initialValue = title, key = resetTrigger)
        onTitleChange(titleValue)
        Spacer(modifier = Modifier.height(12.dp))

        val notesValue = notesInputField(label = "Notes", initialValue = notes, key = resetTrigger)
        onNotesChange(notesValue)
        Spacer(modifier = Modifier.height(12.dp))

        val dateValue = datePickerField(
            label = "Date",
            initialDate = allDayDate,
            key = resetTrigger
        )
        dateValue?.let { onAllDayDateChange(it) }
        Spacer(modifier = Modifier.height(12.dp))

        val deadlineValue = dropdownField(
            label = "Deadline",
            items = deadlines.map { it.title },
            initialSelection = selectedDeadline,
            key = resetTrigger
        )
        if (deadlineValue != selectedDeadline) onDeadlineChange(deadlineValue)
        Spacer(modifier = Modifier.height(12.dp))

        val eventValue = dropdownField(
            label = "Event",
            items = events.map { it.title },
            initialSelection = selectedEvent,
            key = resetTrigger,
            locked = selectedDeadline != null
        )
        if (selectedDeadline == null && eventValue != selectedEvent) onEventChange(eventValue)
        Spacer(modifier = Modifier.height(12.dp))

        val categoryValue = dropdownField(
            label = "Category",
            items = categories.map { it.title },
            initialSelection = selectedCategory,
            key = resetTrigger,
            locked = selectedEvent != null || selectedDeadline != null
        )
        if (selectedEvent == null && selectedDeadline == null && categoryValue != selectedCategory) onCategoryChange(categoryValue)
    }
}

/* ALL-DAY POMODORO PAGE */
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun AllDayPomodoroPage(
    db: AppDatabase,
    task: MasterTask,
    onBack: () -> Unit,
    onComplete: () -> Unit = onBack
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    var currentTask by remember { mutableStateOf(task) }
    var isCompleted by remember { mutableStateOf(false) }

    val breakEvery = SettingsManager.settings?.breakEvery ?: 30
    val breakDuration = SettingsManager.settings?.breakDuration ?: 5

    LaunchedEffect(Unit) {
        currentTask = db.taskDao().getMasterTaskById(task.id) ?: task
        // If this task is not the active one, start fresh
        if (PomodoroState.activeTaskId != task.id) {
            PomodoroState.elapsedSeconds = 0
            PomodoroState.sessionSeconds = 0
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (PomodoroState.isRunning && !isCompleted) {
                kotlinx.coroutines.runBlocking {
                    val sessionMinutes = PomodoroState.sessionSeconds / 60
                    val newActualDuration = (currentTask.actualDuration ?: 0) + sessionMinutes
                    db.taskDao().update(currentTask.copy(actualDuration = newActualDuration, status = 2))
                }
            }
        }
    }

    val totalWorkedMinutes = ((currentTask.actualDuration ?: 0) + (PomodoroState.sessionSeconds / 60))
    val workedHours = totalWorkedMinutes / 60
    val workedMins = totalWorkedMinutes % 60

    Column(modifier = Modifier.fillMaxSize().background(BackgroundColor).padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "Back",
                tint = PrimaryColor,
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onBack() }
                    .size(40.dp)
            )
        }

        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
                Text(text = currentTask.title, fontSize = 20.sp, fontWeight = FontWeight.Medium)
            }

            if (!currentTask.notes.isNullOrBlank()) {
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Text(text = currentTask.notes!!, fontSize = 16.sp)
                }
                Spacer(modifier = Modifier.height(24.dp))
            } else {
                Spacer(modifier = Modifier.height(24.dp))
            }

            PomodoroTimer(
                elapsedSeconds = PomodoroState.elapsedSeconds,
                isRunning = PomodoroState.isRunning,
                breakEveryMinutes = breakEvery,
                breakDurationMinutes = breakDuration
            )
            Spacer(modifier = Modifier.height(24.dp))

            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Time Worked: ${if (workedHours > 0) "${workedHours}h " else ""}${workedMins}m",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.Gray
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = {
                    scope.launch {
                        if (PomodoroState.isRunning) {
                            // PAUSE: save progress, clear PomodoroState
                            val sessionMinutes = PomodoroState.sessionSeconds / 60
                            val newActualDuration = (currentTask.actualDuration ?: 0) + sessionMinutes
                            db.taskDao().update(currentTask.copy(actualDuration = newActualDuration, status = 2))
                            PomodoroState.isRunning = false
                            PomodoroState.elapsedSeconds = 0
                            PomodoroState.sessionSeconds = 0
                            PomodoroState.activeTaskId = null
                            currentTask = db.taskDao().getMasterTaskById(task.id) ?: currentTask
                        } else {
                            // START
                            if (currentTask.status == 1) {
                                db.taskDao().update(currentTask.copy(status = 2))
                                currentTask = currentTask.copy(status = 2)
                            }
                            PomodoroState.activeTaskId = task.id
                            PomodoroState.activeTaskTitle = currentTask.title
                            PomodoroState.isAllDay = true
                            PomodoroState.breakEvery = breakEvery
                            PomodoroState.breakDuration = breakDuration
                            PomodoroState.isRunning = true
                        }
                    }
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Gray),
                contentPadding = PaddingValues(16.dp)
            ) {
                Text(if (PomodoroState.isRunning) "Pause" else "Start", fontSize = 16.sp, color = Color.White)
            }

            Button(
                onClick = {
                    scope.launch {
                        val completedTask = if (PomodoroState.isRunning) {
                            val sessionMinutes = PomodoroState.sessionSeconds / 60
                            val newActualDuration = (currentTask.actualDuration ?: 0) + sessionMinutes
                            currentTask.copy(actualDuration = newActualDuration, status = 3, completedAt = LocalDateTime.now())
                        } else {
                            currentTask.copy(status = 3, completedAt = LocalDateTime.now())
                        }
                        db.taskDao().update(completedTask)
                        updateATIOnTaskComplete(db, completedTask)
                        onTaskDeleted(context, db, completedTask.id)
                        db.taskDao().deleteMasterTask(completedTask.id)
                        isCompleted = true
                        PomodoroState.clear()
                        onComplete()
                    }
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor),
                contentPadding = PaddingValues(16.dp)
            ) {
                Text("Complete", fontSize = 16.sp, color = Color.White)
            }
        }
    }
}

/* HELPER FUNCTIONS */

data class IntervalData(
    val currentIntervalNo: Int,
    val timeLeft: Int,
    val overtime: Int
)

@RequiresApi(Build.VERSION_CODES.O)
fun getCurrentIntervalData(
    intervals: List<TaskInterval>,
    totalActualDuration: Int,
    task: MasterTask
): IntervalData {
    if (intervals.isEmpty()) return IntervalData(0, 0, 0)

    var cumulativeDuration = 0
    var currentInterval: TaskInterval? = null

    for (interval in intervals) {
        val intervalDuration = calculateIntervalDuration(interval)
        if (totalActualDuration <= cumulativeDuration + intervalDuration) {
            currentInterval = interval
            break
        }
        cumulativeDuration += intervalDuration
    }

    if (currentInterval == null) {
        return IntervalData(
            currentIntervalNo = intervals.last().intervalNo,
            timeLeft = 0,
            overtime = if (task.predictedDuration < totalActualDuration) totalActualDuration - task.predictedDuration else 0
        )
    }

    val intervalDuration = calculateIntervalDuration(currentInterval) - currentInterval.atiPadding
    val durationIntoInterval = totalActualDuration - cumulativeDuration
    val timeLeft = maxOf(0, intervalDuration - durationIntoInterval)
    val overtime = if (currentInterval.intervalNo == task.noIntervals && task.predictedDuration < totalActualDuration) {
        totalActualDuration - task.predictedDuration
    } else 0

    return IntervalData(
        currentIntervalNo = currentInterval.intervalNo,
        timeLeft = timeLeft,
        overtime = overtime
    )
}

@RequiresApi(Build.VERSION_CODES.O)
fun calculateIntervalDuration(interval: TaskInterval): Int {
    val startMinutes = interval.startTime.hour * 60 + interval.startTime.minute
    val endMinutes = interval.endTime.hour * 60 + interval.endTime.minute
    return endMinutes - startMinutes
}

@RequiresApi(Build.VERSION_CODES.O)
suspend fun updateTaskProgress(
    db: AppDatabase,
    task: MasterTask,
    intervals: List<TaskInterval>,
    elapsedMinutes: Int
) {
    val newActualDuration = (task.actualDuration ?: 0) + elapsedMinutes
    val newTimeLeft = maxOf(0, task.predictedDuration - newActualDuration)
    val overtime = if (newActualDuration > task.predictedDuration) newActualDuration - task.predictedDuration else 0

    db.taskDao().update(task.copy(actualDuration = newActualDuration, timeLeft = newTimeLeft, overTime = overtime))

    var cumulativeDuration = 0
    for (interval in intervals.sortedBy { it.intervalNo }) {
        val intervalDuration = calculateIntervalDuration(interval) - interval.atiPadding

        if (newActualDuration >= cumulativeDuration + intervalDuration) {
            if (interval.intervalNo < task.noIntervals) {
                db.taskDao().deleteInterval(interval.id)
            } else {
                val intervalOvertime = newActualDuration - cumulativeDuration - intervalDuration
                db.taskDao().updateInterval(interval.copy(status = 2, timeLeft = 0, overTime = intervalOvertime))
            }
        } else {
            val durationIntoInterval = newActualDuration - cumulativeDuration
            val intervalTimeLeft = maxOf(0, intervalDuration - durationIntoInterval)
            db.taskDao().updateInterval(interval.copy(status = 2, timeLeft = intervalTimeLeft, overTime = 0))
        }

        cumulativeDuration += intervalDuration
    }
}

fun formatDuration(minutes: Int): String {
    val hours = minutes / 60
    val mins = minutes % 60
    return if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
}