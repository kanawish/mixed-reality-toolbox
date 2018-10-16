package com.kanawish.prototype

import android.app.Activity
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import com.kanawish.socket.NetworkServer
import com.kanawish.socket.PORT_BM
import com.kanawish.socket.logIpAddresses
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import kotlinx.android.synthetic.main.test_ui.imageView
import timber.log.Timber
import java.net.InetSocketAddress
import javax.inject.Inject


class CameraConsumerActivity : Activity() {

    @Inject
    lateinit var server: NetworkServer

    private val disposables = CompositeDisposable()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.test_ui)

        logIpAddresses()
    }

    override fun onResume() {
        super.onResume()

        disposables += server
            .receiveBitmaps(InetSocketAddress(SERVER_IP, PORT_BM))
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { bm ->
                imageView.setImageBitmap(bm)
            }
    }

    override fun onPause() {
        super.onPause()

        disposables.clear()
    }

}
