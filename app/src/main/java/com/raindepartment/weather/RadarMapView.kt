package com.raindepartment.weather

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

private const val RADAR_MAP_TILE_URL = "https://a.basemaps.cartocdn.com/rastertiles/voyager"
private const val RADAR_MAP_TILE_SIZE_PX = 256.0
private const val RADAR_MAP_MIN_TILE_ZOOM = 3
private const val RADAR_MAP_MAX_TILE_ZOOM = 8
private const val RADAR_MAP_TILE_CACHE_MAX_BYTES = 24L * 1024L * 1024L
private const val RADAR_MAP_TILE_CONNECT_TIMEOUT_MILLIS = 5_000
private const val RADAR_MAP_TILE_READ_TIMEOUT_MILLIS = 8_000

private data class RadarMapTileKey(
    val zoom: Int,
    val x: Int,
    val y: Int,
)

private data class RadarMapTilePlacement(
    val tile: RadarMapTileKey,
    val column: Int,
    val row: Int,
    val leftFraction: Float,
    val topFraction: Float,
    val widthFraction: Float,
    val heightFraction: Float,
)

private data class RadarMapTileMosaic(
    val bitmap: Bitmap,
    val leftFraction: Float,
    val topFraction: Float,
    val widthFraction: Float,
    val heightFraction: Float,
)

