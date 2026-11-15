package com.habittags.app.backend

import android.util.Log
import com.habittags.core.realtime.Realtime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/** Keeps a Realtime socket open while the app is in the foreground; catalog changes trigger a refetch. */
class RealtimeWatcher(
    private val client: OkHttpClient,
    private val url: String,
    private val scope: CoroutineScope,
    private val onCatalogChange: () -> Unit,
) {
    private var socket: WebSocket? = null
    private var loop: Job? = null
    private var backoffMs = MIN_BACKOFF_MS
    private var ref = 0

    @Synchronized
    fun start() {
        if (loop != null) return
        loop = scope.launch {
            while (isActive) {
                val closed = kotlinx.coroutines.CompletableDeferred<Unit>()
                val ws = client.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        backoffMs = MIN_BACKOFF_MS
                        webSocket.send(Realtime.joinMessage(nextRef()))
                    }
                    override fun onMessage(webSocket: WebSocket, text: String) {
                        if (Realtime.isCatalogChange(text)) onCatalogChange()
                    }
                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        Log.w(TAG, "realtime socket failed", t)
                        closed.complete(Unit)
                    }
                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        closed.complete(Unit)
                    }
                })
                socket = ws
                val heartbeat = launch {
                    while (isActive) {
                        delay(Realtime.HEARTBEAT_MS)
                        ws.send(Realtime.heartbeat(nextRef()))
                    }
                }
                closed.await()
                heartbeat.cancel()
                delay(backoffMs)
                backoffMs = (backoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
            }
        }
    }

    @Synchronized
    fun stop() {
        loop?.cancel()
        loop = null
        socket?.close(1000, null)
        socket = null
    }

    @Synchronized
    private fun nextRef(): Int = ++ref

    private companion object {
        const val TAG = "RealtimeWatcher"
        const val MIN_BACKOFF_MS = 2_000L
        const val MAX_BACKOFF_MS = 60_000L
    }
}
