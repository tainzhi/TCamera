package com.tainzhi.android.tcamera.ui

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.media.Image
import android.util.DisplayMetrics
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.viewholder.BaseViewHolder
import com.tainzhi.android.tcamera.App
import com.tainzhi.android.tcamera.ImageProcessor
import com.tainzhi.android.tcamera.R
import com.tainzhi.android.tcamera.databinding.ActivityMainBinding
import com.tainzhi.android.tcamera.ui.FilterBar.Companion.NON_INIT_SELECTED
import com.tainzhi.android.tcamera.ui.FilterBar.Companion.TAG
import com.tainzhi.android.tcamera.util.Kpi
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class FilterTag(val value: Int) {
    ORIGINAL(0),
    GREY(1),
    BLACK_WHITE(2),
    REVERSE(3),
    BRIGHTNESS(4),
    POSTERIZATION(5),

    // non-lut < 10, lut filter >= 10
    AMATORKA(10),
    BEAGLE(11),
    BIRMAN(12),
    CORGIS(13),
    HIGH_KEY(14),
    LABRADOR(15),
    MAINE(16),
    MONO(17),
    PERSIAN(18),
    POODLE(19),
    PUG(20),
    PURITY(21),
    SHORT_HAIR(22),
    SIAMESE(23),
    VERTICAL(24)
}


class FilterBar(val context: Context, val binding: ActivityMainBinding, private val onFilterTypeSelected: (type: FilterType) -> Unit) {
    private lateinit var filterTypeTV: TextView
    private var inflatedView: View? = null
    private var recyclerView: RecyclerView? = null
    private lateinit var filterAdapter: FilterAdapter
    private val types =  mutableListOf<FilterType>()
    private var shouldUpdateAllThumbnails = false
    init {

        types.add(FilterType("Original", FilterTag.ORIGINAL.value, 0))
        types.add(FilterType("Grey", FilterTag.GREY.value, 0))
        types.add(FilterType("BlackWhite", FilterTag.BLACK_WHITE.value, 0))
        types.add(FilterType("Reverse", FilterTag.REVERSE.value, 0))
        types.add(FilterType("Brightness", FilterTag.BRIGHTNESS.value, 0))
        types.add(FilterType("Posterization", FilterTag.POSTERIZATION.value, 0))
        types.add(FilterType("Amatorka", FilterTag.AMATORKA.value, R.raw.lut_amatorka))
        types.add(FilterType("Beagle", FilterTag.BEAGLE.value, R.raw.lut_beagle))
        types.add(FilterType("Birman", FilterTag.BIRMAN.value, R.raw.lut_birman))
        types.add(FilterType("Corgis", FilterTag.CORGIS.value, R.raw.lut_corgis))
        types.add(FilterType("HighKey", FilterTag.HIGH_KEY.value, R.raw.lut_highkey))
        types.add(FilterType("Labrador", FilterTag.LABRADOR.value, R.raw.lut_labrador))
        types.add(FilterType("Maine", FilterTag.MAINE.value, R.raw.lut_maine))
        types.add(FilterType("Mono", FilterTag.MONO.value, R.raw.lut_mono))
        types.add(FilterType("Persian", FilterTag.PERSIAN.value, R.raw.lut_persian))
        types.add(FilterType("Poodle", FilterTag.POODLE.value, R.raw.lut_poodle))
        types.add(FilterType("Pug", FilterTag.PUG.value, R.raw.lut_pug))
        types.add(FilterType("Purity", FilterTag.PURITY.value, R.raw.lut_purity))
        types.add(FilterType("ShortHair", FilterTag.SHORT_HAIR.value, R.raw.lut_shorthair))
        types.add(FilterType("Siamese", FilterTag.SIAMESE.value, R.raw.lut_siamese))
        types.add(FilterType("Vertical", FilterTag.VERTICAL.value, R.raw.lut_vertical))
    }
    private val lutBitmaps = mutableListOf<Bitmap?>()
    private var needLoadLuts = false
    private var filterChooserShow = false

