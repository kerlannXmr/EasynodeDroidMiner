package org.kerlann.easynode.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.material3.FloatingActionButton
import android.os.Build
import android.net.ConnectivityManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
import androidx.compose.foundation.clickable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.kerlann.easynode.AutoRestartStore
import org.kerlann.easynode.BuildConfig
import org.kerlann.easynode.LocaleHelper
import org.kerlann.easynode.MiningSettings
import org.kerlann.easynode.MiningStore
import org.kerlann.easynode.fastModeAllowed
import org.kerlann.easynode.totalRam
import org.kerlann.easynode.isValidMiningAddress
import org.kerlann.easynode.maxMiningThreads
import org.kerlann.easynode.NodeService
import org.kerlann.easynode.NodeSettings
import org.kerlann.easynode.NodeState
import org.kerlann.easynode.NodeStatus
import org.kerlann.easynode.R
import org.kerlann.easynode.SettingsStore
import org.kerlann.easynode.Storage
import org.kerlann.easynode.TorManager
import org.kerlann.easynode.TorStatus
import org.kerlann.easynode.UpdateChecker
import org.kerlann.easynode.CheckStatus
import org.kerlann.easynode.UpdatePhase
import org.kerlann.easynode.Updater
import org.kerlann.easynode.percent
import java.net.Inet4Address

// ── Squelette ────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun EasyNodeApp() {
    // Onglets glissants : on change d'onglet en glissant le doigt ou en touchant la barre du bas
    val pagerState = rememberPagerState(pageCount = { 5 })
    val scope = rememberCoroutineScope()
    val tabs = listOf(
        stringResource(R.string.tab_node) to rememberVectorPainter(Icons.Filled.Home),
        stringResource(R.string.tab_settings) to rememberVectorPainter(Icons.Filled.Settings),
        stringResource(R.string.tab_mining) to painterResource(R.drawable.ic_tab_mining),
        stringResource(R.string.tab_log) to rememberVectorPainter(Icons.AutoMirrored.Filled.List),
        stringResource(R.string.tab_info) to rememberVectorPainter(Icons.Filled.Info),
    )
    Scaffold(
        topBar = { AppHeader() },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { i, (label, icon) ->
                    NavigationBarItem(
                        selected = pagerState.currentPage == i,
                        onClick = { scope.launch { pagerState.animateScrollToPage(i) } },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    )
                }
            }
        },
    ) { pad ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.padding(pad).fillMaxSize(),
        ) { page ->
            Box(Modifier.fillMaxSize()) {
                when (page) {
                    0 -> NodeScreen()
                    1 -> SettingsScreen()
                    2 -> MiningScreen()
                    3 -> LogScreen()
                    else -> InfoScreen()
                }
            }
        }
    }
}

// ── Bandeau titre (« bandeau carte ») ─────────────────────────────────────────

