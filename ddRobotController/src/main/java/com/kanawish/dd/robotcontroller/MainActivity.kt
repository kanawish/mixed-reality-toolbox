package com.kanawish.dd.robotcontroller

import android.app.Activity
import android.os.Bundle
import com.kanawish.nearby.NEARBY_PERMISSIONS
import com.kanawish.nearby.NearbyConnectionManager
import com.kanawish.permission.PermissionManager
import javax.inject.Inject

class MainActivity : Activity() {
    @Inject lateinit var permissionManager: PermissionManager
    @Inject lateinit var nearbyManager: NearbyConnectionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }

    override fun onStart() {
        super.onStart()

        val hasPermissions = permissionManager.hasPermissions(*NEARBY_PERMISSIONS)

        if (hasPermissions) {
            nearbyManager.autoConnect()
        } else {
            permissionManager.requestPermissions(*NEARBY_PERMISSIONS)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        permissionManager.handleRequestPermissionResult(requestCode, permissions, grantResults) {
            nearbyManager.autoConnect()
        }
    }

}
