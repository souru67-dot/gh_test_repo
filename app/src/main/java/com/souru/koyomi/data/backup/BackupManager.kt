package com.souru.koyomi.data.backup

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.souru.koyomi.data.dataStore
import com.souru.koyomi.data.task.Task
import com.souru.koyomi.data.task.TaskRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

/**
 * 機種変更の引き継ぎ: settings and to-dos as one JSON file. Calendar events themselves live in Google Calendar and
 * come back through account sync, so they are not part of this file.
 */
class BackupManager(
    private val context: Context,
    private val taskRepository: TaskRepository,
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
            if (key.name in EXCLUDED_SETTING_KEYS || isDeviceLocal(key.name)) continue
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
        // Parsed clean — now restore. Preserve the premium entitlement across
        // the wipe so a restore can never grant or revoke premium; only
        // Google Play may change it (re-synced on next app start regardless).
        // Device-local keys (calendar / widget ids, the onboarding flag, the
        // weather cache) are kept as they are on THIS device: calendar ids
        // from another phone point at different calendars here, so restoring
        // them used to hide or preselect arbitrary calendars.
        context.dataStore.edit { prefs ->
            val kept = prefs.asMap().filterKeys { key ->
                // The weather cache belongs to the current place, which the
                // backup may change — drop it so the next resume refetches.
                (key.name in EXCLUDED_SETTING_KEYS || isDeviceLocal(key.name)) &&
                    !key.name.startsWith("weather_cache")
            }
            prefs.clear()
            @Suppress("UNCHECKED_CAST")
            for ((key, value) in kept) {
                prefs[key as androidx.datastore.preferences.core.Preferences.Key<Any>] = value
            }
            for (setting in settings) {
                if (setting.key in EXCLUDED_SETTING_KEYS || isDeviceLocal(setting.key)) continue
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

        return tasks.size
    }

    private companion object {
        /** Premium entitlement key (mirrors BillingRepository). Never backed up. */
        val EXCLUDED_PREMIUM_KEY = booleanPreferencesKey("premium_unlocked")
        val EXCLUDED_SETTING_KEYS = setOf(EXCLUDED_PREMIUM_KEY.name)

        private val DEVICE_LOCAL_KEYS = setOf(
            "hidden_calendar_ids",
            "last_used_calendar_id",
            "default_calendar_id",
            "onboarding_done",
            "weather_cache_json",
            "weather_cached_at",
            // Left over from removed features; never restore them.
            "custom_stamps",
        )

        /** Settings that only make sense on the device that wrote them. */
        fun isDeviceLocal(name: String): Boolean =
            name in DEVICE_LOCAL_KEYS ||
                name.startsWith("widget_theme_") ||
                name.startsWith("widget_dotcolor_") ||
                name.startsWith("widget_opacity_") && name != "widget_opacity_percent"
    }
}
