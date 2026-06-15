package com.isygold.procinsight

import android.app.Application
import com.isygold.procinsight.monitor.SystemMonitor

class ProcInsightApp : Application() {

    lateinit var systemMonitor: SystemMonitor
        private set

    override fun onCreate() {
        super.onCreate()
        systemMonitor = SystemMonitor(this)
    }
}
