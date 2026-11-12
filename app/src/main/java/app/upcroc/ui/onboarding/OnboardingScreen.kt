package app.upcroc.ui.onboarding

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.upcroc.core.config.NotificationConfig
import app.upcroc.core.config.QuietHours
import app.upcroc.core.model.SearchGroup
import app.upcroc.core.model.SearchMode
import app.upcroc.permissions.Permissions
import app.upcroc.ui.MainViewModel
import app.upcroc.ui.components.AppDialog
import app.upcroc.ui.components.AppText
import app.upcroc.ui.components.AuthForm
import app.upcroc.ui.components.Avatar
import app.upcroc.ui.components.CrescentMark
import app.upcroc.ui.components.DialogAction
import app.upcroc.ui.icons.IconGlyph
import app.upcroc.ui.components.PrimaryButton
import app.upcroc.ui.components.SwitchRow
import app.upcroc.ui.components.TextLink
import app.upcroc.ui.components.ToastHost
import app.upcroc.ui.components.UpField
import app.upcroc.ui.components.pressable
import app.upcroc.ui.components.rememberToastState
import app.upcroc.ui.icons.UiIcon
import app.upcroc.ui.theme.UpType
import app.upcroc.ui.theme.Upcroc
import app.upcroc.ui.util.Fmt as Format

private val StepButtonLabels = listOf("Continue", "Continue", "Sign in with Upwork", "Continue", "Start watching")

