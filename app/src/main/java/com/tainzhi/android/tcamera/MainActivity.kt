package com.tainzhi.android.tcamera

import android.Manifest
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.*
import android.hardware.camera2.*
import android.hardware.camera2.params.InputConfiguration
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.Image
import android.media.ImageReader
import android.media.ImageWriter
import android.media.MediaActionSound
import android.media.MediaCodec
import android.media.MediaRecorder
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.*
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.util.Range
import android.util.Size
import android.util.SparseIntArray
import android.view.*
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.os.ExecutorCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.tainzhi.android.tcamera.CameraInfoCache.Companion.chooseOptimalSize
import com.tainzhi.android.tcamera.databinding.ActivityMainBinding
import com.tainzhi.android.tcamera.ui.CameraPreviewView
import com.tainzhi.android.tcamera.ui.CircleImageView
import com.tainzhi.android.tcamera.ui.ControlBar
import com.tainzhi.android.tcamera.ui.ErrorDialog
import com.tainzhi.android.tcamera.ui.FilterBar
import com.tainzhi.android.tcamera.ui.FilterType
import com.tainzhi.android.tcamera.ui.VideoIndicator
import com.tainzhi.android.tcamera.ui.scrollpicker.OnSelectedListener
import com.tainzhi.android.tcamera.ui.scrollpicker.ScrollPickerView
import com.tainzhi.android.tcamera.util.Kpi
import com.tainzhi.android.tcamera.util.RotationChangeListener
import com.tainzhi.android.tcamera.util.RotationChangeMonitor
import com.tainzhi.android.tcamera.util.SettingsManager
import com.tainzhi.android.tcamera.util.toast
import java.io.IOException
import java.util.*
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.math.pow

/**
 * @author:       tainzhi
 * @mail:         qfq61@qq.com
 * @date:         2019/11/27 上午11:14
 * @description:
 **/

class MainActivity : AppCompatActivity() {

    private lateinit var _binding: ActivityMainBinding
    private lateinit var cameraPreviewView: CameraPreviewView
    private lateinit var ivThumbnail: CircleImageView
    private lateinit var ivThumbnailVideo: ImageView
    private lateinit var ivTakePicture: ImageView
    private lateinit var ivRecord: ImageView
    private lateinit var ivSwitchCamera: ImageView
    private lateinit var controlBar: ControlBar
    private lateinit var filterBar: FilterBar
    private var filterType = FilterType("Original", 0, 0)
    private lateinit var videoIndicator: VideoIndicator

    private val unGrantedPermissionList: MutableList<String> = ArrayList()

    // to play click sound when take picture
    private val mediaActionSound by lazy { MediaActionSound() }

    private lateinit var rotationChangeMonitor: RotationChangeMonitor
    private var thumbnailOrientation = 0

    private var isEnableZsl = false

    private val cameraThread = HandlerThread("CameraThread").apply { start() }
    private val cameraHandler = Handler(cameraThread.looper)
    private var cameraExecutor = ExecutorCompat.create(cameraHandler)

    private var imageReaderThread = HandlerThread("ImageReaderThread").apply { start() }
    private val imageReaderHandler = Handler(imageReaderThread.looper)

    private lateinit var lastTotalCaptureResult: TotalCaptureResult
    private var zslImageWriter: ImageWriter? = null

    // todo: use coroutine
    // a [Semaphore] to prevent the app from exiting before closing the camera
    private val cameraOpenCloseLock = Semaphore(1)

    private var cameraInfo: CameraInfoCache? = null
    private var previewSession: CameraCaptureSession? = null
    private lateinit var cameraManager: CameraManager
    private var isNeedRecreateCaptureSession = false
    private var isNeedReopenCamera = false
    private var cameraDevice: CameraDevice? = null

    // default open front-facing cameras/lens
    private var useCameraFront = false
    private var isCameraOpen = false
    private lateinit var cameraId: String

    private var flashSupported = false

    // orientation of the camera sensor
    private var sensorOrientation: Int? = 0

    private var cameraState = STATE_PREVIEW

    // handles still image capture
    private lateinit var jpegImageReader: ImageReader
    private lateinit var yuvImageReader: ImageReader
    private var yuvImage: Image? = null
    // todo: 目前有两路preview流，一路preview到屏幕 + 一路yuv用于生成filter thumbnail
    // 后期优化中，只使用一路yuv流，既可以渲染到屏幕，也能生成filter thumbnail
    private lateinit var previewYuvImageReader: ImageReader

    // todo: 启用MediaRecorder后，也不再每次拍照时都需创建 CameraCaptureSession
    // 只需创建一次 previewSession.addTarget(previewSurface, recorder.surface)
    private lateinit var videoSession: CameraCaptureSession
    private lateinit var videoRequestBuilder: CaptureRequest.Builder
    private lateinit var videoSize: Size
    private var isRecordingVideo = false
    private lateinit var mediaRecorder: MediaRecorder

    private lateinit var previewRequestBuilder: CaptureRequest.Builder
    private lateinit var previewRequest: CaptureRequest

    // CameraPreview size
    private var previewSize = Size(0, 0)

    // todo: use coroutine instead
    private val previewSizeLock = ReentrantLock()
    private val previewSizeCondition = previewSizeLock.newCondition()
    private var isPreviewSizeSet = false

    // camera2 output preview surface
    private lateinit var previewSurface: Surface

    // camera2 output preview surface texture
    private lateinit var previewSurfaceTexture: SurfaceTexture

    private var currentCameraMode: CameraMode = CameraMode.PHOTO

    private var lastCapturedMediaUri: Uri? = null
    private var captureType = CaptureType.JPEG

    @Volatile
    private var isCapturing = false
    private val captureJobManager = CaptureJobManager(this) { bitmap, captureType ->
        updateThumbnail(bitmap, captureType)
    }

    private var surfaceTextureListener = object : CameraPreviewView.SurfaceTextureListener {

        override fun onSurfaceTextureAvailable(
            surfaceTexture: SurfaceTexture,
            width: Int,
            height: Int
        ) {
            Log.d(
                TAG,
                "onSurfaceTextureAvailable: ${width}x${height}, isNeedRecreateCaptureSession:" + isNeedRecreateCaptureSession
            )
            previewSurfaceTexture = surfaceTexture
            if (isNeedRecreateCaptureSession) {
                isNeedRecreateCaptureSession = false
                setPreviewSession()
            }
        }

        override fun onSurfaceTextureSizeChanged(
            surfaceTexture: SurfaceTexture,
            width: Int,
            height: Int
        ) {
            Log.d(TAG, "onSurfaceTextureSizeChanged: ${width}x${height}")
            previewSize = Size(width, height)
            // must after set ImageReader and PreviewSurface.SurfaceTexture.FrameSize
            previewSizeLock.lock()
            try {
                isPreviewSizeSet = true
                previewSizeCondition.signal()
            } finally {
                previewSizeLock.unlock()
            }
        }

        override fun onSurfaceTextureUpdated(
            surfaceTexture: SurfaceTexture,
            width: Int,
            height: Int
        ) {
            // producer Camera preview stream write frame to buffer
            // then request render the texture
            cameraPreviewView.requestRender()
        }

    }

