
package com.example.smartstrokeapp

import android.graphics.Canvas
import android.graphics.PointF
import com.jjoe64.graphview.GraphView
import com.jjoe64.graphview.series.DataPointInterface
import com.jjoe64.graphview.series.OnDataPointTapListener
import com.jjoe64.graphview.series.Series
import java.lang.ref.WeakReference

abstract class SmartBaseSeries<E : DataPointInterface> : Series<E> {
    protected val mData: MutableList<E> = ArrayList()
    private val mDataPoints: MutableMap<PointF, E> = HashMap()
    private var mTitle: String? = null
    private var mColor = -0xff8834
    private var mLowestYCache = Double.NaN
    private var mHighestYCache = Double.NaN
    protected var mOnDataPointTapListener: OnDataPointTapListener? = null
    private var mGraphViews: MutableList<WeakReference<GraphView?>>
    private var mIsCursorModeCache: Boolean? = null

    constructor() {
        mGraphViews = ArrayList()
    }

    constructor(data: Array<E>) {
        mGraphViews = ArrayList()
        for (d in data) {
            mData.add(d)
        }
        checkValueOrder(null)
    }

    override fun getLowestValueX(): Double {
        return if (mData.isEmpty()) 0.0 else mData[0].x
    }

    override fun getHighestValueX(): Double {
        return if (mData.isEmpty()) 0.0 else mData[mData.size - 1].x
    }

    override fun getLowestValueY(): Double {
        if (mData.isEmpty()) return 0.0
        if (!java.lang.Double.isNaN(mLowestYCache)) {
            return mLowestYCache
        }
        var l = mData[0].y
        for (i in 1 until mData.size) {
            val c = mData[i].y
            if (l > c) {
                l = c
            }
        }
        return l.also { mLowestYCache = it }
    }

    override fun getHighestValueY(): Double {
        if (mData.isEmpty()) return 0.0
        if (!java.lang.Double.isNaN(mHighestYCache)) {
            return mHighestYCache
        }
        var h = mData[0].y
        for (i in 1 until mData.size) {
            val c = mData[i].y
            if (h < c) {
                h = c
            }
        }
        return h.also { mHighestYCache = it }
    }

    override fun getValues(from: Double, until: Double): Iterator<E> {
        return if (from <= lowestValueX && until >= highestValueX) {
            mData.iterator()
        } else {
            object : MutableIterator<E> {
                var org: Iterator<E> = mData.iterator()
                var nextValue: E? = null
                var nextNextValue: E? = null
                var plusOne = true

                init {
                    // go to first
                    var found = false
                    var prevValue: E? = null
                    if (org.hasNext()) {
                        prevValue = org.next()
                    }
                    if (prevValue != null) {
                        if (prevValue.x >= from) {
                            nextValue = prevValue
                            found = true
                        } else {
                            while (org.hasNext()) {
                                nextValue = org.next()
                                if (nextValue!!.x >= from) {
                                    found = true
                                    nextNextValue = nextValue
                                    nextValue = prevValue
                                    break
                                }
                                prevValue = nextValue
                            }
                        }
                    }
                    if (!found) {
                        nextValue = null
                    }
                }

                override fun remove() {
                    throw UnsupportedOperationException()
                }

                override fun next(): E {
                    return if (hasNext()) {
                        val r = nextValue
                        if (r!!.x > until) {
                            plusOne = false
                        }
                        if (nextNextValue != null) {
                            nextValue = nextNextValue
                            nextNextValue = null
                        } else if (org.hasNext()) nextValue = org.next() else nextValue = null
                        r
                    } else {
                        throw NoSuchElementException()
                    }
                }

                override fun hasNext(): Boolean {
                    return nextValue != null && (nextValue!!.x <= until || plusOne)
                }
            }
        }
    }

    override fun getTitle(): String {
        return mTitle!!
    }

    fun setTitle(mTitle: String?) {
        this.mTitle = mTitle
    }

    override fun getColor(): Int {
        return mColor
    }

    fun setColor(mColor: Int) {
        this.mColor = mColor
    }

    override fun setOnDataPointTapListener(l: OnDataPointTapListener) {
        mOnDataPointTapListener = l
    }

    override fun onTap(x: Float, y: Float) {
        if (mOnDataPointTapListener != null) {
            val p = findDataPoint(x, y)
            if (p != null) {
                mOnDataPointTapListener!!.onTap(this, p)
            }
        }
    }

    protected open fun findDataPoint(x: Float, y: Float): E? {
        var shortestDistance = Float.NaN
        var shortest: E? = null
        for ((key, value) in mDataPoints) {
            val x1 = key.x
            val y1 = key.y
            val distance =
                Math.sqrt(((x1 - x) * (x1 - x) + (y1 - y) * (y1 - y)).toDouble()).toFloat()
            if (shortest == null || distance < shortestDistance) {
                shortestDistance = distance
                shortest = value
            }
        }
        if (shortest != null) {
            if (shortestDistance < 120) {
                return shortest
            }
        }
        return null
    }

