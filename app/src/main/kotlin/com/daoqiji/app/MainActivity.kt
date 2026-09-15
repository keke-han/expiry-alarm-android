package com.daoqiji.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

private enum class HomeView { List, Calendar }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ReminderScheduler.scheduleDailyCheck(this)
        NotificationHelper.ensureChannel(this)
        setContent {
            DaoQiJiApp()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DaoQiJiApp() {
    val context = LocalContext.current
    val repository = remember { ExpiryRepository(context.applicationContext) }
    val settingsRepository = remember { SettingsRepository(context.applicationContext) }
    var settings by remember { mutableStateOf(settingsRepository.load()) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var voiceState by remember { mutableStateOf(VoiceInputState.Idle) }
    var items by remember { mutableStateOf(repository.getAll()) }
    var editingItem by remember { mutableStateOf<ExpiryItem?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var showPrivacyConsent by remember { mutableStateOf(!settings.privacyAccepted) }
    var requestVoiceAfterConsent by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var expiryFilter by remember { mutableStateOf(ExpiryFilter.All) }
    var homeView by remember { mutableStateOf(HomeView.List) }
    var notificationAllowed by remember { mutableStateOf(NotificationHelper.canNotify(context)) }
    val parsedInput = remember(input, settings.defaultRemindBeforeDays) {
        ExpiryParser.parse(input, settings.defaultRemindBeforeDays)
    }
    val dark = when (settings.theme) {
        ThemeChoice.System -> isSystemInDarkTheme()
        ThemeChoice.Light -> false
        ThemeChoice.Dark -> true
    }
    val activity = context as ComponentActivity
    val offlineVoiceRecognizer = remember(context.applicationContext) {
        OfflineVoiceRecognizer(
            context = context.applicationContext,
            onText = { input = it },
            onStateChange = { updated ->
                voiceState = updated
                if (updated == VoiceInputState.Listening) {
                    scope.launch { snackbarHostState.showSnackbar("正在听，请说出提醒事项") }
                }
            },
            onError = { message ->
                scope.launch { snackbarHostState.showSnackbar(message) }
            }
        )
    }
    BackHandler(enabled = showSettings) { showSettings = false }
    DisposableEffect(offlineVoiceRecognizer) {
        onDispose { offlineVoiceRecognizer.dispose() }
    }
    DisposableEffect(activity) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationAllowed = NotificationHelper.canNotify(context)
            }
        }
        activity.lifecycle.addObserver(observer)
        onDispose { activity.lifecycle.removeObserver(observer) }
    }
    SideEffect {
        WindowCompat.getInsetsController(activity.window, activity.window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
        @Suppress("DEPRECATION")
        activity.window.statusBarColor = (if (dark) Color(0xFF121214) else Color.White).toArgb()
        @Suppress("DEPRECATION")
        activity.window.navigationBarColor = (if (dark) Color(0xFF121214) else Color.White).toArgb()
    }

    fun refreshItems() {
        items = repository.getAll()
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        notificationAllowed = NotificationHelper.canNotify(context)
    }

    val microphonePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            offlineVoiceRecognizer.toggle()
        } else {
            scope.launch { snackbarHostState.showSnackbar("需要麦克风权限才能进行语音输入") }
        }
    }

    val exportBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            val result = runCatching {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use {
                    it.write(repository.exportBackup())
                } ?: error("无法写入文件")
            }
            scope.launch {
                snackbarHostState.showSnackbar(if (result.isSuccess) "备份已导出" else "导出失败，请重试")
            }
        }
    }

    val importBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val result = runCatching {
                val raw = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: error("无法读取文件")
                repository.importBackup(raw)
            }
            if (result.isSuccess) {
                refreshItems()
                ReminderScheduler.scheduleDailyCheck(context)
            }
            scope.launch {
                snackbarHostState.showSnackbar(
                    result.fold(
                        onSuccess = { "已恢复 $it 条到期事项" },
                        onFailure = { "备份文件无效或已损坏" }
                    )
                )
            }
        }
    }

    fun startOfflineVoiceInput() {
        if (!canRequestSensitivePermission(settings.privacyAccepted)) {
            requestVoiceAfterConsent = true
            showPrivacyConsent = true
            return
        }
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            offlineVoiceRecognizer.toggle()
        } else {
            microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(settings.privacyAccepted) {
        if (settings.privacyAccepted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    MaterialTheme(
        colorScheme = if (dark) darkColorScheme(
            primary = Color(0xFF5AA7FF),
            onPrimary = Color(0xFF081A30),
            background = Color(0xFF121214),
            surface = Color(0xFF121214),
            onSurface = Color(0xFFF2F2F7),
            onSurfaceVariant = Color(0xFFAEAEB8),
            surfaceContainerHigh = Color(0xFF242426),
            outlineVariant = Color(0xFF38383C)
        ) else lightColorScheme(
            primary = Color(0xFF007AFF),
            onPrimary = Color.White,
            background = Color(0xFFF5F5F7),
            surface = Color.White,
            onSurface = Color(0xFF1D1D1F),
            onSurfaceVariant = Color(0xFF6E6E73),
            surfaceContainerHigh = Color(0xFFF5F5F7),
            outlineVariant = Color(0xFFE5E5EA)
        )
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.surface,
            topBar = {
                TopAppBar(
                    title = { Text(if (showSettings) "设置" else "到期闹钟", fontWeight = FontWeight.SemiBold) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                    navigationIcon = {
                        if (showSettings) {
                            IconButton(onClick = { showSettings = false }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                            }
                        }
                    },
                    actions = {
                        if (!showSettings) IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "设置")
                        }
                    }
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { padding ->
            if (showSettings) {
                SettingsContent(
                    settings = settings,
                    notificationAllowed = notificationAllowed,
                    onChange = { updated ->
                        val timeChanged = settings.reminderTime != updated.reminderTime
                        settingsRepository.save(updated)
                        settings = updated
                        if (timeChanged) ReminderScheduler.scheduleDailyCheck(context)
                    },
                    onNotificationSettings = {
                        context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        })
                    },
                    onTestNotification = {
                        val submitted = NotificationHelper.showTestNotification(context)
                        notificationAllowed = NotificationHelper.canNotify(context)
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                if (submitted) "测试通知已提交，请查看通知栏"
                                else "通知未能发送，请检查系统通知权限"
                            )
                        }
                    },
                    onExportBackup = {
                        exportBackupLauncher.launch("到期闹钟备份-${LocalDate.now()}.json")
                    },
                    onImportBackup = {
                        importBackupLauncher.launch(arrayOf("application/json", "text/plain"))
                    },
                    modifier = Modifier.fillMaxSize().padding(padding)
                )
            } else LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Spacer(Modifier.height(2.dp))
                    ExpiryOverviewPanel(
                        overview = expiryOverview(items),
                        onFilter = { expiryFilter = it }
                    )
                }

                item {
                    QuickTemplates(onSelect = { input = templateInput(it) })
                }

                item {
                    InputWorkspace(
                        input = input,
                        parsed = parsedInput,
                        voiceState = voiceState,
                        onInputChange = { input = it },
                        onClear = { input = "" },
                        onVoiceClick = { startOfflineVoiceInput() },
                        onAddClick = {
                            val parsed = ExpiryParser.parse(input, settings.defaultRemindBeforeDays)
                            if (parsed == null) {
                                scope.launch { snackbarHostState.showSnackbar("没识别到有效日期") }
                            } else {
                                repository.add(parsed)
                                ReminderScheduler.scheduleDailyCheck(context)
                                refreshItems()
                                input = ""
                                scope.launch { snackbarHostState.showSnackbar("已添加：${parsed.title}") }
                            }
                        }
                    )
                }

                item {
                    BrowseControls(
                        query = searchQuery,
                        filter = expiryFilter,
                        view = homeView,
                        onQueryChange = { searchQuery = it },
                        onFilterChange = { expiryFilter = it },
                        onViewChange = { homeView = it }
                    )
                }

                val visibleItems = filterExpiryItems(items, searchQuery, expiryFilter)
                if (visibleItems.isEmpty()) {
                    item { EmptyState(hasItems = items.isNotEmpty()) }
                } else if (homeView == HomeView.List) {
                    items(visibleItems, key = { it.id }) { item ->
                        ExpiryItemRow(item = item, onClick = { editingItem = item })
                    }
                } else {
                    groupExpiryItemsByMonth(visibleItems).forEach { (month, monthItems) ->
                        item(key = "month-$month") { CalendarMonthHeader(month) }
                        items(monthItems, key = { "calendar-${it.id}" }) { item ->
                            ExpiryItemRow(item = item, onClick = { editingItem = item })
                        }
                    }
                }
            }
        }
        editingItem?.let { item ->
            EditItemDialog(
                item = item,
                onDismiss = { editingItem = null },
                onSave = {
                    repository.update(it)
                    refreshItems()
                    editingItem = null
                },
                onComplete = {
                    repository.update(item.copy(lifecycle = ItemLifecycle.Completed))
                    refreshItems()
                    editingItem = null
                },
                onDelete = {
                    repository.delete(item.id)
                    refreshItems()
                    editingItem = null
                }
            )
        }
        if (showPrivacyConsent) {
            PrivacyConsentDialog(
                onAccept = {
                    val updated = settings.copy(privacyAccepted = true)
                    settingsRepository.save(updated)
                    settings = updated
                    showPrivacyConsent = false
                    if (requestVoiceAfterConsent) {
                        requestVoiceAfterConsent = false
                        startOfflineVoiceInput()
                    }
                },
                onDecline = {
                    requestVoiceAfterConsent = false
                    showPrivacyConsent = false
                },
            )
        }
    }
}

