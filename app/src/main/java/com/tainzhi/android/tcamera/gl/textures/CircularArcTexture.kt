package com.tainzhi.android.tcamera.gl.textures

import android.opengl.GLES20
import com.tainzhi.android.tcamera.gl.GlUtil
import com.tainzhi.android.tcamera.gl.Shader
import com.tainzhi.android.tcamera.gl.ShaderFactory
import com.tainzhi.android.tcamera.gl.ShaderType
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class CircularArcTexture(val center: Vertex3F, val radius: Float, val startAngle: Float, val sweepAngle: Float): Texture() {
    private lateinit var vertices :FloatArray
    private lateinit var vertexBuffer:  FloatBuffer

    override fun onSetShader(): Shader = shaderFactory.getShader(ShaderType.FRAME)

    override fun load(shaderFactory: ShaderFactory) {
        super.load(shaderFactory)
        generateVertices()
        vertexBuffer = ByteBuffer.allocateDirect(vertices.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
        vertexBuffer.put(vertices).position(0)
    }

    override fun onDraw() {
        super.onDraw()
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glEnableVertexAttribArray(programHandle)
        GLES20.glVertexAttribPointer(programHandle, GlUtil.COORDS_PER_VERTEX, GLES20.GL_FLOAT, false, GlUtil.VERTEX_STRIDE, vertexBuffer)
        setVec4("u_Color", color)
        setFloat("u_Opacity", alpha)
        GLES20.glLineWidth(lineWidth)
        // 每个顶点3个值，xyz. 此处获取顶点数
        GLES20.glDrawArrays(GLES20.GL_LINE_STRIP, 0, vertices.size / GlUtil.COORDS_PER_VERTEX)
        GLES20.glDisableVertexAttribArray(programHandle)
        // reset blend
        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)
    }

    private fun generateVertices() {
        // 360, 720, 1440...分割越多，绘制出的圆越平滑
        val segments = abs(sweepAngle).toInt() * 6
        vertices = FloatArray((segments + 1) * GlUtil.COORDS_PER_VERTEX)
        val angleStep = sweepAngle / segments.toFloat()
        for (i in 0 .. segments) {
            val angleInRadius = (startAngle + angleStep * i)/180f * PI
            vertices[i * GlUtil.COORDS_PER_VERTEX] = (center.x + radius * cos(angleInRadius)).toFloat()
            vertices[i * GlUtil.COORDS_PER_VERTEX + 1] = (center.y + radius * sin(angleInRadius)).toFloat()
            vertices[i * GlUtil.COORDS_PER_VERTEX + 2] = 0f
        }
    }

    companion object {
        private val TAG = CircularArcTexture::class.java.simpleName
    }
}