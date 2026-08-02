package com.raindepartment.weather

import android.Manifest
import android.app.Activity
import android.content.Context
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Build
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.ScrollView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.GpsFixed
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.LocationCity
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Umbrella
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.raindepartment.weather.update.AppUpdateManager
import com.raindepartment.weather.update.UpdateRelease
import com.raindepartment.weather.update.UpdateUiState
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.abs
import java.util.Locale
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.max
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = true
        }
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
internal fun RainDepartmentApp(
    repository: WeatherRepository? = null,
    radarClient: EcccRadarMapClient? = null,
    requestLocationPermission: Boolean = true,
    checkForUpdates: Boolean = true,
    updateWidget: Boolean = true,
    // Keep native scrolling in production so Android long screenshots capture the full briefing.
    useNativeBriefingScrollCapture: Boolean = true,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current
    val weatherRepository = repository ?: remember(context) {
        WeatherRepositoryFactory.create(context.applicationContext)
    }
    val radarMapClient = radarClient ?: remember(context) {
        HttpEcccRadarClient()
    }
    val weatherState by weatherRepository.state.collectAsStateWithLifecycle()
    val weatherScope = rememberCoroutineScope()
    var locationPermissionRequested by rememberSaveable { mutableStateOf(false) }
    var selectedCityLocation by remember(context) {
        mutableStateOf(WeatherPreferences.selectedLocation(context))
    }
    var isCityPickerVisible by rememberSaveable { mutableStateOf(false) }
    val clearSelectedCityIfCurrentLocationUsed: (RefreshResult, WeatherLocation?) -> Unit =
        { result, fallbackLocation ->
            if (result is RefreshResult.Updated &&
                fallbackLocation != null &&
                result.snapshot.location != fallbackLocation &&
                selectedCityLocation == fallbackLocation
            ) {
                selectedCityLocation = null
                WeatherPreferences.clearSelectedLocation(context)
            }
        }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        val preciseGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val fallbackLocation = selectedCityLocation
        weatherScope.launch {
            val result = weatherRepository.refresh(
                force = true,
                updateLocation = preciseGranted,
                locationOverride = if (preciseGranted) null else fallbackLocation,
            )
            if (preciseGranted) {
                clearSelectedCityIfCurrentLocationUsed(result, fallbackLocation)
            }
        }
    }
    var notificationPermissionRequested by rememberSaveable { mutableStateOf(false) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            weatherState.snapshot?.let { snapshot ->
                RainNotificationManager.notifyIfMeaningful(context, snapshot)
            }
        }
    }
    val updateManager = remember(context) {
        AppUpdateManager(context.applicationContext)
    }
    val updateState by updateManager.state.collectAsStateWithLifecycle()
    val updateScope = rememberCoroutineScope()

    var selectedTabName by rememberSaveable { mutableStateOf(DashboardTab.BRIEFING.name) }
    val selectedTab = DashboardTab.valueOf(selectedTabName)
    var selectedRangeName by rememberSaveable { mutableStateOf(ForecastRange.TODAY.name) }
    val selectedRange = ForecastRange.valueOf(selectedRangeName)
    var selectedDayIndex by rememberSaveable { mutableIntStateOf(-1) }
    var unitSystem by remember { mutableStateOf(WeatherPreferences.unitSystem(context)) }
    val currentWeather = weatherState.snapshot?.forecast?.currentWeather()

    val refreshForecast: () -> Unit = {
        weatherScope.launch {
            weatherRepository.refresh(
                force = true,
                updateLocation = false,
                locationOverride = selectedCityLocation,
            )
        }
    }
    val refreshForecastAndCheckForUpdates: () -> Unit = {
        weatherScope.launch {
            if (checkForUpdates) {
                launch { updateManager.check() }
            }
            weatherRepository.refresh(
                force = true,
                updateLocation = false,
                locationOverride = selectedCityLocation,
            )
        }
    }
    val refreshLocation: () -> Unit = {
        if (context.hasPreciseLocationPermission()) {
            val fallbackLocation = selectedCityLocation
            weatherScope.launch {
                val result = weatherRepository.refresh(force = true, updateLocation = true)
                clearSelectedCityIfCurrentLocationUsed(result, fallbackLocation)
            }
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                ),
            )
        }
    }
    val selectCity: (WeatherLocation) -> Unit = { location ->
        selectedDayIndex = -1
        selectedCityLocation = location
        WeatherPreferences.setSelectedLocation(context, location)
        weatherScope.launch {
            weatherRepository.refresh(
                force = true,
                locationOverride = location,
            )
        }
    }

    LaunchedEffect(updateManager, checkForUpdates) {
        if (checkForUpdates) updateManager.check()
    }

    LaunchedEffect(weatherRepository, requestLocationPermission) {
        if (requestLocationPermission) {
            WeatherRefreshScheduler.schedule(context.applicationContext)
        }
        when {
            requestLocationPermission &&
                selectedCityLocation == null &&
                !context.hasPreciseLocationPermission() &&
                !locationPermissionRequested -> {
                locationPermissionRequested = true
                locationPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.ACCESS_FINE_LOCATION,
                    ),
                )
            }
            else -> {
                weatherRepository.refresh(
                    updateLocation = requestLocationPermission &&
                        selectedCityLocation == null &&
                        context.hasPreciseLocationPermission(),
                    locationOverride = selectedCityLocation,
                )
            }
        }
    }

    LaunchedEffect(weatherState.snapshot?.fetchedAtEpochMillis, repository, updateWidget) {
        if (updateWidget && repository == null && weatherState.snapshot != null) {
            WeatherWidget.updateAll(context.applicationContext)
        }
    }

    LaunchedEffect(weatherState.snapshot?.fetchedAtEpochMillis, requestLocationPermission) {
        val snapshot = weatherState.snapshot ?: return@LaunchedEffect
        if (requestLocationPermission) {
            WeatherRefreshScheduler.schedule(context.applicationContext, snapshot)
            if (snapshot.forecast.rainStartConfidenceMeaningful &&
                snapshot.forecast.rainStartMinutesFromNow(System.currentTimeMillis())
                    ?.let { it <= 60L } == true &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                !RainNotificationManager.hasPermission(context) &&
                !notificationPermissionRequested
            ) {
                notificationPermissionRequested = true
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        RainNotificationManager.notifyIfMeaningful(context, snapshot)
    }

    DisposableEffect(updateManager, weatherRepository, lifecycleOwner, activity) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (checkForUpdates && activity != null) updateManager.resumeInstall(activity)
                weatherScope.launch { weatherRepository.refresh() }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(unitSystem, updateWidget) {
        WeatherPreferences.setUnitSystem(context, unitSystem)
        if (updateWidget) WeatherWidget.updateAll(context.applicationContext)
    }

    Scaffold(
        containerColor = DashboardBackground,
        topBar = {
            RainDepartmentHeader(
                onRefreshLocation = refreshLocation,
                onChooseCity = { isCityPickerVisible = true },
                isRefreshing = weatherState.isRefreshing,
            )
        },
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
                DashboardTab.BRIEFING -> RefreshableBriefingContent(
                    isRefreshing = weatherState.isRefreshing,
                    onRefresh = refreshForecastAndCheckForUpdates,
                ) {
                    weatherState.snapshot?.let { snapshot ->
                        BriefingScreen(
                            forecast = snapshot.forecast,
                            unitSystem = unitSystem,
                            backgroundWeather = snapshot.forecast.currentWeather(),
                            selectedRange = selectedRange,
                            onRangeSelected = {
                                selectedRangeName = it.name
                                selectedDayIndex = -1
                            },
                            selectedDayIndex = selectedDayIndex,
                            onDaySelected = {
                                selectedRangeName = ForecastRange.SEVEN_DAYS.name
                                selectedDayIndex = it
                            },
                            onDayDetailBack = { selectedDayIndex = -1 },
                            isRefreshing = weatherState.isRefreshing,
                            isStale = weatherState.isStale,
                            errorMessage = weatherState.errorMessage,
                            onLocationClick = { isCityPickerVisible = true },
                            onRefresh = refreshForecastAndCheckForUpdates,
                            useNativeScrollCapture = useNativeBriefingScrollCapture,
                        )
                    } ?: BriefingUnavailableScreen(
                        isRefreshing = weatherState.isRefreshing,
                        errorMessage = weatherState.errorMessage,
                    )
                }

                DashboardTab.SETTINGS -> SettingsScreen(
                    weather = currentWeather,
                    unitSystem = unitSystem,
                    onUnitSystemSelected = { unitSystem = it },
                )

                DashboardTab.RADAR -> RadarScreen(
                    location = selectedCityLocation ?: weatherState.snapshot?.location,
                    forecast = weatherState.snapshot?.forecast,
                    radarClient = radarMapClient,
                )

                else -> PlaceholderScreen(tab = selectedTab)
            }
        }
    }

    if (isCityPickerVisible) {
        CityPickerDialog(
            selectedLocation = selectedCityLocation,
            currentLocation = selectedCityLocation ?: weatherState.snapshot?.location,
            currentForecast = weatherState.snapshot?.forecast,
            unitSystem = unitSystem,
            onSelect = { location ->
                isCityPickerVisible = false
                selectCity(location)
            },
            onUseCurrentLocation = {
                isCityPickerVisible = false
                refreshLocation()
            },
            onDismiss = { isCityPickerVisible = false },
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
private fun RainDepartmentHeader(
    onRefreshLocation: () -> Unit,
    onChooseCity: () -> Unit,
    isRefreshing: Boolean,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF2395F1), Color(0xFF39A8F2)),
                ),
            ),
    ) {
        Column(modifier = Modifier.statusBarsPadding()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 8.dp),
            ) {
                IconButton(
                    onClick = onChooseCity,
                    modifier = Modifier.align(Alignment.CenterStart),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.LocationCity,
                        contentDescription = "Choose city",
                        tint = Color.White,
                        modifier = Modifier.size(27.dp),
                    )
                }
                Row(
                    modifier = Modifier.align(Alignment.Center),
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
                        maxLines = 1,
                    )
                }
                Row(
                    modifier = Modifier.align(Alignment.CenterEnd),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onRefreshLocation, enabled = !isRefreshing) {
                        Icon(
                            imageVector = Icons.Outlined.GpsFixed,
                            contentDescription = "Use precise location",
                            tint = Color.White,
                            modifier = Modifier.size(27.dp),
                        )
                    }
                }
            }
        }
    }
}

