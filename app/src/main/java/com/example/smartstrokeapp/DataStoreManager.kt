package com.example.smartstrokeapp

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class DataStoreManager(private val dataStore: DataStore<Preferences>) {
    private val TAG = "DataStore"

    companion object {
        val BLE_ADDRESS = stringPreferencesKey("BLE_ADDRESS")
    }

    suspend fun saveBleAddress(address: String) {
        Log.d(TAG, "Saving $address to DataStore")
        dataStore.edit {
            it[BLE_ADDRESS] = address
        }
    }

    suspend fun getBleAddress() = dataStore.data.map {
        it[BLE_ADDRESS] ?:""
    }

    suspend fun getBleAddressString(): String {
        val dataStoreValues = dataStore.data.first()
        return dataStoreValues[BLE_ADDRESS] ?:""
    }
}