    private var selectedTypePosition = NON_INIT_SELECTED
    private val snapHelper = LinearSnapHelper()
    private val scrollListener = object: RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            if (dx != 0) {
                snapHelper.findSnapView(recyclerView.layoutManager)?.let {
                    val position = recyclerView.getChildAdapterPosition(it)
                    updateTriggerStatus(position)
                }
            }
        }

        override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
            when (newState) {
                RecyclerView.SCROLL_STATE_IDLE -> {
                    shouldUpdateAllThumbnails = false
                }
                RecyclerView.SCROLL_STATE_DRAGGING -> {
                    shouldUpdateAllThumbnails = true
                }
                RecyclerView.SCROLL_STATE_SETTLING -> {
                    shouldUpdateAllThumbnails = true
                }
            }
        }
    }
    private val filterTrigger = binding.ivFilterTrigger.apply {
        setOnClickListener {
            showFilterChooser()
        }
    }
    private fun updateTriggerStatus(position: Int) {
        selectedTypePosition = position
        filterTypeTV.text = types[position].name
        filterAdapter.setItemSelected(position)
        onFilterTypeSelected.invoke(types[position])
        if (selectedTypePosition != NON_INIT_SELECTED) {
            filterTrigger.setImageResource(R.drawable.ic_filter_selected)
        } else {
            // 选择了filter，那么就需要重新加载lut
            filterTrigger.setImageResource(R.drawable.ic_filter)
            needLoadLuts = true
        }
    }

    fun showFilterChooser() {
        Log.d(TAG, "showFilterChooser: ")
        filterTrigger.visibility = View.GONE
        configureFilterThumbnails()
        if (inflatedView == null) {
            filterAdapter = FilterAdapter(types.map { FilterItem(it.name) }.toMutableList()).apply {
                    setOnItemClickListener { _,_, position ->
                        updateTriggerStatus(position)
                    }
                }
            inflatedView = binding.vsFilter.inflate()
            filterTypeTV = inflatedView!!.findViewById<TextView?>(R.id.tv_filter_type).apply {
                text = types[0].name
            }
            inflatedView!!.findViewById<AppCompatImageView>(R.id.iv_filter_close).setOnClickListener {
                hideFilterChooser()
            }
            inflatedView!!.findViewById<RecyclerView>(R.id.filter_recylerview).run {
                recyclerView = this
                addOnScrollListener(scrollListener)
                snapHelper.attachToRecyclerView(this)
                addItemDecoration(object :
                    DividerItemDecoration(context, HORIZONTAL) {
                    override fun getItemOffsets(
                        outRect: Rect,
                        view: View,
                        parent: RecyclerView,
                        state: RecyclerView.State
                    ) {
                        val edgeMargin =
                            ((context as Activity).windowManager.currentWindowMetrics.bounds.width()
                                    - context.resources.getDimension(R.dimen.camera_filter_item_width)) / 2
                        val position = parent.getChildAdapterPosition(view)
                        // center first item
                        if (position == 0) {
                            outRect.set(edgeMargin.toInt(), 0, 5, 0)
                            // center last item
                        } else if (position == state.itemCount - 1) {
                            outRect.set(0, 0, edgeMargin.toInt(), 0)
                        } else {
                            outRect.set(0, 0, 5, 0)
                        }
                    }
                })
                layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
                adapter = filterAdapter

            }
        } else {
            inflatedView!!.visibility = View.VISIBLE
            recyclerView?.addOnScrollListener(scrollListener)
        }
        filterChooserShow = true
    }

    private fun configureFilterThumbnails() {
        Log.d(TAG, "configureFilterThumbnails: ")
        Kpi.start(Kpi.TYPE.CONFIGURE_FILTER_THUMBNAIL)
        val thumbnailSize = getThumbnailSize()
        val bitmapOptions = BitmapFactory.Options().apply {
            inScaled = false
        }
        types.forEach { t ->
            t.thumbnailBitmap = Bitmap.createBitmap(thumbnailSize, thumbnailSize, Bitmap.Config.ARGB_8888)
            // 只有第一次configure时才需要加载lut到 native
            if (!needLoadLuts) {
                if (t.tag < 10) {
                    lutBitmaps.add(null)
                } else {
                    val bitmap = BitmapFactory.decodeResource(
                        App.getInstance().resources,
                        t.resId,
                        bitmapOptions
                    )
                    if (bitmap == null) {
                        Log.e(TAG, "load bitmap from lut ${t.name} failed: bitmap is null")
                    }
                    lutBitmaps.add(bitmap)
                }
            }
        }
        Log.d(TAG, "configureFilterThumbnails: types.size:${types.size}")
        ImageProcessor.instance.configureFilterThumbnails(
            thumbnailSize, thumbnailSize, types.map{it.name}, types.map { it.tag}, types.map{it.thumbnailBitmap}, lutBitmaps
        )
        needLoadLuts = true
        Kpi.end(Kpi.TYPE.CONFIGURE_FILTER_THUMBNAIL)
    }

    fun processThumbnails(image: Image, orientation: Int) {
        if (filterChooserShow) {
            Log.d(TAG, "processThumbnails: ")
            Kpi.start(Kpi.TYPE.PROCESS_FILTER_THUMBNAIL)
            // 只更新部分的缩略图，降低资源占用而且减少计算量提升速度
            var updateRangeStart = 0
            var updateRangeEnd = types.size - 1
            if (!shouldUpdateAllThumbnails) {
                updateRangeStart = max(selectedTypePosition - RECYCLERVIEW_CACHE_SIZE / 2, 0)
                updateRangeEnd = min(selectedTypePosition + RECYCLERVIEW_CACHE_SIZE / 2, types.size - 1)
            }
            if (ImageProcessor.instance.processFilterThumbnails(image, orientation, updateRangeStart, updateRangeEnd)) {
                filterAdapter.updateFilterEffectBitmaps(types.map { it.thumbnailBitmap } as List<Bitmap?>)
            } else {
                Log.e(TAG, "processThumbnails: failed to process thumbnails")
            }
            Kpi.start(Kpi.TYPE.PROCESS_FILTER_THUMBNAIL)
        }
    }

    fun hideFilterChooser() {
        recyclerView?.removeOnScrollListener(scrollListener)
        inflatedView?.visibility = View.GONE
        filterTrigger.visibility = View.VISIBLE
        filterChooserShow = false
        resetEffect()
    }

    fun resetEffect() {
        if (App.DEBUG) {
            Log.d(TAG, "resetEffect: ")
        }
        if (types.isNotEmpty() && needLoadLuts  ) {
            ImageProcessor.instance.clearFilterThumbnails(types[selectedTypePosition].tag)
        }
        types.forEach { t ->
            {
                t.thumbnailBitmap?.recycle()
                t.thumbnailBitmap = null
            }
        }
        lutBitmaps.forEach { t ->
            {
                t?.recycle()
            }
        }
    }

    fun showTrigger() {
        if (App.DEBUG) {
            Log.d(TAG, "showTrigger: ")
        }
        filterTrigger.visibility = View.VISIBLE
    }

    fun hideTrigger() {
        if (App.DEBUG) {
            Log.d(TAG, "hideTrigger: ")
        }
        filterTrigger.visibility = View.GONE
    }

    private fun getThumbnailSize(): Int {
        val dpi = context.resources.displayMetrics.densityDpi
        val dp = context.resources.getDimension(R.dimen.camera_filter_item_width)
        var size = (dp * ((dpi.toFloat() / DisplayMetrics.DENSITY_DEFAULT))).roundToInt()
        // make to even
        return size + size % 2;
    }


    companion object {
        val TAG = FilterBar::class.java.simpleName
        const val NON_INIT_SELECTED = 0
        const val RECYCLERVIEW_CACHE_SIZE = 8
    }
}