private enum class CityPickerFilter {
    NEARBY,
    RECENT,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CityPickerDialog(
    selectedLocation: WeatherLocation?,
    currentLocation: WeatherLocation?,
    currentForecast: DashboardForecast?,
    unitSystem: UnitSystem,
    onSelect: (WeatherLocation) -> Unit,
    onUseCurrentLocation: () -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var filterName by rememberSaveable { mutableStateOf(CityPickerFilter.NEARBY.name) }
    val selectedFilter = CityPickerFilter.valueOf(filterName)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val searchResults = WeatherCities.search(query)
    val nearbyCities = remember(currentLocation) {
        currentLocation?.let { WeatherCities.nearestTo(it, limit = 4) }.orEmpty()
    }
    val recentCities = remember(selectedLocation) {
        selectedLocation?.let { location ->
            listOf(
                WeatherCities.all.firstOrNull { it.location == location }
                    ?: WeatherCity(location.label, location.latitude, location.longitude),
            )
        }.orEmpty()
    }
    val visibleCities = when {
        query.isNotBlank() -> searchResults
        selectedFilter == CityPickerFilter.RECENT -> recentCities
        else -> nearbyCities
    }
    val forecastForCurrentLocation = currentForecast?.takeIf { forecast ->
        currentLocation?.label == forecast.location
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFFF8FBFF),
        scrimColor = Color(0x660D2946),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { CityPickerDragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Choose a city",
                    modifier = Modifier.weight(1f),
                    color = DeepBlue,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(42.dp)
                        .background(Color(0xFFEAF3FC), CircleShape),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Close city picker",
                        tint = DeepBlue,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = {
                    Text(
                        text = "Search cities, states or countries",
                        color = MutedNavy,
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = null,
                        tint = MutedNavy,
                    )
                },
                shape = RoundedCornerShape(15.dp),
            )
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CityPickerFilterChip(
                    label = "Nearby",
                    icon = Icons.Outlined.GpsFixed,
                    selected = selectedFilter == CityPickerFilter.NEARBY,
                    onClick = { filterName = CityPickerFilter.NEARBY.name },
                )
                CityPickerFilterChip(
                    label = "Recent",
                    icon = Icons.Outlined.AccessTime,
                    selected = selectedFilter == CityPickerFilter.RECENT,
                    onClick = { filterName = CityPickerFilter.RECENT.name },
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 220.dp, max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (query.isBlank() &&
                    selectedFilter == CityPickerFilter.NEARBY &&
                    currentLocation != null
                ) {
                    item {
                        CityPickerSectionTitle("Current location")
                    }
                    item {
                        CityPickerCurrentCard(
                            location = currentLocation,
                            forecast = forecastForCurrentLocation,
                            unitSystem = unitSystem,
                        )
                    }
                    item {
                        CityPickerSectionTitle("Nearby cities")
                    }
                } else {
                    item {
                        CityPickerSectionTitle(
                            when {
                                query.isNotBlank() -> "Search results"
                                selectedFilter == CityPickerFilter.RECENT -> "Recent cities"
                                else -> "Nearby cities"
                            },
                        )
                    }
                }

                if (visibleCities.isEmpty()) {
                    item {
                        Text(
                            text = if (query.isBlank()) {
                                "No recent cities yet"
                            } else {
                                "No matching cities"
                            },
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = MutedNavy,
                            fontSize = 13.sp,
                        )
                    }
                } else {
                    items(visibleCities, key = { it.label }) { city ->
                        CityPickerCityRow(
                            city = city,
                            selected = selectedLocation == city.location,
                            onClick = { onSelect(city.location) },
                        )
                    }
                }
                item {
                    CityPickerUseCurrentLocation(onClick = onUseCurrentLocation)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun CityPickerDragHandle() {
    Box(
        modifier = Modifier
            .padding(top = 10.dp, bottom = 4.dp)
            .size(width = 60.dp, height = 5.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFFB9C0C6)),
    )
}

@Composable
private fun CityPickerFilterChip(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        color = if (selected) AccentBlue else Color(0xFFEAF3FC),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) Color.White else DeepBlue,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = label,
                color = if (selected) Color.White else DeepBlue,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun CityPickerSectionTitle(title: String) {
    Text(
        text = title,
        modifier = Modifier.padding(top = 2.dp, bottom = 3.dp),
        color = MutedNavy,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun CityPickerCurrentCard(
    location: WeatherLocation,
    forecast: DashboardForecast?,
    unitSystem: UnitSystem,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(1.dp, Color(0xFFC9E5F2)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color(0xFFDDF2FF),
                            Color(0xFFEAF7FC),
                            Color(0xFFE5F3D7),
                        ),
                    ),
                )
                .padding(horizontal = 14.dp, vertical = 14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color.White.copy(alpha = 0.88f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.LocationOn,
                        contentDescription = null,
                        tint = AccentBlue,
                        modifier = Modifier.size(29.dp),
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = location.label,
                            modifier = Modifier.weight(1f),
                            color = DeepBlue,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = AccentBlue,
                        ) {
                            Text(
                                text = "Current",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    Text(
                        text = locationSubtitle(location.label),
                        color = MutedNavy,
                        fontSize = 12.sp,
                    )
                    Spacer(modifier = Modifier.height(5.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (forecast != null) {
                            ConditionIcon(forecast.condition, Modifier.size(19.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = forecast.conditionLabel,
                                color = DeepBlue,
                                fontSize = 11.sp,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.LocationOn,
                                contentDescription = null,
                                tint = AccentBlue,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Selected city",
                                color = DeepBlue,
                                fontSize = 11.sp,
                            )
                        }
                    }
                }
                if (forecast != null) {
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = forecast.temperature(forecast.currentFahrenheit, unitSystem),
                            color = DeepBlue,
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Feels like ${forecast.temperature(forecast.feelsLikeFahrenheit, unitSystem)}",
                            color = MutedNavy,
                            fontSize = 10.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CityPickerCityRow(
    city: WeatherCity,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(
            1.dp,
            if (selected) Color(0xFF9CCEF0) else Color(0xFFE1EAF1),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(Color(0xFFEAF3FC), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.LocationCity,
                    contentDescription = null,
                    tint = AccentBlue,
                    modifier = Modifier.size(23.dp),
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = city.label,
                    color = if (selected) AccentBlue else DeepBlue,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = locationSubtitle(city.label),
                    color = MutedNavy,
                    fontSize = 12.sp,
                )
            }
            if (selected) {
                Text(
                    text = "Selected",
                    color = AccentBlue,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = Color(0xFF9AB4CC),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun CityPickerUseCurrentLocation(onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, Color(0xFFD5E1EC)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.GpsFixed,
                contentDescription = null,
                tint = AccentBlue,
                modifier = Modifier.size(27.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Use approximate location",
                    color = DeepBlue,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Ask the device for your current city",
                    color = MutedNavy,
                    fontSize = 10.sp,
                )
            }
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = Color(0xFF9AB4CC),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

private val UnitedStatesRegions = setOf(
    "Alabama",
    "Alaska",
    "Arizona",
    "Arkansas",
    "California",
    "Colorado",
    "Connecticut",
    "Delaware",
    "Florida",
    "Georgia",
    "Hawaii",
    "Idaho",
    "Illinois",
    "Indiana",
    "Iowa",
    "Kansas",
    "Kentucky",
    "Louisiana",
    "Maine",
    "Maryland",
    "Massachusetts",
    "Michigan",
    "Minnesota",
    "Mississippi",
    "Missouri",
    "Montana",
    "Nebraska",
    "Nevada",
    "New Hampshire",
    "New Jersey",
    "New Mexico",
    "New York",
    "North Carolina",
    "North Dakota",
    "Ohio",
    "Oklahoma",
    "Oregon",
    "Pennsylvania",
    "Rhode Island",
    "South Carolina",
    "South Dakota",
    "Tennessee",
    "Texas",
    "Utah",
    "Vermont",
    "Virginia",
    "Washington",
    "West Virginia",
    "Wisconsin",
    "Wyoming",
    "D.C.",
)

private val CanadianRegions = setOf(
    "Alberta",
    "British Columbia",
    "Manitoba",
    "New Brunswick",
    "Newfoundland and Labrador",
    "Nova Scotia",
    "Nunavut",
    "Ontario",
    "Prince Edward Island",
    "Quebec",
    "Québec",
    "Saskatchewan",
    "Northwest Territories",
    "Yukon",
)

private fun locationSubtitle(label: String): String {
    val region = label.substringAfter(", ", missingDelimiterValue = "")
    return when {
        region.isBlank() -> "Current location"
        region in UnitedStatesRegions -> "$region, USA"
        region in CanadianRegions -> "$region, Canada"
        else -> region
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
            .background(Color.White)
            .navigationBarsPadding(),
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

private const val RADAR_SCREEN_REFRESH_INTERVAL_MILLIS = 6 * 60_000L

private data class RadarUiState(
    val window: EcccRadarTimeWindow? = null,
    val frame: EcccRadarMapFrame? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
)

@Composable
private fun RadarScreen(
    location: WeatherLocation?,
    forecast: DashboardForecast?,
    radarClient: EcccRadarMapClient,
) {
    var state by remember(location) { mutableStateOf(RadarUiState()) }
    var selectedFrameTime by remember(location) { mutableStateOf<Long?>(null) }
    var isPlaying by remember(location) { mutableStateOf(false) }
    val radarScope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(location, radarClient, lifecycleOwner) {
        if (location == null) {
            state = RadarUiState(isLoading = false)
            return@LaunchedEffect
        }

        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (isActive) {
                val result = runCatching {
                    radarClient.fetchLatest(
                        location = location,
                        nowEpochMillis = System.currentTimeMillis(),
                    )
                }
                val data = result.getOrNull()
                val selected = selectedFrameTime
                if (data != null) {
                    val selectedIsAvailable = selected != null &&
                        data.window.frameTimes().contains(selected)
                    state = if (selectedIsAvailable) {
                        state.copy(
                            window = data.window,
                            isLoading = false,
                            errorMessage = null,
                        )
                    } else {
                        selectedFrameTime = null
                        RadarUiState(
                            window = data.window,
                            frame = data.frame,
                            isLoading = false,
                            errorMessage = null,
                        )
                    }
                } else {
                    state = state.copy(
                        isLoading = false,
                        errorMessage = result.exceptionOrNull()?.message
                            ?: "ECCC radar is temporarily unavailable.",
                    )
                }
                delay(RADAR_SCREEN_REFRESH_INTERVAL_MILLIS)
            }
        }
    }

    val requestFrame: (Long) -> Unit = { timeEpochMillis ->
        val currentLocation = location
        if (currentLocation != null) {
            selectedFrameTime = timeEpochMillis
            radarScope.launch {
                state = state.copy(isLoading = true, errorMessage = null)
                val result = runCatching {
                    radarClient.fetchFrame(
                        location = currentLocation,
                        timeEpochMillis = timeEpochMillis,
                    )
                }
                val frame = result.getOrNull()
                state = if (frame != null) {
                    state.copy(
                        frame = frame,
                        isLoading = false,
                        errorMessage = null,
                    )
                } else {
                    state.copy(
                        isLoading = false,
                        errorMessage = result.exceptionOrNull()?.message
                            ?: "That radar frame could not be loaded.",
                    )
                }
            }
        }
    }

    val requestLatest: () -> Unit = {
        val currentLocation = location
        if (currentLocation != null) {
            selectedFrameTime = null
            radarScope.launch {
                state = state.copy(isLoading = true, errorMessage = null)
                val result = runCatching {
                    radarClient.fetchLatest(
                        location = currentLocation,
                        nowEpochMillis = System.currentTimeMillis(),
                    )
                }
                val data = result.getOrNull()
                state = if (data != null) {
                    RadarUiState(
                        window = data.window,
                        frame = data.frame,
                        isLoading = false,
                        errorMessage = null,
                    )
                } else {
                    state.copy(
                        isLoading = false,
                        errorMessage = result.exceptionOrNull()?.message
                            ?: "ECCC radar is temporarily unavailable.",
                    )
                }
            }
        }
    }

    val frameTimes = state.window?.frameTimes().orEmpty()
    val currentFrameTime = state.frame?.timeEpochMillis
    val currentFrameIndex = frameTimes.indexOf(currentFrameTime).let { index ->
        if (index >= 0) index else frameTimes.lastIndex
    }

    if (location == null) {
        RadarLocationUnavailableScreen()
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        RadarMapCard(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            location = location,
            forecast = forecast,
            frame = state.frame,
            frameIndex = currentFrameIndex,
            frameTimes = frameTimes,
            isLoading = state.isLoading,
            errorMessage = state.errorMessage,
            isPlaying = isPlaying,
            onTogglePlayback = {
                isPlaying = !isPlaying
                if (!isPlaying) return@RadarMapCard
                val nextIndex = if (currentFrameIndex >= frameTimes.lastIndex) {
                    0
                } else {
                    currentFrameIndex + 1
                }
                frameTimes.getOrNull(nextIndex)?.let(requestFrame)
                isPlaying = false
            },
            onSelectFrame = requestFrame,
            onSelectLatest = requestLatest,
        )
    }

}

@Composable
private fun RadarMapCard(
    modifier: Modifier,
    location: WeatherLocation,
    forecast: DashboardForecast?,
    frame: EcccRadarMapFrame?,
    frameIndex: Int,
    frameTimes: List<Long>,
    isLoading: Boolean,
    errorMessage: String?,
    isPlaying: Boolean,
    onTogglePlayback: () -> Unit,
    onSelectFrame: (Long) -> Unit,
    onSelectLatest: () -> Unit,
) {
    val bitmap = remember(frame?.imageBytes) {
        frame?.imageBytes?.let { bytes ->
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }
    }
    val nowEpochMillis = System.currentTimeMillis()
    val arrival = forecast?.let { radarArrivalText(it, nowEpochMillis) }
    val confidence = forecast?.let { radarConfidenceText(it) }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFFE7F0E5)),
    ) {
        RadarBaseMap(modifier = Modifier.fillMaxSize())
        bitmap?.let { image ->
            Image(
                bitmap = image,
                contentDescription = "ECCC 1 kilometre radar image",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds,
            )
        }
        RadarMapLabels(location = location)
        RadarLocationMarker()

        Surface(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(14.dp),
            shape = RoundedCornerShape(24.dp),
            color = Color(0xD91B2931),
            shadowElevation = 3.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.AccessTime,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(19.dp),
                )
                Text(
                    text = if (frame != null) {
                        "Latest frame ${formatRadarFrameTime(frame.timeEpochMillis)}"
                    } else {
                        "Loading latest frame"
                    },
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(14.dp),
            shape = RoundedCornerShape(16.dp),
            color = Color(0xF7FFFFFF),
            shadowElevation = 3.dp,
        ) {
            IconButton(
                onClick = onSelectLatest,
                modifier = Modifier.semantics {
                    contentDescription = "Radar layers and latest frame"
                },
            ) {
                Icon(
                    imageVector = Icons.Outlined.Layers,
                    contentDescription = null,
                    tint = DeepBlue,
                    modifier = Modifier.size(25.dp),
                )
            }
        }

        if (arrival != null && confidence != null) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 14.dp, top = 76.dp, end = 70.dp),
                shape = RoundedCornerShape(19.dp),
                color = Color(0xF5FFFFFF),
                shadowElevation = 5.dp,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(11.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CloudQueue,
                        contentDescription = null,
                        tint = Color(0xFF5D86B5),
                        modifier = Modifier.size(36.dp),
                    )
                    Column {
                        Text(
                            text = "Rain arriving in $arrival",
                            color = DeepBlue,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Confidence: $confidence",
                            color = MutedNavy,
                            fontSize = 12.sp,
                        )
                        Text(
                            text = "Based on current radar trend",
                            color = MutedNavy,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        }

        if (errorMessage != null) {
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
                shape = RoundedCornerShape(15.dp),
                color = Color(0xEFFFFFFF),
            ) {
                Text(
                    text = errorMessage,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    color = DeepBlue,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }

        if (isLoading && frame == null) {
            LinearProgressIndicator(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth(),
                color = AccentBlue,
                trackColor = Color.Transparent,
            )
        }

        RadarMapLegend(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 14.dp, bottom = 124.dp),
        )
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 14.dp, bottom = 126.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                text = "Radar updates\nevery 6 min",
                color = DeepBlue,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.End,
            )
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = "Radar data updates every 6 minutes",
                tint = DeepBlue,
                modifier = Modifier.size(20.dp),
            )
        }

        RadarTimelineControls(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(10.dp),
            frameTimes = frameTimes,
            currentFrameIndex = frameIndex,
            isPlaying = isPlaying,
            isLoading = isLoading,
            onTogglePlayback = onTogglePlayback,
            onSelectFrame = onSelectFrame,
            onSelectLatest = onSelectLatest,
        )
    }
}

@Composable
private fun RadarBaseMap(modifier: Modifier) {
    Canvas(modifier = modifier) {
        drawRect(color = Color(0xFFE6ECDD))

        val river = Path().apply {
            moveTo(size.width * 0.69f, 0f)
            cubicTo(
                size.width * 0.61f,
                size.height * 0.18f,
                size.width * 0.78f,
                size.height * 0.32f,
                size.width * 0.65f,
                size.height * 0.48f,
            )
            cubicTo(
                size.width * 0.57f,
                size.height * 0.63f,
                size.width * 0.74f,
                size.height * 0.75f,
                size.width * 0.59f,
                size.height,
            )
            lineTo(size.width * 0.49f, size.height)
            cubicTo(
                size.width * 0.65f,
                size.height * 0.74f,
                size.width * 0.48f,
                size.height * 0.63f,
                size.width * 0.57f,
                size.height * 0.47f,
            )
            cubicTo(
                size.width * 0.68f,
                size.height * 0.30f,
                size.width * 0.53f,
                size.height * 0.17f,
                size.width * 0.61f,
                0f,
            )
            close()
        }
        drawPath(path = river, color = Color(0xFFB9D3E6))

        val majorRoads = listOf(
            listOf(0.03f to 0.18f, 0.36f to 0.28f, 0.67f to 0.22f, 1f to 0.34f),
            listOf(0.10f to 0.85f, 0.32f to 0.68f, 0.58f to 0.57f, 0.96f to 0.50f),
            listOf(0.25f to 0f, 0.34f to 0.31f, 0.30f to 0.63f, 0.43f to 1f),
            listOf(0.86f to 0f, 0.75f to 0.26f, 0.78f to 0.54f, 0.70f to 1f),
        )
        majorRoads.forEach { points ->
            points.zipWithNext().forEach { (start, end) ->
                drawLine(
                    color = Color(0xFFFAFBF8),
                    start = Offset(size.width * start.first, size.height * start.second),
                    end = Offset(size.width * end.first, size.height * end.second),
                    strokeWidth = 5.dp.toPx(),
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = Color(0xFFC8CFC8),
                    start = Offset(size.width * start.first, size.height * start.second),
                    end = Offset(size.width * end.first, size.height * end.second),
                    strokeWidth = 1.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }

        repeat(8) { index ->
            val x = size.width * (0.07f + index * 0.13f)
            drawLine(
                color = Color(0xFFCDD6CE),
                start = Offset(x, size.height * 0.06f),
                end = Offset(x + size.width * 0.07f, size.height * 0.94f),
                strokeWidth = 1.dp.toPx(),
            )
        }
    }
}

@Composable
private fun RadarMapLabels(location: WeatherLocation) {
    val city = location.label.substringBefore(",").ifBlank { "Current location" }
    val nearbyCities = remember(location) {
        WeatherCities.nearestTo(location, limit = 6)
            .map { it.label.substringBefore(",") }
            .filter { it != city }
            .distinct()
    }
    val northLabel = nearbyCities.getOrNull(0) ?: "North"
    val eastLabel = nearbyCities.getOrNull(1) ?: "East"
    val southLabel = nearbyCities.getOrNull(2) ?: "South"
    val westLabel = nearbyCities.getOrNull(3) ?: "West"
    Box(modifier = Modifier.fillMaxSize()) {
        Text(
            text = northLabel,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 106.dp),
            color = DeepBlue,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = eastLabel,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 108.dp, end = 26.dp),
            color = DeepBlue,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = city,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(top = 40.dp),
            color = Color(0xFF0B315F),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = southLabel,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(top = 118.dp, end = 116.dp),
            color = DeepBlue,
            fontSize = 12.sp,
        )
        Text(
            text = westLabel,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 25.dp, top = 245.dp),
            color = DeepBlue,
            fontSize = 12.sp,
        )
        Text(
            text = nearbyCities.getOrNull(4) ?: "Radar area",
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 24.dp, top = 165.dp),
            color = DeepBlue,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun RadarLocationMarker() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.size(30.dp),
            shape = CircleShape,
            color = Color.White,
            shadowElevation = 4.dp,
        ) {
            Box(
                modifier = Modifier
                    .padding(5.dp)
                    .clip(CircleShape)
                    .background(AccentBlue),
            )
        }
    }
}

@Composable
private fun RadarMapLegend(modifier: Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(15.dp),
        color = Color(0xF2FFFFFF),
        shadowElevation = 4.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Light", color = DeepBlue, fontSize = 10.sp)
                Text("Moderate", color = DeepBlue, fontSize = 10.sp)
                Text("Heavy", color = DeepBlue, fontSize = 10.sp)
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color(0xFF36A853),
                                Color(0xFFE0D829),
                                Color(0xFFF18D20),
                                Color(0xFFD9272E),
                                Color(0xFFE342A5),
                            ),
                        ),
                    ),
            )
        }
    }
}

