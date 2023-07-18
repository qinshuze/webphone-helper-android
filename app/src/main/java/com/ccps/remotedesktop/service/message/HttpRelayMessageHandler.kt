package com.ccps.remotedesktop.service.message

import com.ccps.remotedesktop.service.FileService
import com.ccps.remotedesktop.utils.DataRequest
import com.google.gson.Gson
import java.io.File
import java.net.URLDecoder

class HttpRelayMessageHandler(private val client: Client) {
    data class MessageContent(
        val msgType: String = "",
        val msgId: String = "",
        val payload: Any = ""
    )

    data class SendData(
        val code: Int = 200,
        val msg: String = "ok",
        val data: Any = listOf<String>()
    )

    init {
        client.onMessage {
            val content = Gson().fromJson(it.content, MessageContent::class.java)
            when (content.msgType) {
                "httpRelay@getFileInfo" -> getFileInfoHandle(content, it.sender)
                "httpRelay@fileReceiveReady" -> fileReceiveHandle(content, it.sender)
                "httpRelay@fileUpload" -> fileUploadHandle(content, it.sender)
                "httpRelay@apiOffer" -> apiOfferHandle(content, it.sender)
            }
        }
    }

    /**
     * 获取文件信息
     * 当接收到获取文件信息消息指令后，获取对应的文件信息并提交到服务器
     */
    private fun getFileInfoHandle(content: MessageContent, receiver: String) {
        data class QueryParams(val path: String, val query: Any)
        data class FileInfo(
            val name: String = "",
            val size: Long = 0,
            val mimeType: String = "",
            val lastModified: Long = 0
        )

        val type = "httpRelay@getFileInfoAnswer"
        val queryParams = Gson().fromJson(Gson().toJson(content.payload), QueryParams::class.java)!!
        val fileInfo = FileService().getFileInfo(queryParams.path)

        if (!fileInfo.isFile || !File(queryParams.path).exists()) {
            send(
                MessageContent(type, content.msgId, SendData(404, "文件不存在或已被删除")),
                listOf(receiver)
            )
            return
        }

        send(MessageContent(type, content.msgId, SendData(
            data = FileInfo(
                fileInfo.name,
                fileInfo.size,
                fileInfo.mimeType,
                fileInfo.lastModifyTime
            )
        )), listOf(receiver))
    }

    /**
     * 文件准备就绪，开始上传文件
     * 当接收到文件准备就绪的消息指令后，将指定的文件上传到服务器
     */
    private fun fileReceiveHandle(content: MessageContent, receiver: String) {
        val type = "httpRelay@fileReceiveReadyAnswer"
        val uploadFile =
            Gson().fromJson(Gson().toJson(content.payload), DataRequest.UploadFile::class.java)
        val file = File(URLDecoder.decode(uploadFile.path, "UTF-8"))

        if (!file.isFile || !file.exists()) {
            send(
                MessageContent(type, content.msgId, SendData(404, "文件不存在或已被删除")),
                listOf(receiver)
            )
            return
        }

        Thread { DataRequest.uploadFile(uploadFile) }.start()
        send(MessageContent(type, content.msgId, SendData()), listOf(receiver))
    }

    /**
     * 文件上传处理
     * 当接收到文件上传的消息指令后，发送下载请求到服务器上下载文件
     */
    private fun fileUploadHandle(content: MessageContent, receiver: String) {
        val type = "httpRelay@fileUploadAnswer"
        val downloadFile =
            Gson().fromJson(Gson().toJson(content.payload), DataRequest.DownloadFile::class.java)
        Thread { DataRequest.downloadFile(downloadFile) }.start()

        send(MessageContent(type, content.msgId, SendData()), listOf(receiver))
    }

    /**
     * api接口邀约处理
     * 当接收到api接口邀约的消息指令后，根据消息内容处理并将结果上报到服务器
     */
    private fun apiOfferHandle(content: MessageContent, receiver: String) {
        val type = "httpRelay@apiAnswer"
        val apiOffer =
            Gson().fromJson(Gson().toJson(content.payload), DataRequest.ApiOffer::class.java)
        when (apiOffer.method) {
            "getFileList" -> Thread { DataRequest.putFiles(apiOffer) }.start()
            else          -> {
                send(
                    MessageContent(
                        type,
                        content.msgId,
                        SendData(404, "远程方法 ${apiOffer.method} 不存在")
                    ), listOf(receiver)
                )
                return
            }
        }

        send(MessageContent(type, content.msgId, SendData()), listOf(receiver))
    }

    /**
     * 发送消息
     */
    private fun send(
        content: MessageContent,
        names: List<String> = listOf(),
        roomIds: List<String> = listOf(),
        tags: List<String> = listOf(),
    ) {
        client.send(
            Client.SendMessage(
                roomIds, names, tags, Gson().toJson(content)
            )
        )
    }
}