package com.tainzhi.android.tcamera

import android.graphics.ImageFormat
import android.media.Image
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import kotlin.math.exp

fun ByteBuffer.toByteArray(): ByteArray {
    // 如果ByteBuffer有数组支持，并且当前位置是0，限制等于容量，我们可以直接使用其数组
    if (hasArray() && this.position() == 0 && this.limit() == this.capacity()) {
        return array().copyOfRange(0, capacity())
    }

    // 否则，我们需要创建一个新的数组，并将ByteBuffer的内容复制到其中
    val data = ByteArray(remaining())
    // 注意：这里我们使用了remaining()，它返回从当前位置到限制之间的元素数
    this.get(data) // 这将ByteBuffer的当前位置到限制之间的内容复制到data数组中
    // 如果你需要，可以在这里重置ByteBuffer的位置，例如：this.position(0)

    return data
}

object ImageProcessor {
    fun create() {
        System.loadLibrary("image-processor")
        init()
    }


    fun processImages(images: List<Image>, exposureTimes: List<Long>) {
        assert(images.size == exposureTimes.size){ "imageSize:${images.size}, exposureTimes:${exposureTimes.size}" }
        Log.d(TAG, "processImage, imageSize:" + images.size + ", exposureTimesSize:" + exposureTimes.size + ", image.format is YUV_420_888:${images[0].format == ImageFormat.YUV_420_888}, planes:${images[0].planes.size}")
        images.zip(exposureTimes).forEach { (image, exposureTime) ->
            Log.d(TAG, "processImages: ${exposureTime}")
            processImage(
                App.getCachePath(),
                image.planes[0].buffer,
                image.planes[1].buffer,
                image.planes[2].buffer,
                image.width,
                image.height,
                exposureTime
            )

            try {
                val file = File(App.getCachePath() + "/image.yuv")
                val fos = FileOutputStream(file)
                fos.write(image.planes[0].buffer.toByteArray())
                fos.close()
                Log.d(TAG, "save yuv image success:${file.path}")
            } catch (e: Exception) {
                Log.e(TAG, "save image error:${e.message}")
            }
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