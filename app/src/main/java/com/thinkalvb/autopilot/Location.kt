package com.thinkalvb.autopilot

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Looper
import android.util.Log
import com.google.android.gms.location.*

private const val TAG = "Pilot_Location"

class Location(activity: Activity) {
    private var isAvailable = false
    private var mLastBroadcastTime = System.currentTimeMillis()
    private var mPosition: DoubleArray = DoubleArray(3)
    private lateinit var mFusedLocationProviderClient: FusedLocationProviderClient
    private lateinit var mLocationRequest: LocationRequest
    private lateinit var mLocationCallback: LocationCallback

    init {
        if(activity.packageManager.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS)) {
            isAvailable = true

            mFusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(activity)
            mLocationRequest = LocationRequest.create().apply {
                interval = 4000
                fastestInterval = 2000
                priority = LocationRequest.PRIORITY_HIGH_ACCURACY
            }
            mLocationCallback = object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    super.onLocationResult(locationResult)

                    val latitude = locationResult.lastLocation.latitude
                    val longitude = locationResult.lastLocation.longitude
                    val altitude = locationResult.lastLocation.altitude

                    if(mPosition[0] != latitude || mPosition[1] != longitude || mPosition[2] != altitude) {
                        mPosition[0] = latitude
                        mPosition[1] = longitude
                        mPosition[2] = altitude
                        broadcastLocation()
                    } else {
                        val mCurrentTime = System.currentTimeMillis()
                        if (mCurrentTime - mLastBroadcastTime > 5000) broadcastLocation()
                    }
                }
            }
            Log.d(TAG, "Location Service created")
        }
    }

    private fun broadcastLocation() {
        if(Commander.needToBroadcast){
            var locationStr = "\tLOC "
            locationStr += mPosition[0].toString() + " "
            locationStr += mPosition[1].toString() + " "
            locationStr += mPosition[2].toString() + "\t"

            Broadcaster.sendData(locationStr)
            mLastBroadcastTime = System.currentTimeMillis()
            Log.d(TAG, locationStr)
        }
    }

    fun startService() {
        if(!isAvailable) return
        try {
            mFusedLocationProviderClient.requestLocationUpdates(mLocationRequest, mLocationCallback, Looper.getMainLooper())
            Log.d(TAG, "Location Service started")
        }catch (e: SecurityException) {
            Log.d(TAG, "Location Service cannot start: $e")
        }
    }

    fun stopService() {
        if(!isAvailable) return
        mFusedLocationProviderClient.removeLocationUpdates(mLocationCallback)
        Log.d(TAG, "Location Service stopped")
    }

    fun isEnabled(activity: Activity): Boolean {
        val locationManager = activity.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
    }
}