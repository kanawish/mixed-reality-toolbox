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

    @Inject
    lateinit var videoHelper: VideoHelper
    @Inject
    lateinit var client: NetworkClient
    @Inject
    lateinit var permissionManager: PermissionManager

    private val disposables = CompositeDisposable()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Useful for diagnostics on older versions of Android.
        logIpAddresses()

        if (permissionManager.hasPermissions(Manifest.permission.CAMERA)) {
            dumpFormatInfo()
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                permissionManager.requestPermissions(Manifest.permission.CAMERA)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        videoHelper.startVideoCapture(::onPictureTaken.toImageAvailableListener())
    }

    override fun onPause() {
        super.onPause()
        videoHelper.closeCamera()
        disposables.clear()
    }

    /**
     * For every picture taken, send it to server.
     */
    private fun onPictureTaken(imageBytes: ByteArray) {
        Timber.d("onPictureTaken() called with ${imageBytes.size}")
        BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size).also {
            val outputStream = ByteArrayOutputStream()
            Bitmap
                    .createScaledBitmap(it, 160, 120, false)
                    .compress(Bitmap.CompressFormat.PNG, 100, outputStream)

            // Send image data via network socket.
            client.sendImageData(SERVER_IP, outputStream.toByteArray())
            outputStream.close()
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
                this::dumpFormatInfo
        )
    }

}
