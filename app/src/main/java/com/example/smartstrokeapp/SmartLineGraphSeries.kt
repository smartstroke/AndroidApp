
package com.example.smartstrokeapp

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.animation.AccelerateInterpolator
import androidx.core.view.ViewCompat
import com.jjoe64.graphview.GraphView
import com.jjoe64.graphview.series.BaseSeries
import com.jjoe64.graphview.series.DataPointInterface

open class SmartLineGraphSeries<E : DataPointInterface> : SmartBaseSeries<E> {
    private inner class Styles {
        var thickness = 5
        var drawBackground = false
        var drawDataPoints = false
        var dataPointsRadius = 10f
        var backgroundColor = Color.argb(100, 172, 218, 255)
    }

    private var mStyles: Styles? = null
    private var mSelectionPaint: Paint? = null
    private var mPaint: Paint? = null
    private var mPaintBackground: Paint? = null
    private var mPathBackground: Path? = null
    private var mPath: Path? = null
    private var mCustomPaint: Paint? = null
    private var mAnimated = false
    private var mLastAnimatedValue = Double.NaN
    private var mAnimationStart: Long = 0
    private var mAnimationInterpolator: AccelerateInterpolator? = null
    private var mAnimationStartFrameNo = 0

    var isDrawAsPath = false

    constructor() {
        init()
    }

    constructor(data: Array<E>) : super(data) {
        init()
    }

    protected fun init() {
        mStyles = Styles()
        mPaint = Paint()
        mPaint!!.strokeCap = Paint.Cap.ROUND
        mPaint!!.style = Paint.Style.STROKE
        mPaintBackground = Paint()
        mSelectionPaint = Paint()
        mSelectionPaint!!.color = Color.argb(80, 0, 0, 0)
        mSelectionPaint!!.style = Paint.Style.FILL
        mPathBackground = Path()
        mPath = Path()
        mAnimationInterpolator = AccelerateInterpolator(2f)
    }

