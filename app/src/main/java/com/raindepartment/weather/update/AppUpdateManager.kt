package com.raindepartment.weather.update

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.raindepartment.weather.BuildConfig
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

data class UpdateRelease(
    val tag: String,
    val version: String,
    val title: String,
    val notes: String,
    val assetName: String,
    val downloadUrl: String,
    val size: Long,
    val sha256: String,
)

sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data class Available(val release: UpdateRelease) : UpdateUiState
    data class Downloading(
        val release: UpdateRelease,
        val bytesRead: Long,
        val totalBytes: Long,
    ) : UpdateUiState
    data class AwaitingInstallPermission(val release: UpdateRelease) : UpdateUiState
    data class ReadyToInstall(val release: UpdateRelease) : UpdateUiState
    data class Error(val message: String, val release: UpdateRelease? = null) : UpdateUiState
}

object GitHubReleaseSource {
    private val repositoryPattern = Regex("^https://github\\.com/([^/]+)/([^/]+)/?$")
    private val sha256Pattern = Regex("^[0-9a-fA-F]{64}$")

    fun releasesApiUrl(baseUrl: String): String? {
        val match = repositoryPattern.matchEntire(baseUrl.trim()) ?: return null
        return "https://api.github.com/repos/${match.groupValues[1]}/${match.groupValues[2]}/releases"
    }

    /** Kept as a small helper for callers that only need GitHub's stable-release endpoint. */
    fun latestReleaseApiUrl(baseUrl: String): String? {
        val match = repositoryPattern.matchEntire(baseUrl.trim()) ?: return null
        return "https://api.github.com/repos/${match.groupValues[1]}/${match.groupValues[2]}/releases/latest"
    }

    fun compareVersions(left: String, right: String): Int {
        val a = SemanticVersion.parse(left) ?: return 0
        val b = SemanticVersion.parse(right) ?: return 0
        return a.compareTo(b)
    }

    /**
     * RainDepartment's historical alpha builds kept the Android versionName at the base
     * version and used versionCode as the alpha number. Treat that shape as
     * `<versionName>-alpha.<versionCode>` when comparing a same-base pre-release.
     */
    fun compareToInstalled(
        remoteVersion: String,
        installedVersion: String,
        installedVersionCode: Int,
    ): Int {
        val remote = SemanticVersion.parse(remoteVersion) ?: return 0
        val installed = SemanticVersion.parse(installedVersion) ?: return 0
        val comparableInstalled = if (
            installed.preRelease.isEmpty() &&
            remote.preRelease.isNotEmpty() &&
            installed.core == remote.core &&
            installedVersionCode > 0
        ) {
            installed.copy(preRelease = listOf("alpha", installedVersionCode.toString()))
        } else {
            installed
        }
        return remote.compareTo(comparableInstalled)
    }

    /**
     * Parses either GitHub's release-list response or a single release object. Drafts are
     * ignored, while pre-releases are intentionally accepted because RainDepartment ships
     * alpha builds through GitHub pre-releases.
     */
    fun parse(json: String): UpdateRelease? {
        val value = JSONTokener(json).nextValue()
        val releases = when (value) {
            is JSONArray -> buildList {
                for (index in 0 until value.length()) {
                    value.optJSONObject(index)?.let(::parseRelease)?.let(::add)
                }
            }
            is JSONObject -> listOfNotNull(parseRelease(value))
            else -> emptyList()
        }

        return releases.maxWithOrNull { left, right ->
            val versionComparison = compareVersions(left.version, right.version)
            if (versionComparison != 0) versionComparison else left.tag.compareTo(right.tag)
        }
    }

    /** Compatibility overload for callers that share FlightLog's release-source contract. */
    fun parse(json: String, @Suppress("UNUSED_PARAMETER") supportedAbis: List<String>): UpdateRelease? =
        parse(json)

    private fun parseRelease(root: JSONObject): UpdateRelease? = runCatching {
        if (root.optBoolean("draft")) return@runCatching null

        val tag = root.optString("tag_name").trim()
        val version = tag.removePrefix("v")
        if (tag.isBlank() || SemanticVersion.parse(version) == null) return@runCatching null

        val expectedAssetName = "RainDepartment-v$version.apk"
        val assets = root.optJSONArray("assets") ?: return@runCatching null
        val asset = (0 until assets.length())
            .asSequence()
            .mapNotNull(assets::optJSONObject)
            .firstOrNull { it.optString("name") == expectedAssetName }
            ?: return@runCatching null

        val digest = asset.optString("digest")
        if (!digest.startsWith("sha256:", ignoreCase = true)) return@runCatching null
        val sha256 = digest.substringAfter(':').lowercase()
        if (!sha256Pattern.matches(sha256)) return@runCatching null

        val downloadUrl = asset.optString("browser_download_url")
        if (!downloadUrl.startsWith("https://")) return@runCatching null

        UpdateRelease(
            tag = tag,
            version = version,
            title = root.optString("name").ifBlank { "RainDepartment $tag" },
            notes = root.optString("body"),
            assetName = expectedAssetName,
            downloadUrl = downloadUrl,
            size = asset.optLong("size", -1L),
            sha256 = sha256,
        )
    }.getOrNull()

