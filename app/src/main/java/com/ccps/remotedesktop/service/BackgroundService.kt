package com.ccps.remotedesktop.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.ccps.remotedesktop.BuildConfig
import com.ccps.remotedesktop.R
import com.ccps.remotedesktop.service.message.Client
import com.ccps.remotedesktop.service.message.HttpRelayMessageHandler
import com.ccps.remotedesktop.service.message.SignalingMessageHandler
import com.google.gson.Gson

class BackgroundService : Service() {
    private lateinit var msgClient: Client
    private val handler = Handler(Looper.getMainLooper())
    private var useState: Messenger? = null
    private var failureMessenger: Messenger? = null
    private lateinit var clientId: String
    private lateinit var deviceId: String
    private lateinit var token: String

    companion object {
        private const val CHANNEL_ID = "MessagePushChannel"
        private const val FOREGROUND_SERVICE_ID = 1
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        useState = intent?.getParcelableExtra("useState", Messenger::class.java)
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    fun showMessage(text: String) {
        Looper.prepare()
        Toast.makeText(this@BackgroundService, text, Toast.LENGTH_SHORT).show()
        Looper.loop()
    }

    override fun onCreate() {
        super.onCreate()

        // 创建前台服务的通知通道
        createNotificationChannel()

        // 构建前台服务的通知
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("远程控制后台服务")
            .setContentText("远程控制正在后台运行")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()

        // 启动服务作为前台服务，并传入通知
        startForeground(FOREGROUND_SERVICE_ID, notification)

        // 获取本地登录信息
        val shareData = getSharedPreferences("data", Context.MODE_PRIVATE)
        token = shareData.getString("token", "").toString()
        deviceId = shareData.getString("device_id", "").toString()
        clientId = shareData.getString("client_id", "").toString()

        // 创建消息客户端连接
        val socketUrl = BuildConfig.MSG_PUSH_URL + "?token=${token}&name=${deviceId}&room_ids=${deviceId}&realm=${BuildConfig.MSG_PUSH_ACCESS_KEY}"
        msgClient = Client(socketUrl)

        msgClient.onOpen {
            // 注册Http转发消息处理器
            HttpRelayMessageHandler(msgClient)

            // 注册webrtc信令处理器
            SignalingMessageHandler(msgClient, this)

            useState?.let {
                val data = Bundle()
                data.putString("status", "success")
                val message = Message.obtain()
                message.data = data
                it.send(message)
            }
        }

        msgClient.onClosed { code, _ ->
            showMessage("\"远程控制\" 后台服务启动失败")

            if (code == 3001) {
                shareData.edit().apply {
                    remove("token")
                    remove("client_id")
                    remove("device_id")
                    apply()
                }
            }

            failureMessenger?.let {
                val data = Bundle()
                data.putString("status", "failure")
                val message = Message.obtain()
                message.data = data
                it.send(message)
            }
            onDestroy()
        }
    }

    private fun createNotificationChannel() {
        val name = "MessagePushChannel"
        val descriptionText = "远程控制正在后台运行"
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
            description = descriptionText
        }
        val notificationManager: NotificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        super.onDestroy()

        // 当服务销毁时关闭WebSocket连接
        data class LogoutMessage(val msgType: String = "logout")
        msgClient.send(
            Client.SendMessage(
                names = arrayListOf(clientId),
                content = Gson().toJson(LogoutMessage())
            )
        )
        msgClient.close()
    }
}