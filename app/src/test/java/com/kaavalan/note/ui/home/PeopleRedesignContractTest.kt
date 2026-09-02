package com.kaavalan.note.ui.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PeopleRedesignContractTest {

    @Test
    fun `people screen uses simple search people group labels and capture`() {
        val source = File("src/main/java/com/kaavalan/note/ui/home/HomeScreen.kt").readText()

        listOf("people_search", "AddGroupLabelSheet", "person_phone", "NoteBar", "CaptureSheet")
            .forEach { assertTrue("missing $it", source.contains(it)) }
        listOf("HomeHierarchyAwarePersonList", "popularTags", "stalePersonIds", "selectedTagId")
            .forEach { assertFalse("obsolete active path remains: $it", source.contains(it)) }
    }

    @Test
    fun `person detail has active and completed instructions without legacy relationship features`() {
        val source = File("src/main/java/com/kaavalan/note/ui/home/PersonDetailScreen.kt").readText()

        listOf("person_active_instructions", "person_completed_instructions", "person.phone")
            .forEach { assertTrue("missing $it", source.contains(it)) }
        listOf("PersonLinksRow", "ImportantDatesRow", "PersonSensitiveDialog", "setPersonSensitive")
            .forEach { assertFalse("obsolete active path remains: $it", source.contains(it)) }
    }

    @Test
    fun `add person captures exactly the four editable profile fields`() {
        val source = File("src/main/java/com/kaavalan/note/ui/home/AddPersonSheet.kt").readText()

        listOf("person_name", "person_phone", "person_rank_or_role", "person_unit")
            .forEach { assertTrue("missing $it", source.contains(it)) }
    }
}