@Composable
private fun RadarTimelineControls(
    modifier: Modifier,
    frameTimes: List<Long>,
    currentFrameIndex: Int,
    isPlaying: Boolean,
    isLoading: Boolean,
    onTogglePlayback: () -> Unit,
    onSelectFrame: (Long) -> Unit,
    onSelectLatest: () -> Unit,
) {
    val hasFrames = frameTimes.isNotEmpty()
    val safeIndex = currentFrameIndex.coerceIn(0, (frameTimes.size - 1).coerceAtLeast(0))
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color(0xF7FFFFFF),
        shadowElevation = 6.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                IconButton(
                    onClick = onTogglePlayback,
                    enabled = hasFrames && !isLoading,
                    modifier = Modifier
                        .size(43.dp)
                        .background(Color(0xFFF9FBFD), RoundedCornerShape(12.dp)),
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                        contentDescription = if (isPlaying) "Pause radar timeline" else "Play radar timeline",
                        tint = DeepBlue,
                    )
                }
                IconButton(
                    onClick = {
                        frameTimes.firstOrNull()?.let(onSelectFrame)
                    },
                    enabled = hasFrames && !isLoading,
                    modifier = Modifier
                        .size(43.dp)
                        .background(Color(0xFFF9FBFD), RoundedCornerShape(12.dp)),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.SkipPrevious,
                        contentDescription = "First radar frame",
                        tint = DeepBlue,
                    )
                }
                Slider(
                    value = safeIndex.toFloat(),
                    onValueChange = { value ->
                        frameTimes.getOrNull(value.roundToInt())?.let(onSelectFrame)
                    },
                    valueRange = 0f..(frameTimes.lastIndex.coerceAtLeast(0)).toFloat(),
                    steps = (frameTimes.size - 2).coerceAtLeast(0),
                    enabled = hasFrames && !isLoading,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = {
                        frameTimes.lastOrNull()?.let {
                            onSelectLatest()
                        }
                    },
                    enabled = hasFrames && !isLoading,
                    modifier = Modifier
                        .size(43.dp)
                        .background(Color(0xFFF9FBFD), RoundedCornerShape(12.dp)),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.SkipNext,
                        contentDescription = "Latest radar frame",
                        tint = DeepBlue,
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 117.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = frameTimes.firstOrNull()?.let(::formatRadarFrameTime) ?: "—",
                    color = MutedNavy,
                    fontSize = 11.sp,
                )
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = frameTimes.getOrNull(safeIndex)?.let(::formatRadarFrameTime) ?: "—",
                        color = if (safeIndex == frameTimes.lastIndex) AccentBlue else MutedNavy,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    if (safeIndex == frameTimes.lastIndex) {
                        Text("Now", color = AccentBlue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun RadarLocationUnavailableScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.GpsFixed,
            contentDescription = null,
            tint = AccentBlue,
            modifier = Modifier.size(48.dp),
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "Choose a location to view radar",
            color = DeepBlue,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(7.dp))
        Text(
            text = "Radar uses your precise location to center the ECCC 1 km map.",
            color = MutedNavy,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )
    }
}

