package com.planned

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

// ─────────────────────────────────────────────────────────────────
// Voice State (observed from UI)
// ─────────────────────────────────────────────────────────────────
enum class VoicePhase {
    IDLE,        // mic button sitting quietly
    LISTENING,   // Android STT active
    THINKING,    // Claude API in flight
    SPEAKING,    // TTS reading the reply
    ERROR        // something went wrong
}

data class VoiceResult(
    val userText: String,
    val replyText: String,
    val actionTaken: String? = null   // human-readable summary of DB change
)

// ─────────────────────────────────────────────────────────────────
// VoiceCommandManager
// ─────────────────────────────────────────────────────────────────
@RequiresApi(Build.VERSION_CODES.O)
object VoiceCommandManager {

    // Injected from the composable that owns the lifecycle
    var onPhaseChange: (VoicePhase) -> Unit = {}
    var onResult: (VoiceResult) -> Unit = {}

    private var tts: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var isTtsReady = false

    // ── TTS init ──────────────────────────────────────────────────
    fun initTts(context: Context) {
        if (tts != null) return
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        kotlinx.coroutines.CoroutineScope(Dispatchers.Main).launch {
                            onPhaseChange(VoicePhase.IDLE)
                        }
                    }
                    override fun onError(utteranceId: String?) {
                        kotlinx.coroutines.CoroutineScope(Dispatchers.Main).launch {
                            onPhaseChange(VoicePhase.IDLE)
                        }
                    }
                })
                isTtsReady = true
            }
        }
    }

    fun releaseTts() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isTtsReady = false
    }

    // ── Entry point: called when the mic button is tapped ─────────
    @RequiresApi(Build.VERSION_CODES.O)
    fun startListening(context: Context, db: AppDatabase) {
        cancelSpeech() // Ensure any existing TTS ceases and phase resets before new listening begins.

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onPhaseChange(VoicePhase.ERROR)
            return
        }
        onPhaseChange(VoicePhase.LISTENING)

        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
        }

        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}

            override fun onEndOfSpeech() {
                onPhaseChange(VoicePhase.THINKING)
            }

            override fun onError(error: Int) {
                onPhaseChange(VoicePhase.ERROR)
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val spoken = matches?.firstOrNull()
                if (spoken.isNullOrBlank()) {
                    onPhaseChange(VoicePhase.ERROR)
                    return
                }
                onPhaseChange(VoicePhase.THINKING)
                // Hand off to coroutine-land for network + DB work
                kotlinx.coroutines.CoroutineScope(Dispatchers.Main).launch {
                    handleSpokenCommand(context, db, spoken)
                }
            }
        })

        speechRecognizer?.startListening(intent)
    }

    fun stopListening() {
        speechRecognizer?.stopListening()
    }

    // ── Build context snapshot for Claude ────────────────────────
    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun buildContextSnapshot(db: AppDatabase): String {
        val today = LocalDate.now()
        val todayStr = today.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy"))

        // Today's tasks (by interval date)
        val todayIntervals = db.taskDao().getAllIntervals().filter { it.occurDate == today }
        val allMasterTasks = db.taskDao().getAllMasterTasks()
        val taskLines = todayIntervals.mapNotNull { interval ->
            val master = allMasterTasks.find { it.id == interval.masterTaskId } ?: return@mapNotNull null
            val status = when (master.status) { 3 -> "done" 2 -> "in-progress" else -> "pending" }
            "  • Task \"${master.title}\" (id=${master.id}) ${interval.startTime}–${interval.endTime} [$status]"
        }

        // Today's events
        val todayEvents = db.eventDao().getAllOccurrences().filter { it.occurDate == today }
        val allEvents = db.eventDao().getAllMasterEvents()
        val eventLines = todayEvents.mapNotNull { occ ->
            val master = allEvents.find { it.id == occ.masterEventId } ?: return@mapNotNull null
            "  • Event \"${master.title}\" ${occ.startTime}–${occ.endTime}"
        }

        // Today's reminders
        val todayReminders = db.reminderDao().getAllOccurrences().filter { it.occurDate == today }
        val allReminders = db.reminderDao().getAllMasterReminders()
        val reminderLines = todayReminders.mapNotNull { occ ->
            val master = allReminders.find { it.id == occ.masterReminderId } ?: return@mapNotNull null
            val timeStr = occ.time?.toString() ?: "all-day"
            "  • Reminder \"${master.title}\" at $timeStr"
        }

        // Upcoming deadlines (next 7 days)
        val deadlines = db.deadlineDao().getAll()
            .filter { !it.date.isBefore(today) && !it.date.isAfter(today.plusDays(7)) }
        val deadlineLines = deadlines.map { "  • Deadline \"${it.title}\" on ${it.date} at ${it.time}" }

        // Categories available
        val categories = db.categoryDao().getAll()
        val catList = categories.joinToString(", ") { "\"${it.title}\" (id=${it.id})" }

        return buildString {
            appendLine("TODAY: $todayStr")
            appendLine()
            if (taskLines.isNotEmpty()) { appendLine("TASKS TODAY:"); taskLines.forEach { appendLine(it) }; appendLine() }
            if (eventLines.isNotEmpty()) { appendLine("EVENTS TODAY:"); eventLines.forEach { appendLine(it) }; appendLine() }
            if (reminderLines.isNotEmpty()) { appendLine("REMINDERS TODAY:"); reminderLines.forEach { appendLine(it) }; appendLine() }
            if (deadlineLines.isNotEmpty()) { appendLine("UPCOMING DEADLINES (next 7 days):"); deadlineLines.forEach { appendLine(it) }; appendLine() }
            if (categories.isNotEmpty()) appendLine("AVAILABLE CATEGORIES: $catList")
        }
    }

    // ── Gemini API call ───────────────────────────────────────────
    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun callGemini(systemPrompt: String, userMessage: String): String =
        withContext(Dispatchers.IO) {
            val apiKey = BuildConfig.GEMINI_API_KEY
            val url = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-latest:generateContent?key=$apiKey")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000

            val body = JSONObject().apply {
                put("systemInstruction", JSONObject().apply {
                    put("parts", org.json.JSONArray().put(JSONObject().apply {
                        put("text", systemPrompt)
                    }))
                })
                put("contents", org.json.JSONArray().put(
                    JSONObject().apply {
                        put("role", "user")
                        put("parts", org.json.JSONArray().put(JSONObject().apply {
                            put("text", userMessage)
                        }))
                    }
                ))
                put("generationConfig", JSONObject().apply {
                    put("responseMimeType", "application/json")
                })
            }.toString()

            conn.outputStream.use { it.write(body.toByteArray()) }

            val response = conn.inputStream.bufferedReader().readText()
            val json = JSONObject(response)
            json.getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
        }

    // ── Parse Gemini's JSON response and execute DB actions ───────
    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun executeAction(context: Context, db: AppDatabase, json: JSONObject): String {
        val action = json.optString("action", "REPLY")
        val reply = json.optString("reply", "Done.")

        when (action) {

            "CREATE_TASK" -> {
                val title = json.getString("title")
                val durationMin = json.optInt("duration_minutes", 60)
                val startDateStr = json.optString("start_date", "")
                val startTimeStr = json.optString("start_time", "")
                val notes = json.optString("notes", "").ifBlank { null }
                val catId = json.optInt("category_id", -1).takeIf { it != -1 }

                val startDate = if (startDateStr.isNotBlank()) LocalDate.parse(startDateStr) else null
                val startTime = if (startTimeStr.isNotBlank()) LocalTime.parse(startTimeStr) else null

                TaskManager.insert(
                    context = context,
                    db = db,
                    title = title,
                    notes = notes,
                    allDay = null,
                    breakable = false,
                    startDate = startDate,
                    startTime = startTime,
                    predictedDuration = durationMin,
                    categoryId = catId,
                    eventId = null,
                    deadlineId = null,
                    dependencyTaskId = null
                )
            }

            "CREATE_EVENT" -> {
                val title = json.getString("title")
                val startDateStr = json.getString("start_date")
                val endDateStr = json.optString("end_date", "").ifBlank { null }
                val startTimeStr = json.getString("start_time")
                val endTimeStr = json.getString("end_time")
                val notes = json.optString("notes", "").ifBlank { null }
                val catId = json.optInt("category_id", -1).takeIf { it != -1 }

                EventManager.insert(
                    context = context,
                    db = db,
                    title = title,
                    notes = notes,
                    color = null,
                    startDate = LocalDate.parse(startDateStr),
                    endDate = endDateStr?.let { LocalDate.parse(it) },
                    startTime = LocalTime.parse(startTimeStr),
                    endTime = LocalTime.parse(endTimeStr),
                    recurFreq = RecurrenceFrequency.NONE,
                    recurRule = RecurrenceRule(),
                    categoryId = catId
                )
            }

            "CREATE_REMINDER" -> {
                val title = json.getString("title")
                val startDateStr = json.getString("start_date")
                val timeStr = json.optString("time", "").ifBlank { null }
                val notes = json.optString("notes", "").ifBlank { null }
                val catId = json.optInt("category_id", -1).takeIf { it != -1 }
                val isAllDay = timeStr == null

                ReminderManager.insert(
                    context = context,
                    db = db,
                    title = title,
                    notes = notes,
                    startDate = LocalDate.parse(startDateStr),
                    endDate = null,
                    time = timeStr?.let { LocalTime.parse(it) },
                    allDay = isAllDay,
                    recurFreq = RecurrenceFrequency.NONE,
                    recurRule = RecurrenceRule(),
                    categoryId = catId
                )
            }

            "CREATE_DEADLINE" -> {
                val title = json.getString("title")
                val dateStr = json.getString("date")
                val timeStr = json.optString("time", "23:59")
                val notes = json.optString("notes", "").ifBlank { null }
                val catId = json.optInt("category_id", -1).takeIf { it != -1 }

                DeadlineManager.insert(
                    context = context,
                    db = db,
                    title = title,
                    notes = notes,
                    date = LocalDate.parse(dateStr),
                    time = LocalTime.parse(timeStr),
                    categoryId = catId,
                    eventId = null
                )
            }

            "EDIT_TASK" -> {
                val taskId = json.getInt("task_id")
                val task = db.taskDao().getMasterTaskById(taskId)
                if (task != null) {
                    val durationMin = if (json.has("duration_minutes") && !json.isNull("duration_minutes")) json.getInt("duration_minutes") else task.predictedDuration
                    val startDateStr = json.optString("start_date", "")
                    val startTimeStr = json.optString("start_time", "")
                    val notes = json.optString("notes", "").ifBlank { task.notes }
                    val catId = json.optInt("category_id", -1).takeIf { it != -1 } ?: task.categoryId

                    val startDate = if (startDateStr.isNotBlank()) LocalDate.parse(startDateStr) else task.startDate
                    val startTime = if (startTimeStr.isNotBlank()) LocalTime.parse(startTimeStr) else task.startTime

                    TaskManager.update(
                        context = context,
                        db = db,
                        task = task.copy(
                            predictedDuration = durationMin,
                            startDate = startDate,
                            startTime = startTime,
                            notes = notes,
                            categoryId = catId
                        )
                    )
                }
            }

            "DELETE_TASK" -> {
                val taskId = json.getInt("task_id")
                TaskManager.delete(context = context, db = db, taskId = taskId)
            }

            "CHANGE_SETTING" -> {
                val settingName = json.getString("setting_name")
                val valBool = if (json.has("value_boolean") && !json.isNull("value_boolean")) json.getBoolean("value_boolean") else null
                val valInt = if (json.has("value_int") && !json.isNull("value_int")) json.getInt("value_int") else null

                when (settingName) {
                    "startWeekOnMonday" -> valBool?.let { SettingsManager.setStartWeek(db, it) }
                    "atiPaddingEnabled" -> valBool?.let {
                        SettingsManager.setAtiPaddingEnabled(db, it)
                        generateTaskIntervals(context, db)
                    }
                    "breakDuration" -> valInt?.let { SettingsManager.setBreakDuration(db, it) }
                    "breakEvery" -> valInt?.let { SettingsManager.setBreakEvery(db, it) }
                }
            }

            // QUERY_SCHEDULE and REPLY just return the reply text as-is
        }

        return reply
    }

    // ── Main handler ──────────────────────────────────────────────
    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun handleSpokenCommand(context: Context, db: AppDatabase, spoken: String) {
        try {
            val snapshot = buildContextSnapshot(db)
            val today = LocalDate.now()

            val systemPrompt = """
You are PlannEd Assistant, the AI voice assistant built into the PlannEd productivity app.
The user has just spoken a command. Your job is to understand it and return a JSON response.

CURRENT APP STATE:
$snapshot

RULES:
1. Always respond with a single valid JSON object — no markdown, no extra text.
2. Decide which action fits best:
   - CREATE_TASK   → user wants to create a task/to-do with duration
   - CREATE_EVENT  → user wants to schedule an event (fixed time block, no duration concept)
   - CREATE_REMINDER → user wants a reminder
   - CREATE_DEADLINE → user wants a deadline
   - EDIT_TASK       → user explicitly mentions editing or updating a specific task note, duration, date, time
   - DELETE_TASK     → user explicitly requests deleting or removing a specific task
   - CHANGE_SETTING  → user wants to edit an app setting (e.g. ATI padding, break time)
   - QUERY_SCHEDULE  → user is asking about their schedule, tasks, events etc.
   - REPLY           → general question or something you can't act on

3. Date/time rules:
   - All dates: ISO format YYYY-MM-DD. Today is ${today}.
   - All times: HH:mm (24h). Parse "5am"→"05:00", "3:30pm"→"15:30", "noon"→"12:00".
   - Relative dates: "tomorrow"→${today.plusDays(1)}, "next Monday"→compute correctly.
   - Duration: convert hours to minutes (e.g. "2 hours"→120).

4. JSON schemas per action:

CREATE_TASK:
{
  "action": "CREATE_TASK",
  "title": "string",
  "duration_minutes": number,
  "start_date": "YYYY-MM-DD or empty string for auto-schedule",
  "start_time": "HH:mm or empty string for auto-schedule",
  "notes": "string or empty",
  "category_id": number_or_-1,
  "reply": "confirmation message to speak back, friendly and short"
}

CREATE_EVENT:
{
  "action": "CREATE_EVENT",
  "title": "string",
  "start_date": "YYYY-MM-DD",
  "start_time": "HH:mm",
  "end_time": "HH:mm",
  "notes": "string or empty",
  "category_id": number_or_-1,
  "reply": "short spoken confirmation"
}

CREATE_REMINDER:
{
  "action": "CREATE_REMINDER",
  "title": "string",
  "start_date": "YYYY-MM-DD",
  "time": "HH:mm or empty for all-day",
  "notes": "string or empty",
  "category_id": number_or_-1,
  "reply": "short spoken confirmation"
}

CREATE_DEADLINE:
{
  "action": "CREATE_DEADLINE",
  "title": "string",
  "date": "YYYY-MM-DD",
  "time": "HH:mm",
  "notes": "string or empty",
  "category_id": number_or_-1,
  "reply": "short spoken confirmation"
}

EDIT_TASK:
{
  "action": "EDIT_TASK",
  "task_id": number,
  "duration_minutes": number or null, 
  "start_date": "YYYY-MM-DD or empty string",
  "start_time": "HH:mm or empty string",
  "notes": "string or empty",
  "category_id": number_or_-1,
  "reply": "short spoken confirmation"
}

DELETE_TASK:
{
  "action": "DELETE_TASK",
  "task_id": number,
  "reply": "short spoken confirmation"
}

CHANGE_SETTING:
{
  "action": "CHANGE_SETTING",
  "setting_name": "one of: startWeekOnMonday, atiPaddingEnabled, breakDuration, breakEvery",
  "value_boolean": boolean or null,
  "value_int": number or null,
  "reply": "short spoken confirmation"
}

QUERY_SCHEDULE:
{
  "action": "QUERY_SCHEDULE",
  "reply": "natural spoken answer about their schedule based on the app state above"
}

REPLY:
{
  "action": "REPLY",
  "reply": "your spoken response"
}

Keep all reply strings short, friendly, and natural — they will be read aloud by TTS.
Do NOT include markdown in reply strings.
""".trimIndent()

            val rawResponse = callGemini(systemPrompt, spoken)

            // Strip any accidental markdown fences
            val cleanJson = rawResponse
                .replace(Regex("^```json\\s*", RegexOption.MULTILINE), "")
                .replace(Regex("^```\\s*", RegexOption.MULTILINE), "")
                .trim()

            val json = JSONObject(cleanJson)
            val replyText = executeAction(context, db, json)

            val actionLabel = when (json.optString("action")) {
                "CREATE_TASK" -> "Task \"${json.optString("title")}\" created"
                "CREATE_EVENT" -> "Event \"${json.optString("title")}\" created"
                "CREATE_REMINDER" -> "Reminder \"${json.optString("title")}\" created"
                "CREATE_DEADLINE" -> "Deadline \"${json.optString("title")}\" created"
                "EDIT_TASK" -> "Task ${json.optString("task_id")} updated"
                "DELETE_TASK" -> "Task ${json.optString("task_id")} deleted"
                "CHANGE_SETTING" -> "Setting ${json.optString("setting_name")} updated"
                else -> null
            }

            val result = VoiceResult(
                userText = spoken,
                replyText = replyText,
                actionTaken = actionLabel
            )

            withContext(Dispatchers.Main) {
                onResult(result)
                speakOut(replyText)
            }

        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                val errResult = VoiceResult(
                    userText = spoken,
                    replyText = "Sorry, something went wrong. Please try again.",
                    actionTaken = null
                )
                onResult(errResult)
                speakOut(errResult.replyText)
            }
        }
    }

    // ── TTS speak ────────────────────────────────────────────────
    private fun speakOut(text: String) {
        if (isTtsReady) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "planned_vc")
        }
        // Force the Mic back to IDLE instantly instead of SPEAKING!
        // This makes the button clickable immediately (single click) for your next command!
        onPhaseChange(VoicePhase.IDLE)
    }

    fun cancelSpeech() {
        tts?.stop()
        onPhaseChange(VoicePhase.IDLE)
    }
}


