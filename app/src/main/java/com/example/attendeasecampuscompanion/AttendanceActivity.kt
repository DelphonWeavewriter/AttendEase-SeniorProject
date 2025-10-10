package com.example.attendeasecampuscompanion

import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

import android.widget.TextView

class AttendanceActivity : AppCompatActivity() {

    private var nfcAdapter: NfcAdapter? = null
    private var pendingIntent: PendingIntent? = null
    private var intentFiltersArray: Array<IntentFilter>? = null

    private var nfcTagData: String = ""

    private lateinit var textView: TextView
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_attendance)

        textView = findViewById(R.id.textView)

        // Firestore Initialized
        db = FirebaseFirestore.getInstance()

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        if (nfcAdapter == null) {
            Toast.makeText(this, "This device is not NFC-capable", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_MUTABLE
        )

        // Setup intent filters for NFC discovery
        val ndefDetected = IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED)
        try {
            ndefDetected.addDataType("*/*")
        } catch (e: IntentFilter.MalformedMimeTypeException) {
            throw RuntimeException("Failed to add MIME type.", e)
        }

        intentFiltersArray = arrayOf(ndefDetected)
    }

    override fun onResume() {
        super.onResume()
        // Enable foreground dispatch to intercept NFC intents
        nfcAdapter?.enableForegroundDispatch(
            this,
            pendingIntent,
            intentFiltersArray,
            null
        )
    }

    override fun onPause() {
        super.onPause()
        // Disable NFC detection when app is paused
        nfcAdapter?.disableForegroundDispatch(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        // Check if the intent contains NFC tag data
        if (NfcAdapter.ACTION_NDEF_DISCOVERED == intent.action ||
            NfcAdapter.ACTION_TAG_DISCOVERED == intent.action ||
            NfcAdapter.ACTION_TECH_DISCOVERED == intent.action) {

            val tag: Tag? = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
            tag?.let {
                readNfcTag(it)
            }
        }
    }

    private fun readNfcTag(tag: Tag) {
        val ndef = Ndef.get(tag)

        if (ndef == null) {
            // Try to read raw tag ID if NDEF is not available
            val tagId = bytesToHex(tag.id)
            nfcTagData = "Tag ID: $tagId"
            textView.text = "NFC Tag Read:\n$nfcTagData"
            Toast.makeText(this, "Tag ID saved to variable", Toast.LENGTH_SHORT).show()

            // Send Attendance Record to Firebase
            sendToFirebase(nfcTagData)
            return
        }

        try {
            ndef.connect()
            val ndefMessage = ndef.ndefMessage

            if (ndefMessage != null) {
                val records = ndefMessage.records
                val sb = StringBuilder()

                for (record in records) {
                    // Check if this is a text record
                    if (record.tnf == android.nfc.NdefRecord.TNF_WELL_KNOWN &&
                        record.type.contentEquals(android.nfc.NdefRecord.RTD_TEXT)) {

                        val payload = record.payload

                        // First byte contains the language code length
                        val languageCodeLength = payload[0].toInt() and 0x3F

                        val text = String(
                            payload,
                            languageCodeLength + 1,
                            payload.size - languageCodeLength - 1,
                            charset("UTF-8")
                        )

                        sb.append(text).append("\n")
                    } else {
                        // If for some reason the NFC data is not text, convert as plain string
                        val payload = String(record.payload, charset("UTF-8"))
                        sb.append(payload).append("\n")
                    }
                }

                // Save data to variable
                nfcTagData = sb.toString().trim()

                // Display the data
                textView.text = "NFC Tag Read:\n$nfcTagData"
                Toast.makeText(this, "NFC data saved to variable", Toast.LENGTH_SHORT).show()

                // Send Attendance Record to Firebase
                sendToFirebase(nfcTagData)
            } else {
                nfcTagData = "Empty tag"
                textView.text = nfcTagData
            }

            ndef.close()

        } catch (e: Exception) {
            Toast.makeText(this, "Error reading NFC tag: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }

    }

    // Function to send NFC data to the database
    private fun sendToFirebase(data: String) {
        val currentUser = FirebaseAuth.getInstance().currentUser

        if (currentUser == null) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        val nfcData = hashMapOf(
            "nfcString" to data
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

    // Function to access the saved NFC data from anywhere in your app
    fun getSavedNfcData(): String {
        return nfcTagData
    }



}