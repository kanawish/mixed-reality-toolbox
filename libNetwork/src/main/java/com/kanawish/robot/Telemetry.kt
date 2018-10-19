package com.kanawish.robot

import java.io.Serializable

/**
 * Telemetry received from the Robot.
 *
 * @param distance as reported by the ultrasonic sensor.
 * @param image latest video frame received from the Camera 2 APIs.
 */
class Telemetry(val distance: Double, val image: ByteArray) : Serializable
