package com.kanawish.ar.robotremote

import android.annotation.SuppressLint
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import com.google.ar.core.AugmentedImage
import com.google.ar.core.Frame
import com.google.ar.core.TrackingState
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.FrameTime
import com.google.ar.sceneform.Node
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.ModelRenderable
import com.google.ar.sceneform.rendering.Renderable
import com.google.ar.sceneform.ux.ArFragment
import com.google.ar.sceneform.ux.TransformableNode
import com.kanawish.ar.robotremote.util.checkIsSupportedDeviceOrFinish
import com.kanawish.ar.robotremote.util.modelRenderable
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.Singles
import io.reactivex.rxkotlin.plusAssign
import timber.log.Timber
import java.util.*

@SuppressLint("SetTextI18n")
class ArHelloActivity : AppCompatActivity() {
    private val disposables = CompositeDisposable()

//    private lateinit var arFragment: ArFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!checkIsSupportedDeviceOrFinish()) return

        setContentView(R.layout.activity_ux)

        val arFragment = supportFragmentManager.findFragmentById(R.id.ux_fragment) as ArFragment

        disposables += Singles
                .zip(modelRenderable(R.raw.solar_system), modelRenderable(R.raw.curiosity))
                .subscribe { (solarSystem, curiosity) ->
                    arFragment.bindScenery(solarSystem)
                    arFragment.onTrackingUpdate { frame, frameTime ->
                        arFragment.onUpdateFrame(frame, frameTime, curiosity)
                    }
                }
    }

    val newMap = HashMap<AugmentedImage, AnchorNode>()
    private val augmentedImageMap = HashMap<AugmentedImage, AugmentedImageNode>()
    private fun ArFragment.onUpdateFrame(frame: Frame, frameTime: FrameTime, curiosity: ModelRenderable) {
        for (augmentedImage in frame.getUpdatedTrackables(AugmentedImage::class.java)) {
            when (augmentedImage.trackingState) {
                TrackingState.TRACKING -> {
                    Timber.d("Tracking Image ${augmentedImage.name} / ${augmentedImage.centerPose}")
                    // Add an image once tracking starts
                    if (!newMap.contains(augmentedImage)) {
                        newMap[augmentedImage] = addRenderableToScene(augmentedImage,curiosity)
                    } else {
                        newMap[augmentedImage]?.worldPosition.let {
                            Timber.d("Tracking Image child node? $it")
                        }
                    }
                }
                TrackingState.PAUSED -> Timber.d("Detected Image ${augmentedImage.name} / ${augmentedImage.index}")
                TrackingState.STOPPED -> {
                    // gets rid of children's own children, hopefully.
                    augmentedImageMap[augmentedImage]?.controlNode?.let {
                        arSceneView.scene.removeChild(it)
                    }
                    // Remove an image once tracking stops.
                    augmentedImageMap.remove(augmentedImage)
                }
                else -> Timber.d("Got null tracking state?")
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // NOTE: Explain how this insures we don't trigger out-of-bound handling, as long as threading is good.
        disposables.clear()
    }

    private var sceneAnchorNode: AnchorNode? = null
    private fun ArFragment.bindScenery(scenery: ModelRenderable) {
        setOnTapArPlaneListener { hitResult, plane, motionEvent ->
            // Clean up the old marker, only have one at any time.
            sceneAnchorNode?.let { oldNode -> arSceneView.scene.removeChild(oldNode) }
            // Create a new anchor
            sceneAnchorNode = AnchorNode(hitResult.createAnchor()).apply {
                setParent(arSceneView.scene)
            }
            // Creates transformable, hooks it too sceneAnchorNode
            TransformableNode(transformationSystem).apply {
                setParent(sceneAnchorNode)
                renderable = scenery
                select()
            }
        }
    }

}

private fun ArFragment.addRenderableToScene(augmentedImage: AugmentedImage, genericRenderable: Renderable): AnchorNode {
    val pose = augmentedImage.centerPose
    val anchorNode = AnchorNode(augmentedImage.createAnchor(pose))
    anchorNode.setParent(arSceneView.scene)
    Timber.d("AugmentedImage ${anchorNode.worldPosition} / ${anchorNode.localPosition}")

    Node().apply {
        setParent(anchorNode)
        renderable = genericRenderable
        localScale = Vector3(.5f,.5f,.5f)
        Timber.d("Controls $worldPosition / $localPosition")
    }

    return anchorNode
}


fun ArFragment.onTrackingUpdate(frameHandler: (Frame,FrameTime) -> Unit) {
    arSceneView.scene.addOnUpdateListener { frameTime ->
        // Only process frames when needed.
        arSceneView.arFrame?.let { frame ->
            if (frame.camera.trackingState == TrackingState.TRACKING) {
                frameHandler(frame,frameTime)
            }
        }
    }
}

