package com.kanawish.ar.robotremote

import android.annotation.SuppressLint
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import com.google.ar.core.Anchor
import com.google.ar.core.AugmentedImage
import com.google.ar.core.Frame
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import com.google.ar.core.TrackingState.PAUSED
import com.google.ar.core.TrackingState.STOPPED
import com.google.ar.core.TrackingState.TRACKING
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.FrameTime
import com.google.ar.sceneform.Node
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.DpToMetersViewSizer
import com.google.ar.sceneform.rendering.ModelRenderable
import com.google.ar.sceneform.rendering.ViewRenderable
import com.google.ar.sceneform.ux.ArFragment
import com.google.ar.sceneform.ux.TransformableNode
import com.kanawish.ar.robotremote.util.checkIsSupportedDeviceOrFinish
import com.kanawish.robot.Command
import com.kanawish.socket.NetworkClient
import com.kanawish.socket.NetworkServer
import com.kanawish.socket.ROBOT_ADDRESS
import com.kanawish.socket.toBitmap
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import kotlinx.android.synthetic.main.remote_control_view.view.backwardButton
import kotlinx.android.synthetic.main.remote_control_view.view.forwardButton
import kotlinx.android.synthetic.main.remote_control_view.view.imageView
import kotlinx.android.synthetic.main.remote_control_view.view.leftButton
import kotlinx.android.synthetic.main.remote_control_view.view.rightButton
import timber.log.Timber
import java.util.concurrent.CompletableFuture
import javax.inject.Inject

/**
 */
class ArMainActivity : AppCompatActivity() {
    data class Renderables(
            val controlRenderable: ViewRenderable,
            val labelRenderable: ViewRenderable,
            val andyRenderable: ModelRenderable
    ) {
        val controlView: View get() = controlRenderable.view
    }

    @Inject lateinit var server: NetworkServer
    @Inject lateinit var client: NetworkClient

    private val disposables = CompositeDisposable()

    private lateinit var arFragment: ArFragment
    private lateinit var renderables: Renderables

    private val augmentedImageMap = HashMap<AugmentedImage, AugmentedImageNode>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!checkIsSupportedDeviceOrFinish()) {
            return
        }

        setContentView(R.layout.activity_ux)
        arFragment = supportFragmentManager.findFragmentById(R.id.ux_fragment) as ArFragment

        initRenderables()

        // Plane tap handling
        arFragment.setOnTapArPlaneListener(this::onTapArPlaneListener)

        // Scene update frame handling
        arFragment.arSceneView.scene.addOnUpdateListener { frameTime ->
            // Only process frames when needed.
            arFragment.arSceneView.arFrame?.let { frame ->
                if (frame.camera.trackingState == TRACKING) {
                    onUpdateFrame(frame, frameTime)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        disposables += server
                .receiveTelemetry()
                .doOnNext { Timber.d("Telemetry(${it.distance}cm, ${it.image.size} bytes)") }
                .map { it.distance to it.image.toBitmap() }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { (_, bitmap) ->
                    if( ::renderables.isInitialized ) renderables.controlView.imageView.setImageBitmap(bitmap)
                }
    }

    override fun onPause() {
        super.onPause()
        disposables.clear()
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

    @SuppressLint("NewApi")
    private fun initRenderables() {
        val controlsFuture = ViewRenderable.builder().setView(this, R.layout.remote_control_view).build()
        val labelFuture = ViewRenderable.builder().setView(this, R.layout.info_card_view).build()
        val andyFuture = ModelRenderable.builder().setSource(this, R.raw.andy).build()

        CompletableFuture.allOf(controlsFuture, andyFuture).handle { x, throwable ->
            if (throwable != null) {
                throw IllegalStateException(throwable)
            }

            renderables = Renderables(
                    controlsFuture.get().apply {
                        sizer = DpToMetersViewSizer(1000)
                    },
                    labelFuture.get(),
                    andyFuture.get()
            )

            bindRenderables()
        }
    }

    private fun bindRenderables() {
        var d = 1L
        fun send(command: Command) = client.sendCommand(ROBOT_ADDRESS, command)
        renderables.controlView.forwardButton.setOnClickListener { send(Command(d, -128, -128)) }
        renderables.controlView.rightButton.setOnClickListener { send(Command(d, 128, -128)) }
        renderables.controlView.backwardButton.setOnClickListener { send(Command(d, 128, 128)) }
        renderables.controlView.leftButton.setOnClickListener { send(Command(d, -128, 128)) }
    }

    private fun onTapArPlaneListener(hitResult: HitResult, plane: Plane, motionEvent: MotionEvent) {
        if (!::renderables.isInitialized) {
            return
        }

        // Create the Anchor.
        val anchor = hitResult.createAnchor()
        val anchorNode = AnchorNode(anchor)
        anchorNode.setParent(arFragment.arSceneView.scene)

        // Create the transformable andy and add it to the anchor.
        val andy = TransformableNode(arFragment.transformationSystem)
        andy.setParent(anchorNode)
        andy.renderable = renderables.andyRenderable
        andy.select()

        Node().apply {
            setParent(andy)
            renderable = renderables.labelRenderable
            localPosition = Vector3(0f, .25f, 0f)
        }
    }

    private fun addControlToScene(anchor: Anchor): AnchorNode {
        val anchorNode = AnchorNode(anchor)
        anchorNode.setParent(arFragment.arSceneView.scene)
        Node().apply {
            setParent(anchorNode)
            renderable = renderables.controlRenderable
            localPosition = Vector3(0f, .04f, 0f)
        }
        return anchorNode
    }

    private fun onUpdateFrame(frame: Frame, frameTime: FrameTime) {
        for (augmentedImage in frame.getUpdatedTrackables(AugmentedImage::class.java)) {
            when (augmentedImage.trackingState) {
                PAUSED -> Timber.i("Detected Image ${augmentedImage.name} / ${augmentedImage.index}")
                TRACKING -> {
                    Timber.i("Tracking Image ${augmentedImage.name} / ${augmentedImage.centerPose}")
                    // Add an image once tracking starts
                    if (!augmentedImageMap.containsKey(augmentedImage)) {
                        AugmentedImageNode(augmentedImage, addControlToScene(augmentedImage.createAnchor(augmentedImage.centerPose)))
                                .also { aiNode -> augmentedImageMap[augmentedImage] = aiNode }
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

}