private fun EcccRadarTimeWindow.frameTimes(): List<Long> {
    val frameCount = ((endEpochMillis - startEpochMillis) / intervalMillis)
        .toInt()
        .coerceIn(0, 120)
    return List(frameCount + 1) { index ->
        startEpochMillis + index * intervalMillis
    }
}

private val radarFrameTimeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.US)

private fun formatRadarFrameTime(epochMillis: Long): String =
    radarFrameTimeFormatter
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(epochMillis))

private fun radarArrivalText(forecast: DashboardForecast, nowEpochMillis: Long): String {
    val countdown = forecast.rainStartMinutesFromNow(nowEpochMillis)
    val value = if (countdown != null && countdown <= RADAR_RAIN_WINDOW_MINUTES) {
        formatRainStartCountdown(countdown)
    } else {
        forecast.rainStartsIn.trim()
    }
    return if (value.startsWith("~")) value else "~$value"
}

private fun radarConfidenceText(forecast: DashboardForecast): String = when {
    forecast.rainStartSource == RainStartSource.ECCC_RADAR &&
        forecast.rainStartConfidenceMeaningful -> "High"
    else -> "Medium"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RefreshableBriefingContent(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    content: @Composable () -> Unit,
) {
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        content()
    }
}

@Composable
private fun BriefingScreen(
    forecast: DashboardForecast,
    unitSystem: UnitSystem,
    backgroundWeather: CurrentWeather,
    selectedRange: ForecastRange,
    onRangeSelected: (ForecastRange) -> Unit,
    selectedDayIndex: Int,
    onDaySelected: (Int) -> Unit,
    onDayDetailBack: () -> Unit,
    isRefreshing: Boolean,
    isStale: Boolean,
    errorMessage: String?,
    onLocationClick: () -> Unit,
    onRefresh: () -> Unit,
    useNativeScrollCapture: Boolean,
) {
    val content: @Composable () -> Unit = {
        BriefingContent(
            forecast = forecast,
            unitSystem = unitSystem,
            backgroundWeather = backgroundWeather,
            selectedRange = selectedRange,
            onRangeSelected = onRangeSelected,
            selectedDayIndex = selectedDayIndex,
            onDaySelected = onDaySelected,
            onDayDetailBack = onDayDetailBack,
            isRefreshing = isRefreshing,
            isStale = isStale,
            errorMessage = errorMessage,
            onLocationClick = onLocationClick,
        )
    }

    if (useNativeScrollCapture) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context -> BriefingScrollContainer(context) },
            update = { container ->
                container.updateContent(
                    content = content,
                    onRefresh = onRefresh,
                    isRefreshing = isRefreshing,
                )
            },
        )
    } else {
        BriefingContent(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            forecast = forecast,
            unitSystem = unitSystem,
            backgroundWeather = backgroundWeather,
            selectedRange = selectedRange,
            onRangeSelected = onRangeSelected,
            selectedDayIndex = selectedDayIndex,
            onDaySelected = onDaySelected,
            onDayDetailBack = onDayDetailBack,
            isRefreshing = isRefreshing,
            isStale = isStale,
            errorMessage = errorMessage,
            onLocationClick = onLocationClick,
        )
    }
}

