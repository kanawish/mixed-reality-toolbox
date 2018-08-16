package com.kanawish.dd.robotcontroller

import android.app.Activity
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import com.jakewharton.rxbinding2.widget.textChanges
import com.kanawish.joystick.ConnectionManager
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
import javax.inject.Inject

/**
 * TODOs
 * - [] IDEA: Add ARCore support, have controller map room, send that to robot.
 *   - [] See about sharing Google's visual anchor data to let the robot find them with OpenCV?
 *   - [] Evaluate: simpler to have controller in charge?
 * - [] IDEA: put the phone on top of robot, control the whole thing via joysticks, map...
 */
class ControllerActivity : Activity() {

    @Inject lateinit var connectionManager: ConnectionManager
    @Inject lateinit var server: NetworkServer
    @Inject lateinit var client: NetworkClient

    private val disposables = CompositeDisposable()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.controller_ui)

        // TODO: Change this to auto connect, emit state.
        connectionManager.selectedDevice()?.let { connectionManager.createBond(it) }
                ?: connectionManager.startDiscovery()
    }

    override fun onResume() {
        super.onResume()
        disposables += server
                .receiveTelemetry()
                .doOnNext { Timber.d("Telemetry(${it.distance}cm, ${it.image.size} bytes)") }
                .map { it.distance to it.image.toBitmap() }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ (_, bitmap) -> imageView.setImageBitmap(bitmap) })

        var d = 1L
        disposables += duration.textChanges()
                .filter { it.isNotBlank() }
                .map { x -> x.toString().toLong() }
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

    override fun onDestroy() {
        super.onDestroy()
        connectionManager.cancelDiscovery()
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        val x = event.getAxisValue(MotionEvent.AXIS_X)
        val y = event.getAxisValue(MotionEvent.AXIS_Y)
        val z = event.getAxisValue(MotionEvent.AXIS_Z) // (X right analog)
        val rx = event.getAxisValue(MotionEvent.AXIS_RX)
        val ry = event.getAxisValue(MotionEvent.AXIS_RY)
        val rz = event.getAxisValue(MotionEvent.AXIS_RZ) // (Y right analog)
        Timber.d("MotionEvent: ($x $y $z, $rx $ry $rz)")
        return true
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        return true
    }
}

