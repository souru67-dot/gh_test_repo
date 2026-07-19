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
)

class RouletteViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(
        TodayColorUiState(reminderEnabled = prefs.getBoolean(KEY_REMINDER, false)),
    )
    val uiState: StateFlow<TodayColorUiState> = _state.asStateFlow()

    /** Manual pick from the colour wheel. */
    fun select(colorInt: Int) {
        _state.update {
            it.copy(selectedColor = colorInt, bucket = ColorClassifier.classify(colorInt), isToday = false)
        }
    }

    /** Auto "today's color" — deterministic per day. Returns the colour so the wheel can move its thumb. */
    fun pickToday(): Int {
        val color = DailyColorRoulette.todayColor().swatch
        _state.update {
            it.copy(selectedColor = color, bucket = ColorClassifier.classify(color), isToday = true)
        }
        return color
    }

    fun setReminder(enabled: Boolean) {
        _state.update { it.copy(reminderEnabled = enabled) }
        prefs.edit().putBoolean(KEY_REMINDER, enabled).apply()
        val context = getApplication<Application>()
        if (enabled) ThemeReminderScheduler.enable(context) else ThemeReminderScheduler.disable(context)
    }

    companion object {
        private const val PREFS = "colorhunt_prefs"
        private const val KEY_REMINDER = "daily_reminder_enabled"

        val Factory = viewModelFactory {
            initializer {
                RouletteViewModel(this[APPLICATION_KEY] as ColorHuntApplication)
            }
        }
    }
}