@Composable
private fun AppHeader() {
    val shape = RoundedCornerShape(16.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(
                    Brush.linearGradient(
                        0f to Color(0xFF2A1A10),
                        0.6f to Color(0xFF1C1C1C),
                        start = Offset.Zero,
                        end = Offset.Infinite,
                    )
                )
                .border(1.dp, Color(0xFF3A2A1F), shape)
                // liseré orange à gauche
                .drawBehind { drawRect(XmrOrange, size = Size(4.dp.toPx(), size.height)) }
                .padding(start = 16.dp, end = 12.dp, top = 24.dp, bottom = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(72.dp).clip(RoundedCornerShape(19.dp)).background(Color(0xFF1E1E1E)),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    buildAnnotatedString {
                        append("Easynode")
                        withStyle(SpanStyle(color = XmrOrange)) { append("Droid") }
                    },
                    // 32 sur un écran courant, réduit sur les petits écrans pour ne jamais couper le nom
                    fontSize = if (LocalConfiguration.current.screenWidthDp >= 400) 32.sp else 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
                // Ligne 2 : badge de version puis sous-titre
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "v${BuildConfig.MONERO_VERSION}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = XmrOrange,
                        maxLines = 1,
                        modifier = Modifier
                            .border(1.dp, XmrOrange, RoundedCornerShape(10.dp))
                            .padding(horizontal = 7.dp, vertical = 2.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.header_subtitle),
                        fontSize = 14.sp,
                        color = Color(0xFFA0A0A0),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

// ── Petits composants ────────────────────────────────────────────────────────

@Composable
private fun Section(title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (title != null) Text(title, color = XmrOrange, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun KV(key: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(key, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun Hint(text: String, color: Color = Muted) {
    Text(text, color = color, fontSize = 13.sp, lineHeight = 17.sp)
}

private const val SCRIPT_NAME = "EasyMonerodGui"
private const val SCRIPT_URL = "https://github.com/kerlannXmr/EasyMonerod/tree/main/gui"

/** Texte « À propos » : le mot EasyMonerodGui devient un lien (souligné) vers le script d'origine. */
@Composable
private fun AboutText(text: String) {
    val start = text.indexOf(SCRIPT_NAME, ignoreCase = true)
    val annotated = buildAnnotatedString {
        if (start < 0) {
            append(text)
        } else {
            append(text.substring(0, start))
            withLink(
                LinkAnnotation.Url(
                    SCRIPT_URL,
                    TextLinkStyles(SpanStyle(color = XmrOrange, textDecoration = TextDecoration.Underline)),
                )
            ) { append(text.substring(start, start + SCRIPT_NAME.length)) }
            append(text.substring(start + SCRIPT_NAME.length))
        }
    }
    Text(annotated, color = Muted, fontSize = 13.sp, lineHeight = 17.sp)
}

@Composable
private fun SwitchRow(title: String, hint: String?, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title)
            if (hint != null) Hint(hint)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
private fun RadioRow(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}

@Composable
private fun ScreenColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

private fun bytes(ctx: Context, b: Long): String = Formatter.formatFileSize(ctx, b)

/** Contrôles avant démarrage ; renvoie un message d'erreur ou null. */
private fun tryStart(ctx: Context): String? {
    val s = SettingsStore.load(ctx)
    if (s.rpcLogin && s.rpcPassword.length < 6) return ctx.getString(R.string.err_password)
    val cm = ctx.getSystemService(ConnectivityManager::class.java)
    if (!s.allowMetered && cm.isActiveNetworkMetered) return ctx.getString(R.string.err_metered)
    NodeService.start(ctx)
    return null
}

/**
 * Voyant Tor de l'accueil : état réel de Tor (pas l'option).
 * Vert = en marche, orange = connexion en cours, rouge = arrêté.
 */
@Composable
private fun TorIndicator() {
    val tor by TorManager.status.collectAsStateWithLifecycle()
    val color = when (tor) {
        TorStatus.ON -> Ok
        TorStatus.STARTING -> XmrOrange
        TorStatus.OFF -> Err
    }
    val label = stringResource(
        when (tor) {
            TorStatus.ON -> R.string.tor_state_on
            TorStatus.STARTING -> R.string.tor_state_starting
            TorStatus.OFF -> R.string.tor_state_off
        }
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painterResource(R.drawable.ic_tor_onion),
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text("Tor", fontSize = 16.sp)
        Spacer(Modifier.width(6.dp))
        Box(Modifier.size(10.dp).background(color, CircleShape))
    }
}

// ── Onglet Nœud ──────────────────────────────────────────────────────────────

@Composable
fun NodeScreen() {
    val ctx = LocalContext.current
    val status by NodeState.status.collectAsStateWithLifecycle()
    val info by NodeState.info.collectAsStateWithLifecycle()
    val error by NodeState.error.collectAsStateWithLifecycle()
    val settings = remember(status) { SettingsStore.load(ctx) }
    val storage = remember(settings) { Storage.selected(ctx, settings) }
    var used by remember { mutableLongStateOf(0L) }
    var free by remember { mutableLongStateOf(0L) }
    LaunchedEffect(storage.dir, status, info?.height) {
        val (u, f) = withContext(Dispatchers.IO) {
            Storage.usedBytes(storage.dir) to Storage.freeBytes(storage.dir)
        }
        used = u
        free = f
    }

    val update by UpdateChecker.available.collectAsStateWithLifecycle()
    val updState by Updater.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { UpdateChecker.check(ctx) }

    val (statusText, statusColor) = when (status) {
        NodeStatus.STOPPED -> stringResource(R.string.status_stopped) to Muted
        NodeStatus.STARTING -> stringResource(R.string.status_starting) to Warn
        NodeStatus.RUNNING -> stringResource(R.string.status_running) to Ok
        NodeStatus.STOPPING -> stringResource(R.string.status_stopping) to Warn
        NodeStatus.ERROR -> stringResource(R.string.status_error) to Err
    }
    val active = status == NodeStatus.RUNNING || status == NodeStatus.STARTING

    ScreenColumn {
        update?.let { u ->
            Section(stringResource(R.string.update_available)) {
                Text(u.name, fontWeight = FontWeight.SemiBold)
                when (updState.phase) {
                    UpdatePhase.DOWNLOADING -> {
                        Text(stringResource(R.string.upd_downloading, percent(updState.progress)))
                        LinearProgressIndicator(progress = { updState.progress }, modifier = Modifier.fillMaxWidth(), color = XmrOrange)
                    }
                    UpdatePhase.STOPPING_NODE -> {
                        Text(stringResource(R.string.upd_stopping))
                        LinearProgressIndicator(Modifier.fillMaxWidth(), color = XmrOrange)
                    }
                    UpdatePhase.INSTALLING -> {
                        Text(stringResource(R.string.upd_installing))
                        LinearProgressIndicator(Modifier.fillMaxWidth(), color = XmrOrange)
                    }
                    UpdatePhase.WAITING_USER -> Hint(updState.message ?: "", Warn)
                    UpdatePhase.ERROR, UpdatePhase.IDLE -> {
                        Hint(stringResource(R.string.update_hint))
                        updState.message?.let { Hint(it, Err) }
                    }
                }
                if (!Updater.busy) {
                    Button(
                        onClick = { Updater.start(ctx, u, interactive = true) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.update_install)) }
                }
            }
        }

        Section {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).background(statusColor, CircleShape))
                Spacer(Modifier.width(10.dp))
                Text(statusText, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                TorIndicator()
            }
            Button(
                onClick = {
                    if (active) NodeService.stop(ctx)
                    else tryStart(ctx)?.let { NodeState.setError(it) }
                },
                enabled = status != NodeStatus.STOPPING,
                colors = if (active) ButtonDefaults.buttonColors(containerColor = Err, contentColor = Color.White)
                else ButtonDefaults.buttonColors(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text(stringResource(if (active) R.string.btn_stop else R.string.btn_start), fontSize = 16.sp)
            }
            error?.let { Hint(it, Err) }
            // Redémarrage automatique, juste sous le bouton. Modifiable à tout moment :
            // l'option est lue au moment où le nœud s'arrête.
            var autoRestart by remember { mutableStateOf(AutoRestartStore.get(ctx)) }
            SwitchRow(stringResource(R.string.auto_restart), stringResource(R.string.auto_restart_hint), autoRestart, true) {
                autoRestart = it
                AutoRestartStore.set(ctx, it)
            }
        }

        if (status == NodeStatus.RUNNING) {
            Section {
                val i = info
                if (i == null) {
                    Text(stringResource(R.string.sync_waiting))
                    LinearProgressIndicator(Modifier.fillMaxWidth(), color = XmrOrange)
                } else {
                    Text(
                        if (i.synchronized) stringResource(R.string.synced)
                        else stringResource(R.string.sync_progress, percent(i.progress)),
                        color = if (i.synchronized) Ok else MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                    )
                    LinearProgressIndicator(
                        progress = { if (i.synchronized) 1f else i.progress },
                        modifier = Modifier.fillMaxWidth(),
                        color = if (i.synchronized) Ok else XmrOrange,
                    )
                    KV(stringResource(R.string.label_height), "${i.height} / ${maxOf(i.targetHeight, i.height)}")
                    KV(stringResource(R.string.label_peers), "${i.incoming} / ${i.outgoing}")
                    if (i.databaseSize > 0) KV(stringResource(R.string.label_db), bytes(ctx, i.databaseSize))
                    if (i.version.isNotBlank()) KV(stringResource(R.string.label_version), i.version)
                }
            }
        }

        Section(stringResource(R.string.label_storage)) {
            KV(
                stringResource(R.string.label_type),
                stringResource(R.string.type_pruned),
            )
            KV(storage.label, bytes(ctx, used))
            Hint(stringResource(R.string.free_space, bytes(ctx, free)))
            Hint(stringResource(R.string.space_needed, bytes(ctx, settings.requiredBytes)))
            if (free + used < settings.requiredBytes) Hint(stringResource(R.string.warn_space), Warn)
        }

        Hint(stringResource(R.string.warn_data))

        GithubLinks()
    }
}

private const val REPO_URL = "https://github.com/kerlannXmr/EasynodeDroidMiner"
private const val DOWNLOAD_URL = "https://github.com/kerlannXmr/EasynodeDroidMiner/releases/latest"

/** Petits liens discrets en bas de l'écran d'accueil. */
@Composable
private fun GithubLinks() {
    val ctx = LocalContext.current
    fun open() = ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(REPO_URL)))
    Column(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { open() }) {
                Text(stringResource(R.string.link_github), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("·", color = Muted)
            TextButton(onClick = { open() }) {
                Text(stringResource(R.string.link_star), fontSize = 13.sp, color = XmrOrange)
            }
        }
        Text(
            stringResource(R.string.link_star_hint),
            fontSize = 11.sp,
            color = Muted,
        )
        Spacer(Modifier.height(10.dp))
        val chooserTitle = stringResource(R.string.share_chooser)
        val shareText = stringResource(R.string.share_text, DOWNLOAD_URL)
        OutlinedButton(
            onClick = {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, shareText)
                }
                ctx.startActivity(Intent.createChooser(send, chooserTitle))
            },
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            modifier = Modifier.padding(bottom = 6.dp),
        ) {
            Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp), tint = XmrOrange)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.link_share), fontSize = 13.sp)
        }
        CheckUpdateButton()
    }
}

