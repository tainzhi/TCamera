package com.tainzhi.android.tcamera.ui.scrollpicker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.util.AttributeSet
import com.tainzhi.android.tcamera.R
import kotlin.math.abs
import kotlin.math.min

/**
 * @author: tainzhi
 * @mail: qfq61@qq.com
 * @date: 2020/8/18 07:16
 * @description:
 */
class BitmapScrollPicker @JvmOverloads constructor(
    context: Context?, attrs: AttributeSet?,
    defStyleAttr: Int = 0
) :
    ScrollPickerView<Bitmap>(context, attrs, defStyleAttr) {
    private var mMeasureWidth = 0
    private var mMeasureHeight = 0
    private val mRect1 = Rect()
    private val mRect2 = Rect()
    private val mSpecifiedSizeRect = Rect()
    private val mRectTemp = Rect()
    private var mDrawMode = DRAW_MODE_CENTER

    // item内容缩放倍数
    var minScale: Float = 1f
        private set
    var maxScale: Float = 1f
        private set
    private var mSpecifiedSizeWidth = -1
    private var mSpecifiedSizeHeight = -1

    init {
        init(attrs)
    }

    private fun init(attrs: AttributeSet?) {
        if (attrs != null) {
            val typedArray = context.obtainStyledAttributes(
                attrs,
                R.styleable.BitmapScrollPicker
            )
            mDrawMode = typedArray.getInt(
                R.styleable.BitmapScrollPicker_spv_draw_bitmap_mode, mDrawMode
            )
            mSpecifiedSizeWidth = typedArray.getDimensionPixelOffset(
                R.styleable.BitmapScrollPicker_spv_draw_bitmap_width, mSpecifiedSizeWidth
            )
            mSpecifiedSizeHeight = typedArray.getDimensionPixelOffset(
                R.styleable.BitmapScrollPicker_spv_draw_bitmap_height, mSpecifiedSizeHeight
            )
            minScale = typedArray.getFloat(
                R.styleable.BitmapScrollPicker_spv_min_scale,
                minScale
            )
            maxScale = typedArray.getFloat(
                R.styleable.BitmapScrollPicker_spv_max_scale,
                maxScale
            )
            typedArray.recycle()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        mMeasureWidth = measuredWidth
        mMeasureHeight = measuredHeight
        // 当view的的大小确定后，选择器中item的某些位置也可确定。当水平滚动时，item的顶部和底部的坐标y可确定；当垂直滚动时，item的左边和右边的坐标x可确定
        if (mDrawMode == DRAW_MODE_FULL) { // 填充
            if (isHorizontal) {
                mRect2.top = 0
                mRect2.bottom = mMeasureHeight
            } else {
                mRect2.left = 0
                mRect2.right = mMeasureWidth
            }
        } else if (mDrawMode == DRAW_MODE_SPECIFIED_SIZE) { // 指定大小
            if (mSpecifiedSizeWidth == -1) {
                mSpecifiedSizeWidth = mMeasureWidth
                mSpecifiedSizeHeight = mMeasureHeight
            }
            setDrawModeSpecifiedSize(mSpecifiedSizeWidth, mSpecifiedSizeHeight)
        } else {
            // 居中
            val size = if (isHorizontal) {
                min(mMeasureHeight.toDouble(), itemWidth.toDouble()).toInt()
            } else {
                min(mMeasureWidth.toDouble(), itemHeight.toDouble()).toInt()
            }
            if (isHorizontal) {
                mRect2.top = mMeasureHeight / 2 - size / 2
                mRect2.bottom = mMeasureHeight / 2 + size / 2
            } else {
                mRect2.left = mMeasureWidth / 2 - size / 2
                mRect2.right = mMeasureWidth / 2 + size / 2
            }
        }
    }

    override fun drawItem(
        canvas: Canvas,
        data: List<Bitmap>,
        position: Int,
        relative: Int,
        moveLength: Float,
        top: Float
    ) {
        val itemSize = itemSize
        val bitmap = data[position]

        mRect1.right = bitmap.width
        mRect1.bottom = bitmap.height

        var span: Int

        // 根据不同的绘制模式，计算出item内容的最终绘制位置和大小
        // 当水平滚动时，计算item的左边和右边的坐标x；当垂直滚动时，item的顶部和底部的坐标y
        if (mDrawMode == DRAW_MODE_FULL) { // 填充
            span = 0
            if (isHorizontal) {
                mRect2.left = top.toInt() + span
                mRect2.right = (top + itemSize - span).toInt()
            } else {
                mRect2.top = top.toInt() + span
                mRect2.bottom = (top + itemSize - span).toInt()
            }
            mRectTemp.set(mRect2)
            scale(mRectTemp, relative, itemSize, moveLength)
            canvas.drawBitmap(bitmap, mRect1, mRectTemp, null)
        } else if (mDrawMode == DRAW_MODE_SPECIFIED_SIZE) { // 指定大小
            if (isHorizontal) {
                span = (itemSize - mSpecifiedSizeWidth) / 2

                mSpecifiedSizeRect.left = top.toInt() + span
                mSpecifiedSizeRect.right = top.toInt() + span + mSpecifiedSizeWidth
            } else {
                span = (itemSize - mSpecifiedSizeHeight) / 2

                mSpecifiedSizeRect.top = top.toInt() + span
                mSpecifiedSizeRect.bottom = top.toInt() + span + mSpecifiedSizeHeight
            }
            mRectTemp.set(mSpecifiedSizeRect)
            scale(mRectTemp, relative, itemSize, moveLength)
            canvas.drawBitmap(bitmap, mRect1, mRectTemp, null)
        } else { // 居中
            if (isHorizontal) {
                val scale = mRect2.height() * 1f / bitmap.height
                span = ((itemSize - bitmap.width * scale) / 2).toInt()
            } else {
                val scale = mRect2.width() * 1f / bitmap.width
                span = ((itemSize - bitmap.height * scale) / 2).toInt()
            }
            if (isHorizontal) {
                mRect2.left = (top + span).toInt()
                mRect2.right = (top + itemSize - span).toInt()
            } else {
                mRect2.top = (top + span).toInt()
                mRect2.bottom = (top + itemSize - span).toInt()
            }
            mRectTemp.set(mRect2)
            scale(mRectTemp, relative, itemSize, moveLength)
            canvas.drawBitmap(bitmap, mRect1, mRectTemp, null)
        }
    }

    // 缩放item内容
    private fun scale(rect: Rect, relative: Int, itemSize: Int, moveLength: Float) {
        if (minScale == 1f && maxScale == 1f) {
            return
        }

        val spanWidth: Float
        val spanHeight: Float

        if (minScale == maxScale) {
            spanWidth = (rect.width() - minScale * rect.width()) / 2
            spanHeight = (rect.height() - minScale * rect.height()) / 2
            rect.left = (rect.left + spanWidth).toInt()
            rect.right = (rect.right - spanWidth).toInt()
            rect.top = (rect.top + spanHeight).toInt()
            rect.bottom = (rect.bottom - spanHeight).toInt()
            return
        }

        if (relative == -1 || relative == 1) { // 上一个或下一个
            // 处理上一个item且向上滑动　或者　处理下一个item且向下滑动,
            if ((relative == -1 && moveLength < 0)
                || (relative == 1 && moveLength > 0)
            ) {
                spanWidth = (rect.width() - minScale * rect.width()) / 2
                spanHeight = (rect.height() - minScale * rect.height()) / 2
            } else { // 计算渐变
                val rate = (abs(moveLength.toDouble()) / itemSize).toFloat()
                spanWidth =
                    (rect.width() - (minScale + (maxScale - minScale) * rate) * rect.width()) / 2
                spanHeight =
                    (rect.height() - (minScale + (maxScale - minScale) * rate) * rect.height()) / 2
            }
        } else if (relative == 0) { // 中间item
            val rate = ((itemSize - abs(moveLength.toDouble())) / itemSize).toFloat()
            spanWidth =
                (rect.width() - (minScale + (maxScale - minScale) * rate) * rect.width()) / 2
            spanHeight =
                (rect.height() - (minScale + (maxScale - minScale) * rate) * rect.height()) / 2
        } else {
            spanWidth = (rect.width() - minScale * rect.width()) / 2
            spanHeight = (rect.height() - minScale * rect.height()) / 2
        }

        rect.left = (rect.left + spanWidth).toInt()
        rect.right = (rect.right - spanWidth).toInt()
        rect.top = (rect.top + spanHeight).toInt()
        rect.bottom = (rect.bottom - spanHeight).toInt()
    }

    fun setDrawModeSpecifiedSize(width: Int, height: Int) {
        if (isHorizontal) {
            mSpecifiedSizeRect.top = (mMeasureHeight - height) / 2
            mSpecifiedSizeRect.bottom = (mMeasureHeight - height) / 2 + height
        } else {
            mSpecifiedSizeRect.left = (mMeasureWidth - width) / 2
            mSpecifiedSizeRect.right = (mMeasureWidth - width) / 2 + width
        }
        mSpecifiedSizeWidth = width
        mSpecifiedSizeHeight = height
        invalidate()
    }

    var drawMode: Int
        /**
         * 图片绘制模式 ，默认为居中
         *
         * @return
         */
        get() = mDrawMode
        /**
         * 图片绘制模式 ，默认为居中
         *
         * @param mode
         */
        set(mode) {
            var size = 0
            size = if (isHorizontal) {
                min(mMeasureHeight.toDouble(), itemWidth.toDouble()).toInt()
            } else {
                min(mMeasureWidth.toDouble(), itemHeight.toDouble()).toInt()
            }
            mDrawMode = mode
            if (mDrawMode == DRAW_MODE_FULL) {
                if (isHorizontal) {
                    mRect2.top = 0
                    mRect2.bottom = mMeasureHeight
                } else {
                    mRect2.left = 0
                    mRect2.right = mMeasureWidth
                }
            } else if (mDrawMode == DRAW_MODE_SPECIFIED_SIZE) {
            } else {
                if (isHorizontal) {
                    mRect2.top = mMeasureHeight / 2 - size / 2
                    mRect2.bottom = mMeasureHeight / 2 + size / 2
                } else {
                    mRect2.left = mMeasureWidth / 2 - size / 2
                    mRect2.right = mMeasureWidth / 2 + size / 2
                }
            }
            invalidate()
        }

    /**
     * item内容缩放倍数
     *
     * @param minScale 沒有被选中时的最小倍数
     * @param maxScale 被选中时的最大倍数
     */
    fun setItemScale(minScale: Float, maxScale: Float) {
        this.minScale = minScale
        this.maxScale = maxScale
        invalidate()
    }

    companion object {
        /**
         * 图片绘制模式：填充
         */
        const val DRAW_MODE_FULL: Int = 1 //

        /**
         * 图片绘制模式：居中
         */
        const val DRAW_MODE_CENTER: Int = 2 //

        /**
         * 图片绘制模式：指定大小
         */
        const val DRAW_MODE_SPECIFIED_SIZE: Int = 3 //
    }
}
