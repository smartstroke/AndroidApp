package com.example.smartstrokeapp

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.IBinder
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.jjoe64.graphview.GraphView
import com.jjoe64.graphview.series.DataPoint
import kotlinx.coroutines.*
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import java.lang.Integer.min
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

class SessionActivity : AppCompatActivity() {
    var isDebugMode: Boolean = false
    var withTestCases: Boolean = false
    private var bluetoothService : BluetoothLeService? = null
    lateinit var dataStoreManager: DataStoreManager
    private var currentBleTarget: String = ""

    private var connectedUnits: Int = 4
    private lateinit var cell1: TextView
    private lateinit var cell2: TextView
    private lateinit var cell3: TextView
    private lateinit var cell4: TextView
    private var delayText = mutableListOf<TextView>()
    private lateinit var timerText: TextView
    private lateinit var endSessionButton: Button

    private lateinit var videoBackground: View
    private lateinit var videoView: VideoView

    private var dataVectors: Vector<SmartProcessedSeries<DataPoint>> = Vector<SmartProcessedSeries<DataPoint>>()
    private var seriesReady = false
    private var keepGoing = true
    private var isDataGood = false

    private var strokeCounters = IntArray(connectedUnits) { 0 }
    private val strokesPerSplit = mutableListOf<Int>()

    private lateinit var graph: GraphView
    private val xInterval = 10.0;
    private var xMin = 0.0;
    private var xMax = xInterval;

    private val threshold = 10 // Replace with your threshold
    private var timeNotGreenMs = Array(4){ mutableListOf<Long>() } // array for storing time not green for each cell
    private var splitStartTimes = Array(4){ System.currentTimeMillis() } // array for storing the start time of each split for each cell
    private var isCellGreen = Array(4){ SyncLevel.GREEN }
    private var alertDialog: AlertDialog? = null
    private var millisUntilFinished: Long = 0

    private val TAG: String = "SessionBLE"
    private val serviceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(
            componentName: ComponentName,
            service: IBinder
        ) {
            Log.d(TAG, "on Bluetooth service connected")
            bluetoothService = (service as BluetoothLeService.LocalBinder).getService()
            bluetoothService?.let { bluetooth ->
                if (!bluetooth.initialize()) {
                    Log.e(TAG, "Unable to initialize Bluetooth")
                    finish()
                }
                else {
                    Log.d(TAG, "Bluetooth service initialized")
                    bluetoothService!!.connect(currentBleTarget)
                }
            }
        }

