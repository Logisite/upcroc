package app.upcroc.backup

import android.content.Context
import android.net.Uri
import app.upcroc.BuildConfig
import app.upcroc.core.auth.TokenSet
import app.upcroc.core.config.WatcherConfig
import app.upcroc.core.model.CheckRecord
import app.upcroc.core.model.Job
import app.upcroc.core.model.SearchDraft
import app.upcroc.core.util.CoreJson
import app.upcroc.data.db.CheckEntity
import app.upcroc.data.db.toEntity
import app.upcroc.data.db.toModel
import app.upcroc.data.prefs.UiState
import app.upcroc.di.AppContainer
import app.upcroc.logging.LogLine
import app.upcroc.service.WatcherService
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import java.io.IOException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class BackupOptions(
    val includeAuth: Boolean = true,
    val includeConfig: Boolean = true,
    val includeUiState: Boolean = true,
    val includeDraft: Boolean = true,
    val includeJobs: Boolean = true,
    val includeChecks: Boolean = true,
    val includeLogs: Boolean = false,
)

data class RestoreOptions(
    val restoreAuth: Boolean = true,
    val restoreConfig: Boolean = true,
    val restoreUiState: Boolean = true,
    val restoreDraft: Boolean = true,
    val restoreJobs: Boolean = true,
    val restoreChecks: Boolean = true,
    val restoreLogs: Boolean = true,
    val restoreOnboarding: Boolean = true,
    val restoreRunning: Boolean = true,
)

@Serializable
data class BackupPayload(
    val format: Int = BackupPayload.FORMAT,
    val createdAt: Long,
    val appVersion: String,
    val onboardingComplete: Boolean = false,
    val running: Boolean = false,
    val auth: TokenSet? = null,
    val config: WatcherConfig? = null,
    val uiState: UiState? = null,
    val draft: SearchDraft? = null,
    val jobs: List<Job> = emptyList(),
    val checks: List<CheckRecord> = emptyList(),
    val logs: List<LogLine> = emptyList(),
) {
    companion object {
        const val FORMAT = 1
    }
}

/**
 * Password-protected backup. The token is decrypted from the Android Keystore,
 * placed in the encrypted backup, and re-encrypted into the current install on
 * restore. The password is never persisted.
 */
class BackupManager(private val container: AppContainer) {
    private val context: Context get() = container.app
    private val random = SecureRandom()

    suspend fun create(uri: Uri, password: CharArray, options: BackupOptions) {
        val payload = buildPayload(options)
        val bytes = encrypt(payload, password)
        context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
            ?: throw IOException("Could not open the backup file.")
        container.logger.info("backup: saved ${bytes.size} bytes")
    }

