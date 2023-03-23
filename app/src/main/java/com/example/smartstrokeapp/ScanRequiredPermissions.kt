package com.example.smartstrokeapp

import android.os.Build
import androidx.annotation.RequiresApi

class ScanRequiredPermissions {


    companion object {
        @RequiresApi(Build.VERSION_CODES.S)

        val permissions = arrayOf(
            android.Manifest.permission.BLUETOOTH_SCAN,
            android.Manifest.permission.ACCESS_COARSE_LOCATION,
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.BLUETOOTH_ADMIN,
        )
    }
}