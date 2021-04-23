package com.thinkalvb.autopilot

import android.util.Log

private const val TAG = "Pilot_NavController"

class NavController {
    companion object{
        private var mAcceleration: ShortArray = ShortArray(3)
        private var mOrientation: ShortArray = ShortArray(3)

        fun takeOver() {
        }

        fun updateAcceleration(reqAcceleration: ShortArray, signalData: ByteArray) {
            mAcceleration = reqAcceleration
            Log.d(TAG, "Required acceleration X: ${mAcceleration[0]} Y: ${mAcceleration[1] } Z: ${mAcceleration[2] } ")
            Bluetooth.write(signalData)
        }

        fun updateOrientation(reqOrientation: ShortArray, signalData: ByteArray) {
            mOrientation = reqOrientation
            Log.d(TAG, "Required alignment Yaw: ${mOrientation[0]} Pitch: ${mOrientation[1] } Roll: ${mOrientation[2] } ")
            Bluetooth.write(signalData)
        }
    }
}