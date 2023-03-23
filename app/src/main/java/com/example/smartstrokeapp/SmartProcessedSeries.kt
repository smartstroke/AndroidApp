package com.example.smartstrokeapp

import com.jjoe64.graphview.series.DataPoint
import com.jjoe64.graphview.series.DataPointInterface
import com.jjoe64.graphview.series.LineGraphSeries
import com.jjoe64.graphview.series.PointsGraphSeries
import kotlin.collections.ArrayList
import kotlin.math.abs
import kotlin.math.sqrt

class SmartProcessedSeries<E : DataPointInterface> : SmartLineGraphSeries<E> {

    enum class Signal(val mLevel: Double) {
        MIN(-1.0),
        MID(0.0),
        MAX(1.0)
    }

    var onFinishCallback: (() -> Unit)? = null
    var onStrokeCallback: (() -> Unit)? = null

    var filling = true

    var mSignals: MutableList<Double> = ArrayList()
    var mFilteredInput: MutableList<DataPoint> = ArrayList()
    var mInMean: MutableList<Double> = ArrayList()
    var mFilteredMean: MutableList<Double> = ArrayList()
    var mFilteredStdDev: MutableList<Double> = ArrayList()
    var mFilteredStdDevUp: MutableList<Double> = ArrayList()
    var mFilteredStdDevDwn: MutableList<Double> = ArrayList()
    var mTrackedStrokes: MutableList<DataPoint> = ArrayList()

    var mGraphableMean: LineGraphSeries<DataPoint> = LineGraphSeries<DataPoint>()
    var mGraphableStdDevUp: LineGraphSeries<DataPoint> = LineGraphSeries<DataPoint>()
    var mGraphableStdDevDwn: LineGraphSeries<DataPoint> = LineGraphSeries<DataPoint>()
    var mGraphableSignal: LineGraphSeries<DataPoint> = LineGraphSeries<DataPoint>()
    var mGraphableStrokes: PointsGraphSeries<DataPoint> = PointsGraphSeries<DataPoint>()

    private fun MutableList<Double>.addWithMax(value: Double, maxItems: Int): Double? {
        this.add(value)
        if (maxItems >= this.size) {
            return null
        }
        return this.removeAt(0)
    }

    private fun MutableList<DataPoint>.addWithMax(value: DataPoint, maxItems: Int): DataPoint? {
        this.add(value)
        if (maxItems >= this.size) {
            return null
        }
        return this.removeAt(0)
    }

    constructor() {
        init()
    }

    constructor(data: Array<E>) : super(data) {
        init()
    }

    init {

    }

    var mLag: Int = 50
    var mThreshold:Float = 0.3f
    var mInfluence: Float = 0.5f

    var mSumData: Double = 0.0
    var mSumSqrs: Double = 0.0

