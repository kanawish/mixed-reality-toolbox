package com.kanawish.dd.robotcontroller

import android.app.Activity
import android.os.Bundle
import com.kanawish.socket.NetworkHelper
import javax.inject.Inject

class ServerTestActivity : Activity() {

    @Inject lateinit var helper:NetworkHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onResume() {
        super.onResume()
    }

    override fun onPause() {
        super.onPause()
    }

}