    private data class SemanticVersion(
        val core: List<Int>,
        val preRelease: List<String>,
    ) : Comparable<SemanticVersion> {
        override fun compareTo(other: SemanticVersion): Int {
            for (index in 0 until maxOf(core.size, other.core.size)) {
                val comparison = (core.getOrElse(index) { 0 })
                    .compareTo(other.core.getOrElse(index) { 0 })
                if (comparison != 0) return comparison
            }

            if (preRelease.isEmpty() && other.preRelease.isNotEmpty()) return 1
            if (preRelease.isNotEmpty() && other.preRelease.isEmpty()) return -1
            for (index in 0 until maxOf(preRelease.size, other.preRelease.size)) {
                val left = preRelease.getOrNull(index) ?: return -1
                val right = other.preRelease.getOrNull(index) ?: return 1
                val comparison = compareIdentifiers(left, right)
                if (comparison != 0) return comparison
            }
            return 0
        }

        companion object {
            fun parse(value: String): SemanticVersion? {
                val withoutBuildMetadata = value.trim().removePrefix("v").substringBefore('+')
                val sections = withoutBuildMetadata.split('-', limit = 2)
                val core = sections.firstOrNull()
                    ?.split('.')
                    ?.takeIf { it.isNotEmpty() }
                    ?.map { it.toIntOrNull() ?: return null }
                    ?: return null
                if (core.isEmpty()) return null

                val preRelease = sections.getOrNull(1)
                    ?.split('.')
                    ?.takeIf { it.isNotEmpty() && it.all(String::isNotEmpty) }
                    ?: emptyList()
                return SemanticVersion(core, preRelease)
            }

            private fun compareIdentifiers(left: String, right: String): Int {
                val leftNumeric = left.toIntOrNull()
                val rightNumeric = right.toIntOrNull()
                return when {
                    leftNumeric != null && rightNumeric != null -> leftNumeric.compareTo(rightNumeric)
                    leftNumeric != null -> -1
                    rightNumeric != null -> 1
                    else -> left.compareTo(right)
                }
            }
        }
    }
}

class AppUpdateManager(context: Context) {
    private val applicationContext = context.applicationContext
    private val preferences = applicationContext.getSharedPreferences(
        "update_preferences",
        Context.MODE_PRIVATE,
    )
    private val mutableState = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val state: StateFlow<UpdateUiState> = mutableState.asStateFlow()
    private var downloadJob: Job? = null
    private var downloadedFile: File? = null
    private var downloadedRelease: UpdateRelease? = null

    suspend fun check() {
        val apiUrl = GitHubReleaseSource.releasesApiUrl(BuildConfig.RAINDEPARTMENT_UPDATE_BASE_URL)
        if (apiUrl == null) {
            Log.w(TAG, "Update check disabled: RAINDEPARTMENT_UPDATE_BASE_URL is missing or invalid")
            return
        }

        mutableState.value = UpdateUiState.Checking
        try {
            val release = withContext(Dispatchers.IO) {
                val json = request(apiUrl)
                GitHubReleaseSource.parse(json)
            }
            val shouldShow = release != null &&
                GitHubReleaseSource.compareToInstalled(
                    remoteVersion = release.version,
                    installedVersion = BuildConfig.VERSION_NAME,
                    installedVersionCode = BuildConfig.VERSION_CODE,
                ) > 0 &&
                preferences.getString(SKIPPED_RELEASE, null) != release.tag
            mutableState.value = if (shouldShow) {
                UpdateUiState.Available(requireNotNull(release))
            } else {
                UpdateUiState.Idle
            }
        } catch (cancelled: CancellationException) {
            mutableState.value = UpdateUiState.Idle
            throw cancelled
        } catch (error: Exception) {
            Log.w(TAG, "Update check failed", error)
            mutableState.value = UpdateUiState.Idle
        }
    }