    fun appendData(
        dataPoint: E,
        scrollToEnd: Boolean,
        silent: Boolean
    ) {
        if ((!mData.isEmpty() && dataPoint.x < mData[mData.size - 1].x)) {
            return
        }
        var oldPoint: DataPoint? = null
        filling = mData.size < mLag

        super.appendData(dataPoint, scrollToEnd, mLag, silent)

        var last = mData.size - 1
        var secondLast = mData.size - 2

        // We have enough data
        if (mData.size > 3) {
            if (abs(dataPoint.y - mFilteredMean[secondLast]) > mThreshold * mFilteredStdDev[secondLast]) {
                if (dataPoint.y > mFilteredMean[secondLast]) {
                    mSignals.addWithMax(Signal.MAX.mLevel, mLag) // Positive signal, Stroke peak
                    mGraphableSignal.appendData(DataPoint(dataPoint.x, Signal.MAX.mLevel), false, mLag, true)
                }
                else {
                    mSignals.addWithMax(Signal.MIN.mLevel, mLag) // Negative signal, Recovery
                    mGraphableSignal.appendData(DataPoint(dataPoint.x, Signal.MIN.mLevel), false, mLag, true)
                }
                val newPoint = DataPoint(dataPoint.x, mInfluence * dataPoint.y + (1.0f - mInfluence) * mFilteredInput.last().y)
                oldPoint = mFilteredInput.addWithMax(newPoint, mLag)
            }
            else {
                mSignals.addWithMax(Signal.MID.mLevel, mLag) // No signal, Entering/Exiting stroke
                mGraphableSignal.appendData(DataPoint(dataPoint.x, Signal.MID.mLevel), false, mLag, true)
                val newPoint = DataPoint(dataPoint.x, dataPoint.y)
                oldPoint = mFilteredInput.addWithMax(newPoint, mLag)
            }
        }
        else {
            mSignals.addWithMax(Signal.MID.mLevel, mLag) // No signal, Entering/Exiting stroke
            mGraphableSignal.appendData(DataPoint(dataPoint.x, Signal.MID.mLevel), false, mLag, true)
            val newPoint = DataPoint(dataPoint.x, dataPoint.y)
            mFilteredInput.addWithMax(newPoint, mLag)
        }

        // Initialization
        if (filling) {
            mSumData += dataPoint.y
            mInMean.addWithMax(dataPoint.y, mLag)
            val mean = mSumData / mLag
            mFilteredMean.addWithMax(mean, mLag)
            mGraphableMean.appendData(DataPoint(dataPoint.x, mean), false, mLag, true)

            mSumSqrs += dataPoint.y * dataPoint.y
            mFilteredStdDev.addWithMax(sqrt(mSumSqrs / mLag), mLag)

            val stdDevUp = mFilteredMean.last() + (mThreshold * mFilteredStdDev.last())
            mFilteredStdDevUp.addWithMax(stdDevUp, mLag)
            mGraphableStdDevUp.appendData(DataPoint(dataPoint.x, stdDevUp), false, mLag, true)

            val stdDevDwn = mFilteredMean.last() - (mThreshold * mFilteredStdDev.last())
            mFilteredStdDevDwn.addWithMax(stdDevDwn, mLag)
            mGraphableStdDevDwn.appendData(DataPoint(dataPoint.x, stdDevDwn), false, mLag, true)
        }
        else {
            // update mean/stddev
            mSumData -= oldPoint!!.y
            mSumData += mFilteredInput[last].y
            mInMean.addWithMax(mFilteredInput[last].y, mLag)
            val sum = mInMean.sum()
            val mean = mSumData / mLag
            mFilteredMean.addWithMax(mean, mLag)
            mGraphableMean.appendData(DataPoint(dataPoint.x, mean), false, mLag, true)

            mSumSqrs -= oldPoint.y * oldPoint.y
            mSumSqrs += mFilteredInput[last].y * mFilteredInput[last].y
            mFilteredStdDev.addWithMax(sqrt(mSumSqrs / mLag), mLag)

            val stdDevUp = mFilteredMean[last] + mThreshold * mFilteredStdDev[last]
            mFilteredStdDevUp.addWithMax(stdDevUp, mLag)
            mGraphableStdDevUp.appendData(DataPoint(dataPoint.x, stdDevUp), false, mLag, true)

            val stdDevDwn = mFilteredMean[last] - mThreshold * mFilteredStdDev[last]
            mFilteredStdDevDwn.addWithMax(stdDevDwn, mLag)
            mGraphableStdDevDwn.appendData(DataPoint(dataPoint.x, stdDevDwn), false, mLag, true)
        }

        if (mData.size < 3) {//filling) {
            return
        }
        // Stroke detection
        val postFallValue = mSignals[last]
        val preFallValue = mSignals[secondLast]

        val fallValueChanged = (postFallValue != preFallValue)
        val fromExtreme = (preFallValue != Signal.MID.mLevel)
        val isFallingEdge = fallValueChanged && fromExtreme
        if (!isFallingEdge) {
            onFinishCallback?.let { it.invoke() }
            return
        }

        val fallingEdgeIndex = secondLast
        var risingEdgeIndex = 0
        var indexToCheck = secondLast - 1
        var lastIndexChecked: Int
        var foundRisingEdge = false
        while (!foundRisingEdge) {
            lastIndexChecked = indexToCheck
            indexToCheck--

            if (indexToCheck < 0) {
                break
            }

            val preRiseValue = mSignals[indexToCheck]
            val postRiseValue = mSignals[lastIndexChecked]
            val riseValueChanged = (preRiseValue != postRiseValue)
            val fromSameExtreme = (postRiseValue == preFallValue)
            foundRisingEdge = riseValueChanged && fromSameExtreme
            if (foundRisingEdge) {
                risingEdgeIndex = lastIndexChecked
            }
        }

        if (preFallValue == Signal.MAX.mLevel) {
            val risingEdgeTime = mData[risingEdgeIndex].x
            val fallingEdgeTime = mData[fallingEdgeIndex].x
            val strokeCenterTime = (risingEdgeTime + fallingEdgeTime)/2
            if (mTrackedStrokes.size >= mLag) {
                mTrackedStrokes.removeAt(0)
            }
            mTrackedStrokes.add(DataPoint(strokeCenterTime, preFallValue))
            onStrokeCallback?.let { it.invoke() }

            mGraphableStrokes.appendData(
                DataPoint(strokeCenterTime, preFallValue),
                false,
                mLag,
                true
            )
        }
        onFinishCallback?.let { it.invoke() }
    }
}