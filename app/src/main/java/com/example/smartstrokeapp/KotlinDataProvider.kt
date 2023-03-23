package com.example.smartstrokeapp

class KotlinDataProvider(val mMaxUnits: Int = 1): DataProvider {
    var mSineGenerator = SineGenerator(3.0f)
    //var buffer = Vector<ScrollingBuffer>()

    override fun startSession(maxUnits: Int) {
    }

    override fun endSession() {
    }

    override fun getDesyncMs(unitId: Int): Double {
        return mSineGenerator.getValue()
    }

    init {
        for (unit in 1..mMaxUnits) {
            //buffer.add(ScrollingBuffer())
        }
    }

    fun update() {
        for (unit in 1..mMaxUnits) {
            //buffer[unit].addPoint(mSineGenerator.getDataPoint())
        }
    }
    // Tracks rolling buffer
    // Returns average desync?
    // Optional debug logging/graphing?
}