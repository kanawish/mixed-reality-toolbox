package com.kanawish.ar.robotremote

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.KeyEvent
import android.view.MotionEvent
import com.google.ar.core.AugmentedImage
import com.google.ar.core.Frame
import com.google.ar.core.TrackingState.PAUSED
import com.google.ar.core.TrackingState.STOPPED
import com.google.ar.core.TrackingState.TRACKING
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.FrameTime
import com.google.ar.sceneform.Node
import com.google.ar.sceneform.math.Quaternion
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.DpToMetersViewSizer
import com.google.ar.sceneform.rendering.ViewRenderable
import com.google.ar.sceneform.ux.ArFragment
import com.kanawish.ar.robotremote.ArMainActivity.Steps.Calibrated
import com.kanawish.ar.robotremote.ArMainActivity.Steps.Going
import com.kanawish.ar.robotremote.ArMainActivity.Steps.MeasureDistance
import com.kanawish.ar.robotremote.ArMainActivity.Steps.MeasureRotation
import com.kanawish.ar.robotremote.ArMainActivity.Steps.Moving
import com.kanawish.ar.robotremote.ArMainActivity.Steps.PreCalibration
import com.kanawish.ar.robotremote.ArMainActivity.Steps.Rotating
import com.kanawish.ar.robotremote.util.checkIsSupportedDeviceOrFinish
import com.kanawish.ar.robotremote.util.format
import com.kanawish.ar.robotremote.util.viewRenderable
import com.kanawish.kotlin.safeLet
import com.kanawish.robot.Command
import com.kanawish.socket.NetworkClient
import com.kanawish.socket.NetworkServer
import com.kanawish.socket.ROBOT_ADDRESS
import com.kanawish.socket.toBitmap
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.Singles
import io.reactivex.rxkotlin.plusAssign
import kotlinx.android.synthetic.main.remote_control_view.view.calibrateButton
import kotlinx.android.synthetic.main.remote_control_view.view.goButton
import kotlinx.android.synthetic.main.remote_control_view.view.imageView
import kotlinx.android.synthetic.main.remote_control_view.view.distanceText
import timber.log.Timber
import java.util.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.math.pow

private const val TAG = "ARK "

/**
 * @startuml
 * object Robot
 * object ControllerApp
 * @enduml
 *
 */
class ArMainActivity : AppCompatActivity() {

    @Inject lateinit var server: NetworkServer
    @Inject lateinit var client: NetworkClient

    private val disposables = CompositeDisposable()

    private lateinit var arFragment: ArFragment

    private val augmentedImageMap = HashMap<AugmentedImage, AugmentedImageNode>()