data class FilterItem(val name: String)

class FilterViewHolder(itemView: View): BaseViewHolder(itemView) {
    private val imageView = itemView.findViewById<AppCompatImageView>(R.id.image_item_filter)
    var bitmap: LiveData<Bitmap>? = null
    private val bitmapObserver = Observer<Bitmap> { imageView.setImageBitmap(it)}
    var selected: LiveData<Int>? = null
    var index: Int? = null
    private val selectedObserver = Observer<Int> {
        if (index == it) {
            imageView.background = ContextCompat.getDrawable(imageView.context, R.drawable.border)
            itemView.requestFocus()
        } else {
            imageView.background = null
        }
    }

    fun addObservable() {
        Log.d(TAG, "addObservable: ")
        bitmap?.observeForever(bitmapObserver)
        selected?.observeForever(selectedObserver)
    }

    fun removeObservable() {
        Log.d(TAG, "removeObservable: ")
        bitmap?.removeObserver(bitmapObserver)
        selected?.removeObserver(selectedObserver)
    }
}

class FilterAdapter(val types: MutableList<FilterItem>) : BaseQuickAdapter<FilterItem, FilterViewHolder>(R.layout.item_filter, types) {
    private val selectedIndex = MutableLiveData(NON_INIT_SELECTED)
    private val bitmapsLiveData = Array(types.size) { MutableLiveData<Bitmap>() }
    override fun convert(holder: FilterViewHolder, item: FilterItem) {
        holder.apply {
            index = holder.layoutPosition
            selected = selectedIndex
            bitmap = bitmapsLiveData[holder.layoutPosition]
            addObservable()
        }
    }

    override fun onViewRecycled(holder: FilterViewHolder) {
        holder.removeObservable()
        super.onViewRecycled(holder)
    }

    fun setItemSelected(position: Int) {
        selectedIndex.value = position
    }

    fun updateFilterEffectBitmaps(bitmaps: List<Bitmap?>) {
        recyclerView?.post {
            Log.d(TAG, "updateFilterEffectBitmaps: bitmapSize:${bitmaps.size}")
            for (i in 0 until bitmaps.size) {
                bitmapsLiveData[i].value = bitmaps[i]
            }
        }
    }
}


// color filter tag is in [0, 9), lut filter is in [10, )
data class FilterType(val name: String, val tag: Int, val resId: Int, var thumbnailBitmap: Bitmap? = null)
