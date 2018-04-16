package com.kanawish.thing.mr

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import com.google.android.things.contrib.driver.ultrasonicsensor.DistanceListener
import com.google.android.things.contrib.driver.ultrasonicsensor.UltrasonicSensorDriver
import com.google.android.things.pio.PeripheralManager
import com.kanawish.nearby.NearbyConnectionManager
import com.kanawish.nearby.NearbyConnectionManager.ConnectionEvent.ConnectionResult
import com.kanawish.nearby.NearbyConnectionManager.ConnectionEvent.Disconnect
import com.kanawish.thing.mr.telemetry.CameraHelper
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class MainActivity : Activity() {

    @Inject lateinit var nearbyManager: NearbyConnectionManager
    @Inject lateinit var cameraHelper: CameraHelper

    val manager by lazy {
        PeripheralManager.getInstance()
    }

/*
    val motorHat: MotorHat by lazy {
        try {
            MotorHat(currentDevice().i2cBus())
        } catch (e: IOException) {
            throw RuntimeException("Failed to create MotorHat", e)
        }
    }
*/

    val ultrasonicSensor: UltrasonicSensorDriver = try {
        UltrasonicSensorDriver(
                "GPIO2_IO01", "GPIO2_IO02",
                DistanceListener { distanceInCm -> Timber.d("Distance $distanceInCm cm") })
    } catch (e: IOException) {
        throw RuntimeException("Failed to create UltrasonicSensorDriver", e)
    }


    var disposables: CompositeDisposable = CompositeDisposable()

    override fun onCreate(savedInstanceState: Bundle?) {
        Timber.w("onCreate()")
        super.onCreate(savedInstanceState)

        Timber.d("${manager.gpioList}")

        Timber.d("${manager.pwmList}")
        Timber.d("${manager.i2cBusList}")
        Timber.d("${manager.spiBusList}")
        Timber.d("${manager.uartDeviceList}")

        // Diagnostics
        cameraHelper.dumpFormatInfo()

        // TODO: Convert to a reactive stream setup.
        // Pictures taken will be handled by onPictureTaken
        cameraHelper.openCamera(::onPictureTaken)

        // nearbyManager is an app singleton, don't forget. Just clearing to get easy starting state for now.
        nearbyManager.stopAllEndpoints()

    }

    // Send pictures as nearbyManager payloads.
    private fun onPictureTaken(imageBytes: ByteArray) {
        Timber.d("onPictureTaken() called with ${imageBytes.size}")
        BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size).also {
            val outputStream = ByteArrayOutputStream()
            Bitmap.createScaledBitmap(it, 160, 120, false)
                    .compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            val inputStream = ByteArrayInputStream(outputStream.toByteArray())
            nearbyManager.send(inputStream)
            inputStream.close()
        }
    }

    override fun onResume() {
        Timber.w("onResume()")
        super.onResume()

        // Wait a second before advertising.
        disposables += Observable.timer(3, TimeUnit.SECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    nearbyManager.autoAdvertise()
                }

        // On successful connection, waits 1 second, takes a picture.
        disposables += nearbyManager.connectionEvents()
                .subscribe {
                    when (it) {
                        is ConnectionResult -> {
                            Timber.d("connectionResult = ${it.success} / ${it.connectionCount}")
                            if (it.success) {
                                disposables += Observable.timer(1000, TimeUnit.MILLISECONDS)
                                        .observeOn(AndroidSchedulers.mainThread())
                                        .subscribe {
                                            Timber.d("Calling cameraHelper.takePicture() (#$it)")
                                            cameraHelper.takePicture()
                                        }
                            }
                        }
                        is Disconnect -> Timber.d("disconnect, ${it.connectionCount} remaining ")
                    }
                }

        disposables += nearbyManager.receivedPayloads()
                .subscribe { Timber.d("Payload received: $it") }

        // Attempt to access the I2C device
    }

    fun testDrive() {
/*
        try {

            {
                motorHat.setMotorSpeed(0, 0)
                motorHat.setMotorSpeed(1, 0)
                motorHat.setMotorSpeed(2, 0)
                motorHat.setMotorSpeed(3, 0)
            }()

            disposables += Observable
                    .fromArray(
                            { i: Int ->
                                motorHat.setMotorState(i, MotorHat.MOTOR_STATE_CW)
                                motorHat.setMotorSpeed(i, 32)
                            },
                            { motorHat.setMotorState(it, MotorHat.MOTOR_STATE_CCW) },
                            {
                                motorHat.setMotorState(it, MotorHat.MOTOR_STATE_CW)
                                motorHat.setMotorSpeed(it, 128)
                            },
                            { motorHat.setMotorState(it, MotorHat.MOTOR_STATE_CCW) },
                            {
                                motorHat.setMotorState(it, MotorHat.MOTOR_STATE_CW)
                                motorHat.setMotorSpeed(it, 255)
                            },
                            { motorHat.setMotorState(it, MotorHat.MOTOR_STATE_CCW) },
                            { motorHat.setMotorState(it, MotorHat.MOTOR_STATE_RELEASE) }
                    )
                    .concatMap { command ->
                        Observable.timer(500, TimeUnit.MILLISECONDS).map { command }
                    }
                    .doOnNext { _ -> Timber.i("execute command()") }
                    .subscribe { command ->
                        // Command parameter to address a specific wheel motor.
                        command(0)
                        command(1)
                        command(2)
                        command(3)
                    }

        } catch (e: IOException) {
            Timber.w("Unable to access I2C device $e")
        }
*/
    }

    override fun onPause() {
        Timber.w("onPause()")
        super.onPause()
        disposables.clear()
    }

    override fun onDestroy() {
        Timber.i("onDestroy()")
        super.onDestroy()

        cameraHelper.closeCamera()
        ultrasonicSensor.close()
        //    safeClose("Unable to close I2C device %s", { motorHat.close() })

    }

    private fun safeClose(errMsg: String, close: () -> Unit) {
        try {
            close()
        } catch (e: IOException) {
            Timber.d(e, errMsg)
        }
    }

}