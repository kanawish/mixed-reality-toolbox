package com.kanawish.joystick

import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.text.TextUtils
import timber.log.Timber
import java.lang.reflect.InvocationTargetException
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton


/**
 * Quick and dirty Kotlin port of NES connection manager
 * from https://github.com/zugaldia/android-robocar
 *
 * NOTE:
 * More of an autodetect service, so in our use case where we connect our joystick to the phone,
 * then send commands over WIFI, it's not as useful.
 *
 */
@Qualifier annotation class DefaultDeviceAddress

@Singleton
class ConnectionManager @Inject constructor(private val context: Application, @DefaultDeviceAddress val deviceAddress: String) {

    private val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

    init {
        if (bluetoothAdapter == null) {
            Timber.e("This device does not support Bluetooth.")
        } else if (!isEnabled()) {
            Timber.d("Bluetooth isn't enabled, enabling: %b", bluetoothAdapter.enable())
        }
    }

    fun isEnabled() = bluetoothAdapter.isEnabled

    fun pairedDevices(): Set<BluetoothDevice> = bluetoothAdapter.bondedDevices

    /**
     * Checks whether the device is already paired.
     */
    fun selectedDevice(): BluetoothDevice? {
        for (pairedDevice in pairedDevices()) {
            if (isSelectedDevice(pairedDevice.address)) {
                return pairedDevice
            }
        }
        return null
    }

    // Create a BroadcastReceiver for ACTION_FOUND.
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            // Discovery has found a device.
            if (BluetoothDevice.ACTION_FOUND == action) {
                // Get the BluetoothDevice object and its info from the Intent.
                val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                val bondState = device.bondState
                val foundName = device.name
                val foundAddress = device.address // MAC address
                Timber.d("Discovery has found a device: %d/%s/%s", bondState, foundName, foundAddress)
                if (this@ConnectionManager.isSelectedDevice(foundAddress)) {
                    this@ConnectionManager.createBond(device)
                } else {
                    Timber.d("Unknown device, skipping bond attempt.")
                }
            } else if (BluetoothDevice.ACTION_BOND_STATE_CHANGED == action) {
                val state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1)
                when (state) {
                    BluetoothDevice.BOND_NONE -> Timber.d("The remote device is not bonded.")
                    BluetoothDevice.BOND_BONDING -> Timber.d("Bonding is in progress with the remote device.")
                    BluetoothDevice.BOND_BONDED -> Timber.d("The remote device is bonded.")
                    else -> Timber.d("Unknown remote device bonding state.")
                }
            }
        }
    }

    fun startDiscovery(): Boolean {
        registerReceiver()
        return bluetoothAdapter!!.startDiscovery()
    }

    private fun registerReceiver() {
        // Register for broadcasts when a device is discovered.
        val filter = IntentFilter()
        filter.addAction(BluetoothDevice.ACTION_FOUND)
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        context.registerReceiver(receiver, filter)
    }

    fun cancelDiscovery() {
        bluetoothAdapter!!.cancelDiscovery()
        context.unregisterReceiver(receiver)
    }

    private fun isSelectedDevice(foundAddress: String): Boolean {
        // MAC address is set and recognized
        return !TextUtils.isEmpty(deviceAddress) && deviceAddress == foundAddress
    }

    /**
     * Pair with the specific device.
     */
    fun createBond(device: BluetoothDevice): Boolean {
        val result = device.createBond()
        Timber.d("Creating bond with: %s/%s/%b", device.name, device.address, result)
        return result
    }

    /**
     * Remove bond with the specific device.
     */
    fun removeBond(device: BluetoothDevice) {
        try {
            Timber.w("Removing bond.")
            val method = device.javaClass.getMethod("removeBond", null)
            method.invoke(device, null)
        } catch (e: NoSuchMethodException) {
            Timber.e(e, "Failed to remove bond.")
        } catch (e: IllegalAccessException) {
            Timber.e(e, "Failed to remove bond.")
        } catch (e: InvocationTargetException) {
            Timber.e(e, "Failed to remove bond.")
        }
    }

}