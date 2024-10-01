package com.tainzhi.android.tcamera.gl.textures

import android.graphics.RectF
import android.util.Log
import com.tainzhi.android.tcamera.gl.ShaderFactory

class TextureManager {

    private val shaderFactory = ShaderFactory()
    private val textures = mutableListOf<TextureBase>()

    var previewRectF: RectF = RectF()
    var isReady = false

    fun addTextures(textures: List<TextureBase>) {
        Log.d(TAG, "addTextures: ")
        this.textures.addAll(textures)
    }

    // cannot be called in createContext() like unloadTextures()
    fun load() {
        Log.d(TAG, "load: ")
    }

    fun onDraw() {
        if (textures.size > 0 && !isReady) {
            shaderFactory.loadShaders()
            Log.d(TAG, "onDraw: loadShaders.size=${textures.size}")
            textures.forEach { it.load(shaderFactory) }
        }
        if (isReady) {
            textures.forEach { it.draw() }
        }
    }

    fun unload() {
        Log.d(TAG, "unload: ")
        isReady = false
        textures.forEach {
            it.unload()
        }
        shaderFactory.clearShaders()
    }

    fun setMatrix(model: FloatArray, view: FloatArray, projection:FloatArray) {
        textures.forEach {
            it.setMatrix(model, view, projection)
        }

    }

    companion object {
        private val TAG = TextureManager::class.java.simpleName
    }
}