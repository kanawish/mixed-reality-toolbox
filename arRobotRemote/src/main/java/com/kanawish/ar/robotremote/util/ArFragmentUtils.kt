package com.kanawish.ar.robotremote.util

import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.Node
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.ModelRenderable
import com.google.ar.sceneform.rendering.ViewRenderable
import com.google.ar.sceneform.ux.ArFragment
import com.google.ar.sceneform.ux.TransformableNode

fun ArFragment.buildTapArPlaneListener(model: ModelRenderable, label: ViewRenderable) {
    setOnTapArPlaneListener { hitResult, plane, motionEvent ->
        // Create the Anchor and AnchorNode.
        val anchorNode = AnchorNode(hitResult.createAnchor())
        // Attach node to scene.
        anchorNode.setParent(arSceneView.scene)

        // Create the transformable andy and add it to the anchor.
        val andy = TransformableNode(transformationSystem)
        andy.setParent(anchorNode)
        andy.renderable = model
        andy.select()

        Node().apply {
            setParent(andy)
            renderable = label
            localPosition = Vector3(0f, .25f, 0f)
        }
    }
}

fun Double.format(digits: Int) = java.lang.String.format("%.${digits}f", this)
fun Float.format(digits: Int) = java.lang.String.format("%.${digits}f", this)