    override fun draw(graphView: GraphView, canvas: Canvas, isSecondScale: Boolean) {
        resetDataPoints()

        // get data
        val maxX = graphView.viewport.getMaxX(false)
        val minX = graphView.viewport.getMinX(false)
        val maxY: Double
        val minY: Double
        if (isSecondScale) {
            maxY = graphView.secondScale.getMaxY(false)
            minY = graphView.secondScale.getMinY(false)
        } else {
            maxY = graphView.viewport.getMaxY(false)
            minY = graphView.viewport.getMinY(false)
        }
        val values = getValues(minX, maxX)

        // draw background
        var lastEndY = 0.0
        var lastEndX = 0.0

        // draw data
        mPaint!!.strokeWidth = mStyles!!.thickness.toFloat()
        mPaint!!.color = color
        mPaintBackground!!.color = mStyles!!.backgroundColor
        val paint: Paint?
        paint = if (mCustomPaint != null) {
            mCustomPaint
        } else {
            mPaint
        }
        mPath!!.reset()
        if (mStyles!!.drawBackground) {
            mPathBackground!!.reset()
        }
        val diffY = maxY - minY
        val diffX = maxX - minX
        val graphHeight = graphView.graphContentHeight.toFloat()
        val graphWidth = graphView.graphContentWidth.toFloat()
        val graphLeft = graphView.graphContentLeft.toFloat()
        val graphTop = graphView.graphContentTop.toFloat()
        lastEndY = 0.0
        lastEndX = 0.0

        // needed to end the path for background
        var lastUsedEndX = 0.0
        var lastUsedEndY = 0.0
        var firstX = -1f
        var firstY = -1f
        var lastRenderedX = Float.NaN
        var i = 0
        var lastAnimationReferenceX = graphLeft
        var sameXSkip = false
        var minYOnSameX = 0f
        var maxYOnSameX = 0f
        while (values.hasNext()) {
            val value = values.next()
            val valY = value.y - minY
            val ratY = valY / diffY
            var y = graphHeight * ratY
            val valueX = value.x
            val valX = valueX - minX
            val ratX = valX / diffX
            var x = graphWidth * ratX
            val orgX = x
            val orgY = y
            if (i > 0) {
                // overdraw
                var isOverdrawY = false
                var isOverdrawEndPoint = false
                var skipDraw = false
                if (x > graphWidth) { // end right
                    val b = (graphWidth - lastEndX) * (y - lastEndY) / (x - lastEndX)
                    y = lastEndY + b
                    x = graphWidth.toDouble()
                    isOverdrawEndPoint = true
                }
                if (y < 0) { // end bottom
                    // skip when previous and this point is out of bound
                    if (lastEndY < 0) {
                        skipDraw = true
                    } else {
                        val b = (0 - lastEndY) * (x - lastEndX) / (y - lastEndY)
                        x = lastEndX + b
                    }
                    y = 0.0
                    isOverdrawEndPoint = true
                    isOverdrawY = isOverdrawEndPoint
                }
                if (y > graphHeight) { // end top
                    // skip when previous and this point is out of bound
                    if (lastEndY > graphHeight) {
                        skipDraw = true
                    } else {
                        val b = (graphHeight - lastEndY) * (x - lastEndX) / (y - lastEndY)
                        x = lastEndX + b
                    }
                    y = graphHeight.toDouble()
                    isOverdrawEndPoint = true
                    isOverdrawY = isOverdrawEndPoint
                }
                if (lastEndX < 0) { // start left
                    val b = (0 - x) * (y - lastEndY) / (lastEndX - x)
                    lastEndY = y - b
                    lastEndX = 0.0
                }

                // we need to save the X before it will be corrected when overdraw y
                val orgStartX = lastEndX.toFloat() + (graphLeft + 1)
                if (lastEndY < 0) { // start bottom
                    if (!skipDraw) {
                        val b = (0 - y) * (x - lastEndX) / (lastEndY - y)
                        lastEndX = x - b
                    }
                    lastEndY = 0.0
                    isOverdrawY = true
                }
                if (lastEndY > graphHeight) { // start top
                    // skip when previous and this point is out of bound
                    if (!skipDraw) {
                        val b = (graphHeight - y) * (x - lastEndX) / (lastEndY - y)
                        lastEndX = x - b
                    }
                    lastEndY = graphHeight.toDouble()
                    isOverdrawY = true
                }
                val startX = lastEndX.toFloat() + (graphLeft + 1)
                val startY = (graphTop - lastEndY).toFloat() + graphHeight
                val endX = x.toFloat() + (graphLeft + 1)
                val endY = (graphTop - y).toFloat() + graphHeight
                var startXAnimated = startX
                var endXAnimated = endX
                if (endX < startX) {
                    // dont draw from right to left
                    skipDraw = true
                }

                // NaN can happen when previous and current value is out of y bounds
                if (!skipDraw && !java.lang.Float.isNaN(startY) && !java.lang.Float.isNaN(endY)) {
                    // animation
                    if (mAnimated) {
                        if (java.lang.Double.isNaN(mLastAnimatedValue) || mLastAnimatedValue < valueX) {
                            val currentTime = System.currentTimeMillis()
                            if (mAnimationStart == 0L) {
                                // start animation
                                mAnimationStart = currentTime
                                mAnimationStartFrameNo = 0
                            } else {
                                // anti-lag: wait a few frames
                                if (mAnimationStartFrameNo < 15) {
                                    // second time
                                    mAnimationStart = currentTime
                                    mAnimationStartFrameNo++
                                }
                            }
                            val timeFactor =
                                (currentTime - mAnimationStart).toFloat() / ANIMATION_DURATION
                            val factor = mAnimationInterpolator!!.getInterpolation(timeFactor)
                            if (timeFactor <= 1.0) {
                                startXAnimated =
                                    (startX - lastAnimationReferenceX) * factor + lastAnimationReferenceX
                                startXAnimated = Math.max(startXAnimated, lastAnimationReferenceX)
                                endXAnimated =
                                    (endX - lastAnimationReferenceX) * factor + lastAnimationReferenceX
                                ViewCompat.postInvalidateOnAnimation(graphView)
                            } else {
                                // animation finished
                                mLastAnimatedValue = valueX
                            }
                        } else {
                            lastAnimationReferenceX = endX
                        }
                    }

                    // draw data point
                    if (!isOverdrawEndPoint) {
                        if (mStyles!!.drawDataPoints) {
                            // draw first datapoint
                            val prevStyle = paint!!.style
                            paint.style = Paint.Style.FILL
                            canvas.drawCircle(
                                endXAnimated, endY, mStyles!!.dataPointsRadius,
                                paint
                            )
                            paint.style = prevStyle
                        }
                        registerDataPoint(endX, endY, value)
                    }
                    if (isDrawAsPath) {
                        mPath!!.moveTo(startXAnimated, startY)
                    }
                    // performance opt.
                    if (java.lang.Float.isNaN(lastRenderedX) || Math.abs(endX - lastRenderedX) > .3f) {
                        if (isDrawAsPath) {
                            mPath!!.lineTo(endXAnimated, endY)
                        } else {
                            // draw vertical lines that were skipped
                            if (sameXSkip) {
                                sameXSkip = false
                                renderLine(
                                    canvas,
                                    floatArrayOf(
                                        lastRenderedX,
                                        minYOnSameX,
                                        lastRenderedX,
                                        maxYOnSameX
                                    ),
                                    paint
                                )
                            }
                            renderLine(
                                canvas,
                                floatArrayOf(startXAnimated, startY, endXAnimated, endY),
                                paint
                            )
                        }
                        lastRenderedX = endX
                    } else {
                        // rendering on same x position
                        // save min+max y position and draw it as line
                        if (sameXSkip) {
                            minYOnSameX = Math.min(minYOnSameX, endY)
                            maxYOnSameX = Math.max(maxYOnSameX, endY)
                        } else {
                            // first
                            sameXSkip = true
                            minYOnSameX = Math.min(startY, endY)
                            maxYOnSameX = Math.max(startY, endY)
                        }
                    }
                }
                if (mStyles!!.drawBackground) {
                    if (isOverdrawY) {
                        // start draw original x
                        if (firstX == -1f) {
                            firstX = orgStartX
                            firstY = startY
                            mPathBackground!!.moveTo(orgStartX, startY)
                        }
                        // from original start to new start
                        mPathBackground!!.lineTo(startXAnimated, startY)
                    }
                    if (firstX == -1f) {
                        firstX = startXAnimated
                        firstY = startY
                        mPathBackground!!.moveTo(startXAnimated, startY)
                    }
                    mPathBackground!!.lineTo(startXAnimated, startY)
                    mPathBackground!!.lineTo(endXAnimated, endY)
                }
                lastUsedEndX = endXAnimated.toDouble()
                lastUsedEndY = endY.toDouble()
            } else if (mStyles!!.drawDataPoints) {
                //fix: last value not drawn as datapoint. Draw first point here, and then on every step the end values (above)
                var first_X = x.toFloat() + (graphLeft + 1)
                val first_Y = (graphTop - y).toFloat() + graphHeight
                if (first_X >= graphLeft && first_Y <= graphTop + graphHeight) {
                    if (mAnimated && (java.lang.Double.isNaN(mLastAnimatedValue) || mLastAnimatedValue < valueX)) {
                        val currentTime = System.currentTimeMillis()
                        if (mAnimationStart == 0L) {
                            // start animation
                            mAnimationStart = currentTime
                        }
                        val timeFactor =
                            (currentTime - mAnimationStart).toFloat() / ANIMATION_DURATION
                        val factor = mAnimationInterpolator!!.getInterpolation(timeFactor)
                        if (timeFactor <= 1.0) {
                            first_X =
                                (first_X - lastAnimationReferenceX) * factor + lastAnimationReferenceX
                            ViewCompat.postInvalidateOnAnimation(graphView)
                        } else {
                            // animation finished
                            mLastAnimatedValue = valueX
                        }
                    }
                    val prevStyle = paint!!.style
                    paint.style = Paint.Style.FILL
                    canvas.drawCircle(first_X, first_Y, mStyles!!.dataPointsRadius, paint)
                    paint.style = prevStyle
                    registerDataPoint(first_X, first_Y, value)
                }
            }
            lastEndY = orgY
            lastEndX = orgX
            i++
        }
        if (isDrawAsPath) {
            // draw at the end
            canvas.drawPath(mPath!!, paint!!)
        }
        if (mStyles!!.drawBackground && firstX != -1f) {
            // end / close path
            if (lastUsedEndY != (graphHeight + graphTop).toDouble()) {
                // dont draw line to same point, otherwise the path is completely broken
                mPathBackground!!.lineTo(lastUsedEndX.toFloat(), graphHeight + graphTop)
            }
            mPathBackground!!.lineTo(firstX, graphHeight + graphTop)
            if (firstY != graphHeight + graphTop) {
                // dont draw line to same point, otherwise the path is completely broken
                mPathBackground!!.lineTo(firstX, firstY)
            }
            //mPathBackground.close();
            canvas.drawPath(mPathBackground!!, mPaintBackground!!)
        }
    }