    suspend fun restore(uri: Uri, password: CharArray, options: RestoreOptions) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IOException("Could not read the backup file.")
        val payload = decrypt(bytes, password)
        if (payload.format != BackupPayload.FORMAT) {
            throw IllegalArgumentException("This backup uses an unsupported format.")
        }
        if (options.restoreOnboarding) {
            container.settings.setOnboardingComplete(payload.onboardingComplete)
        }
        if (options.restoreRunning) {
            container.settings.setRunning(payload.running)
        }
        val auth = payload.auth
        if (options.restoreAuth && auth != null) {
            container.auth.importTokenSet(auth)
        }
        val config = payload.config
        if (options.restoreConfig && config != null) {
            container.settings.saveConfig(config)
        }
        val uiState = payload.uiState
        if (options.restoreUiState && uiState != null) {
            container.settings.saveUiState(uiState)
        }
        val draft = payload.draft
        if (options.restoreDraft && draft != null) {
            container.settings.saveDraft(draft)
        }
        if (options.restoreJobs && payload.jobs.isNotEmpty()) {
            container.db.jobDao().insertAll(payload.jobs.map { it.toEntity() })
        }
        if (options.restoreChecks && payload.checks.isNotEmpty()) {
            payload.checks.forEach { container.db.checkDao().insert(it.toEntity()) }
        }
        if (options.restoreLogs && payload.logs.isNotEmpty()) {
            container.logger.importLines(payload.logs)
        }
        container.logger.info(
            "backup: restored auth=${payload.auth != null} jobs=${payload.jobs.size} " +
                "checks=${payload.checks.size} logs=${payload.logs.size}",
        )
        if (options.restoreRunning && payload.running) {
            WatcherService.ensure(context)
        }
    }

    private suspend fun buildPayload(options: BackupOptions): BackupPayload {
        val auth = if (options.includeAuth) container.auth.exportTokenSet() else null
        val config = if (options.includeConfig) container.settings.configOnce() else null
        val uiState = if (options.includeUiState) container.settings.uiStateFlow.first() else null
        val draft = if (options.includeDraft) container.settings.draftFlow.first() else null
        val jobs = if (options.includeJobs) {
            container.db.jobDao().all().map { it.toModel() }
        } else {
            emptyList()
        }
        val checks = if (options.includeChecks) {
            container.db.checkDao().since(0).map { it.toRecord() }
        } else {
            emptyList()
        }
        val logs = if (options.includeLogs) container.logger.lines.value else emptyList()
        return BackupPayload(
            createdAt = System.currentTimeMillis(),
            appVersion = BuildConfig.VERSION_NAME,
            onboardingComplete = container.settings.onboardingCompleteFlow.first(),
            running = container.settings.runningFlow.first(),
            auth = auth,
            config = config,
            uiState = uiState,
            draft = draft,
            jobs = jobs,
            checks = checks,
            logs = logs,
        )
    }

    private fun encrypt(payload: BackupPayload, password: CharArray): ByteArray {
        val salt = ByteArray(SALT_BYTES).also { random.nextBytes(it) }
        val iv = ByteArray(IV_BYTES).also { random.nextBytes(it) }
        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        val plain = CoreJson.encodeToString(BackupPayload.serializer(), payload).toByteArray(Charsets.UTF_8)
        return MAGIC + salt + iv + cipher.doFinal(plain)
    }

    private fun decrypt(bytes: ByteArray, password: CharArray): BackupPayload {
        if (bytes.size <= MAGIC.size + SALT_BYTES + IV_BYTES ||
            !bytes.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)
        ) {
            throw IllegalArgumentException("This is not a Upcroc backup file.")
        }
        val salt = bytes.copyOfRange(MAGIC.size, MAGIC.size + SALT_BYTES)
        val iv = bytes.copyOfRange(MAGIC.size + SALT_BYTES, MAGIC.size + SALT_BYTES + IV_BYTES)
        val cipherText = bytes.copyOfRange(MAGIC.size + SALT_BYTES + IV_BYTES, bytes.size)
        val key = deriveKey(password, salt)
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
            val plain = cipher.doFinal(cipherText)
            CoreJson.decodeFromString(BackupPayload.serializer(), String(plain, Charsets.UTF_8))
        } catch (e: Exception) {
            throw IllegalArgumentException("That password is not correct for this backup.")
        }
    }

    private fun deriveKey(password: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password, salt, ITERATIONS, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded
    }

    private fun CheckEntity.toRecord(): CheckRecord = CheckRecord(
        startedAt = startedAt,
        finishedAt = finishedAt,
        groupId = groupId,
        status = status,
        pages = pages,
        requests = requests,
        newJobs = newJobs,
        notified = notified,
        durationMs = durationMs,
        error = error,
    )

    private fun CheckRecord.toEntity(): CheckEntity = CheckEntity(
        startedAt = startedAt,
        finishedAt = finishedAt,
        groupId = groupId,
        status = status,
        pages = pages,
        requests = requests,
        newJobs = newJobs,
        notified = notified,
        durationMs = durationMs,
        error = error,
    )

    private companion object {
        val MAGIC = "UPCROC-BACKUP-1".toByteArray(Charsets.UTF_8)
        const val SALT_BYTES = 16
        const val IV_BYTES = 12
        const val ITERATIONS = 120_000
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
