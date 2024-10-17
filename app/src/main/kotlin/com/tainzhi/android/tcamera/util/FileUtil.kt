package com.tainzhi.android.tcamera.util

import android.media.Image
import android.util.Log
import com.tainzhi.android.tcamera.App
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
/**
 * @author:      tainzhi
 * @mail:        qfq61@qq.com
 * @date:        2024/10/11 21:07
 * @description:
 **/



fun ByteBuffer.toByteArray(): ByteArray {
    // 如果ByteBuffer有数组支持，并且当前位置是0，限制等于容量，我们可以直接使用其数组
    if (hasArray() && this.position() == 0 && this.limit() == this.capacity()) {
        return array().copyOfRange(0, capacity())
    }

    // 否则，我们需要创建一个新的数组，并将ByteBuffer的内容复制到其中
    val data = ByteArray(remaining())
    // 注意：这里我们使用了remaining()，它返回从当前位置到限制之间的元素数
    this.get(data) // 这将ByteBuffer的当前位置到限制之间的内容复制到data数组中
    // 如果你需要，可以在这里重置ByteBuffer的位置，例如：this.position(0)

    return data
}

object FileUtil {
    private const val TAG = "FileUtil"

}