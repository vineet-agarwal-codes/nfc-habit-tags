package com.habittags.core.realtime

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class RealtimeTest {
    private fun json(s: String) = Json.parseToJsonElement(s)

    @Test
    fun messages() {
        assertEquals("wss://x.supabase.co/realtime/v1/websocket?apikey=k&vsn=1.0.0", Realtime.socketUrl("https://x.supabase.co", "k"))
        assertEquals(
            json("""{"topic":"realtime:catalog","event":"phx_join","ref":"1","payload":{"config":{"postgres_changes":[{"event":"*","schema":"public","table":"recipes"},{"event":"*","schema":"public","table":"app_groups"}]}}}"""),
            json(Realtime.joinMessage(1)),
        )
        assertEquals(json("""{"topic":"phoenix","event":"heartbeat","ref":"2","payload":{}}"""), json(Realtime.heartbeat(2)))
    }

    @Test
    fun catalogChanges() {
        val cases = listOf(
            """{"topic":"realtime:catalog","event":"postgres_changes","payload":{"data":{"table":"recipes"}},"ref":null}""" to true,
            """{"topic":"realtime:other","event":"postgres_changes","payload":{},"ref":null}""" to false,
            """{"topic":"realtime:catalog","event":"phx_reply","payload":{"status":"ok"},"ref":"1"}""" to false,
            """{"topic":"phoenix","event":"phx_reply","payload":{},"ref":"2"}""" to false,
            "garbage" to false,
            "[1,2]" to false,
        )
        for ((message, expected) in cases) assertEquals(expected, Realtime.isCatalogChange(message), message)
    }
}