@Composable
private fun InputWorkspace(
    input: String,
    parsed: ParsedExpiry?,
    voiceState: VoiceInputState,
    onInputChange: (String) -> Unit,
    onClear: () -> Unit,
    onVoiceClick: () -> Unit,
    onAddClick: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "添加到期提醒",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "一句话输入事项、到期时间和提前多久提醒",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = input,
            onValueChange = onInputChange,
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            maxLines = 3,
            placeholder = { Text("例如：护照2029年5月10日到期，提前3个月提醒") },
            trailingIcon = {
                IconButton(
                    onClick = onVoiceClick,
                    enabled = voiceState != VoiceInputState.Loading
                ) {
                    Icon(
                        Icons.Default.Mic,
                        contentDescription = if (voiceState == VoiceInputState.Listening) {
                            "停止语音输入"
                        } else {
                            "语音输入"
                        },
                        tint = if (voiceState == VoiceInputState.Listening) {
                            Color(0xFFFF3B30)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            },
            shape = RoundedCornerShape(8.dp)
        )

        if (parsed != null) {
            ParsedConfirmation(parsed = parsed)
        }

        if (input.isNotBlank()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onClear) {
                    Text("清空输入")
                }
            }
        }

        Button(
            onClick = onAddClick,
            enabled = input.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("确认添加", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ParsedConfirmation(parsed: ParsedExpiry) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val green = if (dark) Color(0xFF62D694) else Color(0xFF1F8A4C)
    Card(
        colors = CardDefaults.cardColors(containerColor = if (dark) Color(0xFF18261F) else Color(0xFFF5FAF7)),
        border = BorderStroke(1.dp, if (dark) Color(0xFF304B3C) else Color(0xFFD8EEE0)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = green
                )
                Spacer(Modifier.width(8.dp))
                Text("已识别", color = green, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(10.dp))
            ParsedRow(label = "事项", value = parsed.title)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            ParsedRow(label = "到期时间", value = parsed.expireDate)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            ParsedRow(
                label = "提前提醒",
                value = (listOf(parsed.remindBeforeDays) + parsed.additionalRemindBeforeDays)
                    .distinct().joinToString(" / ") { "${it}天" }
            )
            if (parsed.repeatRule == RepeatRule.Yearly) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ParsedRow(label = "重复", value = parsed.repeatRule.label)
            }
        }
    }
}