private class BriefingScrollContainer(context: Context) : ScrollView(context) {
    private val composeView = ComposeView(context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
    }

    private var content by mutableStateOf<(@Composable () -> Unit)?>(null)
    private var onRefresh: (() -> Unit)? = null
    private var isRefreshing = false
    private val pullToRefreshDistancePx =
        context.resources.displayMetrics.density * PULL_TO_REFRESH_DISTANCE_DP
    private var canPullToRefresh = false
    private var pullStartX = 0f
    private var pullStartY = 0f
    private var pullDistance = 0f

    init {
        isFillViewport = false
        overScrollMode = View.OVER_SCROLL_NEVER
        setVerticalScrollBarEnabled(false)
        addView(composeView)
        composeView.setContent {
            RainDepartmentTheme {
                content?.invoke()
            }
        }
    }

    fun updateContent(
        content: @Composable () -> Unit,
        onRefresh: () -> Unit,
        isRefreshing: Boolean,
    ) {
        this.content = content
        this.onRefresh = onRefresh
        this.isRefreshing = isRefreshing
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked
        when (action) {
            MotionEvent.ACTION_DOWN -> {
                composeView.animate().cancel()
                composeView.translationY = 0f
                pullStartX = event.x
                pullStartY = event.y
                pullDistance = 0f
                canPullToRefresh = !isRefreshing && !canScrollVertically(-1)
            }

            MotionEvent.ACTION_MOVE -> if (canPullToRefresh) {
                val deltaY = event.y - pullStartY
                val deltaX = event.x - pullStartX
                pullDistance = if (deltaY > 0f && deltaY > abs(deltaX)) deltaY else 0f
                composeView.translationY = pullDistance * PULL_DRAG_MULTIPLIER
            }

            MotionEvent.ACTION_UP -> {
                val shouldRefresh = canPullToRefresh &&
                    pullDistance >= pullToRefreshDistancePx &&
                    !isRefreshing
                resetPullGesture()
                settlePullGesture()
                if (shouldRefresh) {
                    post {
                        if (!isRefreshing) onRefresh?.invoke()
                    }
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                resetPullGesture()
                settlePullGesture()
            }
        }
        return super.dispatchTouchEvent(event)
    }

    private fun resetPullGesture() {
        canPullToRefresh = false
        pullDistance = 0f
    }

    private fun settlePullGesture() {
        if (composeView.translationY == 0f) return
        composeView.animate()
            .translationY(0f)
            .setDuration(PULL_RELEASE_ANIMATION_MS)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    private companion object {
        const val PULL_TO_REFRESH_DISTANCE_DP = 64f
        const val PULL_DRAG_MULTIPLIER = 0.5f
        const val PULL_RELEASE_ANIMATION_MS = 180L
    }
}

@Composable
private fun BriefingContent(
    modifier: Modifier = Modifier,
    forecast: DashboardForecast,
    unitSystem: UnitSystem,
    backgroundWeather: CurrentWeather,
    selectedRange: ForecastRange,
    onRangeSelected: (ForecastRange) -> Unit,
    selectedDayIndex: Int,
    onDaySelected: (Int) -> Unit,
    onDayDetailBack: () -> Unit,
    isRefreshing: Boolean,
    isStale: Boolean,
    errorMessage: String?,
    onLocationClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (!isRefreshing && (isStale || errorMessage != null)) {
            WeatherStatusBanner(
                isStale = isStale,
                errorMessage = errorMessage,
            )
        }
        ForecastRangeSelector(
            selected = selectedRange,
            onSelected = onRangeSelected,
        )
        val selectedDay = forecast.daily.getOrNull(selectedDayIndex)
        if (selectedDay != null) {
            DailyForecastDetailContent(
                forecast = forecast,
                day = selectedDay,
                dayIndex = selectedDayIndex,
                unitSystem = unitSystem,
                onBack = onDayDetailBack,
                onLocationClick = onLocationClick,
            )
        } else if (selectedRange == ForecastRange.SEVEN_DAYS) {
            SevenDayOutlookContent(
                forecast = forecast,
                unitSystem = unitSystem,
                backgroundWeather = backgroundWeather,
                onLocationClick = onLocationClick,
                onDaySelected = onDaySelected,
            )
        } else {
            WeatherHeroCard(
                forecast = forecast,
                unitSystem = unitSystem,
                backgroundWeather = backgroundWeather,
                onLocationClick = onLocationClick,
            )
            HourlyForecastCard(forecast.hourly, unitSystem)
            AdaptiveTwoColumn(
                left = { childModifier -> PrecipitationCard(childModifier, forecast, unitSystem) },
                right = { childModifier -> WindCard(childModifier, forecast, unitSystem) },
            )
            AdaptiveTwoColumn(
                left = { childModifier ->
                    SevenDayForecastCard(childModifier, forecast, unitSystem, onDaySelected)
                },
                right = { childModifier ->
                    Column(
                        modifier = childModifier,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        RainfallOutlookCard(Modifier.fillMaxWidth(), forecast, unitSystem)
                        SunriseSunsetCard(Modifier.fillMaxWidth(), forecast)
                        DryWindowCard(Modifier.fillMaxWidth(), forecast)
                    }
                },
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        OpenMeteoAttribution()
    }
}

@Composable
private fun BriefingUnavailableScreen(
    isRefreshing: Boolean,
    errorMessage: String?,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Cloud,
            contentDescription = null,
            tint = AccentBlue,
            modifier = Modifier.size(54.dp),
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = if (isRefreshing) "Loading live weather…" else "Weather is unavailable",
            color = Navy,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = errorMessage ?: "Open-Meteo data will appear here after the first refresh.",
            color = MutedNavy,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Pull down to refresh",
            color = AccentBlue,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(18.dp))
        OpenMeteoAttribution()
    }
}

@Composable
private fun WeatherStatusBanner(
    isStale: Boolean,
    errorMessage: String?,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE7F2FC)),
        border = BorderStroke(1.dp, Color(0xFFC9E1F5)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = when {
                    errorMessage != null -> errorMessage
                    isStale -> "This forecast is more than six hours old."
                    else -> "Live weather updated."
                },
                modifier = Modifier.weight(1f),
                color = DeepBlue,
                fontSize = 11.sp,
            )
            Text(
                text = "Pull down to refresh",
                color = AccentBlue,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.End,
            )
        }
    }
}