@Composable
internal fun RadarMapBase(
    modifier: Modifier,
    viewport: EcccRadarMapViewport,
    radarBitmap: Bitmap?,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val tileCache = remember(context) {
        RadarMapTileCache(context.applicationContext)
    }
    val placements = remember(viewport.cacheKey()) {
        radarMapTilePlacements(viewport)
    }
    val tileKeys = remember(placements) {
        placements.map { it.tile }.distinct()
    }
    var tileMosaic by remember { mutableStateOf<RadarMapTileMosaic?>(null) }
    var mapSize by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(tileKeys) {
        val cachedTiles = withContext(Dispatchers.IO) {
            tileKeys.mapNotNull { tile ->
                tileCache.read(tile)?.let { tile to it }
            }.toMap()
        }
        tileMosaic = withContext(Dispatchers.Default) {
            radarMapTileMosaic(placements, cachedTiles)
        }

        val missingTiles = tileKeys.filterNot(cachedTiles::containsKey)
        if (missingTiles.isNotEmpty()) {
            val downloadedTiles = withContext(Dispatchers.IO) {
                coroutineScope {
                    missingTiles.map { tile ->
                        async { tileCache.download(tile)?.let { tile to it } }
                    }.awaitAll().filterNotNull().toMap()
                }
            }
            tileMosaic = withContext(Dispatchers.Default) {
                radarMapTileMosaic(placements, cachedTiles + downloadedTiles)
            }
        }
    }

    val radarImage = remember(radarBitmap) { radarBitmap?.asImageBitmap() }
    val mapImage = remember(tileMosaic) { tileMosaic?.bitmap?.asImageBitmap() }
    Box(
        modifier = modifier
            .background(Color(0xFFE7EEE3))
            .clipToBounds()
            .onSizeChanged { mapSize = it },
    ) {
        if (mapSize.width > 0 && mapSize.height > 0 && mapImage != null) {
            Image(
                bitmap = mapImage,
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = (tileMosaic!!.leftFraction * mapSize.width).roundToInt(),
                            y = (tileMosaic!!.topFraction * mapSize.height).roundToInt(),
                        )
                    }
                    .requiredSize(
                        width = with(density) {
                            (tileMosaic!!.widthFraction * mapSize.width).toDp()
                        },
                        height = with(density) {
                            (tileMosaic!!.heightFraction * mapSize.height).toDp()
                        },
                    ),
            )
        }

        if (radarImage != null) {
            Image(
                bitmap = radarImage,
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private fun radarMapTileMosaic(
    placements: List<RadarMapTilePlacement>,
    tiles: Map<RadarMapTileKey, Bitmap>,
): RadarMapTileMosaic? {
    if (placements.isEmpty()) return null

    val leftFraction = placements.minOf { it.leftFraction }
    val topFraction = placements.minOf { it.topFraction }
    val rightFraction = placements.maxOf { it.leftFraction + it.widthFraction }
    val bottomFraction = placements.maxOf { it.topFraction + it.heightFraction }
    val columnCount = (placements.maxOf { it.column } + 1).coerceAtLeast(1)
    val rowCount = (placements.maxOf { it.row } + 1).coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(
        columnCount * RADAR_MAP_TILE_SIZE_PX.toInt(),
        rowCount * RADAR_MAP_TILE_SIZE_PX.toInt(),
        Bitmap.Config.ARGB_8888,
    )
    Canvas(bitmap).apply {
        drawColor(android.graphics.Color.rgb(231, 238, 227))
        placements.forEach { placement ->
            val tile = tiles[placement.tile] ?: return@forEach
            drawBitmap(
                tile,
                placement.column * RADAR_MAP_TILE_SIZE_PX.toInt().toFloat(),
                placement.row * RADAR_MAP_TILE_SIZE_PX.toInt().toFloat(),
                null,
            )
        }
    }
    return RadarMapTileMosaic(
        bitmap = bitmap,
        leftFraction = leftFraction,
        topFraction = topFraction,
        widthFraction = rightFraction - leftFraction,
        heightFraction = bottomFraction - topFraction,
    )
}

private fun radarMapTilePlacements(
    viewport: EcccRadarMapViewport,
): List<RadarMapTilePlacement> {
    val zoom = radarMapTileZoom(viewport)
    val worldSize = RADAR_MAP_TILE_SIZE_PX * (1 shl zoom)
    val west = viewport.centerLongitude - viewport.longitudeSpan / 2.0
    val east = viewport.centerLongitude + viewport.longitudeSpan / 2.0
    val north = viewport.centerLatitude + viewport.latitudeSpan / 2.0
    val south = viewport.centerLatitude - viewport.latitudeSpan / 2.0
    val westX = webMercatorX(west, worldSize)
    val eastX = max(webMercatorX(east, worldSize), westX + 1.0)
    val northY = webMercatorY(north, worldSize)
    val southY = max(webMercatorY(south, worldSize), northY + 1.0)
    val firstTileX = floor(westX / RADAR_MAP_TILE_SIZE_PX).toInt()
    val lastTileX = ceil(eastX / RADAR_MAP_TILE_SIZE_PX).toInt() - 1
    val firstTileY = floor(northY / RADAR_MAP_TILE_SIZE_PX).toInt()
        .coerceAtLeast(0)
    val lastTileY = (ceil(southY / RADAR_MAP_TILE_SIZE_PX).toInt() - 1)
        .coerceAtMost((1 shl zoom) - 1)
    val visibleWidth = eastX - westX
    val visibleHeight = southY - northY
    val tileCount = 1 shl zoom

    return buildList {
        for (tileX in firstTileX..lastTileX) {
            for (tileY in firstTileY..lastTileY) {
                val tileLeft = tileX * RADAR_MAP_TILE_SIZE_PX
                val tileTop = tileY * RADAR_MAP_TILE_SIZE_PX
                add(
                    RadarMapTilePlacement(
                        tile = RadarMapTileKey(
                            zoom = zoom,
                            x = ((tileX % tileCount) + tileCount) % tileCount,
                            y = tileY,
                        ),
                        column = tileX - firstTileX,
                        row = tileY - firstTileY,
                        leftFraction = ((tileLeft - westX) / visibleWidth).toFloat(),
                        topFraction = ((tileTop - northY) / visibleHeight).toFloat(),
                        widthFraction = (RADAR_MAP_TILE_SIZE_PX / visibleWidth).toFloat(),
                        heightFraction = (RADAR_MAP_TILE_SIZE_PX / visibleHeight).toFloat(),
                    ),
                )
            }
        }
    }
}

private fun radarMapTileZoom(viewport: EcccRadarMapViewport): Int = (
    log2(360.0 / viewport.longitudeSpan).roundToInt() + 1
    ).coerceIn(RADAR_MAP_MIN_TILE_ZOOM, RADAR_MAP_MAX_TILE_ZOOM)

private fun webMercatorX(longitude: Double, worldSize: Double): Double =
    (longitude + 180.0) / 360.0 * worldSize

private fun webMercatorY(latitude: Double, worldSize: Double): Double {
    val safeLatitude = latitude.coerceIn(-85.05112878, 85.05112878)
    val sine = sin(Math.toRadians(safeLatitude))
    return (0.5 - ln((1.0 + sine) / (1.0 - sine)) / (4.0 * Math.PI)) * worldSize
}

private class RadarMapTileCache(
    context: Context,
) {
    private val directory = File(
        context.applicationContext.filesDir,
        "radar-map-tile-cache-voyager",
    )
    private val lock = Any()

    fun read(tile: RadarMapTileKey): Bitmap? = synchronized(lock) {
        val file = fileFor(tile)
        if (!file.isFile) return@synchronized null
        file.setLastModified(System.currentTimeMillis())
        BitmapFactory.decodeFile(file.absolutePath)
    }

    fun download(tile: RadarMapTileKey): Bitmap? {
        read(tile)?.let { return it }

        val connection = (URL(tileUrl(tile)).openConnection() as HttpURLConnection).apply {
            connectTimeout = RADAR_MAP_TILE_CONNECT_TIMEOUT_MILLIS
            readTimeout = RADAR_MAP_TILE_READ_TIMEOUT_MILLIS
            requestMethod = "GET"
            setRequestProperty(
                "User-Agent",
                "RainDepartment radar map/0.0.1 (OpenStreetMap and CARTO attribution)",
            )
        }

        val bytes = try {
            if (connection.responseCode !in 200..299) return null
            connection.inputStream.use { it.readBytes() }
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        } ?: return null

        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        synchronized(lock) {
            if (!directory.exists()) directory.mkdirs()
            val file = fileFor(tile)
            val temporary = File(
                directory,
                "${file.name}.tmp-${Thread.currentThread().id}-${System.nanoTime()}",
            )
            runCatching {
                temporary.writeBytes(bytes)
                if (!temporary.renameTo(file)) temporary.delete()
                evictIfNeeded()
            }
        }
        return bitmap
    }

    private fun fileFor(tile: RadarMapTileKey): File = File(
        directory,
        "${tile.zoom}-${tile.x}-${tile.y}.png",
    )

    private fun tileUrl(tile: RadarMapTileKey): String =
        "$RADAR_MAP_TILE_URL/${tile.zoom}/${tile.x}/${tile.y}.png"

    private fun evictIfNeeded() {
        val files = directory.listFiles().orEmpty()
            .filter { it.extension == "png" }
        var totalBytes = files.sumOf { it.length() }
        if (totalBytes <= RADAR_MAP_TILE_CACHE_MAX_BYTES) return

        for (file in files.sortedBy { it.lastModified() }) {
            if (totalBytes <= RADAR_MAP_TILE_CACHE_MAX_BYTES) break
            totalBytes -= file.length()
            file.delete()
        }
    }
}
