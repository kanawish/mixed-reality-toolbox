package com.kanawish.ar.robotremote

import android.app.Activity
import android.app.Application
import com.kanawish.ar.robotremote.di.ActivityModule
import com.kanawish.ar.robotremote.di.AppModule
import com.kanawish.di.ActivityInjectionLifecycle
import com.kanawish.di.ActivitySingleton
import timber.log.Timber
import toothpick.Scope
import toothpick.Toothpick
import toothpick.smoothie.module.SmoothieActivityModule
import toothpick.smoothie.module.SmoothieApplicationModule

class ArDemoApp : Application() {

    override fun onCreate() {
        super.onCreate()

        if (com.kanawish.ar.robotremote.BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        Timber.i("%s %d %s", BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE, BuildConfig.APPLICATION_ID)

        // DI Root Scope init
        Toothpick.inject(this, openApplicationScope(this))
        registerActivityLifecycleCallbacks(ActivityInjectionLifecycle(::openActivityScope))
    }

    private fun openApplicationScope(app: Application): Scope = Toothpick.openScope(app).apply {
        installModules(
                SmoothieApplicationModule(app),
                AppModule(app))
    }

    private fun openActivityScope(activity: Activity): Scope = Toothpick.openScopes(activity.application, activity).apply {
        bindScopeAnnotation(ActivitySingleton::class.java)

        installModules(
                SmoothieActivityModule(activity),
                ActivityModule)
    }

}


