package com.planned

import androidx.room.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/* ENTITIES */

// Category
//<editor-fold desc="Category">

@Entity
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,

    val title: String,
    val notes: String? = null,
    val color: String
)
//</editor-fold>

// Event
//<editor-fold desc="Event">

@Entity(
    foreignKeys = [
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL
        )
    ]
)
data class MasterEvent(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,

    val title: String,
    val notes: String? = null,
    val color: String? = null,

    val startDate: LocalDate,               // date when event occurs
    val endDate: LocalDate? = null,         // for recurring events only, date when occurrences stop

    val startTime: LocalTime,
    val endTime: LocalTime,

    val recurFreq: RecurrenceFrequency,     // only once / daily / weekly / monthly / yearly
    val recurRule: RecurrenceRule,          // none / days of week 1-7 / date 1-31 / date DD-MM

    val categoryId: Int? = null,
)

@Entity(
    foreignKeys = [
        ForeignKey(
            entity = MasterEvent::class,
            parentColumns = ["id"],
            childColumns = ["masterEventId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class EventOccurrence(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,

    val masterEventId: Int,

    val notes: String? = null,

    val occurDate: LocalDate,
    val startTime: LocalTime,
    val endTime: LocalTime
)
//</editor-fold>

// Deadline
//<editor-fold desc="Deadline">

@Entity(
    foreignKeys = [
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = MasterEvent::class,
            parentColumns = ["id"],
            childColumns = ["eventId"],
            onDelete = ForeignKey.SET_NULL
        )
    ]
)
data class Deadline(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,

    val title: String,
    val notes: String? = null,

    val date: LocalDate,
    val time: LocalTime,

    val categoryId: Int? = null,
    val eventId: Int? = null
)
//</editor-fold>

// Task Bucket
//<editor-fold desc="Bucket">

@Entity
data class MasterTaskBucket(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,

    val startDate: LocalDate,               // date when event occurs
    val endDate: LocalDate? = null,         // for recurring events only, date when occurrences stop

    val startTime: LocalTime,
    val endTime: LocalTime,

    val recurFreq: RecurrenceFrequency,     // only once / daily / weekly / monthly / yearly
    val recurRule: RecurrenceRule,          // none / days of week 1-7 / date 1-31 / date DD-MM
)

@Entity(
    foreignKeys = [
        ForeignKey(
            entity = MasterTaskBucket::class,
            parentColumns = ["id"],
            childColumns = ["masterBucketId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class TaskBucketOccurrence(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,

    val masterBucketId: Int,

    val occurDate: LocalDate,
    val startTime: LocalTime,
    val endTime: LocalTime,

    val isException: Boolean = false      //  back-end access only, if individually changed
)
//</editor-fold>

// Task
//<editor-fold desc="Task">

@Entity(
    foreignKeys = [
        ForeignKey(
            entity = MasterTask::class,
            parentColumns = ["id"],
            childColumns = ["dependencyTaskId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = MasterEvent::class,
            parentColumns = ["id"],
            childColumns = ["eventId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = Deadline::class,
            parentColumns = ["id"],
            childColumns = ["deadlineId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL
        )
    ]
)
data class MasterTask(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,

    val title: String,
    val notes: String? = null,
    val allDay: LocalDate? = null,          // null = not all-day; date = all-day task for that date
    val breakable: Boolean? = false,        // can be checked true
    val noIntervals: Int,                   // back-end only, how many intervals, 1 if not breakable

    val startDate: LocalDate? = null,       // null = auto, otherwise manual
    val startTime: LocalTime? = null,       // null = auto, otherwise manual

    val predictedDuration: Int,
    val actualDuration: Int? = null,        // back-end access to this only, total final duration

    val status: Int? = 1,                   // 1 = not started, 2 = in progress, 3 = completed
    val timeLeft: Int? = null,              // auto calculated
    val overTime: Int? = null,              // auto-calculated
    val deadlineMissed: Boolean = false,    // set at completion time
    val completedAt: LocalDateTime? = null, // set at completion time

    val dependencyTaskId: Int? = null,      // task that must be completed before this one
    val eventId: Int? = null,
    val deadlineId: Int? = null,
    val categoryId: Int? = null
)

@Entity(
    foreignKeys = [
        ForeignKey(
            entity = MasterTask::class,
            parentColumns = ["id"],
            childColumns = ["masterTaskId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class TaskInterval(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,

    val masterTaskId: Int,

    val intervalNo: Int,                    // interval number out of all intervals
    val notes: String? = null,

    val occurDate: LocalDate,

    val startTime: LocalTime,               // updates as task progresses
    val endTime: LocalTime,

    val status: Int? = 1,
    val timeLeft: Int? = null,              // auto calculated
    val overTime: Int? = null,              // auto-calculated
    val atiPadding: Int = 0                 // padding added by ATI at scheduling time
)
//</editor-fold>

// Reminder
//<editor-fold desc="Reminder">

// Reminder - MASTER
@Entity(
    foreignKeys = [
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL
        )
    ]
)
data class MasterReminder(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,

    val title: String,
    val notes: String? = null,

    val startDate: LocalDate,
    val endDate: LocalDate? = null,

    val time: LocalTime? = null,
    val allDay: Boolean,

    val recurFreq: RecurrenceFrequency,
    val recurRule: RecurrenceRule,

    val categoryId: Int? = null
)

@Entity(
    foreignKeys = [
        ForeignKey(
            entity = MasterReminder::class,
            parentColumns = ["id"],
            childColumns = ["masterReminderId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class ReminderOccurrence(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,

    val masterReminderId: Int,

    val notes: String? = null,

    val occurDate: LocalDate,
    val time: LocalTime? = null,
    val allDay: Boolean
)
//</editor-fold>

// ATI
//<editor-fold desc="ATI">

@Entity
data class CategoryATI(
    @PrimaryKey val categoryId: Int,
    val score: Float = 0f,
    val deadlineMissCount: Int = 0,     // out of last 10 completed tasks
    val avgOvertime: Float = 0f,        // average overtime in minutes, last 10 tasks
    val tasksCompleted: Int = 0,        // total completed tasks for this category
    val predictedPadding: Int = 0,      // evaluated at avgX, for display only
    val paddingSlope: Float = 0f,       // regression slope
    val paddingIntercept: Float = 0f    // regression intercept
)

@Entity
data class EventATI(
    @PrimaryKey val eventId: Int,
    val score: Float = 0f,
    val deadlineMissCount: Int = 0,     // out of last 10 completed tasks
    val avgOvertime: Float = 0f,        // average overtime in minutes, last 10 tasks
    val tasksCompleted: Int = 0,        // total completed tasks for this event
    val predictedPadding: Int = 0,      // evaluated at avgX, for display only
    val paddingSlope: Float = 0f,       // regression slope
    val paddingIntercept: Float = 0f    // regression intercept
)
//</editor-fold>

// Settings
//<editor-fold desc="Settings">

@Entity
data class AppSetting(
    @PrimaryKey val id: Int = 0,
    val startWeekOnMonday: Boolean = false,
    val primaryColor: String = "#FF4D4D4D",
    val breakDuration: Int = 5,
    val breakEvery: Int = 30,
    val atiPaddingEnabled: Boolean = true,

    // Notifications
    val notifTasksEnabled: Boolean = true,
    val notifTaskAllDayTime: Int = 25200,
    val notifEventsEnabled: Boolean = true,
    val notifEventLeadMinutes: Int = 10,
    val notifRemindersEnabled: Boolean = true,
    val notifReminderAllDayTime: Int = 25200,
    val notifDeadlinesEnabled: Boolean = true,
    val notifDeadlineTiming: String = "TIME_OF",
    val notifDeadlineLeadMinutes: Int = 10,
    val notifDeadlineTime: Int = 25200
)
//</editor-fold>

/* DAOs */
//<editor-fold desc="DAOs">

// Category
@Dao
interface CategoryDao {
    // Insert new category
    @Insert suspend fun insert(category: Category): Long

    // Fetch all categories
    @Query("SELECT * FROM Category ORDER BY title ASC") suspend fun getAll(): List<Category>

    // Fetch category by ID
    @Query("SELECT * FROM Category WHERE id = :categoryId")
    suspend fun getCategoryById(categoryId: Int): Category?

    // Update category
    @Update suspend fun update(category: Category)

    // Delete category
    @Query("DELETE FROM Category WHERE id = :categoryId")
    suspend fun deleteById(categoryId: Int)
}

// Event
@Dao
interface EventDao {
    // Insert new master event
    @Insert suspend fun insert(event: MasterEvent): Long

    // Insert new event occurrence
    @Insert suspend fun insertOccurrence(occurrence: EventOccurrence)

    // Fetch all master events ordered by date and time
    @Query("SELECT * FROM MasterEvent ORDER BY title ASC")
    suspend fun getAllMasterEvents(): List<MasterEvent>

    // Fetch master event by ID
    @Query("SELECT * FROM MasterEvent WHERE id = :eventId")
    suspend fun getMasterEventById(eventId: Int): MasterEvent?

    // Fetch all event occurrences ordered by date and time
    @Query("SELECT * FROM EventOccurrence ORDER BY occurDate, startTime")
    suspend fun getAllOccurrences(): List<EventOccurrence>

    // Fetch master events along with their occurrences
    @Transaction
    @Query("SELECT * FROM MasterEvent")
    suspend fun getAllEventsWithOccurrences(): List<MasterEventWithOccurrences>

    // Update master event
    @Update suspend fun update(event: MasterEvent)

    // Update event occurrence
    @Update suspend fun updateOccurrence(occurrence: EventOccurrence)

    // Delete event occurrence
    @Query("DELETE FROM EventOccurrence WHERE id = :occurrenceId")
    suspend fun deleteOccurrence(occurrenceId: Int)

    // Delete master event
    @Query("DELETE FROM MasterEvent WHERE id = :masterId")
    suspend fun deleteMasterEvent(masterId: Int)
}

// Deadline
@Dao
interface DeadlineDao {
    // Insert new deadline
    @Insert suspend fun insert(deadline: Deadline): Long

    // Fetch all deadlines
    @Query("SELECT * FROM Deadline ORDER BY title ASC") suspend fun getAll(): List<Deadline>

    // Fetch deadline by ID
    @Query("SELECT * FROM Deadline WHERE id = :deadlineId")
    suspend fun getDeadlineById(deadlineId: Int): Deadline?

    // Update deadline
    @Update suspend fun update(deadline: Deadline)

    // Delete deadline
    @Query("DELETE FROM Deadline WHERE id = :deadlineId")
    suspend fun deleteById(deadlineId: Int)
}

// Task Bucket
@Dao
interface TaskBucketDao {
    // Insert new master bucket
    @Insert suspend fun insert(masterBucket: MasterTaskBucket): Long

    // Insert new bucket occurrence
    @Insert suspend fun insertOccurrence(occurrence: TaskBucketOccurrence)

    // Fetch all master buckets ordered by date and time
    @Query("SELECT * FROM MasterTaskBucket ORDER BY startDate, startTime")
    suspend fun getAllMasterBuckets(): List<MasterTaskBucket>

    // Fetch master bucket by ID
    @Query("SELECT * FROM MasterTaskBucket WHERE id = :bucketId")
    suspend fun getMasterBucketById(bucketId: Int): MasterTaskBucket?

    // Fetch all bucket occurrences ordered by date and time
    @Query("SELECT * FROM TaskBucketOccurrence ORDER BY occurDate, startTime")
    suspend fun getAllBucketOccurrences(): List<TaskBucketOccurrence>

    // Fetch master buckets along with their occurrences
    @Transaction
    @Query("SELECT * FROM MasterTaskBucket")
    suspend fun getAllBucketsWithOccurrences(): List<MasterTaskBucketWithOccurrences>

    // Update master bucket
    @Update suspend fun update(masterBucket: MasterTaskBucket)

    // Update bucket occurrence
    @Update suspend fun updateOccurrence(bucketOccurrence: TaskBucketOccurrence)

    // Delete bucket occurrence
    @Query("DELETE FROM TaskBucketOccurrence WHERE id = :occurrenceId")
    suspend fun deleteOccurrence(occurrenceId: Int)

    // Delete master bucket
    @Query("DELETE FROM MasterTaskBucket WHERE id = :masterId")
    suspend fun deleteMasterBucket(masterId: Int)

    // Fetch all occurrences for a specific date
    @Query("SELECT * FROM TaskBucketOccurrence WHERE occurDate = :date")
    suspend fun getOccurrencesByDate(date: LocalDate): List<TaskBucketOccurrence>

    // Update bucket occurrence
    @Update
    suspend fun updateBucketOccurrence(occurrence: TaskBucketOccurrence)
}

// Task
@Dao
interface TaskDao {
    // Insert new master task
    @Insert suspend fun insert(task: MasterTask): Long

    // Insert new task interval
    @Insert suspend fun insertInterval(interval: TaskInterval)

    // Fetch all master tasks
    @Query("SELECT * FROM MasterTask") suspend fun getAllMasterTasks(): List<MasterTask>

    // Fetch all task intervals
    @Query("SELECT * FROM TaskInterval") suspend fun getAllIntervals(): List<TaskInterval>

    // Fetch master task by ID
    @Query("SELECT * FROM MasterTask WHERE id = :taskId") suspend fun getMasterTaskById(taskId: Int): MasterTask?

    // Fetch all intervals for a specific master task
    @Query("SELECT * FROM TaskInterval WHERE masterTaskId = :taskId") suspend fun getIntervalsForTask(taskId: Int): List<TaskInterval>

    // Update master task
    @Update suspend fun update(task: MasterTask)

    // Update task interval
    @Update suspend fun updateInterval(interval: TaskInterval)

    // Delete task interval
    @Query("DELETE FROM TaskInterval WHERE id = :intervalId")
    suspend fun deleteInterval(intervalId: Int)

    // Delete master task
    @Query("DELETE FROM MasterTask WHERE id = :taskId")
    suspend fun deleteMasterTask(taskId: Int)
}

// Reminder
@Dao
interface ReminderDao {
    // Insert new master reminder
    @Insert suspend fun insert(reminder: MasterReminder): Long

    // Insert new reminder occurrence
    @Insert suspend fun insertOccurrence(occurrence: ReminderOccurrence)

    // Fetch all master reminders ordered by date and time
    @Query("SELECT * FROM MasterReminder ORDER BY startDate, time")
    suspend fun getAllMasterReminders(): List<MasterReminder>

    // Fetch master reminder by ID
    @Query("SELECT * FROM MasterReminder WHERE id = :reminderId")
    suspend fun getMasterReminderById(reminderId: Int): MasterReminder?

    // Fetch all reminder occurrences ordered by date and time
    @Query("SELECT * FROM ReminderOccurrence ORDER BY occurDate, time")
    suspend fun getAllOccurrences(): List<ReminderOccurrence>

    // Fetch master reminders along with their occurrences
    @Transaction
    @Query("SELECT * FROM MasterReminder")
    suspend fun getAllRemindersWithOccurrences(): List<MasterReminderWithOccurrences>

    // Update master reminder
    @Update suspend fun update(reminder: MasterReminder)

    // Update reminder occurrence
    @Update suspend fun updateOccurrence(occurrence: ReminderOccurrence)

    // Delete reminder occurrence
    @Query("DELETE FROM ReminderOccurrence WHERE id = :occurrenceId")
    suspend fun deleteOccurrence(occurrenceId: Int)

    // Delete master reminder
    @Query("DELETE FROM MasterReminder WHERE id = :masterId")
    suspend fun deleteMasterReminder(masterId: Int)
}

// CategoryATI
@Dao
interface CategoryATIDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(categoryATI: CategoryATI)

    @Query("SELECT * FROM CategoryATI WHERE categoryId = :categoryId")
    suspend fun getById(categoryId: Int): CategoryATI?

    // Fetch the None record (categoryId = 0)
    @Query("SELECT * FROM CategoryATI WHERE categoryId = 0")
    suspend fun getNoneRecord(): CategoryATI?

    @Query("SELECT * FROM CategoryATI")
    suspend fun getAll(): List<CategoryATI>

    @Update
    suspend fun update(categoryATI: CategoryATI)

    @Query("DELETE FROM CategoryATI WHERE categoryId = :categoryId")
    suspend fun deleteById(categoryId: Int)
}

// EventATI
@Dao
interface EventATIDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(eventATI: EventATI)

    @Query("SELECT * FROM EventATI WHERE eventId = :eventId")
    suspend fun getById(eventId: Int): EventATI?

    // Fetch the None record (eventId = 0)
    @Query("SELECT * FROM EventATI WHERE eventId = 0")
    suspend fun getNoneRecord(): EventATI?

    @Query("SELECT * FROM EventATI")
    suspend fun getAll(): List<EventATI>

    @Update
    suspend fun update(eventATI: EventATI)

    @Query("DELETE FROM EventATI WHERE eventId = :eventId")
    suspend fun deleteById(eventId: Int)
}

// AppSetting
@Dao
interface SettingsDao {
    // Insert all settings
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(setting: AppSetting)

    // Field Updates
    @Query("UPDATE AppSetting SET primaryColor = :color WHERE id = 0")
    suspend fun updatePrimaryColor(color: String)
    @Query("UPDATE AppSetting SET startWeekOnMonday = :value WHERE id = 0")
    suspend fun updateStartWeekOnMonday(value: Boolean)
    @Query("UPDATE AppSetting SET breakDuration = :minutes WHERE id = 0")
    suspend fun updateBreakDuration(minutes: Int)
    @Query("UPDATE AppSetting SET breakEvery = :minutes WHERE id = 0")
    suspend fun updateBreakEvery(minutes: Int)
    @Query("UPDATE AppSetting SET atiPaddingEnabled = :value WHERE id = 0")
    suspend fun updateAtiPaddingEnabled(value: Boolean)
    @Query("UPDATE AppSetting SET notifTasksEnabled = :value WHERE id = 0")
    suspend fun updateNotifTasksEnabled(value: Boolean)
    @Query("UPDATE AppSetting SET notifEventsEnabled = :value WHERE id = 0")
    suspend fun updateNotifEventsEnabled(value: Boolean)
    @Query("UPDATE AppSetting SET notifEventLeadMinutes = :minutes WHERE id = 0")
    suspend fun updateNotifEventLeadMinutes(minutes: Int)
    @Query("UPDATE AppSetting SET notifRemindersEnabled = :value WHERE id = 0")
    suspend fun updateNotifRemindersEnabled(value: Boolean)
    @Query("UPDATE AppSetting SET notifReminderAllDayTime = :seconds WHERE id = 0")
    suspend fun updateNotifReminderAllDayTime(seconds: Int)
    @Query("UPDATE AppSetting SET notifTaskAllDayTime = :seconds WHERE id = 0")
    suspend fun updateNotifTaskAllDayTime(seconds: Int)
    @Query("UPDATE AppSetting SET notifDeadlinesEnabled = :value WHERE id = 0")
    suspend fun updateNotifDeadlinesEnabled(value: Boolean)
    @Query("UPDATE AppSetting SET notifDeadlineTiming = :timing WHERE id = 0")
    suspend fun updateNotifDeadlineTiming(timing: String)
    @Query("UPDATE AppSetting SET notifDeadlineTime = :seconds WHERE id = 0")
    suspend fun updateNotifDeadlineTime(seconds: Int)
    @Query("UPDATE AppSetting SET notifDeadlineLeadMinutes = :minutes WHERE id = 0")
    suspend fun updateNotifDeadlineLeadMinutes(minutes: Int)

    // Fetch all settings
    @Query("SELECT * FROM AppSetting WHERE id = 0")
    suspend fun getAll(): AppSetting?

    // Delete all settings
    @Query("DELETE FROM AppSetting")
    suspend fun deleteAll()
}
//</editor-fold>

/* RELATIONS */
//<editor-fold desc="Relations">

// Category
data class CategoryWithMasterEvents(
    @Embedded val category: Category,
    @Relation(parentColumn = "id", entityColumn = "categoryId")
    val masterEvents: List<MasterEvent>
)
data class CategoryWithDeadlines(
    @Embedded val category: Category,
    @Relation(parentColumn = "id", entityColumn = "categoryId")
    val deadlines: List<Deadline>
)
data class CategoryWithMasterTasks(
    @Embedded val category: Category,
    @Relation(parentColumn = "id", entityColumn = "categoryId")
    val masterTasks: List<MasterTask>
)

// MasterEvent
data class MasterEventWithOccurrences(
    @Embedded val masterEvent: MasterEvent,
    @Relation(parentColumn = "id", entityColumn = "masterEventId")
    val occurrences: List<EventOccurrence>
)
data class MasterEventWithDeadlines(
    @Embedded val masterEvent: MasterEvent,
    @Relation(parentColumn = "id", entityColumn = "eventId")
    val deadlines: List<Deadline>
)
data class MasterEventWithTasks(
    @Embedded val masterEvent: MasterEvent,
    @Relation(parentColumn = "id", entityColumn = "eventId")
    val masterTasks: List<MasterTask>
)
data class MasterEventWithATI(
    @Embedded val masterEvent: MasterEvent,
    @Relation(parentColumn = "id", entityColumn = "eventId")
    val eventATI: EventATI?
)

data class CategoryWithATI(
    @Embedded val category: Category,
    @Relation(parentColumn = "id", entityColumn = "categoryId")
    val categoryATI: CategoryATI?
)

// EventOccurrence
data class EventOccurrenceWithTasks(
    @Embedded val occurrence: EventOccurrence,
    @Relation(parentColumn = "masterEventId", entityColumn = "eventId")
    val tasks: List<MasterTask>
)

// MasterTaskBucket
data class MasterTaskBucketWithOccurrences(
    @Embedded val masterBucket: MasterTaskBucket,
    @Relation(parentColumn = "id", entityColumn = "masterBucketId")
    val occurrences: List<TaskBucketOccurrence>
)

// MasterTask
data class MasterTaskWithIntervals(
    @Embedded val masterTask: MasterTask,
    @Relation(parentColumn = "id", entityColumn = "masterTaskId")
    val intervals: List<TaskInterval>
)
data class MasterTaskWithEvent(
    @Embedded val masterTask: MasterTask,
    @Relation(parentColumn = "eventId", entityColumn = "id")
    val event: MasterEvent?
)
data class MasterTaskWithDeadline(
    @Embedded val masterTask: MasterTask,
    @Relation(parentColumn = "deadlineId", entityColumn = "id")
    val deadline: Deadline?
)
data class MasterTaskWithDependency(
    @Embedded val masterTask: MasterTask,
    @Relation(parentColumn = "dependencyTaskId", entityColumn = "id")
    val dependencyTask: MasterTask?
)

// MasterReminder
data class MasterReminderWithOccurrences(
    @Embedded val masterReminder: MasterReminder,
    @Relation(parentColumn = "id", entityColumn = "masterReminderId")
    val occurrences: List<ReminderOccurrence>
)

data class CategoryWithMasterReminders(
    @Embedded val category: Category,
    @Relation(parentColumn = "id", entityColumn = "categoryId")
    val masterReminders: List<MasterReminder>
)
//</editor-fold>

/* DATABASE */
//<editor-fold desc="Database">

@Database(
    entities = [
        Category::class,
        MasterEvent::class, EventOccurrence::class,
        Deadline::class,
        MasterTaskBucket::class, TaskBucketOccurrence::class,
        MasterTask::class, TaskInterval::class,
        MasterReminder::class, ReminderOccurrence::class,
        AppSetting::class,
        CategoryATI::class, EventATI::class
    ],
    version = 16
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun eventDao(): EventDao
    abstract fun deadlineDao(): DeadlineDao
    abstract fun taskBucketDao(): TaskBucketDao
    abstract fun taskDao(): TaskDao
    abstract fun reminderDao(): ReminderDao
    abstract fun settingsDao(): SettingsDao
    abstract fun categoryATIDao(): CategoryATIDao
    abstract fun eventATIDao(): EventATIDao
}
//</editor-fold>