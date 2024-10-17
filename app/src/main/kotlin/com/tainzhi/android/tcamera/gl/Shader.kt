package com.tainzhi.android.tcamera.gl

import android.opengl.GLES20
import android.opengl.GLES30
import android.os.Build
import android.util.Log
import android.util.LongSparseArray
import android.util.SparseArray
import androidx.annotation.RawRes
import androidx.core.util.isEmpty
import androidx.core.util.isNotEmpty
import com.tainzhi.android.tcamera.App
import com.tainzhi.android.tcamera.BuildConfig
import com.tainzhi.android.tcamera.R
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer

class Shader(val vertexShaderSourceId: Int, val fragmentShaderSourceId: Int) {
    var programHandle = 0
    init {
        programHandle = ShaderCache.createProgram(vertexShaderSourceId, fragmentShaderSourceId)
        if (programHandle == 0) {
            programHandle = GlUtil.createProgram(
                GlUtil.getShaderSource(vertexShaderSourceId), GlUtil.getShaderSource(fragmentShaderSourceId))
            if (programHandle != 0) {
                ShaderCache.cacheProgram(vertexShaderSourceId, fragmentShaderSourceId, programHandle)
            }
        }
    }

    fun use() {
        GlUtil.useProgram(programHandle)
    }

}

enum class ShaderType(val vsId: Int, val fsId: Int) {
    CAMERA_PREVIEW(R.raw.preview_vs, R.raw.preview_fs),
    CAMERA_PREVIEW_BLUR(R.raw.preview_blur_vs, R.raw.preview_blur_fs),
    FRAME(R.raw.frame_vs, R.raw.frame_fs),
}

class ShaderFactory {
    private val shaders = SparseArray<Shader>(ShaderType.values().size)

    fun loadShaders() {
        ShaderType.values().forEach {
            shaders.append(it.ordinal, Shader(it.vsId, it.fsId))
        }
    }

    fun clearShaders() {
        shaders.clear()
    }

    fun isLoaded() = shaders.isNotEmpty()

    fun getShader(shaderType: ShaderType): Shader {
          if (shaders.isEmpty()) throw RuntimeException("Shaders is empty")
          return shaders[shaderType.ordinal]
    }

    companion object {
        private val TAG = ShaderFactory::class.java.name
    }
}

class ShaderCache {
    private val fileLock = Object()
    private val cache = LongSparseArray<ProgramBinary>()
    private var dirty = false
    private val cacheFile: File
        private get() {
            val file = File(App.getInstance().cacheDir, CACHE_FILE)
            return file
        }

    fun loadFromFile() {
        synchronized(fileLock) {
            if (cacheFile.exists()) {
                val _cache = readCache(cacheFile)
                if (_cache != null && _cache.size() > 0) {
                    synchronized(cache) {
                        for (i in 0 until _cache.size()) {
                            cache.append(_cache.keyAt(i), _cache.valueAt(i))
                        }
                    }
                } else {
                    Log.i(TAG, "loadFromFile: cache file is valid")
                    if(!cacheFile.delete()) {
                        Log.d(TAG, "loadFromFile: cache file could not be removed")
                    } else {
                        Log.d(TAG, "loadFromFile: cache file been removed")
                    }
                }
            } else {
                Log.i(TAG, "loadFromFile: cache file not found")
            }
        }
    }

    private fun saveToFile() {
        synchronized(fileLock) {
            var cacheSnapshot: LongSparseArray<ProgramBinary>? = null
            synchronized(cache) {
                if (dirty) {
                    Log.d(TAG, "saveToFile: cache is dirty")
                    if (cache.size() > 0) {
                        cacheSnapshot = cache.clone()
                    } else {
                        Log.d(TAG, "saveToFile: cache is empty")
                    }
                } else {
                    Log.d(TAG, "saveToFile: cache is clean")
                }
            }
            if (cacheSnapshot != null) {
                if (!writeCache(cacheFile, cacheSnapshot!!)) {
                    Log.d(TAG, "saveToFile: cache file could not be written")
                }
                if (cacheFile.exists()) {
                    if (!cacheFile.delete()) {
                        Log.d(TAG, "saveToFile: cache file could not be removed")
                    }
                }
            }
        }
    }

