package at.tugraz.ist.sw20.swta1.cheat.ui.main

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ListView
import androidx.lifecycle.ViewModelProvider
import android.widget.Toast
import at.tugraz.ist.sw20.swta1.cheat.R
import at.tugraz.ist.sw20.swta1.cheat.ui.main.adapters.BluetoothDeviceAdapter
import kotlinx.android.synthetic.main.item_title_cell.view.*

class MainFragment : Fragment() {

    companion object {
        fun newInstance() = MainFragment()
    }

    private lateinit var viewModel: MainViewModel

    private val REQUEST_ENABLE_BLUETOOTH: Int = 1
    private var bluetoothAdapter: BluetoothAdapter? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter == null) {
            // Device doesn't support bluetooth
            Toast.makeText(activity, "No Bluetooth available", Toast.LENGTH_SHORT).show()
        } else {
            if (!bluetoothAdapter!!.isEnabled) {
                val enableBluetoothIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                startActivityForResult(enableBluetoothIntent, REQUEST_ENABLE_BLUETOOTH)
            }
        }

        val root = inflater.inflate(R.layout.main_fragment, container, false)
        val lvPairedDevices: ListView = root.findViewById(R.id.list_paired_devices)
        val lvNearbyDevices: ListView = root.findViewById(R.id.list_nearby_devices)
        // TODO lvPairedDevices.onItemClickListener
        val titlePaired: View = root.findViewById(R.id.title_paired_devices)
        val titleNearby: View = root.findViewById(R.id.title_nearby_devices)

        titlePaired.title.text = getString(R.string.paired_devices)
        titleNearby.title.text = getString(R.string.nearby_devices)

        val devicesPaired = ArrayList<Any>()
        val adapterPaired = BluetoothDeviceAdapter(this.context!!, devicesPaired)
        lvPairedDevices.adapter = adapterPaired

        val devicesNearby = ArrayList<Any>()
        val adapterNearby = BluetoothDeviceAdapter(this.context!!, devicesNearby)
        lvPairedDevices.adapter = adapterNearby

        return root
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        viewModel = ViewModelProvider(this).get(MainViewModel::class.java)
        // TODO: Use the ViewModel
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_ENABLE_BLUETOOTH) {
            if (resultCode == Activity.RESULT_OK) {
                if (bluetoothAdapter!!.isEnabled) {
                    Toast.makeText(activity, "Bluetooth enabled", Toast.LENGTH_SHORT).show()
                    /*
                    val service = BluetoothService(bluetoothAdapter!!)
                    service.getPairedDevices().forEach { device ->
                        Log.println(Log.INFO, "Paired device", device.name)
                    }
                    service.discoverDevices(activity!!) { device : BluetoothDevice ->
                        Log.println(Log.INFO, "Discovered device", device.name)
                    }
                    //Thread.sleep(15000)
                    service.stopDiscovery(activity!!)
                    */
                } else {
                    Toast.makeText(activity, "Bluetooth disabled", Toast.LENGTH_SHORT).show()
                }
            } else if (resultCode == Activity.RESULT_CANCELED) {
                Toast.makeText(activity, "Bluetooth enabling cancelled", Toast.LENGTH_SHORT).show()
            }
        }
    }

}
