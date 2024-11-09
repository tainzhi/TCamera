package com.tainzhi.android.tcamera

import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.params.StreamConfigurationMap
import android.util.Log
import android.util.Range
import android.util.Size
import java.lang.Long.signum
import java.util.Collections
import kotlin.math.abs
import kotlin.math.max

class CameraInfoCache(cameraManager: CameraManager, useFrontCamera: Boolean = false) {
    private lateinit var cameraCharacteristics: CameraCharacteristics
    var cameraId: String = ""
    var largestYuvSize = Size(0, 0)

    // to fixme
    var videoSize = Size(0, 0)
    var isflashSupported = false
    private var requestAvailableAbilities: IntArray? = null
    var sensorOrientation: Int? = 0
    private var noiseModes: IntArray? = null
    private var edgeModes: IntArray? = null
    private var streamConfigurationMap: StreamConfigurationMap? = null
    private var hardwareLevel: Int = 0
    var supportIsoRange = false
    var minIso = 0
    var maxIso = 0
    var supportExposureTime = false
    var supportExposureBracketing = false
    var minExposureTime = 0
    var maxExposureTime = 0
    val exposureBracketingImages = CAPTURE_HDR_FRAME_SIZE
    val exposureBracketingStops = 2.0
    var reprocessingNoiseMode = CameraCharacteristics.NOISE_REDUCTION_MODE_HIGH_QUALITY
    var reprocessingEdgeMode = CameraCharacteristics.EDGE_MODE_HIGH_QUALITY

