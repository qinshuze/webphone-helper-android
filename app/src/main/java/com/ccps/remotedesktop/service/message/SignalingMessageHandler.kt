package com.ccps.remotedesktop.service.message

import android.content.Context
import com.google.gson.Gson
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera1Enumerator
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.PeerConnection.IceServer
import org.webrtc.PeerConnection.PeerConnectionState.*
import org.webrtc.PeerConnectionFactory
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoCapturer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack

class SignalingMessageHandler(private val client: Client, private val context: Context) {
    data class MessageContent(
        val msgType: String = "",
        val msgId: String = "",
        val payload: Any = ""
    )

    data class CandidateData(val sdpMid: String, val sdpMLineIndex: Int, val sdp: String)

    /** WebRTC **/
    private val eglBase = EglBase.create().eglBaseContext
    private val peerConnectionFactory: PeerConnectionFactory = getPeerConnectionFactory()
    private var peerConnection: PeerConnection? = null

    /** 相机视频源 **/
    private var videoCapturer: VideoCapturer? = null
    private var videoSource: VideoSource? = null
    private var videoTrack: VideoTrack? = null
    private var audioSource: AudioSource? = null
    private var audioTrack: AudioTrack? = null

    /** 获取对等连接工厂实例 **/
    private fun getPeerConnectionFactory(): PeerConnectionFactory {
        val options = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(true)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)