/** Bouton « Mise à jour » : vérifie tout de suite ; installe si une version existe, sinon « Pas de mise à jour ». */
@Composable
private fun CheckUpdateButton() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    OutlinedButton(
        onClick = {
            if (checking || Updater.busy) return@OutlinedButton
            checking = true
            scope.launch {
                val r = UpdateChecker.checkNow()
                checking = false
                when (r.status) {
                    CheckStatus.AVAILABLE -> {
                        val info = r.info ?: return@launch
                        Toast.makeText(ctx, ctx.getString(R.string.check_found, info.name), Toast.LENGTH_LONG).show()
                        Updater.start(ctx, info, interactive = true)
                    }
                    CheckStatus.UP_TO_DATE ->
                        Toast.makeText(ctx, ctx.getString(R.string.check_none, BuildConfig.VERSION_NAME), Toast.LENGTH_LONG).show()
                    CheckStatus.ERROR ->
                        Toast.makeText(ctx, R.string.check_error, Toast.LENGTH_LONG).show()
                }
            }
        },
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        modifier = Modifier.padding(bottom = 6.dp),
    ) {
        if (checking) {
            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = Ok)
        } else {
            Text("⟳", color = Ok, fontSize = 15.sp)
        }
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.check_button), color = Ok, fontSize = 13.sp)
    }
}

