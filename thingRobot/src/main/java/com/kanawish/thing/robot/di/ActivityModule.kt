package com.kanawish.thing.robot.di

import android.app.Activity
import com.kanawish.camera.CameraHelper
import toothpick.config.Module

class ActivityModule(activity: Activity) : Module() {
    init {
        bind(CameraHelper::class.java)
    }
}