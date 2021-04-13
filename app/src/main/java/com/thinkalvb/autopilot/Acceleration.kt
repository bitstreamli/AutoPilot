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

private const val TAG = "Pilot_Acceleration"

class Acceleration(activity: Activity) : SensorEventListener {
    private var isAvailable = false
    private var mLastBroadcastTime = System.currentTimeMillis()
    private var mAcceleration: IntArray = IntArray(3)
    private var mSensorManager: SensorManager = activity.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private var mAccelerometer: Sensor? = mSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
    private var mBroadcastBuffer = ByteBuffer.allocate((Short.SIZE_BYTES * 3) + 1).order(ByteOrder.LITTLE_ENDIAN)

    init {
        if(mAccelerometer != null) {
            isAvailable = true
            Log.d(TAG, "Acceleration Sensor created")
        }
    }

    fun startSensor() {
        if(!isAvailable) return
        mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_NORMAL)
        Log.d(TAG, "Acceleration Sensor started")
    }

    fun stopSensor() {
        if(!isAvailable) return
        mSensorManager.unregisterListener(this)
        Log.d(TAG, "Acceleration Sensor stopped")
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event != null && event.sensor.type == Sensor.TYPE_LINEAR_ACCELERATION) {
            val sensorValues = event.values.clone()
            val accelerationX = sensorValues[0].toInt()
            val accelerationY = sensorValues[1].toInt()
            val accelerationZ = sensorValues[2].toInt()

            if(mAcceleration[0] != accelerationX || mAcceleration[1] != accelerationY || mAcceleration[2] != accelerationZ) {
                mAcceleration[0] = accelerationX
                mAcceleration[1] = accelerationY
                mAcceleration[2] = accelerationZ
                broadcastAcceleration()
            } else {
                val mCurrentTime = System.currentTimeMillis()
                if (mCurrentTime - mLastBroadcastTime > 5000) broadcastAcceleration()
            }
        }
    }

    private fun broadcastAcceleration() {
        if(Broadcaster.needToBroadcast) {
            mBroadcastBuffer.clear()
            mBroadcastBuffer.put('A'.toByte())
            mBroadcastBuffer.putShort(mAcceleration[0].toShort())
            mBroadcastBuffer.putShort(mAcceleration[1].toShort())
            mBroadcastBuffer.putShort(mAcceleration[2].toShort())

            mLastBroadcastTime = System.currentTimeMillis()
            Broadcaster.sendData(mBroadcastBuffer.array())
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) { }
}