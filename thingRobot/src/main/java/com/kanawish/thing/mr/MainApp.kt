package com.kanawish.thing.mr

import android.app.Application
import com.kanawish.di.ActivityInjectionLifecycle
import com.kanawish.thing.mr.di.openActivityScope
import com.kanawish.thing.mr.di.openApplicationScope
import timber.log.Timber
import toothpick.Toothpick

/**
 */
class MainApp : Application() {

//    @Inject lateinit var grovePiManager:GrovePiManager

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        } // NOTE: Only logging when running the DEBUG flavor

        Timber.i("%s %d %s", BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE, BuildConfig.APPLICATION_ID)

        // DI Root Scope init
        Toothpick.inject(this, openApplicationScope(this))
        registerActivityLifecycleCallbacks(ActivityInjectionLifecycle(::openActivityScope))
    }

}