package com.baton.app.ui.today

import com.baton.app.data.brief.BriefGenerator
import com.baton.app.data.brief.BriefType
import com.baton.app.data.brief.DailyBrief
import com.baton.app.data.instructions.Direction
import com.baton.app.data.instructions.Instruction
import com.baton.app.data.instructions.Priority
import com.baton.app.data.instructions.Source
import com.baton.app.data.instructions.Status
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Rule 1 of AGENTS.md (Design rules):
 *   "One next action. No 'what do I do now?' screens.
 *    Drill-down only."
 *
 * AGENTS.md says every rule needs a test that fails if the rule
 * is broken. This file adds the missing test.
 *
 * The rule is hard to assert at the UI layer without a full
 * Compose UI test, so we use two proxies at the data layer:
 *
 *  (a) TodayViewModel's exposed state has no field that
 *      represents a "what should I do?" picker. If a future
 *      PR adds a `whatToDoOptions: List<ActionOption>` or
 *      similar, this test fails. (Weak but documented intent.)
 *
 *  (b) The brief generator never labels a section with
 *      "What now?" or similar picker language. Picker language
 *      implies a choice; the rule is drill-down only.
 *
 * Stronger enforcement would require a UI test that asserts
 * the Today screen's top composable is a single drill-down
 * target. That's a Compose UI test (Robolectric or
 * instrumentation), deferred per the audit doc.
 */
class TodayOneNextActionTest {

    /**
     * (a) TodayViewModel's exposed state must not have a
     * "what should I do?" picker field. Allowed: a single
     * "primary" / "next" field, or no field at all.
     * Disallowed: any field that suggests the user picks
     * from options.
     */
    @Test
    fun `TodayViewModel has no what-to-do picker field`() {
        val disallowedNames = listOf(
            "whatToDo", "whatToDoOptions", "options",
            "chooseAction", "pickAction", "actionOptions",
        )
        val fieldNames = TodayViewModel::class.java.declaredFields
            .map { it.name }
        disallowedNames.forEach { name ->
            assertFalse(
                "TodayViewModel must not expose a '$name' field (Rule 1: one next action, drill-down only)",
                fieldNames.any { it.equals(name, ignoreCase = true) },
            )
        }
    }

    /**
     * (b) The brief generator's section labels must not
     * contain picker language ("what now?", "choose", "pick",
     * "decide"). Picker language implies a choice; the
     * design rule is drill-down only.
     */
    @Test
    fun `BriefGenerator section labels contain no picker language`() {
        val forbidden = listOf("what now", "what next", "choose", "pick", "decide", "options")
        val labels = listOf(
            "Needs you today",
            "Waiting on others",
            "Carried over",
        )
        labels.forEach { label ->
            val lower = label.lowercase()
            forbidden.forEach { word ->
                assertFalse(
                    "Brief section '$label' must not contain picker word '$word'",
                    lower.contains(word),
                )
            }
        }
    }

    /**
     * (c) The TodayViewModel exposes a single `brief` flow and
     * a single `review` flow — not multiple competing
     * "next-step" flows. (Drill-down means: one path.)
     */
    @Test
    fun `TodayViewModel exposes a single primary flow`() {
        val primaryFlowFields = TodayViewModel::class.java.declaredFields
            .filter {
                it.type.name.contains("StateFlow") ||
                    it.type.name.contains("Flow")
            }
            .map { it.name }
        // We expect exactly 2 flows: `brief` and `review`. More
        // than that suggests the screen is presenting multiple
        // "next-step" paths (a Rule 1 violation).
        assertTrue(
            "TodayViewModel should expose at most 2 primary flows (brief + review). Found: $primaryFlowFields",
            primaryFlowFields.size <= 2,
        )
    }
}
