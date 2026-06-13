package com.planned

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/* UTILITY FUNCTIONS */

@RequiresApi(Build.VERSION_CODES.O)
fun validateStartTime(time: LocalTime): LocalTime {
    return when {
        time.isBefore(LocalTime.of(0, 0)) -> LocalTime.of(0, 0)
        time.isAfter(LocalTime.of(23, 58)) -> LocalTime.of(23, 58)
        else -> time
    }
}
@RequiresApi(Build.VERSION_CODES.O)
fun validateEndTime(time: LocalTime, startTime: LocalTime): LocalTime {
    var validatedTime = when {
        time.isBefore(LocalTime.of(0, 1)) -> LocalTime.of(0, 1)
        time.isAfter(LocalTime.of(23, 59)) -> LocalTime.of(23, 59)
        else -> time
    }

    if (!validatedTime.isAfter(startTime)) {
        validatedTime = startTime.plusMinutes(1)
        if (validatedTime.isAfter(LocalTime.of(23, 59))) {
            validatedTime = LocalTime.of(23, 59)
        }
    }

    return validatedTime
}
@RequiresApi(Build.VERSION_CODES.O)
fun validateStartTimeWithEnd(time: LocalTime, endTime: LocalTime): LocalTime {
    // Start time must be before end time and within bounds
    var validatedTime = validateStartTime(time)

    if (!validatedTime.isBefore(endTime)) {
        validatedTime = endTime.minusMinutes(1)
        if (validatedTime.isBefore(LocalTime.of(0, 0))) {
            validatedTime = LocalTime.of(0, 0)
        }
        if (validatedTime.isAfter(LocalTime.of(23, 58))) {
            validatedTime = LocalTime.of(23, 58)
        }
    }

    return validatedTime
}
@RequiresApi(Build.VERSION_CODES.O)
fun validateStartTimeForTask(time: LocalTime, durationMinutes: Int): LocalTime {
    var validatedTime = validateStartTime(time)

    if (durationMinutes > 1439) {
        return validatedTime
    }

    // Calculate end time in total minutes
    val startMinutes = validatedTime.hour * 60 + validatedTime.minute
    val endMinutes = startMinutes + durationMinutes
    val maxEndMinutes = 23 * 60 + 59

    // If end time exceeds 23:59, calculate the maximum valid start time
    if (endMinutes > maxEndMinutes) {
        val maxStartMinutes = maxEndMinutes - durationMinutes
        if (maxStartMinutes >= 0) {
            val maxStartHours = maxStartMinutes / 60
            val maxStartMins = maxStartMinutes % 60
            validatedTime = LocalTime.of(maxStartHours, maxStartMins)

            // Ensure within start time bounds
            if (validatedTime.isBefore(LocalTime.of(0, 0))) {
                validatedTime = LocalTime.of(0, 0)
            }
            if (validatedTime.isAfter(LocalTime.of(23, 58))) {
                validatedTime = LocalTime.of(23, 58)
            }
        }
    }

    return validatedTime
}

// Date validation
@RequiresApi(Build.VERSION_CODES.O)
fun validateStartDate(startDate: LocalDate, endDate: LocalDate?): LocalDate {
    if (endDate == null) return startDate

    // Start date must be at least 1 day before end date
    return if (startDate.isAfter(endDate.minusDays(1))) {
        endDate.minusDays(1)
    } else {
        startDate
    }
}
@RequiresApi(Build.VERSION_CODES.O)
fun validateEndDate(endDate: LocalDate, startDate: LocalDate): LocalDate {
    // End date must be at least 1 day after start date
    return if (endDate.isBefore(startDate.plusDays(1))) {
        startDate.plusDays(1)
    } else {
        endDate
    }
}

// Prevent past date/time
@RequiresApi(Build.VERSION_CODES.O)
fun validateDateNotPast(date: LocalDate): LocalDate {
    val today = LocalDate.now()
    return if (date.isBefore(today)) today else date
}
@RequiresApi(Build.VERSION_CODES.O)
fun validateTimeNotPast(time: LocalTime, date: LocalDate): LocalTime {
    val today = LocalDate.now()
    val now = LocalTime.now()

    return if (date == today) {
        if (time.isBefore(now) || time == now) {
            // Round up to next minute
            now.plusMinutes(1)
        } else {
            time
        }
    } else {
        time
    }
}

/* TYPE PICKER FIELD */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun typePickerField(
    initialType: String = "Task",
    key: Int = 0
): String {
    var selectedType by remember(key) { mutableStateOf(initialType) }
    var showTypePicker by remember(key) { mutableStateOf(false) }

    LaunchedEffect(key, initialType) {
        selectedType = initialType
    }

    val types = listOf("Task", "Reminder", "Deadline", "Event", "Task Bucket", "Category")

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { showTypePicker = !showTypePicker }
            .padding(horizontal = 16.dp)
            .padding(vertical = 8.dp)
    ) {
        Column {
            // Display selected type
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BackgroundColor, RoundedCornerShape(8.dp))
                    .border(3.dp, PrimaryColor, RoundedCornerShape(8.dp))
                    .padding(12.dp)
            ) {
                Text(selectedType, fontSize = 16.sp)
            }

            // Type options
            AnimatedVisibility(
                visible = showTypePicker,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    types.forEach { type ->
                        val isSelected = selectedType == type
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .background(
                                    if (isSelected) PrimaryColor else Color.LightGray,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    selectedType = type
                                    showTypePicker = false
                                }
                                .padding(12.dp)
                        ) {
                            Text(
                                type,
                                fontSize = 16.sp,
                                color = if (isSelected) BackgroundColor else Color.Black,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }
    }

    return selectedType
}

