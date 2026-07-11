package com.souru.lumina.util

import java.time.LocalDate

/**
 * 日付グルーピング。入力の並び順に依存せず、
 * 「日付ごとに必ず1セクション」を構造的に保証する
 * (同じ日付が飛び飛びに現れてもヘッダーが重複しない)。
 */
object MediaGrouping {

    /** 撮影時刻の降順に並べたうえで日付ごとにまとめる。 */
    fun <T> byDateDescending(
        items: List<T>,
        timeOf: (T) -> Long,
        dateOf: (T) -> LocalDate,
    ): List<Pair<LocalDate, List<T>>> =
        items
            .sortedByDescending(timeOf)
            .groupBy(dateOf)
            .toList()
}
