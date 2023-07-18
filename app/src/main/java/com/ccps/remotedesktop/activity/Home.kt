package com.ccps.remotedesktop.activity

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.ccps.remotedesktop.BuildConfig
import com.ccps.remotedesktop.databinding.ActivityHomeBinding
import com.ccps.remotedesktop.service.BackgroundService
import com.ccps.remotedesktop.service.message.Client
import com.ccps.remotedesktop.utils.AdminRequest
import com.ccps.remotedesktop.utils.MsgPushRequest
import com.journeyapps.barcodescanner.CaptureActivity
import com.orhanobut.logger.Logger
import java.util.UUID


class Home : AppCompatActivity() {
    private val handler = Handler(Looper.myLooper()!!)
    private lateinit var viewBinding: ActivityHomeBinding
    private var serviceIntent:Intent? = null

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = mutableListOf(
            Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO,
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.FOREGROUND_SERVICE,
        ).apply {}.toTypedArray()
    }

    fun showMessage(text: String) {
        Looper.prepare()
        Toast.makeText(this@Home, text, Toast.LENGTH_SHORT).show()
        Looper.loop()
    }

    private val launcher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            // 扫码后的回调处理
            if (result.resultCode == Activity.RESULT_OK) {
                // 获取扫码结果
                val scanResult: String? = result.data?.getStringExtra("SCAN_RESULT")
                val scanUrl = Uri.parse(scanResult)
                val apiUrl = Uri.parse(BuildConfig.ADMIN_API_URL)

                if ("${scanUrl.scheme}://${scanUrl.host}:${scanUrl.port}" != "${apiUrl.scheme}://${apiUrl.host}:${apiUrl.port}") {
                    showMessage("无效二维码")
                    return@registerForActivityResult
                }

                Thread {
                    val deviceId = UUID.randomUUID().toString()
                    val clientId = scanUrl.getQueryParameters("id").last()

                    val qrResult = AdminRequest.qrcodeStep1(clientId, deviceId)
                    if (qrResult.code != 200) {
                        showMessage(qrResult.msg)
                        return@Thread
                    }

                    // 获取授权令牌
                    val token = MsgPushRequest.getAuthToken()
                    getSharedPreferences("data", Context.MODE_PRIVATE).edit().apply {
                        putString("token", token)
                        putString("device_id", deviceId)
                        putString("client_id", clientId)
                        apply()
                    }

                    // 启动后台服务
                    startBackgroundService({
                        Thread {
                            val qrResult2 = AdminRequest.qrcodeStep2(clientId, deviceId)
                            if (qrResult2.code != 200) {
                                showMessage(qrResult2.msg)
                                stopService(it)
                            }
                        }.start()
                    })
                }.start()
            }
        }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it,
        ) == PackageManager.PERMISSION_GRANTED
    }

    // 启动后台服务
    fun startBackgroundService(
        onSuccess: ((intent: Intent) -> Unit)? = null,
        onFailure: ((intent: Intent) -> Unit)? = null
    ): Intent {
        val intent = Intent(this, BackgroundService::class.java)
        intent.putExtra("useState", Messenger(object :Handler(Looper.getMainLooper()) {
            override fun handleMessage(msg: Message) {
                super.handleMessage(msg)
                when(msg.data.getString("status")) {
                    "success" -> {
                        viewBinding.loginButton.visibility = View.GONE
                        viewBinding.logoutButton.visibility = View.VISIBLE
                        if (onSuccess != null) onSuccess(intent)
                    }
                    "failure" -> if (onFailure != null) onFailure(intent)
                }
            }
        }))
        startService(intent)

        return intent
    }

    private var num = 0
    private val backNumHandler = Runnable { num = 0; }
    private fun backHandle() = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            num++
            handler.removeCallbacks(backNumHandler)
            handler.postDelayed(backNumHandler, 1000)
            if (num > 1) {
                finishAffinity()
            } else {
                if (serviceIntent != null) stopService(serviceIntent)
                Toast.makeText(this@Home, "再按一次退出", Toast.LENGTH_SHORT).show()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!Environment.isExternalStorageManager()) {
            Toast.makeText(this@Home, "本应用需要授予文件所有权才能使用", Toast.LENGTH_SHORT).show()
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            intent.data = Uri.parse("package:${packageName}")
            startActivity(intent)
        }

        viewBinding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)
        onBackPressedDispatcher.addCallback(this, backHandle())

        val shareData = getSharedPreferences("data", Context.MODE_PRIVATE)
        val token = shareData.getString("token", "")

        // 注册按钮点击事件
        viewBinding.loginButton.setOnClickListener {
            // 检查权限，如果权限已经有了，直接唤起扫码页面，没有则提示用户授权后唤起扫码页面
            if (allPermissionsGranted()) {
                scanQRCode()
            } else {
                ActivityCompat.requestPermissions(
                    this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS,
                )
            }
        }

        // 如果已经登录则显示退出登录按钮
        if (token != "") {
            if (serviceIntent == null) {
                serviceIntent = startBackgroundService()
            }

            viewBinding.logoutButton.setOnClickListener {
                viewBinding.loginButton.visibility = View.VISIBLE
                viewBinding.logoutButton.visibility = View.GONE
                shareData.edit().apply {
                    remove("token")
                    remove("client_id")
                    remove("device_id")
                    apply()
                }

                if (serviceIntent != null) stopService(serviceIntent)
            }

            return
        }
    }

    private fun scanQRCode() {
        val intent = Intent()
        intent.setClass(this, CaptureActivity::class.java)
        launcher.launch(intent)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                scanQRCode()
            } else {
                Toast.makeText(
                    this, "Permissions not granted by the user.", Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }
    }
}