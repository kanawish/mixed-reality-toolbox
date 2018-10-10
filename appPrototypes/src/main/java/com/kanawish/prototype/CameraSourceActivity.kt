package com.kanawish.prototype

import android.Manifest
import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import com.kanawish.permission.PermissionManager
import com.kanawish.socket.NetworkClient
import com.kanawish.socket.logIpAddresses
import com.kanawish.utils.camera.VideoHelper
import com.kanawish.utils.camera.dumpFormatInfo
import com.kanawish.utils.camera.toImageAvailableListener
import io.reactivex.disposables.CompositeDisposable
import timber.log.Timber
import java.io.ByteArrayOutputStream
import javax.inject.Inject

class CameraSourceActivity : Activity() {
    @Inject lateinit var cameraHelper: VideoHelper
    @Inject lateinit var client: NetworkClient
    @Inject lateinit var permissionManager: PermissionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        logIpAddresses()

        val hasPermissions = permissionManager.hasPermissions(Manifest.permission.CAMERA)

        if (hasPermissions) {
            initCamera()
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                permissionManager.requestPermissions(Manifest.permission.CAMERA)
            }
        }

    }

    private fun initCamera() {
        // Diagnostics
        dumpFormatInfo()

        // TODO: Convert to a reactive stream setup.
        // Pictures taken will be handled by onPictureTaken

    }

    private val disposables = CompositeDisposable()

    override fun onResume() {
        super.onResume()

        cameraHelper.startVideoCapture(::onPictureTaken.toImageAvailableListener())

        // 1-5 at 1 second intervals.
/*
        disposables += Observable.interval(2000, TimeUnit.MILLISECONDS)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe {
                Timber.d("Calling cameraHelper.takePicture() (#$it)")
                cameraHelper.takePicture()
            }
*/
    }

    override fun onPause() {
        super.onPause()
        cameraHelper.closeCamera()
        disposables.clear()
    }

    /**
     * For every picture taken, send it to server.
     */
    private fun onPictureTaken(imageBytes: ByteArray) {
        Timber.d("onPictureTaken() called with ${imageBytes.size}")
        BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size).also {
            val outputStream = ByteArrayOutputStream()
            Bitmap.createScaledBitmap(it, 160, 120, false)
                .compress(Bitmap.CompressFormat.PNG, 100, outputStream)
//            val inputStream = ByteArrayInputStream(outputStream.toByteArray())
            // Send image data via network socket.
            client.sendImageData(SERVER_IP, outputStream.toByteArray())
            outputStream.close()

//            inputStream.close()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        permissionManager.handleRequestPermissionResult(
                requestCode,
                permissions,
                grantResults,
                this::initCamera
        )
    }

    // Expectation is we use Pixel as receiver, Nexus as broadcaster.
//    fun send(command: Command) = client.sendCommand(LOCAL_ADDRESS, command)

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        val x = event.getAxisValue(MotionEvent.AXIS_X)
        val y = event.getAxisValue(MotionEvent.AXIS_Y)
        val z = event.getAxisValue(MotionEvent.AXIS_Z) // (X right analog)
        val rx = event.getAxisValue(MotionEvent.AXIS_RX)
        val ry = event.getAxisValue(MotionEvent.AXIS_RY)
        val rz = event.getAxisValue(MotionEvent.AXIS_RZ) // (Y right analog)
        Timber.d("MotionEvent: ($x $y $z, $rx $ry $rz)")
        return true
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        Timber.d("KeyEvent: $event")
        return true
    }

}
