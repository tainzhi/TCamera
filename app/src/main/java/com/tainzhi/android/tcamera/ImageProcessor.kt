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

    // exposureTime in nanoseconds
    fun collectImage(jobId: Int, image: Image) {
        assert(image.format == ImageFormat.YUV_420_888) { "imageFormat:${image.format}" }
        assert(image.planes[1].pixelStride == 2) {"imageFormat is not YUV420sp"}
        if (App.DEBUG) {
            Log.d(TAG, "collectImage, imageWidth:" + image.width + ", imageHeight:" + image.height)
            Log.d(
                TAG,
                "collectImage, imagePlane[0] Size:${image.planes[0].buffer.remaining()}, rowStride:${image.planes[0].rowStride}, pixelStride:${image.planes[0].pixelStride}"
            )
            Log.d(
                TAG,
                "collectImage, imagePlane[1] Size:${image.planes[1].buffer.remaining()}, rowStride:${image.planes[1].rowStride}, pixelStride:${image.planes[1].pixelStride}"
            )
            Log.d(
                TAG,
                "collectImage, imagePlane[2] Size:${image.planes[2].buffer.remaining()}, rowStride:${image.planes[2].rowStride}, pixelStride:${image.planes[2].pixelStride}"
            )
        }
        collectImage(
            jobId,
            image.planes[0].buffer,
            image.planes[1].buffer,
            image.planes[2].buffer,
            image.width,
            image.height,
        )
        if (App.DEBUG) {
            Log.d(TAG, "collectImage: close image")
        }
        image.close()
    }

    fun destroy() {
        deinit()
    }

    fun postFromNative(jobId: Int, what: Int) {

    }

    private external fun init(cachePath: String)
    private external fun handlePreviewImage(image: Image)

    external fun capture(jobId:Int, captureType: Int, timeStamp: String, frameSize: Int, exposureTimes: List<Long>)
    external fun abortCapture(jobId: Int)

    private external fun collectImage(jobId: Int, yPlane: ByteBuffer, uPlane: ByteBuffer, vPlane: ByteBuffer, width: Int, height: Int)
    private external fun updateCaptureBackupFilePath(path: String)
    private external fun deinit()

    private val TAG = ImageProcessor.javaClass.simpleName
}