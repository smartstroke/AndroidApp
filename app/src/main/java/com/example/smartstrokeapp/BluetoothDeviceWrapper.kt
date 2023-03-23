package com.example.smartstrokeapp

import android.bluetooth.BluetoothDevice

data class BluetoothDeviceWrapper(val device: BluetoothDevice, var connected: Boolean)
