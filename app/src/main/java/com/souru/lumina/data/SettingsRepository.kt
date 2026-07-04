package com.souru.lumina.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.souru.lumina.data.model.RawFilterMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private val gridColumnsKey = intPreferencesKey("grid_columns")
    private val rawFilterKey = stringPreferencesKey("raw_filter")

    val gridColumns: Flow<Int> = context.dataStore.data
        .map { (it[gridColumnsKey] ?: DEFAULT_COLUMNS).coerceIn(MIN_COLUMNS, MAX_COLUMNS) }

    val rawFilter: Flow<RawFilterMode> = context.dataStore.data
        .map { prefs ->
            prefs[rawFilterKey]?.let { value ->
                RawFilterMode.entries.firstOrNull { it.name == value }
            } ?: RawFilterMode.JPEG
        }

    suspend fun setGridColumns(columns: Int) {
        context.dataStore.edit { it[gridColumnsKey] = columns.coerceIn(MIN_COLUMNS, MAX_COLUMNS) }
    }

    suspend fun setRawFilter(mode: RawFilterMode) {
        context.dataStore.edit { it[rawFilterKey] = mode.name }
    }

    companion object {
        const val MIN_COLUMNS = 2
        const val MAX_COLUMNS = 5
        const val DEFAULT_COLUMNS = 4
    }
}
