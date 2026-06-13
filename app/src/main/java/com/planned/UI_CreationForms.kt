package com.planned

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.LocalTime
import androidx.core.graphics.toColorInt

/* CATEGORY FORM */
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun CategoryForm(
    title: String,
    onTitleChange: (String) -> Unit,
    notes: String,
    onNotesChange: (String) -> Unit,
    color: Color,
    onColorChange: (Color) -> Unit,
    resetTrigger: Int
) {
    Column {
        val titleValue = textInputField(
            label = "Title",
            initialValue = title,
            key = resetTrigger
        )
        onTitleChange(titleValue)
        Spacer(modifier = Modifier.height(12.dp))

        val notesValue = notesInputField(
            label = "Notes",
            initialValue = notes,
            key = resetTrigger
        )
        onNotesChange(notesValue)
        Spacer(modifier = Modifier.height(12.dp))

        colorPickerField(
            label = "Color",
            initialColor = color,
            key = resetTrigger,
            onColorChange = onColorChange
        )
    }
}

/* EVENT FORM */
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun EventForm(
    db: AppDatabase,
    title: String,
    onTitleChange: (String) -> Unit,
    notes: String,
    onNotesChange: (String) -> Unit,
    color: Color,
    onColorChange: (Color) -> Unit,
    startDate: LocalDate,
    onStartDateChange: (LocalDate) -> Unit,
    endDate: LocalDate?,
    startTime: LocalTime,
    onStartTimeChange: (LocalTime) -> Unit,
    endTime: LocalTime,
    onEndTimeChange: (LocalTime) -> Unit,
    recurrenceFreq: RecurrenceFrequency,
    selectedDaysOfWeek: Set<Int>,
    selectedDaysOfMonth: Set<Int>,
    onRecurrenceChange: (RecurrenceFrequency, Set<Int>, Set<Int>, LocalDate?) -> Unit,
    selectedCategory: Int?,
    onCategoryChange: (Int?) -> Unit,
    resetTrigger: Int
) {
    var categories by remember { mutableStateOf<List<Category>>(emptyList()) }
    var previousCategory by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(Unit) {
        categories = CategoryManager.getAll(db)
    }

    // Auto-update color when category changes
    LaunchedEffect(selectedCategory) {
        if (selectedCategory != previousCategory && selectedCategory != null && categories.isNotEmpty()) {
            val categoryColor = categories.getOrNull(selectedCategory)?.color
            if (categoryColor != null) {
                onColorChange(Color(categoryColor.toColorInt()))
            }
        }
        previousCategory = selectedCategory
    }

    Column {
        val titleValue = textInputField(
            label = "Title",
            initialValue = title,
            key = resetTrigger
        )
        onTitleChange(titleValue)
        Spacer(modifier = Modifier.height(12.dp))

        val notesValue = notesInputField(
            label = "Notes",
            initialValue = notes,
            key = resetTrigger
        )
        onNotesChange(notesValue)
        Spacer(modifier = Modifier.height(12.dp))

        val startDateValue = datePickerField(
            label = "Date",
            initialDate = startDate,
            isOptional = false,
            key = resetTrigger,
            otherDate = endDate,
            isStartDate = true,
            onDateValidated = { validated ->
                if (validated != startDate) {
                    onStartDateChange(validated)
                }
            }
        )!!
        if (startDateValue != startDate) {
            onStartDateChange(startDateValue)
        }
        Spacer(modifier = Modifier.height(12.dp))

        val (recurrence, recurrenceEndDate) = recurrencePickerField(
            initialFrequency = recurrenceFreq,
            initialDaysOfWeek = selectedDaysOfWeek,
            initialDaysOfMonth = selectedDaysOfMonth,
            initialEndDate = endDate,
            startDate = startDate,
            key = resetTrigger,
            onEndDateChange = null
        )
        onRecurrenceChange(recurrence.first, recurrence.second, recurrence.third, recurrenceEndDate)
        Spacer(modifier = Modifier.height(12.dp))

        val startTimeValue = timePickerField(
            label = "Start Time",
            initialTime = startTime,
            key = resetTrigger
        )
        onStartTimeChange(startTimeValue)
        Spacer(modifier = Modifier.height(12.dp))

        val endTimeValue = timePickerField(
            label = "End Time",
            initialTime = endTime,
            minTime = startTime,
            key = resetTrigger
        )
        onEndTimeChange(endTimeValue)
        Spacer(modifier = Modifier.height(12.dp))

        val categoryValue = dropdownField(
            label = "Category",
            items = categories.map { it.title },
            initialSelection = selectedCategory,
            key = resetTrigger
        )
        onCategoryChange(categoryValue)
        Spacer(modifier = Modifier.height(12.dp))

        colorPickerField(
            label = "Color",
            initialColor = color,
            key = resetTrigger,
            onColorChange = onColorChange
        )
    }
}


