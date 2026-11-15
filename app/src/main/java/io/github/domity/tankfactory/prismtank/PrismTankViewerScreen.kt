package io.github.domity.tankfactory.prismtank

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.domity.cybertheme.molecules.CyberLoading
import io.github.domity.cybertheme.atoms.CyberSurface
import io.github.domity.cybertheme.atoms.CyberText
import io.github.domity.cybertheme.foundation.CyberTheme
import io.github.domity.cybertheme.molecules.CyberButton
import io.github.domity.cybertheme.molecules.CyberSlider
import io.github.domity.cybertheme.molecules.CyberSwitch
import io.github.domity.cybertheme.templates.CyberScaffold
import io.github.domity.tankfactory.R
import io.github.domity.tankfactory.ui.components.FastUriImage
import io.github.domity.tankfactory.ui.components.ImageFormat
import kotlin.math.roundToInt

private data class DecodeMethodOption(val method: Int, val labelRes: Int)

private val decodeMethodOptions = listOf(
    DecodeMethodOption(PrismTankCoder.DecodeMethod.BLACK, R.string.prism_method_black),
    DecodeMethodOption(PrismTankCoder.DecodeMethod.WHITE, R.string.prism_method_white),
    DecodeMethodOption(PrismTankCoder.DecodeMethod.TRANSPARENT, R.string.prism_method_transparent),
    DecodeMethodOption(PrismTankCoder.DecodeMethod.COPY_LEFT, R.string.prism_method_copy_left),
    DecodeMethodOption(PrismTankCoder.DecodeMethod.COPY_TOP, R.string.prism_method_copy_top),
    DecodeMethodOption(PrismTankCoder.DecodeMethod.LTAVG, R.string.prism_method_ltavg)
)

