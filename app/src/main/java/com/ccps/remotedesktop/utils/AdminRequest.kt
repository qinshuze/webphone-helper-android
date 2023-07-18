package com.ccps.remotedesktop.utils

import com.ccps.remotedesktop.BuildConfig
import com.ccps.remotedesktop.exception.ApiException
import com.google.gson.Gson
import okhttp3.*

class AdminRequest: ApiRequest() {
    data class QRCodeStepResult(val code: Int, val msg: String)

    companion object {
        const val url = BuildConfig.ADMIN_API_URL
        fun qrcodeStep1(qrId: String, deviceId: String): QRCodeStepResult {
            return get<QRCodeStepResult>("${url}/qrcode/step1?id=${qrId}&device_id=${deviceId}")
        }

        fun qrcodeStep2(qrId: String, deviceId: String): QRCodeStepResult {
            return get<QRCodeStepResult>("${url}/qrcode/step2?id=${qrId}&device_id=${deviceId}")
        }
    }
}