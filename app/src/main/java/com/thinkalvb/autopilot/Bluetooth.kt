package com.thinkalvb.autopilot

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.*

private const val TAG = "Pilot_Bluetooth"
private const val READ_BUFFER_SIZE = 128
private val MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

class Bluetooth() : Runnable {
    private var isAvailable = false
    private var isConnected = false

    private lateinit var mBSocket: BluetoothSocket
    private val mBluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()

    private var mOutputStream: OutputStream? = null
    private var mInputStream: InputStream? = null

    init {
        if(mBluetoothAdapter != null) {
            isAvailable = true
            Log.d(TAG, "Bluetooth adapter created")
        }
    }

    private fun getDevice(): BluetoothDevice? {
        if(isAvailable && mBluetoothAdapter!!.isEnabled) {
            val bondedDevices: Set<BluetoothDevice> = mBluetoothAdapter.bondedDevices
            if (bondedDevices.isNotEmpty()) {
                Log.d(TAG, "Paired bluetooth devices found")
                bondedDevices.forEach { bondedDevice -> if(bondedDevice.bondState == BluetoothDevice.BOND_BONDED) return bondedDevice}
            }
        }
        return null
    }

    fun startBluetooth(): Boolean {
        val device = getDevice()
        if(device != null) {
            try{
                mBSocket = device.createInsecureRfcommSocketToServiceRecord(MY_UUID)
                Log.d(TAG, "Bluetooth socket created ${device.name}")
                mBSocket.connect()
                Log.d(TAG, "Bluetooth connected with RCV")
                mOutputStream = mBSocket.outputStream
                mInputStream = mBSocket.inputStream
                isConnected = true
                Log.d(TAG, "Bluetooth connected with RCV")
            } catch (e: IOException) {
                Log.d(TAG, "IO Exception $e")
            }
        }
        return isConnected
    }

    fun write(data: String) {
        if(isConnected) mOutputStream!!.write(data.toByteArray())
    }

    override fun run() {
        val readBuffer = ByteArray(READ_BUFFER_SIZE)
        var bytes = 0
        while (!Thread.currentThread().isInterrupted && isConnected) {
            try {
                bytes = mInputStream!!.read(readBuffer, bytes, READ_BUFFER_SIZE - bytes)
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
        isConnected = false
        mBSocket.inputStream.close()
        mBSocket.outputStream.close()
        mBSocket.close()
    }
}