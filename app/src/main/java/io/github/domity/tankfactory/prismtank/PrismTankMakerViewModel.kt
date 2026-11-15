package io.github.domity.tankfactory.prismtank

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.domity.tankfactory.R
import io.github.domity.tankfactory.ui.components.ImageFormat
import io.github.domity.tankfactory.ui.components.saveImageToDownload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.max
import kotlin.math.roundToInt

class PrismTankMakerViewModel(application: Application) : AndroidViewModel(application) {

    private companion object {
        const val TAG = "PrismTankMaker"
    }

    /** 光棱坦克参数，语义与 Mirage_Decode 一致 */
    data class Params(
        val innerThreshold: Int = 24,
        val coverThreshold: Int = 42,
        val innerContrast: Int = 0,
        val coverContrast: Int = 0,
        val innerGray: Boolean = false,
        val coverGray: Boolean = true,
        val slope: Int = 1,
        val gap: Int = 1,
        val isRow: Boolean = true,
        val isReverse: Boolean = false,
        val maxSize: Int = 1200
    )

    private val _selectedCoverUri = MutableStateFlow<Uri?>(null)
    val selectedCoverUri: StateFlow<Uri?> = _selectedCoverUri

    private val _selectedInnerUri = MutableStateFlow<Uri?>(null)
    val selectedInnerUri: StateFlow<Uri?> = _selectedInnerUri

    private val previewSize = 600
    private var previewInnerBmp: Bitmap? = null
    private var previewCoverBmp: Bitmap? = null
    private var previewOutputBmp: Bitmap? = null

    private var lastParams: Params? = null
    private val workMutex = Mutex()
    private var bitmapVersion = 0L
    private var latestEncodeRequest = 0L

    private val _previewTrigger = MutableStateFlow(0)
    val previewTrigger: StateFlow<Int> = _previewTrigger

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving

    fun setCoverImageUri(uri: Uri) {
        _selectedCoverUri.value = uri
        preparePreviewEnvironment()
    }

    fun setInnerImageUri(uri: Uri) {
        _selectedInnerUri.value = uri
        preparePreviewEnvironment()
    }

    fun onMakerScreenEntered() {
        _selectedCoverUri.value = null
        _selectedInnerUri.value = null
        invalidatePreviews()
        viewModelScope.launch(Dispatchers.IO) {
            workMutex.withLock { recycleBitmapsNow() }
        }
    }

    fun previewBitmap(): Bitmap? = previewOutputBmp

    private fun invalidatePreviews() {
        bitmapVersion++
        latestEncodeRequest++
    }

    /** 只解除引用，交给 GC 释放；Compose 可能还在绘制旧位图，直接 recycle 会崩 */
    private fun releasePreviewBitmaps() {
        previewInnerBmp = null
        previewCoverBmp = null
        previewOutputBmp = null
    }

    /** 仅在没有界面引用这些位图时调用 */
    private fun recycleBitmapsNow() {
        previewInnerBmp?.recycle()
        previewCoverBmp?.recycle()
        previewOutputBmp?.recycle()
        previewInnerBmp = null
        previewCoverBmp = null
        previewOutputBmp = null
    }

    override fun onCleared() {
        super.onCleared()
        invalidatePreviews()
        runBlocking {
            withTimeoutOrNull(2000) {
                workMutex.withLock { recycleBitmapsNow() }
            }
        }
    }

    private fun preparePreviewEnvironment() {
        val gen = ++bitmapVersion
        viewModelScope.launch(Dispatchers.IO) {
            workMutex.withLock {
                if (gen != bitmapVersion) return@withLock
                rebuildPreviewBitmaps(gen, lastParams ?: Params())
            }
        }
    }

    fun updateParams(params: Params) {
        val previous = lastParams
        lastParams = params
        if (previous == null || previous.maxSize != params.maxSize || previewOutputBmp == null) {
            val gen = ++bitmapVersion
            viewModelScope.launch(Dispatchers.IO) {
                workMutex.withLock {
                    if (gen != bitmapVersion) return@withLock
                    rebuildPreviewBitmaps(gen, params)
                }
            }
        } else {
            updatePreviewInternal(params)
        }
    }

    /**
     * 1. 里图等比缩放到不超过 maxSize（预览时再叠加 600px 上限）
     * 2. 表图居中裁剪到和里图同尺寸
     * 3. 分配输出位图并立即编码一次
     */
    private fun rebuildPreviewBitmaps(gen: Long, params: Params) {
        val coverUri = _selectedCoverUri.value ?: return
        val innerUri = _selectedInnerUri.value ?: return
        val app = getApplication<Application>()

        val coverSrc = decodeSampledBitmapFromUri(app, coverUri, previewSize, previewSize) ?: return
        val innerSrc = decodeSampledBitmapFromUri(app, innerUri, previewSize, previewSize) ?: return

        val previewMaxSize = params.maxSize.coerceIn(16, previewSize)
        val inner = fitBitmap(innerSrc, previewMaxSize)
        val cover = coverResizeBitmap(coverSrc, inner.width, inner.height)

        innerSrc.recycle()
        coverSrc.recycle()

        if (gen != bitmapVersion) {
            inner.recycle()
            cover.recycle()
            return
        }

        releasePreviewBitmaps()

        previewInnerBmp = inner
        previewCoverBmp = cover
        previewOutputBmp = createOutputBitmap(inner.width, inner.height)

        encodeWithCurrentBitmaps(params)
    }

