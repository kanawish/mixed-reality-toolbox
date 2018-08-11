package com.kanawish.ar.robotremote.di

import android.app.Application
import com.kanawish.di.ActivityInjectionLifecycle
import toothpick.config.Module

object AppModule : Module() {

    init {
        bind(Application.ActivityLifecycleCallbacks::class.java).to(ActivityInjectionLifecycle::class.java)
//        bind(ScriptManager::class.java).to(FileSystemManager::class.java)
    }
}