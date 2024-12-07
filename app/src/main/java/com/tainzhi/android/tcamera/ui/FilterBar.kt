package com.tainzhi.android.tcamera.ui

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
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
import com.tainzhi.android.tcamera.databinding.ActivityMainBinding
import com.tainzhi.android.tcamera.R
import com.tainzhi.android.tcamera.ui.FilterBar.Companion.NON_INIT_SELECTED
import com.tainzhi.android.tcamera.ui.FilterBar.Companion.TAG
import com.tainzhi.android.tcamera.util.SettingsManager

class FilterBar(val context: Context, val binding: ActivityMainBinding, private val onFilterTypeSelected: (type: FilterType) -> Unit) {
    private lateinit var inflatedView: View
    private lateinit var filterTypeTV: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var filterAdapter: FilterAdapter
    private val types =  mutableListOf<FilterType>()
    init {
        types.add(FilterType("Original", 0, 0))
        types.add(FilterType("Grey", 1, 0))
        types.add(FilterType("BlackWhite", 2, 0))
        types.add(FilterType("Reverse", 3, 0))
        types.add(FilterType("Brightness", 4, 0))
        types.add(FilterType("Posterization", 5, 0))
        types.add(FilterType("Amatorka", 10, R.raw.lut_filter_amatorka))
        types.add(FilterType("HighKey", 11, R.raw.lut_filter_highkey))
        types.add(FilterType("Purity", 12, R.raw.lut_filter_purity))
    }

    private var selectedTypePosition = NON_INIT_SELECTED
    private val snapHelper = LinearSnapHelper()
    private val scrollListener = object: RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            if (dx != 0) {
                snapHelper.findSnapView(recyclerView.layoutManager)?.let {
                    val position = recyclerView.getChildAdapterPosition(it)
                    updateStatus(position)
                }
            }
        }
    }
    private val filterView = binding.filter.apply {
        setOnClickListener {
            show()
        }
    }
    private fun updateStatus(position: Int) {
        selectedTypePosition = position
        filterTypeTV.text = types[position].name
        filterAdapter.setItemSelected(position)
        onFilterTypeSelected.invoke(types[position])
        if (selectedTypePosition != NON_INIT_SELECTED) {
            filterView.setImageResource(R.drawable.ic_filter_selected)
        } else {
            filterView.setImageResource(R.drawable.ic_filter)
        }
    }

    fun show() {
        filterView.visibility = View.GONE
        if (!this::inflatedView.isInitialized) {
            filterAdapter = FilterAdapter(types.map { FilterItem(it.name) }.toMutableList()).apply {
                    setOnItemClickListener { _,_, position ->
                        updateStatus(position)
                    }
                }
            inflatedView = binding.vsFilter.inflate()
            filterTypeTV = inflatedView.findViewById<TextView?>(R.id.tv_filter_type).apply {
                text = types[0].name
            }
            inflatedView.findViewById<AppCompatImageView>(R.id.iv_filter_close).setOnClickListener {
                hide()
            }
            inflatedView.findViewById<RecyclerView>(R.id.filter_recylerview).run {
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
            inflatedView.visibility = View.VISIBLE
            recyclerView.addOnScrollListener(scrollListener)
        }
    }

    fun hide() {
        recyclerView.removeOnScrollListener(scrollListener)
        inflatedView.visibility = View.GONE
        filterView.visibility = View.VISIBLE
    }

    fun resetEffect() {
        // todo
    }


    companion object {
        val TAG = FilterBar::class.java.simpleName
        const val NON_INIT_SELECTED = 0
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

class FilterAdapter(types: MutableList<FilterItem>) : BaseQuickAdapter<FilterItem, FilterViewHolder>(R.layout.item_filter, types) {
    private var selectedFilter = MutableLiveData(NON_INIT_SELECTED)
    override fun convert(holder: FilterViewHolder, item: FilterItem) {
        // holder.setText(R.id.tv_item_filter_type, item)
        holder.apply {
            index = holder.layoutPosition
            selected = selectedFilter
            addObservable()
        }
    }

    override fun onViewRecycled(holder: FilterViewHolder) {
        holder.removeObservable()
        super.onViewRecycled(holder)
    }

    fun setItemSelected(position: Int) {
        selectedFilter.value = position
    }
}

//data class FilterType(val name: String, val bitmap: Bitmap? = null)

// color filter is in [0, 9), lut filter is in [10, )
data class FilterType(val name: String, val tag: Int, val resId: Int)
