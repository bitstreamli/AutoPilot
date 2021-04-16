package com.thinkalvb.autopilot

import android.util.Log

private const val TAG = "Pilot_NavController"

class NavController {
    companion object{
        private var mRPM: Short = 0
        private var mSteerAngle: Short = 0

        fun takeOver() {
            // control signal lost - take over the control-
        }

        fun updateRPM(rpm: Short) {
            mRPM = rpm
            Log.d(TAG, "New RPM $mRPM")

            // do something to adjust rpm of the Falcon
        }

        fun updateSteer(steerAngle: Short) {
            mSteerAngle = steerAngle
            Log.d(TAG, "New Steer Angle $mSteerAngle")
            // do something to adjust steer of the Falcon
        }

    }
}