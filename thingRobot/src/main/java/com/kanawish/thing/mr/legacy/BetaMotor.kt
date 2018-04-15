package com.kanawish.thing.mr.legacy

import android.app.Activity
import android.os.Bundle
import com.google.android.things.pio.I2cDevice
import com.google.android.things.pio.PeripheralManager
import io.reactivex.Observable
import io.reactivex.disposables.Disposable
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.experimental.and
import kotlin.experimental.inv
import kotlin.experimental.or
import kotlin.math.floor

class BetaMotorActivity : Activity() {

    val manager by lazy {
        PeripheralManager.getInstance()
    }

    var device: I2cDevice? = null
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
            device = manager.openI2cDevice("I2C1", 0x60)

            disposable = Observable
                    .fromArray(
                            {
                                device?.init()
                                motors[0].release()
                                motors[0].speed(64)
                                motors[0].forward()
                            },
                            { motors[0].speed(128) },
                            { motors[0].speed(255) },
                            { motors[0].release() })
                    .concatMap { command ->
                        Observable.timer(1, TimeUnit.SECONDS).map { command }
                    }
                    .doOnNext { _ -> Timber.i("execute command()") }
                    .subscribe { command -> command() }

        } catch (e: IOException) {
            Timber.w("Unable to access I2C device $e")
        }


//        motors[0].speed(150)
//        motors[0].forward()


    }

    override fun onResume() {
        super.onResume()

        Timber.w("onResume()")
/*
        disposables += keyRelay
                .filter { (code, _) -> code == KeyEvent.KEYCODE_SPACE }
                .map { (_, event) -> event.action == KeyEvent.ACTION_DOWN }
                .doOnNext { b -> Timber.d("blueLED: $b") }
                .subscribe {
                    blueLED.value = it
                }
*/

    }

    override fun onPause() {
        super.onPause()
        Timber.w("onPause()")
        disposable?.dispose()
    }

    override fun onDestroy() {
        super.onDestroy()

        Timber.i("onDest")
        safeClose("Unable to close I2C device %s", { device?.close() })
    }

    private fun safeClose(errMsg: String, close: () -> Unit) {
        try {
            close()
        } catch (e: IOException) {
            Timber.d(e, errMsg)
        }
    }

    class DCMotor(val pwm: Int, val in1: Int, val in2: Int)

    val motors = arrayOf(
            DCMotor(8, 10, 9),
            DCMotor(13, 11, 12),
            DCMotor(2, 4, 3),
            DCMotor(7, 5, 6)
    )

    fun DCMotor.forward() {
        setPin(in2, 0)
        setPin(in1, 1)
    }

    fun DCMotor.backward() {
        setPin(in1, 0)
        setPin(in2, 1)
    }

    fun DCMotor.release() {
        setPin(in1, 0)
        setPin(in2, 0)
    }

    fun DCMotor.speed(speed: Int) {
        (if (speed < 0) 0 else if (speed > 255) 255 else speed).let {
            device?.setPWM(pwm, 0, it * 16)
        }
    }

    private fun setPin(pin: Int, value: Int) {
        check((pin >= 0) && (pin <= 15)) { "PWM pin must be between 0 and 15 inclusive" }
        check(value == 0 || value == 1) { "Pin value must be 0 or 1!" }

        if (value == 0) device?.setPWM(pin, 0, 4096)
        if (value == 1) device?.setPWM(pin, 4096, 0)
    }

}

// NOTE: There's a software stopAll function in the original drivers too... skipping it for now.

fun I2cDevice.init() {
//    self.i2c = get_i2c_device(address, i2c, i2c_bus)
    Timber.d("init() Resetting PCA9685 MODE1 (without SLEEP) and MODE2")
    setAllPWM(0, 0)
    writeRegByte(__MODE2, __OUTDRV)
    writeRegByte(__MODE1, __ALLCALL)
    // wait for oscillator
    Thread.sleep(5)
    var mode1 = readRegByte(__MODE1)
    Timber.i("mode1 = ${mode1.toString(16)}")
    // wake up (stopAll sleep)
    mode1 = mode1 and __SLEEP.toByte().inv()
    Timber.i("writeRegByte(${__MODE1.toString(16)}: ${mode1.toString(16)}")
    writeRegByte(__MODE1, mode1)
    // wait for oscillator
    Thread.sleep(5)
    Timber.d("init() complete")
}

fun I2cDevice.setPWMFreq(freq: Float) {
    // "Sets the PWM frequency"
    var prescaleval = 25000000.0    // 25MHz
    prescaleval /= 4096.0       // 12-bit
    prescaleval /= freq
    prescaleval -= 1.0

    Timber.i("Setting PWM frequency to $freq Hz")
    Timber.i("Estimated pre-scale: $prescaleval")
    val prescale = floor(prescaleval + 0.5)
    Timber.i("Final pre-scale: $prescale")
    val oldmode = readRegByte(__MODE1)
    // sleep
    val newmode = (oldmode and 0x7F) or 0x10
    // go to sleep
    writeRegByte(__MODE1, newmode)
    writeRegByte(__PRESCALE, floor(prescale).toByte())
    writeRegByte(__MODE1, oldmode)
    // FIXME following python's pattern, but...
    Thread.sleep(5)
    writeRegByte(__MODE1, oldmode and 0x80.toByte())
}

fun I2cDevice.setPWM(channel: Int, on: Int, off: Int) {
    Timber.i("setPWM() start")
    writeRegByte(__LED0_ON_L + 4 * channel, on and 0xFF)
    writeRegByte(__LED0_ON_H + 4 * channel, on shr 8)
    writeRegByte(__LED0_OFF_L + 4 * channel, off and 0xFF)
    writeRegByte(__LED0_OFF_H + 4 * channel, off shr 8)
    Timber.i("setPWM() end")
}

fun I2cDevice.setAllPWM(on: Int, off: Int) {
    Timber.i("setAllPWM() start")
    writeRegByte(__ALL_LED_ON_L, on and 0xFF)
    writeRegByte(__ALL_LED_ON_H, on shr 8)
    writeRegByte(__ALL_LED_OFF_L, off and 0xFF)
    writeRegByte(__ALL_LED_OFF_H, off shr 8)
    Timber.i("setAllPWM() end")
}

fun I2cDevice.writeRegByte(address: Int, value: Int) {
    Timber.i("writeRegByte ${address.toString(16)}: ${value.toString(16)}")
    writeRegByte(address, value.toByte())
}

// Registers/etc.
const val __MODE1 = 0x00
const val __MODE2 = 0x01
const val __SUBADR1 = 0x02
const val __SUBADR2 = 0x03
const val __SUBADR3 = 0x04
const val __PRESCALE = 0xFE
const val __LED0_ON_L = 0x06
const val __LED0_ON_H = 0x07
const val __LED0_OFF_L = 0x08
const val __LED0_OFF_H = 0x09
const val __ALL_LED_ON_L = 0xFA
const val __ALL_LED_ON_H = 0xFB
const val __ALL_LED_OFF_L = 0xFC
const val __ALL_LED_OFF_H = 0xFD

// Bits
const val __RESTART = 0x80
const val __SLEEP = 0x10
const val __ALLCALL = 0x01
const val __INVRT = 0x10
const val __OUTDRV = 0x04