/* DEADLINE FORM */
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun DeadlineForm(
    db: AppDatabase,
    title: String,
    onTitleChange: (String) -> Unit,
    notes: String,
    onNotesChange: (String) -> Unit,
    date: LocalDate,
    onDateChange: (LocalDate) -> Unit,
    time: LocalTime,
    onTimeChange: (LocalTime) -> Unit,
    selectedCategory: Int?,
    onCategoryChange: (Int?) -> Unit,
    selectedEvent: Int?,
    onEventChange: (Int?) -> Unit,
    autoScheduleTask: Boolean,
    onAutoScheduleTaskChange: (Boolean) -> Unit,
    taskDurationHours: Int,
    taskDurationMinutes: Int,
    onTaskDurationChange: (Int, Int) -> Unit,
    taskIsBreakable: Boolean,
    onTaskIsBreakableChange: (Boolean) -> Unit,
    taskBreakableLockedByDuration: Boolean,
    onTaskBreakableLockedByDurationChange: (Boolean) -> Unit,
    resetTrigger: Int
) {
    var categories by remember { mutableStateOf<List<Category>>(emptyList()) }
    var events by remember { mutableStateOf<List<MasterEvent>>(emptyList()) }
    var previousEvent by remember { mutableStateOf<Int?>(null) }
    var maxBucketDuration by remember { mutableIntStateOf(Int.MAX_VALUE) }

    LaunchedEffect(Unit) {
        categories = CategoryManager.getAll(db)
        events = EventManager.getAll(db)

        // Get max bucket duration
        maxBucketDuration = getMaxBucketDurationMinutes(db) ?: Int.MAX_VALUE
    }

    // Check if task duration exceeds max bucket duration
    LaunchedEffect(taskDurationHours, taskDurationMinutes, maxBucketDuration, autoScheduleTask) {
        if (autoScheduleTask) {
            val taskDurationMinutesTotal = (taskDurationHours * 60) + taskDurationMinutes
            val needsToBeBreakable = taskDurationMinutesTotal > maxBucketDuration

            onTaskBreakableLockedByDurationChange(needsToBeBreakable)

            if (needsToBeBreakable) {
                onTaskIsBreakableChange(true)
            }
        } else {
            onTaskBreakableLockedByDurationChange(false)
        }
    }

    // Lock category when event selected, update when event changes
    LaunchedEffect(selectedEvent, events.size) {
        if (selectedEvent != previousEvent) {
            if (selectedEvent != null && events.isNotEmpty()) {
                val event = events.getOrNull(selectedEvent)
                if (event != null) {
                    val eventCategoryId = event.categoryId
                    val categoryIndex = if (eventCategoryId != null) {
                        categories.indexOfFirst { it.id == eventCategoryId }
                    } else {
                        null
                    }
                    if (categoryIndex != null) {
                        onCategoryChange(if (categoryIndex >= 0) categoryIndex else null)
                    }
                }
            }
            previousEvent = selectedEvent
        }
    }

    Column {
        val titleValue = textInputField(
            label = "Title",
            initialValue = title,
            key = resetTrigger
        )
        onTitleChange(titleValue)
        Spacer(modifier = Modifier.height(12.dp))

        val notesValue = notesInputField(
            label = "Notes",
            initialValue = notes,
            key = resetTrigger
        )
        onNotesChange(notesValue)
        Spacer(modifier = Modifier.height(12.dp))

        val dateValue = datePickerField(
            label = "Date",
            initialDate = date,
            isOptional = false,
            key = resetTrigger,
            allowPastDates = false
        )!!
        onDateChange(dateValue)
        Spacer(modifier = Modifier.height(12.dp))

        val timeValue = timePickerField(
            label = "Time",
            initialTime = time,
            key = resetTrigger,
            contextDate = date,
            allowPastTimes = false
        )
        onTimeChange(timeValue)
        Spacer(modifier = Modifier.height(12.dp))

        // Auto schedule task checkbox with expandable fields
        val (autoSchedule, durationHours, durationMinutes, breakable) = autoScheduleTaskPickerField(
            initialAutoScheduleTask = autoScheduleTask,
            initialDurationHours = taskDurationHours,
            initialDurationMinutes = taskDurationMinutes,
            initialBreakable = taskIsBreakable,
            breakableLockedByDuration = taskBreakableLockedByDuration,
            key = resetTrigger,
            onDurationChange = { hours, mins ->
                onTaskDurationChange(hours, mins)
            }
        )
        onAutoScheduleTaskChange(autoSchedule)
        onTaskDurationChange(durationHours, durationMinutes)

        // Only update breakable if not locked by duration
        if (!taskBreakableLockedByDuration) {
            onTaskIsBreakableChange(breakable)
        }

        Spacer(modifier = Modifier.height(12.dp))

        val eventValue = dropdownField(
            label = "Event",
            items = events.map { it.title },
            initialSelection = selectedEvent,
            key = resetTrigger
        )
        onEventChange(eventValue)
        Spacer(modifier = Modifier.height(12.dp))

        // Category locked when Event selected
        val categoryValue = dropdownField(
            label = "Category",
            items = categories.map { it.title },
            initialSelection = selectedCategory,
            key = resetTrigger,
            locked = selectedEvent != null
        )
        // Only allow manual changes when not locked
        if (selectedEvent == null) {
            onCategoryChange(categoryValue)
        }
    }
}

