package com.tainzhi.android.tcamera

import android.content.ContentValues
import android.content.Context
import android.media.Image
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Message
import android.provider.MediaStore
import android.util.Log
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

class MediaSaver(
    private val context: Context,
    private val captureType: CaptureType,
    private val images: List<Image>,
    private val hdrExposureTimes: List<Long>,
    private val handler: Handler
) : Runnable {
    override fun run() {

        Log.d(TAG, "begin run for $captureType")
        Kpi.start(Kpi.TYPE.SHOT_TO_SAVE_IMAGE)
        lateinit var image: Image
        if (captureType == CaptureType.JPEG) {
            image = images[0]
        } else {
            Log.d(TAG, "process hdr images, size: ${images.size}, with 3 exposure times: ${hdrExposureTimes[0]}, ${hdrExposureTimes[1]}, ${hdrExposureTimes[2]}")
//            ImageProcessor.processImages(images, hdrExposureTimes)
            return
        }
        val imageUri = generateMediaUri(context, captureType)
        val resolver = context.contentResolver

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
                    stream.close()
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
        private val TAG = MediaSaver.javaClass.simpleName
        fun generateMediaUri(context: Context, captureType: CaptureType): Uri? {
            val relativeLocation = Environment.DIRECTORY_DCIM + "/Camera"
            // todo: 用拍照时间做文件名，而不是当前保存文件时间
            lateinit var fileName: String
            var mediaUri: Uri?
            val contentValues = ContentValues().apply {
                var fileExtension: String
                var filePrefix: String
                when(captureType) {
                    CaptureType.JPEG -> {
                        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpg")
                        fileExtension = ".jpeg"
                        filePrefix = "IMG_"
                    }
                    CaptureType.HDR -> {
                        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpg")
                        fileExtension = "_HDR.jpeg"
                        filePrefix = "IMG_"
                    }
                    CaptureType.VIDEO -> {
                        fileExtension = ".mp4"
                        filePrefix = "VID_"
                        put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relativeLocation)
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }
                fileName = "${filePrefix}${SimpleDateFormat("yyyyMMddHHmmssSSS", Locale.US).format(System.currentTimeMillis())}${fileExtension}"
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            }
            when(captureType) {
                CaptureType.JPEG, CaptureType.HDR -> {
                    mediaUri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                }
                CaptureType.VIDEO -> {
                    mediaUri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
                }
            }
            if (App.DEBUG) {
                Log.d(TAG, "generateMediaUri: $fileName")
            }
            return mediaUri
        }
    }
}

enum class CaptureType {
    JPEG,
    HDR,
    VIDEO
}