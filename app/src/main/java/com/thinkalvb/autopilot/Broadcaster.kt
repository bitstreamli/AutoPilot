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
private const val FRAME_BROADCAST_INTERVAL = 40
private const val PROBE_INTERVAL = 5000
private const val KEEP_ALIVE_TIMEOUT = 15000
private const val RECEIVE_TIMEOUT = 5

class Broadcaster (private val mDestinationIP: InetAddress, private val mDestinationPort: Int ) : Runnable {
    private lateinit var mUdpSocket: DatagramSocket

    private var mLastDataBroadcastTime: Long = 0
    private var mLastFrameBroadcastTime: Long = 0
    private var mLastReceiveTime: Long = 0
    private var mLastProbeSendTime: Long = 0

    private var mRequiresDataBroadcast: Boolean = false
    private var mRequiresFrameBroadcast: Boolean = false

    override fun run() {

        try {
            mUdpSocket = DatagramSocket()
            mUdpSocket.soTimeout = RECEIVE_TIMEOUT
            Log.d(TAG, "Broadcaster Started on port ${mUdpSocket.localPort}")
        } catch (e: SocketException) {
            //Log.d(TAG, "Socket Exception $e")
        } catch (e: IOException) {
            //Log.d(TAG,"IO Exception $e")
        } catch (e: Exception) {
            //Log.d(TAG,"Exception $e")
        }

        while(!Thread.currentThread().isInterrupted)
        {
            updateFlags()
            try{
                if(mRequiresDataBroadcast) broadcastData()
                if(mRequiresFrameBroadcast) broadcastFrame()
                receiveData()
            } catch (e: SocketException) {
                //Log.d(TAG, "Socket Exception $e")
            } catch (e: IOException) {
                //Log.d(TAG,"IO Exception $e")
            } catch (e: Exception) {
                //Log.d(TAG,"Exception $e")
            }
        }
        Log.d(TAG, "Broadcaster Stopped")
    }

    private fun receiveData() {
        val dataBuffer = ByteArray(128)
        val incomingPacket = DatagramPacket(dataBuffer, dataBuffer.size)
        mUdpSocket.receive(incomingPacket)
        mLastReceiveTime = System.currentTimeMillis()
        processData(dataBuffer, incomingPacket.length)
    }

    private fun processData(dataBuffer: ByteArray, packetSize: Int) {
        val incomingString = String(dataBuffer,Charsets.UTF_8).take(packetSize)
        Log.d(TAG, incomingString)
    }

    private fun broadcastData() {
        mLastDataBroadcastTime = System.currentTimeMillis()
        if(mDataQueue.isNotEmpty()){
            var dataBuffer = ByteArray(0)
            while (mDataQueue.isNotEmpty()) dataBuffer += mDataQueue.poll()
            val packet = DatagramPacket(dataBuffer, dataBuffer.size, mDestinationIP, mDestinationPort)
            mUdpSocket.send(packet)
        }
    }

    private fun broadcastFrame() {
        mLastFrameBroadcastTime = System.currentTimeMillis()
        if(mFrameQueue.isNotEmpty()){
            val dataBuffer: ByteArray = mFrameQueue.poll()
            val packet = DatagramPacket(dataBuffer, dataBuffer.size, mDestinationIP, mDestinationPort)
            mUdpSocket.send(packet)
        }
    }

    private fun updateFlags() {
        val currentTime = System.currentTimeMillis()
        val timeTillLastDataBroadcast = currentTime - mLastDataBroadcastTime
        mRequiresDataBroadcast = timeTillLastDataBroadcast > DATA_BROADCAST_INTERVAL

        val timeTillLastFrameBroadcast = currentTime - mLastFrameBroadcastTime
        mRequiresFrameBroadcast = timeTillLastFrameBroadcast > FRAME_BROADCAST_INTERVAL

        val timeTillLastReceive = currentTime - mLastReceiveTime
        needToBroadcast = timeTillLastReceive < KEEP_ALIVE_TIMEOUT

        if(!needToBroadcast) {
            val timeTillLastProbeSend = currentTime - mLastProbeSendTime
            if (timeTillLastProbeSend > PROBE_INTERVAL) {
                sendData("\tREG\t")
                mLastProbeSendTime = currentTime
            }
        }

        if(needToBroadcast) Log.d(TAG, "Need To Broadcast")
        if (mRequiresFrameBroadcast) Log.d(TAG, "Need To Broadcast Frame")
        if (mRequiresDataBroadcast) Log.d(TAG, "Need To Broadcast Data")
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