/* TASK BUCKET FORM */
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun TaskBucketForm(
    db: AppDatabase,
    startDate: LocalDate,
    onStartDateChange: (LocalDate) -> Unit,
    endDate: LocalDate?,
    startTime: LocalTime,
    onStartTimeChange: (LocalTime) -> Unit,
    endTime: LocalTime,
    onEndTimeChange: (LocalTime) -> Unit,
    recurrenceFreq: RecurrenceFrequency,
    selectedDaysOfWeek: Set<Int>,
    selectedDaysOfMonth: Set<Int>,
    onRecurrenceChange: (RecurrenceFrequency, Set<Int>, Set<Int>, LocalDate?) -> Unit,
    resetTrigger: Int
) {
    Column {
        val startDateValue = datePickerField(
            label = "Date",
            initialDate = startDate,
            isOptional = false,
            key = resetTrigger,
            otherDate = endDate,
            isStartDate = true,
            onDateValidated = { validated ->
                if (validated != startDate) {
                    onStartDateChange(validated)
                }
            }
        )!!
        if (startDateValue != startDate) {
            onStartDateChange(startDateValue)
        }
        Spacer(modifier = Modifier.height(12.dp))

        val (recurrence, recurrenceEndDate) = recurrencePickerField(
            initialFrequency = recurrenceFreq,
            initialDaysOfWeek = selectedDaysOfWeek,
            initialDaysOfMonth = selectedDaysOfMonth,
            initialEndDate = endDate,
            startDate = startDate,
            key = resetTrigger,
            onEndDateChange = null
        )
        onRecurrenceChange(recurrence.first, recurrence.second, recurrence.third, recurrenceEndDate)
        Spacer(modifier = Modifier.height(12.dp))

        val startTimeValue = timePickerField(
            label = "Start Time",
            initialTime = startTime,
            key = resetTrigger
        )
        onStartTimeChange(startTimeValue)
        Spacer(modifier = Modifier.height(12.dp))

        val endTimeValue = timePickerField(
            label = "End Time",
            initialTime = endTime,
            minTime = startTime,
            key = resetTrigger
        )
        onEndTimeChange(endTimeValue)
    }
}

