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
import java.time.format.DateTimeFormatter

data class DeadlineUpdateFormData(
    val categories: List<Category>,
    val events: List<MasterEvent>,
    val selectedCategory: Int?,
    val selectedEvent: Int?
)

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun Deadlines(db: AppDatabase) {
    when (deadlinesCurrentView) {
        "list" -> DeadlinesListView(
            db = db,
            onDeadlineClick = { deadline ->
                deadlinesSelectedDeadline = deadline
                deadlinesCurrentView = "info"
            }
        )
        "info" -> deadlinesSelectedDeadline?.let { deadline ->
            DeadlineInfoView(
                db = db,
                deadline = deadline,
                onBack = {
                    deadlinesCurrentView = "list"
                    deadlinesSelectedDeadline = null
                    deadlinesUpdateFormData = null
                },
                onUpdateDataReady = { data -> deadlinesUpdateFormData = data },
                onUpdate = { deadlinesCurrentView = "update" },
                deadlineReturnScreen = "Deadlines"
            )
        }
        "update" -> deadlinesSelectedDeadline?.let { deadline ->
            deadlinesUpdateFormData?.let { formData ->
                DeadlineUpdateView(
                    db = db,
                    deadline = deadline,
                    preloadedData = formData,
                    onBack = { deadlinesCurrentView = "info" },
                    onSaveSuccess = { updatedDeadline ->
                        deadlinesSelectedDeadline = updatedDeadline
                        deadlinesUpdateFormData = null
                        deadlinesCurrentView = "info"
                    }
                )
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun DeadlinesListView(
    db: AppDatabase,
    onDeadlineClick: (Deadline) -> Unit
) {
    var deadlines by remember { mutableStateOf<List<Deadline>>(emptyList()) }
    var categories by remember { mutableStateOf<List<Category>>(emptyList()) }

    LaunchedEffect(Unit) {
        deadlines = DeadlineManager.getAll(db).sortedBy { it.date }
        categories = CategoryManager.getAll(db)
    }

    val grouped = deadlines.groupBy { it.categoryId }
    val sortedCategoryIds = categories.map { it.id as Int? }.filter { grouped.containsKey(it) } +
            if (grouped.containsKey(null)) listOf(null) else emptyList()

    Column(modifier = Modifier.fillMaxSize().background(BackgroundColor).padding(16.dp)) {
        Text("Deadlines", fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp, top = 4.dp))

        if (deadlines.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "No deadlines", fontSize = 18.sp, color = Color.Gray)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                sortedCategoryIds.forEach { catId ->
                    val categoryName = if (catId == null) "No Category"
                    else categories.find { it.id == catId }?.title ?: "No Category"
                    val categoryDeadlines = grouped[catId] ?: emptyList()
                    item {
                        Text(
                            text = categoryName,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Gray,
                            modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp)
                        )
                    }
                    items(categoryDeadlines) { deadline ->
                        DeadlineItemView(db = db, deadline = deadline, onClick = { onDeadlineClick(deadline) })
                    }
                }
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun DeadlineItemView(
    db: AppDatabase,
    deadline: Deadline,
    onClick: () -> Unit
) {
    val deadlineColor = remember(deadline.id) {
        runBlocking {
            when {
                deadline.eventId != null -> {
                    val event = db.eventDao().getMasterEventById(deadline.eventId)
                    event?.color?.let { Converters.toColor(it) }
                        ?: event?.categoryId?.let { catId -> db.categoryDao().getCategoryById(catId)?.color?.let { Converters.toColor(it) } }
                        ?: deadline.categoryId?.let { catId -> db.categoryDao().getCategoryById(catId)?.color?.let { Converters.toColor(it) } }
                        ?: Color.LightGray
                }
                deadline.categoryId != null -> {
                    val category = db.categoryDao().getCategoryById(deadline.categoryId)
                    category?.color?.let { Converters.toColor(it) } ?: Color.LightGray
                }
                else -> Color.LightGray
            }
        }
    }

    val dateFormatter = java.time.format.DateTimeFormatter.ofPattern("MMMM d, yyyy")
    val timeFormatter = java.time.format.DateTimeFormatter.ofPattern("h:mm a")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(CardColor))
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(INNER_CIRCLE_SIZE).background(deadlineColor, CircleShape))
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = deadline.title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "${deadline.time.format(timeFormatter)}, ${deadline.date.format(dateFormatter)}",
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

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun DeadlineInfoView(
    db: AppDatabase,
    deadline: Deadline,
    onBack: () -> Unit,
    onUpdateDataReady: (DeadlineUpdateFormData) -> Unit,
    onUpdate: () -> Unit,
    deadlineReturnScreen: String = "Calendars"
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    var category by remember { mutableStateOf<Category?>(null) }
    var event by remember { mutableStateOf<MasterEvent?>(null) }
    var currentDeadline by remember { mutableStateOf(deadline) }
    var relatedTasks by remember { mutableStateOf<List<MasterTask>>(emptyList()) }
    var updateDataReady by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(deadline.id) {
        currentDeadline = db.deadlineDao().getDeadlineById(deadline.id) ?: deadline
        category = currentDeadline.categoryId?.let { db.categoryDao().getCategoryById(it) }
        event = currentDeadline.eventId?.let { db.eventDao().getMasterEventById(it) }
        relatedTasks = db.taskDao().getAllMasterTasks().filter { it.deadlineId == deadline.id && it.status != 3 }

        // Preload update form data
        val categories = CategoryManager.getAll(db)
        val events = EventManager.getAll(db)
        val selectedCategory = currentDeadline.categoryId?.let { catId ->
            categories.indexOfFirst { it.id == catId }.takeIf { it >= 0 }
        }
        val selectedEvent = currentDeadline.eventId?.let { evId ->
            events.indexOfFirst { it.id == evId }.takeIf { it >= 0 }
        }
        onUpdateDataReady(DeadlineUpdateFormData(
            categories = categories,
            events = events,
            selectedCategory = selectedCategory,
            selectedEvent = selectedEvent
        ))
        updateDataReady = true
    }

    val dateFormatter = DateTimeFormatter.ofPattern("MMMM d, yyyy")
    val timeFormatter = DateTimeFormatter.ofPattern("h:mm a")

    Column(modifier = Modifier.fillMaxSize().background(BackgroundColor).padding(16.dp)) {
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
                Text(text = currentDeadline.title, fontSize = 20.sp, fontWeight = FontWeight.Medium)
            }

            if (!currentDeadline.notes.isNullOrBlank()) {
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Text(text = currentDeadline.notes!!, fontSize = 16.sp)
                }
                Spacer(modifier = Modifier.height(18.dp))
            }

            InfoCard(listOf(
                "Date" to currentDeadline.date.format(dateFormatter),
                "Time" to currentDeadline.time.format(timeFormatter),
                "Event" to (event?.title ?: "None"),
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
                                kotlinx.coroutines.runBlocking {
                                    when {
                                        task.eventId != null -> {
                                            val event = db.eventDao().getAllMasterEvents().find { it.id == task.eventId }
                                            event?.color?.let { Converters.toColor(it) }
                                                ?: event?.categoryId?.let { catId -> db.categoryDao().getAll().find { it.id == catId }?.color?.let { Converters.toColor(it) } }
                                                ?: Color.LightGray
                                        }
                                        task.categoryId != null -> {
                                            val cat = db.categoryDao().getCategoryById(task.categoryId)
                                            cat?.color?.let { Converters.toColor(it) } ?: Color.LightGray
                                        }
                                        else -> Color.LightGray
                                    }
                                }
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(CardColor))
                                    .clickable {
                                        com.planned.deadlinesCurrentView = "list"
                                        com.planned.deadlinesSelectedDeadline = null
                                        com.planned.selectedDeadlineForInfo = currentDeadline
                                        com.planned.deadlineInfoReturnScreen = deadlineReturnScreen
                                        com.planned.selectedTaskForInfo = task
                                        com.planned.taskInfoReturnScreen = "DeadlineInfo"
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
                text = { Text("Delete this deadline?", fontSize = 16.sp) },
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
                                    DeadlineManager.delete(context, db, currentDeadline.id)
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

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun DeadlineUpdateView(
    db: AppDatabase,
    deadline: Deadline,
    preloadedData: DeadlineUpdateFormData,
    onBack: () -> Unit,
    onSaveSuccess: (Deadline) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var title by remember { mutableStateOf(deadline.title) }
    var notes by remember { mutableStateOf(deadline.notes ?: "") }
    var date by remember { mutableStateOf(deadline.date) }
    var time by remember { mutableStateOf(deadline.time) }

    val categories = preloadedData.categories
    val events = preloadedData.events
    var selectedCategory by remember { mutableStateOf(preloadedData.selectedCategory) }
    var selectedEvent by remember { mutableStateOf(preloadedData.selectedEvent) }
    var previousEvent by remember { mutableStateOf(preloadedData.selectedEvent) }
    var resetTrigger by remember { mutableIntStateOf(0) }
    var showNotification by remember { mutableStateOf(false) }

    // Lock category when event selected
    LaunchedEffect(selectedEvent, events.size) {
        val currentEventIndex = selectedEvent
        val previousEventIndex = previousEvent
        if (currentEventIndex != previousEventIndex) {
            if (currentEventIndex != null && events.isNotEmpty()) {
                val event = events.getOrNull(currentEventIndex)
                if (event != null) {
                    val eventCategoryId = event.categoryId
                    val categoryIndex = if (eventCategoryId != null) {
                        categories.indexOfFirst { it.id == eventCategoryId }
                    } else null
                    if (categoryIndex != null) {
                        selectedCategory = if (categoryIndex >= 0) categoryIndex else null
                    }
                }
            }
            previousEvent = currentEventIndex
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().background(BackgroundColor).padding(16.dp)) {
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
                val titleValue = textInputField("Title", title, resetTrigger)
                title = titleValue
                Spacer(modifier = Modifier.height(12.dp))

                val notesValue = notesInputField("Notes", notes, resetTrigger)
                notes = notesValue
                Spacer(modifier = Modifier.height(12.dp))

                val dateValue = datePickerField(
                    label = "Date",
                    initialDate = date,
                    isOptional = false,
                    key = resetTrigger,
                    allowPastDates = false,
                    onDateValidated = { validated -> if (validated != date) date = validated }
                )!!
                if (dateValue != date) date = dateValue
                Spacer(modifier = Modifier.height(12.dp))

                val timeValue = timePickerField(
                    label = "Time",
                    initialTime = time,
                    key = resetTrigger,
                    contextDate = date,
                    allowPastTimes = false
                )
                time = timeValue
                Spacer(modifier = Modifier.height(12.dp))

                val eventValue = dropdownField(
                    label = "Event",
                    items = events.map { it.title },
                    initialSelection = selectedEvent,
                    key = resetTrigger
                )
                selectedEvent = eventValue
                Spacer(modifier = Modifier.height(12.dp))

                val categoryValue = dropdownField(
                    label = "Category",
                    items = categories.map { it.title },
                    initialSelection = selectedCategory,
                    key = resetTrigger,
                    locked = selectedEvent != null
                )
                if (selectedEvent == null) selectedCategory = categoryValue

                Spacer(modifier = Modifier.height(24.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Button(
                        onClick = {
                            title = deadline.title
                            notes = deadline.notes ?: ""
                            date = deadline.date
                            time = deadline.time
                            selectedCategory = preloadedData.selectedCategory
                            selectedEvent = preloadedData.selectedEvent
                            resetTrigger++
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Gray),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(16.dp)
                    ) { Text("Reset", fontSize = 16.sp) }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = {
                            if (title.isBlank()) {
                                scope.launch {
                                    showNotification = true
                                    scrollState.animateScrollTo(0)
                                    delay(3000)
                                    showNotification = false
                                }
                                return@Button
                            }
                            scope.launch {
                                val updatedDeadline = deadline.copy(
                                    title = title,
                                    notes = notes.ifBlank { null },
                                    date = date,
                                    time = time,
                                    categoryId = selectedCategory?.let { categories.getOrNull(it)?.id },
                                    eventId = selectedEvent?.let { events.getOrNull(it)?.id }
                                )
                                DeadlineManager.update(context, db, updatedDeadline)
                                val refreshedDeadline = db.deadlineDao().getDeadlineById(deadline.id) ?: updatedDeadline
                                onSaveSuccess(refreshedDeadline)
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
                        Text("Title is required", color = BackgroundColor, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}