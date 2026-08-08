package com.raindepartment.weather

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min

internal enum class WeatherConditionIconKind {
    CLEAR,
    MOSTLY_CLEAR,
    PARTLY_CLOUDY,
    OVERCAST,
    FOG,
    HAZE,
    DRIZZLE,
    RAIN,
    HEAVY_RAIN,
    THUNDERSTORM,
    SNOW,
    HEAVY_SNOW,
    WINTRY_MIX,
    SEVERE_WEATHER,
}

internal data class WeatherConditionIconSpec(
    val kind: WeatherConditionIconKind,
    val contentDescription: String,
    val weatherMarkCount: Int,
)

internal fun weatherConditionIconSpec(condition: WeatherCondition): WeatherConditionIconSpec =
    when (condition) {
        WeatherCondition.CLEAR -> WeatherConditionIconSpec(
            WeatherConditionIconKind.CLEAR,
            "Clear",
            weatherMarkCount = 1,
        )
        WeatherCondition.MOSTLY_CLEAR -> WeatherConditionIconSpec(
            WeatherConditionIconKind.MOSTLY_CLEAR,
            "Mostly clear",
            weatherMarkCount = 2,
        )
        WeatherCondition.PARTLY_CLOUDY -> WeatherConditionIconSpec(
            WeatherConditionIconKind.PARTLY_CLOUDY,
            "Partly cloudy",
            weatherMarkCount = 2,
        )
        WeatherCondition.OVERCAST -> WeatherConditionIconSpec(
            WeatherConditionIconKind.OVERCAST,
            "Overcast",
            weatherMarkCount = 2,
        )
        WeatherCondition.FOG -> WeatherConditionIconSpec(
            WeatherConditionIconKind.FOG,
            "Fog",
            weatherMarkCount = 3,
        )
        WeatherCondition.ATMOSPHERIC_HAZE -> WeatherConditionIconSpec(
            WeatherConditionIconKind.HAZE,
            "Atmospheric haze",
            weatherMarkCount = 2,
        )
        WeatherCondition.DRIZZLE -> WeatherConditionIconSpec(
            WeatherConditionIconKind.DRIZZLE,
            "Drizzle",
            weatherMarkCount = 2,
        )
        WeatherCondition.RAIN -> WeatherConditionIconSpec(
            WeatherConditionIconKind.RAIN,
            "Rain",
            weatherMarkCount = 3,
        )
        WeatherCondition.HEAVY_RAIN -> WeatherConditionIconSpec(
            WeatherConditionIconKind.HEAVY_RAIN,
            "Heavy rain",
            weatherMarkCount = 4,
        )
        WeatherCondition.THUNDERSTORM -> WeatherConditionIconSpec(
            WeatherConditionIconKind.THUNDERSTORM,
            "Thunderstorm",
            weatherMarkCount = 1,
        )
        WeatherCondition.SNOW -> WeatherConditionIconSpec(
            WeatherConditionIconKind.SNOW,
            "Snow",
            weatherMarkCount = 2,
        )
        WeatherCondition.HEAVY_SNOW -> WeatherConditionIconSpec(
            WeatherConditionIconKind.HEAVY_SNOW,
            "Heavy snow",
            weatherMarkCount = 3,
        )
        WeatherCondition.WINTRY_MIX -> WeatherConditionIconSpec(
            WeatherConditionIconKind.WINTRY_MIX,
            "Wintry mix",
            weatherMarkCount = 2,
        )
        WeatherCondition.SEVERE_WEATHER -> WeatherConditionIconSpec(
            WeatherConditionIconKind.SEVERE_WEATHER,
            "Severe weather",
            weatherMarkCount = 2,
        )
    }

private val SunAmber = Color(0xFFF4B52D)
private val CloudBlue = Color(0xFF6795C4)
private val CloudSlate = Color(0xFF71869E)
private val RainBlue = Color(0xFF168CE4)
private val StormIndigo = Color(0xFF5F77A7)
private val HazeAmber = Color(0xFFC79B3B)

