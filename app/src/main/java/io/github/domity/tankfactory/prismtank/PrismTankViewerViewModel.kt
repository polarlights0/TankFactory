package io.github.domity.tankfactory.prismtank

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.core.graphics.scale
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

class PrismTankViewerViewModel(application: Application) : AndroidViewModel(application) {

    /** 显形参数，语义与 Mirage_Decode 的解码页一致 */
    data class DecodeParams(
        val lowerThreshold: Int = 0,
        val higherThreshold: Int = 24,
        val method: Int = PrismTankCoder.DecodeMethod.LTAVG,
        val contrast: Int = 0,
        val iterations: Int = 16
    )

    private val _selectedImageUri = MutableStateFlow<Uri?>(null)
    val selectedImageUri: StateFlow<Uri?> = _selectedImageUri

    private val _displayBitmap = MutableStateFlow<Bitmap?>(null)
    val displayBitmap: StateFlow<Bitmap?> = _displayBitmap

    private val _isResultTooLarge = MutableStateFlow(false)
    val isResultTooLarge: StateFlow<Boolean> = _isResultTooLarge

    private val _isDecoding = MutableStateFlow(false)
    val isDecoding: StateFlow<Boolean> = _isDecoding

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving

    private var sourceBitmap: Bitmap? = null
    private var decodedBitmap: Bitmap? = null

    private val workMutex = Mutex()
    private var sourceVersion = 0L
    private var latestRequest = 0L

    private val maxSafePixels = 16_000_000
    private val TAG = "PrismTankViewer"
    private val maxDisplayDimension = 1080

    private var lastParams: DecodeParams = DecodeParams()

    fun setImageUri(uri: Uri) {
        _selectedImageUri.value = uri
        sourceVersion++
        latestRequest++
        _displayBitmap.value = null
        _isResultTooLarge.value = false
        decodedBitmap = null
        viewModelScope.launch(Dispatchers.IO) {
            workMutex.withLock {
                sourceBitmap?.recycle()
                sourceBitmap = null
            }
        }
    }

    fun onPressDecoderButton() {
        _displayBitmap.value = null
        _isResultTooLarge.value = false
        decodedBitmap = null
    }

    override fun onCleared() {
        super.onCleared()
        latestRequest++
        runBlocking {
            withTimeoutOrNull(2000) {
                workMutex.withLock {
                    sourceBitmap?.recycle()
                    decodedBitmap?.recycle()
                    sourceBitmap = null
                    decodedBitmap = null
                }
            }
        }
    }

    fun decode(params: DecodeParams) {
        lastParams = params
        val uri = _selectedImageUri.value ?: return
        val request = ++latestRequest
        val version = sourceVersion
        _isDecoding.value = true

        viewModelScope.launch {
            val app = getApplication<Application>()
            try {
                val result = withContext(Dispatchers.Default) {
                    workMutex.withLock {
                        if (request != latestRequest) return@withLock null
                        var src = sourceBitmap
                        if (src == null) {
                            src = loadBitmap(app, uri)
                            if (src == null) return@withLock null
                            if (src.width.toLong() * src.height.toLong() > maxSafePixels) {
                                src.recycle()
                                return@withLock OVERSIZED
                            }
                            sourceBitmap = src
                        }
                        PrismTankCoder.decode(
                            src,
                            params.lowerThreshold,
                            params.higherThreshold,
                            params.method,
                            params.contrast,
                            params.iterations
                        )
                    }
                }

                if (version != sourceVersion) {
                    (result as? Bitmap)?.recycle()
                    return@launch
                }

                if (result === OVERSIZED) {
                    _isResultTooLarge.value = true
                    _displayBitmap.value = null
                } else {
                    val decoded = result as? Bitmap
                    if (decoded == null) {
                        val reason = PrismTankCoder.unavailableReason()
                        if (reason != null) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(
                                    app,
                                    "原生库不可用: $reason",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                    if (decoded != null) {
                        _isResultTooLarge.value = false
                        decodedBitmap = decoded
                        _displayBitmap.value = withContext(Dispatchers.Default) { scaleForDisplay(decoded) }
                    }
                }
            } catch (_: OutOfMemoryError) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(app, app.getString(R.string.image_too_large), Toast.LENGTH_LONG).show()
                }
            } catch (t: Throwable) {
                Log.e(TAG, "decode failed", t)
                withContext(Dispatchers.Main) {
                    Toast.makeText(app, t.message ?: t.javaClass.simpleName, Toast.LENGTH_LONG).show()
                }
            } finally {
                _isDecoding.value = false
            }
        }
    }

    fun reDecode() = decode(lastParams)

    fun saveImageToDownload(format: ImageFormat) {
        val bitmapToSave = decodedBitmap ?: return
        _isSaving.value = true
        viewModelScope.launch {
            saveImageToDownload(
                context = getApplication(),
                bitmap = bitmapToSave,
                filename = "PrismTank_Decoded_${System.currentTimeMillis()}",
                format = format
            )
            _isSaving.value = false
        }
    }

    private fun loadBitmap(app: Application, uri: Uri): Bitmap? {
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inMutable = false
            inScaled = false
        }
        return app.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }
    }

    private fun scaleForDisplay(bitmap: Bitmap): Bitmap {
        val longSide = max(bitmap.width, bitmap.height)
        if (longSide <= maxDisplayDimension) return bitmap

        val scaleFactor = maxDisplayDimension.toFloat() / longSide
        val newWidth = (bitmap.width * scaleFactor).toInt().coerceAtLeast(1)
        val newHeight = (bitmap.height * scaleFactor).toInt().coerceAtLeast(1)
        return bitmap.scale(newWidth, newHeight)
    }

    private companion object {
        /** 过大的图片不参与预览，但仍允许保存 */
        val OVERSIZED = Any()
    }
}
