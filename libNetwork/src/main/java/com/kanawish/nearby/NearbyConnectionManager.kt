package com.kanawish.nearby

import android.Manifest
import android.app.Application
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import com.jakewharton.rxrelay2.PublishRelay
import com.kanawish.nearby.NearbyConnectionManager.ConnectionEvent.ConnectionResult
import com.kanawish.nearby.NearbyConnectionManager.ConnectionEvent.Disconnect
import io.reactivex.Observable
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import javax.inject.Singleton

const val SERVICE_ID = "com.kanawish.nearby"

const val DEFAULT_ENDPOINT = "defaultEndpoint"

val NEARBY_PERMISSIONS = arrayOf(
        Manifest.permission.BLUETOOTH,
        Manifest.permission.BLUETOOTH_ADMIN,
        Manifest.permission.ACCESS_WIFI_STATE,
        Manifest.permission.CHANGE_WIFI_STATE,
        Manifest.permission.ACCESS_COARSE_LOCATION)


/**
 * NOTE: We'll go for P2P_STAR for bandwidth.
 * TODO: Add support for more than 2 participants, currently we stop with first connection.
 */
@Singleton
class NearbyConnectionManager constructor(app: Application, val endpointName: String) {

    sealed class ConnectionEvent {
        data class ConnectionResult(val success: Boolean, val connectionCount: Int) : ConnectionEvent()
        data class Disconnect(val connectionCount: Int) : ConnectionEvent()
    }

    sealed class State {
        object IDLE : State()
        object SEARCHING : State()
        object CONNECTED : State()
        object UNKNOWN : State()
    }

    data class Endpoint(val id: String, val name: String)

    private val connectionsClient: ConnectionsClient = Nearby.getConnectionsClient(app)

    // Simple state attribute.
    private var connecting = false
    private var discovering = false
    private var advertising = false

    private val discoveredEndpoints = mutableMapOf<String, Endpoint>()
    private val pendingConnections = mutableMapOf<String, Endpoint>()
    private val establishedConnections = mutableMapOf<String, Endpoint>()

    // Public API

    // Connection handling with default no-op.
    private val connectionEventRelay: PublishRelay<ConnectionEvent> = PublishRelay.create<ConnectionEvent>()

    fun connectionEvents(): Observable<ConnectionEvent> = connectionEventRelay.hide()

    // Payload receiving  with default no-op.
    private val payloadRelay: PublishRelay<Payload> = PublishRelay.create<Payload>()
    private val baosRelay: PublishRelay<ByteArrayOutputStream> = PublishRelay.create<ByteArrayOutputStream>()

    fun outputStreams():Observable<ByteArrayOutputStream> = baosRelay.hide()
    fun receivedPayloads(): Observable<Payload> = payloadRelay.hide()

    /**
     * The 'actionable' states are IDLE or UNKNOWN.
     *
     * If IDLE, you should call 'connect'
     * If UNKNOWN, you should call 'stopAll'
     *
     */
    fun state(): State {
        if (discoveredEndpoints.isNotEmpty() || pendingConnections.isNotEmpty() || discovering || advertising) {
            return State.SEARCHING
        } else if (establishedConnections.isNotEmpty()) {
            return State.CONNECTED
        } else if (!connecting) {
            return State.IDLE
        } else {
            return State.UNKNOWN
        }
    }

    fun autoAdvertise() {
        Timber.d("autoAdvertise() with state() == ${state()}")
        when (state()) {
            State.IDLE -> startAdvertising()
            State.UNKNOWN -> {
                stopAllEndpoints()
                startAdvertising()
            }
            else -> Timber.d("waiting on connection.")
        }
    }

    fun autoDiscover() {
        Timber.d("autoDiscover() with state() == ${state()}")
        when (state()) {
            State.IDLE -> startDiscovery()
            State.UNKNOWN -> {
                stopAllEndpoints()
                startDiscovery()
            }
            else -> Timber.d("waiting on connection.")
        }
    }

