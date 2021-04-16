package com.thinkalvb.autopilot

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.lang.Exception
import java.util.*

private const val TAG = "Pilot_Bluetooth"
private const val READ_BUFFER_SIZE = 128
private val MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

class Bluetooth : Runnable {
    init {
        if(mBluetoothAdapter != null) {
            isAvailable = true
            Log.d(TAG, "Bluetooth adapter created")
        }
    }

    override fun run() {
        val readBuffer = ByteArray(READ_BUFFER_SIZE)
        while (!Thread.currentThread().isInterrupted && isConnected) {
            try {
                val bytes = mInputStream!!.read(readBuffer)
                Log.d(TAG, String(readBuffer, Charsets.UTF_8).take(bytes))
            } catch (e: IOException) {
                Log.d(TAG, "Exception $e")
                isConnected = false
            }
        }
        mBSocket.inputStream.close()
        mBSocket.outputStream.close()
        mBSocket.close()
    }

    companion object{
        private var isAvailable = false
        private var isConnected = false

        private lateinit var mBSocket: BluetoothSocket
        private val mBluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()

        private var mOutputStream: OutputStream? = null
        private var mInputStream: InputStream? = null

        fun write(data: ByteArray) {
            if(isConnected) mOutputStream!!.write(data)
        }

        fun getDevices(): Set<BluetoothDevice> {
            if(!isAvailable)
                return emptySet()

            val bondedDevices: Set<BluetoothDevice> = mBluetoothAdapter!!.bondedDevices
            Log.d(TAG, "${bondedDevices.size} Paired bluetooth devices found")
            return  bondedDevices
        }

        fun connect(device: BluetoothDevice): Boolean {
            if(isConnected)
                return true

            mBluetoothAdapter!!.cancelDiscovery()
            try{
                mBSocket = device.createRfcommSocketToServiceRecord(MY_UUID)
                mBSocket.connect()
                isConnected = true
                Log.d(TAG, "Bluetooth connected")
            } catch (e: IOException) {
                Log.d(TAG, "IO Exception $e")
                try{
                    Log.d(TAG, "Starting bluetooth connect fallback strategy")
                    val clazz = mBSocket.remoteDevice.javaClass
                    val paramTypes = arrayOf<Class<*>>(Integer.TYPE)
                    val m = clazz.getMethod("createRfcommSocket", *paramTypes)
                    val fallbackSocket = m.invoke(mBSocket.remoteDevice, Integer.valueOf(1)) as BluetoothSocket
                    fallbackSocket.connect()
                    mBSocket = fallbackSocket
                    isConnected = true
                    Log.d(TAG, "Bluetooth connected")
                } catch (e: Exception) {
                    Log.d(TAG, "IO Exception $e")
                    return false
                }
            }
            mOutputStream = mBSocket.outputStream
            mInputStream = mBSocket.inputStream
            return true
        }
    }
}