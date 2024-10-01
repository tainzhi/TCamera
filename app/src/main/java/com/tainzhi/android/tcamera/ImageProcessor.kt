package com.tainzhi.android.tcamera

import android.graphics.ImageFormat
import android.media.Image
import android.util.Log
import java.nio.ByteBuffer

object ImageProcessor {
    fun create() {
        // System.loadLibrary("image-processor")
        // init()
    }

    fun processImage(image: Image) {
        Log.d(TAG, "processImage: Image.format is YUV_420_888:${image.format == ImageFormat.YUV_420_888}, planes:${image.planes.size}")
        processImage(
            image.planes[0].buffer,
            image.planes[1].buffer,
            image.planes[2].buffer,
            image.width,
            image.height
        )
    }

    fun destroy() {
        deinit()
    }
    external fun init()
    external fun processImage(yPlane: ByteBuffer, uPlane: ByteBuffer ,  vPlane: ByteBuffer,  width: Int, height: Int)
    external fun deinit()

    private const val TAG = "ImageProcessor"
}