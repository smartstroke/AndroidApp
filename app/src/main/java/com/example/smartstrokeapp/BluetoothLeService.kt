package com.example.smartstrokeapp;

import android.Manifest
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGatt.CONNECTION_PRIORITY_HIGH
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Intent
import android.content.pm.PackageManager
import android.nfc.NfcAdapter.EXTRA_DATA
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID


class BluetoothLeService : Service() {

    public val SMARTSTROKE_UUID = UUID.fromString("8bbd2160-84fa-43fb-8779-feb9185daba1")
    public val SS_TIME_UUID = UUID.fromString("0a4a5dc5-23ef-46cc-beb4-ff5fa9a5c992")
    public val SS_FSR1_UUID = UUID.fromString("9fefbfec-8a18-4971-93b8-f83240cb85bb")
    public val SS_FSR2_UUID = UUID.fromString("9b92b831-2ae8-4659-80a4-c6d4652baa79")
    public val SS_FSR3_UUID = UUID.fromString("2315637b-84de-4851-88f8-0cbf3aa29f8a")
    public val SS_FSR4_UUID = UUID.fromString("e9c9be95-5bdb-4cb6-9783-084e33e11d41")
    public val SS_DEBUG_UUID = UUID.fromString("b3d4772d-f279-4435-9a6c-e1a5c42472f6")


    var connectionState = STATE_DISCONNECTED

    var savedDeviceAddress: String = ""

    private val TAG = "BLEService"
    private val binder = LocalBinder()
    private var bluetoothAdapter: BluetoothAdapter? = null
    var bleScanManager: BleScanManager? = null
    private var bluetoothGatt: BluetoothGatt? = null
    private var smartStrokeService: BluetoothGattService? = null
    var smartStrokeDebugCharacteristic: BluetoothGattCharacteristic? = null
    var smartStrokeTimeCharacteristic: BluetoothGattCharacteristic? = null
    var smartStrokeFsr1Characteristic: BluetoothGattCharacteristic? = null
    var smartStrokeFsr2Characteristic: BluetoothGattCharacteristic? = null
    var smartStrokeFsr3Characteristic: BluetoothGattCharacteristic? = null
    var smartStrokeFsr4Characteristic: BluetoothGattCharacteristic? = null

    var lastBasestationTime: Double = 0.0
    var lastReceivedTime: Long = 0L

    override fun onBind(intent: Intent): IBinder? {
        Log.d(TAG, "Binding")
        return binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.d(TAG, "Unbinding")
        close()
        return super.onUnbind(intent)
    }

    private fun close() {
        bluetoothGatt?.let { gatt ->
            gatt.close()
            bluetoothGatt = null
        }
    }

    inner class LocalBinder : Binder() {
        fun getService() : BluetoothLeService {
            return this@BluetoothLeService
        }
    }

    fun initialize(): Boolean {
        val bluetoothService = ContextCompat.getSystemService(this, BluetoothManager::class.java)!!
        bluetoothAdapter = bluetoothService.adapter
        bleScanManager = BleScanManager(bluetoothService, 5000)

        if (bluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.")
            return false
        }
        return true
    }

    companion object {
        const val ACTION_GATT_CONNECTED = "com.example.bluetooth.le.ACTION_GATT_CONNECTED"
        const val ACTION_GATT_ALREADY_CONNECTED = "com.example.bluetooth.le.ACTION_GATT_ALREADY_CONNECTED"
        const val ACTION_GATT_DISCONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_DISCONNECTED"
        const val ACTION_GATT_SERVICES_DISCOVERED =
            "com.example.bluetooth.le.ACTION_GATT_SERVICES_DISCOVERED"
        const val ACTION_DATA_AVAILABLE = "com.example.bluetooth.le.ACTION_DATA_AVAILABLE"

        const val STATE_DISCONNECTED = 0
        const val STATE_CONNECTED = 2
    }

    private fun broadcastUpdate(action: String) {
        val intent = Intent(action)
        sendBroadcast(intent)
    }

