package com.example.smartalarmer.puzzle

interface MathPuzzleProvider {
    fun generate(difficulty: Difficulty): MathPuzzle
}

interface TypingPuzzleProvider {
    fun getRandomQuote(quotes: List<String>): String
    fun isMatch(target: String, input: String): Boolean
}

interface MemoryPuzzleProvider {
    fun generateSequence(length: Int): List<Int>
    fun verifyStep(sequence: List<Int>, userInputs: List<Int>): Boolean
}

interface ShakeSensorProvider {
    fun register(onSensorChanged: (Float, Float, Float) -> Unit)
    fun unregister()
}

class AndroidShakeSensorProvider(private val context: android.content.Context) : ShakeSensorProvider {
    private val sensorManager = context.getSystemService(android.content.Context.SENSOR_SERVICE) as android.hardware.SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER)
    private var eventListener: android.hardware.SensorEventListener? = null

    override fun register(onSensorChanged: (Float, Float, Float) -> Unit) {
        eventListener = object : android.hardware.SensorEventListener {
            override fun onSensorChanged(event: android.hardware.SensorEvent?) {
                if (event != null && event.sensor.type == android.hardware.Sensor.TYPE_ACCELEROMETER) {
                    onSensorChanged(event.values[0], event.values[1], event.values[2])
                }
            }
            override fun onAccuracyChanged(sensor: android.hardware.Sensor?, accuracy: Int) {}
        }
        if (accelerometer != null) {
            sensorManager.registerListener(eventListener, accelerometer, android.hardware.SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun unregister() {
        eventListener?.let { sensorManager.unregisterListener(it) }
        eventListener = null
    }
}
