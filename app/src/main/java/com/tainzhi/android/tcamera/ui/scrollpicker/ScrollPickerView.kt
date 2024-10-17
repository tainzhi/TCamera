package com.tainzhi.android.tcamera.ui.scrollpicker

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.MotionEvent
import android.view.View
import android.view.animation.Interpolator
import android.widget.Scroller
import com.tainzhi.android.tcamera.R
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

/**
 * 滚动选择器,带惯性滑动
 *
 * @author: tainzhi
 * @mail: qfq61@qq.com
 * @date: 2020/8/17 23:32
 * @description: [copy自](https://blog.csdn.net/u012964944/article/details/73189206)
 * [github repo](https://github.com/1993hzw/Androids/blob/master/AndroidsDemo/src/com/example/androidsdemo/ScrollPickerViewDemo.java)
 */
abstract class ScrollPickerView<T> @JvmOverloads constructor(
    context: Context?, attrs: AttributeSet?,
    defStyleAttr: Int = 0
) :
    View(context, attrs, defStyleAttr) {
    private var mVisibleItemCount = 3 // 可见的item数量
    var isInertiaScroll: Boolean = true // 快速滑动时是否惯性滚动一段距离，默认开启
    var isIsCirculation: Boolean = true // 是否循环滚动，默认开启
        private set

    /**
     * 是否允许父元素拦截事件，设置true后可以保证在ScrollView下正常滚动
     */
    /*
           不允许父组件拦截触摸事件，设置为true为不允许拦截，此时该设置才生效
           当嵌入到ScrollView等滚动组件中，为了使该自定义滚动选择器可以正常工作，请设置为true
          */
    var isDisallowInterceptTouch: Boolean = false
    private var mSelected = 0 // 当前选中的item下标
    private var mData: List<T>? = null
    var itemHeight: Int = 0 // 每个条目的高度,当垂直滚动时，高度=mMeasureHeight／mVisibleItemCount
        private set
    var itemWidth: Int = 0 // 每个条目的宽度，当水平滚动时，宽度=mMeasureWidth／mVisibleItemCount
        private set

    /**
     * @return 当垂直滚动时，mItemSize = mItemHeight;水平滚动时，mItemSize = mItemWidth
     */
    var itemSize: Int = 0 // 当垂直滚动时，mItemSize = mItemHeight;水平滚动时，mItemSize = mItemWidth
        private set
    private var mCenterPosition =
        -1 // 中间item的位置，0<=mCenterPosition＜mVisibleItemCount，默认为 mVisibleItemCount / 2

    /**
     * @return 中间item的起始坐标y(不考虑偏移), 当垂直滚动时，y= mCenterPosition*mItemHeight
     */
    var centerY: Int = 0 // 中间item的起始坐标y(不考虑偏移),当垂直滚动时，y= mCenterPosition*mItemHeight
        private set

    /**
     * @return 中间item的起始坐标x(不考虑偏移), 当垂直滚动时，x = mCenterPosition*mItemWidth
     */
    var centerX: Int = 0 // 中间item的起始坐标x(不考虑偏移),当垂直滚动时，x = mCenterPosition*mItemWidth
        private set

    /**
     * @return 当垂直滚动时，mCenterPoint = mCenterY;水平滚动时，mCenterPoint = mCenterX
     */
    var centerPoint: Int = 0 // 当垂直滚动时，mCenterPoint = mCenterY;水平滚动时，mCenterPoint = mCenterX
        private set
    private var mLastMoveY = 0f // 触摸的坐标y
    private var mLastMoveX = 0f // 触摸的坐标X
    private var mMoveLength = 0f // item移动长度，负数表示向上移动，正数表示向下移动
    private val mGestureDetector: GestureDetector
    var listener: OnSelectedListener? = null
        private set
    private val mScroller: Scroller
    var isFling: Boolean = false // 是否正在惯性滑动
        private set
    var isMovingCenter: Boolean = false // 是否正在滑向中间
        private set

    // 可以把scroller看做模拟的触屏滑动操作，mLastScrollY为上次触屏滑动的坐标
    private var mLastScrollY = 0 // Scroller的坐标y
    private var mLastScrollX = 0 // Scroller的坐标x

    /**
     * 设置是否允许手动触摸滚动
     *
     * @param disallowTouch
     */
    var isDisallowTouch: Boolean = false // 不允许触摸
    private val mPaint: Paint //
    private var mCenterItemBackground: Drawable? = null // 中间选中item的背景色

    /**
     * 设置 单击切换选项或触发点击监听器
     *
     * @param canTap
     */
    var isCanTap: Boolean = true // 单击切换选项或触发点击监听器
    private var mIsHorizontal = false // 是否水平滚动
    var isDrawAllItem: Boolean = false // 是否绘制每个item(包括在边界外的item)
    private var mHasCallSelectedListener = false // 用于标志第一次设置selected时把事件通知给监听器
    private var mSelectedOnTouch = 0
    var isAutoScrolling: Boolean = false
        private set
    private val mAutoScrollAnimator: ValueAnimator

    init {
        mGestureDetector = GestureDetector(
            getContext(),
            FlingOnGestureListener()
        )
        mScroller = Scroller(getContext())
        mAutoScrollAnimator = ValueAnimator.ofInt(0, 0)

        mPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        mPaint.style = Paint.Style.FILL

        init(attrs)
    }

    private fun init(attrs: AttributeSet?) {
        if (attrs != null) {
            val typedArray = context.obtainStyledAttributes(
                attrs,
                R.styleable.ScrollPickerView
            )

            if (typedArray.hasValue(R.styleable.ScrollPickerView_spv_center_item_background)) {
                centerItemBackground =
                    typedArray.getDrawable(R.styleable.ScrollPickerView_spv_center_item_background)
            }
            visibleItemCount = typedArray.getInt(
                R.styleable.ScrollPickerView_spv_visible_item_count,
                visibleItemCount
            )
            centerPosition = typedArray.getInt(
                R.styleable.ScrollPickerView_spv_center_item_position,
                centerPosition
            )
            setIsCirculation(
                typedArray.getBoolean(
                    R.styleable.ScrollPickerView_spv_is_circulation,
                    isIsCirculation
                )
            )
            isDisallowInterceptTouch = typedArray.getBoolean(
                R.styleable.ScrollPickerView_spv_disallow_intercept_touch,
                isDisallowInterceptTouch
            )
            isHorizontal = typedArray.getInt(
                R.styleable.ScrollPickerView_spv_orientation,
                if (mIsHorizontal) 1 else 2
            ) == 1
            typedArray.recycle()
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (mData == null || mData!!.size <= 0) {
            return
        }

        // 选中item的背景色
        if (mCenterItemBackground != null) {
            mCenterItemBackground!!.draw(canvas)
        }

        // 只绘制可见的item
        val length = max(
            (mCenterPosition + 1).toDouble(),
            (mVisibleItemCount - mCenterPosition).toDouble()
        ).toInt()
        var position: Int
        var start = min(length.toDouble(), mData!!.size.toDouble()).toInt()
        if (isDrawAllItem) {
            start = mData!!.size
        }
        // 上下两边
        for (i in start downTo 1) { // 先从远离中间位置的item绘制，当item内容偏大时，较近的item覆盖在较远的上面

            if (isDrawAllItem || i <= mCenterPosition + 1) {  // 上面的items,相对位置为 -i
                position = if (mSelected - i < 0)
                    mData!!.size + mSelected - i
                else
                    mSelected - i
                // 传入位置信息，绘制item
                if (isIsCirculation) {
                    drawItem(
                        canvas,
                        mData!!,
                        position,
                        -i,
                        mMoveLength,
                        centerPoint + mMoveLength - i * itemSize
                    )
                } else if (mSelected - i >= 0) { // 非循环滚动
                    drawItem(
                        canvas,
                        mData!!,
                        position,
                        -i,
                        mMoveLength,
                        centerPoint + mMoveLength - i * itemSize
                    )
                }
            }
            if (isDrawAllItem || i <= mVisibleItemCount - mCenterPosition) {  // 下面的items,相对位置为 i
                position = if (mSelected + i >= mData!!.size) (mSelected + i
                        - mData!!.size) else mSelected + i
                // 传入位置信息，绘制item
                if (isIsCirculation) {
                    drawItem(
                        canvas,
                        mData!!,
                        position,
                        i,
                        mMoveLength,
                        centerPoint + mMoveLength + i * itemSize
                    )
                } else if (mSelected + i < mData!!.size) { // 非循环滚动
                    drawItem(
                        canvas,
                        mData!!,
                        position,
                        i,
                        mMoveLength,
                        centerPoint + mMoveLength + i * itemSize
                    )
                }
            }
        }
        // 选中的item
        drawItem(canvas, mData!!, mSelected, 0, mMoveLength, centerPoint + mMoveLength)
    }

    /**
     * 绘制item
     *
     * @param canvas
     * @param data       　数据集
     * @param position   在data数据集中的位置
     * @param relative   相对中间item的位置,relative==0表示中间item,relative<0表示上（左）边的item,relative>0表示下(右)边的item
     * @param moveLength 中间item滚动的距离，moveLength<0则表示向上（右）滚动的距离，moveLength＞0则表示向下（左）滚动的距离
     * @param top        当前绘制item的坐标,当垂直滚动时为顶部y的坐标；当水平滚动时为item最左边x的坐标
     */
    abstract fun drawItem(
        canvas: Canvas,
        data: List<T>,
        position: Int,
        relative: Int,
        moveLength: Float,
        top: Float
    )

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    // 控件发生改变时调用. 初始化会被调用一次
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        reset()
    }

    private fun reset() {
        if (mCenterPosition < 0) {
            mCenterPosition = mVisibleItemCount / 2
        }

        if (mIsHorizontal) {
            itemHeight = measuredHeight
            itemWidth = measuredWidth / mVisibleItemCount

            centerY = 0
            centerX = mCenterPosition * itemWidth

            itemSize = itemWidth
            centerPoint = centerX
        } else {
            itemHeight = measuredHeight / mVisibleItemCount
            itemWidth = measuredWidth

            centerY = mCenterPosition * itemHeight
            centerX = 0

            itemSize = itemHeight
            centerPoint = centerY
        }

        if (mCenterItemBackground != null) {
            mCenterItemBackground!!.setBounds(
                centerX,
                centerY, centerX + itemWidth, centerY + itemHeight
            )
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isDisallowTouch) { // 不允许触摸
            return true
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> mSelectedOnTouch = mSelected
        }

        if (mGestureDetector.onTouchEvent(event)) {
            return true
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                if (mIsHorizontal) {
                    if (abs((event.x - mLastMoveX).toDouble()) < 0.1f) {
                        return true
                    }
                    mMoveLength += event.x - mLastMoveX
                } else {
                    if (abs((event.y - mLastMoveY).toDouble()) < 0.1f) {
                        return true
                    }
                    mMoveLength += event.y - mLastMoveY
                }
                mLastMoveY = event.y
                mLastMoveX = event.x
                checkCirculation()
                invalidate()
            }

            MotionEvent.ACTION_UP -> {
                mLastMoveY = event.y
                mLastMoveX = event.x
                if (mMoveLength == 0f) {
                    if (mSelectedOnTouch != mSelected) { //前后发生变化
                        notifySelected()
                    }
                } else {
                    moveToCenter() // 滚动到中间位置
                }
            }
        }
        return true
    }

    /**
     * @param curr
     * @param end
     */
    private fun computeScroll(curr: Int, end: Int, rate: Float) {
        if (rate < 1) { // 正在滚动
            if (mIsHorizontal) {
                // 可以把scroller看做模拟的触屏滑动操作，mLastScrollX为上次滑动的坐标
                mMoveLength = mMoveLength + curr - mLastScrollX
                mLastScrollX = curr
            } else {
                // 可以把scroller看做模拟的触屏滑动操作，mLastScrollY为上次滑动的坐标
                mMoveLength = mMoveLength + curr - mLastScrollY
                mLastScrollY = curr
            }
            checkCirculation()
            invalidate()
        } else { // 滚动完毕
            isMovingCenter = false
            mLastScrollY = 0
            mLastScrollX = 0

            // 直接居中，不通过动画
            mMoveLength = if (mMoveLength > 0) { //// 向下滑动
                if (mMoveLength < itemSize / 2) {
                    0f
                } else {
                    itemSize.toFloat()
                }
            } else {
                if (-mMoveLength < itemSize / 2) {
                    0f
                } else {
                    -itemSize.toFloat()
                }
            }
            checkCirculation()
            notifySelected()
            invalidate()
        }
    }

    override fun computeScroll() {
        if (mScroller.computeScrollOffset()) { // 正在滚动
            mMoveLength = if (mIsHorizontal) {
                // 可以把scroller看做模拟的触屏滑动操作，mLastScrollX为上次滑动的坐标
                mMoveLength + mScroller.currX - mLastScrollX
            } else {
                // 可以把scroller看做模拟的触屏滑动操作，mLastScrollY为上次滑动的坐标
                mMoveLength + mScroller.currY - mLastScrollY
            }
            mLastScrollY = mScroller.currY
            mLastScrollX = mScroller.currX
            checkCirculation() //　检测当前选中的item
            invalidate()
        } else { // 滚动完毕
            if (isFling) {
                isFling = false
                if (mMoveLength == 0f) { //惯性滑动后的位置刚好居中的情况
                    notifySelected()
                } else {
                    moveToCenter() // 滚动到中间位置
                }
            } else if (isMovingCenter) { // 选择完成，回调给监听器
                notifySelected()
            }
        }
    }

    fun cancelScroll() {
        mLastScrollY = 0
        mLastScrollX = 0
        isMovingCenter = false
        isFling = isMovingCenter
        mScroller.abortAnimation()
        stopAutoScroll()
    }

    // 检测当前选择的item位置
    private fun checkCirculation() {
        if (mMoveLength >= itemSize) { // 向下滑动
            // 该次滚动距离中越过的item数量
            val span = (mMoveLength / itemSize).toInt()
            mSelected -= span
            if (mSelected < 0) {  // 滚动顶部，判断是否循环滚动
                if (isIsCirculation) {
                    do {
                        mSelected = mData!!.size + mSelected
                    } while (mSelected < 0) // 当越过的item数量超过一圈时
                    mMoveLength = (mMoveLength - itemSize) % itemSize
                } else { // 非循环滚动
                    mSelected = 0
                    mMoveLength = itemSize.toFloat()
                    if (isFling) { // 停止惯性滑动，根据computeScroll()中的逻辑，下一步将调用moveToCenter()
                        mScroller.forceFinished(true)
                    }
                    if (isMovingCenter) { //  移回中间位置
                        scroll(mMoveLength, 0)
                    }
                }
            } else {
                mMoveLength = (mMoveLength - itemSize) % itemSize
            }
        } else if (mMoveLength <= -itemSize) { // 向上滑动
            // 该次滚动距离中越过的item数量
            val span = (-mMoveLength / itemSize).toInt()
            mSelected += span
            if (mSelected >= mData!!.size) { // 滚动末尾，判断是否循环滚动
                if (isIsCirculation) {
                    do {
                        mSelected = mSelected - mData!!.size
                    } while (mSelected >= mData!!.size) // 当越过的item数量超过一圈时
                    mMoveLength = (mMoveLength + itemSize) % itemSize
                } else { // 非循环滚动
                    mSelected = mData!!.size - 1
                    mMoveLength = -itemSize.toFloat()
                    if (isFling) { // 停止惯性滑动，根据computeScroll()中的逻辑，下一步将调用moveToCenter()
                        mScroller.forceFinished(true)
                    }
                    if (isMovingCenter) { //  移回中间位置
                        scroll(mMoveLength, 0)
                    }
                }
            } else {
                mMoveLength = (mMoveLength + itemSize) % itemSize
            }
        }
    }

    // 移动到中间位置
    private fun moveToCenter() {
        if (!mScroller.isFinished || isFling || mMoveLength == 0f) {
            return
        }
        cancelScroll()

        // 向下滑动
        if (mMoveLength > 0) {
            if (mIsHorizontal) {
                if (mMoveLength < itemWidth / 2) {
                    scroll(mMoveLength, 0)
                } else {
                    scroll(mMoveLength, itemWidth)
                }
            } else {
                if (mMoveLength < itemHeight / 2) {
                    scroll(mMoveLength, 0)
                } else {
                    scroll(mMoveLength, itemHeight)
                }
            }
        } else {
            if (mIsHorizontal) {
                if (-mMoveLength < itemWidth / 2) {
                    scroll(mMoveLength, 0)
                } else {
                    scroll(mMoveLength, -itemWidth)
                }
            } else {
                if (-mMoveLength < itemHeight / 2) {
                    scroll(mMoveLength, 0)
                } else {
                    scroll(mMoveLength, -itemHeight)
                }
            }
        }
    }

    // 平滑滚动
    private fun scroll(from: Float, to: Int) {
        if (mIsHorizontal) {
            mLastScrollX = from.toInt()
            isMovingCenter = true
            mScroller.startScroll(from.toInt(), 0, 0, 0)
            mScroller.finalX = to
        } else {
            mLastScrollY = from.toInt()
            isMovingCenter = true
            mScroller.startScroll(0, from.toInt(), 0, 0)
            mScroller.finalY = to
        }
        invalidate()
    }

    // 惯性滑动，
    private fun fling(from: Float, vel: Float) {
        if (mIsHorizontal) {
            mLastScrollX = from.toInt()
            isFling = true
            // 最多可以惯性滑动10个item
            mScroller.fling(
                from.toInt(), 0, vel.toInt(), 0, -10 * itemWidth,
                10 * itemWidth, 0, 0
            )
        } else {
            mLastScrollY = from.toInt()
            isFling = true
            // 最多可以惯性滑动10个item
            mScroller.fling(
                0, from.toInt(), 0, vel.toInt(), 0, 0, -10 * itemHeight,
                10 * itemHeight
            )
        }
        invalidate()
    }

    private fun notifySelected() {
        mMoveLength = 0f
        cancelScroll()
        if (listener != null) {
            // 告诉监听器选择完毕
            listener!!.onSelected(this@ScrollPickerView, mSelected)
        }
    }

    /**
     * 自动滚动(必须设置为可循环滚动)
     *
     * @param position
     * @param duration
     * @param speed    每毫秒移动的像素点
     */
    /**
     * 自动滚动
     *
     * @see ScrollPickerView.autoScrollFast
     */
    @JvmOverloads
    fun autoScrollFast(
        position: Int,
        duration: Long,
        speed: Float,
        interpolator: Interpolator? = sAutoScrollInterpolator
    ) {
        if (isAutoScrolling || !isIsCirculation) {
            return
        }
        cancelScroll()
        isAutoScrolling = true


        val length = (speed * duration).toInt()
        var circle = (length * 1f / (mData!!.size * itemSize) + 0.5f).toInt() // 圈数
        circle = if (circle <= 0) 1 else circle

        val aPlan = circle * (mData!!.size) * itemSize + (mSelected - position) * itemSize
        val bPlan = aPlan + (mData!!.size) * itemSize // 多一圈
        // 让其尽量接近length
        val end =
            if (abs((length - aPlan).toDouble()) < abs((length - bPlan).toDouble())) aPlan else bPlan

        mAutoScrollAnimator.cancel()
        mAutoScrollAnimator.setIntValues(0, end)
        mAutoScrollAnimator.interpolator = interpolator
        mAutoScrollAnimator.setDuration(duration)
        mAutoScrollAnimator.removeAllUpdateListeners()
        if (end != 0) { // itemHeight为0导致endy=0
            mAutoScrollAnimator.addUpdateListener { animation ->
                val rate = animation.currentPlayTime * 1f / animation.duration
                computeScroll(animation.animatedValue as Int, end, rate)
            }
            mAutoScrollAnimator.removeAllListeners()
            mAutoScrollAnimator.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    super.onAnimationEnd(animation)
                    isAutoScrolling = false
                }
            })
            mAutoScrollAnimator.start()
        } else {
            computeScroll(end, end, 1f)
            isAutoScrolling = false
        }
    }

    /**
     * 自动滚动，默认速度为 0.6dp/ms
     *
     * @see ScrollPickerView.autoScrollFast
     */
    fun autoScrollFast(position: Int, duration: Long) {
        val speed = dip2px(0.6f).toFloat()
        autoScrollFast(position, duration, speed, sAutoScrollInterpolator)
    }

    /**
     * 滚动到指定位置
     *
     * @param toPosition   　需要滚动到的位置
     * @param duration     　滚动时间
     * @param interpolator
     */
    fun autoScrollToPosition(toPosition: Int, duration: Long, interpolator: Interpolator?) {
        var toPosition = toPosition
        toPosition %= mData!!.size
        val endY = (mSelected - toPosition) * itemHeight
        autoScrollTo(endY, duration, interpolator, false)
    }

    /**
     * @param endY         　需要滚动到的位置
     * @param duration     　滚动时间
     * @param interpolator
     * @param canIntercept 能否终止滚动，比如触摸屏幕终止滚动
     */
    fun autoScrollTo(
        endY: Int,
        duration: Long,
        interpolator: Interpolator?,
        canIntercept: Boolean
    ) {
        if (isAutoScrolling) {
            return
        }
        val temp = isDisallowTouch
        isDisallowTouch = !canIntercept
        isAutoScrolling = true
        mAutoScrollAnimator.cancel()
        mAutoScrollAnimator.setIntValues(0, endY)
        mAutoScrollAnimator.interpolator = interpolator
        mAutoScrollAnimator.setDuration(duration)
        mAutoScrollAnimator.removeAllUpdateListeners()
        mAutoScrollAnimator.addUpdateListener { animation ->
            val rate = animation.currentPlayTime * 1f / animation.duration
            computeScroll(animation.animatedValue as Int, endY, rate)
        }
        mAutoScrollAnimator.removeAllListeners()
        mAutoScrollAnimator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                super.onAnimationEnd(animation)
                isAutoScrolling = false
                isDisallowTouch = temp
            }
        })
        mAutoScrollAnimator.start()
    }


    /**
     * 停止自动滚动
     */
    fun stopAutoScroll() {
        isAutoScrolling = false
        mAutoScrollAnimator.cancel()
    }

    var data: List<T>?
        get() = mData
        set(data) {
            if (data == null) {
                mData = ArrayList()
            } else {
                this.mData = data
            }
            mSelected = mData!!.size / 2
            invalidate()
        }

    val selectedItem: T
        get() = mData!![mSelected]

    var selectedPosition: Int
        get() = mSelected
        set(position) {
            if (position < 0 || position > mData!!.size - 1 || (position == mSelected && mHasCallSelectedListener)) {
                return
            }

            mHasCallSelectedListener = true
            mSelected = position
            invalidate()
            notifySelected()
        }

    fun setOnSelectedListener(listener: OnSelectedListener) {
        this.listener = listener
    }

    fun setIsCirculation(isCirculation: Boolean) {
        this.isIsCirculation = isCirculation
    }

    var visibleItemCount: Int
        get() = mVisibleItemCount
        set(visibleItemCount) {
            mVisibleItemCount = visibleItemCount
            reset()
            invalidate()
        }

    var centerPosition: Int
        /**
         * 中间item的位置,默认为 mVisibleItemCount / 2
         *
         * @return
         */
        get() = mCenterPosition
        /**
         * 中间item的位置，0 <= centerPosition <= mVisibleItemCount
         *
         * @param centerPosition
         */
        set(centerPosition) {
            mCenterPosition = if (centerPosition < 0) {
                0
            } else if (centerPosition >= mVisibleItemCount) {
                mVisibleItemCount - 1
            } else {
                centerPosition
            }
            centerY = mCenterPosition * itemHeight
            invalidate()
        }

    var centerItemBackground: Drawable?
        get() = mCenterItemBackground
        set(centerItemBackground) {
            mCenterItemBackground = centerItemBackground
            mCenterItemBackground!!.setBounds(
                centerX,
                centerY,
                centerX + itemWidth,
                centerY + itemHeight
            )
            invalidate()
        }

    fun setCenterItemBackground(centerItemBackgroundColor: Int) {
        mCenterItemBackground = ColorDrawable(centerItemBackgroundColor).apply{
            setBounds(
            centerX,
            centerY, centerX + itemWidth, centerY + itemHeight)
        }
        invalidate()
    }

    val isScrolling: Boolean
        get() = isFling || isMovingCenter || isAutoScrolling

    var isHorizontal: Boolean
        get() = mIsHorizontal
        set(horizontal) {
            if (mIsHorizontal == horizontal) {
                return
            }
            mIsHorizontal = horizontal
            reset()
            if (mIsHorizontal) {
                itemSize = itemWidth
            } else {
                itemSize = itemHeight
            }
            invalidate()
        }

    var isVertical: Boolean
        get() = !mIsHorizontal
        set(vertical) {
            if (mIsHorizontal == !vertical) {
                return
            }
            mIsHorizontal = !vertical
            reset()
            if (mIsHorizontal) {
                itemSize = itemWidth
            } else {
                itemSize = itemHeight
            }
            invalidate()
        }

    fun dip2px(dipVlue: Float): Int {
        val metrics = context.resources.displayMetrics
        val sDensity = metrics.density
        return (dipVlue * sDensity + 0.5f).toInt()
    }

    override fun setVisibility(visibility: Int) {
        super.setVisibility(visibility)
        if (visibility == VISIBLE) {
            moveToCenter()
        }
    }

    private class SlotInterpolator : Interpolator {
        override fun getInterpolation(input: Float): Float {
            return (cos((input + 1) * Math.PI) / 2.0f).toFloat() + 0.5f
        }
    }

    /**
     * 快速滑动时，惯性滑动一段距离
     *
     */
    private inner class FlingOnGestureListener : SimpleOnGestureListener() {
        private var mIsScrollingLastTime = false

        override fun onDown(e: MotionEvent): Boolean {
            if (isDisallowInterceptTouch) {  // 不允许父组件拦截事件
                val parent = parent
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            mIsScrollingLastTime = isScrolling // 记录是否从滚动状态终止
            // 点击时取消所有滚动效果
            cancelScroll()
            mLastMoveY = e.y
            mLastMoveX = e.x
            return true
        }

        override fun onFling(
            e1: MotionEvent?, e2: MotionEvent, velocityX: Float,
            velocityY: Float
        ): Boolean {
            // 惯性滑动
            if (isInertiaScroll) {
                cancelScroll()
                if (mIsHorizontal) {
                    fling(mMoveLength, velocityX)
                } else {
                    fling(mMoveLength, velocityY)
                }
            }
            return true
        }

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            mLastMoveY = e.y
            mLastMoveX = e.x
            var lastMove: Float
            if (isHorizontal) {
                centerPoint = centerX
                lastMove = mLastMoveX
            } else {
                centerPoint = centerY
                lastMove = mLastMoveY
            }
            if (isCanTap && !mIsScrollingLastTime) {
                if (lastMove >= centerPoint && lastMove <= centerPoint + itemSize) { //点击中间item，回调点击事件
                    performClick()
                } else if (lastMove < centerPoint) { // 点击两边的item，移动到相应的item
                    val move: Int = itemSize
                    autoScrollTo(move, 150, sAutoScrollInterpolator, false)
                } else { // lastMove > mCenterPoint + mItemSize
                    val move: Int = -itemSize
                    autoScrollTo(move, 150, sAutoScrollInterpolator, false)
                }
            } else {
                moveToCenter()
            }
            return true
        }
    }

    companion object {
        private val sAutoScrollInterpolator = SlotInterpolator()
    }
}

interface OnSelectedListener {
    fun onSelected(scrollPickerView: ScrollPickerView<*>?, position: Int)
}
