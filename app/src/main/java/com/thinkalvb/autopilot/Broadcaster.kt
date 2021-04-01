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

class Broadcaster (private val mDestinationIP: InetAddress, private val mDestinationPort: Int ) : Runnable {
    private lateinit var mUdpSocket: DatagramSocket
    private var mLastDataBroadcastTime: Long = System.currentTimeMillis()

    override fun run() {
        try {
            mUdpSocket = DatagramSocket()
            Log.d(TAG, "Broadcaster Started")
            while(!Thread.currentThread().isInterrupted)
            {
                if(Commander.needToBroadcast){
                    broadcastData()
                    broadcastFrame()
                }else Thread.sleep(100)
            }
            Log.d(TAG, "Broadcaster Stopped")
        } catch (e: SocketException) {
            Log.d(TAG, "Socket Exception $e")
        } catch (e: IOException) {
            Log.d(TAG,"IO Exception $e")
        } catch (e: Exception) {
            Log.d(TAG,"Exception $e")
        }
    }

    private fun broadcastData() {
        if(mDataQueue.isNotEmpty()){
            val mCurrentTime = System.currentTimeMillis()
            var dataBuffer = ByteArray(0)

            if (mCurrentTime - mLastDataBroadcastTime > 100) {
                mLastDataBroadcastTime = mCurrentTime
                while (mDataQueue.isNotEmpty()){
                    dataBuffer += mDataQueue.poll()
                }
                val packet = DatagramPacket(dataBuffer, dataBuffer.size, mDestinationIP, mDestinationPort)
                mUdpSocket.send(packet)
            }
        }
    }

    private fun broadcastFrame() {
        if(mFrameQueue.isNotEmpty()){
            val dataBuffer = mFrameQueue.poll()
            val packet = DatagramPacket(dataBuffer, dataBuffer.size, mDestinationIP, mDestinationPort)
            mUdpSocket.send(packet)
        }
    }

    companion object{
        private var mDataQueue: ConcurrentLinkedQueue<ByteArray> = ConcurrentLinkedQueue()
        private var mFrameQueue: ConcurrentLinkedQueue<ByteArray> = ConcurrentLinkedQueue()

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