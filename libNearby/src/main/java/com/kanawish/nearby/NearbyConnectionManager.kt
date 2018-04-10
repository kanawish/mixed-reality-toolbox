package com.kanawish.nearby

import android.Manifest
import android.app.Application
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import timber.log.Timber
import javax.inject.Singleton

const val SERVICE_ID = "com.kanawish.nearby"

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

    sealed class State {
        object IDLE:State()
        object SEARCHING:State()
        object CONNECTED:State()
        object UNKNOWN:State()
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

    /**
     * The 'actionable' states are IDLE or UNKNOWN.
     *
     * If IDLE, you should call 'connect'
     * If UNKNOWN, you should call 'reset'
     *
     */
    fun state():State {
        if( discoveredEndpoints.isNotEmpty() || pendingConnections.isNotEmpty() || discovering || advertising ) {
            return State.SEARCHING
        } else if( establishedConnections.isNotEmpty() ) {
            return State.CONNECTED
        } else if( !connecting ) {
            return State.IDLE
        } else {
            return State.UNKNOWN
        }
    }

    fun autoConnect() {
        Timber.d("autoConnect() with state() == ${state()}")
        when( state() ) {
            State.IDLE -> connect()
            State.UNKNOWN -> {
                reset()
                connect()
            }
            else -> Timber.d("waiting on connection.")
        }
    }

    fun connect() {
        startDiscovery()
        startAdvertising()
    }

    /**
     * Once this is called, we should be back to IDLE, and be ready to attempt
     * a call to 'connect' once again.
     */
    fun reset() {
        stopAllEndpoints()
    }

    // "Private API"

    protected fun stopAllEndpoints() {
        connectionsClient.stopAllEndpoints()
        advertising = false
        discovering = false
        connecting = false

        discoveredEndpoints.clear()
        pendingConnections.clear()
        establishedConnections.clear()
    }

    private fun startAdvertising() {
        advertising = true
        val options = AdvertisingOptions.Builder().setStrategy(Strategy.P2P_STAR).build()
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

    private fun stopAdvertising() {
        advertising = false
        connectionsClient.stopAdvertising()
    }

    private fun startDiscovery() {
        discovering = true
        connectionsClient
                .startDiscovery(
                        SERVICE_ID,
                        discoveryCallback,
                        DiscoveryOptions.Builder().setStrategy(Strategy.P2P_STAR).build()
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
                    stopDiscovery() // TODO: Externalize
                    connectToEndpoint(it) // TODO: Externalize
                }
            }
        }

        override fun onEndpointLost(endpointId: String) = Timber.d("onEndpointLost($endpointId)")
    }

    private fun stopDiscovery() {
        discovering = false
        connectionsClient.stopDiscovery()
    }

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
                stopAdvertising() // TODO: Externalize
            } else {
                pendingConnections.remove(endpointId)
                if (establishedConnections.isEmpty()) {
                    startDiscovery() // TODO: Externalize
                }
            }
        }

        override fun onDisconnected(endpointId: String) {
            Timber.d("Disconnection from endpoint $endpointId")
            establishedConnections.remove(endpointId)
            // TODO: Should we restart discovery / etc?
        }
    }

    // Payload Handling

    private fun buildPayloadCallback(): PayloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            Timber.d("$endpointId received payload ${payload.id}")
        }

        override fun onPayloadTransferUpdate(endpointId: String, transferUpdate: PayloadTransferUpdate) {
            when (transferUpdate.status) {
                PayloadTransferUpdate.Status.IN_PROGRESS -> TODO()
                PayloadTransferUpdate.Status.CANCELED -> TODO()
                PayloadTransferUpdate.Status.FAILURE -> TODO()
                PayloadTransferUpdate.Status.SUCCESS -> TODO()
                else -> Timber.e("Unknown transferUpdate.Status")
            }
        }
    }

}