    companion object {
        private val TAG = ShaderCache::class.java.simpleName
        const val CACHE_VERSION = 1
        const val CACHE_FILE = "shader.bin"
        val FRAGMENT_SOURCE_ID_MASK:Long = 0x00000000FFFFFFFF

        private var INSTANCE: ShaderCache? = null


        @Synchronized
        fun getInstance(): ShaderCache {
            if (INSTANCE == null) {
                INSTANCE = ShaderCache()
            }
            return INSTANCE!!
        }

        fun load() {
            Thread({ getInstance().loadFromFile() }, TAG).start()
        }

        fun save() {
            Thread({ getInstance().saveToFile() }, TAG).start()
        }

        /*package*/
        fun createProgram(@RawRes vertexSourceId: Int, @RawRes fragmentSourceId: Int): Int {
            if (App.DEBUG) {
                val res = App.getInstance().resources
                Log.d(
                    TAG, String.format(
                        "createProgram vertexSourceId:%s fragmentSourceId:%s",
                        res.getResourceEntryName(vertexSourceId),
                        res.getResourceEntryName(fragmentSourceId)
                    )
                )
            }
            if (isNotSupported) {
                if (App.DEBUG) Log.d(TAG, "Cache is not supported")
                return 0
            }
            val key = makeKey(vertexSourceId, fragmentSourceId)
            var programBinary: ProgramBinary?
            synchronized(getInstance().cache) { programBinary = getInstance().cache[key] }
            if (App.DEBUG) Log.d(TAG, "Cache " + if (programBinary != null) "hit" else "miss")
            return if (programBinary != null) programBinary!!.createProgram() else 0
        }

        /*package*/
        fun cacheProgram(
            @RawRes vertexSourceId: Int, @RawRes fragmentSourceId: Int,
            program: Int
        ) {
            if (App.DEBUG) {
                val res = App.getInstance().resources
                Log.d(
                    TAG, String.format(
                        "cacheProgram vertexSourceId:%s fragmentSourceId:%s",
                        res.getResourceEntryName(vertexSourceId),
                        res.getResourceEntryName(fragmentSourceId)
                    )
                )
            }
            if (isNotSupported) {
                if (App.DEBUG) Log.d(TAG, "Cache is not supported")
                return
            }
            val key = makeKey(vertexSourceId, fragmentSourceId)
            if (App.DEBUG) Log.d(TAG, String.format("cacheProgram key:%x", key))
            val programBinary = ProgramBinary.createFromProgram(program)
            if (programBinary != null) {
                synchronized(getInstance().cache) {
                    getInstance().cache.append(key, programBinary)
                    getInstance().dirty = true
                }
            } else {
                if (App.DEBUG) Log.d(TAG, "Cache failed")
            }
        }

        private fun makeKey(@RawRes vertexSourceId: Int, @RawRes fragmentSourceId: Int): Long {
            return vertexSourceId.toLong() shl Integer.SIZE or
                    (fragmentSourceId.toLong() and FRAGMENT_SOURCE_ID_MASK)
        }

        private val isNotSupported: Boolean
            private get() = GlUtil.glVersion < 3

        private fun readCache(file: File): LongSparseArray<ProgramBinary>? {
            if (App.DEBUG) Log.d(TAG, "readCache file:$file")
            var dis: DataInputStream? = null
            try {
                dis = DataInputStream(BufferedInputStream(FileInputStream(file)))
                val header = CacheFileHeader(dis)
                if (header.isValid) {
                    val cache = LongSparseArray<ProgramBinary>(header.getCount())
                    for (i in 0 until header.getCount()) {
                        val key = dis.readLong()
                        val programBinary = ProgramBinary(dis)
                        cache.put(key, programBinary)
                    }
                    return cache
                } else {
                    if (App.DEBUG) Log.d(TAG, "Cache is invalid: $header")
                }
            } catch (e: IOException) {
                if (App.DEBUG) Log.d(TAG, "Cache file read error", e)
            } finally {
                dis?.close()
            }
            return null
        }

        private fun writeCache(
            file: File,
            cache: LongSparseArray<ProgramBinary>
        ): Boolean {
            if (App.DEBUG) Log.d(TAG, "writeCache file:" + file + " cache.size:" + cache.size())
            var dos: DataOutputStream? = null
            try {
                dos = DataOutputStream(BufferedOutputStream(FileOutputStream(file)))
                val header = CacheFileHeader(cache.size())
                header.write(dos)
                for (i in 0 until header.getCount()) {
                    dos.writeLong(cache.keyAt(i))
                    cache.valueAt(i).write(dos)
                }
                return true
            } catch (e: IOException) {
                if (App.DEBUG) Log.d(TAG, "Cache file write error", e)
            } finally {
                dos?.close()
            }
            return false
        }
    }

