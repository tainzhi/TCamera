package com.tainzhi.android.tcamera

import android.content.Context
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import android.util.Log
import android.util.Size
import com.tainzhi.android.tcamera.gl.EglUtil
import com.tainzhi.android.tcamera.gl.GlUtil
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLContext
import javax.microedition.khronos.egl.EGLDisplay
import javax.microedition.khronos.egl.EGLSurface

class CameraPreviewView : GLSurfaceView {
    private var egl10: EGL10? = null
    private var eglDisplay = EGL10.EGL_NO_DISPLAY
    private var eglSurface = EGL10.EGL_NO_SURFACE
    private var eglContext = EGL10.EGL_NO_CONTEXT

    private val cameraPreviewRender = CameraPreviewRender()
    constructor(context: Context): super(context)

    constructor(context: Context, attr: AttributeSet) : super(context, attr) {
        setEGLContextFactory(ContextFactory())
        setEGLWindowSurfaceFactory(WindowSurfaceFactory())
        setEGLConfigChooser(8, 8, 8, 8, 16, 8)
        setZOrderMediaOverlay(true)
        setEGLContextClientVersion(3)
        setRenderer(cameraPreviewRender)
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    var surfaceTextureListener: SurfaceTextureListener? = null
        set(value) {
            cameraPreviewRender.surfaceTextureListener = value
            field = value
        }

    fun setTextureSize(previewTextureSize: Size, isTrueAspectRatio: Boolean, previewRectF: RectF, isFrontCamera: Boolean) {
        queueEvent {
            cameraPreviewRender.createSurfaceTexture(previewTextureSize.width, previewTextureSize.height)
            cameraPreviewRender.setTextureSize(previewTextureSize, isTrueAspectRatio,previewRectF, isFrontCamera)
        }
    }

    fun changeFilterType() {
        cameraPreviewRender.changeFilterType()
    }

    fun changePreviewAspectRatio() {
        cameraPreviewRender.changePreviewAspectRatio()
    }

    fun releaseSurface() {
        queueEvent {
            cameraPreviewRender.releaseSurfaceTexture()
        }
    }

    fun copyFrame() {
        queueEvent {
            cameraPreviewRender.copyFrame()
        }
    }

    inner class WindowSurfaceFactory: EGLWindowSurfaceFactory {

        override fun createWindowSurface(
            egl: EGL10?,
            display: EGLDisplay?,
            config: EGLConfig?,
            nativeWindow: Any?
        ): EGLSurface {
            if (App.DEBUG) Log.d(TAG, "createWindowSurface: ")
            egl10 = egl
            eglDisplay = display
            eglSurface = createSurfaceImpl(egl!!, display!!, config!!, nativeWindow!!)
            EglUtil.makeCurrent(egl, display, eglSurface, eglContext)
            return eglSurface
        }

        override fun destroySurface(egl: EGL10?, display: EGLDisplay?, surface: EGLSurface?) {
            if (App.DEBUG) Log.d(TAG, "destroySurface: ")
            if (!EglUtil.destroySurface(egl, display, surface)) {
                Log.w(TAG, "failed to destroy OpenGL ES surface")
            }
            eglSurface = EGL10.EGL_NO_SURFACE
            eglDisplay = EGL10.EGL_NO_DISPLAY
            egl10 = null
        }

        private fun createSurfaceImpl(egl: EGL10, display: EGLDisplay, eglConfig: EGLConfig, nativeWindow: Any): EGLSurface {
            if (App.DEBUG) Log.d(TAG, "create  OpenGL ES surface")
            try {
                val surface = EglUtil.createWindowSurface(egl, display, eglConfig, nativeWindow)
                if (surface != EGL10.EGL_NO_SURFACE) return surface
                Log.w(TAG, "failed to create OpenGL ES surface")
            } catch (e: RuntimeException) {
                Log.w(TAG, "failed to create OpenGL ES surface, ${e.message}")
            }
            return EGL10.EGL_NO_SURFACE
        }
    }

    inner class ContextFactory: EGLContextFactory {

        override fun createContext(egl: EGL10?, display: EGLDisplay?, eglConfig: EGLConfig?): EGLContext {
            if (App.DEBUG) Log.d(TAG, "createContext: ")
            eglContext = createContextImpl(egl!!, display!!, eglConfig!!)
            cameraPreviewRender.textureManager.load()
            return eglContext
        }

        override fun destroyContext(egl: EGL10?, display: EGLDisplay?, context: EGLContext?) {
            if (App.DEBUG) Log.d(TAG, "destroyContext: ")
            cameraPreviewRender.textureManager.unload()
            if (!EglUtil.destroyContext(egl!!, display!!, context!!)) {
                Log.w(TAG, "failed to destroy OpenGL ES context")
            }
            eglContext = EGL10.EGL_NO_CONTEXT
        }

        private fun createContextImpl(egl: EGL10, display: EGLDisplay, eglConfig: EGLConfig): EGLContext {
            val versions = intArrayOf(EglUtil.GLES3, EglUtil.GLES2)
            versions.forEach {
                if (App.DEBUG) {
                    Log.d(TAG, "create OpenGL ES context with version:${it}")
                }
                try {
                    val context = EglUtil.createContext(egl, display, eglConfig, it)
                    if (context != EGL10.EGL_NO_CONTEXT) {
                        GlUtil.glVersion = it
                        return context
                    }
                    Log.w(TAG, "failed to create OpenGL ES context with version:$it")
                } catch (e: RuntimeException) {
                    Log.w(TAG, "failed to create OpenGL ES context with version:$it, ${e.message}")
                }
            }
            return EGL10.EGL_NO_CONTEXT
        }
    }

    interface SurfaceTextureListener {
        fun onSurfaceTextureAvailable(surface: SurfaceTexture?, width: Int, height: Int)
        fun onSurfaceTextureCreated(surface: SurfaceTexture?, width: Int, height: Int)
        fun onSurfaceTextureDestroyed(surface: SurfaceTexture?): Boolean
        fun onSurfaceTextureUpdated(surface: SurfaceTexture?)

        fun onSurfaceTextureChanged(surfaceTexture: SurfaceTexture?, width: Int, height: Int)
    }

    companion object {
        private val TAG = CameraPreviewView::class.java.simpleName
    }
}