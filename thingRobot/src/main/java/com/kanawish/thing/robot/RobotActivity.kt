package com.kanawish.thing.robot

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import com.google.android.things.contrib.driver.motorhat.MotorHat
import com.google.android.things.contrib.driver.ultrasonicsensor.DistanceListener
import com.google.android.things.contrib.driver.ultrasonicsensor.UltrasonicSensorDriver
import com.google.android.things.pio.PeripheralManager
import com.jakewharton.rxrelay2.BehaviorRelay
import com.jakewharton.rxrelay2.PublishRelay
import com.kanawish.utils.camera.CameraHelper
import com.kanawish.robot.Command
import com.kanawish.robot.Telemetry
import com.kanawish.socket.HOST_PHONE_ADDRESS
import com.kanawish.socket.NetworkClient
import com.kanawish.socket.NetworkServer
import com.kanawish.utils.camera.dumpFormatInfo
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.rxkotlin.withLatestFrom
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.math.abs

/**
 * @startuml
 * object Robot
 * object UltrasonicSensor
 * object Camera
 * object MotorDrive {
 * wheels[4]
 * }
 * Robot *.. Camera
 * Robot *.. UltrasonicSensor
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
 */
class RobotActivity : Activity() {

    @Inject lateinit var cameraHelper: CameraHelper
    @Inject lateinit var networkClient: NetworkClient

    @Inject lateinit var server: NetworkServer


    val distances = BehaviorRelay.create<Double>()
    val images = BehaviorRelay.create<ByteArray>()

    val telemetryReadings: Observable<Telemetry> = distances
            .withLatestFrom(images)
            .map { (d, i) -> Telemetry(d, i) }

    private val manager by lazy {
        PeripheralManager.getInstance()
    }

    private val motorHat: MotorHat by lazy {
        try {
            MotorHat(currentDevice().i2cBus())
        } catch (e: IOException) {
            throw RuntimeException("Failed to create MotorHat", e)
        }
    }

    /**
     * When instantiating the ultrasonic sensor, you must pick the IO pins
     * you used to wire it up.
     */
    private val ultrasonicSensor: UltrasonicSensorDriver = try {
        UltrasonicSensorDriver(
                "GPIO2_IO01", "GPIO2_IO02",
                DistanceListener { distanceInCm ->
                     Timber.d("Distance $distanceInCm cm")
                    distances.accept(distanceInCm)
                })
    } catch (e: IOException) {
        throw RuntimeException("Failed to create UltrasonicSensorDriver", e)
    }

    private var disposables: CompositeDisposable = CompositeDisposable()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Timber.d("${manager.gpioList}")

        Timber.d("${manager.pwmList}")
        Timber.d("${manager.i2cBusList}")
        Timber.d("${manager.spiBusList}")
        Timber.d("${manager.uartDeviceList}")

        // Diagnostics
        dumpFormatInfo()

