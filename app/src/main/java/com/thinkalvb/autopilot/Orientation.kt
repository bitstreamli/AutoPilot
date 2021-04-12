package com.thinkalvb.autopilot

import android.app.Activity
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

private const val TAG = "Pilot_Orientation"

class Orientation(activity: Activity) : SensorEventListener {
    private var isAvailable = false
    private var mLastBroadcastTime = System.currentTimeMillis()
    private var mOrientation: IntArray = IntArray(3)
    private var mSensorManager: SensorManager = activity.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private var mGyroscope: Sensor? = mSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private var mBroadcastBuffer = ByteBuffer.allocate((Short.SIZE_BYTES * 3) + 1).order(ByteOrder.LITTLE_ENDIAN)

    init {
        if(mGyroscope != null) {
            isAvailable = true
            Log.d(TAG, "Orientation Sensor created")
        }
    }

    fun startSensor() {
        if(!isAvailable) return
        mSensorManager.registerListener(this, mGyroscope, SensorManager.SENSOR_DELAY_NORMAL)
        Log.d(TAG, "Orientation Sensor started")
    }

    fun stopSensor() {
        if(!isAvailable) return
        mSensorManager.unregisterListener(this)
        Log.d(TAG, "Orientation Sensor stopped")
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event != null && event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
            val sensorValues = event.values.clone()
            val rotationMatrix = FloatArray(16)
            val orientation = FloatArray(3)

            SensorManager.getRotationMatrixFromVector(rotationMatrix, sensorValues)
            SensorManager.remapCoordinateSystem(rotationMatrix, SensorManager.AXIS_X, SensorManager.AXIS_Z, rotationMatrix)
            SensorManager.getOrientation(rotationMatrix, orientation)

            var orientationX = ((Math.toDegrees(orientation[0].toDouble()) + 360) % 360).roundToInt()
            var orientationY = ((Math.toDegrees(orientation[1].toDouble()) + 360) % 360).roundToInt()
            var orientationZ = ((Math.toDegrees(orientation[2].toDouble()) + 360) % 360).roundToInt()

            if(orientationX >= 360) orientationX = 0
            if(orientationY >= 360) orientationY = 0
            if(orientationZ >= 360) orientationZ = 0

            if(mOrientation[0] != orientationX || mOrientation[1] != orientationY || mOrientation[2] != orientationZ) {
                mOrientation[0] = orientationX
                mOrientation[1] = orientationY
                mOrientation[2] = orientationZ
                broadcastOrientation()
            } else {
                val mCurrentTime = System.currentTimeMillis()
                if (mCurrentTime - mLastBroadcastTime > 5000) broadcastOrientation()
            }
        }
    }

    private fun broadcastOrientation() {
        if(Broadcaster.needToBroadcast){
            mBroadcastBuffer.clear()
            mBroadcastBuffer.put('O'.toByte())
            mBroadcastBuffer.putShort(mOrientation[0].toShort())
            mBroadcastBuffer.putShort(mOrientation[1].toShort())
            mBroadcastBuffer.putShort(mOrientation[2].toShort())

            mLastBroadcastTime = System.currentTimeMillis()
            Log.d(TAG,"Packet Size = ${mBroadcastBuffer.position()}")
            Broadcaster.sendData(mBroadcastBuffer.array())
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) { }
}