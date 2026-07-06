package com.souru.lumina.ui.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.souru.lumina.LuminaApplication
import com.souru.lumina.data.luts.LutInfo
import com.souru.lumina.data.luts.LutRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LutManagerViewModel(private val lutRepository: LutRepository) : ViewModel() {

    val luts: StateFlow<List<LutInfo>> = lutRepository.luts

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        viewModelScope.launch { lutRepository.refresh() }
    }

    fun import(uri: Uri) {
        viewModelScope.launch {
            runCatching { lutRepository.import(uri) }
                .onSuccess { _message.value = "「${it.name}」を読み込みました" }
                .onFailure { _message.value = "LUTを読み込めませんでした: ${it.message}" }
        }
    }

    fun delete(info: LutInfo) {
        viewModelScope.launch { lutRepository.delete(info) }
    }

    fun consumeMessage() {
        _message.value = null
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as LuminaApplication
                LutManagerViewModel(lutRepository = app.container.lutRepository)
            }
        }
    }
}
