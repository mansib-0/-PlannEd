package com.planned

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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

fun formatBucketRecurrence(bucket: MasterTaskBucket, startWeekOnMonday: Boolean): String {
    return when (bucket.recurFreq) {
        RecurrenceFrequency.NONE -> "No Recurrence"
        RecurrenceFrequency.DAILY -> "Daily"
        RecurrenceFrequency.WEEKLY -> {
            val days = bucket.recurRule.daysOfWeek ?: emptyList()
            // Order: if week starts Monday: 1,2,3,4,5,6,7 else 7,1,2,3,4,5,6
            val order = if (startWeekOnMonday) listOf(1,2,3,4,5,6,7) else listOf(7,1,2,3,4,5,6)
            val sorted = days.sortedBy { order.indexOf(it) }
            val dayNames = sorted.joinToString(", ") { d ->
                when (d) { 1 -> "Mo"; 2 -> "Tu"; 3 -> "We"; 4 -> "Th"; 5 -> "Fr"; 6 -> "Sa"; 7 -> "Su"; else -> "" }
            }
            "Weekly (${dayNames})"
        }
        RecurrenceFrequency.MONTHLY -> {
            val days = bucket.recurRule.daysOfMonth?.sorted()?.joinToString(", ") ?: ""
            "Monthly ($days)"
        }
        RecurrenceFrequency.YEARLY -> "Yearly"
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun TaskBuckets(db: AppDatabase) {
    when (taskBucketsCurrentView) {
        "list" -> TaskBucketsList(
            db = db,
            onBucketClick = { bucket ->
                taskBucketsSelectedBucket = bucket
                taskBucketsCurrentView = "info"
            }
        )
        "info" -> taskBucketsSelectedBucket?.let { bucket ->
            TaskBucketInfoPage(
                db = db,
                bucket = bucket,
                occurrence = null,
                onBack = {
                    taskBucketsCurrentView = "list"
                    taskBucketsSelectedBucket = null
                },
                onUpdate = { taskBucketsCurrentView = "update" }
            )
        }
        "update" -> taskBucketsSelectedBucket?.let { bucket ->
            TaskBucketUpdateForm(
                db = db,
                bucket = bucket,
                onBack = { taskBucketsCurrentView = "info" },
                onSaveSuccess = { updatedBucket ->
                    taskBucketsSelectedBucket = updatedBucket
                    taskBucketsCurrentView = "info"
                }
            )
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun TaskBucketsList(
    db: AppDatabase,
    onBucketClick: (MasterTaskBucket) -> Unit
) {
    var buckets by remember { mutableStateOf<List<MasterTaskBucket>>(emptyList()) }

    LaunchedEffect(Unit) {
        buckets = TaskBucketManager.getAll(db)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor)
            .padding(16.dp)
    ) {
        Text(
            "Task Buckets",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 24.dp, top = 4.dp)
        )

        if (buckets.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No task buckets",
                    fontSize = 18.sp,
                    color = Color.Gray
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(buckets) { bucket ->
                    TaskBucketListItem(
                        bucket = bucket,
                        onClick = { onBucketClick(bucket) }
                    )
                }
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun TaskBucketListItem(
    bucket: MasterTaskBucket,
    onClick: () -> Unit
) {
    val timeFormatter = java.time.format.DateTimeFormatter.ofPattern("h:mm a")
    val startWeekOnMonday = SettingsManager.settings?.startWeekOnMonday ?: false
    val recurrenceText = formatBucketRecurrence(bucket, startWeekOnMonday)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(CardColor))
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${bucket.startTime.format(timeFormatter)} - ${bucket.endTime.format(timeFormatter)}",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = recurrenceText,
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
fun TaskBucketInfoPage(
    db: AppDatabase,
    bucket: MasterTaskBucket,
    occurrence: TaskBucketOccurrence? = null,
    onBack: () -> Unit,
    onUpdate: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    var currentBucket by remember { mutableStateOf(bucket) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val dateFormatter = java.time.format.DateTimeFormatter.ofPattern("MMMM d, yyyy")
    val timeFormatter = java.time.format.DateTimeFormatter.ofPattern("h:mm a")

    LaunchedEffect(bucket.id) {
        currentBucket = db.taskBucketDao().getMasterBucketById(bucket.id) ?: bucket
    }

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

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scrollState)
        ) {
            Box(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
                Text(text = "Task Bucket", fontSize = 20.sp, fontWeight = FontWeight.Medium)
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
                "Start Date" to currentBucket.startDate.format(dateFormatter),
                "End Date" to (currentBucket.endDate?.format(dateFormatter) ?: "N/A"),
                "Start Time" to currentBucket.startTime.format(timeFormatter),
                "End Time" to currentBucket.endTime.format(timeFormatter),
                "Recurrence" to formatBucketRecurrence(currentBucket, SettingsManager.settings?.startWeekOnMonday ?: false)
            ))
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = { showDeleteDialog = true },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Gray),
                contentPadding = PaddingValues(16.dp)
            ) {
                Text("Delete", fontSize = 16.sp, color = Color.White)
            }
            Button(
                onClick = onUpdate,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor),
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
                text = { Text("Delete this task bucket?", fontSize = 16.sp) },
                confirmButton = {},
                dismissButton = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (occurrence != null && currentBucket.recurFreq != RecurrenceFrequency.NONE) {
                            Button(
                                onClick = {
                                    showDeleteDialog = false
                                    scope.launch {
                                        db.taskBucketDao().deleteOccurrence(occurrence.id)
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
                                    TaskBucketManager.delete(context, db, currentBucket.id)
                                    onBack()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Gray),
                            contentPadding = PaddingValues(12.dp)
                        ) { Text(if (currentBucket.recurFreq != RecurrenceFrequency.NONE) "Delete All" else "Delete", fontSize = 12.sp, color = Color.White) }

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
fun TaskBucketUpdateForm(
    db: AppDatabase,
    bucket: MasterTaskBucket,
    onBack: () -> Unit,
    onSaveSuccess: (MasterTaskBucket) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var startDate by remember { mutableStateOf(bucket.startDate) }
    var endDate by remember { mutableStateOf(bucket.endDate) }
    var startTime by remember { mutableStateOf(bucket.startTime) }
    var endTime by remember { mutableStateOf(bucket.endTime) }
    var recurrenceFreq by remember { mutableStateOf(bucket.recurFreq) }
    var selectedDaysOfWeek by remember { mutableStateOf(bucket.recurRule.daysOfWeek?.toSet() ?: setOf(7)) }
    var selectedDaysOfMonth by remember { mutableStateOf(bucket.recurRule.daysOfMonth?.toSet() ?: setOf(1)) }
    var resetTrigger by remember { mutableIntStateOf(0) }

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

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
        ) {
            TaskBucketForm(
                db = db,
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
                resetTrigger = resetTrigger
            )

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Button(
                    onClick = {
                        startDate = bucket.startDate
                        endDate = bucket.endDate
                        startTime = bucket.startTime
                        endTime = bucket.endTime
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
                        scope.launch {
                            val recurRule = when (recurrenceFreq) {
                                RecurrenceFrequency.NONE -> RecurrenceRule()
                                RecurrenceFrequency.DAILY -> RecurrenceRule()
                                RecurrenceFrequency.WEEKLY -> RecurrenceRule(daysOfWeek = selectedDaysOfWeek.toList())
                                RecurrenceFrequency.MONTHLY -> RecurrenceRule(daysOfMonth = selectedDaysOfMonth.toList())
                                RecurrenceFrequency.YEARLY -> RecurrenceRule(monthAndDay = Pair(startDate.dayOfMonth, startDate.monthValue))
                            }

                            val updatedBucket = bucket.copy(
                                startDate = startDate,
                                endDate = endDate,
                                startTime = startTime,
                                endTime = endTime,
                                recurFreq = recurrenceFreq,
                                recurRule = recurRule
                            )
                            TaskBucketManager.update(context, db, updatedBucket)
                            val refreshedBucket = db.taskBucketDao().getMasterBucketById(bucket.id) ?: updatedBucket
                            onSaveSuccess(refreshedBucket)
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
}