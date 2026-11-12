package app.upcroc.di

import android.app.Application
import app.upcroc.auth.AuthRepository
import app.upcroc.core.config.WatcherConfig
import app.upcroc.core.engine.EngineState
import app.upcroc.core.engine.PollingEngine
import app.upcroc.core.platform.LogLevel
import app.upcroc.core.platform.SystemClock
import app.upcroc.core.platform.TokenProvider
import app.upcroc.data.db.EngineStateStoreImpl
import app.upcroc.data.db.JobRepositoryImpl
import app.upcroc.data.db.UpcrocDatabase
import app.upcroc.data.net.AndroidNetworkMonitor
import app.upcroc.data.prefs.SettingsRepository
import app.upcroc.data.secure.KeystoreTokenStore
import app.upcroc.logging.AppLogger
import app.upcroc.notify.AndroidNotifier
import app.upcroc.runtime.WatcherRuntime
import app.upcroc.service.WatcherScheduler
import app.upcroc.update.UpdateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class AppContainer(private val app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settings = SettingsRepository(app)
    val logger = AppLogger(app)
    val runtime = WatcherRuntime()
    val db: UpcrocDatabase = UpcrocDatabase.build(app)
    val jobRepository = JobRepositoryImpl(db)
    val engineStateStore = EngineStateStoreImpl(db)
    val engineState = EngineState(engineStateStore, SystemClock)
    val tokenStore = KeystoreTokenStore(app)
    val network = AndroidNetworkMonitor(app)

    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .connectionPool(ConnectionPool(2, 5, TimeUnit.MINUTES))
        .retryOnConnectionFailure(true)
        .dispatcher(Dispatcher().apply { maxRequests = 4; maxRequestsPerHost = 2 })
        .build()

    @Volatile
    private var config: WatcherConfig = WatcherConfig.defaults()

    val notifier = AndroidNotifier(app, { config }, logger)
    val scheduler = WatcherScheduler(app, runtime, logger)
    val updateManager = UpdateManager(app, http, settings, logger)

    private val tokenBridge = object : TokenProvider {
        override suspend fun accessToken(forceRefresh: Boolean): String = auth.accessToken(forceRefresh)
        override suspend fun invalidate() = auth.invalidate()
    }

    val engine: PollingEngine by lazy {
        PollingEngine(
            configProvider = { config },
            tokens = tokenBridge,
            http = http,
            store = jobRepository,
            state = engineStateStore,
            notifier = notifier,
            logger = logger,
            clock = SystemClock,
            network = network,
        )
    }

    val auth: AuthRepository by lazy {
        AuthRepository(
            context = app,
            settings = settings,
            tokenStore = tokenStore,
            http = http,
            logger = logger,
            engineProvider = { engine },
        )
    }

    fun start() {
        scope.launch {
            settings.configFlow.collect { config = it }
        }
        scope.launch {
            settings.runningFlow.collect { runtime.setRunning(it) }
        }
        scope.launch {
            runCatching {
                val snapshot = engine.snapshot()
                runtime.updateStatus { status ->
                    status.copy(
                        lastCheckAt = snapshot.lastCheckAt.takeIf { it > 0 },
                        nextCheckAt = snapshot.nextDueAt.takeIf { it > 0 },
                        failures = snapshot.failures,
                        lastError = snapshot.lastError,
                    )
                }
            }
        }
        logger.log(LogLevel.INFO, "Upcroc ${app.packageName} started")
    }

    suspend fun currentConfig(): WatcherConfig = settings.configOnce()
}
