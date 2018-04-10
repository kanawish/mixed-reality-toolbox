package com.kanawish.dd.robotcontroller.di

import android.app.Application
import com.kanawish.nearby.NearbyConnectionManager
import toothpick.config.Module

class AppModule(appContext: Application) : Module() {
    init {
        bind(NearbyConnectionManager::class.java)
                .toInstance(NearbyConnectionManager(appContext,"myEndpoint"))
    }
}