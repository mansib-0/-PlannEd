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
import java.time.format.DateTimeFormatter

data class ReminderUpdateFormData(
    val categories: List<Category>,
    val selectedCategory: Int?
)

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun Reminders(db: AppDatabase) {
    when (remindersCurrentView) {
        "list" -> RemindersListView(
            db = db,
            onReminderClick = { reminder ->
                remindersSelectedReminder = reminder
                remindersCurrentView = "info"
            }
        )
        "info" -> remindersSelectedReminder?.let { reminder ->
            ReminderInfoView(
                db = db,
                reminder = reminder,
                occurrence = null,
                onBack = {
                    remindersCurrentView = "list"
                    remindersSelectedReminder = null
                    remindersUpdateFormData = null
                },
                onUpdateDataReady = { data -> remindersUpdateFormData = data },
                onUpdate = { remindersCurrentView = "update" }
            )
        }
        "update" -> remindersSelectedReminder?.let { reminder ->
            remindersUpdateFormData?.let { formData ->
                ReminderUpdateView(
                    db = db,
                    reminder = reminder,
                    preloadedData = formData,
                    onBack = { remindersCurrentView = "info" },
                    onSaveSuccess = { updatedReminder ->
                        remindersSelectedReminder = updatedReminder
                        remindersUpdateFormData = null
                        remindersCurrentView = "info"
                    }
                )
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun RemindersListView(
    db: AppDatabase,
    onReminderClick: (MasterReminder) -> Unit
) {
    var reminders by remember { mutableStateOf<List<MasterReminder>>(emptyList()) }
    var categories by remember { mutableStateOf<List<Category>>(emptyList()) }

    LaunchedEffect(Unit) {
        reminders = ReminderManager.getAll(db).sortedBy { it.startDate }
        categories = CategoryManager.getAll(db)
    }

    val grouped = reminders.groupBy { it.categoryId }
    val sortedCategoryIds = categories.map { it.id as Int? }.filter { grouped.containsKey(it) } +
            if (grouped.containsKey(null)) listOf(null) else emptyList()

    Column(modifier = Modifier.fillMaxSize().background(BackgroundColor).padding(16.dp)) {
        Text("Reminders", fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp, top = 4.dp))

        if (reminders.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "No reminders", fontSize = 18.sp, color = Color.Gray)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                sortedCategoryIds.forEach { catId ->
                    val categoryName = if (catId == null) "No Category"
                    else categories.find { it.id == catId }?.title ?: "No Category"
                    val categoryReminders = grouped[catId] ?: emptyList()
                    item {
                        Text(
                            text = categoryName,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Gray,
                            modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp)
                        )
                    }
                    items(categoryReminders) { reminder ->
                        ReminderItemView(reminder = reminder, db = db, onClick = { onReminderClick(reminder) })
                    }
                }
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun ReminderItemView(
    reminder: MasterReminder,
    db: AppDatabase,
    onClick: () -> Unit
) {
    var reminderColor by remember { mutableStateOf(Color.Gray) }
    val timeFormatter = java.time.format.DateTimeFormatter.ofPattern("h:mm a")
    val dateFormatter = java.time.format.DateTimeFormatter.ofPattern("MMMM d, yyyy")

    LaunchedEffect(reminder.id) {
        reminderColor = when {
            reminder.categoryId != null -> {
                val category = db.categoryDao().getCategoryById(reminder.categoryId)
                category?.color?.let { Converters.toColor(it) } ?: Color.LightGray
            }
            else -> Color.LightGray
        }
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
        Box(modifier = Modifier.size(INNER_CIRCLE_SIZE).background(reminderColor, CircleShape))
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = reminder.title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = buildString {
                    append(if (reminder.allDay) "All Day" else reminder.time?.format(timeFormatter) ?: "")
                    append(", ")
                    append(
                        if (reminder.recurFreq != RecurrenceFrequency.NONE)
                            reminder.recurFreq.name.lowercase().replaceFirstChar { it.uppercase() }
                        else
                            reminder.startDate.format(dateFormatter)
                    )
                },
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
fun ReminderInfoView(
    db: AppDatabase,
    reminder: MasterReminder,
    occurrence: ReminderOccurrence? = null,
    onBack: () -> Unit,
    onUpdateDataReady: (ReminderUpdateFormData) -> Unit,
    onUpdate: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    var category by remember { mutableStateOf<Category?>(null) }
    var currentReminder by remember { mutableStateOf(reminder) }
    var updateDataReady by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(reminder.id) {
        currentReminder = db.reminderDao().getMasterReminderById(reminder.id) ?: reminder
        category = currentReminder.categoryId?.let { db.categoryDao().getCategoryById(it) }

        // Preload update form data
        val categories = CategoryManager.getAll(db)
        val selectedCategory = currentReminder.categoryId?.let { catId ->
            categories.indexOfFirst { it.id == catId }.takeIf { it >= 0 }
        }
        onUpdateDataReady(ReminderUpdateFormData(categories = categories, selectedCategory = selectedCategory))
        updateDataReady = true
    }

    val timeFormatter = DateTimeFormatter.ofPattern("h:mm a")
    val dateFormatter = DateTimeFormatter.ofPattern("MMMM d, yyyy")

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
                Text(text = currentReminder.title, fontSize = 20.sp, fontWeight = FontWeight.Medium)
            }

            if (!currentReminder.notes.isNullOrBlank()) {
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Text(text = currentReminder.notes!!, fontSize = 16.sp)
                }
                Spacer(modifier = Modifier.height(18.dp))
            }

            if (occurrence != null) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = if (occurrence.allDay) "All Day, ${occurrence.occurDate.format(dateFormatter)}"
                        else "${occurrence.time?.format(timeFormatter) ?: ""}, ${occurrence.occurDate.format(dateFormatter)}",
                        fontSize = 16.sp,
                        color = Color.Gray
                    )
                }
                Spacer(modifier = Modifier.height(18.dp))
            }

            InfoCard(listOf(
                "Start Date" to currentReminder.startDate.format(dateFormatter),
                "End Date" to (currentReminder.endDate?.format(dateFormatter) ?: "N/A"),
                "Time" to (if (currentReminder.allDay) "All Day" else currentReminder.time?.format(timeFormatter) ?: ""),
                "Recurrence" to (currentReminder.recurFreq.name.lowercase().replaceFirstChar { it.uppercase() } +
                        when (currentReminder.recurFreq) {
                            RecurrenceFrequency.WEEKLY -> currentReminder.recurRule.daysOfWeek?.sorted()?.joinToString(", ") { d ->
                                when (d) { 1 -> "Mo"; 2 -> "Tu"; 3 -> "We"; 4 -> "Th"; 5 -> "Fr"; 6 -> "Sa"; 7 -> "Su"; else -> "" }
                            }?.let { " ($it)" } ?: ""
                            RecurrenceFrequency.MONTHLY -> currentReminder.recurRule.daysOfMonth?.sorted()?.joinToString(", ")?.let { " ($it)" } ?: ""
                            RecurrenceFrequency.YEARLY -> currentReminder.recurRule.monthAndDay?.let { " (${java.time.Month.of(it.second).getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault())} ${it.first})" } ?: ""
                            else -> ""
                        }),
                "Category" to (category?.title ?: "None")
            ))
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
                text = { Text("Delete this reminder?", fontSize = 16.sp) },
                confirmButton = {},
                dismissButton = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (occurrence != null && currentReminder.recurFreq != RecurrenceFrequency.NONE) {
                            Button(
                                onClick = {
                                    showDeleteDialog = false
                                    scope.launch {
                                        db.reminderDao().deleteOccurrence(occurrence.id)
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
                                    ReminderManager.delete(context, db, currentReminder.id)
                                    onBack()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Gray),
                            contentPadding = PaddingValues(12.dp)
                        ) { Text(if (currentReminder.recurFreq != RecurrenceFrequency.NONE) "Delete All" else "Delete", fontSize = 12.sp, color = Color.White) }

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
fun ReminderUpdateView(
    db: AppDatabase,
    reminder: MasterReminder,
    preloadedData: ReminderUpdateFormData,
    onBack: () -> Unit,
    onSaveSuccess: (MasterReminder) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var title by remember { mutableStateOf(reminder.title) }
    var notes by remember { mutableStateOf(reminder.notes ?: "") }
    var startDate by remember { mutableStateOf(reminder.startDate) }
    var endDate by remember { mutableStateOf(reminder.endDate) }
    var isAllDay by remember { mutableStateOf(reminder.allDay) }
    var time by remember { mutableStateOf(reminder.time ?: java.time.LocalTime.of(10, 0)) }
    var recurrenceFreq by remember { mutableStateOf(reminder.recurFreq) }
    var selectedDaysOfWeek by remember { mutableStateOf(reminder.recurRule.daysOfWeek?.toSet() ?: setOf(7)) }
    var selectedDaysOfMonth by remember { mutableStateOf(reminder.recurRule.daysOfMonth?.toSet() ?: setOf(1)) }

    val categories = preloadedData.categories
    var selectedCategory by remember { mutableStateOf(preloadedData.selectedCategory) }
    var resetTrigger by remember { mutableIntStateOf(0) }
    var showNotification by remember { mutableStateOf(false) }

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
                ReminderForm(
                    db = db,
                    title = title,
                    onTitleChange = { title = it },
                    notes = notes,
                    onNotesChange = { notes = it },
                    startDate = startDate,
                    onStartDateChange = { startDate = it },
                    endDate = endDate,
                    isAllDay = isAllDay,
                    time = time,
                    onAllDayTimeChange = { allDay, timeVal -> isAllDay = allDay; time = timeVal },
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
                        onClick = {
                            title = reminder.title
                            notes = reminder.notes ?: ""
                            startDate = reminder.startDate
                            endDate = reminder.endDate
                            isAllDay = reminder.allDay
                            time = reminder.time ?: java.time.LocalTime.of(10, 0)
                            recurrenceFreq = reminder.recurFreq
                            selectedDaysOfWeek = reminder.recurRule.daysOfWeek?.toSet() ?: setOf(7)
                            selectedDaysOfMonth = reminder.recurRule.daysOfMonth?.toSet() ?: setOf(1)
                            selectedCategory = preloadedData.selectedCategory
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
                                val recurRule = when (recurrenceFreq) {
                                    RecurrenceFrequency.NONE -> RecurrenceRule()
                                    RecurrenceFrequency.DAILY -> RecurrenceRule()
                                    RecurrenceFrequency.WEEKLY -> RecurrenceRule(daysOfWeek = selectedDaysOfWeek.toList())
                                    RecurrenceFrequency.MONTHLY -> RecurrenceRule(daysOfMonth = selectedDaysOfMonth.toList())
                                    RecurrenceFrequency.YEARLY -> RecurrenceRule(monthAndDay = Pair(startDate.dayOfMonth, startDate.monthValue))
                                }
                                val updatedReminder = reminder.copy(
                                    title = title,
                                    notes = notes.ifBlank { null },
                                    startDate = startDate,
                                    endDate = endDate,
                                    time = if (isAllDay) null else time,
                                    allDay = isAllDay,
                                    recurFreq = recurrenceFreq,
                                    recurRule = recurRule,
                                    categoryId = selectedCategory?.let { categories.getOrNull(it)?.id }
                                )
                                ReminderManager.update(context, db, updatedReminder)
                                val refreshedReminder = db.reminderDao().getMasterReminderById(reminder.id) ?: updatedReminder
                                onSaveSuccess(refreshedReminder)
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