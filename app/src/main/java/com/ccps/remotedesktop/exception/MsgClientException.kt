package com.ccps.remotedesktop.exception

import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket

class MsgClientException(message: String, val request: Request, val response: Response? = null, val code: Int? = 0): RuntimeException(message) {
}