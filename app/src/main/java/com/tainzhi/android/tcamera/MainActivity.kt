package com.tainzhi.android.tcamera

import android.Manifest
import android.content.Intent
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
import android.media.MediaRecorder
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.*
import android.os.Build.VERSION_CODES
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import android.util.SparseIntArray
import android.view.*
import android.widget.ImageView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.os.ExecutorCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.tainzhi.android.tcamera.CameraInfoCache.Companion.CAPTURE_HDR_FRAME_SIZE
import com.tainzhi.android.tcamera.CameraInfoCache.Companion.chooseOptimalSize
import com.tainzhi.android.tcamera.databinding.ActivityMainBinding
import com.tainzhi.android.tcamera.ui.CameraPreviewView
import com.tainzhi.android.tcamera.ui.CircleImageView
import com.tainzhi.android.tcamera.ui.ControlBar
import com.tainzhi.android.tcamera.ui.ErrorDialog
import com.tainzhi.android.tcamera.ui.FilterBar
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
    private lateinit var ivTakePicture: ImageView
    private lateinit var ivRecord: ImageView
    private lateinit var ivSwitchCamera: ImageView
    private lateinit var controlBar: ControlBar

    private val unGrantedPermissionList: MutableList<String> = ArrayList()

    // to play click sound when take picture
    private val mediaActionSound = MediaActionSound()

    private lateinit var rotationChangeMonitor: RotationChangeMonitor
    private var thumbnailOrientation = 0

    private var isEnableZsl = false

    private val cameraThread = HandlerThread("CameraThread").apply { start() }
    private val cameraHandler = Handler(cameraThread.looper)
    private var cameraExecutor = ExecutorCompat.create(cameraHandler)

    private var imageReaderThread = HandlerThread("ImageReaderThread").apply { start() }
    private val imageReaderHandler = Handler(imageReaderThread.looper) { msg ->
        val pictureUri: Uri = msg.obj as Uri
        capturedImageUri = pictureUri
        Kpi.start(Kpi.TYPE.IMAGE_TO_THUMBNAIL)
        mainExecutor.execute {
            updateThumbnail(pictureUri, true)
        }
        false
    }

    private lateinit var lastTotalCaptureResult: TotalCaptureResult
    private var zslImageWriter: ImageWriter? = null

    // todo: use coroutine
    // a [Semaphore] to prevent the app from exiting before closing the camera
    private val cameraOpenCloseLock = Semaphore(1)

    private lateinit var capturedImageUri: Uri
    private var cameraInfo: CameraInfoCache? = null
    private var previewStreamingSession: CameraCaptureSession? = null
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
    private lateinit var jpgImageReader: ImageReader
    private lateinit var yuvImageReader: ImageReader
    private var yuvImage: Image? = null

    private var captureType = CaptureType.JPEG

    // todo: remove it
    private val capturedImageList = arrayListOf<Image>()

    private var isRecordingVideo = false
    private var mediaRecorder: MediaRecorder? = null

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

    private val hdrNeedImageSize = CAPTURE_HDR_FRAME_SIZE

    // time in nanoseconds
    private val hdrImageExposureTimeList = arrayListOf<Long>()

    private var currentCameraMode: CameraMode = CameraMode.PHOTO

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
                setCaptureSession()
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
                        Log.d(TAG, "setCaptureSession: waiting to image reader set")
                    }
                    previewSizeCondition.await()
                }
            } finally {
                previewSizeLock.unlock()
            }
            setSurfaces()
            setCaptureSession()
        }

        override fun onDisconnected(p0: CameraDevice) {
            Log.i(TAG, "onCameraDisconnected: ${p0}")
            p0.close()
            cameraOpenCloseLock.release()
            closeSurfaces()
            closeCaptureSession()
            this@MainActivity.cameraDevice = null
        }

        override fun onError(p0: CameraDevice, p1: Int) {
            Log.e(TAG, "onCameraError: ${p0.id}, $p1")
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
                Log.e(TAG, "capturePicture: afState is ${afState}")
                val aeState = result.get(CaptureResult.CONTROL_AE_STATE)
                if (aeState == null || aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
                    cameraState = STATE_PICTURE_TAKEN
                    captureStillPicture()
                } else {
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
        controlBar = ControlBar(this, _binding) {
            Log.d(TAG, "onPreviewAspectRatioChange")
            closeSurfaces()
            closeCaptureSession()
            isNeedRecreateCaptureSession = true
            cameraPreviewView.changePreviewAspectRatio()
        }
        FilterBar(this, _binding) {
            cameraPreviewView.changeFilterType()
            Log.d(TAG, "onFilterTypeSelected: ${it}")
        }

        cameraPreviewView = findViewById(R.id.previewView)
        ivThumbnail = findViewById<CircleImageView>(R.id.iv_thumbnail).apply {
            setOnClickListener {
                viewPicture()
            }
        }
        SettingsManager.getInstance().getLastCapturedMediaUri()?.let {
            updateThumbnail(it)
        }
        ivTakePicture = findViewById<ImageView>(R.id.picture).apply {
            setOnClickListener {
                // Most device front lenses/camera have a fixed focal length
                if (useCameraFront) captureStillPicture() else lockFocus()
            }
        }
        ivRecord = findViewById<ImageView>(R.id.iv_record).apply {
            setOnClickListener {
                if (isRecordingVideo) stopVideo() else startVideo()
            }
        }
        ivSwitchCamera = findViewById<ImageView>(R.id.iv_switch_camera).apply {
            setOnClickListener {
                Log.d(TAG, "click switch camera icon")
                isNeedReopenCamera = true
                useCameraFront = !useCameraFront
                closeSurfaces()
                closeCaptureSession()
                closeCamera()
            }
        }

        _binding.cameraModePicker.apply {
            data = cameraModeNames.toList()
            setOnSelectedListener(object : OnSelectedListener {
                override fun onSelected(scrollPickerView: ScrollPickerView<*>?, position: Int) {
                    val mode = CameraMode.fromInt(position)
                    when (mode) {
                        CameraMode.PHOTO -> {
                            ivRecord.visibility = View.INVISIBLE
                            ivTakePicture.visibility = View.VISIBLE
                        }

                        CameraMode.VIDEO -> {
                            ivRecord.visibility = View.VISIBLE
                            ivTakePicture.visibility = View.INVISIBLE
                        }
                    }
                    currentCameraMode = mode
                    controlBar.updateByCameraMode(mode)
                }

            })
        }

        mediaActionSound.load(MediaActionSound.SHUTTER_CLICK)
        rotationChangeMonitor = RotationChangeMonitor(this).apply {
            rotationChangeListener = object : RotationChangeListener {
                override fun onRotateChange(oldOrientation: Int, newOrientation: Int) {
                    Log.d(TAG, "orientation change from ${oldOrientation} -> ${newOrientation}")
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
    @RequiresApi(VERSION_CODES.R)
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
        closeCaptureSession()
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
        if (Build.VERSION.SDK_INT >= VERSION_CODES.M) {
            for (permission in PERMISSIONS_EXCLUDE_STORAGE) {
                if (ContextCompat.checkSelfPermission(this, permission)
                    != PackageManager.PERMISSION_GRANTED
                ) {
                    unGrantedPermissionList.add(permission)
                }
            }
            if (Build.VERSION.SDK_INT < VERSION_CODES.R) {
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
        if (Build.VERSION.SDK_INT >= VERSION_CODES.R) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val controller = WindowCompat.getInsetsController(window, _binding.root)
            controller.isAppearanceLightNavigationBars = true
            controller.hide(WindowInsetsCompat.Type.statusBars())
            ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { view, windowInsets ->
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
            window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
            window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        }

    }

    private fun setSurfaces() {
        Log.i(TAG, "setSurfaces: ")
        val previewAspectRatio = SettingsManager.getInstance().getPreviewAspectRatio()
        val ratioValue: Float = when (previewAspectRatio) {
            SettingsManager.PreviewAspectRatio.RATIO_1x1 -> 1f
            SettingsManager.PreviewAspectRatio.RATIO_4x3 -> 4 / 3f
            SettingsManager.PreviewAspectRatio.RATIO_16x9 -> 16 / 9f
            // activity is portrait, so height < width
            // and sensor is also height < width
            SettingsManager.PreviewAspectRatio.RATIO_FULL -> previewSize.height / previewSize.width.toFloat()
        }
        try {
            isEnableZsl = cameraInfo!!.isSupportReproc() &&
                    SettingsManager.getInstance()
                        .getBoolean(
                            getString(R.string.settings_key_photo_zsl),
                            SettingsManager.PHOTO_ZSL_DEFAULT_VALUE
                        )
            val isHdr =
                SettingsManager.getInstance().getBoolean(SettingsManager.KEY_HDR_ENABLE, false)
            Log.d(
                TAG,
                "setSurfaces enableZsl:$isEnableZsl, hdr:${isHdr}"
            )
            if (currentCameraMode == CameraMode.PHOTO) {
                // HDR on, then disable zsl
                if (isHdr) {
                    val (chosenYuvSize, isTrueAspectRatioJpgSize) = chooseOptimalSize(
                        cameraInfo!!.getOutputYuvSizes(),
                        previewSize,
                        ratioValue,
                        false
                    )
                    Log.d(
                        TAG,
                        "setSurfaces yuv size:${chosenYuvSize}, match ${previewAspectRatio}:${isTrueAspectRatioJpgSize}"
                    )
                    yuvImageReader = ImageReader.newInstance(
                        chosenYuvSize.width, chosenYuvSize.height,
                        ImageFormat.YUV_420_888,
                        CAPTURE_HDR_FRAME_SIZE
                    )
                    yuvImageReader.setOnImageAvailableListener({ reader ->
                        Log.d(TAG, "hdr: yuv image avaiable")
                    }, cameraHandler)
                } else if (isEnableZsl && !isHdr) {
                    yuvImageReader = ImageReader.newInstance(
                        cameraInfo!!.largestYuvSize.width, cameraInfo!!.largestYuvSize.height,
                        ImageFormat.YUV_420_888,
                        YUV_IMAGE_READER_SIZE
                    )
                    yuvImageReader.setOnImageAvailableListener({ reader ->
                        yuvImage?.close()
                        yuvImage = reader.acquireLatestImage()
                    }, cameraHandler)
                }
                val (chosenJpgSize, isTrueAspectRatioJpgSize) = chooseOptimalSize(
                    cameraInfo!!.getOutputJpgSizes(),
                    previewSize,
                    ratioValue,
                    false
                )
                Log.d(
                    TAG,
                    "setSurfaces jpg size:${chosenJpgSize}, match ${previewAspectRatio}:${isTrueAspectRatioJpgSize}"
                )
                jpgImageReader = ImageReader.newInstance(
                    chosenJpgSize.width, chosenJpgSize.height,
                    ImageFormat.JPEG, 1
                )
                jpgImageReader.setOnImageAvailableListener({ reader ->
                    Log.d(TAG, "jpg: image available ")
                    val image = reader.acquireLatestImage()
                    handleOnImageAvailable(image)
                    image.close()
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
            flashSupported = cameraInfo!!.isflashSupported

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
        jpgImageReader.close()
        if (isEnableZsl) {
            yuvImageReader.close()
            zslImageWriter?.close()
            zslImageWriter = null
        }
        isPreviewSizeSet = false
    }

    private fun setCaptureSession() {
        Log.i(TAG, "setCaptureSession: ")
        try {
            Log.d(TAG, "setCaptureSession: previewSize:${previewSize} is set")
            val captureSessionStateCallback = object : CameraCaptureSession.StateCallback() {
                override fun onClosed(session: CameraCaptureSession) {
                    super.onClosed(session)
                    Log.d(TAG, "onSessionClosed")
                    if (isNeedRecreateCaptureSession) {
                        Log.d(TAG, "onSessionClosed: need to recreate CaptureSession")
                        isNeedRecreateCaptureSession = false
                        setSurfaces()
                        setCaptureSession()
                    }
                }

                override fun onSurfacePrepared(session: CameraCaptureSession, surface: Surface) {
                    super.onSurfacePrepared(session, surface)
                    Log.d(TAG, "onSessionSurfacePrepared")
                }

                override fun onConfigureFailed(p0: CameraCaptureSession) {
                    Log.e(TAG, "onConfigureFailed: ")
                    toast("Failed to configure CaptureSession")
                }

                override fun onConfigured(session: CameraCaptureSession) {
                    previewStreamingSession = session
                    setPreviewRequest()
                }

                override fun onReady(session: CameraCaptureSession) {
                    // When the session is ready, we start displaying the preview.
                    super.onReady(session)
                    Log.d(TAG, "onSessionReady")
                }
            }
            if (Build.VERSION.SDK_INT >= VERSION_CODES.Q) {
                val outputConfigurations = mutableListOf<OutputConfiguration>(
                    OutputConfiguration(previewSurface),
                    OutputConfiguration(jpgImageReader.surface)
                )
                val isHdr =
                    SettingsManager.getInstance().getBoolean(SettingsManager.KEY_HDR_ENABLE, false)
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
                if (isEnableZsl) {

                } else {
                    cameraDevice?.createCaptureSession(
                        arrayListOf(previewSurface, jpgImageReader.surface),
                        captureSessionStateCallback, cameraHandler
                    )
                }

            }
        } catch (e: CameraAccessException) {
            Log.e(TAG, e.toString())
        }
    }

    private fun closeCaptureSession() {
        // closeCaptureSession -> session.onClosed() -> closeCamera()
        Log.i(TAG, "closeCaptureSession: ")
        previewStreamingSession!!.stopRepeating()
        previewStreamingSession!!.close()
        previewStreamingSession = null
    }

    private fun setPreviewRequest() {
        if (cameraDevice == null) return
        val isHdr = SettingsManager.getInstance().getBoolean(SettingsManager.KEY_HDR_ENABLE, false)
        if (isEnableZsl && !isHdr) {
            zslImageWriter =
                ImageWriter.newInstance(
                    previewStreamingSession!!.inputSurface!!,
                    ZSL_IMAGE_WRITER_SIZE
                )
            zslImageWriter?.setOnImageReleasedListener({ _ ->
                {
                    Log.d(TAG, "ZslImageWriter onImageReleased()")
                }
            }, cameraHandler)
            Log.d(TAG, "onSessionConfigured: create ImageWriter")
        }
        try {
            previewRequestBuilder =
                cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            previewRequestBuilder.addTarget(previewSurface)
            if (isEnableZsl && !isHdr) {
                previewRequestBuilder.addTarget(yuvImageReader.surface)
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
            if (Build.VERSION.SDK_INT >= VERSION_CODES.O) {
                val controlZsl: Boolean? =
                    previewRequestBuilder.get(CaptureRequest.CONTROL_ENABLE_ZSL)
                Log.d(TAG, "CaptureRequest: controlZsl=${controlZsl}")
            }
            previewRequest = previewRequestBuilder.build()
            previewStreamingSession?.setRepeatingRequest(
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
            previewStreamingSession?.capture(
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
        isEnableZsl = SettingsManager.getInstance()
            .getBoolean(
                getString(R.string.settings_key_photo_zsl),
                SettingsManager.PHOTO_ZSL_DEFAULT_VALUE
            )
        val isHdr =
            SettingsManager.getInstance().getBoolean(SettingsManager.KEY_HDR_ENABLE, false)
        Log.d(TAG, "captureStillPicture: enableZsl:${isEnableZsl}")
        Kpi.start(Kpi.TYPE.SHOT_TO_SHOT)
        // todo: 判断是否有已经在执行的任务，队列执行
        captureType = if (SettingsManager.getInstance()
                .getBoolean(SettingsManager.KEY_HDR_ENABLE, false)
        ) CaptureType.HDR else CaptureType.JPEG
        capturedImageList.clear()
        hdrImageExposureTimeList.clear()
        try {
            if (isEnableZsl && !isHdr && yuvImage == null) {
                Log.e(TAG, "captureStillPicture: no yuv image available")
                return
            }
            val rotation = windowManager.defaultDisplay.rotation

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
                            SettingsManager.getInstance()
                                .getBoolean(SettingsManager.KEY_HDR_ENABLE, false)
                        }"
                    )
                    cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
                }
            captureBuilder.apply {
                addTarget(jpgImageReader.surface)
                if (captureType == CaptureType.HDR) {
                    addTarget(yuvImageReader.surface)
                }
                if (isEnableZsl && captureType != CaptureType.HDR) {
                    set(CaptureRequest.NOISE_REDUCTION_MODE, cameraInfo!!.reprocessingNoiseMode)
                    set(CaptureRequest.EDGE_MODE, cameraInfo!!.reprocessingEdgeMode)
                }
                if (captureType != CaptureType.HDR) {
                    set(CaptureRequest.JPEG_QUALITY, 95)
                    // https://developer.android.com/training/camera2/camera-preview#orientation_calculation
                    // rotation = (sensorOrientationDegrees - deviceOrientationDegrees * sign + 360) % 360
                    // sign 1 for front-facing cameras, -1 for back-facing cameras
                    set(
                        CaptureRequest.JPEG_ORIENTATION,
                        (sensorOrientation!! - OREIENTATIONS.get(rotation) * (if (useCameraFront) 1 else -1) + 360) % 360
                    )
                    set(
                        CaptureRequest.CONTROL_AF_MODE,
                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                    )
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
            }

            val captureCallback = object : CameraCaptureSession.CaptureCallback() {

                override fun onCaptureCompleted(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    result: TotalCaptureResult
                ) {
                    super.onCaptureCompleted(session, request, result)
                    Log.d(TAG, "capture onCaptureCompleted, request.tag:" + request.tag)
                }
            }
            if (SettingsManager.getInstance().getBoolean(SettingsManager.KEY_HDR_ENABLE, false)) {
                Log.d(TAG, "captureStillPicture: HDR enable")

                val baseExposureTime = 1000000000L / 30
                val halfImageSize = cameraInfo!!.exposureBracketingImages / 2
                val scale = Math.pow(2.0, cameraInfo!!.exposureBracketingStops / halfImageSize)
                val requests = ArrayList<CaptureRequest>()
                // darker images
                for (i in 0 until halfImageSize) {
                    var exposureTime = baseExposureTime
                    if (cameraInfo!!.supportExposureTime) {
                        var currentScale = scale
                        for (j in i until halfImageSize - 1)
                            currentScale *= scale
                        exposureTime = (exposureTime / currentScale).toLong()
                        if (exposureTime < cameraInfo!!.minExposureTime) {
                            exposureTime = cameraInfo!!.minExposureTime.toLong()
                        }
                        hdrImageExposureTimeList.add(exposureTime)
                        captureBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, exposureTime)
                        captureBuilder.setTag(RequestTagObject(RequestTagType.CAPTURE_BURST_IN_PROCESS))
                        requests.add(captureBuilder.build())
                    }
                }
                // base image
                hdrImageExposureTimeList.add(baseExposureTime)
                captureBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, baseExposureTime)
                captureBuilder.setTag(RequestTagObject(RequestTagType.CAPTURE_BURST_IN_PROCESS))
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
                        hdrImageExposureTimeList.add(exposureTime)
                        captureBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, exposureTime)
                        if (i == halfImageSize - 1) {
                            captureBuilder.setTag(RequestTagObject(RequestTagType.CAPTURE))
                        } else {
                            captureBuilder.setTag(RequestTagObject(RequestTagType.CAPTURE_BURST_IN_PROCESS))
                        }
                        requests.add(captureBuilder.build())
                    }
                }
                Log.d(TAG, "captureStillPicture: captureBurst, requests.size: ${requests.size}")
                previewStreamingSession?.apply {
                    captureBurst(requests, captureCallback, cameraHandler)
                }
            } else {
                captureBuilder.setTag(RequestTagObject(RequestTagType.CAPTURE))
                previewStreamingSession?.apply {
                    capture(captureBuilder.build(), captureCallback, cameraHandler)
                }
            }
            mediaActionSound.play(MediaActionSound.SHUTTER_CLICK)
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
            previewStreamingSession?.capture(
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
            previewStreamingSession?.capture(
                previewRequestBuilder.build(), captureCallback,
                cameraHandler
            )
            // After this, the camera will go back to the normal state of preview.
            cameraState = STATE_PREVIEW
            previewStreamingSession?.setRepeatingRequest(
                previewRequest, captureCallback,
                cameraHandler
            )
        } catch (e: CameraAccessException) {
            Log.e(TAG, e.toString())
        }
    }

    private fun viewPicture() {
        if (this::capturedImageUri.isInitialized) {
            // if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            //     val intent = Intent().apply {
            //         action = Intent.ACTION_VIEW
            //         addCategory(Intent.CATEGORY_APP_GALLERY)
            //         setDataAndType(capturedImageUri, "image/*")
            //         flags = Intent.FLAG_ACTIVITY_NEW_TASK
            //     }
            //     startActivity(intent)
            // }
            startActivity(Intent(Intent.ACTION_VIEW, capturedImageUri))
        } else {
            toast("请先拍照")
        }
    }

    private fun updateThumbnail(capturedImageUri: Uri, isNew: Boolean = false) {
        val thumbnail = if (Build.VERSION.SDK_INT < VERSION_CODES.Q) {
            val temp = MediaStore.Images.Media.getBitmap(contentResolver, capturedImageUri)
            ThumbnailUtils.extractThumbnail(temp, 360, 360)
        } else {
            contentResolver.loadThumbnail(capturedImageUri, Size(360, 360), null)
        }
        ivThumbnail.apply {
            post {
                setImageBitmap(thumbnail)
            }
        }
        if (isNew) {
            Kpi.end(Kpi.TYPE.IMAGE_TO_THUMBNAIL)
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
            SettingsManager.getInstance().saveLastCaptureMediaUri(capturedImageUri)
        }
    }

    private fun startVideo() {
        if (cameraDevice == null) return
        try {
            closeSurfaces()
            previewStreamingSession!!.stopRepeating()
            setUpMediaRecorder()
            previewRequestBuilder =
                cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
                    .apply {
                        addTarget(previewSurface)
                        addTarget(mediaRecorder!!.surface)
                        set(
                            CaptureRequest.CONTROL_AF_MODE,
                            CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO
                        )
                    }
            previewStreamingSession!!.setRepeatingRequest(
                previewRequestBuilder.build(),
                object : CameraCaptureSession.CaptureCallback() {
                    override fun onCaptureStarted(
                        session: CameraCaptureSession,
                        request: CaptureRequest,
                        timestamp: Long,
                        frameNumber: Long
                    ) {
                        super.onCaptureStarted(session, request, timestamp, frameNumber)
                        Log.d(TAG, "startVideo onCaptureStarted: ")

                        runOnUiThread {
                            ivRecord.setImageResource(R.drawable.btn_record_stop)
                            isRecordingVideo = true
                            mediaRecorder?.start()
                        }
                    }

                    override fun onCaptureCompleted(
                        session: CameraCaptureSession,
                        request: CaptureRequest,
                        result: TotalCaptureResult
                    ) {
                        super.onCaptureCompleted(session, request, result)
                        Log.d(TAG, "startVideo onCaptureCompleted: ")
                    }
                },
                cameraHandler
            )
        } catch (e: CameraAccessException) {
            Log.e(TAG, "startVideo CameraAccessException", e)
        } catch (e: IOException) {
            Log.e(TAG, "startVideo IOException", e)
        }
    }

    @Throws(IOException::class)
    private fun setUpMediaRecorder() {
        mediaRecorder = MediaRecorder()
        val (videoSize, _) = chooseOptimalSize(
            cameraInfo!!.getOutputPreviewSurfaceSizes(),
            previewSize,
            16 / 9f, /*todo: 支持更多分辨率的视频录制; 只支持 16/9， 4/3 */
            true
        )
        if (App.DEBUG) {
            Log.d(TAG, "setUpMediaRecorder: videoSize:${videoSize}")
        }
//        previewSurfaceTexture.setDefaultBufferSize(videoSize.width, videoSize.height)
//        previewSurface = Surface(previewSurfaceTexture)

        val videoUri = MediaSaver.generateMediaUri(this, CaptureType.VIDEO)
        val rotation = windowManager.defaultDisplay.rotation
        when (sensorOrientation) {
            SENSOR_ORIENTATION_DEFAULT_DEGREES ->
                mediaRecorder?.setOrientationHint(OREIENTATIONS.get(rotation))

            SENSOR_ORIENTATION_INVERSE_DEGREES ->
                mediaRecorder?.setOrientationHint(INVERSE_ORIENTATIONS.get(rotation))
        }
        mediaRecorder!!.apply {
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
            prepare()
        }
    }

    private fun stopVideo() {
        isRecordingVideo = false
        ivRecord.setImageResource(R.drawable.btn_record_start)
        mediaRecorder?.apply {
            stop()
            reset()
        }
        setCaptureSession()
    }

    private fun handleRotation(rotateAngle: Int) {
//        Log.d(TAG, "handleRotation: thumbnailOrientation:$thumbnailOrientation")
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

    /**
     * 不能在 onCpatureCompleted 中处理，可能captureBurst没有返回所有的request result之前返回了 captureCompleted
     */
    private fun handleOnImageAvailable(image: Image) {
        Kpi.end(Kpi.TYPE.SHOT_TO_SHOT)
        capturedImageList.add(image)
        if (SettingsManager.getInstance().getBoolean(SettingsManager.KEY_HDR_ENABLE, false)
        ) {
            captureType = CaptureType.HDR
            if (capturedImageList.size < hdrNeedImageSize) {
                Log.d(
                    TAG,
                    "capture hdr, need ${hdrNeedImageSize} images, but collected ${capturedImageList.size}"
                )
                return
            } else {
                Log.d(TAG, "capture hdr, collected ${hdrNeedImageSize} images")
            }
        } else {
        }
        unlockFocus()
        imageReaderHandler.post(
            MediaSaver(
                this@MainActivity,
                captureType,
                capturedImageList,
                hdrImageExposureTimeList,
                imageReaderHandler
            )
        )
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

        private const val SENSOR_ORIENTATION_DEFAULT_DEGREES = 90
        private const val SENSOR_ORIENTATION_INVERSE_DEGREES = 270

        private val INVERSE_ORIENTATIONS = SparseIntArray().apply {
            append(Surface.ROTATION_0, 270)
            append(Surface.ROTATION_90, 180)
            append(Surface.ROTATION_180, 90)
            append(Surface.ROTATION_270, 0)
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