    fun stopDiscovering() {
        discovering = false
        connectionsClient.stopDiscovery()
    }

    fun stopAdvertising() {
        advertising = false
        connectionsClient.stopAdvertising()
    }

    /**
     * Once this is called, we should be back to IDLE, and be ready to attempt
     * a call to 'connect' once again.
     */
    fun stopAllEndpoints() {
        connectionsClient.stopAllEndpoints()

        advertising = false
        discovering = false
        connecting = false

        discoveredEndpoints.clear()
        pendingConnections.clear()
        establishedConnections.clear()
    }


    // "Private API"

    private fun startAdvertising() {
        advertising = true
        val options = AdvertisingOptions(Strategy.P2P_CLUSTER)
        connectionsClient
                .startAdvertising(
                        endpointName,
                        SERVICE_ID,
                        connectionCallback,
                        options
                )
                .addOnSuccessListener { Timber.d("Now advertising $endpointName") }
                .addOnFailureListener {
                    advertising = false
                    Timber.d("Failed to advertise $endpointName")
                    Timber.e(it)
                }
    }


    private fun startDiscovery() {
        discovering = true
        connectionsClient
                .startDiscovery(
                        SERVICE_ID,
                        discoveryCallback,
                        DiscoveryOptions(Strategy.P2P_CLUSTER)
                )
                .addOnSuccessListener { Timber.d("Now discovering") }
                .addOnFailureListener {
                    discovering = false
                    Timber.d("Failed to discover.")
                    Timber.e(it)
                }
    }

