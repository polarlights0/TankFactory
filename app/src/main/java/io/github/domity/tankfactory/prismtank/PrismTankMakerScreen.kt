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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

@Composable
fun PrismTankMakerScreen(
    viewModel: PrismTankMakerViewModel = viewModel()
) {
    LaunchedEffect(Unit) {
        viewModel.onMakerScreenEntered()
    }

    val selectedCoverUri by viewModel.selectedCoverUri.collectAsState()
    val selectedInnerUri by viewModel.selectedInnerUri.collectAsState()
    val previewTrigger by viewModel.previewTrigger.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()

    val previewImageBitmap = remember(previewTrigger) {
        viewModel.previewBitmap()?.asImageBitmap()
    }

    var innerThreshold by remember { mutableIntStateOf(24) }
    var coverThreshold by remember { mutableIntStateOf(42) }
    var innerContrast by remember { mutableIntStateOf(0) }
    var coverContrast by remember { mutableIntStateOf(0) }
    var innerGray by remember { mutableStateOf(false) }
    var coverGray by remember { mutableStateOf(true) }
    var slope by remember { mutableIntStateOf(1) }
    var gap by remember { mutableIntStateOf(1) }
    var isRow by remember { mutableStateOf(true) }
    var isReverse by remember { mutableStateOf(false) }
    var maxSize by remember { mutableIntStateOf(1200) }
    var saveFormat by remember { mutableStateOf(ImageFormat.PNG) }

    fun currentParams() = PrismTankMakerViewModel.Params(
        innerThreshold = innerThreshold,
        coverThreshold = coverThreshold,
        innerContrast = innerContrast,
        coverContrast = coverContrast,
        innerGray = innerGray,
        coverGray = coverGray,
        slope = slope,
        gap = gap,
        isRow = isRow,
        isReverse = isReverse,
        maxSize = maxSize
    )

    fun pushParams() = viewModel.updateParams(currentParams())

    val imagePickerLauncher1 = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri -> if (uri != null) viewModel.setCoverImageUri(uri) }
    )
    val imagePickerLauncher2 = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri -> if (uri != null) viewModel.setInnerImageUri(uri) }
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
                    .height(180.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ImageSelectionSlot(
                    modifier = Modifier.weight(1f),
                    uri = selectedCoverUri,
                    placeholderText = stringResource(id = R.string.cover_image),
                    onClick = { imagePickerLauncher1.launch("image/*") }
                )

                ImageSelectionSlot(
                    modifier = Modifier.weight(1f),
                    uri = selectedInnerUri,
                    placeholderText = stringResource(id = R.string.hidden_image),
                    onClick = { imagePickerLauncher2.launch("image/*") }
                )
            }

            Spacer(Modifier.height(20.dp))

            ConfigCard {
                SliderRow(
                    label = stringResource(id = R.string.prism_tank_maker_inner_threshold, innerThreshold),
                    value = innerThreshold.toFloat(),
                    range = 0f..254f,
                    onValueChange = {
                        innerThreshold = it.roundToInt().coerceIn(0, coverThreshold - 1)
                        pushParams()
                    }
                )
                Spacer(Modifier.height(12.dp))
                SliderRow(
                    label = stringResource(id = R.string.prism_tank_maker_cover_threshold, coverThreshold),
                    value = coverThreshold.toFloat(),
                    range = 1f..255f,
                    onValueChange = {
                        coverThreshold = it.roundToInt().coerceIn(innerThreshold + 1, 255)
                        pushParams()
                    }
                )
            }

            Spacer(Modifier.height(16.dp))

            ConfigCard {
                SliderRow(
                    label = stringResource(id = R.string.prism_tank_maker_inner_contrast, innerContrast),
                    value = innerContrast.toFloat(),
                    range = -255f..255f,
                    steps = 101,
                    onValueChange = {
                        innerContrast = it.roundToInt()
                        pushParams()
                    }
                )
                Spacer(Modifier.height(12.dp))
                SliderRow(
                    label = stringResource(id = R.string.prism_tank_maker_cover_contrast, coverContrast),
                    value = coverContrast.toFloat(),
                    range = -255f..255f,
                    steps = 101,
                    onValueChange = {
                        coverContrast = it.roundToInt()
                        pushParams()
                    }
                )
                Spacer(Modifier.height(12.dp))
                SwitchRow(
                    label = stringResource(id = R.string.prism_tank_maker_inner_gray),
                    checked = innerGray,
                    onCheckedChange = {
                        innerGray = it
                        pushParams()
                    }
                )
                Spacer(Modifier.height(8.dp))
                SwitchRow(
                    label = stringResource(id = R.string.prism_tank_maker_cover_gray),
                    checked = coverGray,
                    onCheckedChange = {
                        coverGray = it
                        pushParams()
                    }
                )
            }

            Spacer(Modifier.height(16.dp))

            ConfigCard {
                SliderRow(
                    label = stringResource(id = R.string.prism_tank_maker_slope, slope),
                    value = slope.toFloat(),
                    range = 0f..4f,
                    steps = 3,
                    onValueChange = {
                        slope = it.roundToInt()
                        pushParams()
                    }
                )
                Spacer(Modifier.height(12.dp))
                SliderRow(
                    label = stringResource(id = R.string.prism_tank_maker_gap, gap),
                    value = gap.toFloat(),
                    range = 1f..4f,
                    steps = 2,
                    onValueChange = {
                        gap = it.roundToInt()
                        pushParams()
                    }
                )
                Spacer(Modifier.height(12.dp))
                SwitchRow(
                    label = stringResource(
                        id = R.string.prism_tank_maker_direction,
                        stringResource(
                            id = if (isRow) R.string.prism_tank_direction_row else R.string.prism_tank_direction_column
                        )
                    ),
                    checked = isRow,
                    onCheckedChange = {
                        isRow = it
                        pushParams()
                    }
                )
                Spacer(Modifier.height(8.dp))
                SwitchRow(
                    label = stringResource(id = R.string.prism_tank_maker_reverse),
                    checked = isReverse,
                    onCheckedChange = {
                        isReverse = it
                        pushParams()
                    }
                )
            }

            Spacer(Modifier.height(16.dp))

            ConfigCard {
                SliderRow(
                    label = stringResource(id = R.string.prism_tank_maker_max_size, maxSize),
                    value = maxSize.toFloat(),
                    range = 200f..4000f,
                    steps = 37,
                    onValueChange = {
                        maxSize = (it.roundToInt() / 100 * 100).coerceIn(200, 4000)
                        pushParams()
                    }
                )
            }

            Spacer(Modifier.height(20.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            ) {
                CyberSurface(
                    modifier = Modifier
                        .weight(1f)
                        .height(200.dp),
                    color = CyberTheme.colors.surface,
                    borderWidth = 1.dp,
                    borderColor = if (previewImageBitmap != null) CyberTheme.colors.primary else CyberTheme.colors.border
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (previewImageBitmap != null) {
                            Image(
                                bitmap = previewImageBitmap,
                                contentDescription = "Prism Tank Preview",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            CyberText(
                                text = stringResource(id = R.string.generated_image),
                                color = CyberTheme.colors.textDim,
                                style = CyberTheme.typography.body
                            )
                        }
                    }
                }

                Spacer(Modifier.width(16.dp))

                Column(
                    modifier = Modifier.height(200.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CyberText(
                        text = if (saveFormat == ImageFormat.PNG) stringResource(id = R.string.format_png)
                        else stringResource(id = R.string.format_webp),
                        color = CyberTheme.colors.text,
                        style = CyberTheme.typography.body
                    )

                    Spacer(Modifier.height(4.dp))

                    CyberSwitch(
                        checked = saveFormat == ImageFormat.PNG,
                        onCheckedChange = { saveFormat = if (it) ImageFormat.PNG else ImageFormat.WEBP }
                    )

                    Spacer(Modifier.height(16.dp))

                    CyberButton(
                        text = if (isSaving) stringResource(id = R.string.saving) else stringResource(id = R.string.save),
                        onClick = { viewModel.savePrismTank(currentParams(), saveFormat) },
                        enabled = selectedCoverUri != null && selectedInnerUri != null && !isSaving,
                        isPrimary = false,
                        modifier = Modifier.width(100.dp)
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            CyberButton(
                text = stringResource(id = R.string.make),
                onClick = { pushParams() },
                enabled = selectedCoverUri != null && selectedInnerUri != null,
                modifier = Modifier.fillMaxWidth(0.5f),
                isPrimary = true
            )

            Spacer(Modifier.height(16.dp))

            CyberText(
                text = stringResource(id = R.string.prism_tank_maker_tips),
                color = CyberTheme.colors.text,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }
    }
}

@Composable
private fun ConfigCard(content: @Composable () -> Unit) {
    CyberSurface(
        modifier = Modifier.fillMaxWidth(),
        color = CyberTheme.colors.surface,
        borderWidth = 1.dp,
        borderColor = CyberTheme.colors.border
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            content()
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
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CyberText(
            text = label,
            color = CyberTheme.colors.text,
            style = CyberTheme.typography.body,
            modifier = Modifier.weight(1f)
        )
        CyberSwitch(checked = checked, onCheckedChange = onCheckedChange)
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
        modifier = modifier
            .fillMaxHeight()
            .clickable(onClick = onClick),
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
