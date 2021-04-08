package com.thinkalvb.autopilot

import android.graphics.Bitmap
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentLinkedQueue

private const val TAG = "Pilot_Broadcaster"
private const val MAX_DATA_SIZE = 500
private const val DATA_BROADCAST_INTERVAL = 100
private const val FRAME_BROADCAST_INTERVAL = 40
private const val PROBE_INTERVAL = 5000
private const val KEEP_ALIVE_TIMEOUT = 15000
private const val RECEIVE_TIMEOUT = 5
private const val READ_BUFFER_SIZE = 128

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
        } catch (e: Exception) {
            Log.d(TAG,"Exception $e")
            return
        }

        while(!Thread.currentThread().isInterrupted)
        {
            //updateFlags()
            try{

                // experimental
                val x: Short = 180
                val y: Short = 0
                val z: Short = -180
                val prefix: Byte = 'O'.toByte()
                var buffer = ByteBuffer.allocate(10)
                buffer.order(ByteOrder.LITTLE_ENDIAN)
                buffer.put(prefix)
                buffer.putShort(x)
                buffer.putShort(y)
                buffer.putShort(z)

                val packet = DatagramPacket(buffer.array(), buffer.position(), mDestinationIP, mDestinationPort)
                mUdpSocket.send(packet)
                buffer.clear()
                Thread.sleep(1000)




                //if(mRequiresDataBroadcast) broadcastData()
                //if(mRequiresFrameBroadcast) broadcastFrame()
                //receiveData()
            } catch (e: SocketException) {
                Log.d(TAG, "Socket Exception $e")
            } catch (e: IOException) {
                Log.d(TAG,"IO Exception $e")
            } catch (e: Exception) {
                Log.d(TAG,"Exception $e")
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
        val incomingString = String(dataBuffer,Charsets.UTF_8).take(packetSize)
        Log.d(TAG, incomingString)
    }

    private fun broadcastData() {
        mLastDataBroadcastTime = System.currentTimeMillis()
        if(mDataBuffer.remaining() != MAX_DATA_SIZE) {
            val packet = DatagramPacket(mDataBuffer.array(), mDataBuffer.position(), mDestinationIP, mDestinationPort)
            mUdpSocket.send(packet)
            mDataBuffer.clear()
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
    }

    companion object{
        private var mFrameQueue: ConcurrentLinkedQueue<ByteArray> = ConcurrentLinkedQueue()
        private var mDataBuffer = ByteBuffer.allocate(MAX_DATA_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        var needToBroadcast = false

        fun sendData(data: String) {
            if(data.length > mDataBuffer.remaining()) {
                Log.d(TAG, "Unsent data - Data buffer cleared")
                mDataBuffer.clear()
            }
            mDataBuffer.put(data.toByteArray())
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

/* Binary broadcast
                    byte[] receivedData = _UDPserver.Receive(ref _IPendpoint);
                    if(receivedData[0] == 'O')
                    {
                        short x = BitConverter.ToInt16(receivedData, 1);
                        short y = BitConverter.ToInt16(receivedData, 3);
                        short z = BitConverter.ToInt16(receivedData, 5);
                        Debug.WriteLine("Value of X:" + x.ToString() + " y: " + y.ToString() + " z:" + z.ToString());
                    }
 */