    // NOTE: Worth documenting the wiring up of the various listeners.
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!checkIsSupportedDeviceOrFinish()) return

        setContentView(R.layout.activity_ux)
        arFragment = supportFragmentManager.findFragmentById(R.id.ux_fragment) as ArFragment

        // NOTE: Show how the future buildup requires us to hook up things 'in due time'
        // NOTE: Show before -> after in deck.
        disposables += Singles
                .zip(
                        viewRenderable(R.layout.remote_control_view),
                        viewRenderable(R.layout.marker)
                )
                .subscribe { (controller, marker) ->
                    // Controller init
                    controller.sizer = DpToMetersViewSizer(1000)
                    bindControlView(controller)

                    // Marker init
                    marker.isShadowCaster = false
                    arFragment.bindTapToMove(marker, controller)
                }

    }

    override fun onPause() {
        super.onPause()
        // NOTE: Explain how this insures we don't trigger out-of-bound handling, as long as threading is good.
        disposables.clear()
    }

    var markerAnchorNode: AnchorNode? = null

    private fun ArFragment.bindTapToMove(marker: ViewRenderable, controller: ViewRenderable) {
        setOnTapArPlaneListener { hitResult, _, _ ->

            // Clean up the old marker, only have one at any time.
            markerAnchorNode?.let { it -> arSceneView.scene.removeChild(it) }

            // Create the Anchor and AnchorNode.
            markerAnchorNode = AnchorNode(hitResult.createAnchor()).apply {
                setParent(arSceneView.scene)
                currentCarPosition()?.let { carPos ->
                    controller.view.distanceText.text = "Distance to marker: ${calculateDistance(carPos,worldPosition).format(2)}"
                }
            }

            Node().apply {
                setParent(markerAnchorNode)
                renderable = marker
                localRotation = Quaternion.axisAngle(Vector3(1f, 0f, 0f), -90f)
                Timber.i("${TAG}Marker ${worldPosition} / ${localPosition}")
            }
        }
    }

    sealed class Steps {
        object PreCalibration:Steps()
        object Rotating:Steps()
        object MeasureRotation:Steps()
        object Moving:Steps()
        object MeasureDistance:Steps()
        object Calibrated:Steps()
        object Going:Steps()
    }

    var step:Steps = PreCalibration

    val calibrationDuration = 2500L
    var startPos:Vector3? = null
    var calibratedDistance:Float = 0f
    var calibratedAngle:Float = 0f

    fun calculateDistance(startPos:Vector3,endPos:Vector3):Float {
        val dx:Double = (startPos.x - endPos.x).toDouble()
        val dy:Double = (startPos.y - endPos.y).toDouble()
        return Math.sqrt(dx.pow(2)+dy.pow(2)).toFloat()
    }

    fun currentCarPosition(): Vector3? {
        return newMap.entries.first().value.worldPosition
    }

    private fun nextStep(time: Long, block: () -> Unit) {
        disposables += Single
                .timer(time,TimeUnit.MILLISECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe{ _ -> block()}
    }

    /**
     * controlView outputs:
     * - Calibration clicks
     *
     * controlView inputs:
     * - Telemetry (photos, calibratedDistance)
     */
    private fun bindControlView(controlRenderable: ViewRenderable) {
        val controlView = controlRenderable.view
        fun send(command: Command) = client.sendCommand(ROBOT_ADDRESS, command)
        controlView.calibrateButton.setOnClickListener {
            when (step) {
                PreCalibration -> {
                    controlView.calibrateButton.text = "MOVING"
                    controlView.calibrateButton.isEnabled = false
                    send(Command(calibrationDuration, -200, -200))
                    startPos = currentCarPosition()
                    step = Moving
                    nextStep(calibrationDuration) {
                        controlView.calibrateButton.text = "MEASURE"
                        controlView.calibrateButton.isEnabled = true
                        step = MeasureDistance
                    }
                }
                Rotating -> TODO()
                MeasureRotation -> TODO()
                MeasureDistance -> {
                    // The user is expected to have re-scanned the augmentedImage
                    safeLet(startPos, currentCarPosition()) { start, end ->
                        calibratedDistance = calculateDistance(start,end)
                        Timber.i("${TAG}Calibrated Distance is ${calibratedDistance.format(2)} over ${calibrationDuration} for distance-per-time-unit of ${distancePerTimeUnit()}")
                        controlView.calibrateButton.text = "GO TO MARKER" // TODO: Use proper calibratedDistance to marker
                        step = Calibrated
                    }
                }
                Calibrated -> {
                    // Given that we have a target marker, rotate, then move towards it.
                    safeLet(currentCarPosition(),markerAnchorNode?.worldPosition) { start, end ->
                        val calculatedDistance = calculateDistance(start, end)
                        val calculatedTime: Long = (calculatedDistance / distancePerTimeUnit()).toLong()
                        Timber.i("${TAG}GOING for calculated time: $calculatedTime ms, distance $calculatedDistance")
                        controlView.calibrateButton.text = "GOING"
                        controlView.calibrateButton.isEnabled = false
                        send(Command(calculatedTime, -200, -200))
                        step = Going

                        nextStep(calculatedTime) {
                            controlView.calibrateButton.text = "GO" // TODO: Use proper calibratedDistance to marker
                            controlView.calibrateButton.isEnabled = true
                            step = Calibrated
                        }
                    }

                }
                else -> throw IllegalStateException("Attempt to handle ${step} via clickListener.")
            }
        }

        disposables += server
                .receiveTelemetry()
                .map { it.distance to it.image.toBitmap() }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { (_, bitmap) ->
                    controlView.imageView.setImageBitmap(bitmap)
                }

        // Scene update frame handling
        arFragment.arSceneView.scene.addOnUpdateListener { frameTime ->
            // Only process frames when needed.
            arFragment.arSceneView.arFrame?.let { frame ->
                if (frame.camera.trackingState == TRACKING) {
                    onUpdateFrame(frame, frameTime, controlRenderable)
                }
            }
        }
    }

    private fun distancePerTimeUnit() = calibratedDistance / calibrationDuration

    val newMap = HashMap<AugmentedImage, AnchorNode>()
    // TODO: Improve the code here, confusing.
    private fun onUpdateFrame(frame: Frame, frameTime: FrameTime, controlRenderable: ViewRenderable) {
        for (augmentedImage in frame.getUpdatedTrackables(AugmentedImage::class.java)) {
            when (augmentedImage.trackingState) {
                PAUSED ->
                    Timber.i("${TAG}Detected Image ${augmentedImage.name} / ${augmentedImage.index}")
                TRACKING -> {
                    Timber.i("${TAG}Tracking Image ${augmentedImage.name} / ${augmentedImage.centerPose}")
                    // Add an image once tracking starts
                    if (!newMap.contains(augmentedImage)) {
                        newMap[augmentedImage] = addControlToScene(augmentedImage, controlRenderable)
                    } else {
                        newMap[augmentedImage]?.worldPosition.let {
                            Timber.i("${TAG}Tracking Image child node? ${it}")
                        }
                    }
                }
                STOPPED -> {
                    Timber.i("${TAG}Image Lost ${augmentedImage.name}")
                    // gets rid of children's own children, hopefully.
                    augmentedImageMap[augmentedImage]?.controlNode?.let {
                        arFragment.arSceneView.scene.removeChild(it)
                    }
                    // Remove an image once tracking stops.
                    augmentedImageMap.remove(augmentedImage)
                }
                null -> throw IllegalStateException("${TAG}Shouldn't be possible")
            }
        }
    }

    private fun addControlToScene(augmentedImage: AugmentedImage, controlRenderable: ViewRenderable): AnchorNode {
        val pose = augmentedImage.centerPose
        val anchorNode = AnchorNode(augmentedImage.createAnchor(pose))
        anchorNode.setParent(arFragment.arSceneView.scene)
        Timber.i("${TAG}AugmentedImage ${anchorNode.worldPosition} / ${anchorNode.localPosition}")

        Node().apply {
            setParent(anchorNode)
            renderable = controlRenderable
            localPosition = Vector3(0.0f, 0f, -0.08f)
            localRotation = Quaternion.axisAngle(Vector3(1f, 0f, 0f), -90f)
            Timber.i("${TAG}Controls ${worldPosition} / ${localPosition}")
        }

        return anchorNode
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        val x = event.getAxisValue(MotionEvent.AXIS_X)
        val y = event.getAxisValue(MotionEvent.AXIS_Y)
        val z = event.getAxisValue(MotionEvent.AXIS_Z) // (X right analog)
        val rx = event.getAxisValue(MotionEvent.AXIS_RX)
        val ry = event.getAxisValue(MotionEvent.AXIS_RY)
        val rz = event.getAxisValue(MotionEvent.AXIS_RZ) // (Y right analog)
        Timber.d("${TAG}MotionEvent: ($x $y $z, $rx $ry $rz)")

        return true
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        Timber.d("${TAG}KeyEvent: $event")
        return true
    }

}

