package com.kanawish.thing.mr.di

import android.app.Activity
import com.kanawish.thing.mr.telemetry.CameraHelper
import toothpick.config.Module

class ActivityModule(activity: Activity) : Module() {
    init {
        bind(CameraHelper::class.java)
    }
}