@Composable
internal fun WeatherConditionIcon(
    condition: WeatherCondition,
    modifier: Modifier = Modifier,
) {
    val spec = weatherConditionIconSpec(condition)
    Canvas(
        modifier = modifier.semantics {
            contentDescription = spec.contentDescription
        },
    ) {
        val side = min(size.width, size.height)
        val origin = Offset(
            x = (size.width - side) / 2f,
            y = (size.height - side) / 2f,
        )
        val strokeWidth = max(1.1.dp.toPx(), side * 0.052f)
        val icon = WeatherIconDrawScope(
            drawScope = this,
            origin = origin,
            side = side,
            strokeWidth = strokeWidth,
        )

        when (spec.kind) {
            WeatherConditionIconKind.CLEAR -> icon.drawSun(
                centerX = 0.50f,
                centerY = 0.50f,
                radius = 0.20f,
                rayRadius = 0.35f,
                color = SunAmber,
            )
            WeatherConditionIconKind.MOSTLY_CLEAR -> {
                icon.drawSun(0.39f, 0.38f, 0.18f, 0.30f, SunAmber)
                icon.drawCloud(Rect(0.37f, 0.40f, 0.94f, 0.79f), CloudBlue)
            }
            WeatherConditionIconKind.PARTLY_CLOUDY -> {
                icon.drawSun(0.35f, 0.34f, 0.14f, 0.24f, SunAmber)
                icon.drawCloud(Rect(0.23f, 0.35f, 0.95f, 0.80f), CloudBlue)
            }
            WeatherConditionIconKind.OVERCAST -> {
                icon.drawCloud(Rect(0.12f, 0.22f, 0.70f, 0.61f), CloudBlue.copy(alpha = 0.72f))
                icon.drawCloud(Rect(0.24f, 0.35f, 0.94f, 0.78f), CloudSlate)
            }
            WeatherConditionIconKind.FOG -> {
                icon.drawCloud(Rect(0.20f, 0.08f, 0.80f, 0.48f), CloudSlate)
                icon.drawBand(0.15f, 0.85f, 0.60f, CloudSlate)
                icon.drawBand(0.25f, 0.75f, 0.75f, CloudSlate)
                icon.drawBand(0.18f, 0.82f, 0.90f, CloudSlate)
            }
            WeatherConditionIconKind.HAZE -> {
                icon.drawSun(0.50f, 0.34f, 0.14f, 0.24f, HazeAmber)
                icon.drawBand(0.18f, 0.82f, 0.62f, CloudSlate)
                icon.drawBand(0.27f, 0.73f, 0.79f, CloudSlate)
            }
            WeatherConditionIconKind.DRIZZLE -> {
                icon.drawPrecipitationCloud()
                icon.drawRainStroke(0.42f, 0.69f, 0.81f, 0.72f)
                icon.drawRainStroke(0.62f, 0.73f, 0.85f, 0.72f)
            }
            WeatherConditionIconKind.RAIN -> {
                icon.drawPrecipitationCloud()
                listOf(0.34f, 0.50f, 0.66f).forEach { x ->
                    icon.drawRainStroke(x, 0.66f, 0.84f)
                }
            }
            WeatherConditionIconKind.HEAVY_RAIN -> {
                icon.drawPrecipitationCloud()
                listOf(0.26f, 0.42f, 0.58f, 0.74f).forEach { x ->
                    icon.drawRainStroke(x, 0.63f, 0.90f, strokeScale = 1.14f)
                }
            }
            WeatherConditionIconKind.THUNDERSTORM -> {
                icon.drawPrecipitationCloud(StormIndigo)
                icon.drawBolt(centerX = 0.50f, topY = 0.57f)
            }
            WeatherConditionIconKind.SNOW -> {
                icon.drawPrecipitationCloud()
                icon.drawSnowflake(0.40f, 0.76f, 0.085f)
                icon.drawSnowflake(0.63f, 0.76f, 0.085f)
            }
            WeatherConditionIconKind.HEAVY_SNOW -> {
                icon.drawPrecipitationCloud()
                icon.drawSnowflake(0.30f, 0.73f, 0.075f)
                icon.drawSnowflake(0.50f, 0.84f, 0.075f)
                icon.drawSnowflake(0.70f, 0.73f, 0.075f)
            }
            WeatherConditionIconKind.WINTRY_MIX -> {
                icon.drawPrecipitationCloud()
                icon.drawRainStroke(0.37f, 0.67f, 0.86f)
                icon.drawSnowflake(0.66f, 0.76f, 0.09f)
            }
            WeatherConditionIconKind.SEVERE_WEATHER -> {
                icon.drawPrecipitationCloud(StormIndigo)
                icon.drawBolt(centerX = 0.35f, topY = 0.64f, scale = 0.68f)
                icon.drawBolt(centerX = 0.58f, topY = 0.57f, scale = 0.92f)
            }
        }
    }
}

