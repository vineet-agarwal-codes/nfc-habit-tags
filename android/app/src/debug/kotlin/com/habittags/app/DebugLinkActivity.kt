package com.habittags.app

import com.habittags.core.store.APP_KEY_PREFIX
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.habittags.app.backend.UsageReporting
import com.habittags.app.block.BlockController
import com.habittags.core.mode.ModeEngine
import kotlinx.coroutines.runBlocking

/**
 * Debug-only deep links so emulator flows can simulate what needs real hardware:
 * habittags-debug://tap/<tagId|last>, habittags-debug://gate-words/<n>, habittags-debug://expire-soon/<tagId|last>,
 * habittags-debug://param/<tagId|last|app:recipeId>/<key>/<value>, habittags-debug://backend/<real|fixture|offline>,
 * habittags-debug://end/<tagId|last|app:recipe>, habittags-debug://report-now, habittags-debug://zone-fake/<spot|off>, habittags-debug://zone-lab
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
            "zone-lab" -> startActivity(Intent(this, com.habittags.app.zone.ZoneLabActivity::class.java))
            "zone-fake" -> DebugSettings.setZoneFakeSpot(this, arg?.takeIf { it != "off" })
            "gate-words" -> arg?.toIntOrNull()?.let { DebugSettings.setGateWords(this, it) }
            "param" -> {
                val (id, key, value) = uri.pathSegments.takeIf { it.size == 3 } ?: return finish()
                if (id.startsWith(APP_KEY_PREFIX)) {
                    val recipeId = id.removePrefix(APP_KEY_PREFIX)
                    app.update { s -> s.copy(recipeParams = s.recipeParams + (recipeId to (s.recipeParams[recipeId].orEmpty() + (key to value.toInt())))) }
                } else {
                    resolve(id)?.let { tagId ->
                        app.update { s ->
                            val tag = s.tags[tagId] ?: return@update s
                            s.copy(tags = s.tags + (tagId to tag.copy(params = tag.params + (key to value.toInt()))))
                        }
                    }
                }
            }
            // Ends the mode the way the typing gate does (no end actions), for flows that don't type the passage.
            "end" -> resolve(arg)?.let { id ->
                app.update { s ->
                    val recipeId = s.tags[id]?.recipeId ?: id.removePrefix(APP_KEY_PREFIX)
                    val key = s.active.keys.firstOrNull { it == id || s.active.getValue(it).recipeId == recipeId } ?: return@update s
                    s.copy(active = ModeEngine.endViaGate(s.active, key))
                }
                BlockController.sync(this)
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
