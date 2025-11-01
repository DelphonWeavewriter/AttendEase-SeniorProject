package com.example.attendeasecampuscompanion

import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NdefMessage
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.nfc.tech.Ndef
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import android.widget.TextView
import java.nio.charset.StandardCharsets

class AttendanceActivity : AppCompatActivity() {

    private var nfcAdapter: NfcAdapter? = null
//    private var pendingIntent: PendingIntent? = null
//    private var intentFiltersArray: Array<IntentFilter>? = null
//
//    private var nfcTagData: String = ""

    private var receivedData: String = ""
    private lateinit var textView: TextView
    private lateinit var db: FirebaseFirestore

    companion object {
        private const val AID = "F0010203040506"

        private val SELECT_APDU_HEADER = byteArrayOf(
            0x00.toByte(),
            0xA4.toByte(),
            0x04.toByte(),
            0x00.toByte()
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_attendance)

        textView = findViewById(R.id.textView)

        // Firestore Initialized
        db = FirebaseFirestore.getInstance()

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        if (nfcAdapter == null) {
            Toast.makeText(this, "This device is not NFC-capable", Toast.LENGTH_LONG).show()
            textView.text = "NFC is not available on this device"
            finish()
            return
        }

        if (!nfcAdapter!!.isEnabled) {
            textView.text = "NFC is disabled. Please enable it in the settings."
            Toast.makeText(this, "Please enable NFC", Toast.LENGTH_SHORT).show()
        } else {
            textView.text = "NFC is enabled, ready to scan"
        }
//
//        pendingIntent = PendingIntent.getActivity(
//            this, 0,
//            Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
//            PendingIntent.FLAG_MUTABLE
//        )
//
//        // Setup intent filters for NFC discovery
//        val ndefDetected = IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED)
//        try {
//            ndefDetected.addDataType("*/*")
//        } catch (e: IntentFilter.MalformedMimeTypeException) {
//            throw RuntimeException("Failed to add MIME type.", e)
//        }
//
//        intentFiltersArray = arrayOf(ndefDetected)

    }


    override fun onResume() {
        super.onResume()

        val callback = NfcAdapter.ReaderCallback { tag ->
            onTagDiscovered(tag)
        }

        // Enable foreground dispatch to intercept NFC intents
        nfcAdapter?.enableReaderMode(
            this,
            callback,
            NfcAdapter.FLAG_READER_NFC_A or
            NfcAdapter.FLAG_READER_NFC_B or
            NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
            null
        )

        runOnUiThread { textView.text = "Scanning for HCE device..." }
    }

    override fun onPause() {
        super.onPause()
        // Disable NFC detection when app is paused
        nfcAdapter?.disableReaderMode(this)
    }

