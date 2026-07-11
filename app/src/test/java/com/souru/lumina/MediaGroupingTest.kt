package com.souru.lumina

import com.souru.lumina.util.MediaGrouping
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class MediaGroupingTest {

    private data class Item(val time: Long, val date: LocalDate)

    private val d0705: LocalDate = LocalDate.of(2026, 7, 5)
    private val d0704: LocalDate = LocalDate.of(2026, 7, 4)

    @Test
    fun `同じ日付が飛び飛びに現れても1セクションに統合される(重複ヘッダー回帰テスト)`() {
        // DATE_TAKENがnullの動画がソート末尾に回り、表示日付だけ既出日付になるケースを再現
        val items = listOf(
            Item(time = 1_000_000, date = d0705),
            Item(time = 900_000, date = d0704),
            Item(time = 0, date = d0705), // DATE_TAKEN=0 → DATE_ADDED由来で7/5
        )
        val sections = MediaGrouping.byDateDescending(items, { it.time }, { it.date })

        // 日付キーは必ず一意(LazyGridのキー重複=クラッシュが構造的に起きない)
        assertEquals(sections.size, sections.map { it.first }.distinct().size)
        assertEquals(2, sections.size)
        assertEquals(3, sections.sumOf { it.second.size })
        assertEquals(2, sections.first { it.first == d0705 }.second.size)
    }

    @Test
    fun `セクションとセクション内は撮影時刻の降順に並ぶ`() {
        val items = listOf(
            Item(100, d0704),
            Item(300, d0705),
            Item(200, d0705),
        )
        val sections = MediaGrouping.byDateDescending(items, { it.time }, { it.date })
        assertEquals(d0705, sections[0].first)
        assertEquals(listOf(300L, 200L), sections[0].second.map { it.time })
        assertEquals(d0704, sections[1].first)
    }

    @Test
    fun `空リストは空セクション`() {
        val sections = MediaGrouping.byDateDescending(emptyList<Item>(), { it.time }, { it.date })
        assertEquals(0, sections.size)
    }
}
