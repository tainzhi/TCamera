package com.tainzhi.android.tcamera.util

import android.content.Context
import android.os.Build.VERSION_CODES
import android.view.OrientationEventListener
import android.view.Surface
import androidx.annotation.RequiresApi
import kotlin.math.abs
import kotlin.math.min

class RotationChangeMonitor(context: Context): OrientationEventListener(context) {

    @RequiresApi(VERSION_CODES.R)
    // already set sensorOrientation=portrait in AndroidManifest.xml
    // so displayOrientation is always 0
    private var displayOrientation: Int = run {
        val angle = when (context.display?.rotation ?: 0) {
            Surface.ROTATION_0 -> 0
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }
        angle
    }
    private var currentOrientation = 0
    private val ORIENTATION_HYSTERSIS = 15
    var rotationChangeListener: RotationChangeListener? = null

    override fun onOrientationChanged(orientation: Int) {
        if (orientation == ORIENTATION_UNKNOWN) return
        val newOrientation = roundOrientation(orientation, currentOrientation)
        if (newOrientation != currentOrientation) {
            rotationChangeListener?.onRotateChange(currentOrientation, newOrientation)
            currentOrientation = newOrientation
        }
    }

    private fun roundOrientation(orientation: Int, orientationHistory: Int): Int {
        var changeOrientation = false
        if (orientationHistory == ORIENTATION_UNKNOWN) {
            changeOrientation = true
        } else {
            var delta: Int = abs(orientation - orientationHistory)
            delta = min(delta, 360 - delta)
            changeOrientation = delta >= (45 + ORIENTATION_HYSTERSIS)
        }
        if (changeOrientation) {
            return ((orientation + 45) / 90 * 90) % 360
        }
        return orientationHistory
    }
}

interface RotationChangeListener {
    fun onRotateChange(oldOrientation: Int, newOrientation: Int)
}

