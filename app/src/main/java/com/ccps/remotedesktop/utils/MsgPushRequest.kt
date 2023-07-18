package com.ccps.remotedesktop.utils

import android.net.Uri
import com.ccps.remotedesktop.BuildConfig
import com.ccps.remotedesktop.activity.Home
import com.orhanobut.logger.Logger
import java.util.Base64
import java.util.Date
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class MsgPushRequest: ApiRequest() {
    data class GetTokenResult(val token: String)

    companion object {
        const val url = BuildConfig.MSG_PUSH_API_URL

        private fun generateSignature(message: String, secretKey: String): String {
            val hmacSha512 = Mac.getInstance("HmacSHA512")
            val keySpec = SecretKeySpec(secretKey.toByteArray(), "HmacSHA512")
            hmacSha512.init(keySpec)
            val signatureBytes = hmacSha512.doFinal(message.toByteArray())
            return Base64.getEncoder()
                .encodeToString(Base64.getEncoder().encodeToString(signatureBytes).toByteArray())
        }

        fun getAuthToken(): String {
            val authUrlBuilder = Uri.parse("$url/token").buildUpon()
            authUrlBuilder.appendQueryParameter("access_key", BuildConfig.MSG_PUSH_ACCESS_KEY)
            authUrlBuilder.appendQueryParameter(
                "timestamp",
                (Date().time / 1000).toInt().toString()
            )

            val authUrl = authUrlBuilder.build()
            val signStr =
                "GET ${authUrl.host}:${authUrl.port}${authUrl.path}?${authUrl.query}"
            val signature = generateSignature(signStr, BuildConfig.MSG_PUSH_ACCESS_SECRET)

            val tokenResult = get<GetTokenResult>(authUrl.toString() + "&signature=${signature}")
            return tokenResult.token
        }
    }
}