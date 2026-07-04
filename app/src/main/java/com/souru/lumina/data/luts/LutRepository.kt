package com.souru.lumina.data.luts

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

/** アプリ内LUTライブラリの1エントリ。 */
data class LutInfo(
    val id: String,       // ファイル名(一意)
    val name: String,     // 表示名(拡張子なし)
    val file: File,
)

/**
 * .cube ファイルのインポートとアプリ内LUTライブラリ管理。
 * ファイルは filesDir/luts/ にコピーして保持する。
 */
class LutRepository(private val context: Context) {

    private val dir: File by lazy {
        File(context.filesDir, "luts").apply { mkdirs() }
    }

    private val _luts = MutableStateFlow<List<LutInfo>>(emptyList())
    val luts: StateFlow<List<LutInfo>> = _luts.asStateFlow()

    private val cache = HashMap<String, CubeLut>()

    suspend fun refresh() = withContext(Dispatchers.IO) {
        _luts.value = dir.listFiles { f -> f.extension.equals("cube", ignoreCase = true) }
            ?.sortedBy { it.name.lowercase() }
            ?.map { LutInfo(id = it.name, name = it.nameWithoutExtension, file = it) }
            ?: emptyList()
    }

    /** SAFで選択された .cube をインポートする。パースに失敗したら例外。 */
    suspend fun import(uri: Uri): LutInfo = withContext(Dispatchers.IO) {
        val displayName = queryDisplayName(uri) ?: "imported_${System.currentTimeMillis()}.cube"
        val fileName = displayName
            .replace(Regex("[/\\\\]"), "_")
            .let { if (it.endsWith(".cube", ignoreCase = true)) it else "$it.cube" }

        val text = context.contentResolver.openInputStream(uri)?.use { input ->
            input.readBytes().toString(Charsets.UTF_8)
        } ?: throw IllegalArgumentException("ファイルを読み込めませんでした")

        // 妥当性検証(不正なら例外で中断)
        val parsed = CubeLutParser.parse(text)

        var target = File(dir, fileName)
        var suffix = 1
        while (target.exists()) {
            target = File(dir, "${fileName.removeSuffix(".cube")}_$suffix.cube")
            suffix++
        }
        target.writeText(text)
        cache[target.name] = parsed
        refresh()
        LutInfo(id = target.name, name = target.nameWithoutExtension, file = target)
    }

    suspend fun delete(info: LutInfo) = withContext(Dispatchers.IO) {
        info.file.delete()
        cache.remove(info.id)
        refresh()
    }

    suspend fun load(info: LutInfo): CubeLut = withContext(Dispatchers.IO) {
        cache.getOrPut(info.id) { CubeLutParser.parse(info.file.readText()) }
    }

    suspend fun loadByPath(path: String): CubeLut = withContext(Dispatchers.IO) {
        val file = File(path)
        cache.getOrPut(file.name) { CubeLutParser.parse(file.readText()) }
    }

    private fun queryDisplayName(uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
}
