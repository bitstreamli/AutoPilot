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

class Broadcaster(private val mDestinationIP: InetAddress, private val mDestinationPort: Int) : Runnable {
    private lateinit var mUdpSocket: DatagramSocket
    private var mLastReceiveTime: Long = 0

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
                } else needToBroadcast = true
                handler.postDelayed(this, PROBE_INTERVAL)
            }
        }, PROBE_INTERVAL)

        try {
            mUdpSocket = DatagramSocket()
            mUdpSocket.soTimeout = DATA_BROADCAST_INTERVAL

            Log.d(TAG, "Broadcaster Started on port ${mUdpSocket.localPort}")
        } catch (e: Exception) {
            Log.d(TAG, "Exception $e")
            return
        }

        sendData("P".toByteArray(Charsets.UTF_8))
        Log.d(TAG,"Probing for server")
        while(!Thread.currentThread().isInterrupted)
        {
            try{
                if(mDataSendBuffer.position() != 0) broadcastData()
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
        val incomingPacket = DatagramPacket(mDataReceiveBuffer.array(), MAX_DATA_SIZE)
        mUdpSocket.receive(incomingPacket)

        mLastReceiveTime = System.currentTimeMillis()
        needToBroadcast = true
        processData(incomingPacket.length)
    }

    private fun processData(packetSize: Int) {
        val signalSize = Byte.SIZE_BYTES + (3 * Short.SIZE_BYTES)
        val signal = ByteArray(signalSize)
        var index = 0

        while(index < packetSize){
            when (mDataReceiveBuffer.get(index).toChar()) {
                'A' -> {
                    mDataReceiveBuffer.get(signal, 0, signalSize)

                    index++
                    val reqAcceleration = ShortArray(3)
                    reqAcceleration[0] = mDataReceiveBuffer.getShort(index)
                    index += Short.SIZE_BYTES
                    reqAcceleration[1] = mDataReceiveBuffer.getShort(index)
                    index += Short.SIZE_BYTES
                    reqAcceleration[2] = mDataReceiveBuffer.getShort(index)
                    NavController.updateAcceleration(reqAcceleration,signal)
                    index += Short.SIZE_BYTES
                }
                'O' -> {
                    mDataReceiveBuffer.get(signal, 0, signalSize)

                    index++
                    val reqOrientation = ShortArray(3)
                    reqOrientation[0] = mDataReceiveBuffer.getShort(index)
                    index += Short.SIZE_BYTES
                    reqOrientation[1] = mDataReceiveBuffer.getShort(index)
                    index += Short.SIZE_BYTES
                    reqOrientation[2] = mDataReceiveBuffer.getShort(index)
                    NavController.updateOrientation(reqOrientation,signal)
                    index += Short.SIZE_BYTES
                }
                else -> {
                    Log.d(TAG, "Protocol parse error")
                    break
                }
            }
        }
        mDataReceiveBuffer.clear()
    }

    private fun broadcastData() {
        mBufferLock.lock()
        val packet = DatagramPacket(mDataSendBuffer.array(), mDataSendBuffer.position(), mDestinationIP, mDestinationPort)
        mUdpSocket.send(packet)
        mDataSendBuffer.clear()
        mBufferLock.unlock()
    }

    companion object{
        private var mDataSendBuffer: ByteBuffer = ByteBuffer.allocate(MAX_DATA_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        private var mDataReceiveBuffer: ByteBuffer = ByteBuffer.allocate(MAX_DATA_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        private var mBufferLock: Lock = ReentrantLock()
        var needToBroadcast = false

        fun sendData(data: ByteArray){
            mBufferLock.lock()
            if(mDataSendBuffer.remaining() < data.size) {
                mDataSendBuffer.clear()
                Log.d(TAG, "Unsent data - Data buffer cleared")
            }
            mDataSendBuffer.put(data)
            mBufferLock.unlock()
        }

        fun sendFrame(bitmap: Bitmap) {
            mBufferLock.lock()
            val broadcastFrame = Bitmap.createScaledBitmap(bitmap, 320, 240, false)
            val stream = ByteArrayOutputStream()
            broadcastFrame.compress(Bitmap.CompressFormat.JPEG, 90, stream)
            val imageDataSize = stream.size() + Short.SIZE_BYTES + 1
            if(mDataSendBuffer.remaining() > imageDataSize) {
                mDataSendBuffer.put('C'.toByte())
                mDataSendBuffer.putShort(stream.size().toShort())
                mDataSendBuffer.put(stream.toByteArray())
                mBufferLock.unlock()
            }
        }
    }
}