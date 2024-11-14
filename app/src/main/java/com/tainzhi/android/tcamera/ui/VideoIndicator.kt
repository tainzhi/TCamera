package com.tainzhi.android.tcamera.ui

import android.content.Context
import android.text.format.DateUtils
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.tainzhi.android.tcamera.R
import com.tainzhi.android.tcamera.databinding.ActivityMainBinding

/**
 * @author:      tainzhi
 * @mail:        qfq61@qq.com
 * @date:        2024/11/14 18:03
 * @description:
 **/

class VideoIndicator(
    val context: Context,
    private val binding: ActivityMainBinding,
) {
    private lateinit var inflatedView: View
    private lateinit var ivIndicator: ImageView
    private lateinit var tvVideoLength: TextView
    private var startTime = 0L
    private val SECOND_IN_MILLIS= 1000L
    private val counterRunnable by lazy { CounterRunnable()}

    fun show() {
        if (!::inflatedView.isInitialized) {
            inflatedView = binding.vsVideoRecordingIndicator.inflate()
            ivIndicator = inflatedView.findViewById(R.id.iv_video_recording_indicator)
            tvVideoLength = inflatedView.findViewById(R.id.tv_video_recording_indicator)
        }
        inflatedView.visibility = View.VISIBLE
        ivIndicator.visibility = View.GONE
        tvVideoLength.text = "00:00"
    }

    fun start() {
        startTime = System.currentTimeMillis()
        ivIndicator.visibility = View.VISIBLE
        tvVideoLength.text = "00:00"
        inflatedView.postDelayed(counterRunnable, SECOND_IN_MILLIS)
    }

    fun stop() {
        inflatedView.removeCallbacks(counterRunnable)
        ivIndicator.visibility = View.GONE
        tvVideoLength.text = "00:00"
    }

    fun hide() {
        if (::inflatedView.isInitialized) {
            inflatedView.visibility = View.GONE
        }
    }

    private inner class CounterRunnable : Runnable {
        override fun run() {
            // in milliseconds
            val videoLength = System.currentTimeMillis() - startTime
            val counter = DateUtils.formatElapsedTime(null, videoLength / SECOND_IN_MILLIS)
            tvVideoLength.text = counter
            inflatedView.postDelayed(this,SECOND_IN_MILLIS - (videoLength % SECOND_IN_MILLIS) + 10L)
        }

    }
}