    private fun renderLine(canvas: Canvas, pts: FloatArray, paint: Paint?) {
        if ((pts.size == 4) && (pts[0] == pts[2]) && (pts[1] == pts[3])) {
            // avoid zero length lines, to makes troubles on some devices
            // see https://github.com/appsthatmatter/GraphView/issues/499
            return
        }
        canvas.drawLines(pts, paint!!)
    }

    var thickness: Int
        get() = mStyles!!.thickness
        set(thickness) {
            mStyles!!.thickness = thickness
        }

    var isDrawBackground: Boolean
        get() = mStyles!!.drawBackground
        set(drawBackground) {
            mStyles!!.drawBackground = drawBackground
        }

    var isDrawDataPoints: Boolean
        get() = mStyles!!.drawDataPoints
        set(drawDataPoints) {
            mStyles!!.drawDataPoints = drawDataPoints
        }

    var dataPointsRadius: Float
        get() = mStyles!!.dataPointsRadius
        set(dataPointsRadius) {
            mStyles!!.dataPointsRadius = dataPointsRadius
        }

    var backgroundColor: Int
        get() = mStyles!!.backgroundColor
        set(backgroundColor) {
            mStyles!!.backgroundColor = backgroundColor
        }

    fun setCustomPaint(customPaint: Paint?) {
        mCustomPaint = customPaint
    }

