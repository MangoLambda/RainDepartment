package com.raindepartment.weather

import android.content.Context
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle

object WeatherWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val refreshState = currentState<Any?>()
            val snapshot = SharedPreferencesWeatherCache(context).read()
            val unitSystem = WeatherPreferences.unitSystem(context)
            val selectedBackplate = if (WeatherPreferences.isAutomaticBackplate(context)) {
                null
            } else {
                BackplateChoices.getOrNull(WeatherPreferences.backplateIndex(context))
            }

            if (snapshot == null) {
                WeatherWidgetUnavailable()
            } else {
                val weather = snapshot.forecast.currentWeather().forBackplate(selectedBackplate)
                val backplate = remember(
                    refreshState,
                    weather.condition,
                    weather.isDay,
                    snapshot.fetchedAtEpochMillis,
                ) {
                    BackplateLoader.imageProvider(context, weather)
                }

                WeatherWidgetContent(
                    weather = weather,
                    forecast = snapshot.forecast,
                    unitSystem = unitSystem,
                    backplate = backplate,
                    isStale = System.currentTimeMillis() - snapshot.fetchedAtEpochMillis >=
                        SIX_HOURS_MILLIS,
                )
            }
        }
    }

    private const val SIX_HOURS_MILLIS = 6 * 60 * 60 * 1_000L
}

class WeatherWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WeatherWidget
}

private val WidgetWhite = ColorProvider(day = Color.White, night = Color.White)
private val WidgetSoftWhite = ColorProvider(
    day = Color(0xE6FFFFFF),
    night = Color(0xE6FFFFFF),
)
private val WidgetScrim = ColorProvider(
    day = Color(0x24001F42),
    night = Color(0x24001F42),
)
private val WidgetDivider = ColorProvider(
    day = Color(0x70FFFFFF),
    night = Color(0x70FFFFFF),
)
private val WidgetUnavailableBackground = ColorProvider(
    day = Color(0xFFDDEBF6),
    night = Color(0xFFDDEBF6),
)

private const val WIDGET_LOCATION_LINE_LIMIT = 10

internal fun widgetLocationLabel(location: String): String = location
    .split(',', limit = 2)
    .map(String::trim)
    .filter { it.isNotEmpty() }
    .take(2)
    .joinToString("\n") { line ->
        if (line.length <= WIDGET_LOCATION_LINE_LIMIT) {
            line
        } else {
            line.take(WIDGET_LOCATION_LINE_LIMIT - 1).trimEnd() + "…"
        }
    }

@androidx.compose.runtime.Composable
private fun WeatherWidgetUnavailable() {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(24.dp)
            .background(WidgetUnavailableBackground)
            .clickable(actionStartActivity<MainActivity>()),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "RainDepartment",
                style = TextStyle(
                    color = ColorProvider(
                        day = Color(0xFF164B91),
                        night = Color(0xFF164B91),
                    ),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Spacer(modifier = GlanceModifier.height(4.dp))
            Text(
                text = "Open app to load live weather",
                style = TextStyle(
                    color = ColorProvider(
                        day = Color(0xFF607792),
                        night = Color(0xFF607792),
                    ),
                    fontSize = 10.sp,
                ),
            )
        }
    }
}

@androidx.compose.runtime.Composable
private fun WeatherWidgetContent(
    weather: CurrentWeather,
    forecast: DashboardForecast,
    unitSystem: UnitSystem,
    backplate: androidx.glance.ImageProvider,
    isStale: Boolean,
) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(24.dp)
            .background(backplate, contentScale = ContentScale.Crop)
            .clickable(actionStartActivity<MainActivity>()),
        contentAlignment = Alignment.TopStart,
    ) {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(WidgetScrim),
        ) {}

        Row(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Leave the illustrated left side clean; weather details start after it.
            Spacer(modifier = GlanceModifier.width(56.dp))
            Spacer(
                modifier = GlanceModifier
                    .width(1.dp)
                    .height(34.dp)
                    .background(WidgetDivider),
            )
            Spacer(modifier = GlanceModifier.width(8.dp))

            Column(
                modifier = GlanceModifier.defaultWeight(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = GlanceModifier.width(74.dp)) {
                        Text(
                            text = widgetLocationLabel(weather.location),
                            style = TextStyle(
                                color = WidgetWhite,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                        )
                        if (isStale) {
                            Spacer(modifier = GlanceModifier.height(2.dp))
                            Text(
                                text = "Stale forecast",
                                style = TextStyle(
                                    color = WidgetSoftWhite,
                                    fontSize = 10.sp,
                                ),
                            )
                        }
                    }
                    Text(
                        text = weather.temperature(unitSystem),
                        style = TextStyle(
                            color = WidgetWhite,
                            fontSize = 38.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                    Spacer(modifier = GlanceModifier.width(10.dp))
                    Box(
                        modifier = GlanceModifier
                            .defaultWeight()
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                    ) {
                        Column {
                            Text(
                                text = weather.conditionLabel,
                                style = TextStyle(
                                    color = WidgetWhite,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                ),
                            )
                            Spacer(modifier = GlanceModifier.height(4.dp))
                            Text(
                                text = "${forecast.precipitationChance}% precip  ·  ${forecast.rainStartsIn}",
                                style = TextStyle(
                                    color = WidgetWhite,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                ),
                            )
                            Spacer(modifier = GlanceModifier.height(2.dp))
                            Text(
                                text = weather.highLow(unitSystem),
                                style = TextStyle(
                                    color = WidgetWhite,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}