@Composable
fun PrismTankViewerScreen(
    viewModel: PrismTankViewerViewModel = viewModel()
) {
    val selectedImageUri by viewModel.selectedImageUri.collectAsState()
    val displayBitmap by viewModel.displayBitmap.collectAsState()
    val isTooLarge by viewModel.isResultTooLarge.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val isDecoding by viewModel.isDecoding.collectAsState()

    var lowerThreshold by remember { mutableIntStateOf(0) }
    var higherThreshold by remember { mutableIntStateOf(24) }
    var method by remember { mutableIntStateOf(PrismTankCoder.DecodeMethod.LTAVG) }
    var contrast by remember { mutableIntStateOf(0) }
    var iterations by remember { mutableIntStateOf(16) }
    var saveFormat by remember { mutableStateOf(ImageFormat.PNG) }
    var hasDecoded by remember { mutableStateOf(false) }

    val cachedDisplayBitmap = remember(displayBitmap) {
        displayBitmap?.asImageBitmap()
    }

    fun currentParams() = PrismTankViewerViewModel.DecodeParams(
        lowerThreshold = lowerThreshold,
        higherThreshold = higherThreshold,
        method = method,
        contrast = contrast,
        iterations = iterations
    )

    fun reDecodeIfNeeded() {
        if (hasDecoded) viewModel.decode(currentParams())
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            if (uri != null) {
                hasDecoded = false
                viewModel.setImageUri(uri)
            }
        }
    )

    CyberScaffold(useSafeArea = true) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
            ) {
                ImageSelectionSlot(
                    modifier = Modifier
                        .weight(2f)
                        .fillMaxHeight(),
                    uri = selectedImageUri,
                    placeholderText = stringResource(id = R.string.select_image).uppercase(),
                    onClick = { imagePickerLauncher.launch("image/*") }
                )

                Spacer(Modifier.width(16.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceEvenly
                ) {
                    CyberButton(
                        text = if (isDecoding) stringResource(id = R.string.decoding) else stringResource(id = R.string.decode),
                        onClick = {
                            hasDecoded = true
                            viewModel.onPressDecoderButton()
                            viewModel.decode(currentParams())
                        },
                        enabled = selectedImageUri != null && !isDecoding,
                        modifier = Modifier.fillMaxWidth(),
                        isPrimary = true
                    )

                    CyberText(
                        text = if (saveFormat == ImageFormat.PNG) stringResource(id = R.string.format_png)
                        else stringResource(id = R.string.format_webp),
                        color = CyberTheme.colors.text,
                        style = CyberTheme.typography.body
                    )

                    CyberSwitch(
                        checked = saveFormat == ImageFormat.PNG,
                        onCheckedChange = { saveFormat = if (it) ImageFormat.PNG else ImageFormat.WEBP }
                    )

                    CyberButton(
                        text = if (isSaving) stringResource(id = R.string.saving) else stringResource(id = R.string.save),
                        onClick = { viewModel.saveImageToDownload(saveFormat) },
                        enabled = (cachedDisplayBitmap != null || isTooLarge) && !isSaving,
                        modifier = Modifier.fillMaxWidth(),
                        isPrimary = false
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            CyberSurface(
                modifier = Modifier.fillMaxWidth(),
                color = CyberTheme.colors.surface,
                borderWidth = 1.dp,
                borderColor = CyberTheme.colors.border
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SliderRow(
                        label = stringResource(id = R.string.prism_tank_viewer_lower_threshold, lowerThreshold),
                        value = lowerThreshold.toFloat(),
                        range = 0f..254f,
                        onValueChange = {
                            lowerThreshold = it.roundToInt().coerceIn(0, higherThreshold - 1)
                            reDecodeIfNeeded()
                        }
                    )
                    Spacer(Modifier.height(12.dp))
                    SliderRow(
                        label = stringResource(id = R.string.prism_tank_viewer_higher_threshold, higherThreshold),
                        value = higherThreshold.toFloat(),
                        range = 1f..255f,
                        onValueChange = {
                            higherThreshold = it.roundToInt().coerceIn(lowerThreshold + 1, 255)
                            reDecodeIfNeeded()
                        }
                    )
                    Spacer(Modifier.height(12.dp))
                    SliderRow(
                        label = stringResource(id = R.string.prism_tank_viewer_contrast, contrast),
                        value = contrast.toFloat(),
                        range = -255f..255f,
                        steps = 101,
                        onValueChange = {
                            contrast = it.roundToInt()
                            reDecodeIfNeeded()
                        }
                    )
                    Spacer(Modifier.height(12.dp))
                    SliderRow(
                        label = stringResource(id = R.string.prism_tank_viewer_iterations, iterations),
                        value = iterations.toFloat(),
                        range = 0f..100f,
                        onValueChange = {
                            iterations = it.roundToInt()
                            reDecodeIfNeeded()
                        }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            CyberSurface(
                modifier = Modifier.fillMaxWidth(),
                color = CyberTheme.colors.surface,
                borderWidth = 1.dp,
                borderColor = CyberTheme.colors.border
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    CyberText(
                        text = stringResource(id = R.string.prism_tank_viewer_method),
                        color = CyberTheme.colors.text,
                        style = CyberTheme.typography.body
                    )
                    Spacer(Modifier.height(8.dp))
                    decodeMethodOptions.chunked(3).forEach { rowOptions ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowOptions.forEach { option ->
                                CyberButton(
                                    text = stringResource(id = option.labelRes),
                                    onClick = {
                                        method = option.method
                                        reDecodeIfNeeded()
                                    },
                                    isPrimary = method == option.method,
                                    modifier = Modifier.weight(1f),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp)
                                )
                            }
                            if (rowOptions.size < 3) {
                                repeat(3 - rowOptions.size) {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            CyberSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp),
                color = CyberTheme.colors.surface,
                borderWidth = 1.dp,
                borderColor = if (cachedDisplayBitmap != null) CyberTheme.colors.primary else CyberTheme.colors.border
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        isTooLarge -> {
                            CyberText(
                                text = stringResource(id = R.string.image_too_large),
                                color = CyberTheme.colors.secondary,
                                modifier = Modifier.padding(8.dp),
                                style = CyberTheme.typography.body
                            )
                        }
                        cachedDisplayBitmap != null -> {
                            Image(
                                bitmap = cachedDisplayBitmap,
                                contentDescription = "Decoded Prism Tank",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        else -> {
                            if (!isDecoding) {
                                CyberText(
                                    text = stringResource(id = R.string.generated_image),
                                    color = CyberTheme.colors.textDim,
                                    style = CyberTheme.typography.body
                                )
                            }
                        }
                    }

                    if (isDecoding) {
                        CyberSurface(
                            modifier = Modifier.fillMaxSize(),
                            color = CyberTheme.colors.background.copy(alpha = 0.7f),
                            borderWidth = 0.dp,
                            borderColor = Color.Transparent
                        ) {}

                        CyberLoading(
                            modifier = Modifier.align(Alignment.Center),
                            size = 48.dp,
                            color = CyberTheme.colors.primary
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            CyberText(
                text = stringResource(id = R.string.prism_tank_viewer_tips),
                color = CyberTheme.colors.text,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    onValueChange: (Float) -> Unit
) {
    Column {
        CyberText(text = label, color = CyberTheme.colors.text, style = CyberTheme.typography.body)
        Spacer(Modifier.height(4.dp))
        CyberSlider(
            value = value,
            onValueChange = onValueChange,
            range = range,
            steps = steps
        )
    }
}

@Composable
private fun ImageSelectionSlot(
    modifier: Modifier = Modifier,
    uri: Uri?,
    placeholderText: String,
    onClick: () -> Unit
) {
    CyberSurface(
        modifier = modifier.clickable(onClick = onClick),
        color = CyberTheme.colors.surface,
        borderWidth = 1.dp,
        borderColor = if (uri != null) CyberTheme.colors.primary else CyberTheme.colors.border
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            if (uri == null) {
                CyberText(
                    text = placeholderText,
                    color = CyberTheme.colors.textDim,
                    style = CyberTheme.typography.body
                )
            } else {
                FastUriImage(
                    uri = uri,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
