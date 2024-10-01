package com.tainzhi.android.tcamera.gl.textures

import android.graphics.RectF
import android.opengl.GLES20
import android.util.Log
import com.tainzhi.android.tcamera.gl.GlUtil
import com.tainzhi.android.tcamera.gl.Shader
import com.tainzhi.android.tcamera.gl.ShaderFactory
import com.tainzhi.android.tcamera.gl.ShaderType
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * OffScreen FrameBufferObject
 */
class BlurPreviewTexture : Texture() {
    private var hTexturePosition = 0
    private lateinit var textureSize: Vertex2F
    private lateinit var textureMatrix: FloatArray
    private var isTrueAspectRatio: Int = 0
    private var textureId: Int = 0
    private lateinit var vertexBuffer: FloatBuffer
    private lateinit var textureVertexBuffer: FloatBuffer

    //纹理坐标
    private var textureVertices = floatArrayOf(
        0.0f, 0.0f,
        0.0f, 1.0f,
        1.0f, 0.0f,
        1.0f, 1.0f
    )

    private var frameBuffer = 0
    private var renderBuffer = 0
    private var renderTexture = 0

    override fun onSetShader(): Shader = shaderFactory.getShader(ShaderType.CAMERA_PREVIEW_BLUR)

    override fun load(shaderFactory: ShaderFactory) {
        super.load(shaderFactory)
        hTexturePosition = GLES20.glGetAttribLocation(shader.programHandle, "a_TexturePosition")
        textureVertexBuffer = ByteBuffer.allocateDirect(textureVertices.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
        textureVertexBuffer.put(textureVertices).position(0)
    }

    override fun unload() {
        Log.d(TAG, "unload: ")
        val ids = IntArray(1){renderBuffer}
        GLES20.glDeleteRenderbuffers(ids.size, ids, 0)
        super.unload()
    }

    override fun onDraw() {
        if (visibility) {
            Log.d(TAG, "onDraw: ")
            super.onDraw()
            setMat4("u_TextureMatrix", textureMatrix)
            setVec2("u_TextureSize", floatArrayOf(textureSize.x, textureSize.y))
            setInt("u_IsTrueAspectRatio", isTrueAspectRatio)
            // bind preview texture
            GLES20.glActiveTexture(PREVIEW_TEXTURE)
            // GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
            setInt("u_TextureSampler", PREVIEW_TEXTURE - GLES20.GL_TEXTURE0)
            // set vertex attribute
            GLES20.glVertexAttribPointer(programHandle, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)
            GLES20.glEnableVertexAttribArray(programHandle)
            GLES20.glVertexAttribPointer(
                hTexturePosition,
                2,
                GLES20.GL_FLOAT,
                false,
                0,
                textureVertexBuffer
            )
            GLES20.glEnableVertexAttribArray(hTexturePosition)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            GLES20.glDisableVertexAttribArray(programHandle)
            GLES20.glDisableVertexAttribArray(hTexturePosition)
        }
    }

    private fun createTexture() {
        renderTexture = GlUtil.generateTexture(GLES20.GL_TEXTURE_2D)
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
            textureSize.x.toInt(), textureSize.y.toInt(), 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null)
    }

    private fun createRenderBuffer(): Int {
        val rb = IntArray(1)
        GLES20.glGenRenderbuffers(rb.size, rb, 0)
        GlUtil.checkGlError("$TAG:glGenRenderBuffers")
        GLES20.glBindRenderbuffer(GLES20.GL_RENDERBUFFER, rb[0])
        GlUtil.checkGlError("$TAG:glBindRenderBuffers")
        GLES20.glRenderbufferStorage(GLES20.GL_RENDERBUFFER, GLES20.GL_DEPTH_COMPONENT16, textureSize.x.toInt(), textureSize.y.toInt())
        GlUtil.checkGlError("$TAG:glRenderBufferStorage")
        return rb[0]
    }

    private fun createFrameBuffer() {
        val ids = IntArray(1)
        GLES20.glGenFramebuffers(ids.size, ids, 0)
        GlUtil.checkGlError("$TAG:glGenFrameBuffers")
        frameBuffer = ids[0]
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, frameBuffer)
        GlUtil.checkGlError("$TAG:glBindFrameBuffers")
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, renderTexture, 0)
        GlUtil.checkGlError("$TAG:glFramebufferTexture2D")
        GLES20.glFramebufferRenderbuffer(GLES20.GL_FRAMEBUFFER, GLES20.GL_DEPTH_ATTACHMENT, GLES20.GL_RENDERBUFFER, renderBuffer)
        val status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER)
        if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
            Log.e(TAG, "create frame buffer object failed", )
        } else {
            Log.d(TAG, "create frame buffer object success")
        }
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
    }


    fun setLayout(
        textureId: Int,
        textureSize: Vertex2F,
        textureMatrix: FloatArray,
        isTrueAspectRatio: Int,
        previewRectF: RectF
    ) {
        Log.d(TAG, "setLayout: ")
        //顶点坐标
        // 忽略z维度，只保留x,y维度
        val vertices = floatArrayOf(
            previewRectF.left, previewRectF.top,
            previewRectF.left, previewRectF.bottom,
            previewRectF.right, previewRectF.top,
            previewRectF.right, previewRectF.bottom
        )

        vertexBuffer = ByteBuffer.allocateDirect(vertices.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
        vertexBuffer.put(vertices).position(0)
        this.textureId = textureId
        this.textureSize = textureSize
        this.textureMatrix = textureMatrix
        this.isTrueAspectRatio = isTrueAspectRatio

        createTexture()
        renderBuffer = createRenderBuffer()
        createFrameBuffer()
    }

    fun toggleBindFrameBuffer(toggle: Boolean) {
        if (toggle) {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, frameBuffer)
        } else {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        }
    }

    companion object {
        private val TAG = BlurPreviewTexture::class.java.simpleName
    }
}