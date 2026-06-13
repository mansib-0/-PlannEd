package com.planned

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.runBlocking
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

/* LOAD SAMPLE OBJECTS INTO DATABASE */
@RequiresApi(Build.VERSION_CODES.O)
fun runSample(context: Context, db: AppDatabase) = runBlocking {

    // Determine week bounds based on settings
    val startOnMonday = SettingsManager.settings?.startWeekOnMonday ?: false
    val firstDayOfWeek = if (startOnMonday) DayOfWeek.MONDAY else DayOfWeek.SUNDAY
    val lastDayOfWeek = if (startOnMonday) DayOfWeek.SUNDAY else DayOfWeek.SATURDAY

    val today = LocalDate.now().plusDays(7)
    val weekStart = today.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
    val weekEnd = today.with(TemporalAdjusters.nextOrSame(lastDayOfWeek))

    // Specific days this week
    val thisSunday    = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
    val thisMonday    = thisSunday.plusDays(1)
    val thisTuesday   = thisSunday.plusDays(2)
    val thisThursday  = thisSunday.plusDays(4)
    val thisSaturday  = thisSunday.plusDays(6)

    // CATEGORIES

    val schoolCatId = CategoryManager.insert(db, "School", "Academic classes and coursework", Preset12)
    val ecCatId     = CategoryManager.insert(db, "Extracurricular", "Band, piano, and other activities", Preset5)
    val homeCatId   = CategoryManager.insert(db, "Home", "Household tasks and personal errands", Preset9)

    // EVENTS

    val englishEventId = EventManager.insert(
        context = context,
        db = db,
        title = "English Class",
        notes = "Prof. Harrison — Room 204",
        color = null,
        startDate = weekStart,
        endDate = weekEnd,
        startTime = LocalTime.of(11, 0),
        endTime = LocalTime.of(12, 30),
        recurFreq = RecurrenceFrequency.WEEKLY,
        recurRule = RecurrenceRule(daysOfWeek = listOf(DayOfWeek.SUNDAY.value, DayOfWeek.TUESDAY.value)),
        categoryId = schoolCatId
    )

    val chemistryEventId = EventManager.insert(
        context = context,
        db = db,
        title = "Chemistry Class",
        notes = "Prof. Chen — Room 114",
        color = Preset2,
        startDate = weekStart,
        endDate = weekEnd,
        startTime = LocalTime.of(9, 20),
        endTime = LocalTime.of(10, 50),
        recurFreq = RecurrenceFrequency.WEEKLY,
        recurRule = RecurrenceRule(daysOfWeek = listOf(DayOfWeek.SUNDAY.value, DayOfWeek.TUESDAY.value)),
        categoryId = schoolCatId
    )

    val biologyEventId = EventManager.insert(
        context = context,
        db = db,
        title = "Biology Class",
        notes = "Prof. Kamal — Room 125",
        color = null,
        startDate = weekStart,
        endDate = weekEnd,
        startTime = LocalTime.of(9, 20),
        endTime = LocalTime.of(10, 50),
        recurFreq = RecurrenceFrequency.WEEKLY,
        recurRule = RecurrenceRule(daysOfWeek = listOf(DayOfWeek.MONDAY.value, DayOfWeek.WEDNESDAY.value)),
        categoryId = schoolCatId
    )

    val physicsEventId = EventManager.insert(
        context = context,
        db = db,
        title = "Physics Class",
        notes = "Prof. Anne — Room 112",
        color = null,
        startDate = weekStart,
        endDate = weekEnd,
        startTime = LocalTime.of(11, 0),
        endTime = LocalTime.of(12, 30),
        recurFreq = RecurrenceFrequency.WEEKLY,
        recurRule = RecurrenceRule(daysOfWeek = listOf(DayOfWeek.MONDAY.value, DayOfWeek.WEDNESDAY.value)),
        categoryId = schoolCatId
    )

    val mathEventId = EventManager.insert(
        context = context,
        db = db,
        title = "Math Class",
        notes = "Prof. Williams — Room 308",
        color = Preset2,
        startDate = weekStart,
        endDate = weekEnd,
        startTime = LocalTime.of(12, 40),
        endTime = LocalTime.of(14, 10),
        recurFreq = RecurrenceFrequency.WEEKLY,
        recurRule = RecurrenceRule(daysOfWeek = listOf(DayOfWeek.SUNDAY.value, DayOfWeek.TUESDAY.value)),
        categoryId = schoolCatId
    )

    val socialStudiesEventId = EventManager.insert(
        context = context,
        db = db,
        title = "Social Studies Class",
        notes = "Prof. Thalia — Room 215",
        color = null,
        startDate = weekStart,
        endDate = weekEnd,
        startTime = LocalTime.of(12, 40),
        endTime = LocalTime.of(14, 10),
        recurFreq = RecurrenceFrequency.WEEKLY,
        recurRule = RecurrenceRule(daysOfWeek = listOf(DayOfWeek.MONDAY.value, DayOfWeek.WEDNESDAY.value)),
        categoryId = schoolCatId
    )

    val bandPracticeEventId = EventManager.insert(
        context = context,
        db = db,
        title = "Band Practice",
        notes = "Main Auditorium",
        color = null,
        startDate = weekStart,
        endDate = weekEnd,
        startTime = LocalTime.of(12, 40),
        endTime = LocalTime.of(14, 10),
        recurFreq = RecurrenceFrequency.WEEKLY,
        recurRule = RecurrenceRule(daysOfWeek = listOf(DayOfWeek.THURSDAY.value)),
        categoryId = ecCatId
    )

    val pianoLessonEventId = EventManager.insert(
        context = context,
        db = db,
        title = "Piano Lesson",
        notes = "Music Room 3",
        color = null,
        startDate = weekStart,
        endDate = weekEnd,
        startTime = LocalTime.of(14, 20),
        endTime = LocalTime.of(15, 50),
        recurFreq = RecurrenceFrequency.WEEKLY,
        recurRule = RecurrenceRule(daysOfWeek = listOf(DayOfWeek.THURSDAY.value)),
        categoryId = ecCatId
    )

    EventManager.insert(
        context = context,
        db = db,
        title = "Morning Yoga",
        notes = "15m stretch, 30m flow sequence, 15m cool down",
        color = null,
        startDate = weekStart,
        endDate = weekEnd,
        startTime = LocalTime.of(11, 0),
        endTime = LocalTime.of(12, 0),
        recurFreq = RecurrenceFrequency.WEEKLY,
        recurRule = RecurrenceRule(daysOfWeek = listOf(DayOfWeek.THURSDAY.value)),
        categoryId = homeCatId
    )

    EventManager.insert(
        context = context,
        db = db,
        title = "Meal Prep",
        notes = "Cook rice, chicken, and veggies",
        color = null,
        startDate = weekStart,
        endDate = weekEnd,
        startTime = LocalTime.of(18, 15),
        endTime = LocalTime.of(19, 45),
        recurFreq = RecurrenceFrequency.WEEKLY,
        recurRule = RecurrenceRule(daysOfWeek = listOf(DayOfWeek.THURSDAY.value)),
        categoryId = homeCatId
    )

    // REMINDERS

    ReminderManager.insert(
        context = context,
        db = db,
        title = "Doctor's Appointment",
        notes = "Get sore throat checked out",
        startDate = thisMonday,
        endDate = null,
        time = LocalTime.of(14, 30),
        allDay = false,
        recurFreq = RecurrenceFrequency.NONE,
        recurRule = RecurrenceRule(),
        categoryId = homeCatId
    )

    ReminderManager.insert(
        context = context,
        db = db,
        title = "Visit Counsellor's Office",
        notes = "Discuss next semester's classes",
        startDate = thisThursday,
        endDate = null,
        time = null,
        allDay = true,
        recurFreq = RecurrenceFrequency.NONE,
        recurRule = RecurrenceRule(),
        categoryId = schoolCatId
    )

    // DEADLINES

    val chemQuizDeadlineId = DeadlineManager.insert(
        context = context,
        db = db,
        title = "Chemistry Quiz",
        notes = "Chapter 3 Atomic Structure",
        date = thisTuesday,
        time = LocalTime.of(12, 40),
        categoryId = schoolCatId,
        eventId = chemistryEventId
    )

    val shakespeareEssayId = DeadlineManager.insert(
        context = context,
        db = db,
        title = "Shakespeare Essay",
        notes = "Upload on Canvas",
        date = thisThursday,
        time = LocalTime.of(20, 0),
        categoryId = schoolCatId,
        eventId = englishEventId
    )

    DeadlineManager.insert(
        context = context,
        db = db,
        title = "Biology Homework",
        notes = "Upload on Canvas",
        date = thisSaturday,
        time = LocalTime.of(20, 0),
        categoryId = schoolCatId,
        eventId = biologyEventId
    )

    // TASK BUCKETS

    TaskBucketManager.insert(
        context = context,
        db = db,
        startDate = weekStart,
        endDate = weekEnd,
        startTime = LocalTime.of(16, 0),
        endTime = LocalTime.of(18, 45),
        recurFreq = RecurrenceFrequency.WEEKLY,
        recurRule = RecurrenceRule(daysOfWeek = listOf(
            DayOfWeek.MONDAY.value,
            DayOfWeek.TUESDAY.value,
            DayOfWeek.WEDNESDAY.value,
            DayOfWeek.THURSDAY.value
        ))
    )

    TaskBucketManager.insert(
        context = context,
        db = db,
        startDate = weekStart,
        endDate = weekEnd,
        startTime = LocalTime.of(14, 0),
        endTime = LocalTime.of(17, 15),
        recurFreq = RecurrenceFrequency.WEEKLY,
        recurRule = RecurrenceRule(daysOfWeek = listOf(
            DayOfWeek.FRIDAY.value
        ))
    )

    // ALL-DAY TASKS

    TaskManager.insert(
        context = context,
        db = db,
        title = "Pick up groceries",
        notes = "Bread, Milk, Eggs",
        allDay = thisMonday,
        breakable = false,
        startDate = null,
        startTime = null,
        predictedDuration = 60,
        categoryId = homeCatId,
        eventId = null,
        deadlineId = null,
        dependencyTaskId = null
    )

    TaskManager.insert(
        context = context,
        db = db,
        title = "Clean bedroom",
        notes = "Change bedsheets, organize closet",
        allDay = thisThursday,
        breakable = false,
        startDate = null,
        startTime = null,
        predictedDuration = 60,
        categoryId = homeCatId,
        eventId = null,
        deadlineId = null,
        dependencyTaskId = null
    )

    // MANUAL TASKS

    TaskManager.insert(
        context = context,
        db = db,
        title = "Read Hamlet",
        notes = "Pages 34–150",
        allDay = null,
        breakable = false,
        startDate = thisThursday,
        startTime = LocalTime.of(19, 30),
        predictedDuration = 60,
        categoryId = schoolCatId,
        eventId = englishEventId,
        deadlineId = null,
        dependencyTaskId = null
    )

    // AUTO-SCHEDULED TASKS

    TaskManager.insert(
        context = context,
        db = db,
        title = "Math Homework",
        notes = "Ch. 3 exercise 3.4 Q 1-20",
        allDay = null,
        breakable = false,
        startDate = null,
        startTime = null,
        predictedDuration = 60,
        categoryId = schoolCatId,
        eventId = mathEventId,
        deadlineId = null,
        dependencyTaskId = null
    )

    TaskManager.insert(
        context = context,
        db = db,
        title = "Rehearse Setlist",
        notes = "Run through full band setlist on beat",
        allDay = null,
        breakable = false,
        startDate = null,
        startTime = null,
        predictedDuration = 60,
        categoryId = ecCatId,
        eventId = bandPracticeEventId,
        deadlineId = null,
        dependencyTaskId = null
    )

    TaskManager.insert(
        context = context,
        db = db,
        title = "Hamlet Essay",
        notes = "Analysis on protagonist versus antagonist",
        allDay = null,
        breakable = false,
        startDate = null,
        startTime = null,
        predictedDuration = 60,
        categoryId = schoolCatId,
        eventId = englishEventId,
        deadlineId = shakespeareEssayId,
        dependencyTaskId = null
    )

    TaskManager.insert(
        context = context,
        db = db,
        title = "Make Reading List",
        notes = "3 books to read over the vacation",
        allDay = null,
        breakable = false,
        startDate = null,
        startTime = null,
        predictedDuration = 60,
        categoryId = null,
        eventId = null,
        deadlineId = null,
        dependencyTaskId = null
    )

    val copyChemNotesId = TaskManager.insert(
        context = context,
        db = db,
        title = "Copy Chem Notes",
        notes = "Get notes from Radia on missed classes",
        allDay = null,
        breakable = false,
        startDate = null,
        startTime = null,
        predictedDuration = 60,
        categoryId = schoolCatId,
        eventId = chemistryEventId,
        deadlineId = null,
        dependencyTaskId = null
    )

    TaskManager.insert(
        context = context,
        db = db,
        title = "Social Studies Project",
        notes = "Research important figures in ancient civilizations",
        allDay = null,
        breakable = false,
        startDate = null,
        startTime = null,
        predictedDuration = 60,
        categoryId = schoolCatId,
        eventId = socialStudiesEventId,
        deadlineId = null,
        dependencyTaskId = null
    )

    TaskManager.insert(
        context = context,
        db = db,
        title = "Review Finances",
        notes = "Go over recent purchases and update budget",
        allDay = null,
        breakable = false,
        startDate = null,
        startTime = null,
        predictedDuration = 60,
        categoryId = null,
        eventId = null,
        deadlineId = null,
        dependencyTaskId = null
    )

    TaskManager.insert(
        context = context,
        db = db,
        title = "Practice Staccato Swap",
        notes = "Drill staccato hand transitions from lesson book",
        allDay = null,
        breakable = false,
        startDate = null,
        startTime = null,
        predictedDuration = 60,
        categoryId = ecCatId,
        eventId = pianoLessonEventId,
        deadlineId = null,
        dependencyTaskId = null
    )

    TaskManager.insert(
        context = context,
        db = db,
        title = "Study Atomic Structure",
        notes = "Ch. 4 pages 68-91",
        allDay = null,
        breakable = false,
        startDate = null,
        startTime = null,
        predictedDuration = 60,
        categoryId = schoolCatId,
        eventId = chemistryEventId,
        deadlineId = chemQuizDeadlineId,
        dependencyTaskId = copyChemNotesId
    )

    generateTaskIntervals(context, db)

    // COMPLETED TASKS

    val lastWeekMonday = today.minusWeeks(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    // Math Class tasks
    db.taskDao().insert(MasterTask(
        title = "Math Homework 1", noIntervals = 0,
        predictedDuration = 45, overTime = 20, deadlineMissed = false,
        status = 3, categoryId = schoolCatId, eventId = mathEventId,
        completedAt = lastWeekMonday.atTime(17, 5)
    ))
    db.taskDao().insert(MasterTask(
        title = "Math Homework 2", noIntervals = 0,
        predictedDuration = 60, overTime = 35, deadlineMissed = true,
        status = 3, categoryId = schoolCatId, eventId = mathEventId,
        completedAt = lastWeekMonday.plusDays(2).atTime(18, 35)
    ))
    db.taskDao().insert(MasterTask(
        title = "Math Homework 3", noIntervals = 0,
        predictedDuration = 30, overTime = 25, deadlineMissed = true,
        status = 3, categoryId = schoolCatId, eventId = mathEventId,
        completedAt = lastWeekMonday.plusDays(4).atTime(16, 55)
    ))

    // English Class tasks
    db.taskDao().insert(MasterTask(
        title = "English Essay 1", noIntervals = 0,
        predictedDuration = 90, overTime = 15, deadlineMissed = false,
        status = 3, categoryId = schoolCatId, eventId = englishEventId,
        completedAt = lastWeekMonday.plusDays(1).atTime(17, 45)
    ))
    db.taskDao().insert(MasterTask(
        title = "English Essay 2", noIntervals = 0,
        predictedDuration = 75, overTime = 0, deadlineMissed = false,
        status = 3, categoryId = schoolCatId, eventId = englishEventId,
        completedAt = lastWeekMonday.plusDays(3).atTime(16, 15)
    ))
    db.taskDao().insert(MasterTask(
        title = "English Essay 3", noIntervals = 0,
        predictedDuration = 120, overTime = 30, deadlineMissed = true,
        status = 3, categoryId = schoolCatId, eventId = englishEventId,
        completedAt = lastWeekMonday.plusDays(5).atTime(19, 30)
    ))

    // Piano Lesson tasks
    db.taskDao().insert(MasterTask(
        title = "Piano Practice 1", noIntervals = 0,
        predictedDuration = 30, overTime = 5, deadlineMissed = false,
        status = 3, categoryId = ecCatId, eventId = pianoLessonEventId,
        completedAt = lastWeekMonday.atTime(16, 35)
    ))
    db.taskDao().insert(MasterTask(
        title = "Piano Practice 2", noIntervals = 0,
        predictedDuration = 25, overTime = 0, deadlineMissed = false,
        status = 3, categoryId = ecCatId, eventId = pianoLessonEventId,
        completedAt = lastWeekMonday.plusDays(2).atTime(17, 25)
    ))
    db.taskDao().insert(MasterTask(
        title = "Piano Practice 3", noIntervals = 0,
        predictedDuration = 20, overTime = 10, deadlineMissed = false,
        status = 3, categoryId = ecCatId, eventId = pianoLessonEventId,
        completedAt = lastWeekMonday.plusDays(4).atTime(16, 30)
    ))

    // Home tasks
    db.taskDao().insert(MasterTask(
        title = "Clean Kitchen", noIntervals = 0,
        predictedDuration = 45, overTime = 15, deadlineMissed = false,
        status = 3, categoryId = homeCatId, eventId = null,
        completedAt = lastWeekMonday.plusDays(1).atTime(18, 0)
    ))
    db.taskDao().insert(MasterTask(
        title = "Laundry", noIntervals = 0,
        predictedDuration = 30, overTime = 0, deadlineMissed = false,
        status = 3, categoryId = homeCatId, eventId = null,
        completedAt = lastWeekMonday.plusDays(3).atTime(15, 30)
    ))
    db.taskDao().insert(MasterTask(
        title = "Grocery Run", noIntervals = 0,
        predictedDuration = 60, overTime = 20, deadlineMissed = false,
        status = 3, categoryId = homeCatId, eventId = null,
        completedAt = lastWeekMonday.plusDays(5).atTime(17, 20)
    ))

    // PATCH ATI RECORDS

    // CategoryATI
    db.categoryATIDao().getById(schoolCatId)?.let {
        db.categoryATIDao().update(it.copy(
            tasksCompleted = 6,
            deadlineMissCount = 3,
            avgOvertime = 20.8333f,
            predictedPadding = 20,
            paddingSlope = -0.009524f,
            paddingIntercept = 21.5f,
            score = 0.3189f
        ))
    }
    db.categoryATIDao().getById(ecCatId)?.let {
        db.categoryATIDao().update(it.copy(
            tasksCompleted = 3,
            deadlineMissCount = 0,
            avgOvertime = 5.0f,
            predictedPadding = 5,
            paddingSlope = -0.5f,
            paddingIntercept = 17.5f,
            score = 0.0333f
        ))
    }
    db.categoryATIDao().getById(homeCatId)?.let {
        db.categoryATIDao().update(it.copy(
            tasksCompleted = 3,
            deadlineMissCount = 0,
            avgOvertime = 11.6667f,
            predictedPadding = 15,
            paddingSlope = 0.6667f,
            paddingIntercept = -18.3333f,
            score = 0.0778f
        ))
    }

    // EventATI
    db.eventATIDao().getById(mathEventId)?.let {
        db.eventATIDao().update(it.copy(
            tasksCompleted = 3,
            deadlineMissCount = 2,
            avgOvertime = 26.6667f,
            predictedPadding = 30,
            paddingSlope = 0.3333f,
            paddingIntercept = 11.6667f,
            score = 0.2978f
        ))
    }
    db.eventATIDao().getById(englishEventId)?.let {
        db.eventATIDao().update(it.copy(
            tasksCompleted = 3,
            deadlineMissCount = 1,
            avgOvertime = 15.0f,
            predictedPadding = 15,
            paddingSlope = 0.6429f,
            paddingIntercept = -46.0714f,
            score = 0.16f
        ))
    }
    db.eventATIDao().getById(pianoLessonEventId)?.let {
        db.eventATIDao().update(it.copy(
            tasksCompleted = 3,
            deadlineMissCount = 0,
            avgOvertime = 5.0f,
            predictedPadding = 5,
            paddingSlope = -0.5f,
            paddingIntercept = 17.5f,
            score = 0.0333f
        ))
    }
    db.eventATIDao().getById(0)?.let {
        db.eventATIDao().update(it.copy(
            tasksCompleted    = 3,
            deadlineMissCount = 0,
            avgOvertime       = 11.6667f,
            predictedPadding  = 15,
            paddingSlope      = 0.6667f,
            paddingIntercept  = -18.3333f,
            score             = 0.0778f
        ))
    }

    generateTaskIntervals(context, db)
}