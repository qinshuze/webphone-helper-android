package com.ccps.remotedesktop

import android.content.Intent
import android.os.Bundle
import android.os.StrictMode
import android.os.StrictMode.ThreadPolicy
import androidx.appcompat.app.AppCompatActivity
import com.ccps.remotedesktop.activity.Home
import com.ccps.remotedesktop.exception.AppExceptionHandler
import com.orhanobut.logger.AndroidLogAdapter
import com.orhanobut.logger.FormatStrategy
import com.orhanobut.logger.Logger
import com.orhanobut.logger.PrettyFormatStrategy


class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        StrictMode.setThreadPolicy(
            ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork() // or .detectAll() for all detectable problems
                .penaltyLog()
                .build()
        )

        super.onCreate(savedInstanceState)

        val formatStrategy = PrettyFormatStrategy.newBuilder()
            .methodCount(5)
            .methodOffset(0).build()
        Logger.addLogAdapter(AndroidLogAdapter(formatStrategy))
        Thread.setDefaultUncaughtExceptionHandler(AppExceptionHandler(this))

        val intent = Intent()
        intent.setClass(this, Home::class.java)
        startActivity(intent)
        finish()
    }
}