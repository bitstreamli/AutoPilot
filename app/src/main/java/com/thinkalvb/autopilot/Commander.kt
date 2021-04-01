package com.thinkalvb.autopilot

import android.util.Log
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketException

private const val TAG = "Pilot_Commander"

class Commander (mDestinationIP: InetAddress, mDestinationPort: Int ) : Runnable {
    companion object {
        var needToBroadcast = false
    }
    private lateinit var mTcpSocket: Socket
    private lateinit var mTCPBufferIncoming: BufferedReader
    private val mServerSocketAddress = InetSocketAddress(mDestinationIP, mDestinationPort)

    override fun run() {

        Log.d(TAG, "Commander Started")
        while(!Thread.currentThread().isInterrupted) {
            try {
                Log.d(TAG, "Waiting for Server")
                mTcpSocket = Socket()
                mTcpSocket.connect(mServerSocketAddress, 10000)
                mTcpSocket.keepAlive = true
                mTcpSocket.soTimeout = 15000

                if(mTcpSocket.isConnected){
                    mTCPBufferIncoming = BufferedReader(InputStreamReader(mTcpSocket.getInputStream(), "UTF-8"))
                    Log.d(TAG, "Connected to Server")
                    needToBroadcast = true
                    processCommands()
                    mTcpSocket.close()
                }
            } catch (e: SocketException) {
                Log.d(TAG,"Socket Exception $e")
            } catch (e: IOException) {
                Log.d(TAG,"IO Exception $e")
            } catch (e: Exception) {
                Log.d(TAG,"Exception $e")
            } finally {
                needToBroadcast = false
            }
        }
        Log.d(TAG, "Commander Stopped")
    }

    private fun processCommands()
    {
        var command: String
        while (mTCPBufferIncoming.readLine().also { command = it } != null) {
            Log.d(TAG, command)
        }
    }
}