@Composable
private fun ParsedRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Text(value, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun BrowseControls(
    query: String,
    filter: ExpiryFilter,
    view: HomeView,
    onQueryChange: (String) -> Unit,
    onFilterChange: (ExpiryFilter) -> Unit,
    onViewChange: (HomeView) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("到期事项", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            placeholder = { Text("搜索事项") },
            shape = RoundedCornerShape(8.dp)
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ExpiryFilter.entries.forEach { option ->
                FilterChip(
                    selected = filter == option,
                    onClick = { onFilterChange(option) },
                    label = { Text(option.label) },
                    colors = browseChipColors()
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = view == HomeView.List,
                onClick = { onViewChange(HomeView.List) },
                label = { Text("列表") },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.ViewList, contentDescription = null, Modifier.size(18.dp)) },
                colors = browseChipColors()
            )
            FilterChip(
                selected = view == HomeView.Calendar,
                onClick = { onViewChange(HomeView.Calendar) },
                label = { Text("日历") },
                leadingIcon = { Icon(Icons.Default.CalendarMonth, contentDescription = null, Modifier.size(18.dp)) },
                colors = browseChipColors()
            )
        }
    }
}

@Composable
private fun browseChipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
    selectedLabelColor = MaterialTheme.colorScheme.primary,
    selectedLeadingIconColor = MaterialTheme.colorScheme.primary
)

