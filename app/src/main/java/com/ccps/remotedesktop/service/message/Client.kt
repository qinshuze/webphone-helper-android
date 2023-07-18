package com.ccps.remotedesktop.service.message

import android.os.Handler
import android.os.Looper
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.orhanobut.logger.Logger
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

class Client(url: String) {

    /** 数据类 **/
    data class ReceiveMessage(val type: String, val content: String, val sender: String)
    data class SendMessage(
        val roomIds: List<String> = arrayListOf(),
        val names: List<String> = arrayListOf(),
        val tags: List<String> = arrayListOf(),
        val content: String
    )

    /** 基础属性 **/
    private var disconnected = false
    private val handler = Handler(Looper.myLooper()!!)
    private var authSuccess = true
    private var isReconnecting = false
    private val request = Request.Builder().url(url).build()
    private var socket: WebSocket = connect()

    /** 事件侦听器 **/
    private val closedEventListeners = ArrayList<ClientEvent.Closed>()
    private val closingEventListeners = ArrayList<ClientEvent.Closing>()
    private val messageEventListeners = ArrayList<ClientEvent.Message>()
    private val openEventListeners = ArrayList<ClientEvent.Open>()
    private val failureEventListeners = ArrayList<ClientEvent.Failure>()

    /**
     * 连接关闭后的处理
     * 当连接关闭后时，自动调用该方法进行处理
     */
    private fun closedHandle(webSocket: WebSocket, code: Int, reason: String) {
        try {
            if (!isReconnecting) {
                closedEventListeners.forEach { it.invoke(code, reason) }
            }
        } catch (e: Exception) {
            Logger.e("消息客户端在关闭时发生错误：${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * 连接关闭时的处理
     * 当连接关闭时，自动调用该方法进行处理
     */
    private fun closingHandle(webSocket: WebSocket, code: Int, reason: String) {
        try {
            when (code) {
                3001 -> authSuccess = false
                3000 -> {}
                else -> {
                    isReconnecting = true
                    socket = connect()
                }
            }
            closingEventListeners.forEach { it.invoke(code, reason) }
        } catch (e: Exception) {
            Logger.e("消息客户端在关闭时发生错误：${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * 收到消息时的处理
     * 当接收到消息时，自动调用该方法进行处理
     */
    private fun messageHandle(webSocket: WebSocket, text: String) {
        try {
            val receiveMessage = Gson().fromJson(text, ReceiveMessage::class.java)
            heartbeatHandle(receiveMessage)
            messageEventListeners.forEach { it.invoke(receiveMessage) }
        } catch (e: Exception) {
            Logger.e("消息客户端在处理消息时发生错误：${e.message}\n消息内容：${text}")
            e.printStackTrace()
        }
    }

    /**
     * 连接成功时的处理
     * 当连接成功后，自动调用该方法进行处理
     */
    private fun openHandle(webSocket: WebSocket, response: Response) {
        try {
            handler.postDelayed({
                if (authSuccess) {
                    openEventListeners.forEach { it.invoke() }
                    isReconnecting = false
                }
            }, 300)
        } catch (e: Exception) {
            Logger.e("消息客户端在打开时发生错误：${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * 当连接发生错误时的处理
     * 当连接发生错误时，自动调用该方法进行处理
     */
    private fun failureHandle(webSocket: WebSocket, t: Throwable, response: Response?) {
        Logger.e("消息客户端发生错误：${t.message}")
        failureEventListeners.forEach { it.invoke() }
    }

    /**
     * 心跳包消息处理
     * 对接收到的心跳包消息处理并回应
     */
    private fun heartbeatHandle(message: ReceiveMessage) {
        val type = "base@heartbeat"
        val jsonElement = Gson().fromJson(message.content, JsonElement::class.java)
        if (jsonElement == null || !jsonElement.isJsonObject) return

        val msgType = jsonElement.asJsonObject.get("msgType")
        if (msgType == null || msgType.asString != type) return

        val msgId = jsonElement.asJsonObject.get("msgId") ?: ""
        send(SendMessage(names = listOf(message.sender), content = "{\"msgType\": \"${type}Answer\", \"msgId\": ${msgId}}"))
    }

    /**
     * 开始连接
     * 调用该方法后开始建立socket连接
     */
    private fun connect(): WebSocket {
        return OkHttpClient.Builder().build().newWebSocket(request, object : WebSocketListener() {
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                super.onClosed(webSocket, code, reason)
                closedHandle(webSocket, code, reason)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                super.onClosing(webSocket, code, reason)
                closingHandle(webSocket, code, reason)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                super.onMessage(webSocket, text)
                messageHandle(webSocket, text)
            }

            override fun onOpen(webSocket: WebSocket, response: Response) {
                super.onOpen(webSocket, response)
                openHandle(webSocket, response)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                super.onFailure(webSocket, t, response)
                failureHandle(webSocket, t, response)
            }
        })
    }

    /**
     * 注册连接关闭后的回调函数
     */
    fun onClosed(callback: ClientEvent.Closed) {
        closedEventListeners.add(callback)
    }

    /**
     * 注册连接关闭时的回调函数
     */
    private fun onClosing(callback: ClientEvent.Closing) {
        closingEventListeners.add(callback)
    }

    /**
     * 注册连接打开后的回调函数
     */
    fun onOpen(callback: ClientEvent.Open) {
        openEventListeners.add(callback)
    }

    /**
     * 注册收到消息时的回调函数
     */
    fun onMessage(callback: ClientEvent.Message) {
        messageEventListeners.add(callback)
    }

    /**
     * 移除消息回调
     */
    fun removeOnMessage(callback: ClientEvent.Message) {
        messageEventListeners.remove(callback)
    }

    /**
     * 关闭连接
     */
    fun close(reason: String = "") {
        disconnected = false
        socket.close(3000, reason)
    }

    /**
     * 发送消息
     */
    fun send(data: SendMessage) {
        socket.send(Gson().toJson(data))
    }
}