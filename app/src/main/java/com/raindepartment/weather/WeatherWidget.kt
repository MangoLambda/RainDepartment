package com.raindepartment.weather

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
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
        val weather = DummyWeatherData.current
        val unitSystem = WeatherPreferences.unitSystem(context)
        val backplate = BackplateLoader.imageProvider(context, weather)

        provideContent {
            WeatherWidgetContent(
                weather = weather,
                unitSystem = unitSystem,
                backplate = backplate,
            )
        }
    }
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

@androidx.compose.runtime.Composable
private fun WeatherWidgetContent(
    weather: DummyWeather,
    unitSystem: UnitSystem,
    backplate: androidx.glance.ImageProvider,
) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(24.dp)
            .background(backplate, contentScale = ContentScale.Crop),
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
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = GlanceModifier.width(92.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "⛅",
                    style = TextStyle(
                        color = WidgetWhite,
                        fontSize = 44.sp,
                    ),
                )
                Spacer(modifier = GlanceModifier.height(3.dp))
                Text(
                    text = "Austin",
                    style = TextStyle(
                        color = WidgetSoftWhite,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                )
            }

            Spacer(modifier = GlanceModifier.width(12.dp))

            Column(
                modifier = GlanceModifier.defaultWeight(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = weather.temperature(unitSystem),
                        style = TextStyle(
                            color = WidgetWhite,
                            fontSize = 40.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                    Spacer(modifier = GlanceModifier.width(12.dp))
                    Text(
                        text = weather.conditionLabel,
                        style = TextStyle(
                            color = WidgetWhite,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                }

                Spacer(modifier = GlanceModifier.height(10.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${weather.precipitationChance}% precip",
                        style = TextStyle(
                            color = WidgetSoftWhite,
                            fontSize = 11.sp,
                        ),
                    )
                    Spacer(modifier = GlanceModifier.width(8.dp))
                    Spacer(
                        modifier = GlanceModifier
                            .width(1.dp)
                            .height(22.dp)
                            .background(WidgetDivider),
                    )
                    Spacer(modifier = GlanceModifier.width(8.dp))
                    Text(
                        text = "UV ${weather.uvIndex}",
                        style = TextStyle(
                            color = WidgetSoftWhite,
                            fontSize = 11.sp,
                        ),
                    )
                    Spacer(modifier = GlanceModifier.width(8.dp))
                    Spacer(
                        modifier = GlanceModifier
                            .width(1.dp)
                            .height(22.dp)
                            .background(WidgetDivider),
                    )
                    Spacer(modifier = GlanceModifier.width(8.dp))
                    Text(
                        text = weather.highLow(unitSystem),
                        style = TextStyle(
                            color = WidgetSoftWhite,
                            fontSize = 11.sp,
                        ),
                    )
                }
            }
        }
    }
}
