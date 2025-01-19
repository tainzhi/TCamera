package com.tainzhi.android.tcamera.util

import android.os.SystemClock
import android.util.Log

object Kpi {

    private const val TAG = "KPI"
    private val kpis = mutableMapOf<TYPE, Long>()

    enum class TYPE {
        OPEN_CAMERA_TO_PREVIEW, // open camera to first preview frame show
        SHOT_TO_SHOT,
        SHOT_TO_SAVE_IMAGE,
        PROCESSED_IMAGE_TO_REPLACE_JPEG_IMAGE,
        IMAGE_TO_THUMBNAIL,

        // filter
        CONFIGURE_FILTER_THUMBNAIL,
        PROCESS_FILTER_THUMBNAIL,
    }

    fun start(type: TYPE) {
        kpis.put(type, SystemClock.elapsedRealtime())
    }

    fun end(type: TYPE) {
        end(type, SystemClock.elapsedRealtime())
    }

    private fun end(type: TYPE, endTime: Long): Long {
        val startTime = kpis[type]
        if (startTime != null) {
            val diff = endTime - startTime
            computeKpi(type, diff)
            kpis.remove(type)
            return diff
        } else {
            return 0L
        }
    }

    @Synchronized
    private fun computeKpi(type: TYPE, time: Long) {
       Log.i(TAG, "$type : $time")
    }
}