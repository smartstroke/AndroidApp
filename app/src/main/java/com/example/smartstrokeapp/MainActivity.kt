package com.example.smartstrokeapp

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.widget.Switch
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.smartstrokeapp.databinding.ActivityMainBinding
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {
    lateinit var dataStoreManager: DataStoreManager
    private var bluetoothService: BluetoothLeService? = null

    private lateinit var binding: ActivityMainBinding
    private var isDebugMode = false // default value boolean for toggle
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    @SuppressLint("UseSwitchCompatOrMaterialCode", "SetTextI18n")
    private var currentBleTarget: String = ""
    private val TAG = "Main"

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
                } else {
                    Log.d(TAG, "Bluetooth service initialized")
                    if (currentBleTarget.isNotEmpty()) {
                        bluetoothService?.connect(currentBleTarget)
                    }
                    bluetoothService?.bleScanManager?.scanBleDevices()
                }
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
                    Toast.makeText(this@MainActivity, "Connected", Toast.LENGTH_SHORT).show()
                    activateButton()
                }

                BluetoothLeService.ACTION_GATT_ALREADY_CONNECTED -> {
                    connected = true
                    activateButton()
                }

                BluetoothLeService.ACTION_GATT_DISCONNECTED -> {
                    connected = false
                    setResult(RESULT_CANCELED, Intent())
                    Toast.makeText(this@MainActivity, "Disconnected", Toast.LENGTH_SHORT).show()
                    deactivateButton()
                }
            }
        }
    }

    private fun makeGattUpdateIntentFilter(): IntentFilter? {
        return IntentFilter().apply {
            addAction(BluetoothLeService.ACTION_GATT_CONNECTED)
            addAction(BluetoothLeService.ACTION_GATT_ALREADY_CONNECTED)
            addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED)
        }
    }

    @SuppressLint("UseSwitchCompatOrMaterialCode")
    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)

        doPermissionsThing()

        val gattServiceIntent = Intent(this, BluetoothLeService::class.java)
        bindService(gattServiceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
        registerReceiver(gattUpdateReceiver, makeGattUpdateIntentFilter())

        dataStoreManager = (applicationContext as MyApplication).dataStoreManager


        GlobalScope.launch(Dispatchers.IO) {
            currentBleTarget = dataStoreManager.getBleAddressString()
            Log.d(TAG, "Loading $currentBleTarget from DataStore")
        }

        setContentView(binding.root)
        val debugSwitch = findViewById<Switch>(R.id.debugSwitch)
        debugSwitch.setOnCheckedChangeListener { _, isChecked ->
            isDebugMode = isChecked
        }

        val beginClick = binding.BeginButton
        beginClick.isEnabled = false // Initially, button is disabled
        beginClick.setBackgroundColor(ContextCompat.getColor(this, R.color.gray)) // Replace with your gray color
        deactivateButton()

        beginClick.setOnClickListener {
            val intent = Intent(this, SessionActivity::class.java)
            intent.putExtra("DebugMode", isDebugMode)
            startActivity(intent)
        }

        val deviceClick = binding.DeviceButton
        deviceClick.setOnClickListener {
            val intent = Intent(this, DeviceListActivity::class.java)
            startActivity(intent)
        }

        val pastClick = binding.PastButton
        pastClick.setOnClickListener {
            val intent = Intent(this, PastSessionActivity::class.java)
            startActivity(intent)
        }
    }

    private fun activateButton() {
        val beginClick = binding.BeginButton
        beginClick.isEnabled = true
        beginClick.setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.white))
        beginClick.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.red )) // changed text colour
        beginClick.text = "Begin Session"
    }

    private fun deactivateButton() {
        val beginClick = binding.BeginButton
        beginClick.isEnabled = false
        beginClick.setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.gray )) // changes background colour
        beginClick.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.darkgray )) // changed text colour
        beginClick.text = "Waiting for Connection..."
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        PermissionUtilities.dispatchOnRequestPermissionsResult(
            requestCode,
            grantResults,
            onGrantedMap = mapOf(MainActivity.BLE_PERMISSION_REQUEST_CODE to {
            }),
            onDeniedMap = mapOf(MainActivity.BLE_PERMISSION_REQUEST_CODE to {
                Toast.makeText(
                    this,
                    "Some permissions were not granted, please grant them and try again",
                    Toast.LENGTH_LONG
                ).show()
            })
        )
    }

    fun doPermissionsThing() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestMultiplePermissions.launch(arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT))
        }
        else{
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            requestBluetooth.launch(enableBtIntent)
        }
    }
    private var requestBluetooth = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            //granted
        }else{
            //deny
        }
    }

    private val requestMultiplePermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            permissions.entries.forEach {
                Log.d("test006", "${it.key} = ${it.value}")
            }
        }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel() // Cancel the coroutine when the activity is destroyed
        unregisterReceiver(gattUpdateReceiver)
        unbindService(serviceConnection)
    }

    companion object { const val BLE_PERMISSION_REQUEST_CODE = 1 }

}
