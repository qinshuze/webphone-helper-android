package com.ccps.remotedesktop.utils

import com.ccps.remotedesktop.exception.MsgClientException
import okhttp3.*
import okio.ByteString
import java.util.UUID

open class MsgClient(val url: String): WebSocketListener() {
    var disconnected = false
    val socketId = UUID.randomUUID().toString()
    private val request = Request.Builder().url(url).build()
    val socket: WebSocket = OkHttpClient.Builder().build().newWebSocket(request, this)

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        super.onClosed(webSocket, code, reason)
        throw MsgClientException(reason, request, code = code)
    }

    override fun onOpen(webSocket: WebSocket, response: Response) {
        super.onOpen(webSocket, response)
    }

    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
        super.onMessage(webSocket, bytes)
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        super.onMessage(webSocket, text)
    }

    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
        super.onClosing(webSocket, code, reason)
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        super.onFailure(webSocket, t, response)
        throw MsgClientException(t.message.toString(), request, response)
    }
}