package at.tugraz.ist.sw20.swta1.cheat

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import at.tugraz.ist.sw20.swta1.cheat.bluetooth.BluetoothService
import at.tugraz.ist.sw20.swta1.cheat.ui.chat.ChatEntry
import at.tugraz.ist.sw20.swta1.cheat.ui.chat.ChatFragment
import java.util.*

class ChatActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.container, ChatFragment.newInstance())
                .commitNow()
        }

        supportActionBar?.hide()

        val chatEntry = ChatEntry(getString(R.string.partner_connected), true, true, Date())
        BluetoothService.sendMessage(chatEntry)
    }

    override fun onBackPressed() {
        disconnect()
    }

    fun disconnect()
    {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(getString(R.string.disconnect_message_title))
        builder.setMessage(getString(R.string.disconnect_message_message))

        builder.setPositiveButton(getString(R.string.disconnect_message_pos)){dialog, which ->
            val chatEntry = ChatEntry(getString(R.string.partner_disconnected), true, true, Date())
            BluetoothService.sendMessage(chatEntry)
            BluetoothService.disconnect()
            super.onBackPressed()
        }

        builder.setNegativeButton(getString(R.string.disconnect_message_pos)){_,_ -> }

        val dialog: AlertDialog = builder.create()
        dialog.show()
    }
}