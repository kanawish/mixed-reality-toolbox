package com.kanawish.thing.robot

import com.google.android.things.contrib.driver.motorhat.MotorHat
import timber.log.Timber
import java.io.IOException
import kotlin.math.abs

fun Double.outOfBounds() = this < 30 || this > 10000

fun safeClose(errMsg: String, close: () -> Unit) {
    try {
        close()
    } catch (e: IOException) {
        Timber.d(e, errMsg)
    }
}

/**
 * A bit of extension function fun.
 */
fun Boolean.toSpin(): Int = if (this) MotorHat.MOTOR_STATE_CW else MotorHat.MOTOR_STATE_CCW

/**
 * Releases all 4 motors.
 */
fun MotorHat.releaseAll() {
    Timber.d("releaseAll()")
    for (i in 0..3) setMotorState(i, MotorHat.MOTOR_STATE_RELEASE)
}

fun MotorHat.cmd(motorId: Int, clockwise: Boolean, speed: Int) {
    setMotorState(motorId, clockwise.toSpin())
    setMotorSpeed(motorId, speed)
}

/**
 * Applies the correct spin and speed to the addressed motor
 */
fun MotorHat.cmd(motorId: Int, speed: Int) {
    setMotorState(motorId, (speed > 0).toSpin())
    setMotorSpeed(motorId, abs(speed))
}

fun MotorHat.move(forward: Boolean, speed: Int = 128) {
    Timber.d("move(forward=$forward,speed=$speed)")
    for (i in 0..3) cmd(i, forward, speed)
}

fun MotorHat.rot(clockwise: Boolean, speed: Int = 200) {
    Timber.d("rot(clockwise=$clockwise,speed=$speed)")
    arrayOf(0, 1).forEach {
        cmd(it, !clockwise, speed)
    }
    arrayOf(2, 3).forEach {
        cmd(it, clockwise, speed)
    }
}

/**
 * Convenience function to control the robot drive system.
 */
fun MotorHat.drive(left: Int, right: Int) {
    Timber.d("left:$left, right:$right")
    for (i in 0..1) cmd(i, right)
    for (i in 2..3) cmd(i, left)
}

