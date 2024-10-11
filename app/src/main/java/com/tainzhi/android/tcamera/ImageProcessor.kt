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


    fun processImages(images: List<Image>, exposureTimes: List<Long>) {
        assert(images.size == exposureTimes.size){ "imageSize:${images.size}, exposureTimes:${exposureTimes.size}" }
        Log.d(TAG, "processImage, imageSize:" + images.size + ", exposureTimesSize:" + exposureTimes.size + ", image.format is YUV_420_888:${images[0].format == ImageFormat.YUV_420_888}, planes:${images[0].planes.size}")
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