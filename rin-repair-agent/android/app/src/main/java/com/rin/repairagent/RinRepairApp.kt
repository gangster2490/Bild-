package com.rin.repairagent

import android.app.Application

class RinRepairApp : Application() {
    lateinit var repository: com.rin.repairagent.data.RinRepository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = com.rin.repairagent.data.RinRepository(this)
    }
}