    fun isConnected(): Boolean {
        return connectionState == STATE_CONNECTED
    }

    private fun broadcastUpdate(action: String, characteristic: BluetoothGattCharacteristic, byteArray: ByteArray) {
        val intent = Intent(action)
        when (characteristic.uuid) {
            SS_DEBUG_UUID -> {
                Log.d(TAG, String.format("Received debug string: %s", byteArray.toString(Charsets.UTF_8)))
                intent.putExtra("BLE_DEBUG", byteArray.toString(Charsets.UTF_8))
            }
            SS_TIME_UUID -> {
                lastBasestationTime = ByteBuffer.wrap(byteArray).order(ByteOrder.LITTLE_ENDIAN).double
                lastReceivedTime = System.currentTimeMillis()
                smartStrokeFsr1Characteristic?.let { fsr1 -> readCharacteristic(fsr1)}
            }
            SS_FSR1_UUID -> {
                sendFsrData(intent, 0, byteArray)
                smartStrokeFsr2Characteristic?.let { fsr2 -> readCharacteristic(fsr2)}
            }
            SS_FSR2_UUID -> {
                sendFsrData(intent, 1, byteArray)
                smartStrokeFsr3Characteristic?.let { fsr3 -> readCharacteristic(fsr3)}
            }
            SS_FSR3_UUID -> {
                sendFsrData(intent, 2, byteArray)
                smartStrokeFsr4Characteristic?.let { fsr4 -> readCharacteristic(fsr4)}
            }
            SS_FSR4_UUID -> {
                sendFsrData(intent, 3, byteArray)
            }
            else -> {
                // For all other profiles, writes the data formatted in HEX.
                val data: ByteArray? = characteristic.value
                if (data?.isNotEmpty() == true) {
                    val hexString: String = data.joinToString(separator = " ") {
                        String.format("%02X", it)
                    }
                    intent.putExtra(EXTRA_DATA, "$data\n$hexString")
                }
            }
        }
        sendBroadcast(intent)
    }

    fun sendFsrData(intent: Intent, unit:Int, byteArray: ByteArray) {
        val value = ByteBuffer.wrap(byteArray).order(ByteOrder.LITTLE_ENDIAN).double
        val timeSinceLastRead = System.currentTimeMillis() - lastReceivedTime
        val adjustedTimeStamp = lastBasestationTime + timeSinceLastRead.toDouble()
        intent.putExtra("FSR_TIME", adjustedTimeStamp)
        intent.putExtra("FSR_TARGET", unit)
        intent.putExtra("FSR_VALUE", value)
    }

