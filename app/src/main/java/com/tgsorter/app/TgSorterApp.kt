package com.tgsorter.app

import android.app.Application
import com.tgsorter.app.di.AppContainer

class TgSorterApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
