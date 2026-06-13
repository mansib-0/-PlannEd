package com.planned

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.room.Room
import com.planned.ui.theme.PlanEdTheme
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object AppDatabaseProvider {
    @Volatile
    private var INSTANCE: AppDatabase? = null

    fun getDatabase(context: Context): AppDatabase {
        return INSTANCE ?: synchronized(this) {
            val instance = Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "planned-db"
            ).fallbackToDestructiveMigration(true).build()
            INSTANCE = instance
            instance
        }
    }
}

class MainActivity : ComponentActivity() {
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
                    .launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                startActivity(
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        Uri.parse("package:$packageName"))
                )
            }
        }

        val db = AppDatabaseProvider.getDatabase(this)
        val appContext = applicationContext

        // Handle deep-link from notification tap (cold start)
        intent?.let { handleDeepLink(it, db) }

        setContent {
            LaunchedEffect(Unit) {
                SettingsManager.load(db)
                trimAndExtendOccurrences(appContext, db)
                NotificationScheduler.rescheduleAll(appContext, db)
            }
            PlanEdTheme {
                AppNavigation(db)
            }
        }
    }

    // Called when the app is already running and a notification is tapped
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val db = AppDatabaseProvider.getDatabase(this)
        handleDeepLink(intent, db)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun handleDeepLink(intent: Intent, db: AppDatabase) {
        val entityType = intent.getStringExtra("entityType") ?: return
        val entityId   = intent.getIntExtra("entityId", -1)
        if (entityId == -1) return

        CoroutineScope(Dispatchers.Main).launch {
            when (entityType) {
                "task" -> {
                    val task = db.taskDao().getMasterTaskById(entityId) ?: return@launch
                    selectedTaskForInfo   = task
                    taskInfoReturnScreen  = "Calendars"
                    currentScreen         = "TaskInfo"
                }
                "task_allday" -> {
                    val task = db.taskDao().getMasterTaskById(entityId) ?: return@launch
                    selectedAllDayTaskForInfo = task
                    currentScreen             = "AllDayTaskInfo"
                }
                "event" -> {
                    val event = db.eventDao().getMasterEventById(entityId) ?: return@launch
                    selectedEventForInfo  = event
                    eventInfoReturnScreen = "Calendars"
                    currentScreen         = "EventInfo"
                }
                "reminder" -> {
                    val reminder = db.reminderDao().getMasterReminderById(entityId) ?: return@launch
                    selectedReminderForInfo = reminder
                    currentScreen           = "ReminderInfo"
                }
                "deadline" -> {
                    val deadline = db.deadlineDao().getDeadlineById(entityId) ?: return@launch
                    selectedDeadlineForInfo  = deadline
                    deadlineInfoReturnScreen = "Calendars"
                    currentScreen            = "DeadlineInfo"
                }
                "pomodoro" -> {
                    val task = db.taskDao().getMasterTaskById(entityId) ?: return@launch
                    selectedTaskForInfo  = task
                    pomodoroReturnScreen = "Calendars"
                    currentScreen        = "TaskPomodoro"
                }
                "pomodoro_allday" -> {
                    val task = db.taskDao().getMasterTaskById(entityId) ?: return@launch
                    selectedAllDayTaskForInfo = task
                    pomodoroReturnScreen      = "Calendars"
                    currentScreen             = "AllDayTaskPomodoro"
                }
            }
        }
    }
}