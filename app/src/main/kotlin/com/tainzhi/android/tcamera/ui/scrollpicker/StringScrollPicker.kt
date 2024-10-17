package com.tainzhi.android.tcamera.ui.scrollpicker

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.AttributeSet
import com.tainzhi.android.tcamera.R
import com.tainzhi.android.tcamera.util.ColorUtil.computeGradientColor
import kotlin.math.abs

/**
 * @author: tainzhi
 * @mail: qfq61@qq.com
 * @date: 2020/8/18 07:15
 * @description: 字符串滚动选择器 [copy自](https://blog.csdn.net/u012964944/article/details/73189206)
 */
class StringScrollPicker @JvmOverloads constructor(
    context: Context?, attrs: AttributeSet?,
    defStyleAttr: Int = 0
) :
    ScrollPickerView<CharSequence>(context, attrs, defStyleAttr) {
    private var mMeasureWidth = 0
    private var mMeasureHeight = 0

    private val mPaint = TextPaint(Paint.ANTI_ALIAS_FLAG) //
    var minTextSize: Int = 24 // 最小的字体
        private set
    var maxTextSize: Int = 32 // 最大的字体
        private set

    // 字体渐变颜色
    var startColor: Int = Color.BLACK // 中间选中ｉｔｅｍ的颜色
        private set
    var endColor: Int = Color.GRAY // 上下两边的颜色
        private set

    /**
     * 最大的行宽,默认为itemWidth.超过后文字自动换行
     *
     * @param maxLineWidth
     */
    var maxLineWidth: Int = -1 // 最大的行宽,默认为itemWidth.超过后文字自动换行

    /**
     * 最大的行宽,默认为itemWidth.超过后文字自动换行
     *
     * @return
     */
    var alignment: Layout.Alignment = Layout.Alignment.ALIGN_CENTER // 对齐方式,默认居中


    init {
        mPaint.style = Paint.Style.FILL
        mPaint.color = Color.BLACK
        init(attrs)

        data = mutableListOf<CharSequence>(
                "one", "two", "three", "four", "five", "six", "seven",
                "eight", "nine", "ten", "eleven", "twelve")
    }

    private fun init(attrs: AttributeSet?) {
        if (attrs != null) {
            val typedArray = context.obtainStyledAttributes(
                attrs,
                R.styleable.StringScrollPicker
            )
            minTextSize = typedArray.getDimensionPixelSize(
                R.styleable.StringScrollPicker_spv_min_text_size, minTextSize
            )
            maxTextSize = typedArray.getDimensionPixelSize(
                R.styleable.StringScrollPicker_spv_max_text_size, maxTextSize
            )
            startColor = typedArray.getColor(
                R.styleable.StringScrollPicker_spv_start_color, startColor
            )
            endColor = typedArray.getColor(
                R.styleable.StringScrollPicker_spv_end_color, endColor
            )
            maxLineWidth = typedArray.getDimensionPixelSize(
                R.styleable.StringScrollPicker_spv_max_line_width,
                maxLineWidth
            )
            val align = typedArray.getInt(R.styleable.StringScrollPicker_spv_alignment, 1)
            if (align == 2) {
                alignment = Layout.Alignment.ALIGN_NORMAL
            } else if (align == 3) {
                alignment = Layout.Alignment.ALIGN_OPPOSITE
            } else {
                alignment = Layout.Alignment.ALIGN_CENTER
            }
            typedArray.recycle()
        }
    }

    /**
     * @param startColor 正中间的颜色
     * @param endColor   上下两边的颜色
     */
    fun setColor(startColor: Int, endColor: Int) {
        this.startColor = startColor
        this.endColor = endColor
        invalidate()
    }

    /**
     * item文字大小
     *
     * @param minText 沒有被选中时的最小文字
     * @param maxText 被选中时的最大文字
     */
    fun setTextSize(minText: Int, maxText: Int) {
        minTextSize = minText
        maxTextSize = maxText
        invalidate()
    }


    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        mMeasureWidth = measuredWidth
        mMeasureHeight = measuredHeight
        if (maxLineWidth < 0) {
            maxLineWidth = itemWidth
        }
    }

    override fun drawItem(
        canvas: Canvas,
        data: List<CharSequence>,
        position: Int,
        relative: Int,
        moveLength: Float,
        top: Float
    ) {
        val text = data[position]
        val itemSize = itemSize

        // 设置文字大小
        if (relative == -1) { // 上一个
            if (moveLength < 0) { // 向上滑动
                mPaint.textSize = minTextSize.toFloat()
            } else { // 向下滑动
                mPaint.textSize = (minTextSize + (maxTextSize - minTextSize)
                        * moveLength / itemSize)
            }
        } else if (relative == 0) { // 中间item,当前选中
            mPaint.textSize = (minTextSize + (maxTextSize - minTextSize)
                    * (itemSize - abs(moveLength.toDouble())) / itemSize).toFloat()
        } else if (relative == 1) { // 下一个
            if (moveLength > 0) { // 向下滑动
                mPaint.textSize = minTextSize.toFloat()
            } else { // 向上滑动
                mPaint.textSize = (minTextSize + (maxTextSize - minTextSize)
                        * -moveLength / itemSize)
            }
        } else { // 其他
            mPaint.textSize = minTextSize.toFloat()
        }

        val layout = StaticLayout(
            text, 0, text.length, mPaint,
            maxLineWidth,
            alignment, 1.0f, 0.0f, true, null, 0
        )
        var x = 0f
        var y = 0f
        val lineWidth = layout.width.toFloat()

        if (isHorizontal) { // 水平滚动
            x = top + (itemWidth - lineWidth) / 2
            y = ((itemHeight - layout.height) / 2).toFloat()
        } else { // 垂直滚动
            x = (itemWidth - lineWidth) / 2
            y = top + (itemHeight - layout.height) / 2
        }
        // 计算渐变颜色
        computeColor(relative, itemSize, moveLength)

        //        canvas.drawText(text, x, y, mPaint);
        canvas.save()
        canvas.translate(x, y)
        layout.draw(canvas)
        canvas.restore()
    }

    /**
     * 计算字体颜色，渐变
     *
     * @param relative 　相对中间item的位置
     */
    private fun computeColor(relative: Int, itemSize: Int, moveLength: Float) {
        var color = endColor // 　其他默认为ｍEndColor

        if (relative == -1 || relative == 1) { // 上一个或下一个
            // 处理上一个item且向上滑动　或者　处理下一个item且向下滑动　，颜色为mEndColor
            if ((relative == -1 && moveLength < 0)
                || (relative == 1 && moveLength > 0)
            ) {
                color = endColor
            } else { // 计算渐变的颜色
                val rate = (((itemSize - abs(moveLength.toDouble()))
                        / itemSize).toFloat())
                color = computeGradientColor(startColor, endColor, rate)
            }
        } else if (relative == 0) { // 中间item
            val rate = (abs(moveLength.toDouble()) / itemSize).toFloat()
            color = computeGradientColor(startColor, endColor, rate)
        }

        mPaint.color = color
    }
}