    private val cameraDeviceCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(p0: CameraDevice) {
            cameraOpenCloseLock.release()
            this@MainActivity.cameraDevice = p0
            Log.i(TAG, "onCameraOpened: ")
            isCameraOpen = true
            previewSizeLock.lock()
            try {
                while (!isPreviewSizeSet) {
                    if (App.DEBUG) {
                        Log.d(TAG, "onCameraOpened: waiting to image reader set")
                    }
                    previewSizeCondition.await()
                }
            } finally {
                previewSizeLock.unlock()
            }
            setSurfaces()
            setPreviewSession()
        }

        override fun onDisconnected(p0: CameraDevice) {
            Log.i(TAG, "onCameraDisconnected: ${p0}")
            p0.close()
            cameraOpenCloseLock.release()
            closeSurfaces()
            closePreviewSession()
            this@MainActivity.cameraDevice = null
        }

        override fun onError(p0: CameraDevice, p1: Int) {
            Log.e(TAG, "onCameraError: ${p0.id}, reason:$p1")
            finish()
        }

        override fun onClosed(camera: CameraDevice) {
            super.onClosed(camera)
            Log.d(TAG, "onCameraClosed: then close targets.surface(ImageReader) ")
            closeSurfaces()
            if (isNeedReopenCamera) {
                Log.i(TAG, "onCameraClosed: need reopen camera")
                isNeedReopenCamera = false
                isPreviewSizeSet = true
                openCamera()
                setSurfaces()
            }
        }
    }

    private val captureCallback = object : CameraCaptureSession.CaptureCallback() {

        private fun process(result: CaptureResult) {
            when (cameraState) {
                STATE_PREVIEW -> {
                }

                STATE_WAITING_LOCK -> capturePicture(result)
                STATE_WAITING_PRECAPTURE -> {
                    // CONTROL_AE_STATE can be null on some devices
                    val aeState = result.get(CaptureResult.CONTROL_AE_STATE)
                    if (aeState == null ||
                        aeState == CaptureResult.CONTROL_AE_STATE_PRECAPTURE ||
                        aeState == CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED
                    ) {
                        cameraState = STATE_WAITING_NON_PRECAPTURE
                    }
                }

                STATE_WAITING_NON_PRECAPTURE -> {
                    // CONTROL_AE_STATE can be null on some devices
                    val aeState = result.get(CaptureResult.CONTROL_AE_STATE)
                    if (aeState == null || aeState != CaptureResult.CONTROL_AE_STATE_PRECAPTURE) {
                        cameraState = STATE_PICTURE_TAKEN
//                        captureStillPicture()
                    }
                }
            }
        }

        private fun capturePicture(result: CaptureResult) {
            val afState = result.get(CaptureResult.CONTROL_AF_STATE)
            if (afState == null) {
                Log.e(TAG, "capturePicture: afState is null")
            } else if (afState == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED
                || afState == CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED
            ) {
                // CONTROL_AE_STATE can be null on some devices
                Log.e(TAG, "capturePicture: afState is $afState")
                val aeState = result.get(CaptureResult.CONTROL_AE_STATE)
                if (aeState == null || aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
                    cameraState = STATE_PICTURE_TAKEN
                    captureStillPicture()
                } else {
                    isCapturing = false
                    runPrecaptureSequence()
                }
            }
        }

        override fun onCaptureCompleted(
            session: CameraCaptureSession,
            request: CaptureRequest,
            result: TotalCaptureResult
        ) {
            lastTotalCaptureResult = result
            process(result)
        }

        override fun onCaptureProgressed(
            session: CameraCaptureSession,
            request: CaptureRequest,
            partialResult: CaptureResult
        ) {
            process(partialResult)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(_binding.root)
        setFullScreen()
        controlBar = ControlBar(this, _binding, {
            Log.d(TAG, "onPreviewAspectRatioChange")
            closeSurfaces()
            closePreviewSession()
            isNeedRecreateCaptureSession = true
        }, {
            Log.d(TAG, "onHdrStateChange: ")
            closeSurfaces()
            closePreviewSession()
            isNeedRecreateCaptureSession = true
        })
        videoIndicator = VideoIndicator(this, _binding)
        filterBar = FilterBar(this, _binding) { it: FilterType ->
            Log.d(TAG, "onFilterTypeSelected: ${it.name}")
            cameraPreviewView.changeFilterType(it)
            filterType = it
        }

        cameraPreviewView = _binding.previewView
        ivThumbnail = _binding.ivThumbnail.apply {
            setOnClickListener {
                viewMedia()
            }
        }
        ivThumbnailVideo = _binding.ivThumbnailVideo
        SettingsManager.instance.getLastCapturedMediaUri()?.let {
            imageReaderHandler.post({
                lastCapturedMediaUri = SettingsManager.instance.getLastCapturedMediaUri()
                if (lastCapturedMediaUri != null) {
                    val lastCapturedMediaType = SettingsManager.instance.getLastCaptureMediaType()
                    Kpi.start(Kpi.TYPE.IMAGE_TO_THUMBNAIL)
                    val thumbnailBitmap = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                        val temp =
                            @Suppress("DEPRECATION")
                            MediaStore.Images.Media.getBitmap(contentResolver, lastCapturedMediaUri)
                        ThumbnailUtils.extractThumbnail(temp, 360, 360)
                    } else {
                        contentResolver.loadThumbnail(lastCapturedMediaUri!!, Size(360, 360), null)
                    }
                    Kpi.end(Kpi.TYPE.IMAGE_TO_THUMBNAIL)
                    updateThumbnail(thumbnailBitmap, lastCapturedMediaType)
                }
            })
        }
        ivTakePicture = _binding.ivPicture.apply {
            setOnClickListener {
                // Most device front lenses/camera have a fixed focal length
                // for front camera, doesn't need lock focus
                if (!isCapturing) {
                    isCapturing = true
                    Log.d(TAG, "click to take picture, set isCapturing to ${isCapturing}")
                    if (useCameraFront) captureStillPicture() else lockFocus()
                } else {
                    Log.i(TAG, "click to take picture, but waiting")
                }
            }
        }
        ivRecord = _binding.ivRecord.apply {
            setOnClickListener {
                if (isRecordingVideo) stopVideo() else startVideo()
            }
        }
        ivSwitchCamera = _binding.ivSwitchCamera.apply {
            setOnClickListener {
                Log.d(TAG, "click switch camera icon")
                isNeedReopenCamera = true
                useCameraFront = !useCameraFront
                closeSurfaces()
                closePreviewSession()
                closeCamera()
            }
        }

        _binding.cameraModePicker.apply {
            data = cameraModeNames.toList()
            setOnSelectedListener(object : OnSelectedListener {
                override fun onSelected(scrollPickerView: ScrollPickerView<*>?, position: Int) {
                    val mode = CameraMode.fromInt(position)
                    Log.d(TAG, "onSelected: $mode")
                    when (mode) {
                        CameraMode.PHOTO -> {
                            ivRecord.visibility = View.INVISIBLE
                            ivTakePicture.visibility = View.VISIBLE
                            videoIndicator.hide()
                            filterBar.showTrigger()
                        }

                        CameraMode.VIDEO -> {
                            ivRecord.visibility = View.VISIBLE
                            ivTakePicture.visibility = View.INVISIBLE
                            videoIndicator.show()
                            filterBar.resetEffect()
                            filterBar.hideFilterChooser()
                            filterBar.hideTrigger()
                        }
                    }
                    isNeedRecreateCaptureSession = true
                    currentCameraMode = mode
                    controlBar.updateByCameraMode(mode)
                    closeSurfaces()
                    closePreviewSession()
                }

            })
        }

        mediaActionSound.load(MediaActionSound.SHUTTER_CLICK)
        rotationChangeMonitor = RotationChangeMonitor(this).apply {
            rotationChangeListener = object : RotationChangeListener {
                override fun onRotateChange(oldOrientation: Int, newOrientation: Int) {
                    Log.d(TAG, "orientation change from $oldOrientation -> $newOrientation")
                    handleRotation(newOrientation - oldOrientation)
                }
            }
        }
    }


    override fun onStart() {
        super.onStart()
        Log.i(TAG, "onStart: ")
    }

    /**
     * open camera in Activity.onResume, while close camera in Activity.onStop
     */
    override fun onResume() {
        super.onResume()
        Log.i(TAG, "onResume: ")
        setBrightness(true)
        rotationChangeMonitor.enable()
        if (checkPermissions()) {
            Log.d(TAG, "onResume: grant all permissions")
            cameraPreviewView.surfaceTextureListener = surfaceTextureListener
            openCamera()
        } else {
            Log.e(TAG, "onResume: not grant required permissions")
        }
        cameraPreviewView.onResume()
    }

    override fun onPause() {
        Log.i(TAG, "onPause: ")
        cameraPreviewView.onPause()
        super.onPause()
    }

    override fun onStop() {
        Log.i(TAG, "onStop: ")
        setBrightness(false)
        rotationChangeMonitor.disable()
        closeSurfaces()
        closePreviewSession()
        closeCamera()
        super.onStop()
    }

    override fun onDetachedFromWindow() {
        Log.i(TAG, "onDetachedFromWindow: ")
        super.onDetachedFromWindow()
    }

    override fun onDestroy() {
        Log.i(TAG, "onDestroy: ")
        imageReaderThread.quitSafely()
        cameraThread.quitSafely()
        mediaActionSound.release()
        super.onDestroy()
    }

    override fun onWindowAttributesChanged(params: WindowManager.LayoutParams?) {
        super.onWindowAttributesChanged(params)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == MY_PERMISSIONS_REQUEST) {
            var grantedPermissions = unGrantedPermissionList.size
            for (i in grantResults.indices) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    Log.e(TAG, permissions[i] + " block")
                } else {
                    grantedPermissions--
                    Log.d(TAG, permissions[i] + " granted")
                }
            }
            if (grantedPermissions == 0) {
                unGrantedPermissionList.clear()
                Log.d(TAG, "onRequestPermissionsResult: success")
            }
        } else {
            // TODO: 2019-11-22 运行时权限的申请
            Log.i(TAG, "onRequestPermissionsResult: $requestCode")
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        Log.d(TAG, "onConfigurationChanged")
    }

    private fun openCamera() {
        cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
        if (cameraInfo == null) {
            cameraInfo = CameraInfoCache(cameraManager, useCameraFront)
        }
        cameraId = cameraInfo!!.cameraId
        Log.i(TAG, "openCamera: id=${cameraId}")
        sensorOrientation = cameraInfo!!.sensorOrientation
        try {
            // Wait for camera to open - 2.5 seconds is sufficient
            if (!cameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw RuntimeException("Time out waiting to lock camera opening.")
            }
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.CAMERA
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                Log.e(TAG, "openCamera: not granted permission")
                return
            }
            cameraManager.openCamera(cameraId, cameraExecutor, cameraDeviceCallback)
        } catch (e: CameraAccessException) {
            Log.e(TAG, e.toString())
        } catch (e: Exception) {
            Log.e(TAG, "$e")
        }
    }

    private fun closeCamera() {
        Log.i(TAG, "closeCamera: ")
        try {
            cameraOpenCloseLock.acquire()
            cameraDevice?.close()
            cameraDevice = null
            cameraInfo = null
        } catch (e: InterruptedException) {
            throw RuntimeException("Interrupted while trying to lock camera closing.", e)
        } finally {
            cameraOpenCloseLock.release()
            Log.i(TAG, "closeCamera: released")
        }
    }

    private fun checkPermissions(): Boolean {
        Log.d(TAG, "checkPermissions: ")
        // Marshmallow开始运行时申请权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            for (permission in PERMISSIONS_EXCLUDE_STORAGE) {
                if (ContextCompat.checkSelfPermission(this, permission)
                    != PackageManager.PERMISSION_GRANTED
                ) {
                    unGrantedPermissionList.add(permission)
                }
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                if (ContextCompat.checkSelfPermission(this, PERMISSION_STORAGE)
                    != PackageManager.PERMISSION_GRANTED
                ) {
                    unGrantedPermissionList.add(PERMISSION_STORAGE)
                }
            }
        }
        if (unGrantedPermissionList.isNotEmpty()) {
            val tmpPermissions = unGrantedPermissionList.toTypedArray()
            Log.d(TAG, "checkPermissions: size=" + tmpPermissions.size)
            requestPermissions(tmpPermissions, MY_PERMISSIONS_REQUEST)
            return false
        } else {
            return true
        }
    }


    private fun setFullScreen() {
        // reference https://developer.android.com/develop/ui/views/layout/edge-to-edge
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val controller = WindowCompat.getInsetsController(window, _binding.root)
            controller.isAppearanceLightNavigationBars = true
            controller.hide(WindowInsetsCompat.Type.statusBars())
            ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { _, _ ->
//                val insets =
//                    windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
//                // Apply the insets as padding to the view. Here we're setting all of the
//                // dimensions, but apply as appropriate to your layout. You could also
//                // update the views margin if more appropriate.
//                view.updatePadding(insets.left, insets.top, insets.right, insets.bottom)

                // Return CONSUMED if we don't want the window insets to keep being passed
                // down to descendant views.
                WindowInsetsCompat.CONSUMED
            }
        } else {
            // it doesn't work when set transparent for statusbar/navigationbar in styles.xml
            // so hardcode here
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        }

    }

    private fun setSurfaces() {
        Log.i(TAG, "setSurfaces: ")
        val previewAspectRatio =
            if (currentCameraMode == CameraMode.PHOTO) SettingsManager.instance.getPreviewAspectRatio()
            else SettingsManager.PreviewAspectRatio.RATIO_16x9
        val ratioValue: Float =
            if (currentCameraMode == CameraMode.PHOTO) when (previewAspectRatio) {
                SettingsManager.PreviewAspectRatio.RATIO_1x1 -> 1f
                SettingsManager.PreviewAspectRatio.RATIO_4x3 -> 4 / 3f
                SettingsManager.PreviewAspectRatio.RATIO_16x9 -> 16 / 9f
                // activity is portrait, so height < width
                // and sensor is also height < width
                SettingsManager.PreviewAspectRatio.RATIO_FULL -> previewSize.height / previewSize.width.toFloat()
            } else {
                16 / 9f /* video 只支持16:9比率 */
            }
        try {
            if (currentCameraMode == CameraMode.PHOTO) {
                isEnableZsl = cameraInfo!!.isSupportReproc() &&
                        SettingsManager.instance
                            .getBoolean(
                                getString(R.string.settings_key_photo_zsl),
                                SettingsManager.PHOTO_ZSL_DEFAULT_VALUE
                            )
                val isHdr =
                    SettingsManager.instance.getBoolean(SettingsManager.KEY_HDR_ENABLE, false)
                Log.d(
                    TAG,
                    "setSurfaces enableZsl:$isEnableZsl, hdr:${isHdr}"
                )
                // HDR on, then disable zsl
                if (isHdr) {
                    val (chosenYuvSize, isTrueAspectRatioJpegSize) = chooseOptimalSize(
                        cameraInfo!!.getOutputYuvSizes(),
                        previewSize,
                        ratioValue,
                        false
                    )
                    Log.d(
                        TAG,
                        "setSurfaces yuv size:${chosenYuvSize}, match ${previewAspectRatio}:${isTrueAspectRatioJpegSize}"
                    )
                    yuvImageReader = ImageReader.newInstance(
                        chosenYuvSize.width, chosenYuvSize.height,
                        ImageFormat.YUV_420_888,
                        CAPTURE_HDR_FRAME_SIZE
                    )
                    yuvImageReader.setOnImageAvailableListener({ reader ->
                        if (App.DEBUG) {
                            Log.d(TAG, "hdr: yuv image is available")
                        }
                        captureJobManager.processYuvImage(reader.acquireLatestImage())
                    }, imageReaderHandler)
                } else if (isEnableZsl && !isHdr) {
                    yuvImageReader = ImageReader.newInstance(
                        cameraInfo!!.largestYuvSize.width, cameraInfo!!.largestYuvSize.height,
                        ImageFormat.YUV_420_888,
                        YUV_IMAGE_READER_SIZE
                    )
                    yuvImageReader.setOnImageAvailableListener({ reader ->
                        yuvImage?.close()
                        yuvImage = reader.acquireLatestImage()
                    }, imageReaderHandler)
                }
                val (chosenJpegSize, isTrueAspectRatioJpegSize) = chooseOptimalSize(
                    cameraInfo!!.getOutputJpegSizes(),
                    previewSize,
                    ratioValue,
                    false
                )
                Log.d(
                    TAG,
                    "setSurfaces jpeg size:${chosenJpegSize}, match ${previewAspectRatio}:${isTrueAspectRatioJpegSize}"
                )
                jpegImageReader = ImageReader.newInstance(
                    chosenJpegSize.width, chosenJpegSize.height,
                    ImageFormat.JPEG, 1
                )
                jpegImageReader.setOnImageAvailableListener({ reader ->
                    Log.d(TAG, "jpeg: image available ")
                    reader.acquireLatestImage()?.let {
                        captureJobManager.processJpegImage(it, filterType.tag)
                    }
                }, imageReaderHandler)
                previewYuvImageReader = ImageReader.newInstance(previewSize.width, previewSize.height, ImageFormat.YUV_420_888, 1)
                var yuvImageCnt = 0
                previewYuvImageReader.setOnImageAvailableListener({ reader ->
                    reader.acquireLatestImage()?.let {
                        yuvImageCnt++
                        // todo 是否需要降低处理频率，因为 processThumbnails 耗时超过预览帧率间隔，导致发送的msg堵塞在 NativeFilterManager looper
                        if (yuvImageCnt % 3 == 0) {
                            filterBar.processThumbnails(it, getMediaOrientation())
                            yuvImageCnt = 0
                        }
                        it.close()
                    }
                }, imageReaderHandler)
            }
//        make activity portrait, so not handle sensor rotation
//        // device display rotation
//        // 0 [Surface.ROTATION_0]{android.view.Surface.ROTATION_0 = 0}  -> portrait, 把手机垂直放置且屏幕朝向我们的时候，即设备自然方向
//        // 90  -> landscape, 手机向右横放(前置镜头在右边)且屏幕朝向我们的时候，
//        // 180 -> portrait, 手机竖着倒放且屏幕朝我我们
//        // 270 -> 手机向左横放且屏幕朝向我们
//        val displayRotation = windowManager?.defaultDisplay?.rotation
//        // reference: https://developer.android.com/training/camera2/camera-preview#device_rotation
//        // 对于大多数设备，portrait且屏幕面向用户
//        // 前置镜头 sensorOrientation = 270, 所以要相对于设备方向逆时针旋转 270
//        // 后置镜头 sensorOrientation = 90, 所以要相对于设备方向逆时针旋转 90
//        // 最终的 rotation = (sensorOrientationDegrees - deviceOrientationDegrees * sign + 360) % 360
//        // sign = 1, 前置镜头，-1 后置镜头
//        sensorOrientation = cameraInfo.sensorOrientation
//        Log.d(TAG, "setUpCameraPreview: displayRotation=$displayRotation, sensorOrientation=$sensorOrientation")
//        // device portrait orientation ,then deviceHeight > deviceWidth
//        // device landscape orientation, then deviceHeight < deviceWidth
//        // whether device orientation, sensorWidth > deviceHeight is always true
//        val swappedDimensions = areDimensionsSwapped(displayRotation)
//        if (swappedDimensions) {
//            Log.d(TAG, "setUpCameraPreview: rotate switch width/height")
//            viewSize = Size(viewSize.height, viewSize.width)
//        }

            // camera output surface size, maybe smaller than viewSize
            // e.g. set camera preview 1:1 for a device 1080:2040, then previewSize 1080:1080, viewSize 1080:2040
            val (cameraOutputPreviewTextureSize, isTrueAspectRatio) = chooseOptimalSize(
                cameraInfo!!.getOutputPreviewSurfaceSizes(),
                previewSize,
                ratioValue,
                true
            )
            Log.d(
                TAG,
                "setSurfaces preview size:${cameraOutputPreviewTextureSize}, match ${previewAspectRatio}:${isTrueAspectRatio}"
            )
            previewSurfaceTexture.setDefaultBufferSize(
                cameraOutputPreviewTextureSize.width,
                cameraOutputPreviewTextureSize.height
            )
            previewSurface = Surface(previewSurfaceTexture)
            if (currentCameraMode == CameraMode.VIDEO) {
                videoSize = cameraOutputPreviewTextureSize
                if (App.DEBUG) {
                    Log.d(TAG, "setSurfaces: videoSize:${videoSize}")
                }
            }
            val previewTopMargin =
                resources.getDimensionPixelSize(R.dimen.preview_top_margin) * resources.displayMetrics.density
            val previewRect = when (previewAspectRatio) {
                SettingsManager.PreviewAspectRatio.RATIO_1x1 -> RectF(
                    0f,
                    previewTopMargin,
                    previewSize.width.toFloat(),
                    previewTopMargin + previewSize.width.toFloat()
                )

                SettingsManager.PreviewAspectRatio.RATIO_4x3 -> RectF(
                    0f,
                    previewTopMargin,
                    previewSize.width.toFloat(),
                    previewTopMargin + previewSize.width * 4 / 3f
                )

                SettingsManager.PreviewAspectRatio.RATIO_16x9 -> RectF(
                    0f,
                    previewTopMargin,
                    previewSize.width.toFloat(),
                    previewTopMargin + previewSize.width * 16 / 9f
                )

                else -> RectF(0f, 0f, previewSize.width.toFloat(), previewSize.height.toFloat())
            }
            cameraPreviewView.setCoordinate(
                cameraOutputPreviewTextureSize,
                isTrueAspectRatio,
                previewRect,
                useCameraFront
            )
            flashSupported = cameraInfo!!.isFlashSupported

        } catch (e: CameraAccessException) {
            Log.e(TAG, e.toString())
        } catch (e: Exception) {
            Log.e(TAG, Log.getStackTraceString(e))
            ErrorDialog.Companion.newInstance(getString(R.string.camera_error))
                .show(supportFragmentManager, "fragment_dialog")
        }
    }

    private fun closeSurfaces() {
        previewSurface.release()
        // close target surface in CaptureSession.onClosed
        jpegImageReader.close()
        previewYuvImageReader.close();
        if (isEnableZsl) {
            yuvImageReader.close()
            zslImageWriter?.close()
            zslImageWriter = null
        }
        isPreviewSizeSet = false
    }

    private fun setPreviewSession() {
        Log.i(TAG, "setPreviewSession: ")
        try {
            val captureSessionStateCallback = object : CameraCaptureSession.StateCallback() {
                override fun onClosed(session: CameraCaptureSession) {
                    super.onClosed(session)
                    Log.d(TAG, "onSessionClosed")
                    if (isNeedRecreateCaptureSession) {
                        Log.d(TAG, "onSessionClosed: need to recreate preview session")
                        isNeedRecreateCaptureSession = false
                        setSurfaces()
                        setPreviewSession()
                    }
                }

                override fun onSurfacePrepared(session: CameraCaptureSession, surface: Surface) {
                    super.onSurfacePrepared(session, surface)
                    Log.d(TAG, "onSessionSurfacePrepared preview")
                }

                override fun onConfigureFailed(p0: CameraCaptureSession) {
                    Log.e(TAG, "onConfigureFailed: preview")
                    toast("Failed to configure CaptureSession")
                }

                override fun onConfigured(session: CameraCaptureSession) {
                    previewSession = session
                    setPreviewRequest()
                }

                override fun onReady(session: CameraCaptureSession) {
                    // When the session is ready, we start displaying the preview.
                    super.onReady(session)
                    Log.d(TAG, "onSessionReady preview")
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val outputConfigurations = mutableListOf(
                    OutputConfiguration(previewSurface),
                )
                if (currentCameraMode == CameraMode.PHOTO) {
                    Log.d(TAG, "setPreviewSession: photo")
                    outputConfigurations.add(OutputConfiguration(jpegImageReader.surface))
                    outputConfigurations.add(OutputConfiguration(previewYuvImageReader.surface))
                    val isHdr =
                        SettingsManager.instance.getBoolean(SettingsManager.KEY_HDR_ENABLE, false)
                    if (isEnableZsl || isHdr) {
                        outputConfigurations.add(
                            OutputConfiguration(yuvImageReader.surface)
                        )
                    }
                    val sessionConfiguration = SessionConfiguration(
                        SessionConfiguration.SESSION_REGULAR,
                        outputConfigurations,
                        cameraExecutor,
                        captureSessionStateCallback
                    )
                    if (isEnableZsl && !isHdr) {
                        sessionConfiguration.inputConfiguration =
                            InputConfiguration(
                                yuvImageReader.width,
                                yuvImageReader.height,
                                ImageFormat.YUV_420_888
                            )
                    }
                    cameraDevice?.createCaptureSession(sessionConfiguration)
                } else {
                    Log.d(TAG, "setPreviewSession: video")
                    val sessionConfiguration = SessionConfiguration(
                        SessionConfiguration.SESSION_REGULAR,
                        outputConfigurations,
                        cameraExecutor,
                        captureSessionStateCallback
                    )
                    cameraDevice?.createCaptureSession(sessionConfiguration)
                }
            } else {
                if (isEnableZsl) {

                } else {
                    @Suppress("DEPRECATION")
                    cameraDevice?.createCaptureSession(
                        arrayListOf(previewSurface, jpegImageReader.surface),
                        captureSessionStateCallback, cameraHandler
                    )
                }
            }
        } catch (e: CameraAccessException) {
            Log.e(TAG, e.toString())
        }
    }

    private fun closePreviewSession() {
        // closePreviewSession -> session.onClosed() -> closeCamera()
        Log.i(TAG, "closePreviewSession")
        previewSession?.stopRepeating()
        previewSession?.close()
        previewSession = null
    }

    private fun setPreviewRequest() {
        Log.i(TAG, "setPreviewRequest: ")
        if (cameraDevice == null) return
        val isHdr = SettingsManager.instance.getBoolean(SettingsManager.KEY_HDR_ENABLE, false)
        if (currentCameraMode == CameraMode.PHOTO) {
            if (isEnableZsl && !isHdr) {
                zslImageWriter =
                    ImageWriter.newInstance(
                        previewSession!!.inputSurface!!,
                        ZSL_IMAGE_WRITER_SIZE
                    )
                zslImageWriter?.setOnImageReleasedListener({ _ ->
                    {
                        Log.d(TAG, "ZslImageWriter onImageReleased()")
                    }
                }, imageReaderHandler)
                Log.d(TAG, "onSessionConfigured: create ImageWriter")
            }
        }
        try {
            previewRequestBuilder =
                cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            previewRequestBuilder.addTarget(previewSurface)
            if (currentCameraMode == CameraMode.PHOTO) {
                previewRequestBuilder.addTarget(previewYuvImageReader.surface)
                if (isEnableZsl && !isHdr) {
                    previewRequestBuilder.addTarget(yuvImageReader.surface)
                }
            } else if (currentCameraMode == CameraMode.VIDEO) {
                previewRequestBuilder.set(
                    CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                    CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_PREVIEW_STABILIZATION
                )
            }

            previewRequestBuilder.apply {
                set(
                    CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                )
                set(CaptureRequest.NOISE_REDUCTION_MODE, cameraInfo!!.getCaptureNoiseMode())
                set(CaptureRequest.EDGE_MODE, cameraInfo!!.getEdgeMode())
                if (flashSupported) {
                    set(
                        CaptureRequest.CONTROL_AE_MODE,
                        CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH
                    )
                }
            }
            // if (AFtrigger) {
            //     b1.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START);
            //     mCurrentCaptureSession.capture(b1.build(), mCaptureCallback, mOpsHandler);
            //     b1.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_IDLE);
            // }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val controlZsl: Boolean? =
                    previewRequestBuilder.get(CaptureRequest.CONTROL_ENABLE_ZSL)
                Log.d(TAG, "setPreviewRequest: controlZsl=${controlZsl}")
            }
            previewRequest = previewRequestBuilder.build()
            previewSession?.setRepeatingRequest(
                previewRequest,
                captureCallback, cameraHandler
            )
        } catch (e: CameraAccessException) {
            Log.e(TAG, e.toString())
        }
    }

    /**
     * Run the precapture sequence for capturing a still image. This method should be called when
     * we get a response in [.captureCallback] from [.lockFocus].
     */
    private fun runPrecaptureSequence() {
        Log.d(TAG, "runPrecaptureSequence: ")
        try {
            previewRequestBuilder.set(
                CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START
            )
            cameraState = STATE_WAITING_PRECAPTURE
            previewSession?.capture(
                previewRequestBuilder.build(), captureCallback,
                cameraHandler
            )
        } catch (e: CameraAccessException) {
            Log.e(TAG, e.toString())
        }
    }

    /**
     * Capture a still picture. This method should be called when we get a response in
     * [.captureCallback] from both [.lockFocus].
     */
    private fun captureStillPicture() {
        isEnableZsl = SettingsManager.instance
            .getBoolean(
                getString(R.string.settings_key_photo_zsl),
                SettingsManager.PHOTO_ZSL_DEFAULT_VALUE
            )
        val isHdr =
            SettingsManager.instance.getBoolean(SettingsManager.KEY_HDR_ENABLE, false)
        Log.d(TAG, "captureStillPicture: enableZsl:${isEnableZsl}")
        Kpi.start(Kpi.TYPE.SHOT_TO_SHOT)
        captureType = if (SettingsManager.instance
                .getBoolean(SettingsManager.KEY_HDR_ENABLE, false)
        ) CaptureType.HDR else CaptureType.JPEG
        try {
            if (isEnableZsl && !isHdr && yuvImage == null) {
                Log.e(TAG, "captureStillPicture: no yuv image available")
                return
            }
            val captureBuilder =
                if (zslImageWriter != null && captureType != CaptureType.HDR
                ) {
                    Log.d(TAG, "captureStillPicture: queueInput yuvLatestReceiveImage to HAL")
                    zslImageWriter!!.queueInputImage(yuvImage)
                    cameraDevice!!.createReprocessCaptureRequest(lastTotalCaptureResult)
                } else {
                    Log.i(
                        TAG,
                        "captureStillPicture: enableZsl:${isEnableZsl}, enableHdr:${
                            SettingsManager.instance
                                .getBoolean(SettingsManager.KEY_HDR_ENABLE, false)
                        }"
                    )
                    cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
                }
            captureBuilder.apply {
                addTarget(jpegImageReader.surface)
                if (isEnableZsl && captureType != CaptureType.HDR) {
                    set(CaptureRequest.NOISE_REDUCTION_MODE, cameraInfo!!.reprocessingNoiseMode)
                    set(CaptureRequest.EDGE_MODE, cameraInfo!!.reprocessingEdgeMode)
                }
                set(CaptureRequest.JPEG_QUALITY, 95)
                // https://developer.android.com/training/camera2/camera-preview#orientation_calculation
                // rotation = (sensorOrientationDegrees - deviceOrientationDegrees * sign + 360) % 360
                // sign 1 for front-facing cameras, -1 for back-facing cameras
                set(CaptureRequest.JPEG_ORIENTATION, getMediaOrientation())
                set(
                    CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                )
                Log.d(TAG, "captureStillPicture: flashSupported:$flashSupported")
                if (flashSupported) {
                    set(
                        CaptureRequest.CONTROL_AE_MODE,
                        CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH
                    )
                } else {
                    captureBuilder.set(
                        CaptureRequest.CONTROL_AE_MODE,
                        CaptureRequest.CONTROL_AE_MODE_OFF
                    )
                    captureBuilder.set(
                        CaptureRequest.FLASH_MODE,
                        CaptureRequest.FLASH_MODE_TORCH
                    )
                }
            }
            captureBuilder.setTag(RequestTagType.CAPTURE_JPEG)
            if (captureType == CaptureType.JPEG) {
                CaptureJob(
                    this,
                    captureJobManager,
                    System.currentTimeMillis(),
                    captureType
                )
            }
            previewSession?.apply {
                capture(
                    captureBuilder.build(),
                    object : CameraCaptureSession.CaptureCallback() {

                        override fun onCaptureCompleted(
                            session: CameraCaptureSession,
                            request: CaptureRequest,
                            result: TotalCaptureResult
                        ) {
                            super.onCaptureCompleted(session, request, result)
                            if (captureType == CaptureType.JPEG) {
                                isCapturing = false
                                unlockFocus()
                                Log.d(
                                    TAG,
                                    "capture onCaptureCompleted, request.tag:${request.tag}, set isCapturing to $isCapturing"
                                )
                            }
                        }
                    },
                    cameraHandler
                )
            }
            mediaActionSound.play(MediaActionSound.SHUTTER_CLICK)
            // HDR拍照先拍一张jpeg,生成临时照片和thumbnail; 再拍3张yuv图片用于合成
            if (captureType == CaptureType.HDR && SettingsManager.instance.getBoolean(
                    SettingsManager.KEY_HDR_ENABLE,
                    false
                )
            ) {
                Log.d(TAG, "captureStillPicture: HDR enable")
                captureBuilder.set(
                    CaptureRequest.CONTROL_AE_MODE,
                    CaptureRequest.CONTROL_AE_MODE_OFF
                )
                captureBuilder.removeTarget(jpegImageReader.surface)
                captureBuilder.addTarget(yuvImageReader.surface)

                val exposureTimeList = mutableListOf<Long>()
                val baseExposureTime = 1000000000L / 30
                val halfImageSize = cameraInfo!!.exposureBracketingImages / 2
                val scale = 2.0.pow(cameraInfo!!.exposureBracketingStops / halfImageSize)
                val requests = ArrayList<CaptureRequest>()
                // darker images
                for (i in 0 until halfImageSize) {
                    var exposureTime = baseExposureTime
                    if (cameraInfo!!.supportExposureTime) {
                        var currentScale = scale
                        for (j in i until halfImageSize - 1) {
                            currentScale *= scale
                        }
                        exposureTime = (exposureTime / currentScale).toLong()
                        if (exposureTime < cameraInfo!!.minExposureTime) {
                            exposureTime = cameraInfo!!.minExposureTime.toLong()
                        }
                        exposureTimeList.add(exposureTime)
                        captureBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, exposureTime)
                        captureBuilder.setTag(RequestTagType.CAPTURE_YUV_BURST_IN_PROCESS)
                        requests.add(captureBuilder.build())
                    }
                }
                // base image
                exposureTimeList.add(baseExposureTime)
                captureBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, baseExposureTime)
                captureBuilder.setTag(RequestTagType.CAPTURE_YUV_BURST_IN_PROCESS)
                requests.add(captureBuilder.build())
                // lighter images
                for (i in 0 until halfImageSize) {
                    var exposureTime = baseExposureTime
                    if (cameraInfo!!.supportExposureTime) {
                        var currentScale = scale
                        for (j in i until halfImageSize)
                            currentScale *= scale
                        exposureTime = (exposureTime * currentScale).toLong()
                        if (exposureTime > cameraInfo!!.maxExposureTime) {
                            exposureTime = cameraInfo!!.maxExposureTime.toLong()
                        }
                        exposureTimeList.add(exposureTime)
                        captureBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, exposureTime)
                        if (i == halfImageSize - 1) {
                            captureBuilder.setTag(RequestTagType.CAPTURE_YUV)
                        } else {
                            captureBuilder.setTag(RequestTagType.CAPTURE_YUV_BURST_IN_PROCESS)
                        }
                        requests.add(captureBuilder.build())
                    }
                }
                Log.d(
                    TAG,
                    "captureStillPicture: captureBurst for ${captureType}, requests.size: ${requests.size}, exposureTimes:[${exposureTimeList.joinToString(",")}]"
                )
                CaptureJob(
                    this,
                    captureJobManager,
                    System.currentTimeMillis(),
                    captureType,
                    getMediaOrientation(),
                    exposureTimeList
                )
                previewSession?.apply {
                    captureBurst(requests, object : CameraCaptureSession.CaptureCallback() {
                        override fun onCaptureCompleted(
                            session: CameraCaptureSession,
                            request: CaptureRequest,
                            result: TotalCaptureResult
                        ) {
                            super.onCaptureCompleted(session, request, result)
                            Log.d(TAG, "onCaptureCompleted: ${request.tag}")
                            if (request.tag == RequestTagType.CAPTURE_YUV) {
                                isCapturing = false
                                unlockFocus()
                                Log.d(
                                    TAG,
                                    "capture onCaptureCompleted for generating yuv frames, set isCapturing to $isCapturing"
                                )
                            }
                        }
                    }, cameraHandler)
                }
            }
        } catch (e: CameraAccessException) {
            Log.e(TAG, e.toString())
        }
        yuvImage = null
    }

    // Lock the focus as the first step for a still image capture.
    private fun lockFocus() {
        Log.d(TAG, "lockFocus: ")
        try {
            // This is how to tell the camera to lock focus.
            previewRequestBuilder.set(
                CaptureRequest.CONTROL_AF_TRIGGER,
                CameraMetadata.CONTROL_AF_TRIGGER_START
            )
            // Tell #captureCallback to wait for the lock.
            cameraState = STATE_WAITING_LOCK
            previewSession?.capture(
                previewRequestBuilder.build(), captureCallback,
                cameraHandler
            )
        } catch (e: CameraAccessException) {
            Log.e(TAG, e.toString())
        }

    }

    // Unlock the focus. This method should be called when still image capture sequence is finished.
    private fun unlockFocus() {
        try {
            // Reset the auto-focus trigger
            previewRequestBuilder.apply {
                set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL)
                if (flashSupported) {
                    set(
                        CaptureRequest.CONTROL_AE_MODE,
                        CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH
                    )
                }
            }
            previewSession?.capture(
                previewRequestBuilder.build(), captureCallback,
                cameraHandler
            )
            // After this, the camera will go back to the normal state of preview.
            cameraState = STATE_PREVIEW
            previewSession?.setRepeatingRequest(
                previewRequest, captureCallback,
                cameraHandler
            )
        } catch (e: CameraAccessException) {
            Log.e(TAG, e.toString())
        }
    }

    private fun viewMedia() {
        lastCapturedMediaUri = SettingsManager.instance.getLastCapturedMediaUri()
        if (lastCapturedMediaUri != null) {
//             if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
//                 val intent = Intent().apply {
//                     action = Intent.ACTION_VIEW
//                     addCategory(Intent.CATEGORY_APP_GALLERY)
//                     setDataAndType(capturedImageUri, "image/*")
//                     flags = Intent.FLAG_ACTIVITY_NEW_TASK
//                 }
//                 startActivity(intent)
//             }
            startActivity(Intent(Intent.ACTION_VIEW, lastCapturedMediaUri))
        } else {
            toast("请先拍照")
        }
    }

    private fun updateThumbnail(bitmap: Bitmap, captureType: CaptureType) {
        mainExecutor.execute {
            ivThumbnail.apply {
                post {
                    setImageBitmap(bitmap)
                }
            }
            if (captureType == CaptureType.VIDEO) {
                ivThumbnailVideo.visibility = View.VISIBLE
            } else {
                ivThumbnailVideo.visibility = View.GONE
            }
            // scale animation from 1 - 1.2 - 1
            ivThumbnail.animate()
                .setDuration(80)
                .scaleX(1.2f)
                .scaleY(1.2f)
                .withEndAction {
                    ivThumbnail.animate()
                        .setDuration(80)
                        .scaleX(1f)
                        .scaleY(1f)
                        .start()
                }
                .start()
        }
    }

    private fun startVideo() {
        if (cameraDevice == null) return
        // Prevents screen rotation during the video recording
        this.requestedOrientation =
            ActivityInfo.SCREEN_ORIENTATION_LOCKED
        try {
            // todo: 弃用 MediaRecorder, 避免在每次 MediaRecorder.stop()之后需要重新set
            // 导致其使用的的surface也需要每次重新设置，最终导致其需要重新设置 videoSession
            // 若是使用 MediaCodec, 则可以在进入video模式开启预览创建 previewSession.addTarget(previewSurface, recorderSurface)
            //  参考 https://blog.csdn.net/weixin_44752167/article/details/131091958
            // 无需每次开启拍摄都需要创建一次 VideoSession
            // MediaRecorder stop 之后需要重新set才能使用
            val videoSurface = MediaCodec.createPersistentInputSurface()
            @Suppress("DEPRECATION")
            mediaRecorder =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(this) else MediaRecorder()
            val videoUri = CaptureJob(
                this,
                captureJobManager,
                System.currentTimeMillis(),
                CaptureType.VIDEO
            ).uri
            mediaRecorder.apply {
                setOrientationHint(getMediaOrientation())
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setVideoSource(MediaRecorder.VideoSource.SURFACE)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setOutputFile(
                    this@MainActivity.contentResolver.openFileDescriptor(
                        videoUri!!,
                        "w"
                    )!!.fileDescriptor

                )
                setVideoEncodingBitRate(10000000)
                setVideoFrameRate(30)
                setVideoSize(videoSize.width, videoSize.height)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(16)
                setAudioSamplingRate(44100)
                // 必须要在 set CaptureSession之前配置好 recorder surface
                setInputSurface(videoSurface)
                prepare()
            }
            Log.d(TAG, "startVideo: set MediaRecorder")
            val outputConfigurations = mutableListOf(
                OutputConfiguration(previewSurface),
                OutputConfiguration(videoSurface)
            )
            val sessionConfiguration = SessionConfiguration(
                SessionConfiguration.SESSION_REGULAR,
                outputConfigurations,
                cameraExecutor,
                object: CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        videoSession = session
                        videoRequestBuilder =
                            cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
                                .apply {
                                    addTarget(previewSurface)
                                    addTarget(videoSurface)
                                    set(
                                        CaptureRequest.CONTROL_AF_MODE,
                                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO
                                    )
                                    set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range(30, 30))
                                    set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                                        CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_PREVIEW_STABILIZATION)
                                }
                        videoSession.setRepeatingRequest(
                            videoRequestBuilder.build(), null, cameraHandler
                        )
                        if (!isRecordingVideo) {
                            runOnUiThread {
                                Log.d(TAG, "startVideo onCaptureStarted: ")
                                videoIndicator.start()
                                ivRecord.setImageResource(R.drawable.btn_record_stop)
                                isRecordingVideo = true
                                mediaRecorder.apply {
                                    start()
                                }
                            }
                        }
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        Log.d(TAG, "startVideo onConfigureFailed: set video session failed")
                    }
                },
            )
            // 停止预览和开启 video record预览要放在一起，避免 MediaRecorder创建期间预览卡顿
            // 当然最好的方式是不使用 MediaRecorder，使用 MediaCodec, 这样就可以在切换到video模式时创建一次recorder后多次 stop/start使用
            closePreviewSession()
            cameraDevice?.createCaptureSession(sessionConfiguration)
        } catch (e: CameraAccessException) {
            Log.e(TAG, "startVideo CameraAccessException", e)
        } catch (e: IOException) {
            Log.e(TAG, "startVideo IOException", e)
        }
    }

    private fun stopVideo() {
        Log.d(TAG, "stopVideo: ")
        this.requestedOrientation =
            ActivityInfo.SCREEN_ORIENTATION_SENSOR
        videoSession.stopRepeating()
        setPreviewSession()
//        previewSession!!.setRepeatingRequest(previewRequestBuilder.build(), null ,cameraHandler)
        mediaRecorder.apply {
            // MediaRecorder stop 之后需要重新初始化配置，即重新 prepare()
            // 参考：https://developer.android.com/reference/android/media/MediaRecorder
            stop()
            reset()
            release()
        }
        videoIndicator.stop()
        captureJobManager.processVideo()
        isRecordingVideo = false
        ivRecord.setImageResource(R.drawable.btn_record_start)
    }

    private fun handleRotation(rotateAngle: Int) {
        thumbnailOrientation = (-rotateAngle + thumbnailOrientation +
                (if (rotateAngle > 180) 360 else 0)) % 360
        ivThumbnail.animate()
            .setDuration(800)
            .rotation(thumbnailOrientation.toFloat())
            .start()
        ivSwitchCamera.animate()
            .setDuration(800)
            .rotation(thumbnailOrientation.toFloat())
            .start()

        controlBar.rotate(thumbnailOrientation)
    }

    private fun setBrightness(forceMax: Boolean) {
        val layoutAttributes = window.attributes
        if (forceMax) {
            // layoutAttributes.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
            layoutAttributes.screenBrightness = 0.7f
        } else {
            layoutAttributes.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
        runOnUiThread {
            window.attributes = layoutAttributes
        }
    }

    private fun getMediaOrientation(): Int {
        val displayRotation = this.display!!.rotation
        return (sensorOrientation!! - OREIENTATIONS.get(displayRotation) * (if (useCameraFront) 1 else -1) + 360) % 360
    }

    companion object {
        private val TAG = MainActivity::class.java.simpleName
        private val OREIENTATIONS = SparseIntArray()

        private const val MY_PERMISSIONS_REQUEST = 10001
        private val PERMISSIONS_EXCLUDE_STORAGE = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA
        )
        private val PERMISSION_STORAGE = Manifest.permission.WRITE_EXTERNAL_STORAGE

        private const val YUV_IMAGE_READER_SIZE = 8
        private const val ZSL_IMAGE_WRITER_SIZE = 2
        // at least 3 buffers to HDR capture and should be odd number
        const val CAPTURE_HDR_FRAME_SIZE = 3

        enum class CameraMode {
            VIDEO,
            PHOTO;

            companion object {
                private val map = CameraMode.values().associateBy { it.ordinal }
                fun fromInt(value: Int): CameraMode = map[value]!!
            }
        }

        val cameraModeNames = arrayOf("视频", "拍照")

        init {
            OREIENTATIONS.append(Surface.ROTATION_0, 0)
            OREIENTATIONS.append(Surface.ROTATION_90, 90)
            OREIENTATIONS.append(Surface.ROTATION_180, 180)
            OREIENTATIONS.append(Surface.ROTATION_270, 270)

        }
        /**
         * Camera state: Showing camera preview.
         */
        private val STATE_PREVIEW = 0

        /**
         * Camera state: Waiting for the focus to be locked.
         */
        private val STATE_WAITING_LOCK = 1

        /**
         * Camera state: Waiting for the exposure to be precapture state.
         */
        private val STATE_WAITING_PRECAPTURE = 2

        /**
         * Camera state: Waiting for the exposure state to be something other than precapture.
         */
        private val STATE_WAITING_NON_PRECAPTURE = 3

        /**
         * Camera state: Picture was taken.
         */
        private val STATE_PICTURE_TAKEN = 4
    }
}