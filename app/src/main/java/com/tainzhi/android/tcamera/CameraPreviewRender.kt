package com.tainzhi.android.tcamera

import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.opengl.GLES20
import android.opengl.Matrix
import android.util.Log
import android.util.Size
import com.tainzhi.android.tcamera.gl.textures.BlurPreviewTexture
import com.tainzhi.android.tcamera.gl.textures.GridLine
import com.tainzhi.android.tcamera.gl.textures.PreviewTexture
import com.tainzhi.android.tcamera.gl.textures.TextureManager
import com.tainzhi.android.tcamera.gl.textures.Vertex2F
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class CameraPreviewRender : GLSurfaceView.Renderer {
    private var windowWidth = 0
    private var windowHeight = 0
    private var textureWidth = 0
    private var textureHeight = 0
    private var previewRectF: RectF = RectF()
    private val modelMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val projectionMatrix = FloatArray(16)
    private val textureMatrix = FloatArray(16)
    private var surfaceTexture: SurfaceTexture? = null
    var surfaceTextureListener: CameraPreviewView.SurfaceTextureListener? = null
    private var isFrontCamera = false
    private var textureId = 0
    private val blurPreviewTexture = BlurPreviewTexture().apply {
        visibility = false
    }
    private val previewTexture = PreviewTexture()
    private val gridLine = GridLine()
    var textureManager: TextureManager = TextureManager().apply {
        addTextures(listOf(previewTexture, gridLine))
    }

    // invoked when EglContext created
    // not need to invoke surfaceTextureListener?.onSurfaceTextureCreated
    override fun onSurfaceCreated(gl: GL10, config: EGLConfig) {
        Log.d(TAG, "onSurfaceCreated: ${windowWidth}x${windowHeight}")
    }

    // first invoked after EglContext created
    // and get the width*height of FullScreen GlSurfaceView and transport to MainActivity
    override fun onSurfaceChanged(gl: GL10, width: Int, height: Int) {
        Log.d(TAG, "onSurfaceChanged: ${width}x${height}")
        windowWidth = width
        windowHeight = height
        surfaceTextureListener?.onSurfaceTextureChanged(surfaceTexture, width, height)
    }

    override fun onDrawFrame(gl: GL10) {
        surfaceTextureListener?.onSurfaceTextureUpdated(surfaceTexture)
        // set black background
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        // reset blend
        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_CONSTANT_ALPHA)
        surfaceTexture?.updateTexImage()
        textureManager.onDraw()
    }

    // camera output texture size, width > height
    fun setTextureSize(
        previewTextureSize: Size,
        isTrueAspectRatio: Boolean,
        previewRectF: RectF,
        isFrontCamera: Boolean
    ) {
        Log.d(
            TAG,
            "setTextureSize:w${previewTextureSize.width}*h${previewTextureSize.height}, trueAspectRatio:${isTrueAspectRatio}"
        )
        this.textureWidth = previewTextureSize.width
        this.textureHeight = previewTextureSize.height
        this.isFrontCamera = isFrontCamera
        this.previewRectF = previewRectF
        calculateMatrix()

        textureManager.apply {
            this.previewRectF = previewRectF
            setMatrix(modelMatrix, viewMatrix, projectionMatrix)
            previewTexture.setLayout(
                textureId,
                Vertex2F(
                    previewTextureSize.width.toFloat(),
                    previewTextureSize.height.toFloat()
                ),
                textureMatrix,
                if (isTrueAspectRatio) 1 else 0,
                previewRectF
            )
            blurPreviewTexture.setLayout(
                textureId,
                Vertex2F(
                    previewTextureSize.width.toFloat(),
                    previewTextureSize.height.toFloat()
                ),
                textureMatrix,
                if (isTrueAspectRatio) 1 else 0,
                previewRectF
            )
            blurPreviewTexture.visibility = false
            // must after setMatrix
            gridLine.setLayout(previewRectF)
            isReady = true
        }
    }

    fun changeFilterType() {
        previewTexture.changeFilterType()
    }

    fun changePreviewAspectRatio() {
        // todo: implement
        // blurPreviewTexture.visibility = true
        // blurPreviewTexture.toggleBindFrameBuffer(true)
        // previewTexture.onDraw()
        // blurPreviewTexture.toggleBindFrameBuffer(false)
        // blurPreviewTexture.onDraw()
    }

    fun copyFrame() {
        Log.d(TAG, "copyFrame: ")
    }

    fun releaseSurfaceTexture() {
        Log.d(TAG, "releaseSurfaceTexture: ")
        surfaceTexture?.release()
        surfaceTexture = null
    }

    fun createSurfaceTexture(width: Int, height: Int) {
        Log.d(TAG, "createSurfaceTexture: w${width}*h${height}")
        // texture 不能在UI thread创建，只能在其他线程创建，比如 GLThread
        // 在 onSurfaceCreated回调就在 GLThread 被执行
        surfaceTexture = SurfaceTexture(textureId).apply {
            setDefaultBufferSize(width, height)
            setOnFrameAvailableListener {
                surfaceTextureListener?.onSurfaceTextureAvailable(surfaceTexture, width, height)
            }
        }

        // set up alpha blending and an android background color
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        surfaceTextureListener?.onSurfaceTextureCreated(surfaceTexture, width, height)
    }

    private fun calculateMatrix() {
        // 坐标轴原点在 top left
        // positive x-axis points right
        // positive y-axis points bottom
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.setLookAtM(viewMatrix, 0, 0f, 0f, 3f, 0f, 0f, 0f, 0f, 1f, 0f)
        // 使得positive y-axis points to bottom
        Matrix.orthoM(
            projectionMatrix,
            0,
            0f,
            windowWidth.toFloat(),
            windowHeight.toFloat(),
            0f,
            1f,
            3f
        )

        Matrix.setIdentityM(textureMatrix, 0)
        // 4. move center back to original
        Matrix.translateM(textureMatrix, 0, 0.5f, 0.5f, 0f)
        // 3. rotate
        Matrix.rotateM(textureMatrix, 0, if (isFrontCamera) 270f else 90f, 0f, 0f, 1f)
        // 2. flip
        Matrix.scaleM(textureMatrix, 0, if (isFrontCamera) 1.0f else -1.0f, -1.0f, 1.0f)
        // 1. move to center (0, 0)
        Matrix.translateM(textureMatrix, 0, -0.5f, -0.5f, 0f)
    }

    companion object {
        private val TAG = CameraPreviewRender::class.java.simpleName
    }
}