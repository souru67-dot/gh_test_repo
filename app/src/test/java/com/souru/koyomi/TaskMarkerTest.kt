package com.souru.koyomi

import com.souru.koyomi.data.model.TaskMarker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskMarkerTest {

    @Test
    fun `detects task and done markers`() {
        assertTrue(TaskMarker.isTask("#koyomi-task"))
        assertTrue(TaskMarker.isTask("メモ\n#koyomi-task #koyomi-done"))
        assertFalse(TaskMarker.isTask("ふつうの予定のメモ"))
        assertFalse(TaskMarker.isTask(null))

        assertTrue(TaskMarker.isDone("#koyomi-task #koyomi-done"))
        assertFalse(TaskMarker.isDone("#koyomi-task"))
    }

    @Test
    fun `withDone toggles the done marker idempotently`() {
        val open = TaskMarker.TASK
        val done = TaskMarker.withDone(open, true)
        assertTrue(TaskMarker.isDone(done))
        assertTrue(TaskMarker.isTask(done))

        // Toggling twice does not accumulate markers.
        val doneTwice = TaskMarker.withDone(done, true)
        assertEquals(done, doneTwice)

        val reopened = TaskMarker.withDone(done, false)
        assertFalse(TaskMarker.isDone(reopened))
        assertTrue(TaskMarker.isTask(reopened))
    }

    @Test
    fun `split separates memo from markers and join restores them`() {
        val (memo, markers) = TaskMarker.split("牛乳を買う\n#koyomi-task #koyomi-done")
        assertEquals("牛乳を買う", memo)
        assertTrue(markers.contains(TaskMarker.TASK))
        assertTrue(markers.contains(TaskMarker.DONE))

        val joined = TaskMarker.join("編集後のメモ", markers)
        assertTrue(TaskMarker.isTask(joined))
        assertTrue(TaskMarker.isDone(joined))
        assertTrue(joined.startsWith("編集後のメモ"))
    }

    @Test
    fun `split on a plain event leaves everything untouched`() {
        val (memo, markers) = TaskMarker.split("ただのメモ")
        assertEquals("ただのメモ", memo)
        assertEquals("", markers)
        assertEquals("ただのメモ", TaskMarker.join(memo, markers))
        assertEquals("", TaskMarker.join("", ""))
    }
}
