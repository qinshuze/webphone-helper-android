package com.ccps.remotedesktop.exception

import android.content.Context
import android.os.Looper
import android.widget.Toast
import com.orhanobut.logger.Logger
import kotlin.system.exitProcess

class AppExceptionHandler(private val context: Context): Thread.UncaughtExceptionHandler {
    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        Looper.prepare()

        if (throwable is ApiException) {
            Toast.makeText(context, "${throwable.response?.code} 网络请求错误，请稍后再试", Toast.LENGTH_SHORT).show()
            Logger.e("api请求错误: ${throwable.message}\n" +
                    "请求地址：${throwable.request.url}\n" +
                    "请求头：${throwable.request.headers}\n" +
                    "请求参数：${throwable.request.body}\n" +
                    "响应：${throwable.response?.body?.string()}")
            return
        }

        if (throwable is MsgClientException) {
            Toast.makeText(context, "${throwable.code} 网络消息错误，请稍后再试", Toast.LENGTH_SHORT).show()
            Logger.e("消息客户端错误: ${throwable.message}\n" +
                    "请求地址：${throwable.request.url}\n" +
                    "请求头：${throwable.request.headers}\n" +
                    "请求参数：${throwable.request.body}\n" +
                    "响应：${throwable.response?.body?.string()}" +
                    "错误码：${throwable.code}"
            )
            return
        }

        Toast.makeText(context, "App异常：${throwable.message}", Toast.LENGTH_SHORT).show()
        Logger.e("App异常: ${throwable.message}")

        Looper.loop()
        // Kill the current process
//        android.os.Process.killProcess(android.os.Process.myPid())
//        exitProcess(0)
    }
}