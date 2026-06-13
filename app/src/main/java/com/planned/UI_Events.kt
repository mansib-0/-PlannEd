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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

data class EventUpdateFormData(
    val categories: List<Category>,
    val selectedCategory: Int?
)

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun Events(db: AppDatabase) {
    when (eventsCurrentView) {
        "list" -> EventsList(
            db = db,
            onEventClick = { event ->
                eventsSelectedEvent = event
                eventsCurrentView = "info"
            }
        )
        "info" -> eventsSelectedEvent?.let { event ->
            EventInfoPage(
                db = db,
                event = event,
                occurrence = null,
                onBack = {
                    eventsCurrentView = "list"
                    eventsSelectedEvent = null
                    eventsUpdateFormData = null
                },
                onUpdateDataReady = { data -> eventsUpdateFormData = data },
                onUpdate = { eventsCurrentView = "update" },
                eventReturnScreen = "Events"
            )
        }
        "update" -> eventsSelectedEvent?.let { event ->
            eventsUpdateFormData?.let { formData ->
                EventUpdateForm(
                    db = db,
                    event = event,
                    preloadedData = formData,
                    onBack = { eventsCurrentView = "info" },
                    onSaveSuccess = { updatedEvent ->
                        eventsSelectedEvent = updatedEvent
                        eventsUpdateFormData = null
                        eventsCurrentView = "info"
                    }
                )
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun EventsList(
    db: AppDatabase,
    onEventClick: (MasterEvent) -> Unit
) {
    var events by remember { mutableStateOf<List<MasterEvent>>(emptyList()) }
    var categories by remember { mutableStateOf<List<Category>>(emptyList()) }

    LaunchedEffect(Unit) {
        events = EventManager.getAll(db)
        categories = CategoryManager.getAll(db)
    }

    val grouped = events.groupBy { it.categoryId }
    val sortedCategoryIds = categories.map { it.id as Int? }.filter { grouped.containsKey(it) } +
            if (grouped.containsKey(null)) listOf(null) else emptyList()

    Column(modifier = Modifier.fillMaxSize().background(BackgroundColor).padding(16.dp)) {
        Text("Events", fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp, top = 4.dp))

        if (events.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "No events", fontSize = 18.sp, color = Color.Gray)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                sortedCategoryIds.forEach { catId ->
                    val categoryName = if (catId == null) "No Category"
                    else categories.find { it.id == catId }?.title ?: "No Category"
                    val categoryEvents = grouped[catId] ?: emptyList()
                    item {
                        Text(
                            text = categoryName,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Gray,
                            modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp)
                        )
                    }
                    items(categoryEvents) { event ->
                        EventListItem(db = db, event = event, categories = categories, onClick = { onEventClick(event) })
                    }
                }
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun EventListItem(
    db: AppDatabase,
    event: MasterEvent,
    categories: List<Category>,
    onClick: () -> Unit
) {
    val eventColor = when {
        event.color != null -> Converters.toColor(event.color)
        event.categoryId != null -> {
            categories.find { it.id == event.categoryId }?.color?.let { Converters.toColor(it) } ?: Preset1
        }
        else -> Preset1
    }

    val timeFormatter = java.time.format.DateTimeFormatter.ofPattern("h:mm a")
    val dateFormatter = java.time.format.DateTimeFormatter.ofPattern("MMMM d, yyyy")
    val timeText = "${event.startTime.format(timeFormatter)} - ${event.endTime.format(timeFormatter)}"
    val recurrenceText = when (event.recurFreq) {
        RecurrenceFrequency.NONE -> event.startDate.format(dateFormatter)
        RecurrenceFrequency.DAILY -> "Daily"
        RecurrenceFrequency.WEEKLY -> "Weekly"
        RecurrenceFrequency.MONTHLY -> "Monthly"
        RecurrenceFrequency.YEARLY -> "Yearly"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(CardColor))
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(INNER_CIRCLE_SIZE).background(eventColor, CircleShape))
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = event.title, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "$timeText, $recurrenceText", fontSize = 14.sp, color = Color.Gray)
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

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun EventInfoPage(
    db: AppDatabase,
    event: MasterEvent,
    occurrence: EventOccurrence? = null,
    onBack: () -> Unit,
    onUpdateDataReady: (EventUpdateFormData) -> Unit,
    onUpdate: () -> Unit,
    eventReturnScreen: String = "Calendars"
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    var category by remember { mutableStateOf<Category?>(null) }
    var currentEvent by remember { mutableStateOf(event) }
    var relatedTasks by remember { mutableStateOf<List<MasterTask>>(emptyList()) }
    var updateDataReady by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val dateFormatter = java.time.format.DateTimeFormatter.ofPattern("MMMM d, yyyy")
    val timeFormatter = java.time.format.DateTimeFormatter.ofPattern("h:mm a")

    LaunchedEffect(event.id) {
        currentEvent = db.eventDao().getMasterEventById(event.id) ?: event
        category = currentEvent.categoryId?.let { db.categoryDao().getCategoryById(it) }
        relatedTasks = db.taskDao().getAllMasterTasks().filter { it.eventId == event.id && it.status != 3 }

        // Preload update form data
        val categories = CategoryManager.getAll(db)
        val selectedCategory = currentEvent.categoryId?.let { catId ->
            categories.indexOfFirst { it.id == catId }.takeIf { it >= 0 }
        }
        onUpdateDataReady(EventUpdateFormData(categories = categories, selectedCategory = selectedCategory))
        updateDataReady = true
    }

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

        Column(modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(scrollState)) {
            Box(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
                Text(text = currentEvent.title, fontSize = 20.sp, fontWeight = FontWeight.Medium)
            }

            if (!currentEvent.notes.isNullOrBlank()) {
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Text(text = currentEvent.notes!!, fontSize = 16.sp)
                }
                Spacer(modifier = Modifier.height(18.dp))
            }

            if (occurrence != null) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "${occurrence.startTime.format(timeFormatter)} - ${occurrence.endTime.format(timeFormatter)}, ${occurrence.occurDate.format(dateFormatter)}",
                        fontSize = 16.sp,
                        color = Color.Gray
                    )
                }
                Spacer(modifier = Modifier.height(18.dp))
            }

            InfoCard(listOf(
                "Start Date" to currentEvent.startDate.format(dateFormatter),
                "End Date" to (currentEvent.endDate?.format(dateFormatter) ?: "N/A"),
                "Start Time" to currentEvent.startTime.format(timeFormatter),
                "End Time" to currentEvent.endTime.format(timeFormatter),
                "Recurrence" to (currentEvent.recurFreq.name.lowercase().replaceFirstChar { it.uppercase() } +
                        when (currentEvent.recurFreq) {
                            RecurrenceFrequency.WEEKLY -> currentEvent.recurRule.daysOfWeek?.sorted()?.joinToString(", ") { d ->
                                when (d) { 1 -> "Mo"; 2 -> "Tu"; 3 -> "We"; 4 -> "Th"; 5 -> "Fr"; 6 -> "Sa"; 7 -> "Su"; else -> "" }
                            }?.let { " ($it)" } ?: ""
                            RecurrenceFrequency.MONTHLY -> currentEvent.recurRule.daysOfMonth?.sorted()?.joinToString(", ")?.let { " ($it)" } ?: ""
                            RecurrenceFrequency.YEARLY -> currentEvent.recurRule.monthAndDay?.let { " (${java.time.Month.of(it.second).getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault())} ${it.first})" } ?: ""
                            else -> ""
                        }),
                "Category" to (category?.title ?: "None")
            ))

            Spacer(modifier = Modifier.height(18.dp))

            // Related tasks section
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
                Text(
                    text = if (relatedTasks.isEmpty()) "No Related Tasks" else "Related Tasks",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                )
                if (relatedTasks.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        relatedTasks.forEach { task ->
                            val taskColor = remember(task) {
                                runBlocking {
                                    when {
                                        task.eventId != null -> {
                                            val event = db.eventDao().getAllMasterEvents().find { it.id == task.eventId }
                                            event?.color?.let { Converters.toColor(it) }
                                                ?: event?.categoryId?.let { catId -> db.categoryDao().getAll().find { it.id == catId }?.color?.let { Converters.toColor(it) } }
                                                ?: Color.Gray
                                        }
                                        task.categoryId != null -> {
                                            val cat = db.categoryDao().getCategoryById(task.categoryId)
                                            cat?.color?.let { Converters.toColor(it) } ?: Color.Gray
                                        }
                                        else -> Color.Gray
                                    }
                                }
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(CardColor))
                                    .clickable {
                                        com.planned.eventsCurrentView = "list"
                                        com.planned.eventsSelectedEvent = null
                                        com.planned.selectedEventForInfo = currentEvent
                                        com.planned.eventInfoReturnScreen = eventReturnScreen
                                        com.planned.selectedTaskForInfo = task
                                        com.planned.taskInfoReturnScreen = "EventInfo"
                                        com.planned.currentScreen = "TaskInfo"
                                    }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(INNER_CIRCLE_SIZE)
                                        .clip(CircleShape)
                                        .background(taskColor)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = task.title,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Normal,
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
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = { showDeleteDialog = true },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Gray),
                contentPadding = PaddingValues(16.dp)
            ) { Text("Delete", fontSize = 16.sp, color = Color.White) }
            Button(
                onClick = { if (updateDataReady) onUpdate() },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = if (updateDataReady) PrimaryColor else Color.LightGray),
                contentPadding = PaddingValues(16.dp)
            ) { Text("Update", fontSize = 16.sp, color = Color.White) }
        }

        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                containerColor = BackgroundColor,
                title = null,
                text = { Text("Delete this event?", fontSize = 16.sp) },
                confirmButton = {},
                dismissButton = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (occurrence != null && currentEvent.recurFreq != RecurrenceFrequency.NONE) {
                            Button(
                                onClick = {
                                    showDeleteDialog = false
                                    scope.launch {
                                        db.eventDao().deleteOccurrence(occurrence.id)
                                        onTaskChanged(context, db)
                                        onBack()
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Gray),
                                contentPadding = PaddingValues(12.dp)
                            ) { Text("Delete This", fontSize = 12.sp, color = Color.White) }
                        }

                        Button(
                            onClick = {
                                showDeleteDialog = false
                                scope.launch {
                                    EventManager.delete(context, db, currentEvent.id)
                                    onBack()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Gray),
                            contentPadding = PaddingValues(12.dp)
                        ) { Text(if (currentEvent.recurFreq != RecurrenceFrequency.NONE) "Delete All" else "Delete", fontSize = 12.sp, color = Color.White) }

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

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun EventUpdateForm(
    db: AppDatabase,
    event: MasterEvent,
    preloadedData: EventUpdateFormData,
    onBack: () -> Unit,
    onSaveSuccess: (MasterEvent) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var title by remember { mutableStateOf(event.title) }
    var notes by remember { mutableStateOf(event.notes ?: "") }
    var color by remember {
        mutableStateOf(
            event.color?.let { Converters.toColor(it) }
                ?: preloadedData.categories.find { it.id == event.categoryId }?.color?.let { Converters.toColor(it) }
                ?: Preset1
        )
    }
    var startDate by remember { mutableStateOf(event.startDate) }
    var endDate by remember { mutableStateOf(event.endDate) }
    var startTime by remember { mutableStateOf(event.startTime) }
    var endTime by remember { mutableStateOf(event.endTime) }
    var recurrenceFreq by remember { mutableStateOf(event.recurFreq) }
    var selectedDaysOfWeek by remember { mutableStateOf(event.recurRule.daysOfWeek?.toSet() ?: setOf(7)) }
    var selectedDaysOfMonth by remember { mutableStateOf(event.recurRule.daysOfMonth?.toSet() ?: setOf(1)) }

    val categories = preloadedData.categories
    var selectedCategory by remember { mutableStateOf(preloadedData.selectedCategory) }
    var resetTrigger by remember { mutableIntStateOf(0) }
    var showNotification by remember { mutableStateOf(false) }
    var notificationMessage by remember { mutableStateOf("") }

    fun clearForm() {
        title = event.title
        notes = event.notes ?: ""
        selectedCategory = preloadedData.selectedCategory
        resetTrigger++
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
                EventForm(
                    db = db,
                    title = title,
                    onTitleChange = { title = it },
                    notes = notes,
                    onNotesChange = { notes = it },
                    color = color,
                    onColorChange = { color = it },
                    startDate = startDate,
                    onStartDateChange = { startDate = it },
                    endDate = endDate,
                    startTime = startTime,
                    onStartTimeChange = { startTime = it },
                    endTime = endTime,
                    onEndTimeChange = { endTime = it },
                    recurrenceFreq = recurrenceFreq,
                    selectedDaysOfWeek = selectedDaysOfWeek,
                    selectedDaysOfMonth = selectedDaysOfMonth,
                    onRecurrenceChange = { freq, daysWeek, daysMonth, endDateVal ->
                        recurrenceFreq = freq
                        selectedDaysOfWeek = daysWeek
                        selectedDaysOfMonth = daysMonth
                        endDate = endDateVal
                    },
                    selectedCategory = selectedCategory,
                    onCategoryChange = { selectedCategory = it },
                    resetTrigger = resetTrigger
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Button(
                        onClick = { clearForm() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Gray),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(16.dp)
                    ) { Text("Reset", fontSize = 16.sp) }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = {
                            if (title.isBlank()) {
                                notificationMessage = "Title is required"
                                scope.launch {
                                    showNotification = true
                                    scrollState.animateScrollTo(0)
                                    delay(3000)
                                    showNotification = false
                                }
                                return@Button
                            }
                            scope.launch {
                                val recurRule = when (recurrenceFreq) {
                                    RecurrenceFrequency.NONE -> RecurrenceRule()
                                    RecurrenceFrequency.DAILY -> RecurrenceRule()
                                    RecurrenceFrequency.WEEKLY -> RecurrenceRule(daysOfWeek = selectedDaysOfWeek.toList())
                                    RecurrenceFrequency.MONTHLY -> RecurrenceRule(daysOfMonth = selectedDaysOfMonth.toList())
                                    RecurrenceFrequency.YEARLY -> RecurrenceRule(monthAndDay = Pair(startDate.dayOfMonth, startDate.monthValue))
                                }
                                val overlapInfo = checkEventOverlapWithEvents(
                                    db = db,
                                    startDate = startDate,
                                    endDate = endDate,
                                    startTime = startTime,
                                    endTime = endTime,
                                    recurFreq = recurrenceFreq,
                                    recurRule = recurRule,
                                    excludeEventId = event.id
                                )
                                if (overlapInfo.hasOverlap) {
                                    notificationMessage = formatOverlapMessage(overlapInfo)
                                    showNotification = true
                                    scrollState.animateScrollTo(0)
                                    delay(3000)
                                    showNotification = false
                                    return@launch
                                }
                                val updatedEvent = event.copy(
                                    title = title,
                                    notes = notes.ifBlank { null },
                                    color = Converters.fromColor(color),
                                    startDate = startDate,
                                    endDate = endDate,
                                    startTime = startTime,
                                    endTime = endTime,
                                    recurFreq = recurrenceFreq,
                                    recurRule = recurRule,
                                    categoryId = selectedCategory?.let { categories.getOrNull(it)?.id }
                                )
                                EventManager.update(context, db, updatedEvent)
                                val refreshedEvent = db.eventDao().getMasterEventById(event.id) ?: updatedEvent
                                onSaveSuccess(refreshedEvent)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(16.dp)
                    ) { Text("Save", fontSize = 16.sp) }
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
                        Text(notificationMessage, color = BackgroundColor, fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
            }
        }
    }
}