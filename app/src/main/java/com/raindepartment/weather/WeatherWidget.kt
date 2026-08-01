package com.raindepartment.weather

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle

object WeatherWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            WeatherWidgetContent()
        }
    }
}

private val WidgetBackground = ColorProvider(
    day = Color(0xFFE8F0FE),
    night = Color(0xFFE8F0FE),
)
private val WidgetTitle = ColorProvider(
    day = Color(0xFF193157),
    night = Color(0xFF193157),
)
private val WidgetBody = ColorProvider(
    day = Color(0xFF374763),
    night = Color(0xFF374763),
)

class WeatherWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WeatherWidget
}

@androidx.compose.runtime.Composable
private fun WeatherWidgetContent() {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(WidgetBackground)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = "RainDepartment",
            style = TextStyle(
                color = WidgetTitle,
                fontWeight = FontWeight.Bold,
            ),
        )
        Spacer(modifier = GlanceModifier.height(8.dp))
        Text(
            text = "Weather coming soon",
            style = TextStyle(
                color = WidgetBody,
            ),
        )
    }
}