/* TEXT INPUT FIELD */
@Composable
fun textInputField(
    label: String,
    initialValue: String = "",
    key: Int = 0
): String {
    var textValue by remember(key) { mutableStateOf(initialValue) }

    LaunchedEffect(key, initialValue) {
        textValue = initialValue
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(CardColor), RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Column {
            Text(label, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(8.dp))
            TextField(
                value = textValue,
                onValueChange = { textValue = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BackgroundColor, RoundedCornerShape(8.dp))
                    .border(1.dp, Color.LightGray, RoundedCornerShape(8.dp)),
                textStyle = LocalTextStyle.current.copy(fontSize = 16.sp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = BackgroundColor,
                    unfocusedContainerColor = BackgroundColor,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )
        }
    }

    return textValue
}

/* NOTES INPUT FIELD */
@Composable
fun notesInputField(
    label: String,
    initialValue: String = "",
    key: Int = 0
): String {
    var notesValue by remember(key) { mutableStateOf(initialValue) }

    LaunchedEffect(key, initialValue) {
        notesValue = initialValue
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(CardColor), RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Column {
            Text(label, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(8.dp))
            TextField(
                value = notesValue,
                onValueChange = { notesValue = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .background(BackgroundColor, RoundedCornerShape(8.dp))
                    .border(1.dp, Color.LightGray, RoundedCornerShape(8.dp)),
                textStyle = LocalTextStyle.current.copy(fontSize = 16.sp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = BackgroundColor,
                    unfocusedContainerColor = BackgroundColor,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                maxLines = 4
            )
        }
    }

    return notesValue
}

/* COLOR PICKER FIELD */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun colorPickerField(
    label: String,
    initialColor: Color = Preset1,
    key: Int = 0,
    onColorChange: ((Color) -> Unit)? = null
): Color {
    var selectedColor by remember(key) { mutableStateOf(initialColor) }
    var showColorPicker by remember(key) { mutableStateOf(false) }

    LaunchedEffect(key, initialColor) {
        selectedColor = initialColor
    }

    val colorPresets = listOf(
        Preset1, Preset2, Preset3, Preset4,
        Preset5, Preset6, Preset7, Preset8,
        Preset9, Preset10, Preset11, Preset12
    )

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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(label, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(selectedColor)
                )
            }

            // Expandable color picker grid
            AnimatedVisibility(
                visible = showColorPicker,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column {
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
                                        selectedColor = c
                                        showColorPicker = false
                                        onColorChange?.invoke(c)
                                    }
                            )
                        }
                    }
                }
            }
        }
    }

    return selectedColor
}

