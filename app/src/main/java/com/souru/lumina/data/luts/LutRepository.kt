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
    val id: String,       // ファイル名(一意。プリセットは "preset:" 接頭辞付き)
    val name: String,     // 表示名(拡張子なし)
    val file: File,
    val isPreset: Boolean = false,
    /** 標準プリセットの場合のみ。カテゴリ分け表示に使う */
    val preset: LutPreset? = null,
)

/**
 * .cube ファイルのインポートとアプリ内LUTライブラリ管理。
 * ユーザーのインポートは filesDir/luts/ に、標準プリセットは
 * filesDir/luts/presets/ に初回アクセス時にコード生成して保持する。
 */
class LutRepository(private val context: Context) {

    private val TAG = "LutRepository"

    private val dir: File by lazy {
        File(context.filesDir, "luts").apply { mkdirs() }
    }

    private val presetsDir: File by lazy {
        File(dir, "presets").apply { mkdirs() }
    }

    private val _luts = MutableStateFlow<List<LutInfo>>(emptyList())
    val luts: StateFlow<List<LutInfo>> = _luts.asStateFlow()

    private val cache = HashMap<String, CubeLut>()

    suspend fun refresh() = withContext(Dispatchers.IO) {
        ensurePresets()
        val presets = LutPreset.entries.mapNotNull { preset ->
            val file = File(presetsDir, preset.fileName)
            if (file.exists()) {
                LutInfo(
                    id = "preset:${preset.fileName}",
                    name = preset.displayName,
                    file = file,
                    isPreset = true,
                    preset = preset,
                )
            } else {
                null
            }
        }
        val imported = dir.listFiles { f ->
            f.isFile && f.extension.equals("cube", ignoreCase = true)
        }
            ?.sortedBy { it.name.lowercase() }
            ?.map { LutInfo(id = it.name, name = it.nameWithoutExtension, file = it) }
            ?: emptyList()
        _luts.value = presets + imported
    }

    /**
     * 標準プリセットの.cubeを用意する。バージョン一致時は高速パスでスキップするが、
     * バージョン不一致・欠落・**パース不能(破損)**のいずれかがあれば、その
     * プリセットだけを生成し直して読み戻し検証する(バージョン管理に頼らず
     * 確実に有効な状態へ収束させる)。
     */
    private fun ensurePresets() {
        val marker = File(presetsDir, "version")
        val current = runCatching { marker.readText().trim() }.getOrNull()
        val expected = LutPresets.VERSION.toString()
        val versionMatches = current == expected

        var allValid = true
        for (preset in LutPreset.entries) {
            val file = File(presetsDir, preset.fileName)
            // 版が一致していればファイル存在のみ確認(全件パースは重いので回避)。
            // 版が違う初回は必ずパース検証して破損を修復する
            val healthy = if (versionMatches) {
                file.exists() && file.length() > 0
            } else {
                file.exists() && isParseable(file)
            }
            if (healthy) continue

            val ok = runCatching {
                file.writeText(LutPresets.generateCubeText(preset))
                // 生成直後に読み戻してパースできることを保証する
                CubeLutParser.parse(file.readText())
            }.isSuccess
            cache.remove("preset:${preset.fileName}")
            if (ok) {
                android.util.Log.i(TAG, "プリセットを生成: ${preset.fileName}")
            } else {
                allValid = false
                android.util.Log.w(TAG, "プリセット生成に失敗: ${preset.fileName}")
            }
        }
        // 全て有効なときだけ版を確定(失敗が残れば次回また修復を試みる)
        if (allValid) runCatching { marker.writeText(expected) }
    }

    private fun isParseable(file: File): Boolean =
        runCatching { CubeLutParser.parse(file.readText()) }.isSuccess

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

    /**
     * 生成済みの .cube テキストを名前付きでライブラリへ取り込む
     * (LUTチューニング画面の書き出し用)。パース不能なら例外。
     */
    suspend fun importCubeText(displayName: String, text: String): LutInfo =
        withContext(Dispatchers.IO) {
            val parsed = CubeLutParser.parse(text) // 妥当性検証
            val base = (if (displayName.endsWith(".cube", ignoreCase = true)) {
                displayName
            } else {
                "$displayName.cube"
            }).replace(Regex("[/\\\\]"), "_")
            var target = File(dir, base)
            var suffix = 1
            while (target.exists()) {
                target = File(dir, "${base.removeSuffix(".cube")}_$suffix.cube")
                suffix++
            }
            target.writeText(text)
            cache[target.name] = parsed
            refresh()
            LutInfo(id = target.name, name = target.nameWithoutExtension, file = target)
        }

    suspend fun delete(info: LutInfo) = withContext(Dispatchers.IO) {
        if (info.isPreset) return@withContext // 標準プリセットは削除不可
        info.file.delete()
        cache.remove(info.id)
        refresh()
    }

    suspend fun load(info: LutInfo): CubeLut = withContext(Dispatchers.IO) {
        cache[info.id]?.let { return@withContext it }
        val parsed = try {
            CubeLutParser.parse(info.file.readText())
        } catch (c: kotlinx.coroutines.CancellationException) {
            throw c
        } catch (t: Throwable) {
            // 標準プリセットのファイルが破損・途中書き込みだった場合は
            // 生成し直して1度だけ再試行する(「読み込めませんでした」の自己修復)
            val preset = info.preset
            if (preset != null) {
                android.util.Log.w(TAG, "プリセット読み込み失敗、再生成して再試行: ${info.name}", t)
                runCatching {
                    info.file.writeText(LutPresets.generateCubeText(preset))
                }
                CubeLutParser.parse(info.file.readText())
            } else {
                android.util.Log.w(TAG, "LUT読み込み失敗: ${info.name}", t)
                throw t
            }
        }
        cache[info.id] = parsed
        parsed
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
