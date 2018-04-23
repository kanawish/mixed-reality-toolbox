package com.kanawish.dd.robotcontroller

import io.reactivex.Observable
import io.reactivex.observers.TestObserver
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun testSequence() {
        val controlSeq = sequence {
            step(1.0, 64)
            step(2.0, 128)
            step(2.0, 64, 128)
            step(2.0, 128, 64)
            step(1.0, 64)
            STOP
        }

        val testObserver = TestObserver<Int>()
        // Just a mock time sequence.
        val sequence = Observable.range(0, 10)
                .map { it * 900 }
                .doOnNext { t -> println("controlSeq($t) = ${controlSeq(t.toLong())}") }
                .subscribe(testObserver)

        testObserver.awaitTerminalEvent()
        println("testObserver.awaitTerminalEvent() completed")
    }
}

fun sequence(init: ControlSequence.() -> Unit): ControlSequence {
    val sequence = ControlSequence()
    sequence.init()
    return sequence
}

// NOTE: Int time implies milliseconds, Double implies seconds.
data class Step(val startTime: Int, val duration: Int, val control: ControlFunction) {
    constructor(startTime: Int, duration: Double, control: ControlFunction) :
            this(startTime, (duration * 1000).toInt(), control)

    // Memory over speed
    fun endTime(): Int = startTime + duration
}

/**
 * A control sequence is composed of ControlFunction commands mapped to a timeline.
 */
class ControlSequence() : ControlFunction {

    /**
     * Sequence items are pairs of cutoff time + control function.
     */
    private val sequence = mutableListOf<Step>()

    /**
     * Add a controlFunction step to the sequence.
     */
    fun step(duration: Double, controlFunction: ControlFunction) {
        val startTime = sequence.lastOrNull()?.let { (lastStart, lastDuration, _) -> lastStart + lastDuration }
                ?: 0
        sequence.add(Step(startTime, duration, controlFunction))
    }

    fun step(duration: Double, speed: Int) = step(duration, { _ -> Drive(speed) })
    fun step(duration: Int, speed: Int) = step(duration.toDouble(), speed)

    fun step(duration: Double, left: Int, right: Int) = step(duration, { _ -> Drive(left, right) })
    fun step(duration: Int, left: Int, right: Int) = step(duration.toDouble(), left, right)

    /**
     * We traverse the sequenceItems until we find a cutoff time > than the
     * current time we are looking up. That sequence item is used to calculate
     * the Drive return value.
     *
     * Items are passed *relative* time, i.e. elapsed since they started.
     *
     * NOTE: The control is passed relative time values. I.e. 0 to duration.
     */
    override fun invoke(time: Long): Drive {
        return sequence
                .firstOrNull { it.endTime() > time }
                ?.let { (startTime, _, control) -> control(time - startTime) }
                ?: STOP(time)
    }
}

/**
 * Speed values for left and right 'drives'
 *
 * Negative values will result in backward spin.
 *
 * [Robot wheel control theory](http://www.robotplatform.com/knowledge/Classification_of_Robots/wheel_control_theory.html)
 */
data class Drive(val left: Int, val right: Int) {
    constructor(speed: Int) : this(left = speed, right = speed)
}

/**
 * ControlFunction is a function that returns Drive values for a give time elapsed.
 */
typealias ControlFunction = (time: Long) -> Drive

/**
 * STOP is the fallback
 */
val STOP: ControlFunction = { _ -> Drive(0) }
