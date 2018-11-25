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
class RemoteControlActivity : Activity()
