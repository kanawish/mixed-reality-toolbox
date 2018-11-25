package com.kanawish.thing.robot

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import com.google.android.things.contrib.driver.motorhat.MotorHat
import com.google.android.things.pio.PeripheralManager
import com.jakewharton.rxrelay2.BehaviorRelay
import com.kanawish.robot.Telemetry
import com.kanawish.socket.*
import com.kanawish.utils.camera.CameraHelper
import com.kanawish.utils.camera.VideoHelper
import com.kanawish.utils.camera.dumpFormatInfo
import com.kanawish.utils.camera.toImageAvailableListener
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.rxkotlin.withLatestFrom
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * @startuml
 * object Robot
 * object Camera
 * object MotorDrive {
 * wheels[4]
 * }
 * Robot *.. Camera
 * Robot *.. MotorDrive
 * @enduml
 *
 * @startuml
 * "Robot Client" --> "Controller Server": telemetry
 * @enduml
 *
 * @startuml
 * "Robot Server" <-- "Controller Client": command
 * @enduml
 *
 * @startuml
 * class RobotActivity {
 * <i>// Android Things Drivers
 * motorHat : MotorHat
 * ..
 * }
 * @enduml
 *
 */
class RobotActivity : Activity() {

    @Inject lateinit var cameraHelper: CameraHelper
    @Inject lateinit var videoHelper: VideoHelper

    @Inject lateinit var networkClient: NetworkClient // Output telemetry
    @Inject lateinit var server: NetworkServer // Input commands

    /**
     * Using Relays simplifies conversion of data to streams.
     */
    private val images = BehaviorRelay.create<ByteArray>()

    private var disposables: CompositeDisposable = CompositeDisposable()

    /*
     * ANDROID THINGS SPECIFIC SECTION
     */

    private val manager by lazy {
        PeripheralManager.getInstance()
    }

    /**
     * MotorHat is a contributed driver for LadyAda's Motor Hat
     */
    private val motorHat: MotorHat by lazy {
        try {
            MotorHat(currentDevice().i2cBus())
        } catch (e: IOException) {
            throw RuntimeException("Failed to create MotorHat", e)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Timber.d("${manager.gpioList}")

        Timber.d("${manager.pwmList}")
        Timber.d("${manager.i2cBusList}")
        Timber.d("${manager.spiBusList}")
        Timber.d("${manager.uartDeviceList}")

        // Dump Camera Diagnostics to logcat.
        dumpFormatInfo()
    }

    override fun onResume() {
        Timber.w("onResume()")
        super.onResume()

        // Each frame received from Camera2 API will be processed by ::onPictureTaken
        videoHelper.startVideoCapture(::onPictureTaken.toImageAvailableListener())

        // Whenever a new image frame is published (by ::onPictureTake), we send it over the network.
        disposables += images
                .throttleLast(333,TimeUnit.MILLISECONDS)
                .subscribe {
                    //            Timber.d("Sending image data [${it.size} bytes]")
                    networkClient.sendImageData(HOST_PHONE_ADDRESS, it)
                }

        // Our stream of commands.
        disposables += server.receiveCommand(InetSocketAddress(ROBOT_ADDRESS, PORT_CMD))
                // SwitchMap will drop previous commands in favor of latest one.
                .switchMap { cmd ->
                    // This programs the "timed-release" of the left and right wheel drives.
                    Observable.concat(
                            Observable.just({
                                Timber.d("drive(Command(${cmd.duration}, ${cmd.left}, ${cmd.right}))")
                                motorHat.drive(cmd.left, cmd.right)
                            }),
                            Observable.just({
                                Timber.d("releaseAll(${cmd.duration}, ${cmd.left}, ${cmd.right})")
                                motorHat.releaseAll()
                            }).delay(cmd.duration, TimeUnit.MILLISECONDS)
                    )
                }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { it() }
    }

    override fun onPause() {
        Timber.w("onPause()")
        super.onPause()
        cameraHelper.closeCamera()
        disposables.clear()
    }

    override fun onDestroy() {
        Timber.i("onDestroy()")
        super.onDestroy()

        safeClose("Problem closing camera %s", cameraHelper::closeCamera)
        safeClose("Problem closing motorHat %s", motorHat::close)
    }

    // Send pictures as nearbyManager payloads.
    private fun onPictureTaken(imageBytes: ByteArray) {
        BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size).also {
            val outputStream = ByteArrayOutputStream()

            // Forced downgrading of bitmap to emphasize speed, add quality when everything works.
            Bitmap.createScaledBitmap(it, 160, 120, false)
                    .compress(Bitmap.CompressFormat.PNG, 100, outputStream)

            // Push new image into our Relay
            images.accept(outputStream.toByteArray())
            // Also push it in parallel to the image data socket

            outputStream.close()
        }
    }

}