    init {
        val cameraList = cameraManager.cameraIdList
        for (id in cameraList) {
            cameraCharacteristics = cameraManager.getCameraCharacteristics(id)
            val facing = cameraCharacteristics.get(CameraCharacteristics.LENS_FACING)
            if (facing == (if (useFrontCamera) CameraMetadata.LENS_FACING_FRONT else CameraMetadata.LENS_FACING_BACK)) {
                cameraId = id
                break
            }
        }
        if (cameraCharacteristics == null) {
            throw Exception("cannot get camera characteristics")
        }
        streamConfigurationMap =
            cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        // videoSize = chooseVideoSize(streamConfigurationMap!!.getOutputSizes(MediaRecorder::class.java))
        if (streamConfigurationMap == null) {
            throw Exception("cannot get stream configuration")
        }
        streamConfigurationMap?.outputFormats?.forEach {
            when (it) {
                ImageFormat.YUV_420_888 -> {
                    largestYuvSize =
                        getLargestSize(streamConfigurationMap!!.getOutputSizes(ImageFormat.YUV_420_888))
                }
            }
        }
        requestAvailableAbilities =
            cameraCharacteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
        edgeModes = cameraCharacteristics.get(CameraCharacteristics.EDGE_AVAILABLE_EDGE_MODES)
        noiseModes =
            cameraCharacteristics.get(CameraCharacteristics.NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES)
        hardwareLevel =
            cameraCharacteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)!!
        sensorOrientation = cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)
        isflashSupported =
            cameraCharacteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true

        val isoRange: Range<Int>? = cameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
        if (isoRange!= null) {
            supportIsoRange = true
            minIso = isoRange.lower
            maxIso = isoRange.upper
            val exposureTimeRange: Range<Long>? = cameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
            if (exposureTimeRange!= null) {
                supportExposureTime = true
                supportExposureBracketing = true
                minExposureTime = exposureTimeRange.lower.toInt()
                maxExposureTime = exposureTimeRange.upper.toInt()
            }
        }


    }


    fun isSupportReproc(): Boolean {
        if (requestAvailableAbilities != null &&
            requestAvailableAbilities!!.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_YUV_REPROCESSING) ||
            requestAvailableAbilities!!.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_PRIVATE_REPROCESSING)
        ) {
            Log.d(TAG, "isSupportReproc: true")
            return true
        }
        Log.d(TAG, "isSupportReproc: false")
        return false
    }

    fun getOutputPreviewSurfaceSizes(): Array<Size> {
        return streamConfigurationMap!!.getOutputSizes(SurfaceTexture::class.java)
    }

    fun getOutputJpegSizes(): Array<Size> {
        return streamConfigurationMap!!.getOutputSizes(ImageFormat.JPEG) +
                streamConfigurationMap!!.getHighResolutionOutputSizes(ImageFormat.JPEG)
    }

    fun getOutputYuvSizes(): Array<Size> {
        return streamConfigurationMap!!.getOutputSizes(ImageFormat.YUV_420_888)
    }

    fun isCamera2FullModeAvailable() =
        isHardwareLevelAtLeast(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL)

    fun getCaptureNoiseMode(): Int {
        if (noiseModes!!.contains(CameraCharacteristics.NOISE_REDUCTION_MODE_ZERO_SHUTTER_LAG)) {
            return CameraCharacteristics.NOISE_REDUCTION_MODE_ZERO_SHUTTER_LAG
        } else {
            return CameraCharacteristics.NOISE_REDUCTION_MODE_FAST
        }
    }

    fun getEdgeMode(): Int {
        if (edgeModes!!.contains(CameraCharacteristics.EDGE_MODE_ZERO_SHUTTER_LAG)) {
            return CameraCharacteristics.EDGE_MODE_ZERO_SHUTTER_LAG
        } else {
            return CameraCharacteristics.EDGE_MODE_FAST
        }
    }

    private fun isHardwareLevelAtLeast(level: Int): Boolean {
        if (level == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY) return true
        if (hardwareLevel == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY) return false
        return hardwareLevel >= level
    }

    companion object {

        fun getLargestSize(sizes: Array<Size>): Size {
            var largestSize = Size(0, 0)
            var largestArea = 0
            sizes.forEach {
                val tempArea = it.width * it.height
                if (tempArea > largestArea) {
                    largestArea = tempArea
                    largestSize = it
                }
            }
            return largestSize
        }

        /**
         * @param choices       camera sensor能输出的所有比例，每个比例都是w>h
         * @param viewSize      预览区域所在的窗口的大小，默认为整个屏幕大小，且不会改变。只用来限定 isPreview == true
         * 时返回的结果size大小
         * Portrait方向时，width < height转换下， landscape方向时，width > height 无需转换
         *                      确保 viewSize.width > view.height
         * @param aspectRatio   需要选的sensor输出的image的w:h比例
         *      比如 1:1, 4:3, 16:9, full=device最长的边: device的短边
         * @return size和是否size的w:h == aspectRatio
         */
        @JvmStatic
        fun chooseOptimalSize(
            choices: Array<Size>,
            viewSize: Size,
            ratioValue: Float,
            isPreview: Boolean = false
        ): Pair<Size, Boolean> {
            val filterChoices = choices.filter {
                if (isPreview)
                    it.width <= max(viewSize.width, viewSize.height)
                else
                    true
            }
            val chosenSizes = ArrayList<Size>()
            val constraintChosenSizes = ArrayList<Size>()
            for (option in filterChoices) {
                val tempRatio = option.width / option.height.toFloat()
                // viewSize.width contrast to sensor height
                if (abs(ratioValue - tempRatio) < DIFF_FLOAT_EPS) {
                    chosenSizes.add(option)
                    // for preview, choose smallest size but larger or equal viewSize
                    if (option.height >= viewSize.width) {
                        constraintChosenSizes.add(option)
                    }
                }
            }
            // todo: 优化空间，对于preview可以不使用最高分辨率的，只要宽高大于预览窗口宽高即可
            if (chosenSizes.isNotEmpty()) {
                Log.d(TAG, "optimal size by same w/h aspect ratio")
                if (isPreview) {
                    if (constraintChosenSizes.isNotEmpty()) {
                        return Pair(Collections.min(constraintChosenSizes, CompareSizesByArea()), true)
                    } else {
                        return Pair(Collections.max(chosenSizes, CompareSizesByArea()), true)
                    }
                } else {
                    // 对于非preview，输出最高分辨率的stream
                    // 首先选取宽高和预览窗口一直且最大的输出尺寸
                    val result = Collections.max(chosenSizes, CompareSizesByArea())
                    return Pair(result, true)
                }
            }

            var suboptimalSize = Size(0, 0)
            var suboptimalAspectRatio = 1f
            // 如果不存在宽高比与预览窗口一致的输出尺寸，则选择与其宽高最接近的尺寸
            var minRatioDiff = Float.MAX_VALUE
            filterChoices.forEach { option ->
                val tempRatio = option.width / option.height.toFloat()
                if (abs(tempRatio - ratioValue) < minRatioDiff) {
                    minRatioDiff = abs(tempRatio - ratioValue)
                    suboptimalAspectRatio = tempRatio
                    suboptimalSize = option
                }
            }
            if (suboptimalSize != Size(0, 0)) {
                Log.d(TAG, "optimal size by closet w/h aspect ratio")
                return Pair(suboptimalSize, false)
            }

            // 选择面积与预览窗口最接近的输出尺寸
            var minAreaDiff = Long.MAX_VALUE
            val previewArea = viewSize.height * ratioValue * viewSize.height
            filterChoices.forEach { option ->
                val tempArea = option.width * option.height
                if (abs(previewArea - tempArea) < minAreaDiff) {
                    suboptimalAspectRatio = option.width / option.height.toFloat()
                    suboptimalSize = option
                }
            }
            Log.d(TAG, "optimal size by closet area")
            return Pair(suboptimalSize, false)
        }

        private val TAG = CameraInfoCache::class.java.simpleName
        private const val DIFF_FLOAT_EPS = 0.0001f
        // at least 3 buffers to HDR capture and should be odd number
        const val CAPTURE_HDR_FRAME_SIZE = 3
    }
}

enum class RequestTagType {
    CAPTURE_JPEG,
    CAPTURE_YUV,
    CAPTURE_YUV_BURST_IN_PROCESS
}

data class RequestTagObject(val type: RequestTagType)

class CompareSizesByArea : Comparator<Size> {
    override fun compare(p0: Size, p1: Size) =
        signum(p0.width * p0.height - p1.width.toLong() * p1.height)
}