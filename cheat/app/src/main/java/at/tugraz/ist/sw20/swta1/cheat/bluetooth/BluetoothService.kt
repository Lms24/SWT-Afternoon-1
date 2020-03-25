package at.tugraz.ist.sw20.swta1.cheat.bluetooth

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log

class BluetoothService(private val adapter: BluetoothAdapter) {
    
    private var receiver : BroadcastReceiver? = null
    
    fun getPairedDevices() : List<BluetoothDevice> {
        return adapter.bondedDevices.toList()
    }
    
    fun discoverDevices(activity: Activity, onDeviceFound: (BluetoothDevice) -> Unit) {
        if(receiver != null) {
            activity.unregisterReceiver(receiver)
        }
        if(adapter.isDiscovering) {
            adapter.cancelDiscovery()
        }
        
        // Create a BroadcastReceiver for ACTION_FOUND.
        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val action: String? = intent.action
                when(action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        // Discovery has found a device. Get the BluetoothDevice
                        // object and its info from the Intent.
                        val device: BluetoothDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        Log.println(Log.DEBUG, "Found device", device.name)
                        onDeviceFound(device)
                    }
                }
            }
        }
        
        activity.registerReceiver(receiver, IntentFilter(BluetoothDevice.ACTION_FOUND))
        adapter.startDiscovery()
        Log.println(Log.DEBUG, "Bluetooth", "Start discovery")
    }
    
    fun stopDiscovery(activity: Activity) {
        adapter.cancelDiscovery()
        Log.println(Log.DEBUG, "Bluetooth", "Stop discovery")
        if(receiver != null) {
            activity.unregisterReceiver(receiver)
        }
    }
}