package com.raindepartment.weather

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raindepartment.weather.update.AppUpdateManager
import com.raindepartment.weather.update.UpdateRelease
import com.raindepartment.weather.update.UpdateUiState
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RainDepartmentTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    RainDepartmentHome()
                }
            }
        }
    }
}

@Composable
internal fun RainDepartmentHome() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current
    val updateManager = remember(context) {
        AppUpdateManager(context.applicationContext)
    }
    val updateState by updateManager.state.collectAsStateWithLifecycle()
    val updateScope = rememberCoroutineScope()
    val weather = DummyWeatherData.current
    var unitSystem by remember { mutableStateOf(WeatherPreferences.unitSystem(context)) }
    var selectedBackplateIndex by remember {
        mutableStateOf(WeatherPreferences.backplateIndex(context))
    }
    val selectedBackplate = BackplateChoices[selectedBackplateIndex]
    val previewWeather = weather.forBackplate(selectedBackplate)

    LaunchedEffect(updateManager) {
        updateManager.check()
    }

    DisposableEffect(updateManager, lifecycleOwner, activity) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && activity != null) {
                updateManager.resumeInstall(activity)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(unitSystem, selectedBackplateIndex) {
        WeatherPreferences.setUnitSystem(context, unitSystem)
        WeatherPreferences.setBackplateIndex(context, selectedBackplateIndex)
        WeatherWidget.updateAll(context.applicationContext)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "RainDepartment",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Navy,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Your quiet window into the sky",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MutedNavy,
                )
            }
            Text(
                text = "DUMMY DATA",
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(ChipBlue)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                color = DeepBlue,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
            )
        }

        Spacer(modifier = Modifier.height(22.dp))

        WeatherPreviewCard(
            weather = previewWeather,
            unitSystem = unitSystem,
        )

        BackplateBrowser(
            selected = selectedBackplate,
            index = selectedBackplateIndex,
            onPrevious = {
                selectedBackplateIndex =
                    (selectedBackplateIndex - 1 + BackplateChoices.size) % BackplateChoices.size
            },
            onNext = {
                selectedBackplateIndex =
                    (selectedBackplateIndex + 1) % BackplateChoices.size
            },
        )

        Spacer(modifier = Modifier.height(22.dp))

        Text(
            text = "Temperature units",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Navy,
        )
        Spacer(modifier = Modifier.height(8.dp))
        UnitSelector(
            selected = unitSystem,
            onSelected = { unitSystem = it },
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Metric is the default. This setting updates the widget on your home screen.",
            style = MaterialTheme.typography.bodySmall,
            color = MutedNavy,
        )

        Spacer(modifier = Modifier.height(22.dp))

        ForecastSummary(
            weather = previewWeather,
            unitSystem = unitSystem,
        )
    }

    if (activity != null) {
        UpdateDialog(
            state = updateState,
            onUpdate = { release ->
                val job = updateScope.launch { updateManager.download(release) }
                updateManager.attachDownloadJob(job)
            },
            onSkip = updateManager::skip,
            onCancelDownload = updateManager::cancelDownload,
            onInstall = { updateManager.install(activity) },
            onDismissError = updateManager::dismissError,
        )
    }
}

@Composable
private fun WeatherPreviewCard(
    weather: DummyWeather,
    unitSystem: UnitSystem,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val bitmap = remember(context, weather.condition, weather.isDay) {
        BackplateLoader.bitmap(context, weather)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(224.dp)
            .clip(RoundedCornerShape(28.dp)),
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Partly cloudy weather preview",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0x28001F42),
                            Color(0x18001F42),
                            Color(0x55001F42),
                        ),
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "Home widget preview",
                    color = Color(0xE6FFFFFF),
                    fontSize = 11.sp,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
            ) {
                Spacer(modifier = Modifier.size(112.dp, 1.dp))
                Column {
                    Text(
                        text = weather.location,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = weather.temperature(unitSystem),
                        color = Color.White,
                        fontSize = 54.sp,
                        lineHeight = 56.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "${weather.conditionLabel}  ·  ${weather.highLow(unitSystem)}",
                        color = Color(0xF2FFFFFF),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@Composable
private fun BackplateBrowser(
    selected: BackplateChoice,
    index: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        TextButton(onClick = onPrevious) {
            Text(text = "‹ Previous")
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = selected.label,
                color = Navy,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "${index + 1} of ${BackplateChoices.size}",
                color = MutedNavy,
                fontSize = 11.sp,
            )
        }
        TextButton(onClick = onNext) {
            Text(text = "Next ›")
        }
    }
}

@Composable
private fun UnitSelector(
    selected: UnitSystem,
    onSelected: (UnitSystem) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(SegmentedBackground)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        UnitOption(
            modifier = Modifier.weight(1f),
            title = "Metric",
            subtitle = "°C · km/h",
            selected = selected == UnitSystem.METRIC,
            onClick = { onSelected(UnitSystem.METRIC) },
        )
        UnitOption(
            modifier = Modifier.weight(1f),
            title = "Imperial",
            subtitle = "°F · mph",
            selected = selected == UnitSystem.IMPERIAL,
            onClick = { onSelected(UnitSystem.IMPERIAL) },
        )
    }
}