        override fun onServiceDisconnected(componentName: ComponentName) {
            Log.d(TAG, "on Bluetooth service disconnected")
            bluetoothService = null
        }
    }

    private fun makeGattUpdateIntentFilter(): IntentFilter? {
        return IntentFilter().apply {
            addAction(BluetoothLeService.ACTION_GATT_CONNECTED)
            addAction(BluetoothLeService.ACTION_GATT_ALREADY_CONNECTED)
            addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED)
            addAction(BluetoothLeService.ACTION_DATA_AVAILABLE)
        }
    }

    var failCount = 0
    val failMax = 1
    var connected = false
    private val gattUpdateReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, broadcastIntent: Intent) {
            when (broadcastIntent.action) {
                BluetoothLeService.ACTION_GATT_CONNECTED -> {
                    connected = true
                    failCount = 0
                    bluetoothService?.readFsrData()
                }
                BluetoothLeService.ACTION_GATT_ALREADY_CONNECTED -> {
                    connected = true
                    failCount = 0
                    bluetoothService?.readFsrData()
                }
                BluetoothLeService.ACTION_GATT_DISCONNECTED -> {
                    connected = false
                    Log.e("failcount: %s", failCount.toString())
                    if (failCount <= failMax) {
                        bluetoothService!!.connect(currentBleTarget)
                        failCount++
                    }
                    else {
                        finish()
                    }
                }
                BluetoothLeService.ACTION_DATA_AVAILABLE -> {
                    addPointOnReceive(broadcastIntent)
                    if (keepGoing && seriesReady && (bluetoothService?.fsrReady() == true)) {
                        bluetoothService?.readFsrData()
                    }
                }
            }
        }
    }

    var lastTimeStamp = 0.0
    var firstTimeStamp = 0.0

    fun addPointOnReceive(broadcastIntent: Intent) {
        val extras = broadcastIntent.extras ?: return
        val unit = extras.getInt("FSR_TARGET", -1)

        if (isDebugMode && (unit >= connectedUnits)) {
            return
        }
        lastTimeStamp = broadcastIntent.extras!!.getDouble("FSR_TIME") / 1000.0

        if (firstTimeStamp == 0.0) {
            firstTimeStamp = lastTimeStamp

            // Initialize splitStartTimes after the first data receiving
            splitStartTimes = Array(4){ System.currentTimeMillis() }
            countDownTimer.start()
            videoView.visibility = View.GONE
            videoBackground.visibility = View.GONE
            return
        }

        var value = broadcastIntent.extras!!.getDouble("FSR_VALUE")
        if (false) {
            if (value < -0.3) {
                value = -1.0
            }
            if (withTestCases && (unit == 2)) {
                if ((timeNotGreenMs[0].size % 2) == 1) {
                    value = -1.0
                }
            } else if (withTestCases && (unit == 3)) {
                value = -1.0
            }
        }

        addPoint(unit, DataPoint(lastTimeStamp - firstTimeStamp, value))
    }

    private val countDownTimer = object : CountDownTimer(Long.MAX_VALUE, 1000) {
        @SuppressLint("SetTextI18n")
        override fun onTick(millisUntilFinished: Long) {
            if(firstTimeStamp == 0.0) {
                return
            }

            this@SessionActivity.millisUntilFinished = millisUntilFinished // Update the value here

            val seconds = (Long.MAX_VALUE - millisUntilFinished) / 1000
            val minutes = seconds / 60
            val remainingSeconds = seconds % 60
            timerText.text = "${minutes.toString().padStart(2, '0')}:${remainingSeconds.toString().padStart(2, '0')}"

            if (((seconds.toInt() % 60) == 0) && (seconds.toInt()!=0)) { // new minute => new split
                for (i in strokeCounters.indices) {
                    strokesPerSplit.add(strokeCounters[i])
                    strokeCounters[i] = 0
                }
                newSplit()
            }
            if (isDataGood) {
                checkReceivedData()
            }
        }

        override fun onFinish() {
            // Timer will never finish
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        supportActionBar?.hide()
        super.onCreate(savedInstanceState)
        isDebugMode = intent.extras?.getBoolean("DebugMode")!!

        val gattServiceIntent = Intent(this, BluetoothLeService::class.java)
        bindService(gattServiceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
        registerReceiver(gattUpdateReceiver, makeGattUpdateIntentFilter())

        dataStoreManager = (applicationContext as MyApplication).dataStoreManager
        GlobalScope.launch(Dispatchers.IO) {
            currentBleTarget = dataStoreManager.getBleAddressString()
            Log.d(TAG, "Loading $currentBleTarget from DataStore")
        }

        setContentView(R.layout.activity_session)
        delayText.add(findViewById(R.id.cell1_small_text))
        delayText.add(findViewById(R.id.cell2_small_text))
        delayText.add(findViewById(R.id.cell3_small_text))
        delayText.add(findViewById(R.id.cell4_small_text))

        for (text in delayText) {
            text.text = "calculating..."
        }

        // Here's where you'd put the video code
        videoBackground = findViewById<View>(R.id.videoBackground)
        videoView = findViewById<VideoView>(R.id.videoView)
        val mediaController = MediaController(this)
        mediaController.setAnchorView(videoView)
        val uri = Uri.parse("android.resource://" + packageName + "/" + R.raw.loading)
        videoView.setMediaController(mediaController)
        videoView.setVideoURI(uri)
        videoView.requestFocus()
        videoView.start()
        videoView.setOnCompletionListener {
            videoView.visibility = View.GONE
            videoBackground.visibility = View.GONE
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            // Permission is not available, so request the user to grant the permission.
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 0)
        }

        val backButton: Button = findViewById(R.id.Backbutton)
        backButton.setOnClickListener {
            onBackPressed()
        }

        // Initialize timeNotGreenMs with a 0 value for the first split for each cell
        for (i in 0 until 4) {
            timeNotGreenMs[i].add(0)
        }
        // Initialize isCellGreen as true for all cells
        isCellGreen = Array(4) { SyncLevel.GREEN }

        cell1 = findViewById(R.id.cell1)
        cell1.text = "L1"
        cell2 = findViewById(R.id.cell2)
        cell2.text = "R1"
        cell3 = findViewById(R.id.cell3)
        cell3.text = "L2"
        cell4 = findViewById(R.id.cell4)
        cell4.text = "R2"

        cell1.setOnClickListener { startEditing(cell1) }
        cell2.setOnClickListener { startEditing(cell2) }
        cell3.setOnClickListener { startEditing(cell3) }
        cell4.setOnClickListener { startEditing(cell4) }

        timerText = findViewById(R.id.timer_text)

        endSessionButton = findViewById(R.id.end_session_button)
        endSessionButton.setOnClickListener {
            keepGoing = false
            showTable(strokesPerSplit)  // call the showTable function
        }

        populateDataVectors()
    }

    var strokeDelays = MutableList<Double?>(4) { null }
    private fun populateDataVectors() {
        graph = findViewById<View>(R.id.graph) as GraphView
        if (isDebugMode) {
            graph.viewport.isXAxisBoundsManual = true
            graph.viewport.setMinX(xMin)
            graph.viewport.setMaxX(xMax)
            graph.viewport.isYAxisBoundsManual = true
            graph.viewport.setMinY(-1.0)
            graph.viewport.setMaxY(1.0)
        }
        else {
            graph.isVisible = false
        }

        val colorList = arrayOf(Color.CYAN, Color.BLUE, Color.GREEN, Color.RED)

        for(unit in 0 until connectedUnits) {
            dataVectors.add(SmartProcessedSeries())
            if (unit == connectedUnits - 1) {
                dataVectors[unit].onFinishCallback = {
                    compareStrokes()
                }
            }
            dataVectors[unit].onStrokeCallback = {
                strokeCounters[unit]++
            }

            if (!isDebugMode) {
                continue
            }
            dataVectors[unit].color = colorList[unit]
            graph.addSeries(dataVectors[unit])
            if (unit == 1) {
                graph.addSeries(dataVectors[unit].mGraphableMean)
                dataVectors[unit].mGraphableMean.color = Color.GRAY
                graph.addSeries(dataVectors[unit].mGraphableStdDevUp)
                dataVectors[unit].mGraphableStdDevUp.color = Color.LTGRAY
                graph.addSeries(dataVectors[unit].mGraphableStdDevDwn)
                dataVectors[unit].mGraphableStdDevDwn.color = Color.LTGRAY
                graph.addSeries(dataVectors[unit].mGraphableSignal)
                dataVectors[unit].mGraphableSignal.color = Color.YELLOW
            }
            graph.addSeries(dataVectors[unit].mGraphableStrokes)
            dataVectors[unit].mGraphableStrokes.color = colorList[unit]
        }
        seriesReady = true
    }

    // I'm so sorry this function is such garbage,
    // but I probably won't fix it.
    // Love, Brigham
    private fun compareStrokes() {
        val min = 1
        val strokesToCheck = 3

        var keyStrokes = mutableListOf<DataPoint>()
        var matchDirection: MutableList<Pair<Int, Double>> = MutableList(strokesToCheck) { Pair(-2, 0.0) }
        strokeDelays.replaceAll { null }
        for (unit in 0 until connectedUnits) {
            val unitStrokes = dataVectors[unit].mTrackedStrokes
            val notEnoughTotalStrokes = unitStrokes.size < (strokesToCheck + min)
            if (notEnoughTotalStrokes) {
                if (unit == 0) {
                    break
                }
                continue
            }
            val useReducedStrokes = unitStrokes.lastIndex <= (strokesToCheck + 2)
            when(unit) {
                0 -> {
                    isDataGood = true
                    for (stroke in 0 until strokesToCheck) {
                        val trackedIndex = unitStrokes.lastIndex - (stroke + min)
                        keyStrokes.add(unitStrokes[trackedIndex])
                    }
                    strokeDelays[unit] = 0.0
                }
                else -> {
                    val delayFromKeyStrokes = abs(keyStrokes.first().x - unitStrokes.last().x)
                    val usingOldStrokes = delayFromKeyStrokes > (strokesToCheck * 1000)
                    if (usingOldStrokes) {
                        continue
                    }
                    var delayTotal = 0.0
                    for ((keyStrokeReferenceIndex, keyPair) in keyStrokes.withIndex()) {
                        val keyTime = keyPair.x
                        var distance = Double.MAX_VALUE
                        for (testOffset in -1..1) {
                            val trackedIndex = unitStrokes.lastIndex - (keyStrokeReferenceIndex + min) + testOffset
                            if ((trackedIndex >= unitStrokes.lastIndex) || (trackedIndex < 0)) {
                                continue
                            }
                            if (useReducedStrokes
                                && (((keyStrokeReferenceIndex == 0) && (testOffset == -1))
                                || ((keyStrokeReferenceIndex == keyStrokes.lastIndex) && (testOffset == 1)))) {
                                continue
                            }
                            val testTime = unitStrokes[trackedIndex].x
                            val testDiff = abs(keyTime - testTime)
                            if (testDiff < distance) {
                                val matchOffsetUsed = matchDirection[keyStrokeReferenceIndex].first
                                val matchDistance = matchDirection[keyStrokeReferenceIndex].second
                                if ((keyStrokeReferenceIndex != 0)
                                    && (matchOffsetUsed - 1 >= keyStrokeReferenceIndex)) {
                                    if (matchDistance > testDiff) {
                                        // this stroke is closer to last keystroke than the previous
                                        // match, so the last match is bogus
                                        matchDirection[keyStrokeReferenceIndex] = Pair(-2, 0.0)
                                    }
                                    else {
                                        // last stroke is already used and seems valid
                                        continue
                                    }
                                }
                                distance = testDiff
                                matchDirection[keyStrokeReferenceIndex] = Pair(testOffset, distance)
                            }
                        }
                    }
                    for ((index, match) in matchDirection.withIndex()) {
                        if (match.first != -2) {
                            delayTotal += match.second
                        }
                        else {
                            delayTotal += (keyStrokes[index].x - keyStrokes[index - 1].x)
                        }
                    }
                    strokeDelays[unit] = delayTotal / strokesToCheck
//                    if ((strokeDelays[unit] != null)
//                        && (strokeDelays[unit]!! > strokesToCheck.toDouble())) {
//                        strokeDelays[unit] = null
//                    }
                }
            }
        }
    }

    private fun addPoint(unit: Int, newPoint:DataPoint) {
        dataVectors[unit].appendData(
            newPoint,
            false,
            unit != connectedUnits - 1
        )
        if (!isDebugMode) {
            return
        }
        if ((unit == 0) && (newPoint.x > xMax)) {
            xMin = floor(newPoint.x / xInterval) * xInterval
            xMax = xMin + xInterval
            graph.viewport.setMinX(xMin)
            graph.viewport.setMaxX(xMax)
        }
    }

    private fun createCellView(text: String): TextView {
        val cellView = TextView(this)
        cellView.text = text
        cellView.setPadding(8, 8, 8, 8)
        return cellView
    }
    private fun startEditing(textView: TextView) {
        val alertDialog = AlertDialog.Builder(this)
            .setTitle("Edit Text")
            .setView(R.layout.dialog_edit_text)
            .setPositiveButton("Save") { dialog, _ ->
                val editText = (dialog as AlertDialog).findViewById<EditText>(R.id.edit_text)
                val newText = editText?.text.toString()
                textView.text = newText
            }
            .setNegativeButton("Cancel", null)
            .create()

        alertDialog.setOnShowListener {
            val editText = alertDialog.findViewById<EditText>(R.id.edit_text)
            editText?.setText(textView.text)
            editText?.selectAll()
        }

        alertDialog.show()
    }
    @RequiresApi(Build.VERSION_CODES.O)
    @SuppressLint("SetTextI18n")
    private fun showTable(strokesPerSplit: List<Int>) {
        newSplit()
        val dialogView = layoutInflater.inflate(R.layout.dialog_table, null)
        val numOfSplits = timeNotGreenMs[0].size

        val reportDetails = "AFTER ACTION REPORT: Each 'Split' is a 1-minute segment, with percentages showing the sync level of each paddler with respect to the L1 'tempo paddler' as well as each paddlers Strokes Per Minute (SPM)."

        val reportTextView = dialogView.findViewById<TextView>(R.id.report_intro)
        reportTextView.text = reportDetails

        val percentages = Array(4){FloatArray(numOfSplits)}
        for (currentPaddler in 0 until 4) {
            var splitSeconds = 60.0f
            for (currentSplit in 0 until numOfSplits) {
                if(currentSplit == numOfSplits-2){
                    splitSeconds = (((Long.MAX_VALUE - millisUntilFinished) / 1000)%60).toFloat()
                    if (splitSeconds == 0.0f) splitSeconds = 1.0f  // Avoid division by zero
                }
                val percentCalc = 100.0f- ((timeNotGreenMs[currentPaddler][currentSplit].toFloat()*100.0f /(splitSeconds*1000.0f)))
                if(percentCalc < 0){
                    percentages[currentPaddler][currentSplit] = 0F
                }else{
                    percentages[currentPaddler][currentSplit] = percentCalc
                }
            }
        }

        val tableContent = dialogView.findViewById<TableLayout>(R.id.table_content)
        val headingRow = TableRow(this)
        headingRow.addView(createCellView("Split"))
        headingRow.addView(createCellView("   "))

        for (i in 0 until 4) {
            val cell = when (i) {
                0 -> cell1
                1 -> cell2
                2 -> cell3
                3 -> cell4
                else -> null
            }
            val cellText = cell?.text?.toString() ?: "Cell ${i + 1}"
            headingRow.addView(createCellView(cellText))
        }

        // Add the heading row to the table
        tableContent.addView(headingRow)


        // Create rows for each split and percentages
        for (splitIndex in 0 until numOfSplits-1) {
            val PercentRow = TableRow(this)
            val SpmRow = TableRow(this)

            // Add split number to the row
            val splitView = createCellView("Split ${splitIndex + 1}")
            val PercentView = createCellView(("  %"))
            val splitViewSPM = createCellView("SPM")
            PercentRow.addView(splitView)
            PercentRow.addView(PercentView)
            SpmRow.addView(createCellView("   "))
            SpmRow.addView(splitViewSPM)

            // Add percentages and SPM for each cell to the row
            for (cellIndex in 0 until 4) {
                if(cellIndex == 0){
                    val percentageView = createCellView("N/A")
                    PercentRow.addView(percentageView)
                }else{
                    val percentage = percentages[cellIndex][splitIndex].roundToInt()
                    val cellText = "$percentage%"
                    val cellView = createCellView(cellText)
                    PercentRow.addView(cellView)
                }

                var spm = 0
                // Get the SPM value for the current split
                spm = if(splitIndex == (timeNotGreenMs[0].lastIndex-1)){
                        strokeCounters[cellIndex]
                    }else{
                        strokesPerSplit[(splitIndex*connectedUnits)+cellIndex]
                    }

                // Create a formatted string to display both percentage and SPM
                val cellSPMText = "$spm"
                val cellViewSPM = createCellView(cellSPMText)


                SpmRow.addView(cellViewSPM)
            }


            tableContent.addView(PercentRow)
            tableContent.addView(SpmRow)
        }

        val alertDialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("Close", null) // Set to null here, will override later
            .create()


        alertDialog.setOnShowListener {
            alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                // Get current date and time
                val current = LocalDateTime.now()
                val formatter = DateTimeFormatter.ofPattern("yyyy_MM_dd_HH_mm")
                val formatted = current.format(formatter)

                // Save the table to a text file
                val filename = "Session_results_from_$formatted.txt"
                val file = File(getExternalFilesDir(null), filename)
                file.printWriter().use { out ->
                    // Print the report details at the top of the txt file
                    out.println(reportDetails)
                    out.println() // Extra line for separation

                    for (splitIndex in 0 until numOfSplits-1) {
                        val split = splitIndex + 1
                        out.println("Split $split:")

                        for (cellIndex in percentages.indices) {
                            val cell = when (cellIndex) {
                                0 -> cell1
                                1 -> cell2
                                2 -> cell3
                                3 -> cell4
                                else -> null
                            }
                            var spm = 0
                            // Get the SPM value for the current split
                            spm = if(splitIndex == (timeNotGreenMs[0].lastIndex-1)){
                                strokeCounters[cellIndex]
                            }else{
                                strokesPerSplit[(splitIndex*connectedUnits)+cellIndex]
                            }
                            val cellText = cell?.text?.toString() ?: "Cell ${cellIndex + 1}"
                            val percentage = percentages[cellIndex][splitIndex].roundToInt()
                            out.println("$cellText: $percentage%")
                            out.println(("SPM: $spm"))
                        }
                        out.println()
                    }
                }

                // Dismiss the dialog
                alertDialog.dismiss()
            }
        }

        alertDialog.setOnDismissListener {
            val intent = Intent(this@SessionActivity, MainActivity::class.java)
            startActivity(intent)
            finish()
        }

        alertDialog.show()
    }

    override fun onDestroy() {
        super.onDestroy()
        alertDialog?.dismiss()
        alertDialog = null
        countDownTimer.cancel()
        unregisterReceiver(gattUpdateReceiver)
        unbindService(serviceConnection)
    }

    private fun checkReceivedData() {
        var syncLevels = MutableList(connectedUnits) { SyncLevel.GREEN }
        for ((paddlerIndex, delay) in strokeDelays.withIndex()) {
            if (delay == null) {
                delayText[paddlerIndex].text = "recalculating..."
                syncLevels[paddlerIndex] = SyncLevel.RED
                continue
            }

            if (paddlerIndex == 0) {
                delayText[paddlerIndex].text = "tempo"
                syncLevels[paddlerIndex] = SyncLevel.GREEN
                continue
            }
            delayText[paddlerIndex].text = "%.2fs".format(delay)
            if (delay <= 0.3) {
                syncLevels[paddlerIndex] = SyncLevel.GREEN
            }
            else if (delay <= 0.7) {
                syncLevels[paddlerIndex] = SyncLevel.BLUE
            }
            else {
                syncLevels[paddlerIndex] = SyncLevel.RED
            }
        }
        changeCellColors(syncLevels)
    }

    enum class SyncLevel {
        GREEN, BLUE, RED
    }

    private fun changeCellColors(data: MutableList<SyncLevel>) {
        for ((paddlerIndex, value) in data.withIndex()) {
            val cell = when (paddlerIndex) {
                0 -> cell1
                1 -> cell2
                2 -> cell3
                3 -> cell4
                else -> cell1 // replace with a default cell
            }

            if (value == SyncLevel.GREEN) {
                cell.setBackgroundColor(Color.GREEN)
            } else if(value == SyncLevel.BLUE){
                cell.setBackgroundColor(Color.BLUE )
            } else if (value == SyncLevel.RED) {
                cell.setBackgroundColor(Color.RED)
            }

            if (value != isCellGreen[paddlerIndex]) { // if the cell's color changed
                cellColorChanged(paddlerIndex, value == SyncLevel.GREEN)
                isCellGreen[paddlerIndex] = value
            }
        }
    }

    private fun newSplit() {
        for (i in 0 until 4) {
            val currentTime = System.currentTimeMillis()
            if (isCellGreen[i] != SyncLevel.GREEN) {
                val additionalValue = currentTime - splitStartTimes[i]
                timeNotGreenMs[i][timeNotGreenMs[i].lastIndex] += additionalValue
            }
            splitStartTimes[i] = currentTime
            timeNotGreenMs[i].add(0)
        }
    }

    private fun cellColorChanged(paddlerIndex: Int, isGreen: Boolean) {
        val currentTime = System.currentTimeMillis()
        if (!isGreen) {
            splitStartTimes[paddlerIndex] = currentTime
        } else {
            val additionalValue = currentTime - splitStartTimes[paddlerIndex]
            timeNotGreenMs[paddlerIndex][timeNotGreenMs[paddlerIndex].lastIndex] += additionalValue
        }
    }
}
