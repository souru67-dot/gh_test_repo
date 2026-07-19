package com.souru.colorhunt.ui.roulette

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.souru.colorhunt.ColorHuntApplication
import com.souru.colorhunt.data.notification.ThemeReminderScheduler
import com.souru.colorhunt.domain.color.ColorBucket
import com.souru.colorhunt.domain.roulette.DailyColorRoulette
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY

data class RouletteUiState(
    val bucket: ColorBucket = DailyColorRoulette.todayColor(),
    val isToday: Boolean = true,
    val reminderEnabled: Boolean = false,
    /** Increments on each pick, so the wheel animation re-triggers. */
    val spinToken: Int = 0,
)

class RouletteViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val bucket = MutableStateFlow(DailyColorRoulette.todayColor())
    private val isToday = MutableStateFlow(true)
    private val reminder = MutableStateFlow(prefs.getBoolean(KEY_REMINDER, false))
    private val spinToken = MutableStateFlow(0)

    val uiState: StateFlow<RouletteUiState> =
        combine(bucket, isToday, reminder, spinToken) { b, today, rem, token ->
            RouletteUiState(bucket = b, isToday = today, reminderEnabled = rem, spinToken = token)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RouletteUiState())

    fun spin() {
        bucket.value = DailyColorRoulette.randomColor()
        isToday.value = false
        spinToken.value += 1
    }

    fun resetToday() {
        bucket.value = DailyColorRoulette.todayColor()
        isToday.value = true
        spinToken.value += 1
    }

    fun setReminder(enabled: Boolean) {
        reminder.value = enabled
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
