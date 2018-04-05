package com.kanawish.thing.mr

import android.app.Activity
import android.os.Bundle
import com.google.android.things.contrib.driver.motorhat.MotorHat
import com.google.android.things.pio.PeripheralManagerService
import io.reactivex.Observable
import io.reactivex.disposables.Disposable
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.TimeUnit


class MainActivity : Activity() {

    val manager by lazy {
        PeripheralManagerService()
    }

    val motorHat: MotorHat by lazy {
        try {
            MotorHat(currentDevice().i2cBus())
        } catch (e: IOException) {
            throw RuntimeException("Failed to create MotorHat", e)
        }
    }

    //    var device: I2cDevice? = null
    var disposable: Disposable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Timber.d("${manager.gpioList}")

        Timber.d("${manager.pwmList}")
        Timber.d("${manager.i2cBusList}")
//        Timber.d("${manager.i2sDeviceList}")
        Timber.d("${manager.spiBusList}")
        Timber.d("${manager.uartDeviceList}")

        // Attempt to access the I2C device
        try {
            motorHat.setMotorSpeed(0, 0)

            disposable = Observable
                    .fromArray(
                            { i: Int ->
                                motorHat.setMotorState(i, MotorHat.MOTOR_STATE_CW)
                                motorHat.setMotorSpeed(i, 64)
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
                        Observable.timer(1, TimeUnit.SECONDS).map { command }
                    }
                    .doOnNext { _ -> Timber.i("execute command()") }
                    .subscribe { command ->
                        command(0)
                        command(1)
                    }

        } catch (e: IOException) {
            Timber.w("Unable to access I2C device $e")
        }


//        motors[0].speed(150)
//        motors[0].forward()


    }

    override fun onResume() {
        super.onResume()

        Timber.w("onResume()")

    }

    override fun onPause() {
        super.onPause()
        Timber.w("onPause()")
        disposable?.dispose()
    }

    override fun onDestroy() {
        super.onDestroy()

        Timber.i("onDest")
        safeClose("Unable to close I2C device %s", { motorHat.close() })
    }

    private fun safeClose(errMsg: String, close: () -> Unit) {
        try {
            close()
        } catch (e: IOException) {
            Timber.d(e, errMsg)
        }
    }

}