    private val discoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            Timber.d("onEndpointFound(endpointId=$endpointId, serviceId=${info.serviceId}, endpointName=${info.endpointName})")
            if (info.serviceId == SERVICE_ID) {
                Endpoint(endpointId, info.endpointName).also {
                    discoveredEndpoints[it.id] = it
                    discovering = false
                    connectionsClient.stopDiscovery()

                    connectToEndpoint(it) // TODO: Externalize
                }
            }
        }

        // NOTE: Currently, only request a connection from discovery callback.
        private fun connectToEndpoint(endpoint: Endpoint) {
            connecting = true
            connectionsClient
                    .requestConnection(endpointName, endpoint.id, connectionCallback)
                    .addOnFailureListener {
                        Timber.d(it, "requestConnection() failed.")
                        connecting = false
                        startDiscovery() // TODO: Externalize
                    }
        }

        override fun onEndpointLost(endpointId: String) = Timber.d("onEndpointLost($endpointId)")
    }

    private val connectionCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            Timber.d("onConnectionInitiated($endpointId,${info.endpointName})")
            Endpoint(endpointId, info.endpointName).also {
                pendingConnections[it.id] = it
                connectionsClient.acceptConnection(endpointId, buildPayloadCallback()) // TODO: Externalize
            }
        }

        override fun onConnectionResult(endpointId: String, resolution: ConnectionResolution) {
            Timber.d("onConnectionResult($endpointId,${resolution.status})")
            connecting = false

            if (resolution.status.isSuccess) {
                Timber.d("success with $endpointId")
                pendingConnections.remove(endpointId)?.also {
                    establishedConnections[it.id] = it
                }

            } else {
                pendingConnections.remove(endpointId)
            }

            // Subscribers can decide what's next.
            connectionEventRelay.accept(
                    ConnectionResult(resolution.status.isSuccess, establishedConnections.size))
        }

        override fun onDisconnected(endpointId: String) {
            Timber.d("Disconnection from endpoint $endpointId")
            establishedConnections.remove(endpointId)

            // Handler can decide what's next.
            connectionEventRelay.accept(
                    Disconnect(establishedConnections.size))
        }
    }

    // Payload Handling
    private val incomingPayloads = mutableMapOf<Long, Payload>()
    private val incomingStreams = mutableMapOf<Long, ByteArrayOutputStream>()

    private fun buildPayloadCallback(): PayloadCallback = object : PayloadCallback() {

        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            Timber.d("onPayloadReceived($endpointId,${payload.id}")
            when (payload.type) {
                Payload.Type.BYTES -> incomingPayloads[payload.id] = payload
                Payload.Type.FILE -> incomingPayloads[payload.id] = payload
                Payload.Type.STREAM -> {
                    incomingPayloads[payload.id] = payload

                    payload.asStream()?.asInputStream()?.let {
                        incomingStreams[payload.id] = ByteArrayOutputStream()
                    }

                }
            }
        }

        // TODO: Test & validate
        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            when (update.status) {
                PayloadTransferUpdate.Status.IN_PROGRESS -> {
                    Timber.d("onPayloadTransferUpdate() IN_PROGRESS = id:${update.payloadId} ${update.bytesTransferred}/${update.totalBytes} bytes")
                    incomingPayloads[update.payloadId]?.let {
                        if (it.type == Payload.Type.STREAM) {
                            it.asStream()?.let {
                                it.asInputStream().let { inputStream ->
                                    val avail = inputStream.available()
                                    Timber.d("read ${inputStream.available()} bytes")
                                    incomingStreams[update.payloadId]
                                            ?.write(inputStream.readBytes(avail))
                                }
                            }
                        }
                    } ?: Timber.d("NOOP - We're sending.")
                }
                PayloadTransferUpdate.Status.FAILURE -> {
                    Timber.d("onPayloadTransferUpdate() FAILURE = id:${update.payloadId} ${update.bytesTransferred}/${update.totalBytes} bytes")
                    incomingPayloads.remove(update.payloadId)
                }
                PayloadTransferUpdate.Status.SUCCESS -> {
                    Timber.d("onPayloadTransferUpdate() SUCCESS = id:${update.payloadId} ${update.bytesTransferred}/${update.totalBytes} bytes")
                    incomingPayloads.remove(update.payloadId)?.let { payload ->
                        when (payload.type) {
                            Payload.Type.BYTES -> Timber.d("Received ByteArray Payload: ${payload.asBytes()?.size} bytes.")
                            Payload.Type.FILE -> Timber.d("Received File Payload: canonical = '${payload.asFile()?.asJavaFile()?.canonicalFile}'")
                            Payload.Type.STREAM -> {
                                Timber.d("Received Stream Payload: available() = ${payload.asStream()?.asInputStream()?.available()} bytes.")

                            }
                        }
                        // Lets receivers handle received payloads as they see fit.
                        payloadRelay.accept(payload)
                        baosRelay.accept(incomingStreams.remove(payload.id))
                    } ?: Timber.d("NOOP - We're sending.")
                }
                else -> Timber.e("onPayloadTransferUpdate() unknown.")
            }
        }
    }

    // TODO: Test & validate

    /**
     * Send payload on all establishedConnections.
     *
     * @param byteArray The bytes to be sent.
     */
    fun send(byteArray: ByteArray) {
        if (establishedConnections.isNotEmpty()) {
            connectionsClient.sendPayload(
                    ArrayList(establishedConnections.keys),
                    Payload.fromBytes(byteArray)
            )
        }
    }

    /**
     * Send payload on all establishedConnections.
     *
     * @param file The file to be sent.
     */
    fun send(file: File) {
        if (establishedConnections.isNotEmpty()) {
            connectionsClient.sendPayload(
                    ArrayList(establishedConnections.keys),
                    Payload.fromFile(file)
            )
        }
    }

    /**
     * Send payload on all establishedConnections.
     *
     * @param inputStream The input stream to be sent.
     */
    fun send(inputStream: InputStream) {
        if (establishedConnections.isNotEmpty()) {
            connectionsClient.sendPayload(
                    ArrayList(establishedConnections.keys),
                    Payload.fromStream(inputStream)
            )
        }
    }


}
