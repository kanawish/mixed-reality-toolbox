package com.kanawish.ar.robotremote

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.KeyEvent
import android.view.MotionEvent
import com.google.ar.core.Anchor
import com.google.ar.core.AugmentedImage
import com.google.ar.core.Frame
import com.google.ar.core.TrackingState.PAUSED
import com.google.ar.core.TrackingState.STOPPED
import com.google.ar.core.TrackingState.TRACKING
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.FrameTime
import com.google.ar.sceneform.Node
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.ModelRenderable
import com.google.ar.sceneform.rendering.ViewRenderable
import com.google.ar.sceneform.ux.ArFragment
import com.google.ar.sceneform.ux.BaseArFragment
import com.google.ar.sceneform.ux.TransformableNode
import com.kanawish.ar.robotremote.util.checkIsSupportedDeviceOrFinish
import com.kanawish.ar.robotremote.util.modelRenderable
import com.kanawish.ar.robotremote.util.viewRenderable
import com.kanawish.robot.Command
import com.kanawish.socket.NetworkClient
import com.kanawish.socket.NetworkServer
import com.kanawish.socket.ROBOT_ADDRESS
import com.kanawish.socket.toBitmap
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.rxkotlin.zipWith
import kotlinx.android.synthetic.main.remote_control_view.view.backwardButton
import kotlinx.android.synthetic.main.remote_control_view.view.forwardButton
import kotlinx.android.synthetic.main.remote_control_view.view.imageView
import kotlinx.android.synthetic.main.remote_control_view.view.leftButton
import kotlinx.android.synthetic.main.remote_control_view.view.rightButton
import timber.log.Timber
import javax.inject.Inject

/**
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
        disposables += viewRenderable(R.layout.remote_control_view).subscribe { viewRenderable ->
            bindControlView(viewRenderable)
        }

        // Plane tap handling
        // NOTE: Show this nice example when 2 renderables are waited on.
        disposables += modelRenderable(R.raw.andy)
                // TODO: RxKotlin for inference, make it pretty.
                .zipWith(viewRenderable(R.layout.info_card_view))
                .subscribe { (model, view) ->
                    arFragment.setOnTapArPlaneListener(buildTapArPlaneListener(model, view))
                }

    }

    override fun onPause() {
        super.onPause()
        // NOTE: Explain how this insures we don't trigger out-of-bound handling, as long as threading is good.
        disposables.clear()
    }

    private fun bindTapToMove(marker:ViewRenderable) {
        arFragment.setOnTapArPlaneListener { hitResult, _, _ ->
            // Create the Anchor and AnchorNode.
            val anchorNode = AnchorNode(hitResult.createAnchor())
            // Attach node to scene.
            anchorNode.setParent(arFragment.arSceneView.scene)
            anchorNode.renderable = marker
        }
    }

    fun buildTapArPlaneListener(model: ModelRenderable, label: ViewRenderable): BaseArFragment.OnTapArPlaneListener =
            BaseArFragment.OnTapArPlaneListener { hitResult, plane, motionEvent ->
                // Create the Anchor and AnchorNode.
                val anchorNode = AnchorNode(hitResult.createAnchor())
                // Attach node to scene.
                anchorNode.setParent(arFragment.arSceneView.scene)

                // Create the transformable andy and add it to the anchor.
                val andy = TransformableNode(arFragment.transformationSystem)
                andy.setParent(anchorNode)
                andy.renderable = model
                andy.select()

                Node().apply {
                    setParent(andy)
                    renderable = label
                    localPosition = Vector3(0f, .25f, 0f)
                }
            }

    /**
     * controlView outputs:
     * - Calibration clicks
     *
     * controlView inputs:
     * - Telemetry (photos, distance)
     */
    private fun bindControlView(controlRenderable: ViewRenderable) {
        val controlView = controlRenderable.view
        val d = 1L
        fun send(command: Command) = client.sendCommand(ROBOT_ADDRESS, command)
        controlView.forwardButton.setOnClickListener { send(Command(d, -128, -128)) }
        controlView.rightButton.setOnClickListener { send(Command(d, 128, -128)) }
        controlView.backwardButton.setOnClickListener { send(Command(d, 128, 128)) }
        controlView.leftButton.setOnClickListener { send(Command(d, -128, 128)) }

        disposables += server
                .receiveTelemetry()
                .doOnNext { Timber.d("Telemetry(${it.distance}cm, ${it.image.size} bytes)") }
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

    // TODO: Improve the code here, confusing.
    private fun onUpdateFrame(frame: Frame, frameTime: FrameTime, controlRenderable: ViewRenderable) {
        for (augmentedImage in frame.getUpdatedTrackables(AugmentedImage::class.java)) {
            when (augmentedImage.trackingState) {
                PAUSED -> Timber.i("Detected Image ${augmentedImage.name} / ${augmentedImage.index}")
                TRACKING -> {
                    Timber.i("Tracking Image ${augmentedImage.name} / ${augmentedImage.centerPose}")
                    // Add an image once tracking starts
                    if (!augmentedImageMap.containsKey(augmentedImage)) {
                        AugmentedImageNode(
                                augmentedImage,
                                addControlToScene(controlRenderable, augmentedImage.createAnchor(augmentedImage.centerPose))
                        ).also { aiNode ->
                            augmentedImageMap[augmentedImage] = aiNode
                        }
                    }
                    augmentedImageMap[augmentedImage]?.controlNode?.worldPosition.let {
                        Timber.i("Tracking Image child node? ${it}")
                    }
                }
                STOPPED -> {
                    Timber.i("Image Lost ${augmentedImage.name}")
                    // gets rid of children's own children, hopefully.
                    augmentedImageMap[augmentedImage]?.controlNode?.let {
                        arFragment.arSceneView.scene.removeChild(it)
                    }
                    // Remove an image once tracking stops.
                    augmentedImageMap.remove(augmentedImage)
                }
                null -> throw IllegalStateException("Shouldn't be possible")
            }
        }
    }

    private fun addControlToScene(controlRenderable: ViewRenderable, anchor: Anchor): AnchorNode {
        val anchorNode = AnchorNode(anchor)
        anchorNode.setParent(arFragment.arSceneView.scene)
        Node().apply {
            setParent(anchorNode)
            renderable = controlRenderable
            localPosition = Vector3(0f, .04f, 0f)
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
        Timber.d("MotionEvent: ($x $y $z, $rx $ry $rz)")

        return true
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        Timber.d("KeyEvent: $event")
        return true
    }

}

