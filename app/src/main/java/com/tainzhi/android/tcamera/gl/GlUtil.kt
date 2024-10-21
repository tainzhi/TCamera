package com.tainzhi.android.tcamera.gl

import android.graphics.BitmapFactory
import android.opengl.GLES20
import android.opengl.GLES30
import android.opengl.GLUtils
import android.util.Log
import com.tainzhi.android.tcamera.App
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader

object GlUtil {

    private val TAG = GlUtil::class.java.simpleName
    const val COORDS_PER_VERTEX: Int = 3
    const val VERTEX_STRIDE: Int = COORDS_PER_VERTEX * 4

    var glVersion = 3

    fun loadTextureFromRes(sourceId: Int): Int {
        val options = BitmapFactory.Options().apply {
            inScaled = false
        }
        val bitmap = BitmapFactory.decodeResource(App.getInstance().resources, sourceId, options)
        if (bitmap == null) {
            Log.e(TAG, "loadTextureFromRes: bitmap is null")
        }
        val textureId = generateTexture(GLES30.GL_TEXTURE_2D)
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bitmap, 0)
        bitmap.recycle()
        return textureId
    }

    fun getShaderSource(sourceId: Int): String {
        val sb = StringBuilder()
        val inputStream = App.getInstance().resources.openRawResource(sourceId)
        val bufferReader = BufferedReader(InputStreamReader(inputStream))
        try {
            var read = bufferReader.readLine()
            while (read != null) {
                sb.append(read).append("\n")
                read = bufferReader.readLine()
            }
            sb.deleteCharAt(sb.length - 1)
        } catch (ioe: IOException) {
            Log.e(TAG, "error reading sharer: ${ioe}")
        }
        return sb.toString()
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        val shader: Int = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, shaderCode)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] != GLES20.GL_TRUE) {
            val logLength = IntArray(1)
            GLES20.glGetShaderiv(shader, GLES20.GL_INFO_LOG_LENGTH, logLength, 0)
            // 输出编译错误日志
            Log.e(TAG, "loadShader($type) failed: ${GLES20.glGetShaderInfoLog(shader)}")
            // 可以进行一些清理工作，如删除着色器、程序等
        }
        return shader
    }

    fun createProgram(vertexSource: String, fragmentSource: String): Int {
        val vertex: Int = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource)
        if (vertex == 0) {
            Log.e(TAG, "load shader vertex failed")
            return 0
        }
        val fragment: Int = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        if (fragment == 0) {
            Log.e(TAG, "load shader fragment failed")
            return 0
        }

        var program: Int = GLES20.glCreateProgram()
        if (program != 0) {
            GLES20.glAttachShader(program, vertex)
            GLES20.glAttachShader(program, fragment)
            GLES20.glLinkProgram(program)
            val linkedStatus: IntArray = IntArray(1)
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkedStatus, 0)
            if (linkedStatus[0] != GLES20.GL_TRUE) {
                Log.e(TAG, "Could not linked program: " + GLES20.glGetProgramInfoLog(program))
                GLES20.glDeleteProgram(program)
                program = 0
            }
        }
        return program
    }


    private fun getGlErrorName(error: Int): String {
        when (error) {
            GLES20.GL_NO_ERROR -> return "GL_NO_ERROR"
            GLES20.GL_INVALID_ENUM -> return "GL_INVALID_ENUM"
            GLES20.GL_INVALID_VALUE -> return "GL_INVALID_VALUE"
            GLES20.GL_INVALID_OPERATION -> return "GL_INVALID_OPERATION"
            GLES20.GL_INVALID_FRAMEBUFFER_OPERATION -> return "GL_INVALID_FRAMEBUFFER_OPERATION"
            GLES20.GL_OUT_OF_MEMORY -> return "GL_OUT_OF_MEMORY"
            else ->
                return String.format("glGetError(%x)", error)
        }
    }

    fun useProgram(program: Int) {
        GLES20.glUseProgram(program)
        checkGlError("glUseProgram")
    }
    fun checkGlError(op: String) {
        val error = GLES20.glGetError()
        if (GLES20.GL_NO_ERROR != error) {
            val message = op + ": " + getGlErrorName(error)
            when (error) {
                GLES20.GL_OUT_OF_MEMORY ->
                    // The state of the GL is undefined after this error occurs.
                    throw OutOfMemoryError(message)

                GLES20.GL_INVALID_ENUM, GLES20.GL_INVALID_VALUE,
                GLES20.GL_INVALID_OPERATION, GLES20.GL_INVALID_FRAMEBUFFER_OPERATION -> {
                    // The offending command is ignored and should have no side effects.
                    Log.e(TAG, message, Throwable())
                    throw RuntimeException(message)
                }

                else -> {
                    // This should never happen.
                    throw RuntimeException(message)
                }
            }
        }
    }

    fun generateTexture(type: Int): Int {
        val ids = IntArray(1)
        GLES30.glGenTextures(1, ids, 0)
        checkGlError("glGenTextures")
        GLES30.glBindTexture(type, ids[0])
        GLES30.glTexParameterf(type, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_NEAREST.toFloat())
        GLES30.glTexParameterf(type, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_NEAREST.toFloat())
        GLES30.glTexParameteri(type, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(type, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        return ids[0]
    }

    fun deleteTexture(id: Int) {
        val ids = IntArray(1) { id}
        GLES20.glDeleteTextures(ids.size, ids, 0)
        checkGlError("glDeleteTextures")
    }
}