package com.ccps.remotedesktop.service.message

import com.orhanobut.logger.Logger
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription

open class SdpListener: SdpObserver {
    override fun onCreateSuccess(p0: SessionDescription?) {
        if (p0 == null) {
            Logger.d("offer/answer 创建成功：null")
            return
        }

        Logger.d("${p0.type.name} 创建成功：$p0")
    }

    override fun onSetSuccess() {
        Logger.d("设置sdp成功")
    }

    override fun onCreateFailure(p0: String?) {
        if (p0 == null) {
            Logger.w("offer/answer 创建失败：null")
            return
        }
        Logger.w("offer/answer 创建失败：${p0}")
    }

    override fun onSetFailure(p0: String?) {
        if (p0 == null) {
            Logger.w("sdp 设置失败：null")
            return
        }
        Logger.w("sdp 设置失败：${p0}")
    }
}