    fun findDataPointAtX(x: Float): E? {
        var shortestDistance = Float.NaN
        var shortest: E? = null
        for ((key, value) in mDataPoints) {
            val x1 = key.x
            val distance = Math.abs(x1 - x)
            if (shortest == null || distance < shortestDistance) {
                shortestDistance = distance
                shortest = value
            }
        }
        if (shortest != null) {
            if (shortestDistance < 200) {
                return shortest
            }
        }
        return null
    }

    protected fun registerDataPoint(x: Float, y: Float, dp: E) {
        // performance
        // TODO maybe invalidate after setting the listener
        if (mOnDataPointTapListener != null || isCursorMode) {
            mDataPoints[PointF(x, y)] = dp
        }
    }

    private val isCursorMode: Boolean
        get() {
            if (mIsCursorModeCache != null) {
                return mIsCursorModeCache as Boolean
            }
            for (graphView in mGraphViews) {
                if ((graphView != null) && (graphView.get() != null) && (graphView.get()!!.isCursorMode)) {
                    return true.also { mIsCursorModeCache = it }
                }
            }
            return false.also { mIsCursorModeCache = it }
        }

    protected open fun resetDataPoints() {
        mDataPoints.clear()
    }

    fun resetData(data: Array<E>) {
        mData.clear()
        for (d in data) {
            mData.add(d)
        }
        checkValueOrder(null)
        mLowestYCache = Double.NaN
        mHighestYCache = mLowestYCache

        // update graphview
        for (gv in mGraphViews) {
            if (gv != null && gv.get() != null) {
                gv.get()!!.onDataChanged(true, false)
            }
        }
    }

    override fun onGraphViewAttached(graphView: GraphView) {
        mGraphViews.add(WeakReference(graphView))
    }

    @JvmOverloads
    open fun appendData(
        dataPoint: E,
        scrollToEnd: Boolean,
        maxDataPoints: Int,
        silent: Boolean = false
    ) {
        checkValueOrder(dataPoint)
        require(!(!mData.isEmpty() && dataPoint.x < mData[mData.size - 1].x)) { "new x-value must be greater then the last value. x-values has to be ordered in ASC." }
        synchronized(mData) {
            val curDataCount = mData.size
            if (curDataCount < maxDataPoints) {
                // enough space
                mData.add(dataPoint)
            } else {
                // we have to trim one data
                mData.removeAt(0)
                mData.add(dataPoint)
            }

            // update lowest/highest cache
            val dataPointY = dataPoint.y
            if (!java.lang.Double.isNaN(mHighestYCache)) {
                if (dataPointY > mHighestYCache) {
                    mHighestYCache = dataPointY
                }
            }
            if (!java.lang.Double.isNaN(mLowestYCache)) {
                if (dataPointY < mLowestYCache) {
                    mLowestYCache = dataPointY
                }
            }
        }
        if (!silent) {
            // recalc the labels when it was the first data
            val keepLabels = mData.size != 1

            // update linked graph views
            // update graphview
            for (gv in mGraphViews) {
                if (gv != null && gv.get() != null) {
                    if (scrollToEnd) {
                        gv.get()!!.viewport.scrollToEnd()
                    } else {
                        gv.get()!!.onDataChanged(keepLabels, scrollToEnd)
                    }
                }
            }
        }
    }

    override fun isEmpty(): Boolean {
        return mData.isEmpty()
    }

    protected fun checkValueOrder(onlyLast: DataPointInterface?) {
        if (mData.size > 1) {
            if (onlyLast != null) {
                // only check last
                require(onlyLast.x >= mData[mData.size - 1].x) { "new x-value must be greater then the last value. x-values has to be ordered in ASC." }
            } else {
                var lx = mData[0].x
                for (i in 1 until mData.size) {
                    if (mData[i].x != Double.NaN) {
                        require(lx <= mData[i].x) { "The order of the values is not correct. X-Values have to be ordered ASC. First the lowest x value and at least the highest x value." }
                        lx = mData[i].x
                    }
                }
            }
        }
    }

    abstract fun drawSelection(
        graphView: GraphView,
        canvas: Canvas,
        b: Boolean,
        value: DataPointInterface
    )

    fun clearCursorModeCache() {
        mIsCursorModeCache = null
    }

    override fun clearReference(graphView: GraphView) {
        // find and remove
        for (view in mGraphViews) {
            if ((view != null) && (view.get() != null) && (view.get() === graphView)) {
                mGraphViews.remove(view)
                break
            }
        }
    }
}