package com.kanawish.thing.robot.di

import android.app.Application
import android.content.Context
import android.hardware.camera2.CameraManager
import toothpick.config.Module

class AppModule(appContext: Application) : Module() {
    init {
        bind(CameraManager::class.java)
                .toInstance(appContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager)
    }
}