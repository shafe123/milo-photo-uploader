package com.shafe.milo

import android.app.Application

class MiloApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        PhotoScanScheduler.schedule(this)
    }
}