    class CacheFileHeader {
        private val version: Int
        private val versionCode: Int
        private val fingerprint: String
        private val count: Int

        constructor(count: Int) {
            version = CACHE_VERSION
            versionCode = BuildConfig.VERSION_CODE
            fingerprint = Build.FINGERPRINT
            this.count = count
            require(this.count > 0) { "count must be greater than 0" }
        }

        constructor(dis: DataInputStream) {
            version = dis.readInt()
            if (CACHE_VERSION != version) {
                throw IOException("Version mismatch: $version")
            }
            versionCode = dis.readInt()
            fingerprint = dis.readUTF()
            count = dis.readInt()
        }

        @Throws(IOException::class)
        fun write(dos: DataOutputStream) {
            dos.writeInt(version)
            dos.writeInt(versionCode)
            dos.writeUTF(fingerprint)
            dos.writeInt(count)
        }

        val isValid: Boolean
            get() = BuildConfig.VERSION_CODE == versionCode && Build.FINGERPRINT == fingerprint && count > 0

        fun getCount() = count

        override fun toString(): String {
            return "CacheFileHeader{version=" + version + ", versionCode=" + versionCode +
                    ", fingerprint=" + fingerprint + ", count=" + count + "}"
        }
    }

    class ProgramBinary {
        private var format: Int = 0
        private var binary: ByteBuffer

        constructor(format: Int, binary: ByteBuffer) {
            this.format = format
            this.binary = binary
        }

        constructor(dis: DataInputStream) {
            this.format = dis.readInt()
            this.binary = ByteBuffer.allocate(dis.readInt())
            dis.readFully(binary.array(), 0, binary.limit())
        }


        fun createProgram():Int {
            val program = GLES20.glCreateProgram()
            if (program != 0) {
                GLES30.glProgramBinary(program, format, binary.rewind(), binary.limit())
                val linkedStatus: IntArray = IntArray(1)
                GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkedStatus, 0)
                if (linkedStatus[0] != GLES20.GL_TRUE) {
                    GLES30.glDeleteProgram(program)
                }
            }
            return program
        }

        fun write(dos: DataOutputStream) {
            dos.writeInt(format)
            dos.writeInt(binary.limit())
            dos.write(binary.array(), 0, binary.limit())
        }

        companion object {
            fun createFromProgram(program: Int): ProgramBinary? {
                val length: IntArray = IntArray(1)
                GLES20.glGetProgramiv(program, GLES30.GL_PROGRAM_BINARY_LENGTH, length, 0)
                if (length[0] > 0) {
                    val format = IntArray(1)
                    val binary = ByteBuffer.allocate(length[0])
                    GLES30.glGetProgramBinary(program, length[0], null, 0, format, 0, binary)
                    return ProgramBinary(format[0], binary)
                }
                return null
            }
        }
    }
}
