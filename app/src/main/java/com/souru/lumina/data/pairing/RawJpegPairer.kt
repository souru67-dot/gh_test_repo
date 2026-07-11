package com.souru.lumina.data.pairing

import kotlin.math.abs

/**
 * ペアリング判定に必要な最小限の情報。Androidクラスに依存しないため
 * JVMユニットテストできる。
 */
data class PairCandidate(
    val id: Long,
    val baseName: String,
    val dateTakenMs: Long,
    val isRaw: Boolean,
)

/**
 * 同時撮影された RAW(.DNG) と JPEG のペアリング。
 * ファイル名の拡張子を除いた共通部分が一致し、かつ撮影日時が
 * [MAX_TIME_DIFF_MS] 以内のものをペアとみなす。
 * 同名グループ内に複数候補がある場合は撮影時刻が最も近いものを採用する。
 */
object RawJpegPairer {

    const val MAX_TIME_DIFF_MS = 5_000L

    /** 戻り値は id -> ペア相手の id。ペアの両側がエントリされる。 */
    fun pair(candidates: List<PairCandidate>): Map<Long, Long> {
        val result = HashMap<Long, Long>()
        val byBase = candidates.groupBy { normalizeBaseName(it.baseName) }
        for ((base, group) in byBase) {
            if (base.isEmpty()) continue
            val raws = group.filter { it.isRaw }.sortedBy { it.dateTakenMs }
            val others = group.filter { !it.isRaw }.sortedBy { it.dateTakenMs }
            if (raws.isEmpty() || others.isEmpty()) continue

            val used = BooleanArray(others.size)
            for (raw in raws) {
                var bestIndex = -1
                var bestDiff = Long.MAX_VALUE
                for (i in others.indices) {
                    if (used[i]) continue
                    val diff = abs(others[i].dateTakenMs - raw.dateTakenMs)
                    if (diff < bestDiff) {
                        bestDiff = diff
                        bestIndex = i
                    }
                }
                if (bestIndex >= 0 && bestDiff <= MAX_TIME_DIFF_MS) {
                    used[bestIndex] = true
                    result[raw.id] = others[bestIndex].id
                    result[others[bestIndex].id] = raw.id
                }
            }
        }
        return result
    }

    fun normalizeBaseName(baseName: String): String = baseName.trim().lowercase()
}
