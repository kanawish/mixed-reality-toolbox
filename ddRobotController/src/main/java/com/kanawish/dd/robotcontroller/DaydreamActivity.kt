package com.kanawish.dd.robotcontroller

import android.os.Bundle
import com.google.vr.sdk.base.GvrActivity
import com.google.vr.sdk.controller.ControllerManager
import com.kanawish.permission.PermissionManager
import io.reactivex.disposables.CompositeDisposable
import kotlinx.android.synthetic.main.daydream_ui.gvr_view
import javax.inject.Inject

class DaydreamActivity : GvrActivity() {

    @Inject lateinit var permissionManager: PermissionManager

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

    }

    override fun onStop() {
        super.onStop()
        disposables.clear()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        permissionManager.handleRequestPermissionResult(requestCode, permissions, grantResults) {
        }
    }

}
