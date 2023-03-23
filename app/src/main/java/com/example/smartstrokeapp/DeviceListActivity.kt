package com.example.smartstrokeapp

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class DeviceListActivity : AppCompatActivity(), BleDeviceClickListener {
    private var bluetoothService : BluetoothLeService? = null
    lateinit var dataStoreManager: DataStoreManager
    private var currentBleTarget: String = ""

    private lateinit var noDevicesText: TextView
    private lateinit var btnStartScan: FloatingActionButton
    private lateinit var deviceListAdapter: BleDeviceAdapter
    private var foundDevices: MutableList<BluetoothDeviceWrapper> = mutableListOf()


    private val TAG: String = "DeviceListBLE"

    private val serviceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(
            componentName: ComponentName,
            service: IBinder
        ) {
            Log.d(TAG, "on Bluetooth service connected")
            bluetoothService = (service as BluetoothLeService.LocalBinder).getService()
            bluetoothService?.let { bluetooth ->
                if (!bluetooth.initialize()) {
                    Log.e(TAG, "Unable to initialize Bluetooth")
                    finish()
                }
                else {
                    Log.d(TAG, "Bluetooth service initialized")
                    setupScanManager()

                    if (bluetoothService!!.isConnected()
                        && (bluetoothService!!.getCurrentDevice()?.address == currentBleTarget)) {
                        val currentDevice = bluetoothService!!.getCurrentDevice()!!
                        foundDevices.add(BluetoothDeviceWrapper(currentDevice, true)) }
                    }
                    deviceListAdapter.notifyItemInserted(foundDevices.size - 1)
                }
            }

        override fun onServiceDisconnected(componentName: ComponentName) {
            Log.d(TAG, "on Bluetooth service disconnected")
            bluetoothService = null
        }
    }

    var connected = false;
    private val gattUpdateReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothLeService.ACTION_GATT_CONNECTED -> {
                    connected = true
                    // Update BluetoothDeviceWrapper in the list.
                    currentBleTarget = bluetoothService!!.savedDeviceAddress
                    val deviceWrapper = foundDevices.find { it.device.address == currentBleTarget }
                    deviceWrapper?.connected = true
                    deviceListAdapter.notifyDataSetChanged()
                    setResult(RESULT_OK, Intent().putExtra("BleAddress", currentBleTarget))
                    Log.e(TAG, "Result set, saved address: ${bluetoothService?.savedDeviceAddress}")
                    GlobalScope.launch(Dispatchers.IO) {
                        dataStoreManager.saveBleAddress(currentBleTarget)
                    }
                }

                BluetoothLeService.ACTION_GATT_DISCONNECTED -> {
                    connected = false
                    // Update BluetoothDeviceWrapper in the list.
                    val deviceWrapper = foundDevices.find { it.device.address == currentBleTarget }
                    deviceWrapper?.connected = false
                    deviceListAdapter.notifyDataSetChanged()
                    currentBleTarget = ""
                    GlobalScope.launch(Dispatchers.IO) {
                        dataStoreManager.saveBleAddress("")
                    }
                }
            }
        }
    }

    private fun makeGattUpdateIntentFilter(): IntentFilter? {
        return IntentFilter().apply {
            addAction(BluetoothLeService.ACTION_GATT_CONNECTED)
            addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED)
        }
    }

    override fun onBleDeviceClick(deviceWrapper: BluetoothDeviceWrapper) {
        if (bluetoothService == null) {
            Log.e(TAG, "Bluetooth service not initialized")
        }
        if (bluetoothService!!.isConnected()) {
            bluetoothService?.disconnect()
        }
        else {
            currentBleTarget = deviceWrapper.device.address
            bluetoothService?.connect(deviceWrapper.device.address)
        }
    }

    companion object {
        private const val MY_PERMISSIONS_REQUEST_LOCATION = 1
    }

    @RequiresApi(Build.VERSION_CODES.S)
    @SuppressLint("CutPasteId", "NotifyDataSetChanged")
    override fun onCreate(savedInstanceState: Bundle?) {
        supportActionBar?.hide()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_list)
        noDevicesText = findViewById(R.id.noDevicesText)
        val backButton: Button = findViewById(R.id.Backbutton)
        backButton.setOnClickListener {
            onBackPressed()
        }

        dataStoreManager = (applicationContext as MyApplication).dataStoreManager
        GlobalScope.launch(Dispatchers.IO) {
            currentBleTarget = dataStoreManager.getBleAddressString()
            Log.d(TAG, "Loading $currentBleTarget from DataStore")
        }

        val gattServiceIntent = Intent(this, BluetoothLeService::class.java)
        bindService(gattServiceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
        registerReceiver(gattUpdateReceiver, makeGattUpdateIntentFilter())

        val recyclerView = findViewById<RecyclerView>(R.id.device_list)

        deviceListAdapter = BleDeviceAdapter(foundDevices, this)

        recyclerView.adapter = deviceListAdapter
        recyclerView.layoutManager = LinearLayoutManager(this)

        btnStartScan = findViewById(R.id.buttonScan)
        btnStartScan.isEnabled = false
    }

    fun setupScanManager() {
        bluetoothService!!.bleScanManager!!.setScanCallbacks(newCallbacks = BleScanCallback({ scanResult ->
        try {
            val device = scanResult!!.device!!

            // you may need to change this or comment it out if your test board has a different name
            if (device.name.isNullOrBlank() || (device.name.take(12).compareTo("SmartStroker") != 0)) {
                return@BleScanCallback
            }

            if (!foundDevices.any { it.device == device }) {
                val deviceWrapper = BluetoothDeviceWrapper(device, false)
                foundDevices.add(deviceWrapper)
                deviceListAdapter.notifyItemInserted(foundDevices.size - 1)
            }
        } catch (e: SecurityException) {
            // Handle the error as appropriate for your app
        }
    }))

        bluetoothService!!.bleScanManager!!.beforeScanActions.clear()
        bluetoothService!!.bleScanManager!!.beforeScanActions.add {
            btnStartScan.isEnabled = false
            foundDevices.clear()
            deviceListAdapter.notifyDataSetChanged()
        }
        bluetoothService!!.bleScanManager!!.afterScanActions.clear()
        bluetoothService!!.bleScanManager!!.afterScanActions.add {
            btnStartScan.isEnabled = true
        }

        btnStartScan = findViewById(R.id.buttonScan)
        btnStartScan.isEnabled = true
        btnStartScan.setOnClickListener {
            if(checkPermissions()) {
                bluetoothService?.bleScanManager?.scanBleDevices()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        bluetoothService?.bleScanManager?.setScanCallbacks(BleScanCallback())
        bluetoothService?.bleScanManager?.beforeScanActions?.clear()
        bluetoothService?.bleScanManager?.afterScanActions?.clear()
        unregisterReceiver(gattUpdateReceiver)
        unbindService(serviceConnection)
    }

    private fun checkPermissions(): Boolean {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {

            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.ACCESS_FINE_LOCATION)) {
                ActivityCompat.requestPermissions(this,
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    MY_PERMISSIONS_REQUEST_LOCATION)
            } else {
                ActivityCompat.requestPermissions(this,
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    MY_PERMISSIONS_REQUEST_LOCATION)
            }
            return false
        } else {
            return true
        }
    }


}