    private val bluetoothGattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                // successfully connected to the GATT Server
                broadcastUpdate(ACTION_GATT_CONNECTED)
                connectionState = STATE_CONNECTED
                // Attempts to discover services after successful connection.
                bluetoothGatt?.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                // disconnected from the GATT Server
                broadcastUpdate(ACTION_GATT_DISCONNECTED)
                connectionState = STATE_DISCONNECTED
                savedDeviceAddress = ""
            }
        }

        @Suppress("DEPRECATION")
        @Deprecated(
            "Used natively in Android 12 and lower",
            ReplaceWith("onCharacteristicRead(gatt, characteristic, characteristic.value, status)")
        )
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) = onCharacteristicRead(gatt, characteristic, characteristic.value, status)

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            byteArray: ByteArray,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic, byteArray)
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            Log.d(TAG, "on service discovered")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                gatt?.requestConnectionPriority(CONNECTION_PRIORITY_HIGH)
                smartStrokeService = gatt?.getService(SMARTSTROKE_UUID)
                if (smartStrokeService != null) {
                    Log.w(TAG, "smart stroke service found")
                    smartStrokeDebugCharacteristic = smartStrokeService!!.getCharacteristic(SS_DEBUG_UUID)
                    if (smartStrokeDebugCharacteristic != null) {
                        val readStarted = gatt?.readCharacteristic(smartStrokeDebugCharacteristic)
                        Log.w(TAG, "debug characteristic found, read status ${readStarted.toString()}")
                    }
                    smartStrokeTimeCharacteristic = smartStrokeService!!.getCharacteristic(SS_TIME_UUID)
                    if (smartStrokeTimeCharacteristic != null) {
                        Log.w(TAG, "time characteristic found")
                    }
                    smartStrokeFsr1Characteristic = smartStrokeService!!.getCharacteristic(SS_FSR1_UUID)
                    if (smartStrokeFsr1Characteristic != null) {
                        Log.w(TAG, "fsr1 characteristic found")
                    }
                    smartStrokeFsr2Characteristic = smartStrokeService!!.getCharacteristic(SS_FSR2_UUID)
                    if (smartStrokeFsr2Characteristic != null) {
                        Log.w(TAG, "fsr2 characteristic found")
                    }
                    smartStrokeFsr3Characteristic = smartStrokeService!!.getCharacteristic(SS_FSR3_UUID)
                    if (smartStrokeFsr3Characteristic != null) {
                        Log.w(TAG, "fsr3 characteristic found")
                    }
                    smartStrokeFsr4Characteristic = smartStrokeService!!.getCharacteristic(SS_FSR4_UUID)
                    if (smartStrokeFsr4Characteristic != null) {
                        Log.w(TAG, "fsr4 characteristic found")
                    }
                }
                broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED)
            } else {
                Log.w(TAG, "onServicesDiscovered received: $status")
            }
        }
    }

    fun writeCharacteristic(characteristic: BluetoothGattCharacteristic, payload: ByteArray) {
        bluetoothGatt?.let { gatt ->
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            characteristic.value = payload
            gatt.writeCharacteristic(characteristic)
        } ?: error("Not connected to a BLE device!")
    }

    fun writeDebugString(string: String) {
        smartStrokeDebugCharacteristic?.let { writeCharacteristic(it, string.toByteArray(Charsets.UTF_8)) }
    }

    fun fsrReady(): Boolean {
        val value = ((smartStrokeFsr1Characteristic != null)
                && (smartStrokeFsr2Characteristic != null)
                && (smartStrokeFsr3Characteristic != null)
                && (smartStrokeFsr4Characteristic != null))
        return value
    }

    fun readCharacteristic(characteristic: BluetoothGattCharacteristic) {
        bluetoothGatt?.let { gatt ->
            gatt.readCharacteristic(characteristic)
        } ?: run {
            Log.w(TAG, "BluetoothGatt not initialized")
            return
        }
    }

    fun readFsrData() {
        if (smartStrokeFsr1Characteristic == null) {
            Log.e(TAG, "Fsr1 characteristic not discovered yet")
            return
        }
        smartStrokeTimeCharacteristic?.let { readCharacteristic(it)}
    }

    fun connect(address: String): Boolean {
        if (connectionState == STATE_CONNECTED) {
            broadcastUpdate(ACTION_GATT_ALREADY_CONNECTED)
            return true
        }
        if (address.isEmpty()) {
            return false
        }
        bluetoothAdapter?.let { adapter ->
            try {
                val device = adapter.getRemoteDevice(address)
                savedDeviceAddress = device.address
                // connect to the GATT server on the device
                bluetoothGatt = device.connectGatt(this, false, bluetoothGattCallback)
                Log.e(TAG, "Bluetooth connection success! Attempted with: $savedDeviceAddress")
                return true
            } catch (exception: IllegalArgumentException) {
                Log.w(TAG, "Device not found with provided address.  Unable to connect.")
                return false
            }
        } ?: run {
            Log.w(TAG, "BluetoothAdapter not initialized")
            return false
        }
    }

    fun disconnect(): Boolean {
        bluetoothGatt.let { gatt ->
            gatt?.disconnect()
            return true
        }
        return false
    }

    fun getCurrentDevice(): BluetoothDevice? {
        return bluetoothGatt?.device
    }
}