@Composable
private fun UnitOption(
    modifier: Modifier,
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) Color.White else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            color = if (selected) DeepBlue else MutedNavy,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = subtitle,
            color = if (selected) MutedNavy else MutedNavy.copy(alpha = 0.8f),
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun ForecastSummary(
    weather: DummyWeather,
    unitSystem: UnitSystem,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Austin",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Navy,
                    )
                    Text(
                        text = "Today · ${weather.conditionLabel.lowercase()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedNavy,
                    )
                }
                Text(
                    text = weather.temperature(unitSystem),
                    color = DeepBlue,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                SummaryMetric("Precipitation", "${weather.precipitationChance}%")
                SummaryMetric("UV index", weather.uvIndex.toString())
                SummaryMetric("High / low", weather.highLow(unitSystem))
            }
        }
    }
}

@Composable
private fun SummaryMetric(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MutedNavy,
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = DeepBlue,
        )
    }
}

@Composable
private fun UpdateDialog(
    state: UpdateUiState,
    onUpdate: (UpdateRelease) -> Unit,
    onSkip: (UpdateRelease) -> Unit,
    onCancelDownload: () -> Unit,
    onInstall: () -> Unit,
    onDismissError: () -> Unit,
) {
    when (state) {
        is UpdateUiState.Available -> AlertDialog(
            onDismissRequest = { onSkip(state.release) },
            title = { Text("${state.release.title} is available") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Installed: ${BuildConfig.VERSION_NAME}  •  New: ${state.release.version}")
                    if (state.release.notes.isNotBlank()) {
                        Text(
                            state.release.notes.take(1_500),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text("Android will ask you to approve the installation.")
                }
            },
            confirmButton = { Button(onClick = { onUpdate(state.release) }) { Text("Update") } },
            dismissButton = {
                TextButton(onClick = { onSkip(state.release) }) { Text("Skip this version") }
            },
        )

        is UpdateUiState.Downloading -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Downloading ${state.release.version}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val progress = if (state.totalBytes > 0L) {
                        state.bytesRead.toFloat() / state.totalBytes
                    } else {
                        0f
                    }
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    val total = if (state.totalBytes > 0L) {
                        formatUpdateSize(state.totalBytes)
                    } else {
                        "unknown size"
                    }
                    Text("${formatUpdateSize(state.bytesRead)} of $total")
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = onCancelDownload) { Text("Cancel") }
            },
        )

        is UpdateUiState.AwaitingInstallPermission -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Allow app updates") },
            text = {
                Text(
                    "Allow RainDepartment to install unknown apps, then return here " +
                        "to continue with ${state.release.version}.",
                )
            },
            confirmButton = { Button(onClick = onInstall) { Text("Open settings") } },
            dismissButton = {
                TextButton(onClick = onDismissError) { Text("Cancel") }
            },
        )

        is UpdateUiState.ReadyToInstall -> {
            LaunchedEffect(state.release.tag) { onInstall() }
            AlertDialog(
                onDismissRequest = {},
                title = { Text("Update ready") },
                text = { Text("Opening Android's installer for ${state.release.version}…") },
                confirmButton = { Button(onClick = onInstall) { Text("Install") } },
                dismissButton = {
                    TextButton(onClick = onDismissError) { Text("Cancel") }
                },
            )
        }

        is UpdateUiState.Error -> AlertDialog(
            onDismissRequest = onDismissError,
            title = { Text("Update failed") },
            text = { Text(state.message) },
            confirmButton = { Button(onClick = onDismissError) { Text("OK") } },
        )

        UpdateUiState.Idle, UpdateUiState.Checking -> Unit
    }
}

private fun formatUpdateSize(bytes: Long): String {
    if (bytes < 1_024L) return "$bytes B"
    if (bytes < 1_024L * 1_024L) return "%.1f KB".format(bytes / 1_024f)
    return "%.1f MB".format(bytes / (1_024f * 1_024f))
}

private val Navy = Color(0xFF183354)
private val DeepBlue = Color(0xFF245589)
private val MutedNavy = Color(0xFF60728C)
private val ChipBlue = Color(0xFFDCEEFF)
private val SegmentedBackground = Color(0xFFE4EBF3)
