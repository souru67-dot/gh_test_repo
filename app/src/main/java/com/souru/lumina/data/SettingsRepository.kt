package com.souru.lumina.data

import android.content.Context
import android.util.Log
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.souru.lumina.data.model.GalleryFilter
import com.souru.lumina.data.model.MediaTypeFilter
import com.souru.lumina.data.model.RawFilterMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

// ファイル破損時は空のPreferencesで置き換える(起動不能ループの防止)
private val Context.dataStore by preferencesDataStore(
    name = "settings",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

class SettingsRepository(private val context: Context) {

    private val gridColumnsKey = intPreferencesKey("grid_columns")
    private val rawFilterKey = stringPreferencesKey("raw_filter")
    private val mediaTypeFilterKey = stringPreferencesKey("media_type_filter")
    private val externalTreeUriKey = stringPreferencesKey("external_tree_uri")

    // Pro購入状態のローカルキャッシュ。Play Billingが照会できるまで/オフライン時に
    // 直近の権利を維持するため(サーバ検証は行わない端末内完結アプリのため許容)。
    private val proPurchasedCacheKey = booleanPreferencesKey("pro_purchased_cache")
    // デバッグビルド専用: Pro状態を手動で切り替える開発者スイッチ。
    private val proDebugOverrideKey = booleanPreferencesKey("pro_debug_override")

    /**
     * 読み込みの防御層。破損・I/O例外など復元に失敗した場合は
     * デフォルト(空)にフォールバックし、起動時クラッシュを構造的に防ぐ。
     */
    private val safeData: Flow<Preferences> = context.dataStore.data
        .catch { t ->
            Log.w("SettingsRepository", "設定の読み込みに失敗。デフォルトへフォールバック", t)
            emit(emptyPreferences())
        }

    val gridColumns: Flow<Int> = safeData
        .map { (it[gridColumnsKey] ?: DEFAULT_COLUMNS).coerceIn(MIN_COLUMNS, MAX_COLUMNS) }

    /** フィルタ状態。復元時に不正値のフォールバックと矛盾組み合わせの正規化を行う。 */
    val galleryFilter: Flow<GalleryFilter> = safeData
        .map { prefs ->
            GalleryFilter.fromStored(
                typeValue = prefs[mediaTypeFilterKey],
                formatValue = prefs[rawFilterKey],
            )
        }

    /** 最後に選択した外部デバイス(USB/SDカード)のツリーURI。未選択はnull。 */
    val externalTreeUri: Flow<String?> = safeData.map { it[externalTreeUriKey] }

    /** ツリーURIの一回取得(走査時にFlowを購読せず読むため)。 */
    suspend fun externalTreeUriOnce(): String? = externalTreeUri.first()

    /** Pro購入状態の直近キャッシュ(Billing照会前/オフライン時のフォールバック)。 */
    val proPurchasedCache: Flow<Boolean> = safeData.map { it[proPurchasedCacheKey] ?: false }

    suspend fun setProPurchasedCache(purchased: Boolean) {
        safeEdit { it[proPurchasedCacheKey] = purchased }
    }

    /** デバッグ用Proオーバーライド(リリースでは無視される)。 */
    val proDebugOverride: Flow<Boolean> = safeData.map { it[proDebugOverrideKey] ?: false }

    suspend fun setProDebugOverride(enabled: Boolean) {
        safeEdit { it[proDebugOverrideKey] = enabled }
    }

    /** 動画ごとに手動選択した入力変換(各社Log→709)のID。素材IDでキーする。 */
    suspend fun videoInputTransform(mediaId: Long): String? =
        safeData.map { it[stringPreferencesKey("video_input_$mediaId")] }.first()

    suspend fun setVideoInputTransform(mediaId: Long, id: String) {
        safeEdit { it[stringPreferencesKey("video_input_$mediaId")] = id }
    }

    suspend fun setExternalTreeUri(uri: String?) {
        safeEdit { prefs ->
            if (uri == null) prefs.remove(externalTreeUriKey) else prefs[externalTreeUriKey] = uri
        }
    }

    suspend fun setGridColumns(columns: Int) {
        safeEdit { it[gridColumnsKey] = columns.coerceIn(MIN_COLUMNS, MAX_COLUMNS) }
    }

    suspend fun setRawFilter(mode: RawFilterMode) {
        safeEdit { prefs ->
            val normalized = GalleryFilter.fromStored(prefs[mediaTypeFilterKey], mode.name)
            prefs[rawFilterKey] = normalized.format.name
            prefs[mediaTypeFilterKey] = normalized.type.name
        }
    }

    suspend fun setMediaTypeFilter(filter: MediaTypeFilter) {
        safeEdit { prefs ->
            val normalized = GalleryFilter.fromStored(filter.name, prefs[rawFilterKey])
            prefs[mediaTypeFilterKey] = normalized.type.name
            prefs[rawFilterKey] = normalized.format.name
        }
    }

    /** 書き込み失敗でアプリを落とさない(次回起動時はデフォルトで復元される)。 */
    private suspend fun safeEdit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        try {
            context.dataStore.edit { block(it) }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (t: Exception) {
            Log.w("SettingsRepository", "設定の保存に失敗", t)
        }
    }

    companion object {
        const val MIN_COLUMNS = 2
        const val MAX_COLUMNS = 5
        const val DEFAULT_COLUMNS = 4
    }
}