@Composable
private fun OpenMeteoAttribution() {
    val uriHandler = LocalUriHandler.current
    TextButton(onClick = { uriHandler.openUri("https://open-meteo.com/") }) {
        Text(
            text = "Weather data by Open-Meteo.com · GEM Canada",
            color = MutedNavy,
            fontSize = 10.sp,
        )
    }
}

@Composable
private fun WeatherHeroCard(
    forecast: DashboardForecast,
    unitSystem: UnitSystem,
    backgroundWeather: CurrentWeather,
    onLocationClick: () -> Unit,
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
                    contentDescription = "${forecast.conditionLabel} sky over ${forecast.location}",
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
                    Row(
                        modifier = Modifier.clickable(onClick = onLocationClick),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
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
                            condition = forecast.condition,
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
                lineHeight = 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            Text(
                text = label,
                color = MutedNavy,
                fontSize = 9.sp,
                lineHeight = 10.sp,
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
private fun SevenDayOutlookContent(
    forecast: DashboardForecast,
    unitSystem: UnitSystem,
    backgroundWeather: CurrentWeather,
    onLocationClick: () -> Unit,
    onDaySelected: (Int) -> Unit,
) {
    val days = forecast.daily.take(7)
    if (days.isEmpty()) {
        Text(
            text = "The 7-day outlook is not available yet.",
            modifier = Modifier.padding(vertical = 24.dp),
            color = MutedNavy,
            fontSize = 13.sp,
        )
        return
    }

    SevenDaySummaryCard(
        forecast = forecast,
        days = days,
        unitSystem = unitSystem,
        backgroundWeather = backgroundWeather,
        onLocationClick = onLocationClick,
    )
    SevenDayDailyStrip(
        forecast = forecast,
        days = days,
        unitSystem = unitSystem,
        onDaySelected = onDaySelected,
    )
    SevenDayPrecipitationCard(
        forecast = forecast,
        days = days,
        unitSystem = unitSystem,
    )
    SevenDayInsightsCard(
        forecast = forecast,
        days = days,
        unitSystem = unitSystem,
    )
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFFE5F1FC),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                tint = AccentBlue,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = "Forecasts can change. Check back for the latest updates.",
                color = DeepBlue,
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun DailyForecastDetailContent(
    forecast: DashboardForecast,
    day: DailyForecast,
    dayIndex: Int,
    unitSystem: UnitSystem,
    onBack: () -> Unit,
    onLocationClick: () -> Unit,
) {
    val hourly = day.hourly.ifEmpty { if (dayIndex == 0) forecast.hourly else emptyList() }
    val peakWindMph = day.peakWindMph.takeIf { it > 0 } ?: forecast.peakWindMph
    val peakWindDirection = day.peakWindDirection.ifBlank { forecast.peakWindDirection }
    val detailForecast = forecast.copy(
        highFahrenheit = day.highFahrenheit,
        lowFahrenheit = day.lowFahrenheit,
        conditionLabel = day.conditionLabel,
        precipitationChance = day.precipitationChance,
        expectedRainInches = day.rainfallInches,
        peakWindMph = peakWindMph,
        peakWindDirection = peakWindDirection,
        peakWindTime = day.peakWindTime.ifBlank { forecast.peakWindTime },
        sunrise = day.sunrise.ifBlank { forecast.sunrise },
        sunset = day.sunset.ifBlank { forecast.sunset },
        dryWindow = day.dryWindow.ifBlank { forecast.dryWindow },
    )

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = onBack,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.ChevronLeft,
                    contentDescription = null,
                    tint = AccentBlue,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text(text = "7-Day Outlook", color = AccentBlue, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "Day details",
                color = MutedNavy,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        DailyWeatherHeroCard(
            forecast = forecast,
            day = day,
            dayIndex = dayIndex,
            unitSystem = unitSystem,
            peakWindMph = peakWindMph,
            peakWindDirection = peakWindDirection,
            onLocationClick = onLocationClick,
        )
        if (hourly.isNotEmpty()) {
            HourlyForecastCard(
                hourly = hourly,
                unitSystem = unitSystem,
                title = "Hourly forecast",
                action = "All day",
            )
            AdaptiveTwoColumn(
                left = { childModifier ->
                    DailyPrecipitationCard(childModifier, forecast, day, hourly, unitSystem)
                },
                right = { childModifier ->
                    DailyWindCard(
                        modifier = childModifier,
                        forecast = forecast,
                        day = day,
                        hourly = hourly,
                        unitSystem = unitSystem,
                        peakWindMph = peakWindMph,
                        peakWindDirection = peakWindDirection,
                    )
                },
            )
        }
        AdaptiveTwoColumn(
            left = { childModifier ->
                SunriseSunsetCard(childModifier, detailForecast)
            },
            right = { childModifier ->
                DryWindowCard(childModifier, detailForecast)
            },
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = Color(0xFFE5F1FC),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    tint = AccentBlue,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = "Daily conditions are forecast values and can change.",
                    color = DeepBlue,
                    fontSize = 11.sp,
                )
            }
        }
    }
}

@Composable
private fun DailyWeatherHeroCard(
    forecast: DashboardForecast,
    day: DailyForecast,
    dayIndex: Int,
    unitSystem: UnitSystem,
    peakWindMph: Int,
    peakWindDirection: String,
    onLocationClick: () -> Unit,
) {
    val context = LocalContext.current
    val dayWeather = remember(context, day.condition) {
        CurrentWeather(
            location = forecast.location,
            condition = day.condition,
            conditionLabel = day.conditionLabel,
            isDay = true,
            currentFahrenheit = day.highFahrenheit,
            highFahrenheit = day.highFahrenheit,
            lowFahrenheit = day.lowFahrenheit,
            precipitationChance = day.precipitationChance,
        )
    }
    val bitmap = remember(context, dayWeather.condition, dayWeather.isDay) {
        BackplateLoader.bitmap(context, dayWeather)
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
                    .height(204.dp)
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)),
            ) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "${day.conditionLabel} forecast for ${day.day}",
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onLocationClick),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.LocationOn,
                            contentDescription = null,
                            tint = DeepBlue,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = forecast.location,
                            modifier = Modifier.weight(1f),
                            color = DeepBlue,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Icon(
                            imageVector = Icons.Outlined.ChevronRight,
                            contentDescription = "Choose city",
                            tint = DeepBlue,
                            modifier = Modifier.size(17.dp),
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (dayIndex == 0) "Today" else "${day.day} forecast",
                        color = Navy,
                        fontSize = 27.sp,
                        lineHeight = 30.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Row(
                        modifier = Modifier.padding(top = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ConditionIcon(day.condition, Modifier.size(25.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = day.conditionLabel,
                            color = Navy,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = forecast.temperature(day.highFahrenheit, unitSystem),
                            color = DarkBlue,
                            fontSize = 52.sp,
                            lineHeight = 54.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Column(modifier = Modifier.padding(start = 12.dp, bottom = 6.dp)) {
                            Text(
                                text = "High",
                                color = Navy,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                text = "Low ${forecast.temperature(day.lowFahrenheit, unitSystem)}",
                                color = Navy,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
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
                    value = "${day.precipitationChance}%",
                    label = "Chance of Rain",
                )
                MetricDivider()
                HeroMetric(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.WaterDrop,
                    value = forecast.precipitation(day.rainfallInches, unitSystem),
                    label = "Total Rain",
                )
                MetricDivider()
                HeroMetric(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.Air,
                    value = forecast.windSpeed(peakWindMph, unitSystem),
                    label = "Peak Wind ($peakWindDirection)",
                )
            }
        }
    }
}

@Composable
private fun DailyPrecipitationCard(
    modifier: Modifier,
    forecast: DashboardForecast,
    day: DailyForecast,
    hourly: List<HourlyForecast>,
    unitSystem: UnitSystem,
) {
    DashboardCard(modifier = modifier) {
        SectionHeader(title = "Precipitation by Hour", action = "All day", icon = Icons.Outlined.WaterDrop)
        Spacer(modifier = Modifier.height(5.dp))
        AreaChart(
            points = hourlyChartPoints(hourly) { it.rainfallInches.toFloat() },
            lineColor = ChartBlue,
            fillColor = ChartBlue.copy(alpha = 0.25f),
            maxValue = max(0.1f, hourly.maxOfOrNull { it.rainfallInches.toFloat() } ?: 0.1f),
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
                    text = "${forecast.precipitation(day.rainfallInches, unitSystem)} total",
                    color = DeepBlue,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Expected throughout the day",
                    color = MutedNavy,
                    fontSize = 10.sp,
                )
            }
        }
    }
}

