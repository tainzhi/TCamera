package com.tainzhi.android.tcamera

import android.graphics.ImageFormat
import android.media.Image
import android.util.Log
import java.nio.ByteBuffer


object ImageProcessor {
    fun create() {
        System.loadLibrary("image-processor")
        init(App.getCachePath())
    }

    fun capture(jobId:Int, timeStamp: String, captureType: Int, frameSize: Int, exposureTimes: List<Long>) {
        Log.d(TAG, "capture, jobId:$jobId, timeStamp:$timeStamp")
        capture(captureType, jobId, timeStamp, frameSize, exposureTimes)
    }


    // exposureTime in nanoseconds
    fun processImage(jobId: Int, image: Image) {
        assert(image.format == ImageFormat.YUV_420_888) { "imageFormat:${image.format}" }
        assert(image.planes[1].pixelStride == 2) {"imageFormat is not YUV420sp"}
        Log.d(TAG, "processImage, imageWidth:" + image.width + ", imageHeight:" + image.height)
        Log.d(TAG, "processImage, imagePlane[0] Size:${image.planes[0].buffer.remaining()}, rowStride:${image.planes[0].rowStride}, pixelStride:${image.planes[0].pixelStride}")
        Log.d(TAG, "processImage, imagePlane[1] Size:${image.planes[1].buffer.remaining()}, rowStride:${image.planes[1].rowStride}, pixelStride:${image.planes[1].pixelStride}")
        Log.d(TAG, "processImage, imagePlane[2] Size:${image.planes[2].buffer.remaining()}, rowStride:${image.planes[2].rowStride}, pixelStride:${image.planes[2].pixelStride}")
        processImage(
            jobId,
            image.planes[0].buffer,
            image.planes[1].buffer,
            image.planes[2].buffer,
            image.width,
            image.height,
        )
        image.close()
    }

    fun destroy() {
        deinit()
    }

    private external fun init(cachePath: String)
    private external fun handlePreviewImage(yPlane: ByteBuffer, uPlane: ByteBuffer, vPlane: ByteBuffer,  width: Int, height: Int)

    private external fun capture(captureType: Int, jobId:Int, timeStamp: String, frameSize: Int, exposureTimes: List<Long>)
    private external fun processImage(jobId: Int, yPlane: ByteBuffer, uPlane: ByteBuffer, vPlane: ByteBuffer,  width: Int, height: Int)
    private external fun updateCaptureBackupFilePath(path: String)
    private external fun deinit()

    private const val TAG = "ImageProcessor"
}