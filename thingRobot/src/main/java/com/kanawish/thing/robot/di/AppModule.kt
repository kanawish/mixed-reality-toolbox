package com.kanawish.thing.robot.di

import android.app.Application
import android.content.Context
import android.hardware.camera2.CameraManager
import com.kanawish.nearby.DEFAULT_ENDPOINT
import com.kanawish.nearby.NearbyConnectionManager
import toothpick.config.Module

class AppModule(appContext: Application) : Module() {
    init {
        bind(NearbyConnectionManager::class.java)
                .toInstance(NearbyConnectionManager(appContext, DEFAULT_ENDPOINT))
        bind(CameraManager::class.java)
                .toInstance(appContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager)
    }
}