package app.upcroc.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import app.upcroc.BuildConfig
import app.upcroc.core.platform.Logger
import app.upcroc.data.prefs.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

@Serializable
data class UpdateInfo(
    val versionCode: Int,
    val versionName: String = "",
    val apkUrl: String = "",
    val sha256: String = "",
    val changelog: String = "",
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data object Downloading : UpdateState
    data class Ready(val message: String) : UpdateState
    data class Error(val message: String) : UpdateState
}

class UpdateManager(
    private val context: Context,
    private val http: OkHttpClient,
    private val settings: SettingsRepository,
    private val logger: Logger,
    private val manifestUrl: String = BuildConfig.UPDATE_MANIFEST_URL,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state

    suspend fun check(force: Boolean = false) {
        if (!BuildConfig.SELF_UPDATE) {
            _state.value = UpdateState.UpToDate
            return
        }
        val now = System.currentTimeMillis()
        if (!force) {
            val last = settings.lastUpdateCheckOnce()
            if (now - last < 24 * 60 * 60 * 1000L) return
        }
        _state.value = UpdateState.Checking
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(manifestUrl)
                    .header("Accept", "application/json")
                    .header("User-Agent", "Upcroc/${BuildConfig.VERSION_NAME}")
                    .build()
                http.newCall(request).execute().use { response ->
                    if (response.code == 404) {
                        settings.setLastUpdateCheck(now)
                        _state.value = UpdateState.UpToDate
                        return@withContext
                    }
                    if (!response.isSuccessful) {
                        _state.value = UpdateState.Error("Update check returned HTTP ${response.code}.")
                        return@withContext
                    }
                    val body = response.body?.string().orEmpty()
                    val info = json.decodeFromString(UpdateInfo.serializer(), body)
                    settings.setLastUpdateCheck(now)
                    _state.value = if (info.versionCode > BuildConfig.VERSION_CODE) {
                        UpdateState.Available(info)
                    } else {
                        UpdateState.UpToDate
                    }
                }
            } catch (e: Exception) {
                logger.warn("update check failed", e)
                _state.value = UpdateState.Error("Could not check for updates: ${e.message ?: "network error"}")
            }
        }
    }

    suspend fun downloadAndInstall(info: UpdateInfo) {
        _state.value = UpdateState.Downloading
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            val file = File(dir, "upcroc-${info.versionCode}.apk")
            try {
                val request = Request.Builder()
                    .url(info.apkUrl)
                    .header("User-Agent", "Upcroc/${BuildConfig.VERSION_NAME}")
                    .build()
                http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        _state.value = UpdateState.Error("Download returned HTTP ${response.code}.")
                        return@withContext
                    }
                    response.body?.byteStream()?.use { input ->
                        file.outputStream().use { output -> input.copyTo(output) }
                    } ?: run {
                        _state.value = UpdateState.Error("The update download was empty.")
                        return@withContext
                    }
                }
                if (info.sha256.isNotBlank()) {
                    val actual = sha256(file)
                    if (!actual.equals(info.sha256, ignoreCase = true)) {
                        file.delete()
                        _state.value = UpdateState.Error("The update failed its integrity check.")
                        return@withContext
                    }
                }
                if (!signatureMatches(file)) {
                    file.delete()
                    _state.value = UpdateState.Error("The update was not signed by the same developer.")
                    return@withContext
                }
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                val intent = Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                _state.value = UpdateState.Ready("Installer opened. Follow the Android prompt to update.")
            } catch (e: Exception) {
                logger.warn("update install failed", e)
                _state.value = UpdateState.Error(e.message ?: "Could not install the update.")
            }
        }
    }

    fun dismiss() {
        _state.value = UpdateState.Idle
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun signatureMatches(file: File): Boolean {
        val pm = context.packageManager
        val archive = if (Build.VERSION.SDK_INT >= 33) {
            pm.getPackageArchiveInfo(file.absolutePath, PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
        } ?: return false
        val installed = if (Build.VERSION.SDK_INT >= 33) {
            pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        }
        val archiveSigners = archive.signingInfo?.apkContentsSigners?.map { it.toCharsString() }?.toSet() ?: return false
        val installedSigners = installed.signingInfo?.apkContentsSigners?.map { it.toCharsString() }?.toSet() ?: return false
        return archiveSigners.isNotEmpty() && archiveSigners == installedSigners
    }
}
