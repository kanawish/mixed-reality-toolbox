package com.kanawish.ar.robotremote

import com.google.ar.core.AugmentedImage
import com.google.ar.sceneform.AnchorNode

/**
 * In sample code, used to create a sceneform node tree.
 *
 * TODO: Clean this up
 */
class AugmentedImageNode(image: AugmentedImage, val controlNode:AnchorNode) : AnchorNode() {

    var image:AugmentedImage = image
        set(value) {
            anchor = value.createAnchor(value.centerPose)
            field = value
    }

}