/**
 * The five-step first-run guide from prototype/onboarding.html. Same order,
 * same copy, with the real auth, sound, quiet-hours, keyword, and permission
 * actions wired behind it. It is only shown when onboarding is incomplete.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(
    vm: MainViewModel,
    onFinished: () -> Unit,
) {
    val context = LocalContext.current
    val config by vm.config.collectAsStateWithLifecycle()
    val auth by vm.authState.collectAsStateWithLifecycle()
    val toast = rememberToastState()

    var step by remember { mutableIntStateOf(0) }
    var groups by remember { mutableStateOf(config.searches) }
    var filters by remember { mutableStateOf(config.filters) }
    var notifications by remember { mutableStateOf(config.notifications) }
    var quiet by remember { mutableStateOf(config.cadence.quietHours) }
    var initialized by remember { mutableStateOf(false) }
    var keywordTarget by remember { mutableStateOf<Int?>(null) }
    var keywordText by remember { mutableStateOf("") }
    var keywordDialog by remember { mutableStateOf(false) }
    var permissionTick by remember { mutableIntStateOf(0) }

    LaunchedEffect(config) {
        if (!initialized) {
            groups = config.searches
            filters = config.filters
            notifications = config.notifications
            quiet = config.cadence.quietHours
            initialized = true
        }
    }

    var wasConnected by remember { mutableStateOf(auth.signedIn) }
    LaunchedEffect(auth.signedIn) {
        if (auth.signedIn && !wasConnected) toast.show("Connected to Upwork · read-only")
        wasConnected = auth.signedIn
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { permissionTick++ }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        notifications = notifications.copy(enabled = granted)
        if (!granted) toast.show("Notifications stay off until Android allows them.")
    }

    val ringtonePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            notifications = notifications.copy(soundUri = uri?.toString())
        }
    }

    fun pickSound() {
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
            putExtra(
                RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                notifications.soundUri?.let(Uri::parse) ?: Settings.System.DEFAULT_NOTIFICATION_URI,
            )
        }
        ringtonePicker.launch(intent)
    }

    fun finish() {
        vm.completeOnboarding(
            groups = groups,
            filters = filters,
            notifications = notifications,
            quietHours = quiet,
            context = context,
            onDone = onFinished,
        )
    }

    fun next() {
        if (step == 2 && !auth.signedIn) {
            vm.signIn()
            return
        }
        if (step == 4) {
            finish()
            return
        }
        step = (step + 1).coerceAtMost(4)
    }

    val backgroundOk = remember(permissionTick) {
        Permissions.notificationsEnabled(context) && Permissions.batteryExempt(context)
    }

    fun requestBackground() {
        if (!Permissions.notificationsEnabled(context)) {
            if (Permissions.notificationPermissionGranted(context)) {
                Permissions.openNotificationSettings(context)
            } else {
                notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
            return
        }
        if (!Permissions.batteryExempt(context)) {
            Permissions.openBatterySettings(context)
            toast.show("Set Upcroc battery usage to Unrestricted.")
        }
    }

    fun editKeyword(index: Int) {
        val group = groups.getOrNull(index) ?: return
        keywordTarget = index
        keywordText = group.keywords
        keywordDialog = true
    }

    fun addKeyword() {
        val index = groups.indexOfFirst { it.mode == SearchMode.SEARCH && it.keywords.isBlank() }
        if (index < 0) {
            toast.show("Three search slots are already in use.")
            return
        }
        keywordTarget = index
        keywordText = ""
        keywordDialog = true
    }

    Box(Modifier.fillMaxSize().background(Upcroc.Ink)) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 18.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .pressable({ if (step > 0) step -= 1 }, enabled = step > 0),
                    contentAlignment = Alignment.Center,
                ) {
                    IconGlyph(
                        UiIcon.Back,
                        Modifier.size(18.dp),
                        if (step == 0) Upcroc.Faint else Upcroc.Muted,
                    )
                }
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    repeat(5) { index ->
                        Box(
                            Modifier
                                .weight(1f)
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(
                                    when {
                                        index == step -> Upcroc.Blue
                                        index < step -> Color(0xFF5C5C5C)
                                        else -> Color(0xFF242424)
                                    }
                                )
                        )
                    }
                }
                TextLink(
                    "Skip",
                    onClick = { finish() },
                    color = Upcroc.Muted,
                    style = UpType.caption.copy(fontSize = 11.sp),
                )
            }

            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(top = 18.dp, bottom = 12.dp),
            ) {
                when (step) {
                    0 -> WelcomeStep()
                    1 -> HowStep()
                    2 -> AccountStep(auth, onSignIn = vm::signIn, onSubmitManual = vm::submitManualAuth)
                    3 -> NotificationsStep(
                        notifications = notifications,
                        quiet = quiet,
                        onNotificationsChange = { notifications = it },
                        onQuietChange = { quiet = it },
                        onRequestPermission = {
                            if (!Permissions.notificationPermissionGranted(context)) {
                                notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                            }
                        },
                        onPickSound = ::pickSound,
                    )
                    4 -> FirstSearchStep(
                        groups = groups,
                        onEditKeyword = ::editKeyword,
                        onAddKeyword = ::addKeyword,
                        backgroundOk = backgroundOk,
                        onBackgroundChange = { enabled ->
                            if (enabled) {
                                requestBackground()
                                permissionTick++
                            }
                        },
                        searches = groups.size,
                        intervalMin = groups.firstOrNull()?.intervalSec ?: 90,
                        intervalMax = groups.firstOrNull()?.intervalUpperSec ?: 300,
                        softCap = config.budget.dailyLimit,
                    )
                }
            }

            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 10.dp)) {
                AppText(
                    text = if (step == 2 && auth.signedIn) "Connected" else "Step ${step + 1} of 5",
                    style = UpType.tiny.copy(fontWeight = FontWeight.W600),
                    color = Upcroc.Dim,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(9.dp))
                PrimaryButton(
                    text = if (step == 2 && auth.signedIn) "Continue" else StepButtonLabels[step],
                    onClick = ::next,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !(step == 2 && auth.busy),
                    icon = when {
                        step == 2 && !auth.signedIn -> UiIcon.Key
                        step == 4 -> UiIcon.Play
                        else -> null
                    },
                )
            }
        }

        ToastHost(
            state = toast,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp),
        )
    }

    if (keywordDialog) {
        val target = keywordTarget
        AppDialog(
            onDismissRequest = { keywordDialog = false },
            title = "Keyword search",
            body = {
                AppText(
                    "The first search stays smart. This slot checks one keyword phrase.",
                    style = UpType.small,
                    color = Upcroc.Muted,
                )
                Spacer(Modifier.height(10.dp))
                UpField(
                    label = "Keywords",
                    value = keywordText,
                    onValueChange = { keywordText = it },
                    placeholder = "AI automation",
                    modifier = Modifier.fillMaxWidth(),
                )
                if (target != null && groups.getOrNull(target)?.keywords?.isNotBlank() == true) {
                    Spacer(Modifier.height(8.dp))
                    TextLink(
                        "Remove this keyword",
                        onClick = {
                            groups = groups.mapIndexed { index, group ->
                                if (index == target) {
                                    group.copy(
                                        name = "Keyword search ${index + 1}",
                                        keywords = "",
                                        enabled = false,
                                    )
                                } else group
                            }
                            keywordDialog = false
                        },
                        color = Upcroc.BannerDanger,
                    )
                }
            },
            buttons = {
                DialogAction("Cancel", { keywordDialog = false }, Modifier.weight(1f))
                DialogAction(
                    "Save",
                    {
                        if (target != null && keywordText.isNotBlank()) {
                            groups = groups.mapIndexed { index, group ->
                                if (index == target) {
                                    group.copy(
                                        name = keywordText.trim(),
                                        keywords = keywordText.trim(),
                                        enabled = true,
                                        mode = SearchMode.SEARCH,
                                    )
                                } else group
                            }
                        }
                        keywordDialog = false
                    },
                    Modifier.weight(1f),
                    filled = true,
                )
            },
        )
    }
}

@Composable
private fun StepKicker(text: String) {
    AppText(
        text.uppercase(),
        style = UpType.caption.copy(
            fontWeight = FontWeight.W700,
            letterSpacing = 1.3.sp,
            fontSize = 11.sp,
        ),
        color = Upcroc.Dim,
    )
}

@Composable
private fun StepTitle(text: String) {
    AppText(
        text,
        style = UpType.heroBig.copy(fontSize = 25.sp, lineHeight = 28.sp, letterSpacing = (-0.9).sp),
        color = Upcroc.Text,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun StepCopy(text: String, modifier: Modifier = Modifier) {
    AppText(
        text,
        style = UpType.body.copy(lineHeight = 19.sp),
        color = Upcroc.Muted,
        modifier = modifier.padding(top = 10.dp),
    )
}

@Composable
private fun WelcomeStep() {
    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(200.dp)
                .padding(top = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            CrescentMark(Modifier.size(92.dp))
        }
        StepKicker("Welcome")
        StepTitle("New Upwork jobs.")
        StepCopy("Upcroc checks your searches on a schedule and sends a notification when a job matches.")
    }
}

@Composable
private fun HowStep() {
    Column(Modifier.fillMaxWidth()) {
        StepKicker("How it works")
        StepTitle("What it does.")
        StepCopy("It checks, filters, and notifies.")
        Column(
            Modifier.fillMaxWidth().padding(top = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Feature(UiIcon.Radar, "Checks Upwork", "On the interval you choose.")
            Feature(UiIcon.Filter, "Uses your filters", "Budget, proposals, client history, location.")
            Feature(UiIcon.Bell, "Sends a notification", "Tap it to open the job on Upwork.")
        }
    }
}

@Composable
private fun Feature(icon: UiIcon, title: String, body: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Upcroc.Card)
            .padding(13.dp),
        horizontalArrangement = Arrangement.spacedBy(11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF1D1D1D)),
            contentAlignment = Alignment.Center,
        ) {
            IconGlyph(icon, Modifier.size(17.dp), Upcroc.Blue)
        }
        Column(Modifier.weight(1f)) {
            AppText(title, style = UpType.rowTitle, color = Upcroc.Text)
            Spacer(Modifier.height(2.dp))
            AppText(body, style = UpType.small, color = Upcroc.Muted)
        }
    }
}

@Composable
private fun AccountStep(
    auth: app.upcroc.auth.AuthState,
    onSignIn: () -> Unit,
    onSubmitManual: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        StepKicker("Account")
        StepTitle("Connect Upwork.")
        StepCopy("You sign in on Upwork's page.")
        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = 20.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Upcroc.Card)
                .padding(15.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                IconGlyph(UiIcon.Lock, Modifier.size(15.dp), Upcroc.Blue)
                AppText(
                    "ACCESS",
                    style = UpType.caption.copy(fontWeight = FontWeight.W800, letterSpacing = 0.9.sp),
                    color = Upcroc.Muted,
                )
            }
            Spacer(Modifier.height(2.dp))
            AccessRow("Search jobs", allowed = true, last = false)
            AccessRow("Read your profile", allowed = true, last = false)
            AccessRow("Cannot apply or message", allowed = false, last = true)
        }
        Spacer(Modifier.height(16.dp))
        AuthForm(
            state = auth,
            onSignIn = onSignIn,
            onSubmitManual = onSubmitManual,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun AccessRow(label: String, allowed: Boolean, last: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        IconGlyph(
            if (allowed) UiIcon.Check else UiIcon.Close,
            Modifier.size(15.dp),
            if (allowed) Color(0xFF5BC982) else Color(0xFF8A8A8A),
        )
        AppText(
            label,
            style = UpType.body,
            color = if (allowed) Upcroc.Text else Upcroc.Dim,
            modifier = Modifier.weight(1f),
        )
    }
    if (!last) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Upcroc.LineSoft))
    }
}

@Composable
private fun NotificationsStep(
    notifications: NotificationConfig,
    quiet: QuietHours,
    onNotificationsChange: (NotificationConfig) -> Unit,
    onQuietChange: (QuietHours) -> Unit,
    onRequestPermission: () -> Unit,
    onPickSound: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        StepKicker("Alerts")
        StepTitle("Notifications.")
        StepCopy("One notification per matching job.")
        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = 20.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Upcroc.Card)
                .padding(13.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                IconGlyph(UiIcon.Bell, Modifier.size(13.dp), Upcroc.Blue)
                AppText(
                    "UPCROC · JOB ALERT",
                    style = UpType.tiny.copy(fontWeight = FontWeight.W800, letterSpacing = 1.sp),
                    color = Upcroc.Dim,
                )
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Upcroc.Notify)
                    .padding(11.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Avatar("SA", size = 34.dp, textSize = 9f)
                Column(Modifier.weight(1f)) {
                    AppText("Senior AI Automation Engineer", style = UpType.caption.copy(fontWeight = FontWeight.W800), maxLines = 1)
                    AppText("\$40–70/hr · Australia · Expert", style = UpType.small, color = Upcroc.Muted, maxLines = 1)
                    Spacer(Modifier.height(3.dp))
                    AppText("Matched “AI automation” · 6s after posting", style = UpType.tiny.copy(fontSize = 10.sp), color = Upcroc.Dim)
                }
                AppText("now", style = UpType.tiny.copy(fontSize = 10.sp), color = Upcroc.Dim)
            }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = 14.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Upcroc.Card)
                .padding(horizontal = 13.dp),
        ) {
            MiniSwitchRow(
                title = "Notifications",
                subtitle = "Banners and badge",
                checked = notifications.enabled,
                onCheckedChange = { enabled ->
                    if (enabled) onRequestPermission()
                    onNotificationsChange(notifications.copy(enabled = enabled))
                },
            )
            MiniDivider()
            MiniLinkRow(
                title = "Sound",
                subtitle = Format.soundLabel(notifications.soundUri),
                onClick = onPickSound,
            )
            MiniDivider()
            MiniSwitchRow(
                title = "Quiet hours",
                subtitle = "${Format.minuteOfDay(quiet.startMinute)} to ${Format.minuteOfDay(quiet.endMinute)}",
                checked = quiet.enabled,
                onCheckedChange = { onQuietChange(quiet.copy(enabled = it)) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FirstSearchStep(
    groups: List<SearchGroup>,
    onEditKeyword: (Int) -> Unit,
    onAddKeyword: () -> Unit,
    backgroundOk: Boolean,
    onBackgroundChange: (Boolean) -> Unit,
    searches: Int,
    intervalMin: Int,
    intervalMax: Int,
    softCap: Int,
) {
    Column(Modifier.fillMaxWidth()) {
        StepKicker("First search")
        StepTitle("Add a search.")
        StepCopy("Start with keywords. You can change filters later.")
        val keywordGroups = groups.mapIndexedNotNull { index, group ->
            if (index > 0 && group.keywords.isNotBlank()) index to group else null
        }
        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = 18.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Upcroc.Card)
                .padding(14.dp),
        ) {
            AppText("Primary keywords", style = UpType.small, color = Upcroc.Muted)
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(top = 9.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                keywordGroups.forEach { (index, group) ->
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Upcroc.Field)
                            .pressable(onClick = { onEditKeyword(index) })
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                    ) {
                        AppText(group.keywords, style = UpType.caption.copy(fontWeight = FontWeight.W600), color = Upcroc.Text)
                    }
                }
                Box(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Transparent)
                        .pressable(onClick = onAddKeyword)
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                ) {
                    AppText("+ Add keyword", style = UpType.caption.copy(fontWeight = FontWeight.W600), color = Upcroc.Blue)
                }
            }
            AppText(
                "Tap a keyword to edit it. The first search stays smart and needs no keywords.",
                style = UpType.tiny.copy(fontSize = 10.5.sp),
                color = Upcroc.Dim,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .padding(top = 14.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Upcroc.Card)
                .padding(horizontal = 13.dp),
        ) {
            MiniSwitchRow(
                title = "Allow background checks",
                subtitle = "Keeps checks running with the screen off",
                checked = backgroundOk,
                onCheckedChange = onBackgroundChange,
            )
        }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SummaryTile("$searches", "searches", Modifier.weight(1f))
            SummaryTile(Format.intervalRange(intervalMin, intervalMax), "interval", Modifier.weight(1f))
            SummaryTile("$softCap", "soft cap", Modifier.weight(1f))
        }
    }
}

@Composable
private fun SummaryTile(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(11.dp))
            .background(Upcroc.Card)
            .padding(horizontal = 9.dp, vertical = 10.dp),
    ) {
        AppText(value, style = UpType.groupTitle.copy(fontWeight = FontWeight.W800), color = Upcroc.Text)
        AppText(label.uppercase(), style = UpType.tiny.copy(fontSize = 10.sp, letterSpacing = 0.4.sp), color = Upcroc.Dim)
    }
}

@Composable
private fun MiniSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            AppText(title, style = UpType.caption.copy(fontWeight = FontWeight.W700), color = Upcroc.Text)
            Spacer(Modifier.height(2.dp))
            AppText(subtitle, style = UpType.tiny.copy(fontSize = 11.sp), color = Upcroc.Muted)
        }
        app.upcroc.ui.components.UpSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun MiniLinkRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .pressable(onClick)
            .padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            AppText(title, style = UpType.caption.copy(fontWeight = FontWeight.W700), color = Upcroc.Text)
            Spacer(Modifier.height(2.dp))
            AppText(subtitle, style = UpType.tiny.copy(fontSize = 11.sp), color = Upcroc.Muted)
        }
        IconGlyph(UiIcon.Chevron, Modifier.size(15.dp), Upcroc.Dim)
    }
}

@Composable
private fun MiniDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Upcroc.LineSoft))
}
