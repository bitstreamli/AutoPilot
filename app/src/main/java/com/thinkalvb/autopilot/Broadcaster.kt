package com.thinkalvb.autopilot

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock

private const val TAG = "Pilot_Broadcaster"
private const val MAX_DATA_SIZE = 65507
private const val DATA_BROADCAST_INTERVAL = 40
private const val PROBE_INTERVAL = 10000L
private const val RECEIVE_TIMEOUT = 5
private const val READ_BUFFER_SIZE = 128

class Broadcaster(private val mDestinationIP: InetAddress, private val mDestinationPort: Int) : Runnable {
    private lateinit var mUdpSocket: DatagramSocket

    private var mLastDataBroadcastTime: Long = 0
    private var mLastReceiveTime: Long = 0
    private var mRequiresDataBroadcast: Boolean = false

    override fun run() {
        val handler = Handler(Looper.getMainLooper())
        handler.postDelayed(object : Runnable {
            override fun run() {
                val currentTime = System.currentTimeMillis()
                val timeTillLastReceive = currentTime - mLastReceiveTime
                if (timeTillLastReceive > PROBE_INTERVAL * 2){
                    Log.d(TAG,"Probing for server")
                    needToBroadcast = false
                    sendData("P".toByteArray(Charsets.UTF_8))
                    mLastReceiveTime = currentTime
                } else needToBroadcast = true
                handler.postDelayed(this, PROBE_INTERVAL)
            }
        }, PROBE_INTERVAL)

        try {
            mUdpSocket = DatagramSocket()
            mUdpSocket.soTimeout = RECEIVE_TIMEOUT
            Log.d(TAG, "Broadcaster Started on port ${mUdpSocket.localPort}")
        } catch (e: Exception) {
            Log.d(TAG, "Exception $e")
            return
        }

        sendData("P".toByteArray(Charsets.UTF_8))
        Log.d(TAG,"Probing for server")
        while(!Thread.currentThread().isInterrupted)
        {
            updateFlags()
            try{
                if(mRequiresDataBroadcast) {
                    broadcastData()
                    mLastDataBroadcastTime = System.currentTimeMillis()
                }
                receiveData()
            } catch (e: SocketException) {
                Log.d(TAG, "Socket Exception $e")
            } catch (e: IOException) {
                Log.d(TAG, "IO Exception $e")
            } catch (e: Exception) {
                Log.d(TAG, "Exception $e")
            }
        }
        Log.d(TAG, "Broadcaster Stopped")
    }

    private fun receiveData() {
        val dataBuffer = ByteArray(READ_BUFFER_SIZE)
        val incomingPacket = DatagramPacket(dataBuffer, dataBuffer.size)
        mUdpSocket.receive(incomingPacket)
        mLastReceiveTime = System.currentTimeMillis()
        processData(dataBuffer, incomingPacket.length)
    }

    private fun processData(dataBuffer: ByteArray, packetSize: Int) {
        val incomingString = String(dataBuffer, Charsets.UTF_8).take(packetSize)
        Log.d(TAG, incomingString)
    }

    private fun broadcastData() {
        mBufferLock.lock()
        mLastDataBroadcastTime = System.currentTimeMillis()
        if(mDataBuffer.position() != 0) {
            val packet = DatagramPacket(mDataBuffer.array(), mDataBuffer.position(), mDestinationIP, mDestinationPort)
            mUdpSocket.send(packet)
            mDataBuffer.clear()
        }
        mBufferLock.unlock()
    }

    private fun updateFlags() {
        val currentTime = System.currentTimeMillis()
        val timeTillLastDataBroadcast = currentTime - mLastDataBroadcastTime
        mRequiresDataBroadcast = timeTillLastDataBroadcast > DATA_BROADCAST_INTERVAL
    }

    companion object{
        private var mDataBuffer = ByteBuffer.allocate(MAX_DATA_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        private var mBufferLock: Lock = ReentrantLock()
        var needToBroadcast = false

        fun sendData(data: ByteArray){
            mBufferLock.lock()
            if(mDataBuffer.remaining() < data.size) {
                mDataBuffer.clear()
                Log.d(TAG, "Unsent data - Data buffer cleared")
            }
            mDataBuffer.put(data)
            mBufferLock.unlock()
        }

        fun sendFrame(bitmap: Bitmap) {
            mBufferLock.lock()
            val broadcastFrame = Bitmap.createScaledBitmap(bitmap, 320, 240, false)
            val stream = ByteArrayOutputStream()
            broadcastFrame.compress(Bitmap.CompressFormat.JPEG, 90, stream)
            val imageDataSize = stream.size() + Short.SIZE_BYTES + 1
            if(mDataBuffer.remaining() < imageDataSize) {
                mDataBuffer.clear()
                Log.d(TAG, "Unsent data - Data buffer cleared")
            }
            mDataBuffer.put('C'.toByte())
            mDataBuffer.putShort(stream.size().toShort())
            mDataBuffer.put(stream.toByteArray())
            mBufferLock.unlock()
        }
    }
}