/* DATE PICKER FIELD */
@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun datePickerField(
    label: String,
    initialDate: LocalDate? = LocalDate.now().plusDays(1),
    isOptional: Boolean = false,
    key: Int = 0,
    allowPastDates: Boolean = true,
    otherDate: LocalDate? = null,
    isStartDate: Boolean = true,
    onDateValidated: ((LocalDate) -> Unit)? = null
): LocalDate? {
    var selectedDate by remember(key) { mutableStateOf(initialDate ?: LocalDate.now().plusDays(1)) }
    var showDatePicker by remember(key) { mutableStateOf(false) }

    LaunchedEffect(key, initialDate) {
        selectedDate = initialDate ?: LocalDate.now().plusDays(1)
    }

    // Validate when other Date changes
    LaunchedEffect(otherDate) {
        if (otherDate != null) {
            val validated = if (isStartDate) {
                validateStartDate(selectedDate, otherDate)
            } else {
                validateEndDate(selectedDate, otherDate)
            }

            if (validated != selectedDate) {
                selectedDate = validated
                onDateValidated?.invoke(validated)
            }
        }
    }

    // Format date
    val dateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")
    val displayDate = if (isOptional && initialDate == null) "None" else selectedDate.format(dateFormatter)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(CardColor), RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(label, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.weight(1f))
            Button(
                onClick = { showDatePicker = true },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor)
            ) {
                Text(displayDate)
            }
        }

        // Date picker dialog
        if (showDatePicker) {
            val datePickerState = rememberDatePickerState(
                initialSelectedDateMillis = selectedDate.toEpochDay() * 86400000L
            )
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(
                        onClick = {
                            datePickerState.selectedDateMillis?.let {
                                var picked = LocalDate.ofEpochDay(it / 86400000L)

                                // Apply past date validation
                                if (!allowPastDates) {
                                    picked = validateDateNotPast(picked)
                                }

                                // Apply start/end date validation
                                if (otherDate != null) {
                                    picked = if (isStartDate) {
                                        validateStartDate(picked, otherDate)
                                    } else {
                                        validateEndDate(picked, otherDate)
                                    }
                                }

                                selectedDate = picked
                                onDateValidated?.invoke(picked)
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
    }

    return if (isOptional && initialDate == null) null else selectedDate
}

/* TIME PICKER FIELD */
@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun timePickerField(
    label: String,
    initialTime: LocalTime = LocalTime.of(10, 0),
    minTime: LocalTime? = null,
    key: Int = 0,
    contextDate: LocalDate? = null,
    allowPastTimes: Boolean = true
): LocalTime {
    var selectedTime by remember(key) { mutableStateOf(initialTime) }
    var showTimePicker by remember(key) { mutableStateOf(false) }

    LaunchedEffect(key, initialTime) {
        selectedTime = initialTime
    }

    // Enforce minimum time if provided
    LaunchedEffect(minTime) {
        if (minTime != null) {
            selectedTime = validateEndTime(selectedTime, minTime)
        }
    }

    // Validate against past time
    LaunchedEffect(contextDate) {
        if (!allowPastTimes && contextDate != null && minTime == null) {
            val validated = validateTimeNotPast(selectedTime, contextDate)
            if (validated != selectedTime) {
                selectedTime = validated
            }
        }
    }

    // Format time
    val timeFormatter = DateTimeFormatter.ofPattern("h:mm a")
    val displayTime = selectedTime.format(timeFormatter)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(CardColor), RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(label, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.weight(1f))
            Button(
                onClick = { showTimePicker = true },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor)
            ) {
                Text(displayTime)
            }
        }

        // Time picker dialog
        if (showTimePicker) {
            val timePickerState = rememberTimePickerState(
                initialHour = selectedTime.hour,
                initialMinute = selectedTime.minute,
                is24Hour = false
            )
            AlertDialog(
                onDismissRequest = { showTimePicker = false },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val newTime = LocalTime.of(timePickerState.hour, timePickerState.minute, 0, 0)

                            // Validate based on start or end time
                            var validatedTime = if (minTime != null) {
                                validateEndTime(newTime, minTime)
                            } else {
                                validateStartTime(newTime)
                            }

                            if (!allowPastTimes && contextDate != null && minTime == null) {
                                validatedTime = validateTimeNotPast(validatedTime, contextDate)
                            }

                            selectedTime = validatedTime
                            showTimePicker = false
                        }
                    ) {
                        Text("OK", color = Color.Black, fontSize = 16.sp)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showTimePicker = false }) {
                        Text("Cancel", color = Color.Black, fontSize = 16.sp)
                    }
                },
                containerColor = BackgroundColor,
                text = {
                    TimePicker(
                        state = timePickerState,
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

    return selectedTime
}

/* RECURRENCE PICKER FIELD */
@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun recurrencePickerField(
    initialFrequency: RecurrenceFrequency = RecurrenceFrequency.NONE,
    initialDaysOfWeek: Set<Int> = setOf(7),
    initialDaysOfMonth: Set<Int> = setOf(1),
    initialEndDate: LocalDate? = null,
    startDate: LocalDate = LocalDate.now().plusDays(1),
    key: Int = 0,
    onEndDateChange: ((LocalDate?) -> Unit)? = null
): Pair<Triple<RecurrenceFrequency, Set<Int>, Set<Int>>, LocalDate?> {
    var recurrenceFreq by remember(key) { mutableStateOf(initialFrequency) }
    var showRecurrenceDropdown by remember(key) { mutableStateOf(false) }
    var selectedDaysOfWeek by remember(key) { mutableStateOf(initialDaysOfWeek) }
    var selectedDaysOfMonth by remember(key) { mutableStateOf(initialDaysOfMonth) }
    var repeatForever by remember(key) { mutableStateOf(initialEndDate == null) }
    var endDate by remember(key) { mutableStateOf(initialEndDate ?: startDate.plusMonths(1)) }

    LaunchedEffect(key, initialFrequency, initialDaysOfWeek, initialDaysOfMonth, initialEndDate) {
        recurrenceFreq = initialFrequency
        selectedDaysOfWeek = initialDaysOfWeek
        selectedDaysOfMonth = initialDaysOfMonth
        repeatForever = initialEndDate == null
        endDate = initialEndDate ?: startDate.plusMonths(1)
    }

    // Validate end date when start date changes
    LaunchedEffect(startDate) {
        if (!repeatForever) {
            val validated = validateEndDate(endDate, startDate)
            if (validated != endDate) {
                endDate = validated
                onEndDateChange?.invoke(validated)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(CardColor), RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { showRecurrenceDropdown = !showRecurrenceDropdown }
            .padding(16.dp)
    ) {
        Column {
            Text("Recurrence", fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(12.dp))

            // Display current recurrence frequency
            val recurrenceText = when (recurrenceFreq) {
                RecurrenceFrequency.NONE -> "Don't Repeat"
                RecurrenceFrequency.DAILY -> "Daily"
                RecurrenceFrequency.WEEKLY -> "Weekly"
                RecurrenceFrequency.MONTHLY -> "Monthly"
                RecurrenceFrequency.YEARLY -> "Yearly"
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BackgroundColor, RoundedCornerShape(8.dp))
                    .border(1.dp, Color.LightGray, RoundedCornerShape(8.dp))
                    .padding(12.dp)
            ) {
                Text(recurrenceText, fontSize = 16.sp)
            }

            // Dropdown for frequency selection
            AnimatedVisibility(
                visible = showRecurrenceDropdown,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    listOf(
                        RecurrenceFrequency.NONE to "Don't Repeat",
                        RecurrenceFrequency.DAILY to "Daily",
                        RecurrenceFrequency.WEEKLY to "Weekly",
                        RecurrenceFrequency.MONTHLY to "Monthly",
                        RecurrenceFrequency.YEARLY to "Yearly"
                    ).forEach { (freq, label) ->
                        val isSelected = recurrenceFreq == freq
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .background(
                                    if (isSelected) PrimaryColor else Color.LightGray,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    recurrenceFreq = freq
                                    showRecurrenceDropdown = false
                                    // Reset end date when "Don't Repeat"
                                    if (freq == RecurrenceFrequency.NONE) {
                                        endDate = startDate.plusMonths(1)
                                        repeatForever = true
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

            // Weekly recurrence rule
            if (recurrenceFreq == RecurrenceFrequency.WEEKLY) {
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val days = listOf("Su", "Mo", "Tu", "We", "Th", "Fr", "Sa")
                    days.forEachIndexed { index, day ->
                        val dayNum = if (index == 0) 7 else index
                        val isSelected = selectedDaysOfWeek.contains(dayNum)

                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) PrimaryColor else Color.LightGray)
                                .clickable {
                                    selectedDaysOfWeek = if (isSelected && selectedDaysOfWeek.size > 1) {
                                        selectedDaysOfWeek - dayNum
                                    } else if (!isSelected) {
                                        val newSet = selectedDaysOfWeek + dayNum
                                        if (newSet.size == 7) {
                                            recurrenceFreq = RecurrenceFrequency.DAILY
                                        }
                                        newSet
                                    } else {
                                        selectedDaysOfWeek
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                day,
                                color = if (isSelected) BackgroundColor else Color.Black,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            // Monthly recurrence rule
            if (recurrenceFreq == RecurrenceFrequency.MONTHLY) {
                Spacer(modifier = Modifier.height(16.dp))

                LazyVerticalGrid(
                    columns = GridCells.Fixed(7),
                    modifier = Modifier.height(215.dp)
                ) {
                    items(31) { index ->
                        val dayNum = index + 1
                        val isSelected = selectedDaysOfMonth.contains(dayNum)

                        Box(
                            modifier = Modifier
                                .padding(4.dp)
                                .size(35.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) PrimaryColor else Color.LightGray)
                                .clickable {
                                    selectedDaysOfMonth = if (isSelected && selectedDaysOfMonth.size > 1) {
                                        selectedDaysOfMonth - dayNum
                                    } else if (!isSelected) {
                                        val newSet = selectedDaysOfMonth + dayNum
                                        if (newSet.size == 31) {
                                            recurrenceFreq = RecurrenceFrequency.DAILY
                                        }
                                        newSet
                                    } else {
                                        selectedDaysOfMonth
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                dayNum.toString(),
                                color = if (isSelected) BackgroundColor else Color.Black,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            // End date options
            if (recurrenceFreq != RecurrenceFrequency.NONE) {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), thickness = 1.dp, color = Color.LightGray)

                // Repeat Forever toggle
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Repeat Forever", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.weight(1f))
                    Switch(
                        checked = repeatForever,
                        onCheckedChange = {
                            repeatForever = it
                            if (!it && endDate == null) {
                                endDate = startDate.plusMonths(1)
                            }
                            onEndDateChange?.invoke(if (it) null else endDate)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = BackgroundColor,
                            checkedTrackColor = PrimaryColor,
                            uncheckedTrackColor = Color.LightGray,
                            uncheckedThumbColor = BackgroundColor
                        )
                    )
                }

                // End date picker
                AnimatedVisibility(
                    visible = !repeatForever,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), thickness = 1.dp, color = Color.LightGray)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("End Date", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                            Spacer(modifier = Modifier.weight(1f))

                            var showEndDatePicker by remember { mutableStateOf(false) }
                            val dateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")

                            val displayEndDate = endDate ?: startDate.plusMonths(1)
                            val displayDate = displayEndDate.format(dateFormatter)

                            Button(
                                onClick = { showEndDatePicker = true },
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor)
                            ) {
                                Text(displayDate)
                            }

                            if (showEndDatePicker) {
                                val datePickerState = rememberDatePickerState(
                                    initialSelectedDateMillis = (endDate ?: startDate.plusMonths(1)).toEpochDay() * 86400000L
                                )

                                DatePickerDialog(
                                    onDismissRequest = { showEndDatePicker = false },
                                    confirmButton = {
                                        TextButton(
                                            onClick = {
                                                datePickerState.selectedDateMillis?.let {
                                                    val selectedDate = LocalDate.ofEpochDay(it / 86400000L)
                                                    val validated = validateEndDate(selectedDate, startDate)
                                                    endDate = validated
                                                    onEndDateChange?.invoke(validated)
                                                }
                                                showEndDatePicker = false
                                            }
                                        ) {
                                            Text("OK", color = Color.Black, fontSize = 16.sp)
                                        }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { showEndDatePicker = false }) {
                                            Text("Cancel", color = Color.Black, fontSize = 16.sp)
                                        }
                                    },
                                    colors = DatePickerDefaults.colors(containerColor = BackgroundColor)
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
                        }
                    }
                }
            }
        }
    }

    return Pair(Triple(recurrenceFreq, selectedDaysOfWeek, selectedDaysOfMonth), if (repeatForever) null else endDate)
}

/* DURATION PICKER FIELD */
@Composable
fun durationPickerField(
    label: String = "Duration",
    initialHours: Int = 0,
    initialMinutes: Int = 30,
    key: Int = 0,
    onDurationChange: ((Int, Int) -> Unit)? = null
): Pair<Int, Int> {
    var durationHours by remember(key) { mutableIntStateOf(initialHours) }
    var durationMinutes by remember(key) { mutableIntStateOf(initialMinutes) }
    var showDurationPicker by remember(key) { mutableStateOf(false) }
    var tempHours by remember { mutableIntStateOf(initialHours) }
    var tempMinutes by remember { mutableIntStateOf(initialMinutes) }

    LaunchedEffect(key, initialHours, initialMinutes) {
        durationHours = initialHours
        durationMinutes = initialMinutes
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(CardColor), RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(label, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.weight(1f))
            Button(
                onClick = {
                    tempHours = durationHours
                    tempMinutes = durationMinutes
                    showDurationPicker = true
                },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor)
            ) {
                Text("${durationHours}h ${durationMinutes}m")
            }
        }

        // Duration picker dialog
        if (showDurationPicker) {
            AlertDialog(
                onDismissRequest = { showDurationPicker = false },
                confirmButton = {
                    TextButton(
                        onClick = {
                            durationHours = tempHours
                            durationMinutes = tempMinutes
                            showDurationPicker = false
                            onDurationChange?.invoke(durationHours, durationMinutes)
                        }
                    ) {
                        Text("OK", color = Color.Black, fontSize = 16.sp)
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showDurationPicker = false
                    }) {
                        Text("Cancel", color = Color.Black, fontSize = 16.sp)
                    }
                },
                containerColor = BackgroundColor,
                text = {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Hours picker
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Hours", fontSize = 12.sp)
                            Spacer(modifier = Modifier.height(8.dp))

                            // Increment button
                            Button(
                                onClick = { tempHours++ },
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor)
                            ) {
                                Text("▲")
                            }

                            Text(
                                tempHours.toString(),
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )

                            // Decrement button
                            Button(
                                onClick = {
                                    if (tempHours > 0 && !(tempHours == 1 && tempMinutes == 0)) {
                                        tempHours--
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor)
                            ) {
                                Text("▼")
                            }
                        }

                        Spacer(modifier = Modifier.width(32.dp))

                        // Minutes picker
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Minutes", fontSize = 12.sp)
                            Spacer(modifier = Modifier.height(8.dp))

                            // Increment button
                            Button(
                                onClick = {
                                    val newMinutes = (tempMinutes + 5) % 60
                                    if (!(tempHours == 0 && newMinutes == 0)) {
                                        tempMinutes = newMinutes
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor)
                            ) {
                                Text("▲")
                            }

                            Text(
                                tempMinutes.toString(),
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )

                            // Decrement button
                            Button(
                                onClick = {
                                    val newMinutes = if (tempMinutes - 5 < 0) 55 else tempMinutes - 5
                                    if (!(tempHours == 0 && newMinutes == 0)) {
                                        tempMinutes = newMinutes
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor)
                            ) {
                                Text("▼")
                            }
                        }
                    }
                }
            )
        }
    }

    return Pair(durationHours, durationMinutes)
}

/* SCHEDULE PICKER FIELD */
@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun schedulePickerField(
    initialAutoSchedule: Boolean = true,
    initialDate: LocalDate? = null,
    initialTime: LocalTime? = null,
    durationHours: Int = 0,
    durationMinutes: Int = 30,
    key: Int = 0,
    onTimeValidated: ((LocalTime) -> Unit)? = null
): Triple<Boolean, LocalDate?, LocalTime?> {

    var isAutoSchedule by remember(key) { mutableStateOf(initialAutoSchedule) }
    var startDate by remember(key) { mutableStateOf(initialDate) }
    var startTime by remember(key) { mutableStateOf(initialTime) }

    // Restore state when inputs change
    LaunchedEffect(key) {
        isAutoSchedule = initialAutoSchedule
        startDate = initialDate
        startTime = initialTime
    }

    val totalDurationMinutes = (durationHours * 60) + durationMinutes
    val autoScheduleLocked = totalDurationMinutes > 1439

    // Auto-schedule forced ON when locked
    val effectiveAutoSchedule = if (autoScheduleLocked) true else isAutoSchedule

    // Validate start time by manual
    LaunchedEffect(effectiveAutoSchedule) {
        if (!effectiveAutoSchedule && startTime != null && startDate != null) {
            var validated = validateTimeNotPast(startTime!!, startDate!!)
            validated = validateStartTimeForTask(validated, totalDurationMinutes)
            if (validated != startTime) {
                startTime = validated
                onTimeValidated?.invoke(validated)
            }
        }
    }

    // Validate start time by date
    LaunchedEffect(startDate) {
        if (!effectiveAutoSchedule && startTime != null && startDate != null) {
            var validated = validateTimeNotPast(startTime!!, startDate!!)
            validated = validateStartTimeForTask(validated, totalDurationMinutes)
            if (validated != startTime) {
                startTime = validated
                onTimeValidated?.invoke(validated)
            }
        }
    }

    // Validate start time by duration
    LaunchedEffect(durationHours, durationMinutes) {
        if (!effectiveAutoSchedule && startTime != null && startDate != null) {
            var validated = validateTimeNotPast(startTime!!, startDate!!)
            validated = validateStartTimeForTask(validated, totalDurationMinutes)
            if (validated != startTime) {
                startTime = validated
                onTimeValidated?.invoke(validated)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(CardColor), RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Column {

            // Auto schedule toggle
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Auto Schedule",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.weight(1f))
                Switch(
                    checked = effectiveAutoSchedule,
                    onCheckedChange = {
                        if (!autoScheduleLocked) {
                            isAutoSchedule = it

                            if (it) {
                                startDate = null
                                startTime = null
                            } else {
                                if (startDate == null) startDate = LocalDate.now().plusDays(1)
                                if (startTime == null) startTime = LocalTime.of(10, 0)
                            }
                        }
                    },
                    enabled = !autoScheduleLocked,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = BackgroundColor,
                        checkedTrackColor = PrimaryColor,
                        uncheckedTrackColor = Color.LightGray,
                        uncheckedThumbColor = BackgroundColor,
                        disabledCheckedThumbColor = BackgroundColor,
                        disabledCheckedTrackColor = Color.LightGray
                    )
                )
            }

            // Manual fields
            AnimatedVisibility(
                visible = !effectiveAutoSchedule,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {

                Column {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), thickness = 1.dp, color = Color.LightGray)

                    // Date picker
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Date", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.weight(1f))

                        var showDatePicker by remember { mutableStateOf(false) }
                        val dateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")
                        val displayDate = startDate?.format(dateFormatter) ?: "Select Date"

                        Button(
                            onClick = { showDatePicker = true },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor)
                        ) {
                            Text(displayDate)
                        }

                        if (showDatePicker) {
                            val datePickerState = rememberDatePickerState(
                                initialSelectedDateMillis =
                                    (startDate ?: LocalDate.now().plusDays(1)).toEpochDay() * 86400000L
                            )

                            DatePickerDialog(
                                onDismissRequest = { showDatePicker = false },
                                confirmButton = {
                                    TextButton(onClick = {
                                        datePickerState.selectedDateMillis?.let {
                                            val picked = LocalDate.ofEpochDay(it / 86400000L)
                                            startDate = validateDateNotPast(picked)

                                            // If date changed to today, validate time isn't in the past
                                            if (startTime != null && startDate != null) {
                                                startTime = validateTimeNotPast(startTime!!, startDate!!)
                                            }
                                        }
                                        showDatePicker = false
                                    }) {
                                        Text("OK", color = Color.Black, fontSize = 16.sp)
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showDatePicker = false }) {
                                        Text("Cancel", color = Color.Black, fontSize = 16.sp)
                                    }
                                },
                                colors = DatePickerDefaults.colors(containerColor = BackgroundColor)
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
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), thickness = 1.dp, color = Color.LightGray)

                    // Time picker
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Start Time", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.weight(1f))

                        var showTimePicker by remember { mutableStateOf(false) }
                        val timeFormatter = DateTimeFormatter.ofPattern("h:mm a")
                        val displayTime = startTime?.format(timeFormatter) ?: "Select Time"

                        Button(
                            onClick = { showTimePicker = true },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor)
                        ) {
                            Text(displayTime)
                        }

                        if (showTimePicker) {
                            val timePickerState = rememberTimePickerState(
                                initialHour = startTime?.hour ?: 10,
                                initialMinute = startTime?.minute ?: 0,
                                is24Hour = false
                            )

                            AlertDialog(
                                onDismissRequest = { showTimePicker = false },
                                confirmButton = {
                                    TextButton(onClick = {
                                        val picked = LocalTime.of(
                                            timePickerState.hour,
                                            timePickerState.minute
                                        )

                                        // Validate against duration
                                        var validated = validateStartTimeForTask(
                                            picked,
                                            totalDurationMinutes
                                        )

                                        // Validate against past time if date is today
                                        if (startDate != null) {
                                            validated = validateTimeNotPast(validated, startDate!!)
                                        }

                                        startTime = validated
                                        onTimeValidated?.invoke(validated)
                                        showTimePicker = false
                                    }) {
                                        Text("OK", color = Color.Black, fontSize = 16.sp)
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showTimePicker = false }) {
                                        Text("Cancel", color = Color.Black, fontSize = 16.sp)
                                    }
                                },
                                containerColor = BackgroundColor,
                                text = {
                                    TimePicker(
                                        state = timePickerState,
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

    return Triple(
        effectiveAutoSchedule,
        if (autoScheduleLocked) null else startDate,
        if (autoScheduleLocked) null else startTime
    )
}

/* CHECKBOX FIELD */
@Composable
fun checkboxField(
    label: String,
    initialChecked: Boolean = false,
    key: Int = 0,
    locked: Boolean = false,
    forceChecked: Boolean = false
): Boolean {
    var isChecked by remember(key) { mutableStateOf(initialChecked) }

    LaunchedEffect(key, initialChecked) {
        isChecked = initialChecked
    }

    // Force checked, always show as checked
    val displayChecked = if (forceChecked) true else if (locked) false else isChecked

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(CardColor), RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                label,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color.Black
            )
            Spacer(modifier = Modifier.weight(1f))
            Switch(
                checked = displayChecked,
                onCheckedChange = {
                    if (!locked && !forceChecked) isChecked = it
                },
                enabled = !locked && !forceChecked,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = BackgroundColor,
                    checkedTrackColor = PrimaryColor,
                    uncheckedTrackColor = Color.LightGray,
                    uncheckedThumbColor = BackgroundColor,
                    disabledCheckedThumbColor = BackgroundColor,
                    disabledCheckedTrackColor = Color.LightGray,
                    disabledUncheckedThumbColor = BackgroundColor,
                    disabledUncheckedTrackColor = Color.LightGray
                )
            )
        }
    }

    return displayChecked
}

/* ALL DAY PICKER FIELD */
@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun allDayPickerField(
    initialAllDay: Boolean = true,
    initialTime: LocalTime = LocalTime.of(10, 0),
    key: Int = 0
): Pair<Boolean, LocalTime> {
    var isAllDay by remember(key) { mutableStateOf(initialAllDay) }
    var time by remember(key) { mutableStateOf(initialTime) }

    LaunchedEffect(key, initialAllDay, initialTime) {
        isAllDay = initialAllDay
        time = initialTime
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(CardColor), RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("All Day", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.weight(1f))
                Switch(
                    checked = isAllDay,
                    onCheckedChange = {
                        isAllDay = it
                        if (!it) {
                            time = LocalTime.of(10, 0)
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = BackgroundColor,
                        checkedTrackColor = PrimaryColor,
                        uncheckedTrackColor = Color.LightGray,
                        uncheckedThumbColor = BackgroundColor
                    )
                )
            }

            // Time picker
            AnimatedVisibility(
                visible = !isAllDay,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), thickness = 1.dp, color = Color.LightGray)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Time", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.weight(1f))

                        var showTimePicker by remember { mutableStateOf(false) }
                        val timeFormatter = DateTimeFormatter.ofPattern("h:mm a")
                        val displayTime = time.format(timeFormatter)

                        Button(
                            onClick = { showTimePicker = true },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor)
                        ) {
                            Text(displayTime)
                        }

                        if (showTimePicker) {
                            val timePickerState = rememberTimePickerState(
                                initialHour = time.hour,
                                initialMinute = time.minute,
                                is24Hour = false
                            )

                            AlertDialog(
                                onDismissRequest = { showTimePicker = false },
                                confirmButton = {
                                    TextButton(
                                        onClick = {
                                            val newTime = LocalTime.of(timePickerState.hour, timePickerState.minute, 0, 0)
                                            time = validateStartTime(newTime)
                                            showTimePicker = false
                                        }
                                    ) {
                                        Text("OK", color = Color.Black, fontSize = 16.sp)
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showTimePicker = false }) {
                                        Text("Cancel", color = Color.Black, fontSize = 16.sp)
                                    }
                                },
                                containerColor = BackgroundColor,
                                text = {
                                    TimePicker(
                                        state = timePickerState,
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

    return Pair(isAllDay, time)
}

/* DROPDOWN FIELD */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun dropdownField(
    label: String,
    items: List<String>,
    initialSelection: Int? = null,
    key: Int = 0,
    locked: Boolean = false
): Int? {
    var selectedIndex by remember(key) { mutableStateOf(initialSelection) }
    var showDropdown by remember(key) { mutableStateOf(false) }

    LaunchedEffect(key, initialSelection) {
        selectedIndex = initialSelection
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (locked) Color(CardColor) else Color(CardColor), RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = !locked
            ) {
                if (!locked) showDropdown = !showDropdown
            }
            .padding(16.dp)
    ) {
        Column {
            Text(label, fontSize = 16.sp, fontWeight = FontWeight.Medium,
                color = Color.Black)
            Spacer(modifier = Modifier.height(12.dp))

            // Display selected item or "None"
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (locked) Color.LightGray else BackgroundColor, RoundedCornerShape(8.dp))
                    .border(1.dp, Color.LightGray, RoundedCornerShape(8.dp))
                    .padding(12.dp)
            ) {
                Text(
                    if (selectedIndex != null) items.getOrNull(selectedIndex!!) ?: "None"
                    else "None",
                    fontSize = 16.sp,
                    color = Color.Black
                )
            }

            // Dropdown list
            if (!locked) {
                AnimatedVisibility(
                    visible = showDropdown,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column(
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .heightIn(max = 180.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        // "None" option
                        val isNoneSelected = selectedIndex == null
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .background(
                                    if (isNoneSelected) PrimaryColor else Color.LightGray,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    selectedIndex = null
                                    showDropdown = false
                                }
                                .padding(12.dp)
                        ) {
                            Text(
                                "None",
                                fontSize = 16.sp,
                                color = if (isNoneSelected) BackgroundColor else Color.Black,
                                fontWeight = if (isNoneSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }

                        // Regular items
                        items.forEachIndexed { index, item ->
                            val isSelected = selectedIndex == index
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .background(
                                        if (isSelected) PrimaryColor else Color.LightGray,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        selectedIndex = index
                                        showDropdown = false
                                    }
                                    .padding(12.dp)
                            ) {
                                Text(
                                    item,
                                    fontSize = 16.sp,
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

    return selectedIndex
}

/* ALL DAY TASK PICKER FIELD */
@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun allDayTaskPickerField(
    initialAllDay: Boolean = false,
    initialDate: java.time.LocalDate = java.time.LocalDate.now().plusDays(1),
    key: Int = 0
): Pair<Boolean, java.time.LocalDate> {
    var isAllDay by remember(key) { mutableStateOf(initialAllDay) }
    var date by remember(key) { mutableStateOf(initialDate) }

    LaunchedEffect(key, initialAllDay, initialDate) {
        isAllDay = initialAllDay
        date = initialDate
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(CardColor), RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("All Day", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.weight(1f))
                Switch(
                    checked = isAllDay,
                    onCheckedChange = { isAllDay = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = BackgroundColor,
                        checkedTrackColor = PrimaryColor,
                        uncheckedTrackColor = Color.LightGray,
                        uncheckedThumbColor = BackgroundColor
                    )
                )
            }

            // When ALL DAY is checked: show date picker
            AnimatedVisibility(
                visible = isAllDay,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), thickness = 1.dp, color = Color.LightGray)
                    var showDatePicker by remember { mutableStateOf(false) }
                    val dateFormatter = java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy")

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Date", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.weight(1f))
                        Button(
                            onClick = { showDatePicker = true },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor)
                        ) {
                            Text(date.format(dateFormatter))
                        }
                    }

                    if (showDatePicker) {
                        val datePickerState = rememberDatePickerState(
                            initialSelectedDateMillis = date.toEpochDay() * 86400000L
                        )
                        DatePickerDialog(
                            onDismissRequest = { showDatePicker = false },
                            confirmButton = {
                                TextButton(onClick = {
                                    datePickerState.selectedDateMillis?.let {
                                        date = java.time.LocalDate.ofEpochDay(it / 86400000L)
                                    }
                                    showDatePicker = false
                                }) {
                                    Text("OK", color = Color.Black, fontSize = 16.sp)
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showDatePicker = false }) {
                                    Text("Cancel", color = Color.Black, fontSize = 16.sp)
                                }
                            },
                            colors = DatePickerDefaults.colors(containerColor = BackgroundColor)
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
                }
            }
        }
    }

    return Pair(isAllDay, date)
}

/* AUTO SCHEDULE TASK PICKER FIELD */
@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun autoScheduleTaskPickerField(
    initialAutoScheduleTask: Boolean = false,
    initialDurationHours: Int = 0,
    initialDurationMinutes: Int = 30,
    initialBreakable: Boolean = false,
    breakableLockedByDuration: Boolean = false,
    key: Int = 0,
    onDurationChange: ((Int, Int) -> Unit)? = null
): Tuple4<Boolean, Int, Int, Boolean> {
    var autoScheduleTask by remember(key) { mutableStateOf(initialAutoScheduleTask) }
    var durationHours by remember(key) { mutableIntStateOf(initialDurationHours) }
    var durationMinutes by remember(key) { mutableIntStateOf(initialDurationMinutes) }
    var isBreakable by remember(key) { mutableStateOf(initialBreakable) }

    LaunchedEffect(key, initialAutoScheduleTask, initialDurationHours, initialDurationMinutes, initialBreakable) {
        autoScheduleTask = initialAutoScheduleTask
        durationHours = initialDurationHours
        durationMinutes = initialDurationMinutes
        isBreakable = initialBreakable
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(CardColor), RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Auto Schedule Task", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.weight(1f))
                Switch(
                    checked = autoScheduleTask,
                    onCheckedChange = {
                        autoScheduleTask = it
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = BackgroundColor,
                        checkedTrackColor = PrimaryColor,
                        uncheckedTrackColor = Color.LightGray,
                        uncheckedThumbColor = BackgroundColor
                    )
                )
            }

            // Expandable task fields
            AnimatedVisibility(
                visible = autoScheduleTask,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), thickness = 1.dp, color = Color.LightGray)
                    // Duration picker
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Duration", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.weight(1f))

                        var showDurationPicker by remember { mutableStateOf(false) }
                        var tempHours by remember { mutableIntStateOf(durationHours) }
                        var tempMinutes by remember { mutableIntStateOf(durationMinutes) }

                        Button(
                            onClick = {
                                tempHours = durationHours
                                tempMinutes = durationMinutes
                                showDurationPicker = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor)
                        ) {
                            Text("${durationHours}h ${durationMinutes}m")
                        }

                        if (showDurationPicker) {
                            AlertDialog(
                                onDismissRequest = { showDurationPicker = false },
                                confirmButton = {
                                    TextButton(onClick = {
                                        durationHours = tempHours
                                        durationMinutes = tempMinutes
                                        showDurationPicker = false
                                        onDurationChange?.invoke(durationHours, durationMinutes)
                                    }) {
                                        Text("OK", color = Color.Black, fontSize = 16.sp)
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showDurationPicker = false }) {
                                        Text("Cancel", color = Color.Black, fontSize = 16.sp)
                                    }
                                },
                                containerColor = BackgroundColor,
                                text = {
                                    Row(
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("Hours", fontSize = 12.sp)
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Button(
                                                onClick = { tempHours++ },
                                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor)
                                            ) { Text("▲") }
                                            Text(
                                                tempHours.toString(),
                                                fontSize = 24.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(vertical = 8.dp)
                                            )
                                            Button(
                                                onClick = {
                                                    if (tempHours > 0 && !(tempHours == 1 && tempMinutes == 0)) {
                                                        tempHours--
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor)
                                            ) { Text("▼") }
                                        }

                                        Spacer(modifier = Modifier.width(32.dp))

                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("Minutes", fontSize = 12.sp)
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Button(
                                                onClick = {
                                                    val newMinutes = (tempMinutes + 5) % 60
                                                    if (!(tempHours == 0 && newMinutes == 0)) {
                                                        tempMinutes = newMinutes
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor)
                                            ) { Text("▲") }
                                            Text(
                                                tempMinutes.toString(),
                                                fontSize = 24.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(vertical = 8.dp)
                                            )
                                            Button(
                                                onClick = {
                                                    val newMinutes = if (tempMinutes - 5 < 0) 55 else tempMinutes - 5
                                                    if (!(tempHours == 0 && newMinutes == 0)) {
                                                        tempMinutes = newMinutes
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryColor)
                                            ) { Text("▼") }
                                        }
                                    }
                                }
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), thickness = 1.dp, color = Color.LightGray)

                    // Breakable toggle
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val displayChecked = if (breakableLockedByDuration) true else isBreakable

                        Text(
                            "Breakable",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.Black
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Switch(
                            checked = displayChecked,
                            onCheckedChange = {
                                if (!breakableLockedByDuration) isBreakable = it
                            },
                            enabled = !breakableLockedByDuration,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = BackgroundColor,
                                checkedTrackColor = PrimaryColor,
                                uncheckedTrackColor = Color.LightGray,
                                uncheckedThumbColor = BackgroundColor,
                                disabledCheckedThumbColor = BackgroundColor,
                                disabledCheckedTrackColor = Color.LightGray,
                                disabledUncheckedThumbColor = BackgroundColor,
                                disabledUncheckedTrackColor = Color.LightGray
                            )
                        )
                    }
                }
            }
        }
    }

    return Tuple4(autoScheduleTask, durationHours, durationMinutes, isBreakable)
}

data class Tuple4<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)