// ── Onglet Réglages ──────────────────────────────────────────────────────────

@SuppressLint("BatteryLife")
/** Une ligne du cadre des versions : nom à gauche, numéro en orange à droite. */
@Composable
private fun VersionRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 14.sp, color = Color(0xFFC9C9C9))
        Text(value, fontSize = 15.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = XmrOrange)
    }
}

@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val status by NodeState.status.collectAsStateWithLifecycle()
    val editable = status == NodeStatus.STOPPED || status == NodeStatus.ERROR
    var s by remember { mutableStateOf(SettingsStore.load(ctx)) }
    fun update(n: NodeSettings) { s = n; SettingsStore.save(ctx, n) }
    val options = remember { Storage.options(ctx) }
    var confirmDelete by remember { mutableStateOf(false) }

    ScreenColumn {
        Section(stringResource(R.string.set_language)) {
            val current = remember { LocaleHelper.get(ctx) }
            listOf(
                LocaleHelper.SYSTEM to stringResource(R.string.lang_system),
                "fr" to "Français",
                "en" to "English",
            ).forEach { (code, label) ->
                RadioRow(label, current == code, true) {
                    if (code != current) {
                        LocaleHelper.set(ctx, code)
                        (ctx as? Activity)?.recreate()
                    }
                }
            }
        }

        if (!editable) Hint(stringResource(R.string.settings_running_hint), Warn)

        Section(stringResource(R.string.set_storage)) {
            options.forEachIndexed { i, o ->
                val sel = s.storageIndex == i || (s.storageIndex >= options.size && i == 0)
                RadioRow("${o.label} — ${bytes(ctx, Storage.freeBytes(o.dir))}", sel, editable) {
                    update(s.copy(storageIndex = i))
                }
            }
            Hint(stringResource(R.string.storage_hint))
        }

        Section("RPC") {
            SwitchRow(stringResource(R.string.set_rpc_lan), stringResource(R.string.set_rpc_lan_hint), s.rpcLan, editable) {
                update(s.copy(rpcLan = it))
            }
            HorizontalDivider()
            SwitchRow(stringResource(R.string.set_rpc_login), null, s.rpcLogin, editable) { update(s.copy(rpcLogin = it)) }
            if (s.rpcLogin) {
                OutlinedTextField(
                    value = s.rpcUser,
                    onValueChange = { v -> update(s.copy(rpcUser = v.filter { it.isLetterOrDigit() || it == '_' || it == '-' })) },
                    label = { Text(stringResource(R.string.set_rpc_user)) },
                    singleLine = true,
                    enabled = editable,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = s.rpcPassword,
                    onValueChange = { v -> update(s.copy(rpcPassword = v.filter { it != ':' && !it.isWhitespace() })) },
                    label = { Text(stringResource(R.string.set_rpc_pass)) },
                    singleLine = true,
                    enabled = editable,
                    isError = s.rpcPassword.length < 6,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (s.rpcLan && !s.rpcLogin) Hint(stringResource(R.string.warn_lan_no_login), Warn)
        }

        Section(stringResource(R.string.label_peers).substringBefore(" (")) {
            SwitchRow(stringResource(R.string.set_banlist), stringResource(R.string.set_banlist_hint), s.useBanList, editable) {
                update(s.copy(useBanList = it))
            }
            HorizontalDivider()
            SwitchRow(stringResource(R.string.set_tor), stringResource(R.string.set_tor_hint), s.torTx, editable) {
                update(s.copy(torTx = it))
            }
            if (s.torTx) OnionInfo()
        }

        Section {
            SwitchRow(stringResource(R.string.set_metered), stringResource(R.string.set_metered_hint), s.allowMetered, true) {
                update(s.copy(allowMetered = it))
            }
            HorizontalDivider()
            SwitchRow(stringResource(R.string.set_updates), stringResource(R.string.set_updates_hint), s.checkUpdates, true) {
                update(s.copy(checkUpdates = it))
            }
            if (s.checkUpdates) {
                SwitchRow(stringResource(R.string.set_auto_update), stringResource(R.string.set_auto_update_hint), s.autoInstall, true) {
                    update(s.copy(autoInstall = it))
                }
            }
            val pm = ctx.getSystemService(PowerManager::class.java)
            if (!pm.isIgnoringBatteryOptimizations(ctx.packageName)) {
                HorizontalDivider()
                Hint(stringResource(R.string.set_battery_hint))
                OutlinedButton(
                    onClick = {
                        ctx.startActivity(
                            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}"))
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.set_battery)) }
            }
        }

        HorizontalDivider()
        Spacer(Modifier.height(12.dp))
        
        // Signaler un bug (violet), bien séparé du bouton rouge « Supprimer »
        val clipboard = LocalClipboardManager.current
        OutlinedButton(
            onClick = {
                openBugReport(ctx) {
                    clipboard.setText(AnnotatedString(BUG_EMAIL))
                    Toast.makeText(ctx, ctx.getString(R.string.bug_no_mail, BUG_EMAIL), Toast.LENGTH_LONG).show()
                }
            },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = BugPurple),
            border = BorderStroke(1.dp, BugPurple),
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.bug_button)) }
        

        Spacer(Modifier.height(12.dp))
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))

        OutlinedButton(
            onClick = { confirmDelete = true },
            enabled = editable,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Err),
            border = BorderStroke(1.dp, if (editable) Err else Err.copy(alpha = 0.38f)),
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.set_delete)) }

        // Versions dans un cadre gris anthracite, une par ligne
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF262626))
                .border(1.dp, Color(0xFF444444), RoundedCornerShape(14.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            VersionRow("Monero", "v${BuildConfig.MONERO_VERSION}")
            VersionRow("EasynodeDroid Miner", BuildConfig.VERSION_NAME)
        }
    }

    if (confirmDelete) {
        val dir = Storage.selected(ctx, s).dir
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_confirm_title)) },
            text = { Text(stringResource(R.string.delete_confirm_text, dir.path)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        withContext(Dispatchers.IO) { dir.deleteRecursively() }
                        Toast.makeText(ctx, R.string.deleted, Toast.LENGTH_SHORT).show()
                    }
                }) { Text(stringResource(R.string.btn_delete), color = Err) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.btn_cancel)) }
            },
        )
    }
}

