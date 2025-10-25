package com.example.attendeasecampuscompanion

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import android.util.Log
import java.nio.charset.StandardCharsets

class HCEService : HostApduService() {

    companion object {
        private const val TAG = "HCEService"

        private const val AID = "F0010203040506"

        private val SELECT_APDU = byteArrayOf(
            0x00.toByte(), 0xA4.toByte(), 0x04.toByte(), 0x00.toByte(),
        )

        private val STATUS_SUCCESS = byteArrayOf(0x90.toByte(), 0x00.toByte())
        private val STATUS_FAILED = byteArrayOf(0x6F.toByte(), 0x00.toByte())

        var dataToSend: String = "Default Attendance ID: 1234567890"
    }

    @OptIn(ExperimentalStdlibApi::class)
    override fun processCommandApdu(commandApdu: ByteArray?, extras: Bundle?): ByteArray? {
        if (commandApdu == null) {
            Log.e(TAG, "Command APDU is null")
            return STATUS_FAILED
        }

        Log.d(TAG, "Received APDU: ${commandApdu.toHexString()}")

        if (commandApdu.size >= 5 &&
            commandApdu[0] == SELECT_APDU[0] &&
            commandApdu[1] == SELECT_APDU[1] &&
            commandApdu[2] == SELECT_APDU[2] &&
            commandApdu[3] == SELECT_APDU[3]) {

            Log.d(TAG, "Selected AID, sending data: $dataToSend")

            val dataBytes = dataToSend.toByteArray(StandardCharsets.UTF_8)
            return dataBytes + STATUS_SUCCESS
        }

        if (commandApdu.size >= 5 && commandApdu[0] == 0x00.toByte()) {
            Log.d(TAG, "Read command received, sending data: $dataToSend")
            val dataBytes = dataToSend.toByteArray(StandardCharsets.UTF_8)
            return dataBytes + STATUS_SUCCESS
        }

        return STATUS_FAILED
    }

    override fun onDeactivated(reason: Int) {
        Log.d(TAG, "Deactivated: $reason")
    }
}