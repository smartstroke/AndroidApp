package com.example.smartstrokeapp

interface DataProvider {
    fun startSession(maxUnits: Int)
    fun endSession()
    fun getDesyncMs(unitId :Int): Double
}