        val builder = PeerConnectionFactory.builder()
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase))
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase, true, true))

        return builder.createPeerConnectionFactory()
    }

    /** 获取视频捕获器 **/
    private fun getVideoCapturer(): VideoCapturer {
        val cameraEnumerator = Camera1Enumerator(false)
        val deviceNames = cameraEnumerator.deviceNames

        // Choose the front camera
        val frontCameraDeviceName = deviceNames.find { cameraEnumerator.isFrontFacing(it) }
        return cameraEnumerator.createCapturer(frontCameraDeviceName, null)
    }

    /** 获取视频源 **/
    private fun getVideoSource(): VideoSource {
        val videoCapturer =
            videoCapturer ?: throw IllegalStateException("Video capturer not initialized")
        val videoSource = peerConnectionFactory.createVideoSource(videoCapturer.isScreencast)
        videoCapturer.initialize(
            SurfaceTextureHelper.create(
                "CameraThread",
                eglBase
            ), context, videoSource.capturerObserver)

        videoCapturer.startCapture(640, 480, 30)

        return videoSource
    }

    /** 获取视频轨道 **/
    private fun getVideoTrack(): VideoTrack {
        val videoSource = videoSource ?: throw IllegalStateException("Video source not initialized")
        val localVideoTrack = peerConnectionFactory.createVideoTrack("videoTrack", videoSource)
        localVideoTrack.setEnabled(true)

        return localVideoTrack
    }

    /** 获取音频源 **/
    private fun getAudioSource(): AudioSource {
        val videoSource = peerConnectionFactory.createAudioSource(MediaConstraints())
        return videoSource
    }

    /** 获取音频轨道 **/
    private fun getAudioTrack(): AudioTrack {
        val audioSource = audioSource ?: throw IllegalStateException("Audio source not initialized")
        val localAudioTrack = peerConnectionFactory.createAudioTrack("audioTrack", audioSource)
        localAudioTrack.setEnabled(true)

        return localAudioTrack
    }

    /** 获取候选服务器列表 **/
    private fun getIceServerList(): List<IceServer> {
        return listOf(
            // 谷歌公共STUN服务器
//            IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            IceServer.builder("turn:web-phone.xianyudev.top:3478")
                .setUsername("qsz")
                .setPassword("MWhD2ZEVkmzpHnB")
                .createIceServer()
        )
    }

    /**
     * 获取对等连接
     * @param receiver 接收人，当收集到信令信息后要发送给到的人
     */
    private fun getPeerConnection(receiver: String, signalingId: String): PeerConnection? {
        // 创建对等连接
        val peerConnectionConfig = PeerConnection.RTCConfiguration(getIceServerList())
        val peer = peerConnectionFactory.createPeerConnection(
            peerConnectionConfig,
            object : PeerConnectionListener() {
                override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
                    super.onConnectionChange(newState)
                    when (newState) {
                        NEW          -> {}
                        CONNECTING   -> {}
                        CONNECTED    -> {}
                        DISCONNECTED -> {}
                        FAILED       -> closeCameraVideoResource()
                        CLOSED       -> closeCameraVideoResource()
                        null         -> {}
                    }
                }

                override fun onIceCandidate(p0: IceCandidate?) {
                    super.onIceCandidate(p0)
                    if (p0 == null) return

                    client.send(
                        Client.SendMessage(
                            names = listOf(receiver),
                            content = Gson().toJson(
                                MessageContent(
                                    "signaling@candidate", signalingId, payload = CandidateData(
                                        p0.sdpMid, p0.sdpMLineIndex, p0.sdp
                                    )
                                )
                            )
                        )
                    )
                }
            })

        val signalingHandler = object : ClientEvent.Message {
            override fun invoke(message: Client.ReceiveMessage) {
                if (message.sender != receiver) return

                if (peer == null || peer.connectionState() == CLOSED) {
                    client.removeOnMessage(this)
                    return
                }

                val content = Gson().fromJson(message.content, MessageContent::class.java)
                if (content.msgId != signalingId) return

                when (content.msgType) {
                    "signaling@closeCamera" -> {
                        peer.close()
                    }
                    "signaling@offer"     -> {
                        // 设置远程邀约信息
                        peer.setRemoteDescription(
                            object : SdpListener() {
                                override fun onSetSuccess() {
                                    super.onSetSuccess()
                                    // 设置成功后，开始创建应答信息
                                    peer.createAnswer(object : SdpListener() {
                                        override fun onCreateSuccess(p0: SessionDescription?) {
                                            super.onCreateSuccess(p0)
                                            if (p0 == null) return
                                            // 创建成功，保存本地
                                            peer.setLocalDescription(this, p0)
                                        }

                                        override fun onSetSuccess() {
                                            super.onSetSuccess()
                                            // 设置成功后，发送应答信息
                                            client.send(
                                                Client.SendMessage(
                                                    names = listOf(receiver),
                                                    content = Gson().toJson(
                                                        MessageContent(
                                                            "signaling@answer",
                                                            signalingId,
                                                            payload = peer.localDescription.description
                                                        )
                                                    )
                                                )
                                            )
                                        }
                                    }, MediaConstraints())
                                }
                            },
                            SessionDescription(
                                SessionDescription.Type.OFFER,
                                content.payload.toString()
                            )
                        )
                    }

                    "signaling@answer"    -> {
                        // 设置远程应答信息
                        peer.setRemoteDescription(
                            object : SdpListener() {
                            }, SessionDescription(
                                SessionDescription.Type.ANSWER,
                                content.payload.toString()
                            )
                        )
                    }

                    "signaling@candidate" -> {
                        // 添加候选服务器
                        val candidateData = Gson().fromJson(
                            Gson().toJson(content.payload),
                            CandidateData::class.java
                        )
                        peer.addIceCandidate(
                            IceCandidate(
                                candidateData.sdpMid,
                                candidateData.sdpMLineIndex,
                                candidateData.sdp
                            )
                        )
                    }
                }
            }
        }

        client.onMessage(signalingHandler)
        return peer
    }

    /** 关闭相机视频资源 **/
    private fun closeCameraVideoResource() {
        if (videoCapturer != null) {
            videoCapturer!!.dispose()
            videoCapturer!!.stopCapture()
            videoCapturer = null
        }

        if (videoSource != null) {
            videoSource!!.dispose()
            videoSource = null
        }

        if (videoTrack != null) {
            videoTrack!!.dispose()
            videoTrack = null
        }

        if (audioSource != null) {
            audioSource!!.dispose()
            audioSource = null
        }

        if (audioTrack != null) {
            audioTrack!!.dispose()
            audioTrack = null
        }
    }

    init {
        client.onMessage {
            val content = Gson().fromJson(it.content, MessageContent::class.java)
            when (content.msgType) {
                "signaling@cameraOffer" -> cameraOfferHandle(content, it.sender)
                "signling@screenOffer" -> screenOfferHandle(content, it.sender)
//                "signling@camare" -> fileUploadHandle(content, it.sender)
//                "signling@screen" -> apiOfferHandle(content, it.sender)
            }
        }
    }

    private fun screenOfferHandle(content: MessageContent, receiver: String) {
        val peerConnection = getPeerConnection(receiver, content.msgId)
                             ?: throw IllegalStateException("PeerConnection create fail: null")
        val constraints = MediaConstraints().apply {
//            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        }
    }

    private fun cameraOfferHandle(content: MessageContent, receiver: String) {
        val peerConnection = getPeerConnection(receiver, content.msgId)
                         ?: throw IllegalStateException("PeerConnection create fail: null")

        // 创建视频源
        val constraints = MediaConstraints().apply {
//            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
        }

        videoCapturer = videoCapturer ?: getVideoCapturer()
        videoSource = videoSource ?: getVideoSource()
        videoTrack = videoTrack ?: getVideoTrack()
//        audioSource = audioSource ?: getAudioSource()
//        audioTrack = audioTrack ?: getAudioTrack()

        val mediaStream = peerConnectionFactory.createLocalMediaStream("mediaStream")
        mediaStream.addTrack(videoTrack)
//        mediaStream.addTrack(audioTrack)

        peerConnection.addStream(mediaStream)

        // 创建邀约信息
        peerConnection.createOffer(object : SdpListener() {
            override fun onCreateSuccess(p0: SessionDescription?) {
                super.onCreateSuccess(p0)
                // 创建成功后设置本地，并发送
                peerConnection.setLocalDescription(object : SdpListener() {
                    override fun onSetSuccess() {
                        super.onSetSuccess()
                        client.send(
                            Client.SendMessage(
                                names = listOf(receiver),
                                content = Gson().toJson(
                                    MessageContent(
                                        "signaling@offer",
                                        content.msgId,
                                        payload = peerConnection.localDescription?.description.toString()
                                    )
                                )
                            )
                        )
                    }
                }, p0)
            }
        }, constraints)
    }
}