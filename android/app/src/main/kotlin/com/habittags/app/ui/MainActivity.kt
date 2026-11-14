package com.habittags.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val setupTagId = intent.getStringExtra(EXTRA_SETUP_TAG_ID)
        setContent { MaterialTheme { Text(if (setupTagId != null) "Set up this tag" else "Habit Tags") } }
    }

    companion object {
        const val EXTRA_SETUP_TAG_ID = "setupTagId"
        const val EXTRA_CHECKLIST_TITLE = "checklistTitle"
        const val EXTRA_CHECKLIST_ITEMS = "checklistItems"
    }
}
