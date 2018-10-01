package com.kanawish.ar.robotremote

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.ar.core.AugmentedImageDatabase
import com.google.ar.core.Config
import com.google.ar.core.Session
import com.google.ar.sceneform.ux.ArFragment

class ArRemoteFragment : ArFragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return super.onCreateView(inflater, container, savedInstanceState)
        // TODO: Turn on/off certain features from this block
    }

    /**
     * Load up augmented image database, assign it to session config.
     */
    override fun getSessionConfiguration(session: Session?): Config {
        return super.getSessionConfiguration(session).also { config ->
            val augmentedImageDatabase = context?.assets?.open("hiro.imgdb")
                    .let { inputStream ->
                        AugmentedImageDatabase.deserialize(session, inputStream)
                    }
            config.augmentedImageDatabase = augmentedImageDatabase
            // Likely will cause issues with quality but makes small 'hiro' trackable.
            config.focusMode = Config.FocusMode.AUTO
        }
    }

}