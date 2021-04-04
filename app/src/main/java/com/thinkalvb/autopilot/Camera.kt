package com.thinkalvb.autopilot

import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.media.Image
import android.util.Log
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.core.content.ContextCompat
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.renderscript.*
import com.example.camerax.ScriptC_yuv420888
import com.google.common.util.concurrent.ListenableFuture
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

private const val TAG = "Pilot_Camera"

class Camera(activity: Activity) {
    private var isAvailable = false
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    private lateinit var cameraSelector: CameraSelector
    private lateinit var cameraProvider: ProcessCameraProvider
    private lateinit var imageAnalyzer: ImageAnalysis
    private var mRS: RenderScript = RenderScript.create(activity)
    private var mYuv420: ScriptC_yuv420888 = ScriptC_yuv420888(mRS)

    inner class ImageAnalyzer : ImageAnalysis.Analyzer{
        override fun analyze(imageProxy: ImageProxy) {
            if(imageProxy.format == ImageFormat.YUV_420_888) {
                val capturedFrame = toRGBA(imageProxy, imageProxy.image!!.width, imageProxy.image!!.height)
                if(Broadcaster.needToBroadcast) Broadcaster.sendFrame(capturedFrame!!)
            }
            imageProxy.close()
        }

        private fun toRGBA(imageProxy: ImageProxy, width: Int, height: Int): Bitmap? {
            val planes: Array<Image.Plane> = imageProxy.image!!.planes
            var buffer: ByteBuffer = planes[0].buffer
            val y = ByteArray(buffer.remaining())
            buffer.get(y)
            buffer = planes[1].buffer
            val u = ByteArray(buffer.remaining())
            buffer.get(u)
            buffer = planes[2].buffer
            val v = ByteArray(buffer.remaining())
            buffer.get(v)

            val yRowStride: Int = planes[0].rowStride
            val uvRowStride: Int = planes[1].rowStride
            val uvPixelStride: Int = planes[1].pixelStride

            val typeUCharY = Type.Builder(mRS, Element.U8(mRS))
            typeUCharY.setX(yRowStride).setY(y.size / yRowStride)
            val yAlloc = Allocation.createTyped(mRS, typeUCharY.create())
            yAlloc.copyFrom(y)
            mYuv420._ypsIn = yAlloc

            val typeUCharUV: Type.Builder = Type.Builder(mRS, Element.U8(mRS))
            typeUCharUV.setX(u.size)
            val uAlloc = Allocation.createTyped(mRS, typeUCharUV.create())
            uAlloc.copyFrom(u)
            mYuv420._uIn = uAlloc

            val vAlloc = Allocation.createTyped(mRS, typeUCharUV.create())
            vAlloc.copyFrom(v)
            mYuv420._vIn = vAlloc

            mYuv420._picWidth = width.toLong()
            mYuv420._uvRowStride = uvRowStride.toLong()
            mYuv420._uvPixelStride = uvPixelStride.toLong()

            val outBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val outAlloc = Allocation.createFromBitmap(mRS, outBitmap, Allocation.MipmapControl.MIPMAP_NONE, Allocation.USAGE_SCRIPT)

            val launchOption = Script.LaunchOptions()
            launchOption.setX(0, width)
            launchOption.setY(0, height)

            mYuv420.forEach_doConvert(outAlloc, launchOption)
            outAlloc.copyTo(outBitmap)
            return outBitmap
        }
    }

    init {
        if(activity.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
            isAvailable = true

            cameraProviderFuture = ProcessCameraProvider.getInstance(activity)
            cameraProviderFuture.addListener({
                cameraProvider = cameraProviderFuture.get()
                cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                imageAnalyzer = ImageAnalysis.Builder()
                        .setTargetResolution(Size(640, 480))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                imageAnalyzer.setAnalyzer(cameraExecutor, ImageAnalyzer())
            }, ContextCompat.getMainExecutor(activity))
            cameraExecutor = Executors.newSingleThreadExecutor()
            Log.d(TAG, "Camera Service created")
        }
    }

    fun startCamera(activity: MainActivity) {
        if(!isAvailable) return
        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(activity, cameraSelector, imageAnalyzer)
            Log.d(TAG, "Camera Service started")
        } catch (ex: Exception) {
            Log.e(TAG, "Use case binding failed", ex)
        }
    }

    fun stopCamera() {
        if(!isAvailable) return
        cameraProvider.unbindAll()
        Log.d(TAG, "Camera Service stopped")
    }
}