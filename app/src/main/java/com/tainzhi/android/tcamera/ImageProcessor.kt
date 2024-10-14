package com.tainzhi.android.tcamera

import android.graphics.ImageFormat
import android.media.Image
import android.util.Log
import com.tainzhi.android.tcamera.util.FileUtil
import java.nio.ByteBuffer


object ImageProcessor {
    fun create() {
        System.loadLibrary("image-processor")
        init()
    }


    // exposureTime in nanoseconds
    fun processImages(images: List<Image>, exposureTimes: List<Long>) {
        assert(images.size == exposureTimes.size){ "imageSize:${images.size}, exposureTimes:${exposureTimes.size}" }
        assert(images[0].format == ImageFormat.YUV_420_888) { "imageFormat:${images[0].format}" }
        assert(images[0].planes[1].pixelStride == 2) {"imageFormat is not YUV420sp"}
        Log.d(TAG, "processImage, imageSize:" + images.size + ", exposureTimesSize:" + exposureTimes.size )
        Log.d(TAG, "processImage, imageWidth:" + images[0].width + ", imageHeight:" + images[0].height )
        Log.d(TAG, "processImage, imagePlane0Size:${images[0].planes[0].buffer.remaining()}, rowStride:${images[0].planes[0].rowStride}, pixelStride:${images[0].planes[0].pixelStride}")
        Log.d(TAG, "processImage, imagePlane1Size:${images[0].planes[1].buffer.remaining()}, rowStride:${images[0].planes[1].rowStride}, pixelStride:${images[0].planes[1].pixelStride}")
        Log.d(TAG, "processImage, imagePlane2Size:${images[0].planes[2].buffer.remaining()}, rowStride:${images[0].planes[2].rowStride}, pixelStride:${images[0].planes[2].pixelStride}")
        images.zip(exposureTimes).forEach { (image, exposureTime) ->
            processImage(
                App.getCachePath(),
                image.planes[0].buffer,
                image.planes[1].buffer,
                image.planes[2].buffer,
                image.width,
                image.height,
                exposureTime
            )
        }
    }

    fun destroy() {
        deinit()
    }
    private external fun init()
    private external fun processImage(cachePath: String, yPlane: ByteBuffer, uPlane: ByteBuffer ,  vPlane: ByteBuffer,  width: Int, height: Int, exposureTime: Long)
    private external fun deinit()

    private const val TAG = "ImageProcessor"
}