    fun setAnimated(animated: Boolean) {
        mAnimated = animated
    }

    override fun appendData(
        dataPoint: E,
        scrollToEnd: Boolean,
        maxDataPoints: Int,
        silent: Boolean
    ) {
        if (!isAnimationActive) {
            mAnimationStart = 0
        }
        super.appendData(dataPoint, scrollToEnd, maxDataPoints, silent)
    }

    private val isAnimationActive: Boolean
        get() {
            if (mAnimated) {
                val curr = System.currentTimeMillis()
                return curr - mAnimationStart <= ANIMATION_DURATION
            }
            return false
        }

    override fun drawSelection(
        graphView: GraphView,
        canvas: Canvas,
        b: Boolean,
        value: DataPointInterface
    ) {
        val spanX = graphView.viewport.getMaxX(false) - graphView.viewport.getMinX(false)
        val spanXPixel = graphView.graphContentWidth.toDouble()
        val spanY = graphView.viewport.getMaxY(false) - graphView.viewport.getMinY(false)
        val spanYPixel = graphView.graphContentHeight.toDouble()
        var pointX = (value.x - graphView.viewport.getMinX(false)) * spanXPixel / spanX
        pointX += graphView.graphContentLeft.toDouble()
        var pointY = (value.y - graphView.viewport.getMinY(false)) * spanYPixel / spanY
        pointY = graphView.graphContentTop + spanYPixel - pointY

        // border
        canvas.drawCircle(pointX.toFloat(), pointY.toFloat(), 30f, mSelectionPaint!!)

        // fill
        val prevStyle = mPaint!!.style
        mPaint!!.style = Paint.Style.FILL
        canvas.drawCircle(pointX.toFloat(), pointY.toFloat(), 23f, mPaint!!)
        mPaint!!.style = prevStyle
    }

    companion object {
        private const val ANIMATION_DURATION: Long = 333
    }
}