@Composable
private fun DailyWindCard(
    modifier: Modifier,
    forecast: DashboardForecast,
    day: DailyForecast,
    hourly: List<HourlyForecast>,
    unitSystem: UnitSystem,
    peakWindMph: Int,
    peakWindDirection: String,
) {
    DashboardCard(modifier = modifier) {
        SectionHeader(title = "Wind by Hour", action = "All day", icon = Icons.Outlined.Air)
        Spacer(modifier = Modifier.height(5.dp))
        LineChart(
            points = hourlyChartPoints(hourly) { it.windMph.toFloat() },
            lineColor = Aqua,
            fillColor = Aqua.copy(alpha = 0.12f),
            maxValue = max(1f, hourly.maxOfOrNull { it.windMph.toFloat() } ?: 1f),
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
                    text = forecast.windSpeed(peakWindMph, unitSystem),
                    color = DeepBlue,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Peak wind${day.peakWindTime.takeIf { it.isNotBlank() }?.let { " at $it" } ?: ""} ($peakWindDirection)",
                    color = MutedNavy,
                    fontSize = 10.sp,
                )
            }
        }
    }
}

private fun hourlyChartPoints(
    hourly: List<HourlyForecast>,
    value: (HourlyForecast) -> Float,
): List<ChartPoint> {
    val sampled = if (hourly.size <= 8) {
        hourly
    } else {
        hourly.filterIndexed { index, _ -> index % 3 == 0 || index == hourly.lastIndex }
    }
    return sampled.map { ChartPoint(it.time, value(it)) }
}

@Composable
private fun SevenDaySummaryCard(
    forecast: DashboardForecast,
    days: List<DailyForecast>,
    unitSystem: UnitSystem,
    backgroundWeather: CurrentWeather,
    onLocationClick: () -> Unit,
) {
    val context = LocalContext.current
    val bitmap = remember(context, backgroundWeather.condition, backgroundWeather.isDay) {
        BackplateLoader.bitmap(context, backgroundWeather)
    }
    val averageHigh = days.map { it.highFahrenheit }.average().roundToInt()
    val averageLow = days.map { it.lowFahrenheit }.average().roundToInt()
    val totalRain = days.sumOf { it.rainfallInches }
    val representativeCondition = days
        .groupingBy { it.condition }
        .eachCount()
        .maxByOrNull { it.value }
        ?.key
        ?: days.first().condition

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(1.dp, Color(0xFFC9E1F5)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(204.dp)
                .clip(RoundedCornerShape(20.dp)),
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
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
                                Color(0x35EAF7FC),
                            ),
                        ),
                    ),
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 13.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onLocationClick),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.LocationOn,
                        contentDescription = null,
                        tint = DeepBlue,
                        modifier = Modifier.size(17.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = forecast.location,
                        modifier = Modifier.weight(1f),
                        color = DeepBlue,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Icon(
                        imageVector = Icons.Outlined.ChevronRight,
                        contentDescription = "Choose city",
                        tint = DeepBlue,
                        modifier = Modifier.size(17.dp),
                    )
                }
                Spacer(modifier = Modifier.height(7.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Next 7 Days Outlook",
                            color = DeepBlue,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = sevenDayHeadline(days),
                            color = Navy,
                            fontSize = 25.sp,
                            lineHeight = 28.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = sevenDaySummary(days),
                            color = MutedNavy,
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                        )
                    }
                    ConditionIcon(
                        condition = representativeCondition,
                        modifier = Modifier.size(45.dp),
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SevenDaySummaryMetric(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Outlined.WbSunny,
                        value = "${forecast.temperature(averageHigh, unitSystem)} / " +
                            forecast.temperature(averageLow, unitSystem),
                        label = "Avg. high / low",
                    )
                    MetricDivider()
                    SevenDaySummaryMetric(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Outlined.WaterDrop,
                        value = forecast.precipitation(totalRain, unitSystem),
                        label = "Total precipitation",
                    )
                }
            }
        }
    }
}

