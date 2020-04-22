package at.tugraz.ist.sw20.swta1.cheat.bluetooth

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.util.Log
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.*

class BluetoothService(private val adapter: BluetoothAdapter) {
    private val tag = "BluetoothService"
    private val connectionTag = "$tag/Connection"
    private var receiver: BroadcastReceiver? = null
    private var initConnection: InitConnection? = null
    private var acceptConnection: AcceptConnection? = null
    private var currentConnection: CurrentConnection? = null
    private var onStateChange: (BluetoothState) -> Unit = {}
    private var onMessageReceive: (ByteArray, Int) -> Any = { _: ByteArray, _: Int -> }
    
    val uuid = UUID.fromString("871dc78d-b4c1-4bf4-81f1-52af98e32350")
    var state: BluetoothState = BluetoothState.DISABLED
    
    fun getPairedDevices() : List<RealBluetoothDevice> {
        return adapter.bondedDevices.map { device -> RealBluetoothDevice(device) }.toList()
    }
    
    fun setOnStateChangeListener(onStateChange: (BluetoothState) -> Unit) {
        this.onStateChange = onStateChange
    }
    
    fun setOnMessageReceive(onMessageReceive: (ByteArray, Int) -> Any) {
        this.onMessageReceive = onMessageReceive
    }
    
    private fun updateState(state: BluetoothState) {
        Log.i(tag, "State changed to '$state', was '${this.state}'")
        this.state = state
        onStateChange(state)
    }
    
    fun setup() {
        updateState(BluetoothState.READY)
        acceptConnection = AcceptConnection()
        acceptConnection!!.start()
    }
    
