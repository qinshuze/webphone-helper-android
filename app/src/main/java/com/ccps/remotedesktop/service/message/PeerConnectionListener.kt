package com.ccps.remotedesktop.service.message

import com.orhanobut.logger.Logger
import org.webrtc.CandidatePairChangeEvent
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnection.PeerConnectionState.*
import org.webrtc.RtpReceiver

open class PeerConnectionListener: PeerConnection.Observer {
    override fun onSignalingChange(p0: PeerConnection.SignalingState?) {
        Logger.d("onSignalingChange: ${p0?.name}")
    }

    override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState?) {
        Logger.d("onIceConnectionChange: ${p0?.name}")
    }

    override fun onIceConnectionReceivingChange(p0: Boolean) {

    }

    override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {
        Logger.d("onIceGatheringChange: ${p0?.name}")
    }

    override fun onIceCandidate(p0: IceCandidate?) {
        Logger.d("onIceCandidate: ${p0.toString()}")
    }

    override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {

    }

    override fun onAddStream(p0: MediaStream?) {
        Logger.d("onAddStream: ${p0.toString()}")
    }

    override fun onRemoveStream(p0: MediaStream?) {

    }

    override fun onDataChannel(p0: DataChannel?) {

    }

    override fun onRenegotiationNeeded() {

    }

    override fun onAddTrack(p0: RtpReceiver?, p1: Array<out MediaStream>?) {
        Logger.d("onAddTrack: ${p0.toString()}")
    }

    override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
        super.onConnectionChange(newState)
        when (newState) {
            CONNECTED -> Logger.d("webrtc 对等连接建立成功")
            NEW -> {}
            CONNECTING -> Logger.d("webrtc 对等连接正在建立")
            DISCONNECTED -> Logger.d("webrtc 对等连接已断开")
            FAILED -> Logger.d("webrtc 对等连接发生错误")
            CLOSED -> Logger.d("webrtc 对等连接已关闭")
            null -> {}
        }
    }

    override fun onStandardizedIceConnectionChange(newState: PeerConnection.IceConnectionState?) {
        super.onStandardizedIceConnectionChange(newState)
        Logger.d("onStandardizedIceConnectionChange: ${newState?.name}")
    }

    override fun onSelectedCandidatePairChanged(event: CandidatePairChangeEvent?) {
        super.onSelectedCandidatePairChanged(event)
        Logger.d("onSelectedCandidatePairChanged: ${event.toString()}")
    }
}