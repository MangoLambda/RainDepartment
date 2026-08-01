package com.raindepartment.weather

import android.content.Context
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
            val selectedBackplate = BackplateChoices[WeatherPreferences.backplateIndex(context)]
            val weather = DummyWeatherData.current.forBackplate(selectedBackplate)
            val unitSystem = WeatherPreferences.unitSystem(context)
            val backplate = BackplateLoader.imageProvider(context, weather)

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
            Spacer(modifier = GlanceModifier.width(72.dp))
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
                    Column(modifier = GlanceModifier.width(66.dp)) {
                        Text(
                            text = weather.location,
                            style = TextStyle(
                                color = WidgetWhite,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                        )
                        Spacer(modifier = GlanceModifier.height(2.dp))
                        Text(
                            text = "Today",
                            style = TextStyle(
                                color = WidgetSoftWhite,
                                fontSize = 10.sp,
                            ),
                        )
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
                    Column(modifier = GlanceModifier.defaultWeight()) {
                        Text(
                            text = weather.conditionLabel,
                            style = TextStyle(
                                color = WidgetWhite,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                        )
                        Spacer(modifier = GlanceModifier.height(5.dp))
                        Text(
                            text = "${weather.precipitationChance}% precip  ·  UV ${weather.uvIndex}  ·  ${weather.highLow(unitSystem)}",
                            style = TextStyle(
                                color = WidgetSoftWhite,
                                fontSize = 10.sp,
                            ),
                        )
                    }
                }
            }
        }
    }
}
