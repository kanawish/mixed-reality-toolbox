package com.kanawish.dd.robotcontroller

import android.Manifest
import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import com.kanawish.camera.CameraHelper
import com.kanawish.permission.PermissionManager
import com.kanawish.robot.Command
import com.kanawish.robot.Telemetry
import com.kanawish.socket.*
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import kotlinx.android.synthetic.main.test_ui.*
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class LoopbackTestActivity : Activity() {
    companion object {
        const val LOCALHOST = "127.0.0.1"
    }

    @Inject lateinit var cameraHelper: CameraHelper
    @Inject lateinit var permissionManager: PermissionManager

    @Inject lateinit var server: NetworkServer
    @Inject lateinit var client: NetworkClient

    private val disposables = CompositeDisposable()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.test_ui)

        val hasPermissions = permissionManager.hasPermissions(Manifest.permission.CAMERA)

        if (hasPermissions) {
            initCamera()
        } else {
            permissionManager.requestPermissions(Manifest.permission.CAMERA)
        }
        logIpAddresses()
    }

    private fun initCamera() {
        // Diagnostics
        cameraHelper.dumpFormatInfo()

        // TODO: Convert to a reactive stream setup.
        // Pictures taken will be handled by onPictureTaken

        cameraHelper.openCamera(::onPictureTaken)
    }

    override fun onResume() {
        super.onResume()

        disposables += server
                .receiveCommand(InetSocketAddress(LOCALHOST, PORT_CMD))
                .doOnNext { Timber.d("Received Command: $it") }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ cmd -> line1TextView.text = cmd.toString() })

        disposables += server
                .receiveTelemetry(InetSocketAddress(LOCALHOST, PORT_TM))
                .doOnNext { Timber.d("server processed image?") }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe( {
                    tm ->
                    line2TextView.text = "${tm.distance.toInt()} cm"
                    imageView.setImageBitmap(BitmapFactory.decodeByteArray(tm.image, 0, tm.image.size))
                } )

        // Send mock command
        disposables += Observable.interval(2000, TimeUnit.MILLISECONDS)
//                .observeOn(AndroidSchedulers.mainThread())
                .doOnNext { Timber.d("sending command $it") }
                .subscribe { client.sendCommand(LOCALHOST, Command(it, (it * 10 % 256).toInt(), (it * -10 % 256).toInt())) }

        // Send mock telemetry
        disposables += Observable.interval(2000, TimeUnit.MILLISECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    Timber.d("Calling cameraHelper.takePicture() (#$it)")
                    cameraHelper.takePicture()
                }

    }

    override fun onPause() {
        super.onPause()

        disposables.clear()
    }

    /**
     * For every picture taken, send mock telemetry to loopback.
     */
    private fun onPictureTaken(imageBytes: ByteArray) {
        Timber.d("onPictureTaken() called with ${imageBytes.size}")
        BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size).also {
            val outputStream = ByteArrayOutputStream()
            Bitmap.createScaledBitmap(it, 160, 120, false)
                    .compress(Bitmap.CompressFormat.PNG, 100, outputStream)

            // Send mock telemetry data via network socket.
            client.sendTelemetry(LOCALHOST, Telemetry(0.1, outputStream.toByteArray()))
            outputStream.close()

//            val inputStream = ByteArrayInputStream(outputStream.toByteArray())
//            inputStream.close()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        permissionManager.handleRequestPermissionResult(requestCode, permissions, grantResults, this::initCamera)
    }


}
