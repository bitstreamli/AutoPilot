package com.thinkalvb.autopilot

import android.graphics.Bitmap
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketException
import java.util.concurrent.ConcurrentLinkedQueue

private const val TAG = "Pilot_Broadcaster"
private const val DATA_BROADCAST_INTERVAL = 100
private const val KEEP_ALIVE_TIMEOUT = 15000

class Broadcaster (private val mDestinationIP: InetAddress, private val mDestinationPort: Int ) : Runnable {
    private lateinit var mUdpSocket: DatagramSocket
    private var mLastDataBroadcastTime: Long = 0
    private var mLastKeepAlive: Long = 0

    override fun run() {
        try {
            mUdpSocket = DatagramSocket()
            mUdpSocket.soTimeout = 10
            Log.d(TAG, "Broadcaster Started on port ${mUdpSocket.localPort}")
            while(!Thread.currentThread().isInterrupted)
            {
                val currentTime = System.currentTimeMillis()
                val timeTillLastDataBroadcast = currentTime - mLastDataBroadcastTime
                val timeTillLastKeepAlive = currentTime - mLastKeepAlive

                if (timeTillLastDataBroadcast > DATA_BROADCAST_INTERVAL) {
                    broadcastData()
                    mLastDataBroadcastTime = currentTime
                }
                broadcastFrame()

                val dataBuffer = ByteArray(128)
                val incomingPacket = DatagramPacket(dataBuffer, dataBuffer.size)
                needToBroadcast = timeTillLastKeepAlive < KEEP_ALIVE_TIMEOUT
                try{
                    mUdpSocket.receive(incomingPacket)
                    mLastKeepAlive = currentTime
                    processData(dataBuffer, incomingPacket.length)
                } catch (e: Exception) {}
            }
        } catch (e: SocketException) {
            Log.d(TAG, "Socket Exception $e")
        } catch (e: IOException) {
            Log.d(TAG,"IO Exception $e")
        } catch (e: Exception) {
            Log.d(TAG,"Exception $e")
        }
        Log.d(TAG, "Broadcaster Stopped")
    }

    private fun processData(dataBuffer: ByteArray, packetSize: Int) {
        val incomingString = String(dataBuffer,Charsets.UTF_8).take(packetSize)
        Log.d(TAG, incomingString)
    }

    private fun broadcastData() {
        if(mDataQueue.isNotEmpty()){
            var dataBuffer = ByteArray(0)
            while (mDataQueue.isNotEmpty()) dataBuffer += mDataQueue.poll()
            val packet = DatagramPacket(dataBuffer, dataBuffer.size, mDestinationIP, mDestinationPort)
            mUdpSocket.send(packet)
        }
    }

    private fun broadcastFrame() {
        if(mFrameQueue.isNotEmpty()){
            val dataBuffer: ByteArray = mFrameQueue.poll()
            val packet = DatagramPacket(dataBuffer, dataBuffer.size, mDestinationIP, mDestinationPort)
            mUdpSocket.send(packet)
        }
    }

    companion object{
        private var mDataQueue: ConcurrentLinkedQueue<ByteArray> = ConcurrentLinkedQueue()
        private var mFrameQueue: ConcurrentLinkedQueue<ByteArray> = ConcurrentLinkedQueue()
        var needToBroadcast = false

        fun sendData(data: String) {
            if(mDataQueue.size > 10) {
                Log.d(TAG, "Unsent data - Data buffer cleared")
                mDataQueue.clear()
            }
            mDataQueue.add(data.toByteArray())
        }

        fun sendFrame(bitmap: Bitmap) {
            if(mFrameQueue.size > 5) {
                Log.d(TAG, "Unsent data - Frame buffer cleared")
                mFrameQueue.clear()
            }
            val broadcastFrame = Bitmap.createScaledBitmap(bitmap, 320, 240, false)
            val stream = ByteArrayOutputStream()
            broadcastFrame.compress(Bitmap.CompressFormat.JPEG, 90, stream)
            val imageByteArray = stream.toByteArray()

            if(imageByteArray.size <= 65527) mFrameQueue.add(imageByteArray)
            else Log.d(TAG, "Exceeded maximum payload size")
        }
    }
}