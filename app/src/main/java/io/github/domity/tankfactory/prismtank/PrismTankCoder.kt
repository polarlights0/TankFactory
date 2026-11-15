package io.github.domity.tankfactory.prismtank

import android.graphics.Bitmap
import android.util.Log

object PrismTankCoder {

    private const val TAG = "PrismTankCoder"

    /** 解码时的区间外填充方式，取值与 native 侧一致 */
    object DecodeMethod {
        const val BLACK = 0
        const val WHITE = 1
        const val TRANSPARENT = 2
        const val COPY_LEFT = 3
        const val COPY_TOP = 4
        const val LTAVG = 5
    }

    /** 原生库是否可用；不可用时上层显示错误提示而不是闪退 */
    @Volatile
    var isNativeAvailable: Boolean = false
        private set

    private var loadError: String? = null

    init {
        isNativeAvailable = try {
            System.loadLibrary("tankfactory")
            true
        } catch (t: Throwable) {
            loadError = t.message ?: t.javaClass.simpleName
            Log.e(TAG, "loadLibrary(tankfactory) failed", t)
            false
        }
    }

    fun unavailableReason(): String? = loadError

    /**
     * 光棱坦克合成：inner/cover/out 必须尺寸一致，out 为可写位图
     */
    private external fun encodeNative(
        innerBitmap: Bitmap,
        coverBitmap: Bitmap,
        outputBitmap: Bitmap,
        innerThreshold: Int,
        coverThreshold: Int,
        innerContrast: Int,
        coverContrast: Int,
        innerGray: Boolean,
        coverGray: Boolean,
        slope: Int,
        gap: Int,
        isRow: Boolean,
        isReverse: Boolean
    ): Boolean

    /**
     * 光棱坦克显形：返回解码后的新位图
     */
    private external fun decodeNative(
        sourceBitmap: Bitmap,
        lowerThreshold: Int,
        higherThreshold: Int,
        method: Int,
        contrast: Int,
        iterations: Int
    ): Bitmap?

    fun encode(
        innerBitmap: Bitmap,
        coverBitmap: Bitmap,
        outputBitmap: Bitmap,
        innerThreshold: Int,
        coverThreshold: Int,
        innerContrast: Int,
        coverContrast: Int,
        innerGray: Boolean,
        coverGray: Boolean,
        slope: Int,
        gap: Int,
        isRow: Boolean,
        isReverse: Boolean
    ): Boolean {
        if (!isNativeAvailable) return false
        return try {
            encodeNative(
                innerBitmap, coverBitmap, outputBitmap,
                innerThreshold, coverThreshold, innerContrast, coverContrast,
                innerGray, coverGray, slope, gap, isRow, isReverse
            )
        } catch (t: Throwable) {
            Log.e(TAG, "encodeNative failed", t)
            false
        }
    }

    fun decode(
        sourceBitmap: Bitmap,
        lowerThreshold: Int,
        higherThreshold: Int,
        method: Int,
        contrast: Int,
        iterations: Int
    ): Bitmap? {
        if (!isNativeAvailable) return null
        return try {
            decodeNative(sourceBitmap, lowerThreshold, higherThreshold, method, contrast, iterations)
        } catch (t: Throwable) {
            Log.e(TAG, "decodeNative failed", t)
            null
        }
    }
}
