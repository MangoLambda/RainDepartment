package com.raindepartment.weather

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.GpsFixed
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Umbrella
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
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
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RainDepartmentTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DashboardBackground,
                ) {
                    RainDepartmentApp()
                }
            }
        }
    }
}

@Composable
internal fun RainDepartmentApp() {
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current
    val updateManager = remember(context) {
        AppUpdateManager(context.applicationContext)
    }
    val updateState by updateManager.state.collectAsStateWithLifecycle()
    val updateScope = rememberCoroutineScope()
    val forecast = MockDashboardData.current

    var selectedTabName by rememberSaveable { mutableStateOf(DashboardTab.BRIEFING.name) }
    val selectedTab = DashboardTab.valueOf(selectedTabName)
    var selectedRangeName by rememberSaveable { mutableStateOf(ForecastRange.TODAY.name) }
    val selectedRange = ForecastRange.valueOf(selectedRangeName)
    var unitSystem by remember { mutableStateOf(WeatherPreferences.unitSystem(context)) }
    var selectedBackplateIndex by remember {
        mutableStateOf(WeatherPreferences.backplateIndex(context))
    }
    val selectedBackplate = BackplateChoices[selectedBackplateIndex]
    val previewWeather = DummyWeatherData.current.forBackplate(selectedBackplate)

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

    Scaffold(
        containerColor = DashboardBackground,
        topBar = { RainDepartmentHeader() },
        bottomBar = {
            RainDepartmentBottomNavigation(
                selected = selectedTab,
                onSelected = { selectedTabName = it.name },
            )
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            when (selectedTab) {
                DashboardTab.BRIEFING -> BriefingScreen(
                    forecast = forecast,
                    unitSystem = unitSystem,
                    backgroundWeather = previewWeather,
                    selectedRange = selectedRange,
                    onRangeSelected = { selectedRangeName = it.name },
                )

                DashboardTab.SETTINGS -> SettingsScreen(
                    weather = previewWeather,
                    unitSystem = unitSystem,
                    selectedBackplate = selectedBackplate,
                    selectedBackplateIndex = selectedBackplateIndex,
                    onUnitSystemSelected = { unitSystem = it },
                    onPreviousBackplate = {
                        selectedBackplateIndex =
                            (selectedBackplateIndex - 1 + BackplateChoices.size) % BackplateChoices.size
                    },
                    onNextBackplate = {
                        selectedBackplateIndex =
                            (selectedBackplateIndex + 1) % BackplateChoices.size
                    },
                )

                else -> PlaceholderScreen(tab = selectedTab)
            }
        }
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
private fun RainDepartmentHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(70.dp)
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF2395F1), Color(0xFF39A8F2)),
                ),
            )
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = {}) {
            Icon(
                imageVector = Icons.Outlined.Menu,
                contentDescription = "Open menu",
                tint = Color.White,
                modifier = Modifier.size(27.dp),
            )
        }
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.Cloud,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(30.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Rain Department",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        IconButton(onClick = {}) {
            Icon(
                imageVector = Icons.Outlined.LocationOn,
                contentDescription = "Change location",
                tint = Color.White,
                modifier = Modifier.size(27.dp),
            )
        }
    }
}

@Composable
private fun RainDepartmentBottomNavigation(
    selected: DashboardTab,
    onSelected: (DashboardTab) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color(0xFFD9E5EF)),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DashboardTab.entries.forEach { tab ->
                val isSelected = tab == selected
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSelected(tab) }
                        .padding(top = 7.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector = tab.icon(),
                        contentDescription = tab.label,
                        tint = if (isSelected) AccentBlue else MutedNavy,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = tab.label,
                        color = if (isSelected) AccentBlue else MutedNavy,
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    )
                }
            }
        }
    }
}