@Composable
private fun SevenDaySummaryMetric(
    modifier: Modifier,
    icon: ImageVector,
    value: String,
    label: String,
) {
    Row(
        modifier = modifier.padding(horizontal = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = AccentBlue,
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.width(5.dp))
        Column {
            Text(
                text = value,
                color = DeepBlue,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            Text(
                text = label,
                color = MutedNavy,
                fontSize = 8.sp,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun SevenDayDailyStrip(
    forecast: DashboardForecast,
    days: List<DailyForecast>,
    unitSystem: UnitSystem,
    onDaySelected: (Int) -> Unit,
) {
    DashboardCard(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(title = "Daily Forecast", action = "Next 7 Days", icon = Icons.Outlined.Cloud)
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            days.forEachIndexed { index, day ->
                Surface(
                    modifier = Modifier
                        .width(78.dp)
                        .height(148.dp)
                        .semantics(mergeDescendants = true) {
                            contentDescription = "View ${day.day} forecast"
                        }
                        .clickable { onDaySelected(index) },
                    shape = RoundedCornerShape(15.dp),
                    color = if (index == 0) Color(0xFFEAF5FF) else Color.White,
                    border = BorderStroke(
                        1.dp,
                        if (index == 0) AccentBlue else Color(0xFFE2EAF1),
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = day.day,
                            color = if (index == 0) AccentBlue else DeepBlue,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                        Text(
                            text = if (index == 0) "Today" else "Day ${index + 1}",
                            color = MutedNavy,
                            fontSize = 8.sp,
                        )
                        Spacer(modifier = Modifier.height(7.dp))
                        ConditionIcon(day.condition, Modifier.size(31.dp))
                        Spacer(modifier = Modifier.height(5.dp))
                        Text(
                            text = forecast.temperature(day.highFahrenheit, unitSystem),
                            color = Orange,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = forecast.temperature(day.lowFahrenheit, unitSystem),
                            color = AccentBlue,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Icon(
                            imageVector = Icons.Outlined.WaterDrop,
                            contentDescription = null,
                            tint = AccentBlue,
                            modifier = Modifier.size(13.dp),
                        )
                        Text(
                            text = "${day.precipitationChance}%",
                            color = MutedNavy,
                            fontSize = 9.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SevenDayPrecipitationCard(
    forecast: DashboardForecast,
    days: List<DailyForecast>,
    unitSystem: UnitSystem,
) {
    val points = days.map { day ->
        ChartPoint(day.day, day.rainfallInches.toFloat())
    }
    DashboardCard(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(
            title = "Weekly Precipitation",
            action = if (unitSystem == UnitSystem.IMPERIAL) "in" else "mm",
            icon = Icons.Outlined.WaterDrop,
        )
        Text(
            text = "Total: ${forecast.precipitation(days.sumOf { it.rainfallInches }, unitSystem)}",
            color = MutedNavy,
            fontSize = 10.sp,
        )
        Spacer(modifier = Modifier.height(6.dp))
        BarChart(
            points = points,
            valueLabel = { forecast.precipitation(it.toDouble(), unitSystem).substringBefore(' ') },
        )
    }
}

@Composable
private fun SevenDayInsightsCard(
    forecast: DashboardForecast,
    days: List<DailyForecast>,
    unitSystem: UnitSystem,
) {
    val bestDay = days.minWithOrNull(
        compareBy<DailyForecast> { it.precipitationChance }
            .thenBy { it.rainfallInches },
    ) ?: days.first()
    val wettestDay = days.maxByOrNull { it.rainfallInches } ?: days.first()

    DashboardCard(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(title = "7-Day Insights", icon = Icons.Outlined.WbSunny)
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            SevenDayInsight(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.Umbrella,
                tint = Color(0xFF5AB867),
                title = "Best outdoor day",
                value = bestDay.day,
                detail = "${bestDay.precipitationChance}% chance of rain",
            )
            SevenDayInsightDivider()
            SevenDayInsight(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.WaterDrop,
                tint = ChartBlue,
                title = "Heaviest rain day",
                value = wettestDay.day,
                detail = forecast.precipitation(wettestDay.rainfallInches, unitSystem),
            )
            SevenDayInsightDivider()
            SevenDayInsight(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.Air,
                tint = Aqua,
                title = "Peak wind",
                value = forecast.windSpeed(forecast.peakWindMph, unitSystem),
                detail = forecast.peakWindDirection,
            )
        }
    }
}

@Composable
private fun SevenDayInsight(
    modifier: Modifier,
    icon: ImageVector,
    tint: Color,
    title: String,
    value: String,
    detail: String,
) {
    Column(
        modifier = modifier.padding(horizontal = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(tint.copy(alpha = 0.18f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(21.dp))
        }
        Spacer(modifier = Modifier.height(5.dp))
        Text(
            text = title,
            color = MutedNavy,
            fontSize = 8.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
        Text(
            text = value,
            color = DeepBlue,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        Text(
            text = detail,
            color = MutedNavy,
            fontSize = 8.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SevenDayInsightDivider() {
    Box(
        modifier = Modifier
            .padding(top = 2.dp)
            .width(1.dp)
            .height(92.dp)
            .background(Color(0xFFDCE8F1)),
    )
}

private fun sevenDayHeadline(days: List<DailyForecast>): String {
    val rainyDays = days.count { it.precipitationChance >= 50 || it.rainfallInches > 0.1 }
    return when {
        rainyDays >= (days.size * 0.6f).coerceAtLeast(1f) -> "Rainy days ahead"
        rainyDays >= 2 -> "Mixed sun & showers"
        else -> "Mostly clear days"
    }
}

private fun sevenDaySummary(days: List<DailyForecast>): String {
    val rainyDays = days.count { it.precipitationChance >= 50 || it.rainfallInches > 0.1 }
    return when {
        rainyDays >= 3 -> "Variable precipitation with a few wetter days."
        rainyDays > 0 -> "A mix of sunshine and occasional showers."
        else -> "Mostly dry weather with mild conditions."
    }
}

@Composable
private fun HourlyForecastCard(
    hourly: List<HourlyForecast>,
    unitSystem: UnitSystem,
    title: String = "Hourly Precipitation, Temperature & Wind",
    action: String = "Next 24 Hours",
) {
    DashboardCard(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(title = title, action = action)
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
                WindDirectionArrow(item.windDirectionLabel)
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
private fun WindDirectionArrow(direction: String) {
    Canvas(
        modifier = Modifier
            .size(width = 20.dp, height = 18.dp)
            .semantics { contentDescription = "Wind direction $direction" },
    ) {
        val centerX = size.width / 2f
        val arrowTop = 2.dp.toPx()
        val arrowBottom = size.height - 2.dp.toPx()
        val arrowHeadSize = 4.dp.toPx()
        val strokeWidth = 1.8.dp.toPx()

        rotate(windDirectionRotationDegrees(direction)) {
            drawLine(
                color = Aqua,
                start = Offset(centerX, arrowBottom),
                end = Offset(centerX, arrowTop),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = Aqua,
                start = Offset(centerX, arrowTop),
                end = Offset(centerX - arrowHeadSize, arrowTop + arrowHeadSize),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = Aqua,
                start = Offset(centerX, arrowTop),
                end = Offset(centerX + arrowHeadSize, arrowTop + arrowHeadSize),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
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
            maxValue = max(0.1f, forecast.precipitation24h.maxOfOrNull { it.value } ?: 0.1f),
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
                    text = "Expected total through tomorrow",
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
            maxValue = max(1f, forecast.windByHour.maxOfOrNull { it.value } ?: 1f),
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
                    text = "Peak at ${forecast.peakWindTime} (${forecast.peakWindDirection})",
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
    onDaySelected: (Int) -> Unit,
) {
    DashboardCard(modifier = modifier) {
        SectionHeader(title = "7-Day Forecast", action = "More Details")
        Spacer(modifier = Modifier.height(3.dp))
        forecast.daily.forEachIndexed { index, day ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 30.dp)
                    .semantics(mergeDescendants = true) {
                        contentDescription = "View ${day.day} forecast"
                    }
                    .clickable { onDaySelected(index) },
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
    weather: CurrentWeather?,
    unitSystem: UnitSystem,
    onUnitSystemSelected: (UnitSystem) -> Unit,
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
                    Text(text = "Follows the live condition shown in the app.", color = MutedNavy, fontSize = 11.sp)
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            WeatherPreviewCard(weather = weather, unitSystem = unitSystem)
        }
        OpenMeteoAttribution()
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
private fun WeatherPreviewCard(weather: CurrentWeather?, unitSystem: UnitSystem) {
    if (weather == null) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(17.dp))
                .background(Color(0xFFDDEBF6)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Waiting for live weather…",
                color = DeepBlue,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        return
    }

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
