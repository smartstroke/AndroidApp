package com.example.smartstrokeapp
import com.jjoe64.graphview.series.DataPoint
import java.util.Vector
import kotlin.math.PI
import kotlin.math.sin

class SineGenerator(private var mPeriodInSeconds: Float = 2.0f) {

    val mStartTimeMillis = System.currentTimeMillis()
    private val mod = (2 * PI) / (mPeriodInSeconds * 1000)
    private var offsets: Vector<Int> = Vector<Int>()

    init {
        offsets.add(0)
    }

    // 0 reserved
    fun setOffset(unitNumber:Int, offsetAmount:Int) {
        if (unitNumber == 0) {
            return
        }
        offsets.add(unitNumber, offsetAmount)
    }

    // Still returns +1 to -1
    fun getValue(): Double {
        val currentTimeMillis = System.currentTimeMillis() - mStartTimeMillis;
        return sin(currentTimeMillis.toDouble() * mod)
    }

    fun getDataPoint(): DataPoint {
        return getDataPoint(0)
    }

    fun getDataPoint(unitNumber:Int): DataPoint {
        val currentTimeMillis = System.currentTimeMillis() - mStartTimeMillis;
        return DataPoint((currentTimeMillis).toDouble() / 1000.0, sin((currentTimeMillis + offsets[unitNumber]).toDouble() * mod))
    }
}