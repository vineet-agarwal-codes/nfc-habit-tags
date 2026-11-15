package com.habittags.app

import android.app.Activity
import android.os.Bundle
import com.habittags.app.backend.UsageReporting
import com.habittags.app.block.BlockController
import kotlinx.coroutines.runBlocking

/**
 * Debug-only deep links so emulator flows can simulate what needs real hardware:
 * habittags-debug://tap/<tagId|last>, habittags-debug://gate-words/<n>, habittags-debug://expire-soon/<tagId|last>,
 * habittags-debug://param/<tagId|last>/<key>/<value>, habittags-debug://backend/<real|fixture|offline>,
 * habittags-debug://report-now
 */
class DebugLinkActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent.data
        val arg = uri?.pathSegments?.firstOrNull()
        val app = HabitTagsApp.from(this)
        fun resolve(id: String?) = if (id == "last") app.state.value.tags.keys.lastOrNull() else id
        when (uri?.host) {
            "tap" -> resolve(arg)?.let { TapHandler.handle(this, it) }
            "backend" -> arg?.let {
                DebugSettings.setBackendMode(this, it)
                runBlocking { app.catalogRepository.refresh() }
            }
            "report-now" -> runBlocking { UsageReporting.sendNow(this@DebugLinkActivity) }
            "gate-words" -> arg?.toIntOrNull()?.let { DebugSettings.setGateWords(this, it) }
            "param" -> {
                val (id, key, value) = uri.pathSegments.takeIf { it.size == 3 } ?: return finish()
                resolve(id)?.let { tagId ->
                    app.update { s ->
                        val tag = s.tags[tagId] ?: return@update s
                        s.copy(tags = s.tags + (tagId to tag.copy(params = tag.params + (key to value.toInt()))))
                    }
                }
            }
            "expire-soon" -> resolve(arg)?.let { id ->
                app.update { s ->
                    val mode = s.active[id] ?: return@update s
                    s.copy(active = s.active + (id to mode.copy(autoOffAt = System.currentTimeMillis() + 60_000, strictUntil = null)))
                }
                BlockController.sync(this)
            }
        }
        finish()
    }
}
