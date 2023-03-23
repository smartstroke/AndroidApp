package com.example.smartstrokeapp

import android.bluetooth.BluetoothSocket
import android.util.Log
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream


//Class that given an open BT Socket will
//Open, manage and close the data Stream from the Arduino BT device
class ConnectedThread(private val mmSocket: BluetoothSocket) : Thread() {
    private val mmInStream: InputStream?
    private val mmOutStream: OutputStream?
    var valueRead: String? = null
        private set

    init {
        var tmpIn: InputStream? = null
        var tmpOut: OutputStream? = null

        // Get the input and output streams; using temp objects because
        // member streams are final.
        try {
            tmpIn = mmSocket.inputStream
        } catch (e: IOException) {
            Log.e(TAG, "Error occurred when creating input stream", e)
        }
        try {
            tmpOut = mmSocket.outputStream
        } catch (e: IOException) {
            Log.e(TAG, "Error occurred when creating output stream", e)
        }
        //Input and Output streams members of the class
        //We wont use the Output stream of this project
        mmInStream = tmpIn
        mmOutStream = tmpOut
    }

    override fun run() {
        val buffer = ByteArray(1024)
        var bytes = 0 // bytes returned from read()
        var numberOfReadings = 0 //to control the number of readings from the Arduino

        // Keep listening to the InputStream until an exception occurs.
        //We just want to get 1 temperature readings from the Arduino
        while (numberOfReadings < 1) {
            try {
                buffer[bytes] = mmInStream!!.read().toByte()
                var readMessage: String
                // If I detect a "\n" means I already read a full measurement
                if (buffer[bytes] == '\n'.code.toByte()) {
                    readMessage = String(buffer, 0, bytes)
                    Log.e(TAG, readMessage)
                    //Value to be read by the Observer streamed by the Obervable
                    valueRead = readMessage
                    bytes = 0
                    numberOfReadings++
                } else {
                    bytes++
                }
            } catch (e: IOException) {
                Log.d(TAG, "Input stream was disconnected", e)
                break
            }
        }
    }

    // Call this method from the main activity to shut down the connection.
    fun cancel() {
        try {
            mmSocket.close()
        } catch (e: IOException) {
            Log.e(TAG, "Could not close the connect socket", e)
        }
    }

    companion object {
        private const val TAG = "FrugalLogs"
    }
}