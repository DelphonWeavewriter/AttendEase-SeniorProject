package com.example.attendeasecampuscompanion

import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.NfcEvent
import android.os.Build
import android.nfc.cardemulation.CardEmulation
import android.content.ComponentName
import android.nfc.tech.NfcA
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.nio.charset.StandardCharsets

class AttendanceSenderActivity : AppCompatActivity(){
    private lateinit var editText: EditText
    private lateinit var statusText: TextView
    private var nfcAdapter: NfcAdapter? = null
    private var cardEmulation: CardEmulation? = null
    private var attendanceID  = "CSCI456|Test"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_attendance_sender)

        editText = findViewById(R.id.editText)
        statusText = findViewById(R.id.statusText)
        val setButton: Button = findViewById(R.id.sendButton)

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        if (nfcAdapter == null) {
            Toast.makeText(this, "NFC is not available on this device", Toast.LENGTH_SHORT).show()
            setButton.isEnabled = false
            return
        }

        cardEmulation = CardEmulation.getInstance(nfcAdapter)

        if (!packageManager.hasSystemFeature("android.hardware.nfc.hce")) {
            statusText.text = "HCE not supported on this device"
            setButton.isEnabled = false
            return
        }

        editText.setText(HCEService.dataToSend)
        statusText.text = "Ready to be scanned..."


        setButton.setOnClickListener {
            val newText = editText.text.toString()
            if (newText.isEmpty()) {
                Toast.makeText(this, "Enter attendance ID", Toast.LENGTH_SHORT).show()
            } else {
                HCEService.dataToSend = newText
                statusText.text = "Preset Updated!\n\"$newText\"\n\nReady to be scanned..."
                Toast.makeText(this, "Data updated, ready to be scanned", Toast.LENGTH_SHORT).show()
            }
        }

        val component = ComponentName(this, HCEService::class.java)
        if (!cardEmulation!!.isDefaultServiceForCategory(component, CardEmulation.CATEGORY_OTHER)) {
            Toast.makeText(this, "Please set this app as default for NFC payments in settings", Toast.LENGTH_LONG).show()
        }
    }

}