    private fun updatePreviewInternal(params: Params) {
        if (previewInnerBmp == null || previewCoverBmp == null || previewOutputBmp == null) return

        val request = ++latestEncodeRequest
        val gen = bitmapVersion

        viewModelScope.launch(Dispatchers.Default) {
            workMutex.withLock {
                if (gen != bitmapVersion) return@withLock
                if (request != latestEncodeRequest) return@withLock
                encodeWithCurrentBitmaps(params)
            }
        }
    }

    private fun encodeWithCurrentBitmaps(params: Params) {
        val inner = previewInnerBmp ?: return
        val cover = previewCoverBmp ?: return
        val out = previewOutputBmp ?: return
        try {
            encodeInto(inner, cover, out, params)
            _previewTrigger.value++
        } catch (t: Throwable) {
            Log.e(TAG, "preview encode failed", t)
        }
    }

    private fun encodeInto(inner: Bitmap, cover: Bitmap, out: Bitmap, params: Params) {
        PrismTankCoder.encode(
            inner, cover, out,
            params.innerThreshold, params.coverThreshold,
            params.innerContrast, params.coverContrast,
            params.innerGray, params.coverGray,
            params.slope, params.gap, params.isRow, params.isReverse
        )
    }

    fun savePrismTank(params: Params, format: ImageFormat) {
        val coverUri = _selectedCoverUri.value ?: return
        val innerUri = _selectedInnerUri.value ?: return

        _isSaving.value = true

        viewModelScope.launch {
            val app = getApplication<Application>()
            try {
                val result: Bitmap? = withContext(Dispatchers.IO) {
                    val coverSrc = decodeFullBitmapFromUri(app, coverUri) ?: return@withContext null
                    val innerSrc = decodeFullBitmapFromUri(app, innerUri) ?: return@withContext null

                    val inner = fitBitmap(innerSrc, params.maxSize)
                    val cover = coverResizeBitmap(coverSrc, inner.width, inner.height)

                    innerSrc.recycle()
                    coverSrc.recycle()

                    val out = createOutputBitmap(inner.width, inner.height)
                    val encoded = PrismTankCoder.encode(
                        inner, cover, out,
                        params.innerThreshold, params.coverThreshold,
                        params.innerContrast, params.coverContrast,
                        params.innerGray, params.coverGray,
                        params.slope, params.gap, params.isRow, params.isReverse
                    )

                    inner.recycle()
                    cover.recycle()

                    if (encoded) out else {
                        out.recycle()
                        null
                    }
                }

                if (result != null) {
                    saveImageToDownload(
                        context = app,
                        bitmap = result,
                        filename = "PrismTank_${System.currentTimeMillis()}",
                        format = format
                    )
                    result.recycle()
                }
            } catch (_: OutOfMemoryError) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(app, app.getString(R.string.image_too_large), Toast.LENGTH_LONG).show()
                }
            } catch (t: Throwable) {
                Log.e(TAG, "save failed", t)
                withContext(Dispatchers.Main) {
                    Toast.makeText(app, t.message ?: t.javaClass.simpleName, Toast.LENGTH_LONG).show()
                }
            } finally {
                _isSaving.value = false
            }
        }
    }

    // ---------------- 位图工具 ----------------

    private fun createOutputBitmap(width: Int, height: Int): Bitmap =
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

    /** 等比缩放到不超过 maxSize（只缩不放），语义与参考实现的 resizeFit 一致 */
    private fun fitBitmap(source: Bitmap, maxSize: Int): Bitmap {
        val longSide = max(source.width, source.height)
        if (maxSize <= 0 || longSide <= maxSize) {
            return source.copy(Bitmap.Config.ARGB_8888, true) ?: source
        }
        val ratio = maxSize.toFloat() / longSide.toFloat()
        val width = (source.width * ratio).roundToInt().coerceAtLeast(1)
        val height = (source.height * ratio).roundToInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(source, width, height, true)
        return scaled.copy(Bitmap.Config.ARGB_8888, true) ?: scaled
    }

    /** 居中裁剪缩放（cover），语义与参考实现的 resizeCover 一致 */
    private fun coverResizeBitmap(source: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
        if (source.width == targetWidth && source.height == targetHeight) {
            return source.copy(Bitmap.Config.ARGB_8888, true) ?: source
        }

        val result = createOutputBitmap(targetWidth, targetHeight)
        val canvas = Canvas(result)
        val scale = max(
            targetWidth.toFloat() / source.width.toFloat(),
            targetHeight.toFloat() / source.height.toFloat()
        )
        val cropWidth = (targetWidth / scale).roundToInt().coerceIn(1, source.width)
        val cropHeight = (targetHeight / scale).roundToInt().coerceIn(1, source.height)
        val left = ((source.width - cropWidth) / 2).coerceAtLeast(0)
        val top = ((source.height - cropHeight) / 2).coerceAtLeast(0)

        val srcRect = Rect(left, top, left + cropWidth, top + cropHeight)
        val dstRect = Rect(0, 0, targetWidth, targetHeight)
        val paint = Paint().apply {
            isFilterBitmap = true
            isAntiAlias = true
        }
        canvas.drawBitmap(source, srcRect, dstRect, paint)
        return result
    }

    private fun decodeSampledBitmapFromUri(app: Application, uri: Uri, reqWidth: Int, reqHeight: Int): Bitmap? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        app.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }

        val sampleSize = calculateInSampleSize(options, reqWidth, reqHeight)

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inJustDecodeBounds = false
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inMutable = true
            inScaled = false
        }
        return app.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, decodeOptions)
        }
    }

    private fun decodeFullBitmapFromUri(app: Application, uri: Uri): Bitmap? {
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inMutable = true
            inScaled = false
        }
        return app.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}