/** Adresse .onion + port, avec bouton copier (Réglages et onglet Wallet). */
@Composable
private fun OnionInfo() {
    val ctx = LocalContext.current
    val status by NodeState.status.collectAsStateWithLifecycle()
    val onion = remember(status) { TorManager.savedOnion(ctx) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (onion == null) {
            Hint(stringResource(R.string.tor_pending), Warn)
        } else {
            Text(stringResource(R.string.tor_address), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            CopyRow(onion, small = true)
            Text(stringResource(R.string.tor_port, TorManager.ONION_PORT), fontFamily = FontFamily.Monospace, fontSize = 14.sp)
            Hint(stringResource(R.string.tor_wallet_hint))
        }
    }
}

// ── Onglet Minage (mineur intégré à monerod) ─────────────────────────────────

@Composable
fun MiningScreen() {
    val ctx = LocalContext.current
    val status by NodeState.status.collectAsStateWithLifecycle()
    val mining by NodeState.mining.collectAsStateWithLifecycle()
    var m by remember { mutableStateOf(MiningStore.load(ctx)) }
    fun update(n: MiningSettings) {
        m = n
        MiningStore.save(ctx, n)
        NodeState.wakeMining()
    }
    val maxThreads = remember { maxMiningThreads() }
    val blocks = remember(mining) { MiningStore.blocksFound(ctx) }
    val fastAllowed = remember { fastModeAllowed(ctx) }
    val fastStarted by NodeState.fastModeStarted.collectAsStateWithLifecycle()

    val mi = mining
    val (stateText, stateColor) = when {
        !m.enabled -> stringResource(R.string.mine_off) to Muted
        status != NodeStatus.RUNNING -> stringResource(R.string.mine_wait_node) to Warn
        mi == null -> stringResource(R.string.mine_preparing) to Warn
        mi.pause != null -> stringResource(mi.pause.text) to Warn
        mi.active -> stringResource(R.string.mine_running) to Ok
        else -> stringResource(R.string.mine_starting) to Warn
    }

    ScreenColumn {
        Section {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.mine_title), color = XmrOrange, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                    Hint(stringResource(R.string.mine_subtitle))
                }
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = m.enabled,
                    onCheckedChange = { update(m.copy(enabled = it)) },
                    // Une adresse valide est nécessaire pour activer (on peut toujours désactiver)
                    enabled = m.enabled || m.addressValid,
                )
            }
            // Mode rapide juste sous l'interrupteur « Minage solo »
            if (fastAllowed) {
                SwitchRow(stringResource(R.string.mine_fast), stringResource(R.string.mine_fast_hint), m.fastMode, true) {
                    update(m.copy(fastMode = it))
                }
                // Le mode est choisi au démarrage de monerod : il faut redémarrer le nœud pour l'appliquer
                if (fastStarted != null && fastStarted != m.fastMode) Hint(stringResource(R.string.mine_fast_restart), Warn)
            } else {
                Column {
                    Text(stringResource(R.string.mine_fast))
                    Hint(stringResource(R.string.mine_fast_unavailable, bytes(ctx, totalRam(ctx))))
                }
            }
            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(stateColor, CircleShape))
                Spacer(Modifier.width(8.dp))
                Text(stateText, fontWeight = FontWeight.SemiBold)
            }
            if (m.enabled && mi != null && mi.active) {
                KV(stringResource(R.string.mine_speed), "${mi.speed} H/s")
                KV(stringResource(R.string.mine_threads_label), "${mi.threads} / $maxThreads")
                KV(stringResource(R.string.mine_blocks), blocks.toString())
                if (mi.speed > 0 && mi.difficulty > 0) {
                    // Temps moyen pour trouver un bloc = difficulté du réseau / vitesse
                    val days = mi.difficulty.toDouble() / mi.speed / 86_400
                    KV(
                        stringResource(R.string.mine_chance),
                        if (days >= 730) stringResource(R.string.mine_chance_years, (days / 365.25).toLong().toInt())
                        else stringResource(R.string.mine_chance_days, days.toLong().coerceAtLeast(1).toInt()),
                    )
                }
            } else if (blocks > 0) {
                KV(stringResource(R.string.mine_blocks), blocks.toString())
            }
        }

        Section(stringResource(R.string.mine_settings)) {
            val badAddress = m.address.isNotBlank() && !m.addressValid
            OutlinedTextField(
                value = m.address,
                onValueChange = { v ->
                    val a = v.filter { !it.isWhitespace() }
                    // Adresse effacée ou invalide : on coupe le minage
                    update(m.copy(address = a, enabled = m.enabled && isValidMiningAddress(a)))
                },
                label = { Text(stringResource(R.string.mine_address)) },
                singleLine = true,
                isError = badAddress,
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.fillMaxWidth(),
            )
            if (badAddress) Hint(stringResource(R.string.mine_address_bad), Err)
            Hint(stringResource(R.string.mine_address_hint))

            if (maxThreads > 1) {
                HorizontalDivider()
                var threads by remember { mutableIntStateOf(m.threads.coerceIn(1, maxThreads)) }
                Text(stringResource(R.string.mine_threads, threads, maxThreads))
                Slider(
                    value = threads.toFloat(),
                    onValueChange = { threads = it.toInt().coerceIn(1, maxThreads) },
                    // On n'enregistre qu'au lâcher du doigt (évite de relancer le mineur en boucle)
                    onValueChangeFinished = { update(m.copy(threads = threads)) },
                    valueRange = 1f..maxThreads.toFloat(),
                    steps = (maxThreads - 2).coerceAtLeast(0),
                )
                Hint(stringResource(R.string.mine_threads_hint))
            }

            HorizontalDivider()
            SwitchRow(stringResource(R.string.mine_only_charging), stringResource(R.string.mine_only_charging_hint), m.onlyCharging, true) {
                update(m.copy(onlyCharging = it))
            }
            SwitchRow(stringResource(R.string.mine_pause_hot_set), stringResource(R.string.mine_pause_hot_set_hint), m.pauseHot, true) {
                update(m.copy(pauseHot = it))
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            border = BorderStroke(1.dp, Warn),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.mine_warn_title), color = Warn, fontWeight = FontWeight.SemiBold)
                Hint(stringResource(R.string.mine_warn))
            }
        }
    }
}

