package com.tainzhi.android.tcamera.gl.textures

import android.opengl.GLES20
import com.tainzhi.android.tcamera.gl.GlUtil
import com.tainzhi.android.tcamera.gl.Shader
import com.tainzhi.android.tcamera.gl.ShaderFactory

abstract class TextureBase {
    protected val PREVIEW_TEXTURE = GLES20.GL_TEXTURE0
    protected val FILTER_TEXTURE = GLES20.GL_TEXTURE1
    protected val OFFSCREEN_TEXTURE = GLES20.GL_TEXTURE3
    private var isInitialed = false
    protected var modelMatrix = FloatArray(16)
    protected var viewMatrix = FloatArray(16)
    protected var projectionMatrix = FloatArray(16)
    open fun load(shaderFactory: ShaderFactory) {
        isInitialed = true
    }
    open fun unload() {
        isInitialed = false
    }

    open fun draw() {
        onDraw()
    }

    fun setMatrix(model: FloatArray, view: FloatArray, projection: FloatArray) {
        modelMatrix = model
        viewMatrix = view
        projectionMatrix = projection

    }
    abstract fun onDraw()
}

abstract class Texture: TextureBase() {
     // 顶点坐标句柄
    protected var programHandle = 0
    protected lateinit var shader: Shader

    private var attributeMap = hashMapOf<String, Int>()
    protected lateinit var shaderFactory: ShaderFactory
    var color = floatArrayOf(1f, 1f, 1f, 1f)
    var alpha = 1f
    var lineWidth = 1f
    var visibility = true

    override fun load(shaderFactory: ShaderFactory) {
        super.load(shaderFactory)
        this.shaderFactory = shaderFactory
        shader = onSetShader()
        programHandle = GLES20.glGetAttribLocation(shader.programHandle, "a_Position")
    }

    abstract fun onSetShader(): Shader

    protected fun onClear() {

    }

    override fun onDraw() {
        if (visibility) {
            onClear()
            shader.use()
            // 4x4 matrix
            setMat4("u_ModelMatrix", modelMatrix)
            setMat4("u_ViewMatrix", viewMatrix)
            setMat4("u_ProjectionMatrix", projectionMatrix)
        }
    }

    protected fun setMat4(matName: String, mat: FloatArray) {
        if (!attributeMap.containsKey(matName)) {
            attributeMap.put(matName, GLES20.glGetUniformLocation(shader.programHandle, matName))
        }
        GLES20.glUniformMatrix4fv(
            attributeMap[matName]!!,
            mat.size / 16, false, mat, 0
        )
        GlUtil.checkGlError("set Matrix4 to ${matName}:")
    }

    protected fun setFloat(name: String, value: Float) {
        if (!attributeMap.containsKey(name)) {
            attributeMap.put(name, GLES20.glGetUniformLocation(shader.programHandle, name))
        }
        GLES20.glUniform1f(attributeMap[name]!!, value)
    }


    protected fun setInt(name: String, value: Int) {
        if (!attributeMap.containsKey(name)) {
            attributeMap.put(name, GLES20.glGetUniformLocation(shader.programHandle, name))
        }
        GLES20.glUniform1i(attributeMap[name]!!, value)
    }

    protected fun setVec2(name: String, value: FloatArray) {
        if (!attributeMap.containsKey(name)) {
            attributeMap.put(name, GLES20.glGetUniformLocation(shader.programHandle, name))
        }
        GLES20.glUniform2fv(
            attributeMap[name]!!,
            value.size / 2, value, 0
        )
    }

    protected fun setVec4(name: String, value: FloatArray) {
        if (!attributeMap.containsKey(name)) {
            attributeMap.put(name, GLES20.glGetUniformLocation(shader.programHandle, name))
        }
        GLES20.glUniform4fv(
            attributeMap[name]!!,
            value.size / 4, value, 0
        )
    }
}