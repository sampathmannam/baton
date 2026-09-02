package com.kaavalan.note.ui.timeline

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TimelineScreenContractTest {

    @Test
    fun `timeline uses one stable keyed list and keeps capture visible`() {
        val source = File("src/main/java/com/kaavalan/note/ui/timeline/TimelineScreen.kt").readText()
        assertTrue(source.contains("LazyColumn"))
        assertTrue(source.contains("key = { it.id }"))
        assertTrue(source.contains("FloatingActionButton"))
        assertTrue(source.contains("onCapture"))
    }

    @Test
    fun `timeline exposes filters urgent and ownership language without dashboard cards`() {
        val source = File("src/main/java/com/kaavalan/note/ui/timeline/TimelineScreen.kt").readText()
        listOf("All", "To do", "Waiting", "Done", "Urgent", "Action with you", "Waiting on another person")
            .forEach { assertTrue("missing $it", source.contains(it)) }
        listOf("Today's win", "Worry Box", "Meeting brief", "Quiet a while")
            .forEach { assertTrue("obsolete dashboard copy present: $it", !source.contains(it)) }
    }

    @Test
    fun `main navigation starts on Timeline and exposes exactly Timeline People Ask AI`() {
        val source = File("src/main/java/com/kaavalan/note/MainActivity.kt").readText()
        assertTrue(source.contains("startDestination = Routes.TIMELINE"))
        assertTrue(source.contains("Routes.TIMELINE"))
        assertTrue(source.contains("Routes.PEOPLE"))
        assertTrue(source.contains("Routes.ASK_AI"))
        assertTrue(!source.contains("route = \"settings-tab\""))
    }
}
