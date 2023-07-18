package com.ccps.remotedesktop.exception

import okhttp3.Request
import okhttp3.Response

class ApiException(message: String, val request: Request, val response: Response?): RuntimeException(message) {
}