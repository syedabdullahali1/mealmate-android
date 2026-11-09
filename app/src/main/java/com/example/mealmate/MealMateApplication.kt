package com.example.mealmate

import android.app.Application
import com.chaquo.python.AndroidPlatform
import com.chaquo.python.Python

class BekalBuApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (!Python.isStarted()) Python.start(AndroidPlatform(this))
    }
}
