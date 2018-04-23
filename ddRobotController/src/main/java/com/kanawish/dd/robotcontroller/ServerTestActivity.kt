package com.kanawish.dd.robotcontroller

import android.app.Activity
import android.os.Bundle
import com.kanawish.socket.NetworkServer
import com.kanawish.socket.PORT_BM
import com.kanawish.socket.logIpAddresses
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import kotlinx.android.synthetic.main.test_ui.*
import timber.log.Timber
import java.net.InetSocketAddress
import javax.inject.Inject

class ServerTestActivity : Activity() {

    @Inject lateinit var server: NetworkServer

    private val disposables = CompositeDisposable()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.test_ui)

        logIpAddresses()
    }

    override fun onResume() {
        super.onResume()

/*
        disposables += server
                .receiveCommand()
                .doOnNext { Timber.d("Command: $it") }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe( { cmd -> line1TextView.text = cmd.toString() } )
*/

        disposables += server
                .receiveBitmaps(InetSocketAddress("192.168.232.2", PORT_BM))
                .doOnNext { Timber.d("server processed image?") }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe( {
                    bm -> imageView.setImageBitmap(bm)
                } )
    }

    override fun onPause() {
        super.onPause()

        disposables.clear()
    }

}
