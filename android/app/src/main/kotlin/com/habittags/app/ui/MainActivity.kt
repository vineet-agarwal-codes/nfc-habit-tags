package com.habittags.app.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.runtime.mutableStateOf
import com.habittags.app.HabitTagsApp
import com.habittags.app.block.BlockController
import com.habittags.app.ui.theme.MiseTheme

class MainActivity : ComponentActivity() {
    /** Latest intent-driven request (set up a tag, show a checklist, open the gate); consumed by [AppNav]. */
    val pending = mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pending.value = intent
        HabitTagsApp.from(this).catalogNow()
        BlockController.sync(this)
        // Surface supplies the theme's background and text colour to every screen, including ones outside a Scaffold.
        setContent { MiseTheme { Surface(Modifier.fillMaxSize()) { AppNav(this) } } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pending.value = intent
    }

    companion object {
        const val EXTRA_SETUP_TAG_ID = "setupTagId"
        const val EXTRA_CHECKLIST_TITLE = "checklistTitle"
        const val EXTRA_CHECKLIST_ITEMS = "checklistItems"
        const val EXTRA_GATE_TAG_ID = "gateTagId"
    }
}
