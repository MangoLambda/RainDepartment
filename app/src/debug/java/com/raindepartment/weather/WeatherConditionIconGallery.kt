package com.raindepartment.weather

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Preview(
    name = "Weather condition icons — minimum and hero",
    widthDp = 720,
    heightDp = 820,
    showBackground = true,
)
@Composable
private fun WeatherConditionIconGalleryPreview() {
    RainDepartmentTheme(darkTheme = false) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF0F7FD))
                .padding(24.dp),
            color = Color.White,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = "Weather conditions",
                    color = Color(0xFF12346D),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                )
                WeatherCondition.entries.chunked(2).forEach { rowConditions ->
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        rowConditions.forEach { condition ->
                            WeatherConditionIconGalleryItem(
                                condition = condition,
                                modifier = Modifier.width(300.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeatherConditionIconGalleryItem(
    condition: WeatherCondition,
    modifier: Modifier = Modifier,
) {
    val spec = weatherConditionIconSpec(condition)
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WeatherConditionIcon(condition, Modifier.size(19.dp))
        Spacer(modifier = Modifier.width(14.dp))
        WeatherConditionIcon(condition, Modifier.size(58.dp))
        Spacer(modifier = Modifier.width(14.dp))
        Column {
            Text(
                text = spec.contentDescription,
                color = Color(0xFF164B91),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = spec.kind.name.lowercase().replace('_', ' '),
                color = Color(0xFF607792),
                fontSize = 10.sp,
            )
        }
    }
}