/* TASK FORM */
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun TaskForm(
    db: AppDatabase,
    title: String,
    onTitleChange: (String) -> Unit,
    notes: String,
    onNotesChange: (String) -> Unit,
    isAllDay: Boolean,
    onAllDayChange: (Boolean) -> Unit,
    allDayDate: java.time.LocalDate,
    onAllDayDateChange: (java.time.LocalDate) -> Unit,
    isBreakable: Boolean,
    onBreakableChange: (Boolean) -> Unit,
    isAutoSchedule: Boolean,
    startDate: LocalDate?,
    startTime: LocalTime?,
    onScheduleChange: (Boolean, LocalDate?, LocalTime?) -> Unit,
    durationHours: Int,
    durationMinutes: Int,
    onDurationChange: (Int, Int) -> Unit,
    selectedCategory: Int?,
    onCategoryChange: (Int?) -> Unit,
    selectedEvent: Int?,
    onEventChange: (Int?) -> Unit,
    selectedDeadline: Int?,
    onDeadlineChange: (Int?) -> Unit,
    selectedDependencyTask: Int?,
    onDependencyTaskChange: (Int?) -> Unit,
    breakableLockedByDuration: Boolean,
    onBreakableLockedByDurationChange: (Boolean) -> Unit,
    resetTrigger: Int,
    currentTaskId: Int? = null
) {
    var categories by remember { mutableStateOf<List<Category>>(emptyList()) }
    var events by remember { mutableStateOf<List<MasterEvent>>(emptyList()) }
    var deadlines by remember { mutableStateOf<List<Deadline>>(emptyList()) }
    var dependencyTasks by remember { mutableStateOf<List<MasterTask>>(emptyList()) }
    var previousEvent by remember { mutableStateOf<Int?>(null) }
    var previousDeadline by remember { mutableStateOf<Int?>(null) }
    var previousDependencyTask by remember { mutableStateOf<Int?>(null) }
    var maxBucketDuration by remember { mutableIntStateOf(Int.MAX_VALUE) }

    LaunchedEffect(Unit) {
        categories = CategoryManager.getAll(db)
        events = EventManager.getAll(db)
        deadlines = DeadlineManager.getAll(db)

        // Get tasks that can be dependencies
        dependencyTasks = TaskManager.getAll(db).sortedBy { it.title }.filter {
            it.status == 1 &&
                    it.startDate == null &&
                    it.startTime == null &&
                    it.allDay == null &&
                    it.id != currentTaskId
        }

        // Get max bucket duration
        maxBucketDuration = getMaxBucketDurationMinutes(db) ?: Int.MAX_VALUE
    }

    // Check if task duration exceeds max bucket duration
    LaunchedEffect(durationHours, durationMinutes, maxBucketDuration, isAutoSchedule) {
        if (isAutoSchedule) {
            val taskDurationMinutes = (durationHours * 60) + durationMinutes
            val needsToBeBreakable = taskDurationMinutes > maxBucketDuration

            onBreakableLockedByDurationChange(needsToBeBreakable)

            if (needsToBeBreakable) {
                onBreakableChange(true)
            }
        } else {
            onBreakableLockedByDurationChange(false)
        }
    }

    // Validate start time for manual tasks by duration, time, date, schedule mode
    LaunchedEffect(durationHours, durationMinutes, startTime, startDate, isAutoSchedule) {
        if (!isAutoSchedule && startTime != null && startDate != null) {
            val totalDurationMinutes = (durationHours * 60) + durationMinutes
            val today = LocalDate.now()
            val now = LocalTime.now()

            var validated = startTime

            // Calculate the maximum start time based on duration
            var maxAllowedStartTime: LocalTime? = null
            if (totalDurationMinutes <= 1439) {
                val maxEndMinutes = 23 * 60 + 59
                val maxStartMinutes = maxEndMinutes - totalDurationMinutes
                if (maxStartMinutes >= 0) {
                    val maxStartHours = maxStartMinutes / 60
                    val maxStartMins = maxStartMinutes % 60
                    maxAllowedStartTime = LocalTime.of(maxStartHours, maxStartMins)
                }
            }

            // Apply duration constraint
            if (maxAllowedStartTime != null && validated.isAfter(maxAllowedStartTime)) {
                validated = maxAllowedStartTime
            }

            // Apply past time constraint if result doesn't exceed max allowed
            if (startDate == today) {
                if (validated.isBefore(now) || validated == now) {
                    val candidateTime = now.plusMinutes(1)

                    // Only use if it respects absolute duration limit
                    if (maxAllowedStartTime == null || !candidateTime.isAfter(maxAllowedStartTime)) {
                        validated = candidateTime
                    }
                }
            }

            // Update if validation changed time
            if (validated != startTime) {
                onScheduleChange(false, startDate, validated)
            }
        }
    }

    // Reset dependency task when auto-schedule disabled
    LaunchedEffect(isAutoSchedule) {
        if (!isAutoSchedule && selectedDependencyTask != null) {
            onDependencyTaskChange(null)
        }
    }

    LaunchedEffect(selectedDeadline, deadlines.size, events.size, categories.size) {
        if (selectedDeadline != previousDeadline) {
            if (selectedDeadline != null && deadlines.isNotEmpty()) {
                val deadline = deadlines.getOrNull(selectedDeadline)
                if (deadline != null) {
                    // Lock event to deadline's event
                    val deadlineEventId = deadline.eventId
                    val eventIndex = if (deadlineEventId != null) {
                        events.indexOfFirst { it.id == deadlineEventId }
                    } else {
                        null
                    }
                    if (eventIndex != null) {
                        onEventChange(if (eventIndex >= 0) eventIndex else null)
                    }

                    // Lock category to that event's category
                    if (eventIndex != null && eventIndex >= 0) {
                        val event = events[eventIndex]
                        val eventCategoryId = event.categoryId
                        val categoryIndex = if (eventCategoryId != null) {
                            categories.indexOfFirst { it.id == eventCategoryId }
                        } else {
                            null
                        }
                        if (categoryIndex != null) {
                            onCategoryChange(if (categoryIndex >= 0) categoryIndex else null)
                        }
                    } else {
                        // Lock category to deadline's category
                        val deadlineCategoryId = deadline.categoryId
                        val categoryIndex = if (deadlineCategoryId != null) {
                            categories.indexOfFirst { it.id == deadlineCategoryId }
                        } else {
                            null
                        }
                        if (categoryIndex != null) {
                            onCategoryChange(if (categoryIndex >= 0) categoryIndex else null)
                        }
                    }
                }
            }
            previousDeadline = selectedDeadline
        }
    }

    // Lock category when event selected
    LaunchedEffect(selectedEvent, events.size, categories.size) {
        if (selectedDeadline == null && selectedEvent != previousEvent) {
            if (selectedEvent != null && events.isNotEmpty()) {
                val event = events.getOrNull(selectedEvent)
                if (event != null) {
                    val eventCategoryId = event.categoryId
                    val categoryIndex = if (eventCategoryId != null) {
                        categories.indexOfFirst { it.id == eventCategoryId }
                    } else {
                        null
                    }
                    if (categoryIndex != null) {
                        onCategoryChange(if (categoryIndex >= 0) categoryIndex else null)
                    }
                }
            }
            previousEvent = selectedEvent
        }
    }

    Column {
        val titleValue = textInputField(
            label = "Title",
            initialValue = title,
            key = resetTrigger
        )
        onTitleChange(titleValue)
        Spacer(modifier = Modifier.height(12.dp))

        val notesValue = notesInputField(
            label = "Notes",
            initialValue = notes,
            key = resetTrigger
        )
        onNotesChange(notesValue)
        Spacer(modifier = Modifier.height(12.dp))

        // All Day checkbox with date picker
        val (allDayChecked, allDayDateValue) = allDayTaskPickerField(
            initialAllDay = isAllDay,
            initialDate = allDayDate,
            key = resetTrigger
        )
        onAllDayChange(allDayChecked)
        onAllDayDateChange(allDayDateValue)
        Spacer(modifier = Modifier.height(12.dp))

        // When NOT all-day: show duration, auto-schedule, breakable
        if (!isAllDay) {
            // Duration picker
            val (hours, minutes) = durationPickerField(
                initialHours = durationHours,
                initialMinutes = durationMinutes,
                key = resetTrigger,
                label = "Duration",
                onDurationChange = { newHours, newMinutes ->
                    onDurationChange(newHours, newMinutes)
                }
            )
            if (hours != durationHours || minutes != durationMinutes) {
                onDurationChange(hours, minutes)
            }
            Spacer(modifier = Modifier.height(12.dp))

            // Schedule picker with time validation
            val (autoSchedule, date, time) = schedulePickerField(
                initialAutoSchedule = isAutoSchedule,
                initialDate = startDate,
                initialTime = startTime,
                durationHours = hours,
                durationMinutes = minutes,
                key = resetTrigger,
                onTimeValidated = { validatedTime ->
                    if (validatedTime != startTime) {
                        onScheduleChange(false, startDate, validatedTime)
                    }
                }
            )
            onScheduleChange(autoSchedule, date, time)
            Spacer(modifier = Modifier.height(12.dp))

            // Breakable field with duration-based locking
            val breakableValue = checkboxField(
                label = "Breakable",
                initialChecked = isBreakable,
                key = resetTrigger,
                locked = !isAutoSchedule || breakableLockedByDuration,
                forceChecked = breakableLockedByDuration
            )

            if (isAutoSchedule && !breakableLockedByDuration) {
                onBreakableChange(breakableValue)
            } else if (!isAutoSchedule) {
                onBreakableChange(false)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Dependency Task dropdown locked if not auto-schedule
            val dependencyTaskValue = dropdownField(
                label = "Prerequisite Task",
                items = dependencyTasks.map { it.title },
                initialSelection = selectedDependencyTask,
                key = resetTrigger,
                locked = !isAutoSchedule
            )
            if (dependencyTaskValue != previousDependencyTask) {
                previousDependencyTask = dependencyTaskValue
                if (isAutoSchedule) {
                    onDependencyTaskChange(dependencyTaskValue)
                } else {
                    onDependencyTaskChange(null)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        } else {
            // All-day: lock dependency to None
            if (selectedDependencyTask != null) {
                onDependencyTaskChange(null)
            }
        }

        val deadlineValue = dropdownField(
            label = "Deadline",
            items = deadlines.map { it.title },
            initialSelection = selectedDeadline,
            key = resetTrigger
        )
        if (deadlineValue != selectedDeadline) {
            onDeadlineChange(deadlineValue)
        }
        Spacer(modifier = Modifier.height(12.dp))

        // Event locked when Deadline selected
        val eventValue = dropdownField(
            label = "Event",
            items = events.map { it.title },
            initialSelection = selectedEvent,
            key = resetTrigger,
            locked = selectedDeadline != null
        )
        if (selectedDeadline == null && eventValue != selectedEvent) {
            onEventChange(eventValue)
        }
        Spacer(modifier = Modifier.height(12.dp))

        // Category locked when Event/Deadline selected
        val categoryValue = dropdownField(
            label = "Category",
            items = categories.map { it.title },
            initialSelection = selectedCategory,
            key = resetTrigger,
            locked = selectedEvent != null || selectedDeadline != null
        )
        if (selectedEvent == null && selectedDeadline == null && categoryValue != selectedCategory) {
            onCategoryChange(categoryValue)
        }
    }
}

/* REMINDER FORM */
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun ReminderForm(
    db: AppDatabase,
    title: String,
    onTitleChange: (String) -> Unit,
    notes: String,
    onNotesChange: (String) -> Unit,
    startDate: LocalDate,
    onStartDateChange: (LocalDate) -> Unit,
    endDate: LocalDate?,
    isAllDay: Boolean,
    time: LocalTime,
    onAllDayTimeChange: (Boolean, LocalTime) -> Unit,
    recurrenceFreq: RecurrenceFrequency,
    selectedDaysOfWeek: Set<Int>,
    selectedDaysOfMonth: Set<Int>,
    onRecurrenceChange: (RecurrenceFrequency, Set<Int>, Set<Int>, LocalDate?) -> Unit,
    selectedCategory: Int?,
    onCategoryChange: (Int?) -> Unit,
    resetTrigger: Int
) {
    var categories by remember { mutableStateOf<List<Category>>(emptyList()) }

    LaunchedEffect(Unit) {
        categories = CategoryManager.getAll(db)
    }

    Column {
        val titleValue = textInputField(
            label = "Title",
            initialValue = title,
            key = resetTrigger
        )
        onTitleChange(titleValue)
        Spacer(modifier = Modifier.height(12.dp))

        val notesValue = notesInputField(
            label = "Notes",
            initialValue = notes,
            key = resetTrigger
        )
        onNotesChange(notesValue)
        Spacer(modifier = Modifier.height(12.dp))

        val startDateValue = datePickerField(
            label = "Date",
            initialDate = startDate,
            isOptional = false,
            key = resetTrigger,
            otherDate = endDate,
            isStartDate = true,
            onDateValidated = { validated ->
                if (validated != startDate) {
                    onStartDateChange(validated)
                }
            }
        )!!
        if (startDateValue != startDate) {
            onStartDateChange(startDateValue)
        }
        Spacer(modifier = Modifier.height(12.dp))

        val (recurrence, recurrenceEndDate) = recurrencePickerField(
            initialFrequency = recurrenceFreq,
            initialDaysOfWeek = selectedDaysOfWeek,
            initialDaysOfMonth = selectedDaysOfMonth,
            initialEndDate = endDate,
            startDate = startDate,
            key = resetTrigger,
            onEndDateChange = null
        )
        onRecurrenceChange(recurrence.first, recurrence.second, recurrence.third, recurrenceEndDate)
        Spacer(modifier = Modifier.height(12.dp))

        val (allDay, selectedTime) = allDayPickerField(
            initialAllDay = isAllDay,
            initialTime = time,
            key = resetTrigger
        )
        onAllDayTimeChange(allDay, selectedTime)
        Spacer(modifier = Modifier.height(12.dp))

        val categoryValue = dropdownField(
            label = "Category",
            items = categories.map { it.title },
            initialSelection = selectedCategory,
            key = resetTrigger
        )
        onCategoryChange(categoryValue)
    }
}