package com.kanawish.ar.robotremote.util

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import timber.log.Timber

/**
 * Returns false and displays an error message if Sceneform can not run, true if Sceneform can run
 * on this device.
 *
 * Sceneform requires Android N on the device as well as OpenGL 3.0 capabilities.
 *
 * Finishes the activity if Sceneform can not run
 */
private const val MIN_OPENGL_VERSION = 3.0

fun Activity.checkIsSupportedDeviceOrFinish(): Boolean {
    val openGlVersionString = (this.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
            .deviceConfigurationInfo
            .glEsVersion
    if (java.lang.Double.parseDouble(openGlVersionString) < MIN_OPENGL_VERSION) {
        Timber.e("Sceneform requires OpenGL ES 3.0 later")
        Toast.makeText(this, "Sceneform requires OpenGL ES 3.0 or later", Toast.LENGTH_LONG)
                .show()
        finish()
        return false
    }
    return true
}
