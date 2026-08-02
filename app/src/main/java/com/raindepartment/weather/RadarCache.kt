package com.raindepartment.weather

import android.content.Context
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import org.json.JSONObject

internal data class CachedEcccRadarTimeWindow(
    val window: EcccRadarTimeWindow,
    val cachedAtEpochMillis: Long,
)

internal data class CachedEcccRadarMapFrame(
    val frame: EcccRadarMapFrame,
    val cachedAtEpochMillis: Long,
)

internal interface EcccRadarMapCache {
    fun readTimeWindow(layer: String): CachedEcccRadarTimeWindow?

    fun writeTimeWindow(
        layer: String,
        window: EcccRadarTimeWindow,
        cachedAtEpochMillis: Long,
    )

    fun readFrame(key: String): CachedEcccRadarMapFrame?

    fun writeFrame(
        key: String,
        frame: EcccRadarMapFrame,
        cachedAtEpochMillis: Long,
    )
}

internal class FileEcccRadarMapCache(
    private val directory: File,
    private val clock: () -> Long = System::currentTimeMillis,
    private val maxBytes: Long = DEFAULT_MAX_BYTES,
) : EcccRadarMapCache {
    constructor(
        context: Context,
        clock: () -> Long = System::currentTimeMillis,
        maxBytes: Long = DEFAULT_MAX_BYTES,
    ) : this(
        directory = File(context.applicationContext.filesDir, CACHE_DIRECTORY_NAME),
        clock = clock,
        maxBytes = maxBytes,
    )

    private val lock = Any()

    override fun readTimeWindow(layer: String): CachedEcccRadarTimeWindow? = synchronized(lock) {
        val file = File(directory, "window-${hash(layer)}.json")
        if (!file.isFile) return@synchronized null
        runCatching {
            val root = JSONObject(file.readText())
            CachedEcccRadarTimeWindow(
                window = EcccRadarTimeWindow(
                    startEpochMillis = root.getLong("start"),
                    endEpochMillis = root.getLong("end"),
                    intervalMillis = root.getLong("interval"),
                ),
                cachedAtEpochMillis = root.getLong("cachedAt"),
            )
        }.getOrNull()
    }

    override fun writeTimeWindow(
        layer: String,
        window: EcccRadarTimeWindow,
        cachedAtEpochMillis: Long,
    ) {
        synchronized(lock) {
            ensureDirectory()
            val file = File(directory, "window-${hash(layer)}.json")
            writeAtomically(
                file = file,
                contents = JSONObject().apply {
                    put("start", window.startEpochMillis)
                    put("end", window.endEpochMillis)
                    put("interval", window.intervalMillis)
                    put("cachedAt", cachedAtEpochMillis)
                }.toString(),
            )
        }
    }

    override fun readFrame(key: String): CachedEcccRadarMapFrame? = synchronized(lock) {
        val baseName = "frame-${hash(key)}"
        val metadataFile = File(directory, "$baseName.json")
        val imageFile = File(directory, "$baseName.png")
        if (!metadataFile.isFile || !imageFile.isFile) return@synchronized null

        runCatching {
            val root = JSONObject(metadataFile.readText())
            val viewport = EcccRadarMapViewport(
                centerLatitude = root.getDouble("centerLatitude"),
                centerLongitude = root.getDouble("centerLongitude"),
                latitudeSpan = root.getDouble("latitudeSpan"),
                width = root.getInt("width"),
                height = root.getInt("height"),
            )
            val cachedAt = root.getLong("cachedAt")
            val lastAccess = clock()
            val result = CachedEcccRadarMapFrame(
                frame = EcccRadarMapFrame(
                    timeEpochMillis = root.getLong("time"),
                    imageBytes = imageFile.readBytes(),
                    viewport = viewport,
                    isFromCache = true,
                    isStale = clock() - cachedAt >= RADAR_CACHE_FRESHNESS_MILLIS,
                ),
                cachedAtEpochMillis = cachedAt,
            )
            writeAtomically(
                file = metadataFile,
                contents = root.apply { put("lastAccess", lastAccess) }.toString(),
            )
            result
        }.getOrNull()
    }

    override fun writeFrame(
        key: String,
        frame: EcccRadarMapFrame,
        cachedAtEpochMillis: Long,
    ) {
        val viewport = frame.viewport ?: return
        synchronized(lock) {
            ensureDirectory()
            val baseName = "frame-${hash(key)}"
            val metadataFile = File(directory, "$baseName.json")
            val imageFile = File(directory, "$baseName.png")
            writeAtomically(imageFile, frame.imageBytes)
            writeAtomically(
                file = metadataFile,
                contents = JSONObject().apply {
                    put("time", frame.timeEpochMillis)
                    put("centerLatitude", viewport.centerLatitude)
                    put("centerLongitude", viewport.centerLongitude)
                    put("latitudeSpan", viewport.latitudeSpan)
                    put("width", viewport.width)
                    put("height", viewport.height)
                    put("cachedAt", cachedAtEpochMillis)
                    put("lastAccess", cachedAtEpochMillis)
                }.toString(),
            )
            evictIfNeeded()
        }
    }

    private fun ensureDirectory() {
        if (!directory.exists()) directory.mkdirs()
    }

    private fun evictIfNeeded() {
        val files = directory.listFiles().orEmpty()
        var totalBytes = files.sumOf { it.length() }
        if (totalBytes <= maxBytes) return

        val entries = files
            .filter { it.extension == "json" && it.name.startsWith("frame-") }
            .mapNotNull { metadataFile ->
                runCatching {
                    metadataFile to JSONObject(metadataFile.readText()).optLong(
                        "lastAccess",
                        metadataFile.lastModified(),
                    )
                }.getOrNull()
            }
            .sortedBy { it.second }

        for ((metadataFile, _) in entries) {
            if (totalBytes <= maxBytes) break
            val imageFile = File(directory, metadataFile.name.removeSuffix(".json") + ".png")
            totalBytes -= metadataFile.length()
            totalBytes -= imageFile.length()
            metadataFile.delete()
            imageFile.delete()
        }
    }

    private fun writeAtomically(file: File, contents: String) {
        writeAtomically(file, contents.toByteArray(Charsets.UTF_8))
    }

    private fun writeAtomically(file: File, contents: ByteArray) {
        val temporary = File(directory, "${file.name}.tmp-${Thread.currentThread().id}-${System.nanoTime()}")
        temporary.writeBytes(contents)
        if (!temporary.renameTo(file)) {
            file.delete()
            check(temporary.renameTo(file)) { "Could not store radar cache file." }
        }
    }

    private fun hash(value: String): String = MessageDigest
        .getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString(separator = "") { byte -> "%02x".format(Locale.US, byte) }

    private companion object {
        const val CACHE_DIRECTORY_NAME = "radar-cache"
        const val DEFAULT_MAX_BYTES = 32L * 1024L * 1024L
    }
}