// ── Onglet Journal ───────────────────────────────────────────────────────────

@Composable
fun LogScreen() {
    val ctx = LocalContext.current
    val lines by NodeState.log.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    LaunchedEffect(lines.size, lines.lastOrNull()) {
        if (lines.isNotEmpty()) listState.scrollToItem(lines.lastIndex)
    }
    val chooserTitle = stringResource(R.string.log_share_title)
    if (lines.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Hint(stringResource(R.string.log_empty)) }
    } else Box(Modifier.fillMaxSize()) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
        // place en bas pour que le bouton ne cache pas la dernière ligne
        contentPadding = PaddingValues(bottom = 88.dp),
    ) {
        itemsIndexed(lines) { _, line ->
            Text(
                line,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                color = when {
                    line.startsWith("!!") || line.contains(" E ") || line.contains("ERROR") -> Err
                    line.contains(" W ") || line.contains("WARN") -> Warn
                    line.contains("Synced") || line.contains("SYNCHRONIZED") -> Ok
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
    FloatingActionButton(
        onClick = {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "EasynodeDroid Miner log")
                putExtra(Intent.EXTRA_TEXT, shareableLog(ctx, lines))
            }
            ctx.startActivity(Intent.createChooser(send, chooserTitle))
        },
        containerColor = XmrOrange,
        contentColor = Color.White,
        modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
    ) {
        Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.log_share))
    }
    }
}

