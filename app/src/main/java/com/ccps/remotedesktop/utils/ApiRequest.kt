package com.ccps.remotedesktop.utils

import com.ccps.remotedesktop.exception.ApiException
import com.google.gson.Gson
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.File

open class ApiRequest {
    companion object {
        fun request(
            method: String,
            url: String,
            data: Any? = null,
            headers: Headers? = null
        ): Response {
            val client = OkHttpClient()
            val requestBuild = Request.Builder()

            val body: RequestBody? = if (data != null) {
                when (data) {
                    is File -> {
                        data.asRequestBody("application/octet-stream".toMediaTypeOrNull())
                    }

                    is ByteArray -> {
                        data.toRequestBody("application/octet-stream".toMediaTypeOrNull())
                    }

                    is RequestBody -> data

                    else -> {
                        Gson().toJson(data)
                            .toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
                    }
                }
            } else {
                null
            }

            requestBuild.url(url).method(method, body)
            headers?.let { requestBuild.headers(headers) }

            val request = requestBuild.build()
            try {
                val call = client.newCall(request)
                return call.execute()
            } catch (e: Exception) {
                throw ApiException(e.toString(), request, null)
            }
        }

        inline fun <reified T> request(method: String, url: String, data: Any? = null, headers: Headers? = null): T {
            val response = request(method, url, data, headers)
            val body = response.body ?: throw ApiException("空响应", response.request, response)
            return Gson().fromJson(body.string(), T::class.java)
        }

        fun get(url: String, headers: Headers? = null): Response {
            return request("GET", url, null, headers)
        }

        inline fun <reified T> get(url: String, headers: Headers? = null): T {
            val response = get(url, headers)
            val body = response.body ?: throw ApiException("空响应", response.request, response)
            return Gson().fromJson(body.string(), T::class.java)
        }

        fun post(url: String, data: Any?, headers: Headers? = null): Response {
            return request("POSt", url, data, headers)
        }

        inline fun <reified T> post(url: String, data: Any?, headers: Headers? = null): T {
            val response = post(url, data, headers)
            val body = response.body ?: throw ApiException("空响应", response.request, response)
            return Gson().fromJson(body.string(), T::class.java)
        }
    }
}