package com.kanawish.prototype

import android.app.Activity
import android.app.Application
import com.kanawish.di.ActivityInjectionLifecycle
import com.kanawish.di.ActivitySingleton
import com.kanawish.di.HelperModule
import timber.log.Timber
import toothpick.Scope
import toothpick.Toothpick
import toothpick.smoothie.module.SmoothieActivityModule
import toothpick.smoothie.module.SmoothieApplicationModule

class ProtoApp : Application() {

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        Timber.i("%s %d %s", BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE, BuildConfig.APPLICATION_ID)

        // DI Root Scope init
        Toothpick.inject(this, openApplicationScope(this))
        registerActivityLifecycleCallbacks(ActivityInjectionLifecycle(::openActivityScope))
    }

    private fun openApplicationScope(app: Application): Scope = Toothpick.openScope(app).apply {
        installModules(SmoothieApplicationModule(app), HelperModule(app))
    }

    private fun openActivityScope(activity: Activity): Scope = Toothpick.openScopes(activity.application, activity).apply {
        bindScopeAnnotation(ActivitySingleton::class.java)

        installModules(SmoothieActivityModule(activity))
    }

}
