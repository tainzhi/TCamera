package com.tainzhi.android.tcamera

import android.content.ContentValues
import android.content.Context
import android.media.Image
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Message
import android.provider.MediaStore
import android.util.Log
import com.tainzhi.android.tcamera.util.FileUtil
import com.tainzhi.android.tcamera.util.Kpi
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * @author:       tainzhi
 * @mail:         qfq61@qq.com
 * @date:         2019/11/27 下午7:52
 * @description:
 **/

class ImageSaver(
    private val context: Context,
    private val captureType: CaptureType,
    private val images: List<Image>,
    private val hdrExposureTimes: List<Long>,
    private val handler: Handler
) : Runnable {
    override fun run() {

        Log.d(TAG, "begin run for $captureType")
        Kpi.start(Kpi.TYPE.SHOT_TO_SAVE_IMAGE)
        val relativeLocation = Environment.DIRECTORY_DCIM + "/Camera"

        lateinit var image: Image
        if (captureType == CaptureType.JPEG) {
            image = images[0]
        } else {
            Log.d(TAG, "process hdr images, size: ${images.size}, with 3 exposure times: ${hdrExposureTimes[0]}, ${hdrExposureTimes[1]}, ${hdrExposureTimes[2]}")
//            ImageProcessor.processImages(images, hdrExposureTimes)
            return
        }
        val fileName = "IMG_${SimpleDateFormat("yyyyMMddHHmmssSSS", Locale.US).format(image.timestamp)}.jpg"
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativeLocation)
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }
        }
        val resolver = context.contentResolver
        val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

        val buffer = image.planes[0].buffer
        val bytes = ByteArray(image.planes[0].buffer.remaining())
        buffer.get(bytes)

        try {
            imageUri?.let { uri ->
                val stream = resolver.openOutputStream(uri)
                if (stream != null) {
                    stream.write(bytes)

                    val message = Message().apply {
                        obj = uri
                    }
                    handler.removeCallbacksAndMessages(null)
                    handler.sendMessage(message)
                } else {
                    throw IOException("Failed to create new MediaStore record")
                }
            }
        } catch (e: IOException) {
            imageUri?.let { resolver.delete(it, null, null) }
            throw IOException(e)
        } finally {
            // 必须关掉, 否则不能连续拍照
            Log.d(TAG, "close image")
            image.close()
        }
        Log.d(TAG, "end run")
        Kpi.end(Kpi.TYPE.SHOT_TO_SAVE_IMAGE)
    }

    companion object {
        private val TAG = "ImageSaver"
    }
}

enum class CaptureType {
    JPEG,
    HDR
}