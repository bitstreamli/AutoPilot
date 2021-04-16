package com.thinkalvb.autopilot

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.net.InetAddress

private const val TAG = "Pilot_MainActivity"

class MainActivity : AppCompatActivity() {
    private lateinit var mUDPThread: Thread
    private lateinit var mBluetoothThread: Thread

    private lateinit var mOrientation: Orientation
    private lateinit var mAcceleration: Acceleration
    private lateinit var mLocation: Location
    private lateinit var mCamera: Camera
    private lateinit var mBluetooth: Bluetooth

    private lateinit var mDeviceSpinner: Spinner
    private var mDeviceList: Set<BluetoothDevice> = emptySet()
    private var isRunning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        checkAndRequestPermissions()

        mDeviceSpinner = findViewById(R.id.device_spinner)
        mOrientation = Orientation(this)
        mAcceleration = Acceleration(this)
        mCamera = Camera(this)
        mLocation = Location(this)
        mBluetooth = Bluetooth()

        val startButton: Button = findViewById(R.id.start_button)
        val stopButton: Button = findViewById(R.id.stop_button)
        startButton.setOnClickListener{
            if(!isRunning) {
                val (validPort, portNumber) = getPortNumber()
                val (validIP, ip) = getIP()

                if (validIP && validPort) {
                    startNetworkService(portNumber, ip)
                    startServices()
                    isRunning = true
                } else Toast.makeText(
                    applicationContext,
                    "Invalid Broadcast Address",
                    Toast.LENGTH_SHORT
                ).show()
            } else Toast.makeText(
                applicationContext,
                "Services are already running",
                Toast.LENGTH_SHORT
            ).show()
        }
        stopButton.setOnClickListener{
            if(isRunning) {
                stopNetworkService()
                stopServices()
                isRunning = false
            } else Toast.makeText(applicationContext, "No service is running", Toast.LENGTH_SHORT).show()
        }

        val refreshButton: ImageButton = findViewById(R.id.refersh_button)
        refreshButton.setOnClickListener { refreshDeviceList() }
        val connectButton: ImageButton = findViewById(R.id.connect_button)
        connectButton.setOnClickListener {
            if(mDeviceList.isEmpty()) {
                Toast.makeText(applicationContext, "No paired devices (try refreshing)", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val connected = Bluetooth.connect(mDeviceList.elementAt(mDeviceSpinner.selectedItemPosition))
            if(!connected) {
                Toast.makeText(applicationContext, "Bluetooth connection failed", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            Log.d(TAG, "${mDeviceList.elementAt(mDeviceSpinner.selectedItemPosition).name} Connected")
            Toast.makeText(applicationContext, "Device Connected", Toast.LENGTH_SHORT).show()
            mBluetoothThread = Thread(mBluetooth)
            mBluetoothThread.start()
        }
    }

    private fun stopServices() {
        Log.d(TAG, "Services stopping")
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        mOrientation.stopSensor()
        mAcceleration.stopSensor()
        mCamera.stopCamera()
        mLocation.stopService()
    }

    private fun startServices() {
        Log.d(TAG, "Services starting")
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        mOrientation.startSensor()
        mAcceleration.startSensor()
        mCamera.startCamera(this)
        mLocation.startService()
        if(!mLocation.isEnabled(this)) Toast.makeText(applicationContext, "Enable GPS for Location", Toast.LENGTH_SHORT).show()
    }

    private fun refreshDeviceList()
    {
        mDeviceList = Bluetooth.getDevices()
        val spinnerArray: MutableList<String> = ArrayList()
        mDeviceList.forEach { device-> spinnerArray.add(device.name)}
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, spinnerArray)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        mDeviceSpinner.adapter = adapter
    }

    private fun startNetworkService(portNumber: Int, ip: InetAddress) {
        val broadcaster = Broadcaster(ip, portNumber)
        mUDPThread = Thread(broadcaster)
        mUDPThread.start()
    }

    private fun stopNetworkService(){
        mUDPThread.interrupt()
    }

    private fun checkAndRequestPermissions() {
        val appPermissions = arrayOf( Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.CAMERA)
        val neededPermissions = ArrayList<String>()
        for (perm in appPermissions) {
            if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                neededPermissions.add(perm)
            }
        }

        if(neededPermissions.isNotEmpty()) {
            val requestCode = 108
            ActivityCompat.requestPermissions(this, neededPermissions.toTypedArray(), requestCode)
        }
    }

    private fun getPortNumber() : Pair<Boolean, Int> {
        val portNumberStr: EditText = findViewById(R.id.port_number)
        return try {
            val targetPort = portNumberStr.text.toString().toInt()
            if (targetPort < 0 || targetPort > 65535) Pair(false, 1357)
            else Pair(true, targetPort)
        } catch (ex: Exception){
            Pair(false, 1357)
        }
    }

    private fun getIP() : Pair<Boolean, InetAddress> {
        val ipAddressStr: EditText = findViewById(R.id.ip_address)
        return try {
            val targetIP = InetAddress.getByName(ipAddressStr.text.toString())
            Pair(true, targetIP)
        } catch (ex: Exception){
            Pair(false, InetAddress.getByAddress("10.0.2.2".toByteArray()))
        }
    }
}

