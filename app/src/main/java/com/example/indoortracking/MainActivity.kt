package com.example.indoortracking

import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.hardware.*
import android.os.Bundle
import androidx.activity.ComponentActivity
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import kotlin.math.*

class MainActivity : ComponentActivity(), SensorEventListener {

    // 🔥 Anchor IDs (DEVICE NAMES now, not MAC)
    private val anchors = mapOf(
        "ANCHOR_1" to Pair(2.0, 2.0),
        "ANCHOR_2" to Pair(8.0, 2.0),
        "ANCHOR_3" to Pair(5.0, 8.0)
    )

    private val rssiMap = mutableMapOf<String, MutableList<Int>>()

    private lateinit var scanner: BluetoothLeScanner
    private lateinit var sensorManager: SensorManager

    private val client = OkHttpClient()

    private val SUPABASE_URL = "https://yunuilkqtczmlqhvdakz.supabase.co/rest/v1/positions"
    private val API_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Inl1bnVpbGtxdGN6bWxxaHZkYWt6Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzQwODc4MzUsImV4cCI6MjA4OTY2MzgzNX0.m1hJUfGIqUghhZE23U6i_XWCAPcxwuZ0ht-BYhb33u8"

    // 🔥 Position
    private var x = 5.0
    private var y = 5.0

    // 🔥 Sensor tracking
    private var heading = 0.0
    private var lastStepTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Sensors
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager

        sensorManager.registerListener(
            this,
            sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
            SensorManager.SENSOR_DELAY_GAME
        )

        sensorManager.registerListener(
            this,
            sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE),
            SensorManager.SENSOR_DELAY_GAME
        )

        // BLE
        val adapter = BluetoothAdapter.getDefaultAdapter()
        scanner = adapter.bluetoothLeScanner

        startBLEScan()
        startFusionLoop()
    }

    // 📡 BLE SCAN
    private fun startBLEScan() {
        scanner.startScan(scanCallback)
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {

            val name = result.device.name
                ?: result.scanRecord?.deviceName
                ?: return

            val rssi = result.rssi

            // 🔥 Filter by anchor IDs
            if (anchors.containsKey(name)) {
                val list = rssiMap.getOrPut(name) { mutableListOf() }
                list.add(rssi)
                if (list.size > 5) list.removeAt(0)

                println("DETECTED: $name RSSI: $rssi")
            }
        }
    }

    // 🎯 BLE POSITION (with filtering + confidence)
    private fun computeBLEPosition(): Pair<Double, Double>? {

        val valid = mutableListOf<Triple<String, Double, Pair<Double, Double>>>()

        for ((id, rssis) in rssiMap) {

            if (rssis.size < 3) continue

            val avg = rssis.average()
            val variance = rssis.map { (it - avg).pow(2) }.average()

            // 🔥 Reject unstable anchors
            if (variance > 20) continue

            val d = rssiToDistance(avg)
            valid.add(Triple(id, d, anchors[id]!!))
        }

        if (valid.size < 3) return null

        // 🔥 Take closest 3 anchors
        val top = valid.sortedBy { it.second }.take(3)

        val weights = top.map { 1 / (it.second + 0.5) }
        val sum = weights.sum()

        val x = top.indices.sumOf { i ->
            top[i].third.first * weights[i]
        } / sum

        val y = top.indices.sumOf { i ->
            top[i].third.second * weights[i]
        } / sum

        return Pair(x, y)
    }

    // 📡 RSSI → Distance
    private fun rssiToDistance(rssi: Double): Double {
        val txPower = -59
        val n = 2.0
        return 10.0.pow((txPower - rssi) / (10 * n))
    }

    // 📱 SENSOR HANDLING
    override fun onSensorChanged(event: SensorEvent?) {

        if (event == null) return

        when (event.sensor.type) {

            // 🔥 STEP DETECTION
            Sensor.TYPE_ACCELEROMETER -> {
                val magnitude = sqrt(
                    event.values[0].pow(2) +
                            event.values[1].pow(2) +
                            event.values[2].pow(2)
                )

                val now = System.currentTimeMillis()

                if (magnitude > 12 && now - lastStepTime > 300) {
                    lastStepTime = now
                    takeStep()
                }
            }

            // 🔥 GYRO HEADING
            Sensor.TYPE_GYROSCOPE -> {
                heading += event.values[2] * 0.1
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    // 🚶 REAL STEP MOVEMENT
    private fun takeStep() {
        val stepSize = 0.7

        val dx = stepSize * cos(heading)
        val dy = stepSize * sin(heading)

        x += dx
        y += dy
    }

    // 🔄 FUSION LOOP
    private fun startFusionLoop() {
        Thread {
            while (true) {

                val ble = computeBLEPosition()

                if (ble != null) {
                    val bleWeight = 0.7
                    val imuWeight = 0.3

                    x = bleWeight * ble.first + imuWeight * x
                    y = bleWeight * ble.second + imuWeight * y
                }

                // 🔥 Clamp inside area
                x = x.coerceIn(0.0, 10.0)
                y = y.coerceIn(0.0, 10.0)

                println("FINAL POS: ($x,$y)")

                sendToSupabase(x, y)

                Thread.sleep(300)
            }
        }.start()
    }

    // 🌐 SUPABASE
    private fun sendToSupabase(x: Double, y: Double) {

        val json = """
            {
                "id": "device_1",
                "x": ${"%.2f".format(x)},
                "y": ${"%.2f".format(y)}
            }
        """.trimIndent()

        val body = json.toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(SUPABASE_URL)
            .post(body)
            .addHeader("apikey", API_KEY)
            .addHeader("Authorization", "Bearer $API_KEY")
            .addHeader("Prefer", "resolution=merge-duplicates")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                println("FAIL: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                println("SENT → ($x,$y)")
                response.close()
            }
        })
    }
}