private fun DashboardTab.icon(): ImageVector = when (this) {
    DashboardTab.BRIEFING -> Icons.Outlined.Cloud
    DashboardTab.TIMELINE -> Icons.Outlined.AccessTime
    DashboardTab.RADAR -> Icons.Outlined.GpsFixed
    DashboardTab.OUTLOOK -> Icons.Outlined.BarChart
    DashboardTab.SETTINGS -> Icons.Outlined.Settings
}

@Composable
private fun BriefingScreen(
    forecast: DashboardForecast,
    unitSystem: UnitSystem,
    backgroundWeather: DummyWeather,
    selectedRange: ForecastRange,
    onRangeSelected: (ForecastRange) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        WeatherHeroCard(
            forecast = forecast,
            unitSystem = unitSystem,
            backgroundWeather = backgroundWeather,
        )
        ForecastRangeSelector(
            selected = selectedRange,
            onSelected = onRangeSelected,
        )
        HourlyForecastCard(forecast.hourly, unitSystem)

        AdaptiveTwoColumn(
            left = { modifier -> PrecipitationCard(modifier, forecast, unitSystem) },
            right = { modifier -> WindCard(modifier, forecast, unitSystem) },
        )

        AdaptiveTwoColumn(
            left = { modifier -> SevenDayForecastCard(modifier, forecast, unitSystem) },
            right = { modifier ->
                Column(
                    modifier = modifier,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    RainfallOutlookCard(Modifier.fillMaxWidth(), forecast, unitSystem)
                    SunriseSunsetCard(Modifier.fillMaxWidth(), forecast)
                    DryWindowCard(Modifier.fillMaxWidth(), forecast)
                }
            },
        )
        Spacer(modifier = Modifier.height(4.dp))
    }
}

@Composable
private fun WeatherHeroCard(
    forecast: DashboardForecast,
    unitSystem: UnitSystem,
    backgroundWeather: DummyWeather,
) {
    val context = LocalContext.current
    val bitmap = remember(context, backgroundWeather.condition, backgroundWeather.isDay) {
        BackplateLoader.bitmap(context, backgroundWeather)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(238.dp)
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)),
            ) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Partly cloudy sky over Austin",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color(0xE6EAF7FF),
                                    Color(0xBBD7F1FF),
                                    Color(0x150E7DC7),
                                ),
                            ),
                        ),
                )
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.LocationOn,
                            contentDescription = null,
                            tint = DeepBlue,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = forecast.location,
                            color = DeepBlue,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Icon(
                            imageVector = Icons.Outlined.ChevronRight,
                            contentDescription = null,
                            tint = DeepBlue,
                            modifier = Modifier.size(17.dp),
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Rain starts in",
                        color = Navy,
                        fontSize = 28.sp,
                        lineHeight = 30.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = forecast.rainStartsIn,
                        color = Navy,
                        fontSize = 38.sp,
                        lineHeight = 40.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(1.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = forecast.temperature(forecast.currentFahrenheit, unitSystem),
                            color = DarkBlue,
                            fontSize = 64.sp,
                            lineHeight = 65.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                                .width(1.dp)
                                .height(47.dp)
                                .background(Color(0x66516E93)),
                        )
                        Column(modifier = Modifier.padding(bottom = 6.dp)) {
                            Text(
                                text = "Feels like ${forecast.temperature(forecast.feelsLikeFahrenheit, unitSystem)}",
                                color = Navy,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                text = "H ${forecast.temperature(forecast.highFahrenheit, unitSystem)}   " +
                                    "L ${forecast.temperature(forecast.lowFahrenheit, unitSystem)}",
                                color = Navy,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 1.dp),
                    ) {
                        ConditionIcon(
                            condition = WeatherCondition.PARTLY_CLOUDY,
                            modifier = Modifier.size(25.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = forecast.conditionLabel,
                            color = Navy,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(66.dp)
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HeroMetric(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.WaterDrop,
                    value = "${forecast.precipitationChance}%",
                    label = "Chance of Rain",
                )
                MetricDivider()
                HeroMetric(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.WaterDrop,
                    value = forecast.precipitation(forecast.expectedRainInches, unitSystem),
                    label = "Expected Rain",
                )
                MetricDivider()
                HeroMetric(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.Air,
                    value = forecast.windSpeed(forecast.peakWindMph, unitSystem),
                    label = "Peak Wind (${forecast.peakWindDirection})",
                )
            }
        }
    }
}

