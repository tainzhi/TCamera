package com.tainzhi.android.tcamera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.media.Image
import android.util.Log
import java.nio.ByteBuffer
import kotlin.jvm.javaClass


class ImageProcessor private constructor(val context: Context) {
    
    fun create() {
        // 加载 libimage-processor.so
        // 不需要prefix lib，和suffix .so
        // 不存在unloadLibrary()库，因为加载后lib成为程序的一部分，若unload可能导致程序崩溃
        Log.d(TAG, "create: ")
        System.loadLibrary("image-processor")
        init(context)
    }

    // exposureTime in nanoseconds
    fun collectImage(jobId: Int, image: Image) {
        assert(image.format == ImageFormat.YUV_420_888) { "imageFormat:${image.format}" }
        assert(image.planes[1].pixelStride == 2) { "imageFormat is not YUV420sp" }
        if (App.DEBUG) {
            Log.d(
                TAG,
                "collectImage, imageWidth:" + image.width + ", imageHeight:" + image.height
            )
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

    companion object {
        val instance: ImageProcessor by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
            ImageProcessor(App.getInstance().applicationContext)
        }
        private val TAG = ImageProcessor.javaClass.simpleName


        @JvmStatic
        fun postFromNative(jobId: Int, resultImagePath: String) {
            Log.d(TAG, "postFromNative: job-${jobId}, cacheImagePath:${resultImagePath}")
            captureJobManager.onNativeProcessed(jobId, resultImagePath)
        }

        lateinit var captureJobManager: CaptureJobManager
    }


    external fun init(context: Context)
    external fun capture(
        jobId: Int,
        captureType: Int,
        timeStamp: String,
        orientation: Int,
        frameSize: Int,
        exposureTimes: List<Long>
    )

    external fun abortCapture(jobId: Int)

    external fun collectImage(
        jobId: Int,
        yPlane: ByteBuffer,
        uPlane: ByteBuffer,
        vPlane: ByteBuffer,
        width: Int,
        height: Int
    )

    external fun configureFilterThumbnails(
        thumbnailWidth: Int, thumbnailHeight: Int, filterNames: List<String>, filterTags: List<Int>, filterThumbnailBitmaps: List<Bitmap?>, lutBitmaps: List<Bitmap?>
    ): Boolean

    external fun processFilterThumbnails(image: Image, orientation: Int, updateRangeStart: Int, updateRangeEnd: Int): Boolean

    external fun clearFilterThumbnails()


    external fun deinit()
}