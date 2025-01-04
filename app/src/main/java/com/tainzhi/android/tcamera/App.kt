package com.tainzhi.android.tcamera

import android.app.Activity
import android.app.Application
import android.app.Application.ActivityLifecycleCallbacks
import android.os.Bundle
import android.util.Log
import com.tainzhi.android.tcamera.gl.ShaderCache
import com.tainzhi.android.tcamera.util.SettingsManager

class App: Application(), ActivityLifecycleCallbacks {

    override fun onCreate() {
        super.onCreate()
        INSTANCE = this
        registerActivityLifecycleCallbacks(this)
        ShaderCache.load()
        ImageProcessor.instance.create()
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
    }

    override fun onActivityStarted(activity: Activity) {
    }

    override fun onActivityResumed(activity: Activity) {
    }

    override fun onActivityPaused(activity: Activity) {
        SettingsManager.instance.commit()
    }

    override fun onActivityStopped(activity: Activity) {
        Log.d(TAG, "onActivityStopped: ")
        if (activity is MainActivity) {
            ShaderCache.save()
        }
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
    }

    override fun onActivityDestroyed(activity: Activity) {
        Log.d(TAG, "onActivityDestroyed: ");
        ImageProcessor.instance.destroy()
    }

    companion object {
        private val TAG = App::class.java.simpleName
        @Volatile private lateinit var INSTANCE: App
        fun getInstance() = INSTANCE

        fun getCachePath(): String {
            return getInstance().applicationContext.cacheDir.path
        }

        val DEBUG: Boolean = BuildConfig.DEBUG
    }

}