/**
 * Journal à partager : en-tête de diagnostic + lignes, avec l'adresse .onion
 * et les adresses IP masquées (le journal affiché dans l'app n'est pas modifié).
 */
private fun shareableLog(ctx: Context, lines: List<String>): String =
    diagnosticHeader(ctx) + lines.joinToString("\n") { maskPrivate(it) }

/** Résumé de diagnostic : versions, téléphone et réglages principaux (aucune donnée privée). */
private fun diagnosticHeader(ctx: Context): String {
    val s = SettingsStore.load(ctx)
    fun onOff(b: Boolean) = if (b) "on" else "off"
    return buildString {
        appendLine("=== EasynodeDroid Miner ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) ===")
        appendLine("monerod: ${BuildConfig.MONERO_VERSION}")
        appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("Tor: ${onOff(s.torTx)} · RPC LAN: ${onOff(s.rpcLan)} · RPC auth: ${onOff(s.rpcLogin)} · storage: ${if (s.storageIndex == 0) "internal" else "SD #${s.storageIndex}"} · ban list: ${onOff(s.useBanList)}")
        appendLine("=".repeat(40))
    }
}

private const val BUG_EMAIL = "easynodedroid@kerlann.org"
private val BugPurple = Color(0xFFB388FF)

/**
 * Ouvre l'application e-mail avec un rapport de bug prérempli :
 * objet, questions à compléter, diagnostic et 100 dernières lignes du journal (onion et IP masquées).
 * Sans application e-mail, l'adresse est copiée.
 */
private fun openBugReport(ctx: Context, onNoMailApp: () -> Unit) {
    val lines = NodeState.log.value.takeLast(100)
    val body = buildString {
        appendLine(ctx.getString(R.string.bug_body_intro))
        appendLine()
        appendLine()
        append(diagnosticHeader(ctx))
        appendLine(ctx.getString(R.string.bug_body_log))
        append(lines.joinToString("\n") { maskPrivate(it) })
    }
    val intent = Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.parse("mailto:")
        putExtra(Intent.EXTRA_EMAIL, arrayOf(BUG_EMAIL))
        putExtra(Intent.EXTRA_SUBJECT, "[EasynodeDroid Miner] Bug report – ${BuildConfig.VERSION_NAME}")
        putExtra(Intent.EXTRA_TEXT, body)
    }
    try {
        ctx.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        onNoMailApp()
    }
}

private val ONION_RE = Regex("""\b[a-z2-7]{16,56}\.onion\b""")
private val IPV4_RE = Regex("""\b(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})\b""")

/** Masque l'adresse .onion et les adresses IP (garde 127.x et les numéros de version 0.x). */
private fun maskPrivate(line: String): String =
    IPV4_RE.replace(ONION_RE.replace(line, "[onion]")) { m ->
        val a = m.groupValues[1]
        if (a == "0" || a == "127") m.value else "$a.${m.groupValues[2]}.x.x"
    }

// ── Onglet Wallet ────────────────────────────────────────────────────────────

private fun localIp(ctx: Context): String? {
    val cm = ctx.getSystemService(ConnectivityManager::class.java)
    val lp = cm.getLinkProperties(cm.activeNetwork) ?: return null
    return lp.linkAddresses.map { it.address }
        .firstOrNull { it is Inet4Address && !it.isLoopbackAddress }?.hostAddress
}

@Composable
private fun CopyRow(value: String, small: Boolean = false) {
    val clipboard = LocalClipboardManager.current
    val ctx = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(value, fontFamily = FontFamily.Monospace, fontSize = if (small) 13.sp else 18.sp, color = XmrOrange, modifier = Modifier.weight(1f))
        TextButton(onClick = {
            clipboard.setText(AnnotatedString(value))
            Toast.makeText(ctx, R.string.copied, Toast.LENGTH_SHORT).show()
        }) { Text("⧉") }
    }
}

