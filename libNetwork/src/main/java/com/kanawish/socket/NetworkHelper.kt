package com.kanawish.socket

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.kanawish.robot.Command
import com.kanawish.robot.Telemetry
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.PublishSubject
import timber.log.Timber
import java.io.IOException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.net.*
import javax.inject.Inject
import javax.inject.Singleton


const val HOST_PHONE_ADDRESS = "192.168.43.60" // [for RobotActivity] Pixel 2 Remote on ATR
//const val HOST_PHONE_ADDRESS = "192.168.43.1"    // [for RobotActivity] Nexus 5 Remote on ATR
const val HOST_P2_ADDRESS = "192.168.43.60" // [for ARRemoteActivity] Pixel 2 on ATR
const val ROBOT_ADDRESS = "192.168.43.220" // Robot on ATR

const val PORT_CMD = 60123
const val PORT_TM = 60124
const val PORT_BM = 60125


fun ByteArray.toBitmap(): Bitmap {
    return BitmapFactory.decodeByteArray(this, 0, this.size)
}

// NOTE: NetworkServer and NetworkClient currently don't really hold state.
// TODO: Consider only using top level functions instead of singleton.

@Singleton
class NetworkServer @Inject constructor() {

    private val errorHandler = { t: Throwable -> Timber.e(t) }

    fun receiveCommand(inetSocketAddress: InetSocketAddress = InetSocketAddress(PORT_CMD)): Observable<Command> {
        // Server listens for clients, emits a socket object for each incoming connection.
        val clientSockets = PublishSubject.create<Socket>()
        val disposable = serverSocketAcceptor(inetSocketAddress).subscribe(clientSockets::onNext)

        // We expect one ByteArray object, convert it to bitmap.
        return clientSockets.clientSocketProcessor()
                .map { (input, output) ->
                    val command = input.readObject() as Command

                    input.close()
                    output.close()

                    command
                }
                .doOnDispose { disposable.dispose() }
    }

    fun receiveTelemetry(inetSocketAddress: InetSocketAddress = InetSocketAddress(PORT_TM)): Observable<Telemetry> {
        // Server listens for clients, emits a socket object for each incoming connection.
        val clientSockets = PublishSubject.create<Socket>()
        val disposable = serverSocketAcceptor(inetSocketAddress).subscribe(clientSockets::onNext)

        // We expect one ByteArray object, convert it to bitmap.
        return clientSockets.clientSocketProcessor()
                .map { (input, output) ->

                    // Iffy, but then all this network code is iffy. ^_^
                    val telemetry = input.readObject() as Telemetry
                    Timber.d("Server received Telemetry:\nDistance:\t${telemetry.distance}\nBitmap:\t${telemetry.image.size} bytes")

                    input.close()
                    output.close()

                    telemetry
                }
                .doOnDispose { disposable.dispose() }
    }

    // Very quick and dirty.
    fun receiveBitmaps(inetSocketAddress: InetSocketAddress = InetSocketAddress(PORT_BM)): Observable<Bitmap> {
        // Server listens for clients, emits a socket object for each incoming connection.
        val clientSockets = PublishSubject.create<Socket>()
        val disposable = serverSocketAcceptor(inetSocketAddress).subscribe(clientSockets::onNext)

        // We expect one ByteArray object, convert it to bitmap.
        return clientSockets.clientSocketProcessor()
                .map { (input, output) ->
                    val bytes = input.readObject() as ByteArray
                    input.close()
                    output.close()

                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }
                .doOnDispose { disposable.dispose() }
    }

    private fun serverSocketAcceptor(inetSocketAddress: InetSocketAddress): Observable<Socket> {
        return Observable
                .create<Socket> { e ->
                    var isStopped = false
                    try {
                        Timber.d("Starting server socket")
                        val serverSocket = ServerSocket()
                        serverSocket.reuseAddress = true
                        serverSocket.bind(inetSocketAddress)

                        e.setCancellable {
                            Timber.d("Trying to close on cancel/dispose.")
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
                        } finally {
                            serverSocket.close()
                        }

                    } catch (t: Exception) {
                        Timber.e(t, "Top level server exception caught.")
                    }
                }
                .subscribeOn(Schedulers.io())
    }

    private fun PublishSubject<Socket>.clientSocketProcessor(): Observable<Pair<ObjectInputStream, ObjectOutputStream>> {
        return this
                .observeOn(Schedulers.io())
//                .doOnNext { s -> Timber.d("client socket ${s.inetAddress}") }
                .map { s -> ObjectInputStream(s.getInputStream()) to ObjectOutputStream(s.getOutputStream()) }
    }

}

@Singleton
class NetworkClient @Inject constructor() {

    fun sendCommand(serverAddress: String, command: Command): Disposable {
        Timber.i("$command -> $serverAddress")
        return Completable
                .create {
                    val socket = Socket(serverAddress, PORT_CMD)

                    val oos = ObjectOutputStream(socket.getOutputStream())
                    val ois = ObjectInputStream(socket.getInputStream())

                    oos.writeObject(command)
                    oos.flush()

                    socket.close()
                }
                .subscribeOn(Schedulers.io())
                .subscribe({}, { t -> Timber.e(t, "Caught exception for this command upload.") })
    }

    fun sendTelemetry(serverAddress: String, telemetry: Telemetry): Disposable {
        return Completable
                .create {
                    val socket = Socket(serverAddress, PORT_TM)

                    val oos = ObjectOutputStream(socket.getOutputStream())
                    val ois = ObjectInputStream(socket.getInputStream())

                    oos.writeObject(telemetry)
                    oos.flush()

                    socket.close()
                }
                .subscribeOn(Schedulers.io())
                .subscribe({}, { t -> Timber.e(t, "Caught exception for this telemetry upload.") })
    }

    fun sendImageData(serverAddress: String, byteArray: ByteArray): Disposable {
        return Completable
                .create {
                    val socket = Socket(serverAddress, PORT_BM)

                    val oos = ObjectOutputStream(socket.getOutputStream())
                    val ois = ObjectInputStream(socket.getInputStream())

                    oos.writeObject(byteArray)
                    oos.flush()

                    socket.close()
                }
                .subscribeOn(Schedulers.io())
                .subscribe({}, { t -> Timber.e(t, "Caught exception for this image upload.") })
    }

}

fun logIpAddresses() {
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
