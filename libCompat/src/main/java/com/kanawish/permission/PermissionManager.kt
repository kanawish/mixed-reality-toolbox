package com.kanawish.permission

import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.support.annotation.RequiresApi
import android.support.v4.content.ContextCompat
import com.kanawish.di.ActivitySingleton
import javax.inject.Inject


const val REQUEST_PERMISSIONS = 1

/**
 * This class will become useful when adding any rationale / overall logic to permission requests.
 *
 */
@ActivitySingleton
class PermissionManager @Inject constructor(
        val activity: Activity
) {

    fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED
    }

    fun hasPermissions(vararg permissions: String): Boolean {
        return permissions.fold(true) { acc, permission ->
            acc && hasPermission(permission)
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    fun requestPermissions(vararg permissions: String) {
        activity.requestPermissions(permissions, REQUEST_PERMISSIONS)
    }

    fun handleRequestPermissionResult(
            requestCode: Int,
            permissions: Array<out String>,
            grantResults: IntArray,
            handler: () -> Unit
    ) {
        if (requestCode == REQUEST_PERMISSIONS) {
            if (grantResults.isNotEmpty()) {
                val granted = grantResults.fold(true) { acc, i ->
                    acc && (i == PackageManager.PERMISSION_GRANTED)
                }

                // Granted
                if (granted) handler()
            }
        }
    }
}