package com.kanawish.socket

import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.PublishSubject
import timber.log.Timber
import java.io.IOException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import javax.inject.Inject
import javax.inject.Singleton


private const val PORT = 60123

@Singleton
class NetworkHelper @Inject constructor() {

    class Server {
        val disposables = CompositeDisposable()

        // Run from async thread.
        fun test() {
            val clientSockets = PublishSubject.create<Socket>()

            // Emits client sockets
            Observable
                    .create<Socket> { e ->
                        var isStopped = false
                        try {
                            Timber.d("Starting server socket")
                            val serverSocket = ServerSocket(PORT)

                            e.setCancellable {
                                isStopped = true
                                try {
                                    serverSocket.close()
                                } catch (e: IOException) {
                                    Timber.e(e, "Error closing server.")
                                }
                            }

                            try {
                                while (!serverSocket.isClosed) e.onNext(serverSocket.accept())
                            } catch (e: Exception) {
                                if (isStopped) {
                                    Timber.d("Server stopped")
                                } else {
                                    Timber.e(e, "Socket accept exception caught. isStopped == $isStopped")
                                }
                            }

                        } catch (e: Exception) {
                            Timber.e(e, "Top level server exception caught.")
                        }
                    }
                    .subscribeOn(Schedulers.io())
                    .subscribe(clientSockets)

            disposables += clientSockets
                    .observeOn(Schedulers.io())
                    .doOnNext { s -> Timber.d("client socket ${s.inetAddress}") }
                    .map { s ->
                        ObjectInputStream(s.getInputStream()) to ObjectOutputStream(s.getOutputStream())
                    }
                    .subscribe(
                            { (inputStream, outputStream) ->
                                val name = inputStream.readObject() as String
                                outputStream.writeObject("Hello client $name")
                                inputStream.close()
                                outputStream.close()
                            },
                            { e -> Timber.e(e) }
                    )
        }

    }

    class Client {
        val serverAddress = "192.168.43.1"
        val serverPort = PORT


        fun sendTest(name:String) {
            Completable
                    .create {
                        val socket = Socket(serverAddress, serverPort)

                        val oos = ObjectOutputStream(socket.getOutputStream())
                        val ois = ObjectInputStream(socket.getInputStream())

                        oos.writeObject(name)
                        val response = ois.readObject() as String
                        Timber.d("got response: $response")

                        socket.close()
                    }
                    .subscribeOn(Schedulers.io())
                    .subscribe()
        }
    }

    private fun logIpAddresses() {
        try {
            val enumNetworkInterfaces = NetworkInterface.getNetworkInterfaces()
            enumNetworkInterfaces.toList().forEach { networkInterface ->
                networkInterface.inetAddresses.toList().forEach { address ->
                    if (address.isSiteLocalAddress) {
                        Timber.d("SiteLocalAddress: ${address.hostAddress}")
                    }
                }
            }
        } catch (e: SocketException) {
            Timber.d(e, "Caught a SocketException")
        }
    }
}