@Composable
private fun HeroMetric(
    modifier: Modifier,
    icon: ImageVector,
    value: String,
    label: String,
) {
    Row(
        modifier = modifier.padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = AccentBlue,
            modifier = Modifier.size(26.dp),
        )
        Spacer(modifier = Modifier.width(5.dp))
        Column {
            Text(
                text = value,
                color = DeepBlue,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            Text(
                text = label,
                color = MutedNavy,
                fontSize = 9.sp,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun MetricDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(34.dp)
            .background(Color(0xFFDCE8F1)),
    )
}

@Composable
private fun ForecastRangeSelector(
    selected: ForecastRange,
    onSelected: (ForecastRange) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xFFE6F0F9))
            .padding(3.dp),
    ) {
        ForecastRange.entries.forEach { range ->
            val isSelected = selected == range
            Text(
                text = range.label,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(19.dp))
                    .background(if (isSelected) AccentBlue else Color.Transparent)
                    .clickable { onSelected(range) }
                    .padding(vertical = 7.dp),
                color = if (isSelected) Color.White else DeepBlue,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun HourlyForecastCard(
    hourly: List<HourlyForecast>,
    unitSystem: UnitSystem,
) {
    DashboardCard(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(title = "Hourly Precipitation, Temperature & Wind", action = "Next 10 Hours")
        Spacer(modifier = Modifier.height(8.dp))
        Column(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            HourlyHeaderRow(hourly)
            HourlyChanceRow(hourly)
            HourlyValueRow(
                label = "Rainfall\n(${if (unitSystem == UnitSystem.IMPERIAL) "in" else "mm"})",
                values = hourly.map { it.rainfallInches.toRainValue(unitSystem) },
                color = AccentBlue,
            )
            HourlyValueRow(
                label = "Temp\n(${unitSystem.temperatureUnitLabel()})",
                values = hourly.map { it.temperature(unitSystem) },
                color = Orange,
            )
            HourlyValueRow(
                label = "Wind\n(${if (unitSystem == UnitSystem.IMPERIAL) "mph" else "km/h"})",
                values = hourly.map { it.windSpeed(unitSystem) },
                color = DeepBlue,
            )
            HourlyDirectionRow(hourly)
        }
    }
}

@Composable
private fun HourlyHeaderRow(hourly: List<HourlyForecast>) {
    Row(modifier = Modifier.width(64.dp + (hourly.size * 50).dp)) {
        TableLabel(text = "", modifier = Modifier.width(64.dp))
        hourly.forEach { item ->
            TableCell(
                text = item.time,
                modifier = Modifier.width(50.dp),
                color = DeepBlue,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun HourlyChanceRow(hourly: List<HourlyForecast>) {
    Row(
        modifier = Modifier.width(64.dp + (hourly.size * 50).dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        TableLabel(text = "Precip\nChance", modifier = Modifier.width(64.dp))
        hourly.forEach { item ->
            Column(
                modifier = Modifier.width(50.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "${item.precipitationChance}%",
                    color = AccentBlue,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
                Box(
                    modifier = Modifier
                        .height(40.dp)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Box(
                        modifier = Modifier
                            .width(27.dp)
                            .height((4 + item.precipitationChance * 0.32f).dp)
                            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                            .background(AccentBlue.copy(alpha = 0.88f)),
                    )
                }
            }
        }
    }
}

@Composable
private fun HourlyValueRow(
    label: String,
    values: List<String>,
    color: Color,
) {
    Row(
        modifier = Modifier
            .width(64.dp + (values.size * 50).dp)
            .heightIn(min = 28.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TableLabel(text = label, modifier = Modifier.width(64.dp))
        values.forEach { value ->
            TableCell(
                text = value,
                modifier = Modifier.width(50.dp),
                color = color,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun HourlyDirectionRow(hourly: List<HourlyForecast>) {
    Row(
        modifier = Modifier
            .width(64.dp + (hourly.size * 50).dp)
            .heightIn(min = 35.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TableLabel(text = "Wind Dir", modifier = Modifier.width(64.dp))
        hourly.forEach { item ->
            Column(
                modifier = Modifier.width(50.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(text = "↓", color = Aqua, fontSize = 13.sp, lineHeight = 12.sp)
                Text(
                    text = item.windDirectionLabel,
                    color = DeepBlue,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun TableLabel(text: String, modifier: Modifier) {
    Text(
        text = text,
        modifier = modifier.padding(vertical = 3.dp),
        color = MutedNavy,
        fontSize = 9.sp,
        lineHeight = 10.sp,
        fontWeight = FontWeight.Medium,
    )
}

@Composable
private fun TableCell(
    text: String,
    modifier: Modifier,
    color: Color,
    fontWeight: FontWeight,
) {
    Text(
        text = text,
        modifier = modifier.padding(vertical = 3.dp),
        color = color,
        fontSize = 10.sp,
        lineHeight = 11.sp,
        fontWeight = fontWeight,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun PrecipitationCard(
    modifier: Modifier,
    forecast: DashboardForecast,
    unitSystem: UnitSystem,
) {
    DashboardCard(modifier = modifier) {
        SectionHeader(title = "Precipitation Next 24h", action = "Next 24 Hours", icon = Icons.Outlined.WaterDrop)
        Spacer(modifier = Modifier.height(5.dp))
        AreaChart(
            points = forecast.precipitation24h,
            lineColor = ChartBlue,
            fillColor = ChartBlue.copy(alpha = 0.25f),
            maxValue = 1f,
        )
        Spacer(modifier = Modifier.height(3.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.WaterDrop,
                contentDescription = null,
                tint = AccentBlue,
                modifier = Modifier.size(22.dp),
            )
            Spacer(modifier = Modifier.width(5.dp))
            Column {
                Text(
                    text = "${forecast.precipitation(forecast.expectedRainInches, unitSystem)} total",
                    color = DeepBlue,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Rain through tomorrow 10 AM",
                    color = MutedNavy,
                    fontSize = 10.sp,
                )
            }
        }
    }
}

@Composable
private fun WindCard(
    modifier: Modifier,
    forecast: DashboardForecast,
    unitSystem: UnitSystem,
) {
    DashboardCard(modifier = modifier) {
        SectionHeader(title = "Wind by Hour", action = "Next 24 Hours", icon = Icons.Outlined.Air)
        Spacer(modifier = Modifier.height(5.dp))
        LineChart(
            points = forecast.windByHour,
            lineColor = Aqua,
            fillColor = Aqua.copy(alpha = 0.12f),
            maxValue = 20f,
        )
        Spacer(modifier = Modifier.height(3.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.Air,
                contentDescription = null,
                tint = Aqua,
                modifier = Modifier.size(22.dp),
            )
            Spacer(modifier = Modifier.width(5.dp))
            Column {
                Text(
                    text = forecast.windSpeed(forecast.peakWindMph, unitSystem),
                    color = DeepBlue,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Peak at 2 PM (${forecast.peakWindDirection})",
                    color = MutedNavy,
                    fontSize = 10.sp,
                )
            }
        }
    }
}

@Composable
private fun SevenDayForecastCard(
    modifier: Modifier,
    forecast: DashboardForecast,
    unitSystem: UnitSystem,
) {
    DashboardCard(modifier = modifier) {
        SectionHeader(title = "7-Day Forecast", action = "More Details")
        Spacer(modifier = Modifier.height(3.dp))
        forecast.daily.forEach { day ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 30.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = day.day,
                    modifier = Modifier.width(35.dp),
                    color = DeepBlue,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
                ConditionIcon(day.condition, Modifier.size(22.dp))
                Text(
                    text = day.conditionLabel,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 5.dp),
                    color = DeepBlue,
                    fontSize = 9.sp,
                    lineHeight = 10.sp,
                )
                Text(
                    text = "${day.precipitationChance}%  ${forecast.precipitation(day.rainfallInches, unitSystem)}",
                    modifier = Modifier.widthIn(min = 62.dp),
                    color = AccentBlue,
                    fontSize = 8.sp,
                    textAlign = TextAlign.End,
                )
                Text(
                    text = forecast.temperature(day.highFahrenheit, unitSystem),
                    modifier = Modifier.width(31.dp),
                    color = Orange,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.End,
                )
                Text(
                    text = forecast.temperature(day.lowFahrenheit, unitSystem),
                    modifier = Modifier.width(31.dp),
                    color = AccentBlue,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

@Composable
private fun RainfallOutlookCard(
    modifier: Modifier,
    forecast: DashboardForecast,
    unitSystem: UnitSystem,
) {
    DashboardCard(modifier = modifier) {
        SectionHeader(title = "Rainfall Outlook", action = "Next 7 Days", icon = Icons.Outlined.Air)
        Spacer(modifier = Modifier.height(4.dp))
        BarChart(
            points = forecast.rainfallOutlook,
            valueLabel = { forecast.precipitation(it.toDouble(), unitSystem).substringBefore(' ') },
        )
    }
}

@Composable
private fun SunriseSunsetCard(
    modifier: Modifier,
    forecast: DashboardForecast,
) {
    DashboardCard(modifier = modifier) {
        SectionHeader(title = "Sunrise & Sunset", icon = Icons.Outlined.WbSunny)
        Spacer(modifier = Modifier.height(2.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(text = forecast.sunrise, color = DeepBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(text = "Sunrise", color = MutedNavy, fontSize = 9.sp)
            }
            Icon(
                imageVector = Icons.Outlined.WbSunny,
                contentDescription = null,
                tint = Color(0xFFF6B632),
                modifier = Modifier.size(29.dp),
            )
            Column(horizontalAlignment = Alignment.End) {
                Text(text = forecast.sunset, color = DeepBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(text = "Sunset", color = MutedNavy, fontSize = 9.sp)
            }
        }
    }
}

@Composable
private fun DryWindowCard(
    modifier: Modifier,
    forecast: DashboardForecast,
) {
    DashboardCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.Umbrella,
                contentDescription = null,
                tint = AccentBlue,
                modifier = Modifier.size(22.dp),
            )
            Spacer(modifier = Modifier.width(7.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Dry Window", color = DeepBlue, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(text = "Best chance of staying dry", color = MutedNavy, fontSize = 9.sp)
            }
            Text(
                text = forecast.dryWindow,
                color = DeepBlue,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    action: String? = null,
    icon: ImageVector? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = AccentBlue,
                modifier = Modifier.size(17.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
        }
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            color = DeepBlue,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
        if (action != null) {
            Text(text = action, color = AccentBlue, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun DashboardCard(
    modifier: Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(17.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        border = BorderStroke(1.dp, CardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp), content = content)
    }
}

@Composable
private fun AdaptiveTwoColumn(
    left: @Composable (Modifier) -> Unit,
    right: @Composable (Modifier) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        if (maxWidth >= 560.dp) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                left(Modifier.weight(1f))
                right(Modifier.weight(1f))
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                left(Modifier.fillMaxWidth())
                right(Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun AreaChart(
    points: List<ChartPoint>,
    lineColor: Color,
    fillColor: Color,
    maxValue: Float,
) {
    LineChart(points, lineColor, fillColor, maxValue)
}

@Composable
private fun LineChart(
    points: List<ChartPoint>,
    lineColor: Color,
    fillColor: Color,
    maxValue: Float,
) {
    val safeMax = max(maxValue, points.maxOfOrNull { it.value } ?: 1f)
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(98.dp)
            .padding(horizontal = 4.dp),
    ) {
        val chartWidth = size.width
        val chartHeight = size.height - 5.dp.toPx()
        val xStep = if (points.size <= 1) chartWidth else chartWidth / (points.size - 1)
        val coordinates = points.mapIndexed { index, point ->
            Offset(index * xStep, chartHeight - (point.value / safeMax) * chartHeight)
        }

        for (line in 1..3) {
            val y = chartHeight * line / 4f
            drawLine(
                color = ChartGrid,
                start = Offset(0f, y),
                end = Offset(chartWidth, y),
                strokeWidth = 1.dp.toPx(),
            )
        }

        if (coordinates.isNotEmpty()) {
            val fillPath = Path().apply {
                moveTo(coordinates.first().x, chartHeight)
                coordinates.forEach { point -> lineTo(point.x, point.y) }
                lineTo(coordinates.last().x, chartHeight)
                close()
            }
            drawPath(fillPath, color = fillColor)

            val linePath = Path().apply {
                moveTo(coordinates.first().x, coordinates.first().y)
                coordinates.drop(1).forEach { point -> lineTo(point.x, point.y) }
            }
            drawPath(
                path = linePath,
                color = lineColor,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                ),
            )
            coordinates.forEach { point ->
                drawCircle(color = lineColor, radius = 3.dp.toPx(), center = point)
            }
        }
    }
    Row(modifier = Modifier.fillMaxWidth()) {
        points.forEach { point ->
            Text(
                text = point.label,
                modifier = Modifier.weight(1f),
                color = MutedNavy,
                fontSize = 7.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun BarChart(
    points: List<ChartPoint>,
    valueLabel: (Float) -> String,
) {
    val maxValue = max(0.1f, points.maxOfOrNull { it.value } ?: 1f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(112.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        points.forEach { point ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                Text(
                    text = valueLabel(point.value),
                    color = DeepBlue,
                    fontSize = 7.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .width(14.dp)
                        .height((point.value / maxValue * 65f).dp.coerceAtLeast(2.dp))
                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                        .background(ChartBlue),
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(text = point.label, color = MutedNavy, fontSize = 7.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun ConditionIcon(condition: WeatherCondition, modifier: Modifier) {
    val (icon, tint) = when (condition) {
        WeatherCondition.CLEAR, WeatherCondition.MOSTLY_CLEAR -> Icons.Outlined.WbSunny to Color(0xFFF4B52D)
        WeatherCondition.PARTLY_CLOUDY -> Icons.Outlined.CloudQueue to Color(0xFF6795C4)
        WeatherCondition.SNOW, WeatherCondition.HEAVY_SNOW, WeatherCondition.WINTRY_MIX -> Icons.Outlined.AcUnit to AccentBlue
        WeatherCondition.THUNDERSTORM, WeatherCondition.SEVERE_WEATHER -> Icons.Outlined.Air to Color(0xFF5F77A7)
        else -> Icons.Outlined.WaterDrop to AccentBlue
    }
    Icon(imageVector = icon, contentDescription = condition.name, tint = tint, modifier = modifier)
}

@Composable
private fun SettingsScreen(
    weather: DummyWeather,
    unitSystem: UnitSystem,
    selectedBackplate: BackplateChoice,
    selectedBackplateIndex: Int,
    onUnitSystemSelected: (UnitSystem) -> Unit,
    onPreviousBackplate: () -> Unit,
    onNextBackplate: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = "Settings", color = Navy, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(
            text = "Tune the dashboard and home-screen widget.",
            color = MutedNavy,
            fontSize = 13.sp,
        )
        DashboardCard(modifier = Modifier.fillMaxWidth()) {
            Text(text = "Temperature units", color = Navy, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            UnitSelector(selected = unitSystem, onSelected = onUnitSystemSelected)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "The first launch follows your device locale. Manual changes are shared with the widget.",
                color = MutedNavy,
                fontSize = 11.sp,
            )
        }
        DashboardCard(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Outlined.Tune, contentDescription = null, tint = AccentBlue)
                Spacer(modifier = Modifier.width(6.dp))
                Column {
                    Text(text = "Widget appearance", color = Navy, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(text = "Choose the illustrated weather backplate.", color = MutedNavy, fontSize = 11.sp)
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            WeatherPreviewCard(weather = weather, unitSystem = unitSystem)
            BackplateBrowser(
                selected = selectedBackplate,
                index = selectedBackplateIndex,
                onPrevious = onPreviousBackplate,
                onNext = onNextBackplate,
            )
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
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFFE4EDF5))
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
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) Color.White else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            color = if (selected) DeepBlue else MutedNavy,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(text = subtitle, color = MutedNavy, fontSize = 10.sp)
    }
}

@Composable
private fun WeatherPreviewCard(weather: DummyWeather, unitSystem: UnitSystem) {
    val context = LocalContext.current
    val bitmap = remember(context, weather.condition, weather.isDay) {
        BackplateLoader.bitmap(context, weather)
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(17.dp)),
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Weather widget preview",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.horizontalGradient(listOf(Color(0xCC0A365B), Color(0x180A365B)))),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Bottom,
        ) {
            Text(text = weather.location, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(
                text = weather.temperature(unitSystem),
                color = Color.White,
                fontSize = 42.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "${weather.conditionLabel} · ${weather.highLow(unitSystem)}",
                color = Color.White,
                fontSize = 11.sp,
            )
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
            .padding(top = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        TextButton(onClick = onPrevious) {
            Icon(imageVector = Icons.Outlined.ChevronLeft, contentDescription = null)
            Text(text = "Previous")
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = selected.label, color = Navy, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(text = "${index + 1} of ${BackplateChoices.size}", color = MutedNavy, fontSize = 10.sp)
        }
        TextButton(onClick = onNext) {
            Text(text = "Next")
            Icon(imageVector = Icons.Outlined.ChevronRight, contentDescription = null)
        }
    }
}

@Composable
private fun PlaceholderScreen(tab: DashboardTab) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(Color(0xFFDCEEFF)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = tab.icon(), contentDescription = null, tint = AccentBlue, modifier = Modifier.size(38.dp))
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = tab.label, color = Navy, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "This view is ready for the next weather data layer.",
            color = MutedNavy,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
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

private fun Double.toRainValue(unitSystem: UnitSystem): String = when (unitSystem) {
    UnitSystem.IMPERIAL -> String.format(Locale.US, "%.2f", this)
    UnitSystem.METRIC -> String.format(Locale.US, "%.1f", this * 25.4)
}

private fun HourlyForecast.temperature(unitSystem: UnitSystem): String =
    MockDashboardData.current.temperature(temperatureFahrenheit, unitSystem)

private fun HourlyForecast.windSpeed(unitSystem: UnitSystem): String {
    val value = when (unitSystem) {
        UnitSystem.IMPERIAL -> windMph
        UnitSystem.METRIC -> (windMph * 1.60934).roundToInt()
    }
    return value.toString()
}

private val DashboardBackground = Color(0xFFF0F7FD)
private val CardWhite = Color(0xFFFBFDFF)
private val CardBorder = Color(0xFFD7E7F3)
private val Navy = Color(0xFF12346D)
private val DarkBlue = Color(0xFF062D73)
private val DeepBlue = Color(0xFF164B91)
private val MutedNavy = Color(0xFF607792)
private val AccentBlue = Color(0xFF168CE4)
private val ChartBlue = Color(0xFF328EE1)
private val Aqua = Color(0xFF14B7B4)
private val Orange = Color(0xFFE56E2B)
private val ChartGrid = Color(0xFFD9E7F2)