        // TODO: Convert to a reactive stream setup.
        // Pictures taken will be handled by onPictureTaken
        cameraHelper.openCamera(::onPictureTaken)

    }

    override fun onResume() {
        Timber.w("onResume()")
        super.onResume()

        // Photo capture frequency.
        disposables += Observable.interval(2500, TimeUnit.MILLISECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    Timber.d("Calling cameraHelper.takePicture() (#$it)")
                    cameraHelper.takePicture()
                }

        disposables += telemetryReadings
                .throttleLast(3, TimeUnit.SECONDS)
                .subscribe(
                        { telemetry ->
                            Timber.d("Sending telemetry: ${telemetry.distance} cm / ${telemetry.image.size} bytes")
                            // Every call opens a socket with server, sends the data, and for now is non-blocking.
                            networkClient.sendTelemetry(HOST_PHONE_ADDRESS, telemetry)
                        },
                        { throwable -> Timber.e(throwable) }
                )

        disposables += server.receiveCommand()
                .doOnNext { Timber.d("Command(${it.duration}, ${it.left}. ${it.right})") }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ acceptCommand(it) })

        disposables += subscribeParser()

        // TODO: Demo stuff goes here
        wallChicken()
    }

    override fun onPause() {
        Timber.w("onPause()")
        super.onPause()
        disposables.clear()
    }

    override fun onDestroy() {
        Timber.i("onDestroy()")
        super.onDestroy()

        safeClose("Problem closing camera %s", cameraHelper::closeCamera)
        safeClose("Problem closing ultrasonicSensor %s", ultrasonicSensor::close)
        safeClose("Problem closing motorHat %s", motorHat::close)
    }

    // CCW is forward, CW is backward.
    fun Boolean.direction(): Int = if (this) MotorHat.MOTOR_STATE_CW else MotorHat.MOTOR_STATE_CCW

    fun releaseAll() {
        Timber.d("releaseAll()")
        for (i in 0..3) motorHat.setMotorState(i, MotorHat.MOTOR_STATE_RELEASE)
    }

    fun cmd(motorId: Int, direction: Boolean, speed: Int) {
        motorHat.setMotorState(motorId, direction.direction())
        motorHat.setMotorSpeed(motorId, speed)
    }

    fun move(forward: Boolean, speed: Int = 128) {
        Timber.d("move(forward=$forward,speed=$speed)")
        for (i in 0..3) cmd(i, forward, speed)
    }

    fun rot(clockwise: Boolean, speed: Int = 200) {
        Timber.d("rot(clockwise=$clockwise,speed=$speed)")
        arrayOf(0, 1).forEach {
            cmd(it, !clockwise, speed)
        }
        arrayOf(2, 3).forEach {
            cmd(it, clockwise, speed)
        }
    }

    fun drive(left: Int, right: Int) {
        Timber.d("left:$left, right:$right")
        for (i in 0..1) cmd(i, right < 0, abs(right))
        for (i in 2..3) cmd(i, left < 0, abs(left))
    }

    val parsedCommand = PublishRelay.create<Pair<Long, () -> Unit>>()

    fun acceptCommand(cmd: Command) {
        val parsed = 1L to { drive(cmd.left, cmd.right) }

        // Queues command
        parsedCommand.accept(parsed)
        // Queues terminator if needed.
        if (cmd.duration > 0) parsedCommand.accept(cmd.duration to { releaseAll() })
    }

    /**
     * Subscribes to command stream, pausing for specified time before each execution.
     */
    fun subscribeParser(): Disposable {
        return parsedCommand
                .concatMap { (time, command) ->
                    Observable.timer(time, TimeUnit.MILLISECONDS).map { command }
                }
                .subscribe { it() }
    }

    // Send pictures as nearbyManager payloads.
    private fun onPictureTaken(imageBytes: ByteArray) {
        BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size).also {
            val outputStream = ByteArrayOutputStream()

            // Forced downgrading of bitmap to emphasize speed, add quality when everything works.
            Bitmap.createScaledBitmap(it, 160, 120, false)
                    .compress(Bitmap.CompressFormat.PNG, 100, outputStream)

            // Push new image into relay
            images.accept(outputStream.toByteArray())
            outputStream.close()
        }
    }

    private fun safeClose(errMsg: String, close: () -> Unit) {
        try {
            close()
        } catch (e: IOException) {
            Timber.d(e, errMsg)
        }
    }

    /************
     * DEMO ZONE
     ************/

    /**
     * Playing chicken with the wall. Let's hope the sensor stops us.
     */
    fun wallChicken() {
        releaseAll()
        distances
                .takeUntil { it.outOfBounds() }
                .flatMap {
                    // TODO: demo - check vs "infinity"
                    if (it.outOfBounds()) Observable.just({
                        Timber.d("Stop to avoid wall!")
                        releaseAll()
                    })
                    else Observable.empty()
                }
                .startWith({ move(true, 120) }) // TODO: demo - tweak speed
    }

    fun Double.outOfBounds() = this < 30 || this > 10000

    /**
     * How about measuring the clockwise/anticlockwise bias?
     */

}