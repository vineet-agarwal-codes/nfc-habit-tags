package com.habittags.app.ui

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.habittags.app.DebugSettings
import com.habittags.app.HabitTagsApp
import com.habittags.core.mode.ActiveMode
import com.habittags.core.mode.InstalledTag
import com.habittags.core.store.AppState
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GateTextFieldTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val app get() = context.applicationContext as HabitTagsApp
    private val tagId = "gateTestTag0000000000A"

    @Before
    fun seed() {
        DebugSettings.setGateWords(context, 5)
        val now = System.currentTimeMillis()
        app.update {
            AppState(
                tags = mapOf(tagId to InstalledTag(tagId, "desk-deep-work", "Desk: Deep Work", mapOf("strictMinutes" to 60))),
                active = mapOf(tagId to ActiveMode(tagId, "desk-deep-work", now, now + 3_600_000, now + 3_600_000, setOf("social"), false)),
                onboarded = true,
            )
        }
    }

    @After
    fun reset() {
        DebugSettings.setGateWords(context, 0)
        app.update { AppState() }
    }

    @Test
    fun bulkInsertRejectedTypingEndsModeEvenDuringStrict() {
        val intent = Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_GATE_TAG_ID, tagId)
        ActivityScenario.launch<MainActivity>(intent).use {
            lateinit var target: String
            onView(withContentDescription("gate-input")).check { view, _ -> target = (view as GateEditText).target }

            onView(withContentDescription("gate-input")).perform(replaceText(target))
            onView(withContentDescription("gate-input")).check(matches(withText("")))
            assertTrue(tagId in app.state.value.active)

            onView(withContentDescription("gate-input")).perform(click(), typeText(target))
            val end = System.currentTimeMillis() + 5_000
            while (tagId in app.state.value.active && System.currentTimeMillis() < end) Thread.sleep(50)
            assertTrue(tagId !in app.state.value.active)
        }
    }
}
