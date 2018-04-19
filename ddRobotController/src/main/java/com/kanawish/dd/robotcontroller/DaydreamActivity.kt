package com.kanawish.dd.robotcontroller

import android.graphics.BitmapFactory
import android.os.Bundle
import com.google.vr.sdk.base.GvrActivity
import com.google.vr.sdk.controller.ControllerManager
import com.kanawish.nearby.NEARBY_PERMISSIONS
import com.kanawish.nearby.NearbyConnectionManager
import com.kanawish.nearby.NearbyConnectionManager.ConnectionEvent.ConnectionResult
import com.kanawish.nearby.NearbyConnectionManager.ConnectionEvent.Disconnect
import com.kanawish.permission.PermissionManager
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import timber.log.Timber
import javax.inject.Inject
import kotlinx.android.synthetic.main.daydream_ui.*

class DaydreamActivity : GvrActivity() {

    @Inject lateinit var permissionManager: PermissionManager
    @Inject lateinit var nearbyManager: NearbyConnectionManager

    private lateinit var controllerManager: ControllerManager

    private val disposables = CompositeDisposable()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.daydream_ui)

        gvrView = gvr_view
        gvrView.setEGLConfigChooser(8, 8, 8, 8, 16, 8)
    }

    override fun onStart() {
        super.onStart()

        val hasPermissions = permissionManager.hasPermissions(*NEARBY_PERMISSIONS)

        if (hasPermissions) {
            nearbyManager.stopAllEndpoints()
            nearbyManager.autoDiscover()
        } else {
            permissionManager.requestPermissions(*NEARBY_PERMISSIONS)
        }

        disposables += nearbyManager.connectionEvents()
                .subscribe {
                    when (it) {
                        is ConnectionResult -> Timber.d("connectionResult = ${it.success} / ${it.connectionCount}")
                        is Disconnect -> {
                            Timber.d("disconnect, ${it.connectionCount} remaining ")
                            nearbyManager.stopAllEndpoints()
                            nearbyManager.autoDiscover()
                        }
                    }
                }

        disposables += nearbyManager.receivedPayloads()
                .subscribe{ Timber.d("Payload received: $it")}

        disposables += nearbyManager.outputStreams().subscribe {
            val bitmap = BitmapFactory.decodeByteArray(it.toByteArray(), 0, it.size())
            // TODO: Add it to the simulation somewhere.
//            image.setImageBitmap(bitmap)
        }
    }

    override fun onStop() {
        super.onStop()
        disposables.clear()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        permissionManager.handleRequestPermissionResult(requestCode, permissions, grantResults) {
            nearbyManager.stopAllEndpoints()
            nearbyManager.autoDiscover()
        }
    }

}
