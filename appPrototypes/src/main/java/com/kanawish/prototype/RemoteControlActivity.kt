package com.kanawish.prototype

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import com.jakewharton.rxbinding2.widget.textChanges
import com.kanawish.robot.Command
import com.kanawish.socket.NetworkClient
import com.kanawish.socket.NetworkServer
import com.kanawish.socket.ROBOT_ADDRESS
import com.kanawish.socket.toBitmap
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import kotlinx.android.synthetic.main.controller_ui.*
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

/**
 * Simpler "phone in hand" remote control Activity.
 * Sends joystick readings as commands to the Robot.
 */
@SuppressLint("SetTextI18n")
class RemoteControlActivity : Activity() {

    @Inject
    lateinit var server: NetworkServer // Our input channel
    @Inject
    lateinit var client: NetworkClient // Our output channel

    private val disposables = CompositeDisposable()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.controller_ui)
    }

    override fun onResume() {
        super.onResume()

        // Subscription handling the incoming telemetry data.
        disposables += server
                .receiveTelemetry()
//            .doOnNext { Timber.d("Telemetry(${it.distance}cm, ${it.image.size} bytes)") }
                .map { it.distance to it.image.toBitmap() }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { (distance, bitmap) -> telemetryTextView.text = "range: $distance\nimg size: ${bitmap.byteCount}" }

        // Bitmap feed processing.
        disposables += server
                .receiveBitmaps() // InetSocketAddress(SERVER_IP, PORT_BM)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(imageView::setImageBitmap)

        //
        var d = 1L
        disposables += durationEditText.textChanges()
                .filter { it.isNotBlank() }
                .map { charSequence -> charSequence.toString().toLong() }
                .subscribe { d = it }

        fun send(command: Command) = client.sendCommand(ROBOT_ADDRESS, command)
        forwardButton.setOnClickListener { send(Command(d, -128, -128)) }
        rightButton.setOnClickListener { send(Command(d, 128, -128)) }
        backwardButton.setOnClickListener { send(Command(d, 128, 128)) }
        leftButton.setOnClickListener { send(Command(d, -128, 128)) }
        scanButton.setOnClickListener { send(Command(d, 0, 0)) }
    }

    override fun onPause() {
        super.onPause()
        disposables.clear()
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        val x = event.getAxisValue(MotionEvent.AXIS_X)
        val y = event.getAxisValue(MotionEvent.AXIS_Y) // (Y left analog)
        val z = event.getAxisValue(MotionEvent.AXIS_Z)
        val rx = event.getAxisValue(MotionEvent.AXIS_RX)
        val ry = event.getAxisValue(MotionEvent.AXIS_RY)
        val rz = event.getAxisValue(MotionEvent.AXIS_RZ) // (Y right analog)

        val cmd = Command(10000, calcDrive(y), calcDrive(rz))
//        Timber.d("MotionEvent: ($x $y $z, $rx $ry $rz), $cmd")

        client.sendCommand(ROBOT_ADDRESS, cmd)

        return true
    }

    /** Massage values received from joystick. */
    fun calcDrive(axis:Float): Int = deadZone(clamp(-axis * 255))
    /** Limit possible values. */
    fun clamp(result: Float) = Math.min(Math.max(-255, result.roundToInt()), 255)
    /** Ignore joysticks at rest. */
    fun deadZone(result:Int) = if( result.absoluteValue > 8 ) result else 0

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        return true
    }
}