/**
 * Mot de passe masqué (••••••••) : un toucher l'affiche, un second le masque.
 * Il se re-masque tout seul quand on change d'onglet ou qu'on quitte l'app.
 * Le bouton copier fonctionne même masqué.
 */
@Composable
private fun PasswordRow(password: String) {
    var shown by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { shown = false }
    val clipboard = LocalClipboardManager.current
    val ctx = LocalContext.current
    val masked = "•".repeat(8)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.info_pass, if (shown) password else masked),
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f).clickable { shown = !shown },
        )
        TextButton(onClick = { shown = !shown }) {
            Text(stringResource(if (shown) R.string.pass_hide else R.string.pass_show), fontSize = 13.sp)
        }
        TextButton(onClick = {
            clipboard.setText(AnnotatedString(password))
            Toast.makeText(ctx, R.string.copied, Toast.LENGTH_SHORT).show()
        }) { Text("⧉") }
    }
}

/** Sous-adresse XMR dédiée aux dons (vérifiée : réseau principal, somme de contrôle OK). */
private const val DONATION_ADDRESS =
    "88YbpCfGuA5Ez2qVNWEYHtc6qzFS4uLRaQ5iydCGoR2p3XVQfVkmmPDHjyw5tpDUnJCQL7QjdtxAS8nzC4x6VgH8Vs3Kjwk"

/**
 * Dons : ouvre le wallet Monero du téléphone (lien monero:) avec l'adresse remplie ;
 * si aucun wallet n'est installé, l'adresse est copiée. Boutons copier et partager en plus.
 */
@Composable
private fun DonationBox() {
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    fun copy(toastRes: Int) {
        clipboard.setText(AnnotatedString(DONATION_ADDRESS))
        Toast.makeText(ctx, toastRes, Toast.LENGTH_LONG).show()
    }
    val shareTitle = stringResource(R.string.donate_share_title)
    val shareText = stringResource(R.string.donate_share_text, DONATION_ADDRESS)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.donate_address_label), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        // Adresse abrégée pour vérifier début et fin dans le wallet ; un toucher copie l'adresse complète
        Row(
            Modifier.fillMaxWidth().clickable { copy(R.string.copied) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                DONATION_ADDRESS.take(8) + "…" + DONATION_ADDRESS.takeLast(8),
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                color = XmrOrange,
                modifier = Modifier.weight(1f),
            )
            Text("⧉", color = XmrOrange, fontSize = 16.sp)
        }
        Button(
            onClick = {
                val uri = Uri.parse("monero:$DONATION_ADDRESS?tx_description=EasynodeDroid")
                try {
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, uri))
                } catch (e: ActivityNotFoundException) {
                    copy(R.string.donate_no_wallet)
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = XmrOrange, contentColor = Color.White),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("♥ " + stringResource(R.string.donate_button)) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { copy(R.string.copied) }, modifier = Modifier.weight(1f)) {
                Text("⧉ " + stringResource(R.string.donate_copy), fontSize = 13.sp)
            }
            OutlinedButton(
                onClick = {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, shareText)
                    }
                    ctx.startActivity(Intent.createChooser(send, shareTitle))
                },
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.donate_share), fontSize = 13.sp)
            }
        }
        Hint(stringResource(R.string.donate_hint))
    }
}

@Composable
fun InfoScreen() {
    val ctx = LocalContext.current
    val s = remember { SettingsStore.load(ctx) }
    val ip = remember { localIp(ctx) }

    ScreenColumn {
        Section(stringResource(R.string.info_title)) {
            Text(stringResource(R.string.info_local), fontWeight = FontWeight.SemiBold)
            CopyRow("127.0.0.1:${NodeService.RPC_PORT}")
            HorizontalDivider()
            Text(stringResource(R.string.info_lan), fontWeight = FontWeight.SemiBold)
            when {
                !s.rpcLan -> Hint(stringResource(R.string.info_lan_off), Warn)
                ip == null -> Hint(stringResource(R.string.info_no_ip), Warn)
                else -> CopyRow("$ip:${NodeService.RESTRICTED_PORT}")
            }
            HorizontalDivider()
            val cred = s.rpcCredentials
            if (cred != null) {
                Text(stringResource(R.string.info_user, cred.first), fontFamily = FontFamily.Monospace)
                PasswordRow(cred.second)
            } else {
                Hint(stringResource(R.string.info_noauth))
            }
        }
        if (s.torTx) {
            Section(stringResource(R.string.info_tor_title)) { OnionInfo() }
        }
        Section(stringResource(R.string.info_steps_title)) { Text(stringResource(R.string.info_steps), lineHeight = 22.sp) }
        Section(stringResource(R.string.info_p2p_title)) { Hint(stringResource(R.string.info_p2p)) }
        Section(stringResource(R.string.info_about_title)) {
            AboutText(stringResource(R.string.info_about))
            DonationBox()
        }
    }
}
