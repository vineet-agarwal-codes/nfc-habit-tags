package com.habittags.app.nfc

import android.app.Instrumentation
import android.content.Intent
import android.nfc.NfcAdapter
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.habittags.app.HabitTagsApp
import com.habittags.app.ui.MainActivity
import com.habittags.core.mode.InstalledTag
import com.habittags.core.store.AppState
import com.habittags.core.tag.TagPayload
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TapActivityTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app get() = instrumentation.targetContext.applicationContext as HabitTagsApp
    private val tagId = TagPayload.newTagId()

    @Before
    fun seed() {
        app.update { AppState(tags = mapOf(tagId to InstalledTag(tagId, "desk-deep-work", "Desk: Deep Work", emptyMap()))) }
    }

    @After
    fun reset() {
        app.update { AppState() }
    }

    private fun tap(id: String) {
        val intent = Intent(NfcAdapter.ACTION_NDEF_DISCOVERED)
            .setClass(instrumentation.targetContext, TapActivity::class.java)
            .putExtra(NfcAdapter.EXTRA_NDEF_MESSAGES, arrayOf(TagWriter.message(id)))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val before = app.state.value
        instrumentation.targetContext.startActivity(intent)
        waitUntil { app.state.value !== before }
        instrumentation.waitForIdleSync()
    }

    private fun waitUntil(timeoutMs: Long = 5_000, condition: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (!condition() && System.currentTimeMillis() < end) Thread.sleep(50)
    }

    @Test
    fun tapStartsModeAndDesksTagCantEndIt() {
        tap(tagId)
        val mode = app.state.value.active[tagId]
        assertNotNull(mode)
        assertEquals(setOf("social", "video"), mode!!.blockedGroups)
        tap(tagId)
        Thread.sleep(500)
        // Desk: Deep Work has tagCanEnd = false, so a second tap leaves it on.
        assertTrue(tagId in app.state.value.active)
    }

    @Test
    fun unknownTagOpensSetup() {
        val monitor = Instrumentation.ActivityMonitor(MainActivity::class.java.name, null, false)
        instrumentation.addMonitor(monitor)
        val unknown = TagPayload.newTagId()
        tap(unknown)
        val opened = instrumentation.waitForMonitorWithTimeout(monitor, 5_000)
        assertNotNull(opened)
        assertEquals(unknown, opened.intent.getStringExtra(MainActivity.EXTRA_SETUP_TAG_ID))
        opened.finish()
        assertTrue(app.state.value.active.isEmpty())
    }
}
