package com.tainzhi.android.tcamera.gl

import android.opengl.EGL14
import android.util.Log
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGL11
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLContext
import javax.microedition.khronos.egl.EGLDisplay
import javax.microedition.khronos.egl.EGLSurface

object EglUtil {
    val TAG = EglUtil::class.simpleName

    const val GLES2 = 2
    const val GLES3 = 3

    fun createContext(
            egl: EGL10,
            display: EGLDisplay,
            config: EGLConfig,
            version: Int
    ): EGLContext {
        return createContext(egl, display, config, EGL10.EGL_NO_CONTEXT, version)
    }

    fun createContext(
            egl: EGL10,
            display: EGLDisplay,
            config: EGLConfig,
            shareContext: EGLContext,
            version: Int
    ): EGLContext {
        val attributeList = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, version, EGL10.EGL_NONE)
        val context = egl.eglCreateContext(display, config, shareContext, attributeList)
        checkEglError(egl, "eglCreateContext")
        return context ?: EGL10.EGL_NO_CONTEXT
    }

    fun createWindowSurface(egl: EGL10, display: EGLDisplay, config: EGLConfig,
                            nativeWindow: Any): EGLSurface {
        val attributeList =  intArrayOf(EGL10.EGL_NONE)

        var windowSurface = egl.eglCreateWindowSurface(display, config, nativeWindow,
                attributeList)
        val error = egl.eglGetError()
        if (error != EGL10.EGL_SUCCESS && attributeList.size > 1) {
            Log.e(TAG,"eglCreateWindowSurface: ${getEglErrorName(error)}")
            windowSurface = egl.eglCreateWindowSurface(display, config, nativeWindow,
                intArrayOf(EGL10.EGL_NONE))
        }
        checkEglError(egl, "eglCreateWindowSurface")
        return windowSurface ?: EGL10.EGL_NO_SURFACE
    }

    fun destroyContext(egl: EGL10, display: EGLDisplay, context: EGLContext): Boolean {
        val result = egl.eglDestroyContext(display, context)
        checkEglError(egl, "eglDestroyContext")
        return result
    }

    fun destroySurface(egl: EGL10?, display: EGLDisplay?, surface: EGLSurface?): Boolean {
        val result = egl?.eglDestroySurface(display, surface)
        if (egl!= null) {
            checkEglError(egl, "eglDestroySurface")
            return result ?: false
        } else {
            return false
        }
    }

    fun makeCurrent(egl: EGL10, display: EGLDisplay, surface: EGLSurface, context: EGLContext) {
        egl.eglMakeCurrent(display, surface, surface, context)
        checkEglError(egl, "eglMakeCurrent")
    }

    fun swapBuffers(egl: EGL10, display: EGLDisplay, surface: EGLSurface) {
        egl.eglSwapBuffers(display, surface)
        checkEglError(egl, "eglSwapBuffers")
    }

    private fun checkEglError(egl: EGL10, method: String) {
        val error = egl.eglGetError()
        if (EGL10.EGL_SUCCESS != error) {
            throw RuntimeException("$method: ${getEglErrorName(error)}")
        }
    }

    private fun getEglErrorName(error: Int): String {
        return when (error) {
            EGL10.EGL_SUCCESS -> "EGL_SUCCESS"
            EGL10.EGL_NOT_INITIALIZED -> "EGL_NOT_INITIALIZED"
            EGL10.EGL_BAD_ACCESS -> "EGL_BAD_ACCESS"
            EGL10.EGL_BAD_ALLOC -> "EGL_BAD_ALLOC"
            EGL10.EGL_BAD_ATTRIBUTE -> "EGL_BAD_ATTRIBUTE"
            EGL10.EGL_BAD_CONTEXT -> "EGL_BAD_CONTEXT"
            EGL10.EGL_BAD_CONFIG -> "EGL_BAD_CONFIG"
            EGL10.EGL_BAD_CURRENT_SURFACE -> "EGL_BAD_CURRENT_SURFACE"
            EGL10.EGL_BAD_DISPLAY -> "EGL_BAD_DISPLAY"
            EGL10.EGL_BAD_SURFACE -> "EGL_BAD_SURFACE"
            EGL10.EGL_BAD_MATCH -> "EGL_BAD_MATCH"
            EGL10.EGL_BAD_PARAMETER -> "EGL_BAD_PARAMETER"
            EGL10.EGL_BAD_NATIVE_PIXMAP -> "EGL_BAD_NATIVE_PIXMAP"
            EGL10.EGL_BAD_NATIVE_WINDOW -> "EGL_BAD_NATIVE_WINDOW"
            EGL11.EGL_CONTEXT_LOST -> "EGL_CONTEXT_LOST"
            else -> String.format("eglGetError(%x)", error)
        }
    }
}