    fun discoverDevices(activity: Activity, onDeviceFound: (BluetoothDevice) -> Unit, onDiscoveryStopped: () -> Unit) {
        if(state != BluetoothState.READY && receiver != null) {
            activity.unregisterReceiver(receiver)
        }
        if (adapter.isDiscovering) {
            adapter.cancelDiscovery()
        }
        
        if (activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            activity.requestPermissions(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                1 /* <- is for identifying this request, random number */
            )
            
            return
        }
        
        // Create a BroadcastReceiver for ACTION_FOUND.
        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val action: String? = intent.action
                when (action) {
                    BluetoothDevice.ACTION_FOUND -> {
                        // Discovery has found a device. Get the BluetoothDevice
                        // object and its info from the Intent.
                        val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                        if(device?.name != null) {
                            Log.println(Log.DEBUG, "Found device", device.name)
                            onDeviceFound(device)
                        }
                    }
                }
            }
        }

        val finishedReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                Log.println(Log.DEBUG, "Bluetooth", "Discovery stopped")
                onDiscoveryStopped()
            }
        }
        
        activity.registerReceiver(receiver, IntentFilter(BluetoothDevice.ACTION_FOUND))
        activity.registerReceiver(finishedReceiver, IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED))
        if(!adapter.startDiscovery()) {
            Log.println(Log.ERROR, tag, "Error starting discovery")
        }
        Log.println(Log.DEBUG, tag, "Start discovery")
    }
    
    fun stopDiscovery(activity: Activity) {
        adapter.cancelDiscovery()
        updateState(BluetoothState.READY)
        Log.println(Log.DEBUG, tag, "Stop discovery")
        if (receiver != null) {
            activity.unregisterReceiver(receiver)
        }
    }
    
    fun connectToDevice(activity: Activity, device: IBluetoothDevice): Boolean {
        stopDiscovery(activity)
        if (state != BluetoothState.READY) {
            return false
        }
        //updateState(BluetoothState.CONNECTING)
        synchronized(this) {
            initConnection?.cancel()
            initConnection = InitConnection(device)
            initConnection!!.start()
        }
        return true
    }
    
    fun sendMessage(message: ByteArray) {
        if (state != BluetoothState.CONNECTED || currentConnection == null) {
            return
        }
        
        try {
            currentConnection?.outStream?.write(message)
        } catch (e: IOException) {
            Log.e(connectionTag, "Error sending message", e)
            // TODO disconnect
        }
    }
    
    @Synchronized
    private fun connect(device: IBluetoothDevice, socket: BluetoothSocket) {
        if (state != BluetoothState.CONNECTING) {
            return
        }
        if (initConnection != null) {
            initConnection?.cancel()
            initConnection = null
        }
        
        if (acceptConnection != null) {
            acceptConnection?.cancel()
            acceptConnection = null
        }
        
        if (currentConnection != null) {
            currentConnection?.cancel()
            currentConnection = null
        }
        Log.i(connectionTag, "Connecting to " + device.name + "...")
        currentConnection = CurrentConnection(device, socket)
        currentConnection?.start()
    }
    
    inner class InitConnection(private val target: IBluetoothDevice) : Thread() {
        private var targetSocket: BluetoothSocket?
        
        init {
            try {
                targetSocket = target.createSocket(uuid)
            } catch (e: IOException) {
                targetSocket = null
                updateState(BluetoothState.READY)
                Log.e(connectionTag, "Failed to create socket to " + target.name)
            }
        }
        
        override fun run() {
            Log.i(connectionTag, "Beginning connection")
            if (this@BluetoothService.state != BluetoothState.READY) {
                Log.e(connectionTag, "Unexpected state: " + this@BluetoothService.state)
                return
            }
            if (targetSocket == null) {
                Log.e(connectionTag, "Failed to create socket to " + target.name)
                return
            }
            try {
                targetSocket!!.connect()
            } catch (e: IOException) { // Close the socket
                Log.e(connectionTag, "Failed to connect, closing socket")
                try {
                    targetSocket!!.close()
                } catch (e2: IOException) {
                    Log.e(connectionTag, "Unable to close connection during connection failure")
                }
                return
            }
            Log.i(connectionTag, "Socket connection established")
            synchronized(this@BluetoothService) {
                initConnection = null
            }
            updateState(BluetoothState.CONNECTING)
            connect(target, targetSocket!!)
        }
        
        fun cancel() {
            Log.i(connectionTag, "Cancelling InitConnection")
            try {
                targetSocket?.close()
            } catch (e: IOException) {
            }
        }
    }
    
    inner class AcceptConnection() : Thread() {
        private var serverSocket: BluetoothServerSocket?
        
        init {
            Log.d(connectionTag, "Starting accepting thread")
            try {
                serverSocket = adapter.listenUsingRfcommWithServiceRecord(adapter.name, uuid)
                Log.d(connectionTag, "Server socket opened")
            } catch (e: IOException) {
                serverSocket = null
                updateState(BluetoothState.READY)
                Log.println(Log.ERROR, connectionTag, "Failed to create server socket")
            }
        }
        
        override fun run() {
            Log.i(connectionTag, "Listening for incoming connections")
            if (this@BluetoothService.state != BluetoothState.READY) {
                Log.e(connectionTag, "Unexpected state: " + this@BluetoothService.state)
                return
            }
            if (serverSocket == null) {
                Log.e(connectionTag, "Failed to create server socket")
                return
            }
            var clientSocket: BluetoothSocket? = null
            
            while (this@BluetoothService.state == BluetoothState.READY) {
                try {
                    clientSocket = serverSocket!!.accept()
                } catch (e: IOException) { // Close the socket
                    Log.e(connectionTag, "AcceptConnection server socket failed")
                    clientSocket = null
                }
                if (clientSocket != null) {
                    Log.i(connectionTag, "Incoming socket connection established")
                    var ready = true
                    synchronized(this@BluetoothService) {
                        if (this@BluetoothService.state != BluetoothState.READY) {
                            Log.e(
                                connectionTag,
                                "State was not READY. (State: " + this@BluetoothService.state + ")"
                            )
                            ready = false
                        } else {
                            acceptConnection = null
                            updateState(BluetoothState.CONNECTING)
                        }
                    }
                    if (ready) {
                        connect(RealBluetoothDevice(clientSocket.remoteDevice), clientSocket)
                    } else {
                        try {
                            clientSocket.close()
                        } catch (e: IOException) {
                        }
                    }
                } else {
                    Log.e(connectionTag, "Client socket was null")
                }
            }
            cancel()
        }
        
        fun cancel() {
            Log.i(connectionTag, "Cancelling AcceptConnection")
            try {
                serverSocket?.close()
            } catch (e: IOException) {
            }
        }
    }
    
    inner class CurrentConnection(
        private val device: IBluetoothDevice,
        private val socket: BluetoothSocket
    ) : Thread() {
        var inStream: InputStream?
        var outStream: OutputStream?
        
        init {
            try {
                inStream = socket.inputStream
                outStream = socket.outputStream
                updateState(BluetoothState.CONNECTED)
            } catch (e: IOException) {
                inStream = null
                outStream = null
                Log.e(connectionTag, "Error during stream creation", e)
                currentConnection = null
                cancel()
                updateState(BluetoothState.READY)
            }
        }
        
        override fun run() {
            Log.i(connectionTag, "Connection to ${device.name} established, ready to send/receive")
            val buffer = ByteArray(1024)
            var bytesRead = 0
            while (this@BluetoothService.state == BluetoothState.CONNECTED) {
                try {
                    bytesRead = inStream!!.read(buffer)
                    Log.d(connectionTag, "Message: $buffer")
                    onMessageReceive(buffer, bytesRead)
                } catch (e: IOException) { // Close the socket
                    Log.e(connectionTag, "CurrentConnection error reading message", e)
                    // TODO: disconnect
                }
            }
            
            cancel()
        }
        
        fun cancel() {
            Log.i(connectionTag, "Cancelling CurrentConnection")
            try {
                socket.close()
            } catch (e: IOException) {
            }
            try {
                inStream?.close()
            } catch (e: IOException) {
            }
            try {
                outStream?.close()
            } catch (e: IOException) {
            }
        }
    }
    
}
