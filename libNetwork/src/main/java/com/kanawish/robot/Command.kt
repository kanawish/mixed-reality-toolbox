package com.kanawish.robot

import java.io.Serializable

/**
 * Command sent to the robot.
 *
 * @param duration time for command, in milliseconds.
 * @param left speed of left drivers, negative means reverse.
 * @param right speed of right drivers, negative means reverse.
 */
class Command(val duration: Long, val left: Int, val right: Int) : Serializable {
    companion object {
        fun fromString(string: String): Command {
            val params = string.split(Regex.fromLiteral(","))
            return Command(params[0].toLong(), params[1].toInt(), params[2].toInt())
        }
    }

    override fun toString(): String {
        return "$duration,$left,$right"
    }
}

