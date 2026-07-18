package com.souru.koyomi.ui.anniversary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.souru.koyomi.KoyomiApplication
import com.souru.koyomi.data.anniversary.Anniversary
import com.souru.koyomi.data.anniversary.AnniversaryRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class AnniversaryViewModel(
    private val repository: AnniversaryRepository,
) : ViewModel() {

    val anniversaries: StateFlow<List<Anniversary>> = repository.changes
        .mapLatest { repository.loadAll() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun save(anniversary: Anniversary) {
        viewModelScope.launch { repository.save(anniversary) }
    }

    fun delete(id: Long) {
        viewModelScope.launch { repository.delete(id) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as KoyomiApplication
                AnniversaryViewModel(repository = app.container.anniversaryRepository)
            }
        }
    }
}