    fun onTagDiscovered(tag: Tag?) {
        if (tag == null) return

        val isoDep = IsoDep.get(tag)
        if (isoDep == null) {
            runOnUiThread {
                Toast.makeText(this, "Not an ISO-DEP tag", Toast.LENGTH_SHORT).show()
            }
            return
        }

        try {
            isoDep.connect()
            isoDep.timeout = 5000

            // Convert AID from hex string to byte array
            val aidBytes = hexStringToByteArray(AID)

            // Build SELECT APDU command
            val selectApdu = SELECT_APDU_HEADER + byteArrayOf(aidBytes.size.toByte()) + aidBytes + byteArrayOf(0x00.toByte())

            // Send SELECT command
            val response = isoDep.transceive(selectApdu)

            if (response != null && response.size >= 2) {
                // Check status code (last 2 bytes should be 90 00 for success)
                val statusCode = response.sliceArray(response.size - 2 until response.size)

                if (statusCode[0] == 0x90.toByte() && statusCode[1] == 0x00.toByte()) {
                    // Extract data (everything except last 2 status bytes)
                    val dataBytes = response.sliceArray(0 until response.size - 2)
                    receivedData = String(dataBytes, StandardCharsets.UTF_8)

                    runOnUiThread {
                        textView.text = "Received Data:\n$receivedData"
                        Toast.makeText(this, "Data received successfully!", Toast.LENGTH_SHORT).show()
                    }

                    sendToFirebase(receivedData)
                } else {
                    runOnUiThread {
                        textView.text = "Failed to read data (status code error)"
                        Toast.makeText(this, "Communication error", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            isoDep.close()

        } catch (e: Exception) {
            runOnUiThread {
                textView.text = "Error: ${e.message}"
                Toast.makeText(this, "Error reading HCE: ${e.message}", Toast.LENGTH_LONG).show()
            }
            e.printStackTrace()
        }
    }

    private fun hexStringToByteArray(hexString: String): ByteArray {
        val len = hexString.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(hexString[i], 16) shl 4) + Character.digit(hexString[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }

//
//    override fun onNewIntent(intent: Intent) {
//        super.onNewIntent(intent)
//
//        handleNfcIntent(intent)
//
//        // Check if the intent contains NFC tag data
////        if (NfcAdapter.ACTION_NDEF_DISCOVERED == intent.action ||
////            NfcAdapter.ACTION_TAG_DISCOVERED == intent.action ||
////            NfcAdapter.ACTION_TECH_DISCOVERED == intent.action) {
////
////            val tag: Tag? = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
////            tag?.let {
////                readNfcTag(it)
////            }
////        }
//    }
//
//    private fun handleNfcIntent(intent: Intent) {
//        if (intent == null) return
//
//        if (NfcAdapter.ACTION_NDEF_DISCOVERED == intent.action ||
//            NfcAdapter.ACTION_TAG_DISCOVERED == intent.action ||
//            NfcAdapter.ACTION_TECH_DISCOVERED == intent.action) {
//
//            val rawMessages = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
//            if (rawMessages != null && rawMessages.isNotEmpty()) {
//                val ndefMessage = rawMessages[0] as NdefMessage
//                val text = parseNdefMessage(ndefMessage)
//                if (text.isNotEmpty()) {
//                    nfcTagData = text
//                    textView.text = "NFC Tag Read:\n$nfcTagData"
//                    Toast.makeText(this, "NFC data saved to variable", Toast.LENGTH_SHORT).show()
//                    sendToFirebase(nfcTagData)
//                    return
//                }
//            }
//
//            val tag: Tag? = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
//            tag?.let {
//                readNfcTag(it)
//            }
//
//        }
//
//    }

    private fun parseNdefMessage(message: NdefMessage): String {
        val sb = StringBuilder()

        for (record in message.records) {
            if (record.tnf == android.nfc.NdefRecord.TNF_WELL_KNOWN &&
                        record.type.contentEquals(android.nfc.NdefRecord.RTD_TEXT)) {

                val payload = record.payload

                val languageCodeLength = payload[0].toInt() and 0x3F

                val text = String(
                    payload,
                    languageCodeLength + 1,
                    payload.size - languageCodeLength - 1,
                    charset("UTF-8")
                )

                sb.append(text).append("\n")
            }
            else if (record.tnf == android.nfc.NdefRecord.TNF_MIME_MEDIA) {
                try {
                    val text = String(record.payload, StandardCharsets.UTF_8)
                    sb.append(text).append("\n")
                } catch (e: Exception) {
                    val payload = String(record.payload, charset("UTF-8"))
                    sb.append(payload).append("\n")
                }

            }
            else {
                val payload = String(record.payload, charset("UTF-8"))
                sb.append(payload).append("\n")
            }
        }

        return sb.toString().trim()
    }


//    private fun readNfcTag(tag: Tag) {
//        val ndef = Ndef.get(tag)
//
//        if (ndef == null) {
//            // Try to read raw tag ID if NDEF is not available
//            val tagId = bytesToHex(tag.id)
//            nfcTagData = "Tag ID: $tagId"
//            textView.text = "NFC Tag Read:\n$nfcTagData"
//            Toast.makeText(this, "Tag ID saved to variable", Toast.LENGTH_SHORT).show()
//
//            // Send Attendance Record to Firebase
//            sendToFirebase(nfcTagData)
//            return
//        }
//
//        try {
//            ndef.connect()
//            val ndefMessage = ndef.ndefMessage
//
////            if (ndefMessage != null) {
////                val records = ndefMessage.records
////                val sb = StringBuilder()
////
////                for (record in records) {
////                    // Check if this is a text record
////                    if (record.tnf == android.nfc.NdefRecord.TNF_WELL_KNOWN &&
////                        record.type.contentEquals(android.nfc.NdefRecord.RTD_TEXT)) {
////
////                        val payload = record.payload
////
////                        // First byte contains the language code length
////                        val languageCodeLength = payload[0].toInt() and 0x3F
////
////                        val text = String(
////                            payload,
////                            languageCodeLength + 1,
////                            payload.size - languageCodeLength - 1,
////                            charset("UTF-8")
////                        )
////
////                        sb.append(text).append("\n")
////                    } else {
////                        // If for some reason the NFC data is not text, convert as plain string
////                        val payload = String(record.payload, charset("UTF-8"))
////                        sb.append(payload).append("\n")
////                    }
////                }
////
////                // Save data to variable
////                nfcTagData = sb.toString().trim()
////
////                // Display the data
////                textView.text = "NFC Tag Read:\n$nfcTagData"
////                Toast.makeText(this, "NFC data saved to variable", Toast.LENGTH_SHORT).show()
////
////                // Send Attendance Record to Firebase
////                sendToFirebase(nfcTagData)
////            } else {
////                nfcTagData = "Empty tag"
////                textView.text = nfcTagData
////            }
//
//            if (ndefMessage != null) {
//                nfcTagData = parseNdefMessage(ndefMessage)
//                textView.text = "NFC Tag Read:\n$nfcTagData"
//                Toast.makeText(this,"NFC data saved to variable", Toast.LENGTH_SHORT).show()
//                sendToFirebase(nfcTagData)
//            } else {
//                nfcTagData = "Empty tag"
//                textView.text = nfcTagData
//            }
//
//            ndef.close()
//
//        } catch (e: Exception) {
//            Toast.makeText(this, "Error reading NFC tag: ${e.message}", Toast.LENGTH_LONG).show()
//            e.printStackTrace()
//        }
//
//    }

    // Function to send NFC data to the database
    private fun sendToFirebase(data: String) {
        val currentUser = FirebaseAuth.getInstance().currentUser

        if (currentUser == null) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
            return
        }



        val nfcData = hashMapOf(
            "nfcString" to data,
            "timestamp" to System.currentTimeMillis(),
            "userId" to currentUser.uid,
            "userName" to currentUser.displayName,
            "userEmail" to currentUser.email,
        )

        db.collection("Test")
            .add(nfcData)
            .addOnSuccessListener { documentReference ->
                Toast.makeText(
                    this,
                    "Data saved to Firebase: ${documentReference.id}",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(
                    this,
                    "Error saving to Firebase: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    // Helper function to convert byte array to hex string
    private fun bytesToHex(bytes: ByteArray): String {
        val hexChars = CharArray(bytes.size * 2)
        for (i in bytes.indices) {
            val v = bytes[i].toInt() and 0xFF
            hexChars[i * 2] = "0123456789ABCDEF"[v ushr 4]
            hexChars[i * 2 + 1] = "0123456789ABCDEF"[v and 0x0F]
        }
        return String(hexChars)
    }


//    // Function to access the saved NFC data from anywhere in your app
//    fun getSavedNfcData(): String {
//        return nfcTagData
//    }



}