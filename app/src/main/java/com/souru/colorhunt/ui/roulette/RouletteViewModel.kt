package com.souru.colorhunt.ui.roulette

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.souru.colorhunt.ColorHuntApplication
import com.souru.colorhunt.data.notification.ThemeReminderScheduler
import com.souru.colorhunt.domain.color.ColorBucket
import com.souru.colorhunt.domain.color.ColorClassifier
import com.souru.colorhunt.domain.roulette.DailyColorRoulette
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class TodayColorUiState(
    /** Chosen colour (0xFFRRGGBB), or null until the user picks one. */
    val selectedColor: Int? = null,
    val bucket: ColorBucket? = null,
    /** True when the colour came from "today's color" auto-pick rather than manual. */
    val isToday: Boolean = false,
    val reminderEnabled: Boolean = false,
    val reminderHour: Int = ThemeReminderScheduler.DEFAULT_HOUR,
    val reminderMinute: Int = ThemeReminderScheduler.DEFAULT_MINUTE,
)

class RouletteViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(
        TodayColorUiState(
            reminderEnabled = prefs.getBoolean(KEY_REMINDER, false),
            reminderHour = prefs.getInt(KEY_REMINDER_HOUR, ThemeReminderScheduler.DEFAULT_HOUR),
            reminderMinute = prefs.getInt(KEY_REMINDER_MINUTE, ThemeReminderScheduler.DEFAULT_MINUTE),
        ),
    )
    val uiState: StateFlow<TodayColorUiState> = _state.asStateFlow()

    /**
     * Commit a colour to the result card.
     * @param fromSpin true when it came from the roulette spin ("today's color"),
     *                 false for a manual pick on the wheel.
     */
    fun select(colorInt: Int, fromSpin: Boolean = false) {
        _state.update {
            it.copy(selectedColor = colorInt, bucket = ColorClassifier.classify(colorInt), isToday = fromSpin)
        }
    }

    /** A fresh random hue (0..360) for the spin to land on — varied every press. */
    fun randomSpinHue(): Float = DailyColorRoulette.randomHue()

    fun setReminder(enabled: Boolean) {
        _state.update { it.copy(reminderEnabled = enabled) }
        prefs.edit().putBoolean(KEY_REMINDER, enabled).apply()
        val s = _state.value
        val context = getApplication<Application>()
        if (enabled) ThemeReminderScheduler.enable(context, s.reminderHour, s.reminderMinute)
        else ThemeReminderScheduler.disable(context)
    }

    /** Change the daily reminder time; reschedules if the reminder is on. */
    fun setReminderTime(hour: Int, minute: Int) {
        _state.update { it.copy(reminderHour = hour, reminderMinute = minute) }
        prefs.edit().putInt(KEY_REMINDER_HOUR, hour).putInt(KEY_REMINDER_MINUTE, minute).apply()
        if (_state.value.reminderEnabled) {
            ThemeReminderScheduler.enable(getApplication(), hour, minute)
        }
    }

    companion object {
        private const val PREFS = "colorhunt_prefs"
        private const val KEY_REMINDER = "daily_reminder_enabled"
        private const val KEY_REMINDER_HOUR = "daily_reminder_hour"
        private const val KEY_REMINDER_MINUTE = "daily_reminder_minute"

        val Factory = viewModelFactory {
            initializer {
                RouletteViewModel(this[APPLICATION_KEY] as ColorHuntApplication)
            }
        }
    }
}
