package com.kanawish.ar.robotremote.di

import android.app.Application
import android.content.Context
import android.hardware.camera2.CameraManager
import com.kanawish.di.ActivityInjectionLifecycle
import com.kanawish.joystick.DefaultDeviceAddress
import toothpick.config.Module

class AppModule(val appContext:Application) : Module() {

    init {
        bind(Application.ActivityLifecycleCallbacks::class.java).to(ActivityInjectionLifecycle::class.java)
//        bind(ScriptManager::class.java).to(FileSystemManager::class.java)
        // NOTE: This hardcoded pairing bit should only be needed on Android Things device, since you can't access bluetooth manager, etc.
        bind(String::class.java).withName(DefaultDeviceAddress::class.java)
            .toInstance("E4:17:D8:FB:09:69")

        bind(CameraManager::class.java)
            .toInstance(appContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager)

    }
}