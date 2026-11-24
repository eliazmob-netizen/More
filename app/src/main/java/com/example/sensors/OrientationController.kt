package com.example.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.example.simulation.AtmosphereMath
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.sqrt

class OrientationController(
    context: Context,
    private val onSensorUpdate: (
        yawG: Double,
        pitG: Double,
        roll: Double,
        tiltTx: Double,
        tiltTy: Double,
    ) -> Unit,
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val gameRotationSensor: Sensor? =
        sensorManager?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
    private val rotationSensor: Sensor? =
        sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val gyroSensor: Sensor? =
        sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val accelSensor: Sensor? =
        sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magSensor: Sensor? =
        sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    private val activeFusionSensor: Sensor? = gameRotationSensor ?: rotationSensor

    private val rMatrix = FloatArray(9)
    private val rRemapped = FloatArray(9)
    private val orient = FloatArray(3)
    private val accel = FloatArray(3)
    private val mag = FloatArray(3)
    private var hasAccel = false
    private var hasMag = false

    private var lastGyroNs = 0L
    private var gyroYawAccum = 0.0
    private var gyroPitchAccum = 0.0
    private var gyroRollAccum = 0.0

    private var gyroActive = false
    private var head0: Double? = null
    private var yawG = 0.0
    private var pitG = 0.0
    private var roll = 0.0
    private var tiltTx = 0.0
    private var tiltTy = 0.0

    val isAvailable: Boolean
        get() = activeFusionSensor != null || gyroSensor != null || accelSensor != null

    fun setGyroEnabled(enabled: Boolean) {
        gyroActive = enabled
        lastGyroNs = 0L
        gyroYawAccum = 0.0
        gyroPitchAccum = 0.0
        gyroRollAccum = 0.0
        if (enabled) {
            head0 = null
            yawG = 0.0
            tiltTx = 0.0
            tiltTy = 0.0
        } else {
            yawG = 0.0
            pitG = 0.0
            roll = 0.0
        }
        onSensorUpdate(yawG, pitG, roll, tiltTx, tiltTy)
    }

    fun start() {
        val sm = sensorManager ?: return
        if (activeFusionSensor != null) {
            sm.registerListener(this, activeFusionSensor, SensorManager.SENSOR_DELAY_GAME)
        } else if (gyroSensor != null) {
            sm.registerListener(this, gyroSensor, SensorManager.SENSOR_DELAY_GAME)
            accelSensor?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        } else {
            accelSensor?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
            magSensor?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        }
    }

    fun stop() {
        sensorManager?.unregisterListener(this)
        lastGyroNs = 0L
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_GAME_ROTATION_VECTOR, Sensor.TYPE_ROTATION_VECTOR -> {
                SensorManager.getRotationMatrixFromVector(rMatrix, event.values)
                processRotationMatrix()
            }
            Sensor.TYPE_GYROSCOPE -> {
                processRawGyroscope(event)
            }
            Sensor.TYPE_ACCELEROMETER -> {
                System.arraycopy(event.values, 0, accel, 0, 3)
                hasAccel = true
                if (activeFusionSensor == null) {
                    if (gyroSensor == null && hasMag &&
                        SensorManager.getRotationMatrix(rMatrix, null, accel, mag)
                    ) {
                        processRotationMatrix()
                    } else {
                        processAccelTiltOnly()
                    }
                }
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                System.arraycopy(event.values, 0, mag, 0, 3)
                hasMag = true
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun processRawGyroscope(event: SensorEvent) {
        val ts = event.timestamp
        if (lastGyroNs != 0L) {
            val dt = ((ts - lastGyroNs) * 1e-9).coerceIn(0.0, 0.1)
            // Device axes: x = tilt up/down, y = turn left/right (upright portrait), z = roll
            val wx = event.values[0].toDouble()
            val wy = event.values[1].toDouble()
            val wz = event.values[2].toDouble()

            gyroYawAccum = AtmosphereMath.wrapDeg(gyroYawAccum - Math.toDegrees(wy * dt))
            gyroPitchAccum = AtmosphereMath.clamp(gyroPitchAccum + Math.toDegrees(wx * dt), -35.0, 35.0)
            gyroRollAccum = AtmosphereMath.clamp(gyroRollAccum - Math.toDegrees(wz * dt), -35.0, 35.0)

            if (gyroActive) {
                yawG += AtmosphereMath.wrapDeg(gyroYawAccum - yawG) * 0.35
                pitG += (gyroPitchAccum - pitG) * 0.35
                roll += (gyroRollAccum - roll) * 0.25
                tiltTx = 0.0
                tiltTy = 0.0
                onSensorUpdate(yawG, pitG, roll, tiltTx, tiltTy)
            }
        }
        lastGyroNs = ts
    }

    private fun processAccelTiltOnly() {
        val ax = accel[0].toDouble()
        val ay = accel[1].toDouble()
        val az = accel[2].toDouble()
        val norm = sqrt(ax * ax + ay * ay + az * az).coerceAtLeast(0.001)
        val nx = ax / norm
        val ny = ay / norm
        val nz = az / norm

        if (gyroActive) {
            if (gyroSensor == null) {
                // Tilt-based look fallback when only accelerometer is present
                val pitchDeg = AtmosphereMath.clamp(Math.toDegrees(asin((-nz).coerceIn(-1.0, 1.0))), -35.0, 35.0)
                val rollDeg = AtmosphereMath.clamp(Math.toDegrees(atan2(nx, ny)), -35.0, 35.0)
                yawG = AtmosphereMath.wrapDeg(yawG - nx * 1.8)
                pitG += (pitchDeg - pitG) * 0.18
                roll += (rollDeg - roll) * 0.18
                tiltTx = 0.0
                tiltTy = 0.0
                onSensorUpdate(yawG, pitG, roll, tiltTx, tiltTy)
            }
        } else {
            tiltTx = AtmosphereMath.clamp((-nx * 1.5), -1.0, 1.0)
            tiltTy = AtmosphereMath.clamp(((nz - 0.5) * 1.5), -1.0, 1.0)
            onSensorUpdate(yawG, pitG, roll, tiltTx, tiltTy)
        }
    }

    private fun processRotationMatrix() {
        SensorManager.remapCoordinateSystem(
            rMatrix,
            SensorManager.AXIS_X,
            SensorManager.AXIS_Z,
            rRemapped,
        )
        SensorManager.getOrientation(rRemapped, orient)

        val headingDeg = Math.toDegrees(orient[0].toDouble())
        val pitchDeg = AtmosphereMath.clamp(Math.toDegrees(orient[1].toDouble()), -35.0, 35.0)
        val rawRollDeg = Math.toDegrees(atan2(rMatrix[6].toDouble(), rMatrix[7].toDouble()))
        val tiltGamma = Math.toDegrees(asin(AtmosphereMath.clamp(rMatrix[6].toDouble(), -1.0, 1.0)))
        val tiltBeta = Math.toDegrees(asin(AtmosphereMath.clamp(rMatrix[8].toDouble(), -1.0, 1.0)))

        if (gyroActive) {
            if (head0 == null) head0 = headingDeg
            val tg = AtmosphereMath.wrapDeg(headingDeg - (head0 ?: headingDeg))
            yawG += AtmosphereMath.wrapDeg(tg - yawG) * 0.25
            pitG += (pitchDeg - pitG) * 0.25
            val targetRoll = if (abs(rMatrix[7]) > 0.35f) rawRollDeg.coerceIn(-35.0, 35.0) else 0.0
            roll += (targetRoll - roll) * 0.22
            tiltTx = 0.0
            tiltTy = 0.0
        } else {
            tiltTx = AtmosphereMath.clamp(tiltGamma / 35.0, -1.0, 1.0)
            tiltTy = AtmosphereMath.clamp((tiltBeta - 30.0) / 35.0, -1.0, 1.0)
        }
        onSensorUpdate(yawG, pitG, roll, tiltTx, tiltTy)
    }
}
