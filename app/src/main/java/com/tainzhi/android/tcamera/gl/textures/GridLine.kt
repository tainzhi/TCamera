package com.tainzhi.android.tcamera.gl.textures

import android.graphics.RectF
import com.tainzhi.android.tcamera.gl.ShaderFactory
import com.tainzhi.android.tcamera.util.SettingsManager
import java.lang.Math.pow
import kotlin.math.sqrt

class GridLine : TextureBase() {
    private val components = mutableListOf<Texture>()
    private var lineWidth = 1f
    private var linePadding = 20f
    private var alpha = 0.9f
    private lateinit var shaderFactory: ShaderFactory

    private fun generateLines(previewRectF: RectF) {
        val enableGridLine = SettingsManager.instance.getGridLineEnable()
        if (!enableGridLine) return
        when (SettingsManager.instance.getGridLineType()) {
            // 对角线
            SettingsManager.GridLineType.DIAGONAL.ordinal -> {
                components.add(
                    LineTexture(
                        Vertex3F(previewRectF.left + linePadding, previewRectF.top + linePadding, 0f),
                        Vertex3F(previewRectF.right - linePadding, previewRectF.bottom - linePadding, 0f)
                    )
                )
                components.add(
                    LineTexture(
                        Vertex3F(previewRectF.right - linePadding, previewRectF.top + linePadding, 0f),
                        Vertex3F(previewRectF.left + linePadding, previewRectF.bottom - linePadding , 0f)
                    )
                )
            }
            // 2x2均分线
            SettingsManager.GridLineType.CROSSHAIR_2X2.ordinal -> {
                components.add(
                    LineTexture(
                        Vertex3F(previewRectF.centerX(), previewRectF.top + linePadding, 0f),
                        Vertex3F(previewRectF.centerX(), previewRectF.bottom - linePadding, 0f)
                    )
                )
                components.add(
                    LineTexture(
                        Vertex3F(previewRectF.left + linePadding, previewRectF.centerY(), 0f),
                        Vertex3F(previewRectF.right - linePadding, previewRectF.centerY(), 0f),
                    )
                )
            }
            // 3x3 均分线
            SettingsManager.GridLineType.CONTOUR_3x3.ordinal -> {
                components.add(
                    LineTexture(
                        Vertex3F(previewRectF.left + linePadding, previewRectF.top + previewRectF.height() /3f + linePadding, 0f),
                        Vertex3F(previewRectF.right - linePadding, previewRectF.top + previewRectF.height() /3f + linePadding, 0f)
                    )
                )
                components.add(
                    LineTexture(
                        Vertex3F(previewRectF.left + linePadding, previewRectF.top + previewRectF.height() *2 /3f + linePadding, 0f),
                        Vertex3F(previewRectF.right - linePadding, previewRectF.top + previewRectF.height() *2/3f + linePadding, 0f)
                    )
                )
                components.add(
                    LineTexture(
                        Vertex3F(previewRectF.left + previewRectF.width()/3f + linePadding, previewRectF.top + linePadding , 0f),
                        Vertex3F(previewRectF.left + previewRectF.width()/3f + linePadding, previewRectF.bottom - linePadding, 0f)
                    )
                )
                components.add(
                    LineTexture(
                        Vertex3F(previewRectF.left + previewRectF.width() * 2/3f + linePadding, previewRectF.top + linePadding , 0f),
                        Vertex3F(previewRectF.left + previewRectF.width() * 2/3f + linePadding, previewRectF.bottom - linePadding, 0f)
                    )
                )
            }
            // 3x3 golden
            SettingsManager.GridLineType.GOLDEN_SECTION_3x3.ordinal -> {
                val widthGolden = previewRectF.width() / GOLDEN_SPLIT_RATIO.toFloat()
                val heightGolden = previewRectF.height() / GOLDEN_SPLIT_RATIO.toFloat()
                components.add(
                    LineTexture(
                        Vertex3F(previewRectF.left + linePadding + widthGolden, previewRectF.top + linePadding , 0f),
                        Vertex3F(previewRectF.left + linePadding + widthGolden, previewRectF.bottom - linePadding, 0f)
                    )
                )
                components.add(
                    LineTexture(
                        Vertex3F(previewRectF.right - linePadding - widthGolden, previewRectF.top + linePadding , 0f),
                        Vertex3F(previewRectF.right - linePadding - widthGolden, previewRectF.bottom - linePadding, 0f)
                    )
                )
                components.add(
                    LineTexture(
                        Vertex3F(previewRectF.left + linePadding, previewRectF.top + linePadding + heightGolden , 0f),
                        Vertex3F(previewRectF.right - linePadding, previewRectF.top + linePadding  + heightGolden, 0f)
                    )
                )
                components.add(
                    LineTexture(
                        Vertex3F(previewRectF.left + linePadding, previewRectF.bottom - linePadding - heightGolden , 0f),
                        Vertex3F(previewRectF.right - linePadding,previewRectF.bottom - linePadding - heightGolden, 0f)
                    )
                )
            }
            // golden spiral
            SettingsManager.GridLineType.GOLDEN_SPIRAL.ordinal -> {
                // 对于1:1, 4:3比例中height过短的情形，使得golden spiral与top边相切
                // 对于16:9, FULL比例的height较长的情形， 使得golden spiral与right边相切
                // like android view coordinates, origin is top left, so
                // https://en.wikipedia.org/wiki/Golden_spiral
                val isHeightGoldenDividen = previewRectF.height() /previewRectF.width() < 1.5
                var r1 = 0.0
                var x1 = 0.0
                var y1 = 0.0
                if (isHeightGoldenDividen) {
                    // h'(1/ph^2 + 1/ph^3) = w
                    val goldenWidth = previewRectF.height() *(1 / pow(
                        GOLDEN_SPLIT_RATIO, 2.0) + 1/pow(GOLDEN_SPLIT_RATIO, 3.0))
                    r1 = (previewRectF.height() )/ GOLDEN_SPLIT_RATIO
                    y1 = previewRectF.bottom - r1
                    x1 = (previewRectF.width() - goldenWidth)/2 + r1 + previewRectF.left
                    components.add(
                        CircularArcTexture(Vertex3F(x1.toFloat(), y1.toFloat(), 0f),r1.toFloat(),90f, 90f )
                    )
                }
                else {
                    // h'(1/ph^2 + 1/ph^3) = w
                    val goldenHeight = previewRectF.width()/(1 / pow(
                        GOLDEN_SPLIT_RATIO, 2.0) + 1/pow(GOLDEN_SPLIT_RATIO, 3.0))
                    x1 = previewRectF.right.toDouble()
                    y1 = previewRectF.bottom -((previewRectF.height() - goldenHeight)/2 + goldenHeight / GOLDEN_SPLIT_RATIO)
                    r1 = goldenHeight/ GOLDEN_SPLIT_RATIO
                    components.add(
                        CircularArcTexture(Vertex3F(x1.toFloat(), y1.toFloat(), 0f),r1.toFloat(),90f, 90f )
                    )
                }
                val r2 = r1/ GOLDEN_SPLIT_RATIO
                val x2 = x1 - (r1 -r2)
                val y2 = y1
                components.add(
                    CircularArcTexture(Vertex3F(x2.toFloat(), y2.toFloat(), 0f),r2.toFloat(),180f, 90f )
                )
                val r3 = r2/ GOLDEN_SPLIT_RATIO
                val x3 = x2
                val y3 = y2 - (r2 - r3)
                components.add(
                    CircularArcTexture(Vertex3F(x3.toFloat(), y3.toFloat(), 0f),r3.toFloat(),270f, 90f )
                )
                val r4 = r3/ GOLDEN_SPLIT_RATIO
                val x4 = x3 + (r3 - r4)
                val y4 = y3
                components.add(
                    CircularArcTexture(Vertex3F(x4.toFloat(), y4.toFloat(), 0f),r4.toFloat(),0f, 90f )
                )
                val r5 = r4/ GOLDEN_SPLIT_RATIO
                val x5 = x4
                val y5 = y4 + (r4 - r5)
                components.add(
                    CircularArcTexture(Vertex3F(x5.toFloat(), y5.toFloat(), 0f),r5.toFloat(),90f, 90f )
                )
                val r6 = r5/ GOLDEN_SPLIT_RATIO
                val x6 = x5 - (r5 - r6)
                val y6 = y5
                components.add(
                    CircularArcTexture(Vertex3F(x6.toFloat(), y6.toFloat(), 0f),r6.toFloat(),180f, 90f )
                )
                val r7 = r6/ GOLDEN_SPLIT_RATIO
                val x7 = x6
                val y7 = y6 - (r6 - r7)
                components.add(
                    CircularArcTexture(Vertex3F(x7.toFloat(), y7.toFloat(), 0f),r7.toFloat(),270f, 90f )
                )
                val r8 = r7/ GOLDEN_SPLIT_RATIO
                val x8 = x7 + (r7 - r8)
                val y8 = y7
                components.add(
                    CircularArcTexture(Vertex3F(x8.toFloat(), y8.toFloat(), 0f),r8.toFloat(),0f, 90f )
                )
                val r9 = r8/ GOLDEN_SPLIT_RATIO
                val x9 = x8
                val y9 = y8 + (r8 - r9)
                components.add(
                    CircularArcTexture(Vertex3F(x9.toFloat(), y9.toFloat(), 0f),r9.toFloat(),90f, 90f )
                )

                components.add(
                    LineTexture(Vertex3F(x4.toFloat(), (y4 + r4).toFloat(), 0f), Vertex3F((x1 - r1).toFloat(), y1.toFloat(), 0f))
                )
                components.add(
                    LineTexture(Vertex3F((x5 - r5).toFloat(), (y5 + r5).toFloat(), 0f), Vertex3F(x2.toFloat(), (y1 - r2).toFloat(), 0f))
                )
                components.add(
                    LineTexture(Vertex3F((x6 - r6).toFloat(), (y6 - r6).toFloat(), 0f), Vertex3F((x2 + r3).toFloat(), y3.toFloat(), 0f))
                )
                components.add(
                    LineTexture(Vertex3F((x7 + r7).toFloat(), (y7 - r7).toFloat(), 0f), Vertex3F(x4.toFloat(), (y4 + r4).toFloat(), 0f))
                )
                components.add(
                    LineTexture(Vertex3F((x8 + r8).toFloat(), (y8 + r8).toFloat(), 0f), Vertex3F(x2.toFloat(), y5.toFloat(), 0f))
                )
            }
        }
    }

    override fun load(shaderFactory: ShaderFactory) {
        super.load(shaderFactory)
        this.shaderFactory = shaderFactory
    }
    override fun onDraw() {
        components.forEach {
            it.draw()
        }
    }

    fun setLayout(previewRect: RectF) {
        components.clear()
        generateLines(previewRect)
        components.forEach {
            it.load(shaderFactory)
            it.lineWidth = lineWidth
            it.alpha = alpha
            it.setMatrix(modelMatrix, viewMatrix, projectionMatrix)
        }
    }

    override fun unload() {
        components.clear()
        super.unload()
    }

    companion object {
        private val GOLDEN_SPLIT_RATIO = (1 + sqrt(5.0))/2
        private val TAG = GridLine::class.java.simpleName
    }
}