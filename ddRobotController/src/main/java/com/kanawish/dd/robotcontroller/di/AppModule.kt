package com.kanawish.dd.robotcontroller.di

import android.app.Application
import android.content.Context
import android.hardware.camera2.CameraManager
import com.kanawish.joystick.ConnectionManager
import com.kanawish.joystick.DefaultDeviceAddress
import toothpick.config.Module
import javax.inject.Qualifier



/**
 */
class AppModule(appContext: Application) : Module() {
    init {
        // NOTE: This hardcoded pairing bit should only be needed on Android Things device, since you can't access bluetooth manager, etc.
        bind(String::class.java).withName(DefaultDeviceAddress::class.java)
                .toInstance("E4:17:D8:FB:09:69")

        bind(CameraManager::class.java)
                .toInstance(appContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager)
    }
}