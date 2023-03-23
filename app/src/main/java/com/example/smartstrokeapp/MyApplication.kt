package com.example.smartstrokeapp

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.preferencesDataStore

class MyApplication : Application() {

    private val Context.dataStore by preferencesDataStore(name = "settings")

    lateinit var dataStoreManager: DataStoreManager

    override fun onCreate() {
        super.onCreate()

        // Initialize the DataStoreManager
        dataStoreManager = DataStoreManager(this.dataStore)
    }
}