private class WeatherIconDrawScope(
    private val drawScope: DrawScope,
    private val origin: Offset,
    private val side: Float,
    private val strokeWidth: Float,
) {
    private fun point(x: Float, y: Float) = Offset(origin.x + side * x, origin.y + side * y)

    private fun stroke(widthScale: Float = 1f) = Stroke(
        width = strokeWidth * widthScale,
        cap = StrokeCap.Round,
        join = StrokeJoin.Round,
    )

    fun drawSun(
        centerX: Float,
        centerY: Float,
        radius: Float,
        rayRadius: Float,
        color: Color,
    ) = with(drawScope) {
        drawCircle(
            color = color,
            radius = side * radius,
            center = point(centerX, centerY),
            style = stroke(),
        )
        val diagonal = 0.7071f
        listOf(
            Offset(0f, -1f), Offset(diagonal, -diagonal), Offset(1f, 0f),
            Offset(diagonal, diagonal), Offset(0f, 1f), Offset(-diagonal, diagonal),
            Offset(-1f, 0f), Offset(-diagonal, -diagonal),
        ).forEach { direction ->
            val inner = radius + 0.055f
            drawLine(
                color = color,
                start = point(
                    centerX + direction.x * inner,
                    centerY + direction.y * inner,
                ),
                end = point(
                    centerX + direction.x * rayRadius,
                    centerY + direction.y * rayRadius,
                ),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
        }
    }

    fun drawCloud(bounds: Rect, color: Color) = with(drawScope) {
        fun x(value: Float) = origin.x + side * (bounds.left + bounds.width * value)
        fun y(value: Float) = origin.y + side * (bounds.top + bounds.height * value)

        val path = Path().apply {
            moveTo(x(0.22f), y(0.82f))
            cubicTo(x(0.09f), y(0.82f), x(0.02f), y(0.69f), x(0.04f), y(0.55f))
            cubicTo(x(0.06f), y(0.41f), x(0.17f), y(0.32f), x(0.30f), y(0.34f))
            cubicTo(x(0.36f), y(0.13f), x(0.53f), y(0.02f), x(0.69f), y(0.12f))
            cubicTo(x(0.80f), y(0.18f), x(0.86f), y(0.31f), x(0.85f), y(0.44f))
            cubicTo(x(0.96f), y(0.45f), x(1.02f), y(0.55f), x(0.99f), y(0.67f))
            cubicTo(x(0.97f), y(0.77f), x(0.88f), y(0.82f), x(0.78f), y(0.82f))
            close()
        }
        drawPath(path = path, color = color, style = stroke())
    }

    fun drawBand(startX: Float, endX: Float, y: Float, color: Color) = with(drawScope) {
        drawLine(
            color = color,
            start = point(startX, y),
            end = point(endX, y),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
    }

    fun drawPrecipitationCloud(color: Color = CloudBlue) {
        drawCloud(Rect(0.14f, 0.03f, 0.86f, 0.55f), color)
    }

    fun drawRainStroke(
        x: Float,
        startY: Float,
        endY: Float,
        strokeScale: Float = 1f,
    ) = with(drawScope) {
        drawLine(
            color = RainBlue,
            start = point(x + 0.025f, startY),
            end = point(x - 0.025f, endY),
            strokeWidth = strokeWidth * strokeScale,
            cap = StrokeCap.Round,
        )
    }

    fun drawSnowflake(centerX: Float, centerY: Float, radius: Float) = with(drawScope) {
        val diagonal = 0.866f
        listOf(
            Offset(0f, 1f),
            Offset(diagonal, 0.5f),
            Offset(diagonal, -0.5f),
        ).forEach { direction ->
            drawLine(
                color = RainBlue,
                start = point(
                    centerX - direction.x * radius,
                    centerY - direction.y * radius,
                ),
                end = point(
                    centerX + direction.x * radius,
                    centerY + direction.y * radius,
                ),
                strokeWidth = strokeWidth * 0.72f,
                cap = StrokeCap.Round,
            )
        }
    }

    fun drawBolt(
        centerX: Float,
        topY: Float,
        scale: Float = 1f,
    ) = with(drawScope) {
        fun boltPoint(xOffset: Float, yOffset: Float): Offset = point(
            centerX + xOffset * scale,
            topY + yOffset * scale,
        )
        val path = Path().apply {
            boltPoint(0.04f, 0f).let { moveTo(it.x, it.y) }
            boltPoint(-0.09f, 0.18f).let { lineTo(it.x, it.y) }
            boltPoint(0.01f, 0.18f).let { lineTo(it.x, it.y) }
            boltPoint(-0.05f, 0.38f).let { lineTo(it.x, it.y) }
            boltPoint(0.14f, 0.13f).let { lineTo(it.x, it.y) }
            boltPoint(0.05f, 0.13f).let { lineTo(it.x, it.y) }
            close()
        }
        drawPath(path = path, color = StormIndigo)
    }
}
