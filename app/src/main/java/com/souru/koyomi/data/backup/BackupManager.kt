package com.souru.koyomi.data.backup

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.souru.koyomi.data.anniversary.Anniversary
import com.souru.koyomi.data.anniversary.AnniversaryRepository
import com.souru.koyomi.data.dataStore
import com.souru.koyomi.data.diary.DiaryRepository
import com.souru.koyomi.data.task.Task
import com.souru.koyomi.data.task.TaskRepository
import com.souru.koyomi.data.template.EventTemplate
import com.souru.koyomi.data.template.EventTemplateRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

/**
 * 機種変更の引き継ぎ: settings, to-dos, templates, anniversaries and diary
 * as one JSON file. Calendar events themselves live in Google Calendar and
 * come back through account sync, so they are not part of this file.
 */
class BackupManager(
    private val context: Context,
    private val taskRepository: TaskRepository,
    private val templateRepository: EventTemplateRepository,
    private val anniversaryRepository: AnniversaryRepository,
    private val diaryRepository: DiaryRepository,
) {

    suspend fun exportJson(): String {
        val root = JSONObject()
        root.put("app", "koyomi")
        root.put("version", 1)

        val settings = JSONArray()
        for ((key, value) in context.dataStore.data.first().asMap()) {
            // Never let the premium entitlement travel in a backup — otherwise
            // a purchased user's file would unlock premium on any device that
            // imports it. Billing (Google Play) is the sole source of truth.
            if (key.name in EXCLUDED_SETTING_KEYS) continue
            val entry = JSONObject().put("k", key.name)
            when (value) {
                is Boolean -> entry.put("t", "bool").put("v", value.toString())
                is Set<*> -> entry.put("t", "stringset")
                    .put("v", JSONArray(value.map { it.toString() }).toString())
                else -> entry.put("t", "string").put("v", value.toString())
            }
            settings.put(entry)
        }
        root.put("settings", settings)

        val tasks = JSONArray()
        for (task in taskRepository.loadAllTasks()) {
            tasks.put(
                JSONObject()
                    .put("title", task.title)
                    .put("due", task.dueDate.toEpochDay())
                    .putOpt("time", task.timeMinutes)
                    .putOpt("color", task.color)
                    .putOpt("reminder", task.reminderMinutes)
                    .put("done", task.done),
            )
        }
        root.put("tasks", tasks)

        val templates = JSONArray()
        for (template in templateRepository.loadTemplates()) {
            templates.put(
                JSONObject()
                    .put("title", template.title)
                    .put("allDay", template.allDay)
                    .put("startMinutes", template.startMinutes)
                    .put("durationMinutes", template.durationMinutes)
                    .putOpt("color", template.color)
                    .putOpt("location", template.location)
                    .putOpt("description", template.description)
                    .putOpt("reminder", template.reminderMinutes),
            )
        }
        root.put("templates", templates)

        val anniversaries = JSONArray()
        for (anniversary in anniversaryRepository.loadAll()) {
            anniversaries.put(
                JSONObject()
                    .put("title", anniversary.title)
                    .put("day", anniversary.date.toEpochDay())
                    .put("yearly", anniversary.repeatYearly),
            )
        }
        root.put("anniversaries", anniversaries)

        val diary = JSONArray()
        for ((date, text) in diaryRepository.loadAll()) {
            diary.put(JSONObject().put("day", date.toEpochDay()).put("text", text))
        }
        root.put("diary", diary)

        return root.toString(2)
    }

    /**
     * Replaces local data with the backup's. Parses everything BEFORE
     * touching any store, so a corrupt file can never wipe existing data.
     * Returns the number of restored records.
     */
    suspend fun importJson(json: String): Int {
        val root = JSONObject(json)
        require(root.optString("app") == "koyomi") { "not a koyomi backup" }

        data class Setting(val key: String, val type: String, val value: String)

        val settings = mutableListOf<Setting>()
        root.optJSONArray("settings")?.let { array ->
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                settings += Setting(o.getString("k"), o.getString("t"), o.getString("v"))
            }
        }
        val tasks = mutableListOf<Task>()
        root.optJSONArray("tasks")?.let { array ->
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                tasks += Task(
                    id = 0L,
                    title = o.getString("title"),
                    dueDate = LocalDate.ofEpochDay(o.getLong("due")),
                    timeMinutes = if (o.has("time")) o.getInt("time") else null,
                    color = if (o.has("color")) o.getInt("color") else null,
                    reminderMinutes = if (o.has("reminder")) o.getInt("reminder") else null,
                    done = o.optBoolean("done"),
                )
            }
        }
        val templates = mutableListOf<EventTemplate>()
        root.optJSONArray("templates")?.let { array ->
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                templates += EventTemplate(
                    id = 0L,
                    title = o.getString("title"),
                    allDay = o.optBoolean("allDay"),
                    startMinutes = o.optInt("startMinutes", 9 * 60),
                    durationMinutes = o.optInt("durationMinutes", 60),
                    // Calendar ids don't survive a device change.
                    calendarId = null,
                    color = if (o.has("color")) o.getInt("color") else null,
                    location = if (o.has("location")) o.getString("location") else null,
                    description = if (o.has("description")) o.getString("description") else null,
                    reminderMinutes = if (o.has("reminder")) o.getInt("reminder") else null,
                )
            }
        }
        val anniversaries = mutableListOf<Anniversary>()
        root.optJSONArray("anniversaries")?.let { array ->
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                anniversaries += Anniversary(
                    id = 0L,
                    title = o.getString("title"),
                    date = LocalDate.ofEpochDay(o.getLong("day")),
                    repeatYearly = o.optBoolean("yearly", true),
                )
            }
        }
        val diary = mutableListOf<Pair<LocalDate, String>>()
        root.optJSONArray("diary")?.let { array ->
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                diary += LocalDate.ofEpochDay(o.getLong("day")) to o.getString("text")
            }
        }

        // Parsed clean — now restore. Preserve the premium entitlement across
        // the wipe so a restore can never grant or revoke premium; only
        // Google Play may change it (re-synced on next app start regardless).
        context.dataStore.edit { prefs ->
            val premium = prefs[EXCLUDED_PREMIUM_KEY]
            prefs.clear()
            premium?.let { prefs[EXCLUDED_PREMIUM_KEY] = it }
            for (setting in settings) {
                if (setting.key in EXCLUDED_SETTING_KEYS) continue
                when (setting.type) {
                    "bool" -> prefs[booleanPreferencesKey(setting.key)] =
                        setting.value.toBoolean()
                    "stringset" -> {
                        val array = JSONArray(setting.value)
                        prefs[stringSetPreferencesKey(setting.key)] =
                            (0 until array.length()).map { array.getString(it) }.toSet()
                    }
                    else -> prefs[stringPreferencesKey(setting.key)] = setting.value
                }
            }
        }
        taskRepository.deleteAll()
        for (task in tasks) taskRepository.saveTask(task)
        templateRepository.deleteAll()
        for (template in templates) templateRepository.save(template)
        anniversaryRepository.deleteAll()
        for (anniversary in anniversaries) anniversaryRepository.save(anniversary)
        diaryRepository.deleteAll()
        for ((date, text) in diary) diaryRepository.save(date, text)

        return tasks.size + templates.size + anniversaries.size + diary.size
    }

    private companion object {
        /** Premium entitlement key (mirrors BillingRepository). Never backed up. */
        val EXCLUDED_PREMIUM_KEY = booleanPreferencesKey("premium_unlocked")
        val EXCLUDED_SETTING_KEYS = setOf(EXCLUDED_PREMIUM_KEY.name)
    }
}