    suspend fun download(release: UpdateRelease) = withContext(Dispatchers.IO) {
        val updateDir = File(applicationContext.cacheDir, "updates").apply { mkdirs() }
        updateDir.listFiles()?.forEach { it.delete() }
        val destination = File(updateDir, release.assetName)
        downloadedFile = null
        downloadedRelease = null
        mutableState.value = UpdateUiState.Downloading(release, 0L, release.size)

        var connection: HttpURLConnection? = null
        try {
            connection = open(release.downloadUrl)
            val total = connection.contentLengthLong.takeIf { it > 0L } ?: release.size
            connection.inputStream.use { input ->
                destination.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var copied = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        copied += read
                        mutableState.value = UpdateUiState.Downloading(release, copied, total)
                    }
                }
            }
            require(sha256(destination) == release.sha256) {
                "The downloaded APK failed its checksum check."
            }
            validateApk(destination)
            downloadedFile = destination
            downloadedRelease = release
            mutableState.value = UpdateUiState.ReadyToInstall(release)
        } catch (cancelled: CancellationException) {
            destination.delete()
            mutableState.value = UpdateUiState.Available(release)
            throw cancelled
        } catch (error: Exception) {
            destination.delete()
            mutableState.value = UpdateUiState.Error(
                error.message ?: "The update could not be downloaded.",
                release,
            )
        } finally {
            connection?.disconnect()
        }
    }

    fun attachDownloadJob(job: Job) {
        downloadJob = job
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
    }

    fun skip(release: UpdateRelease) {
        preferences.edit().putString(SKIPPED_RELEASE, release.tag).apply()
        clearDownloadedFile()
        mutableState.value = UpdateUiState.Idle
    }

    fun dismissError() {
        clearDownloadedFile()
        mutableState.value = UpdateUiState.Idle
    }

    fun install(activity: Activity) {
        val release = downloadedRelease ?: return
        if (!activity.packageManager.canRequestPackageInstalls()) {
            mutableState.value = UpdateUiState.AwaitingInstallPermission(release)
            activity.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${activity.packageName}"),
                ),
            )
            return
        }
        launchInstaller(activity)
    }

    fun resumeInstall(activity: Activity) {
        if (
            mutableState.value is UpdateUiState.AwaitingInstallPermission &&
            activity.packageManager.canRequestPackageInstalls()
        ) {
            launchInstaller(activity)
        }
    }

    private fun launchInstaller(activity: Activity) {
        val file = downloadedFile?.takeIf(File::exists) ?: return
        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.files", file)
        mutableState.value = UpdateUiState.Idle
        activity.startActivity(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        })
    }

    @Suppress("DEPRECATION")
    private fun validateApk(file: File) {
        val flags = signingFlags()
        val archive = requireNotNull(
            applicationContext.packageManager.getPackageArchiveInfo(file.path, flags),
        ) { "The downloaded file is not a valid APK." }
        require(archive.packageName == applicationContext.packageName) {
            "The update belongs to a different app."
        }

        val installed = applicationContext.packageManager.getPackageInfo(
            applicationContext.packageName,
            flags,
        )
        require(versionCode(archive) > versionCode(installed)) {
            "The downloaded APK is not newer than this app."
        }
        require(signerDigests(installed) == signerDigests(archive)) {
            "The update was signed with a different key."
        }
    }

    @Suppress("DEPRECATION")
    private fun signerDigests(info: PackageInfo): Set<String> {
        val signatures: Array<Signature> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners ?: emptyArray()
        } else {
            info.signatures ?: emptyArray()
        }
        require(signatures.isNotEmpty()) { "The APK has no signing certificate." }
        return signatures.map { signature ->
            MessageDigest.getInstance("SHA-256")
                .digest(signature.toByteArray())
                .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
        }.toSet()
    }

    private fun signingFlags(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        PackageManager.GET_SIGNING_CERTIFICATES
    } else {
        @Suppress("DEPRECATION")
        PackageManager.GET_SIGNATURES
    }

    @Suppress("DEPRECATION")
    private fun versionCode(info: PackageInfo): Long = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        info.longVersionCode
    } else {
        info.versionCode.toLong()
    }

    private fun request(url: String): String = open(url).inputStream.bufferedReader().use { it.readText() }

    private fun open(url: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        require(url.startsWith("https://")) { "Update connections must use HTTPS." }
        connectTimeout = 10_000
        readTimeout = 30_000
        instanceFollowRedirects = true
        setRequestProperty("Accept", "application/vnd.github+json")
        setRequestProperty("User-Agent", "RainDepartment/${BuildConfig.VERSION_NAME}")
        require(responseCode in 200..299) { "Update server returned HTTP $responseCode." }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }

    private fun clearDownloadedFile() {
        downloadedFile?.delete()
        downloadedFile = null
        downloadedRelease = null
    }

    private companion object {
        const val TAG = "RainDepartmentUpdate"
        const val SKIPPED_RELEASE = "skipped_update_release"
    }
}