@Composable
private fun CalendarMonthHeader(month: YearMonth) {
    Text(
        text = "${month.year}年${month.monthValue}月",
        modifier = Modifier.padding(top = 8.dp),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun EmptyState(hasItems: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(if (hasItems) "没有匹配的事项" else "还没有到期事项", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            if (hasItems) "试试其他关键词或筛选条件" else "从上方输入第一条到期提醒",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ExpiryItemRow(
    item: ExpiryItem,
    onClick: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape),
                color = item.status().statusColor(),
                content = {}
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.title, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        item.category.label,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = buildString {
                        append(item.expireDate)
                        append(" · ")
                        append(item.allReminderDays().joinToString("/") { "${it}天" })
                        if (item.repeatRule == RepeatRule.Yearly) append(" · 每年")
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Text(
                text = if (item.lifecycle == ItemLifecycle.Completed) "已处理" else compactRemainingText(item),
                color = if (item.lifecycle == ItemLifecycle.Completed) Color(0xFF8E8E93) else item.status().statusColor(),
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "编辑${item.title}",
                tint = Color(0xFFAEAEB2)
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun EditItemDialog(
    item: ExpiryItem,
    onDismiss: () -> Unit,
    onSave: (ExpiryItem) -> Unit,
    onComplete: () -> Unit,
    onDelete: () -> Unit
) {
    var title by remember(item.id) { mutableStateOf(item.title) }
    var date by remember(item.id) { mutableStateOf(item.expireDate) }
    var remindDays by remember(item.id) { mutableStateOf(item.allReminderDays().joinToString(",")) }
    var repeatYearly by remember(item.id) { mutableStateOf(item.repeatRule == RepeatRule.Yearly) }
    var category by remember(item.id) { mutableStateOf(item.category) }
    var error by remember(item.id) { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑提醒") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("事项") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    label = { Text("到期日 YYYY-MM-DD") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = remindDays,
                    onValueChange = { remindDays = it.filter { char -> char.isDigit() || char in ",，、 " } },
                    label = { Text("提醒天数，可填多个") },
                    supportingText = { Text("例如：30,7,1") },
                    singleLine = true
                )
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ItemCategory.entries.forEach { option ->
                        FilterChip(
                            selected = category == option,
                            onClick = { category = option },
                            label = { Text(option.label) },
                            colors = browseChipColors()
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { repeatYearly = !repeatYearly },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = repeatYearly, onCheckedChange = { repeatYearly = it })
                    Text("每年重复")
                }
                if (item.renewalHistory.isNotEmpty()) {
                    Text(
                        "续期记录：${item.renewalHistory.joinToString(" → ")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val days = remindDays.split(Regex("""[,\s，、]+"""))
                        .mapNotNull(String::toIntOrNull).distinct()
                    val validDate = runCatching { LocalDate.parse(date) }.isSuccess
                    error = when {
                        title.isBlank() -> "事项不能为空"
                        !validDate -> "日期格式应为 YYYY-MM-DD"
                        days.isEmpty() -> "提醒天数不能为空"
                        days.any { it !in 0..3650 } -> "提醒天数应为 0–3650"
                        item.lifecycle == ItemLifecycle.Completed && date == item.expireDate -> "续期请填写新的到期日"
                        else -> null
                    }
                    if (error == null) {
                        val updated = if (item.lifecycle == ItemLifecycle.Completed) item.renew(date) else item.copy(expireDate = date)
                        onSave(updated.copy(
                            title = title.trim(),
                            remindBeforeDays = days.first(),
                            additionalRemindBeforeDays = days.drop(1),
                            repeatRule = if (repeatYearly) RepeatRule.Yearly else RepeatRule.None,
                            category = category
                        ))
                    }
                }
            ) {
                Text(if (item.lifecycle == ItemLifecycle.Completed) "续期" else "保存")
            }
        },
        dismissButton = {
            Row {
                if (item.lifecycle == ItemLifecycle.Active) {
                    TextButton(onClick = onComplete) { Text("已处理") }
                }
                TextButton(onClick = onDelete) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = onDismiss) {
                    Text("取消")
                }
            }
        }
    )
}

@Composable
private fun ExpiryOverviewPanel(
    overview: ExpiryOverview,
    onFilter: (ExpiryFilter) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("到期概览", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            OverviewMetric("待处理", overview.active, Modifier.weight(1f)) { onFilter(ExpiryFilter.All) }
            OverviewMetric("30天内", overview.within30Days, Modifier.weight(1f)) { onFilter(ExpiryFilter.Within30Days) }
            OverviewMetric("已过期", overview.expired, Modifier.weight(1f)) { onFilter(ExpiryFilter.Expired) }
            OverviewMetric("已处理", overview.completed, Modifier.weight(1f)) { onFilter(ExpiryFilter.Completed) }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun OverviewMetric(
    label: String,
    value: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier.clickable(onClick = onClick).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun QuickTemplates(onSelect: (ItemCategory) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("快捷添加", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ItemCategory.entries.filterNot { it == ItemCategory.Other }.forEach { category ->
                FilterChip(
                    selected = false,
                    onClick = { onSelect(category) },
                    label = { Text(category.label) }
                )
            }
        }
    }
}

private fun templateInput(category: ItemCategory): String = when (category) {
    ItemCategory.Document -> "护照2030年12月31日到期，提前90天、30天和7天提醒"
    ItemCategory.Card -> "信用卡2029年6月到期，提前30天和7天提醒"
    ItemCategory.Membership -> "会员2027年12月31日到期，提前30天和7天提醒"
    ItemCategory.Food -> "食品2026年10月15日到期，提前7天和1天提醒"
    ItemCategory.Anniversary -> "妈妈生日2027年5月20日，每年提前7天和1天提醒"
    ItemCategory.Other -> ""
}

@Composable
private fun PrivacyConsentDialog(
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("隐私保护说明") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("请阅读《到期闹钟隐私政策》：", fontWeight = FontWeight.SemiBold)
                Text(PRIVACY_POLICY_TEXT)
            }
        },
        confirmButton = { TextButton(onClick = onAccept) { Text("同意并继续") } },
        dismissButton = { TextButton(onClick = onDecline) { Text("不同意") } }
    )
}

private fun compactRemainingText(item: ExpiryItem): String {
    val days = item.daysRemaining()
    return when {
        days < 0 -> "过期${-days}天"
        days == 0L -> "今天到期"
        days < 365 -> "${days}天"
        else -> "${days / 365}年"
    }
}

private fun ExpiryStatus.statusColor(): Color {
    return when (this) {
        ExpiryStatus.Safe -> Color(0xFF34C759)
        ExpiryStatus.Soon -> Color(0xFFFFCC00)
        ExpiryStatus.Urgent -> Color(0xFFFF3B30)
        ExpiryStatus.Expired -> Color(0xFF8E8E93)
    }
}
