package com.tainzhi.android.tcamera

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.Image
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Build
import android.os.Build.VERSION_CODES
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import com.tainzhi.android.tcamera.MainActivity.Companion.CameraMode
import com.tainzhi.android.tcamera.util.Kpi
import com.tainzhi.android.tcamera.util.SettingsManager
import kotlinx.coroutines.Runnable
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * @author:       tainzhi
 * @mail:         qfq61@qq.com
 * @date:         2019/11/27 下午7:52
 * @description:
 **/
enum class CaptureType {
    UNKNOWN,
    JPEG,
    HDR,
    VIDEO;

    companion object {
        private val map = CameraMode.values().associateBy { it.ordinal }
        fun fromInt(value: Int): CameraMode = map[value]!!
    }
}

class CaptureJobManager(val context: Context, val onThumbnailBitmapUpdate: (bitmap: Bitmap, captureType: CaptureType) -> Unit) {
    private val thread = HandlerThread("CaptureJobManagerThread").apply { start() }
    private val handler = Handler(thread.looper) { msg ->
        when (msg.what) {

        }
        true
    }
    private val jobMap = mutableMapOf<Int, CaptureJob>()
    private var currentJobId = -1

    fun addJob(captureJob: CaptureJob) {
        Log.d(TAG, "addJob: job-${captureJob.id}")
        jobMap[captureJob.id] = captureJob
        currentJobId = captureJob.id
    }

    private fun getCurrentJob(): CaptureJob? {
        if (currentJobId == -1 || jobMap.isEmpty() || !jobMap.containsKey(currentJobId)) {
            return null
        }
        return jobMap[currentJobId]
    }

    fun removeJob(jobId: Int) {
        Log.d(TAG, "removeJob: job-$jobId")
        jobMap.remove(jobId)
    }

    fun processJpegImage(image: Image) {
        jobMap[currentJobId]!!.jpegImage = image
        handler.post(Runnable {
            saveJpeg(jobMap[currentJobId]!!)
        })
    }

    fun processYuvImage(image: Image?) {
        handler.post({
            if (image != null) {
                Log.d(TAG, "processYuvImage: ")
                ImageProcessor.collectImage(currentJobId, image)
            } else {
                Log.d(TAG, "processYuvImage but image is null, so abort capture job-${currentJobId}")
                ImageProcessor.abortCapture(currentJobId)
            }
        })
    }

    fun onNativeProcessed(jobId: Int) {
        Log.d(TAG, "onNativeProcessed: job-${jobId}")
    }

    fun processVideo() {
        Log.d(TAG, "processVideo: ")
        handler.post({
            val currentJob = jobMap[currentJobId]
            context.contentResolver.query(currentJob!!.uri!!, null, null, null, null)?.run {
                if (moveToFirst()) {
                    val filePath = getString(getColumnIndexOrThrow(MediaStore.Video.Media.DATA))
                    Log.d(TAG, "processVideo: $filePath")
                    generateThumbnail(currentJobId)
                }
            }
        })
    }

    private fun generateThumbnail(jobId: Int) {
        Log.d(TAG, "generateThumbnail: job-${jobId}")
        val job = jobMap[jobId]!!
        Kpi.start(Kpi.TYPE.IMAGE_TO_THUMBNAIL)
        val thumbnail = if (Build.VERSION.SDK_INT < VERSION_CODES.Q) {
            @Suppress("DEPRECATION")
            val temp = MediaStore.Images.Media.getBitmap(context.contentResolver, job.uri!!)
            ThumbnailUtils.extractThumbnail(temp, 360, 360)
        } else {
            context.contentResolver.loadThumbnail(job.uri!!, Size(360, 360), null)
        }
        Kpi.end(Kpi.TYPE.IMAGE_TO_THUMBNAIL)
        onThumbnailBitmapUpdate(thumbnail, job.captureType)
        SettingsManager.instance.apply {
            saveLastCaptureMediaType(job.captureType)
            saveLastCaptureMediaUri(job.uri!!)
        }
        if (jobMap[jobId]!!.captureType == CaptureType.JPEG) {
            removeJob(jobId)
        }
    }

    /**
     *  saveJpeg -> generateThumbnail
     */
    private fun saveJpeg(job: CaptureJob) {
        Log.d(TAG, "saveJpeg: job-${job.id} ${job.captureType}")
        Kpi.start(Kpi.TYPE.SHOT_TO_SAVE_IMAGE)
        val image: Image = job.jpegImage!!
        val resolver = context.contentResolver
        val buffer = image.planes[0].buffer
        val bytes = ByteArray(image.planes[0].buffer.remaining())
        buffer.get(bytes)

        try {
            job.uri?.let { uri ->
                val stream = resolver.openOutputStream(uri)
                if (stream != null) {
                    stream.write(bytes)
                    stream.close()
                    generateThumbnail(job.id)
                } else {
                    throw IOException("Failed to create new MediaStore record")
                }
            }
        } catch (e: IOException) {
            job.uri?.let { resolver.delete(it, null, null) }
            throw IOException(e)
        } finally {
            // 必须关掉, 否则不能连续拍照
            Log.d(TAG, "close image")
            image.close()
        }
        Kpi.end(Kpi.TYPE.SHOT_TO_SAVE_IMAGE)
    }

    companion object {
        private val TAG = CaptureJob::class.java.simpleName
    }
}

class CaptureJob {
    private val context: Context
    private val captureJobManager: CaptureJobManager
    private val captureTime: Long
    val captureType: CaptureType
    val id = SettingsManager.instance.getJobId() + 1
    val uri by lazy { getMediaUri() }
    lateinit var jpegImage: Image
    var yuvImageList = mutableListOf<Image>()
    private lateinit var exposureTimes: List<Long>
    private var yuvImageSize = 0

    constructor(context: Context,
                captureJobManager: CaptureJobManager,
                captureTime: Long,
                captureType: CaptureType) {
        this.context = context
        this.captureJobManager = captureJobManager
        this.captureTime = captureTime
        this.captureType = captureType
        SettingsManager.instance.saveJobId(id)
        if (captureType == CaptureType.HDR) yuvImageSize = MainActivity.CAPTURE_HDR_FRAME_SIZE
        captureJobManager.addJob(this)

    }

    constructor(
        context: Context,
        captureJobManager: CaptureJobManager,
        captureTime: Long,
        captureType: CaptureType,
        exposureTimes: List<Long>
    ) : this(context, captureJobManager, captureTime, captureType) {
        this.exposureTimes = exposureTimes
        if (captureType == CaptureType.HDR) {
            yuvImageSize = MainActivity.CAPTURE_HDR_FRAME_SIZE
            ImageProcessor.capture(id, captureType.ordinal,"${SimpleDateFormat("yyyyMMddHHmmssSSS", Locale.US).format(captureTime)}",  yuvImageSize, exposureTimes)
        }
    }

    private fun getMediaUri(): Uri? {
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

                CaptureType.UNKNOWN -> TODO()
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativeLocation)
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }
            fileName = "${filePrefix}${SimpleDateFormat("yyyyMMddHHmmssSSS", Locale.US).format(captureTime)}${fileExtension}"
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
        }
        when(captureType) {
            CaptureType.JPEG, CaptureType.HDR -> {
                mediaUri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            }
            CaptureType.VIDEO -> {
                mediaUri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
            }

            CaptureType.UNKNOWN -> TODO()
        }
        if (App.DEBUG) {
            Log.d(TAG, "getMediaUri: $fileName")
        }
        return mediaUri
    }

    companion object {
        private val TAG = CaptureJob.javaClass.simpleName
    }
}