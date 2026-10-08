@file:OptIn(ExperimentalMaterial3Api::class)

package com.tgsorter.app.ui.screens.home

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tgsorter.app.domain.model.Channel
import com.tgsorter.app.domain.model.ChannelListSummary
import com.tgsorter.app.ui.components.CenteredMessage
import com.tgsorter.app.ui.components.CenteredProgress
import com.tgsorter.app.ui.components.ConfirmDialog
import com.tgsorter.app.ui.components.StatLine
import com.tgsorter.app.ui.components.TextInputDialog
import com.tgsorter.app.ui.theme.AppTheme
import com.tgsorter.app.util.channelsCount
import com.tgsorter.app.util.formatCount
import com.tgsorter.app.util.formatPercent
import com.tgsorter.app.util.toast

/** MIME-типы для системного выбора файла: любые текстовые + «неизвестные» (.txt из некоторых файловых менеджеров). */
private val IMPORT_MIME_TYPES = arrayOf("text/*", "application/octet-stream")

@Composable
fun HomeScreen(
    onContinue: (Long) -> Unit,
    onOpenResults: (Long) -> Unit,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val importState by viewModel.importState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // id списка, который дополняем; null — создаём новый
    var appendTarget by rememberSaveable { mutableStateOf<Long?>(null) }
    var renameTarget by remember { mutableStateOf<ChannelListSummary?>(null) }
    var deleteTarget by remember { mutableStateOf<ChannelListSummary?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importFile(uri, appendTarget)
        appendTarget = null
    }
    val launchImport: (Long?) -> Unit = { target ->
        appendTarget = target
        try {
            picker.launch(IMPORT_MIME_TYPES)
        } catch (e: ActivityNotFoundException) {
            context.toast("Не найдено системное окно выбора файлов")
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { context.toast(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Каналы", fontWeight = FontWeight.SemiBold) },
                actions = {
                    if (state.lists.isNotEmpty()) {
                        IconButton(onClick = { launchImport(null) }) {
                            Icon(Icons.Filled.Add, contentDescription = "Импортировать список")
                        }
                    }
                },
            )
        },
        bottomBar = {
            state.active?.let { active ->
                HomeBottomBar(
                    active = active,
                    onContinue = { onContinue(active.id) },
                    onResults = { onOpenResults(active.id) },
                    onImport = { launchImport(null) },
                )
            }
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                state.loading -> CenteredProgress()
                state.error != null -> CenteredMessage("Ошибка", state.error)
                state.lists.isEmpty() -> EmptyState(onImport = { launchImport(null) })
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    state.active?.let { active ->
                        item(key = "active") {
                            ActiveListCard(active = active, resumeChannel = state.resumeChannel)
                        }
                    }
                    item(key = "header") {
                        Text(
                            text = "Мои списки",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    items(state.lists, key = { it.id }) { summary ->
                        ListRow(
                            summary = summary,
                            isActive = summary.id == state.active?.id,
                            onOpen = { onContinue(summary.id) },
                            onMakeActive = { viewModel.makeActive(summary.id) },
                            onResults = { onOpenResults(summary.id) },
                            onAppend = { launchImport(summary.id) },
                            onRename = { renameTarget = summary },
                            onDelete = { deleteTarget = summary },
                        )
                    }
                }
            }
        }
    }

    ImportDialogs(
        state = importState,
        onDismiss = viewModel::dismissImportResult,
        onStart = { listId ->
            viewModel.dismissImportResult()
            onContinue(listId)
        },
    )

    renameTarget?.let { target ->
        TextInputDialog(
            title = "Переименовать список",
            initialValue = target.name,
            confirmLabel = "Сохранить",
            onConfirm = { name ->
                viewModel.renameList(target.id, name)
                renameTarget = null
            },
            onDismiss = { renameTarget = null },
        )
    }

    deleteTarget?.let { target ->
        ConfirmDialog(
            title = "Удалить список?",
            text = "«${target.name}» (${channelsCount(target.counts.total)}) и все отметки будут удалены без возможности восстановления. " +
                "Если нужна копия — сначала сделайте экспорт CSV в «Результатах».",
            confirmLabel = "Удалить",
            onConfirm = {
                viewModel.deleteList(target.id)
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null },
        )
    }
}

@Composable
private fun EmptyState(onImport: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.AutoMirrored.Filled.List,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Список каналов отсутствует",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Выберите .txt файл: по одному каналу в строке —\n@username, t.me/username или https://t.me/username",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onImport,
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text("Импортировать TXT", fontSize = 18.sp)
        }
    }
}

@Composable
private fun ActiveListCard(active: ChannelListSummary, resumeChannel: Channel?) {
    val counts = active.counts
    val colors = AppTheme.statusColors
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                text = active.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(12.dp))
            StatLine("Всего", counts.total)
            StatLine("Просмотрено", counts.reviewed)
            StatLine("Подходит", counts.positive, dotColor = colors.positive)
            StatLine("Не подходит", counts.negative, dotColor = colors.negative)
            StatLine("Пропущено", counts.skipped, dotColor = colors.skipped)
            StatLine("Осталось", counts.pending, emphasized = true)
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { counts.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Обработано ${counts.progress.formatPercent()}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            if (counts.pending > 0 && resumeChannel != null) {
                Text(
                    "Продолжить с:",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "@${resumeChannel.username}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${resumeChannel.position.formatCount()} / ${counts.total.formatCount()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (counts.pending == 0) {
                Text(
                    "Все каналы обработаны ✓",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.positive,
                )
            }
        }
    }
}

@Composable
private fun HomeBottomBar(
    active: ChannelListSummary,
    onContinue: () -> Unit,
    onResults: () -> Unit,
    onImport: () -> Unit,
) {
    Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(
                onClick = onContinue,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text(
                    if (active.counts.pending > 0) "Продолжить просмотр" else "Открыть итоги списка",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onImport,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                ) { Text("Импортировать") }
                OutlinedButton(
                    onClick = onResults,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                ) { Text("Результаты") }
            }
        }
    }
}

@Composable
private fun ListRow(
    summary: ChannelListSummary,
    isActive: Boolean,
    onOpen: () -> Unit,
    onMakeActive: () -> Unit,
    onResults: () -> Unit,
    onAppend: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val counts = summary.counts
    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
        ),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = summary.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = channelsCount(counts.total),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "${counts.reviewed.formatCount()} просмотрено · осталось ${counts.pending.formatCount()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { counts.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                )
            }
            Spacer(Modifier.width(4.dp))
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Действия со списком")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Продолжить обработку") },
                        onClick = { menuOpen = false; onOpen() },
                    )
                    if (!isActive) {
                        DropdownMenuItem(
                            text = { Text("Сделать активным") },
                            onClick = { menuOpen = false; onMakeActive() },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Результаты и экспорт") },
                        onClick = { menuOpen = false; onResults() },
                    )
                    DropdownMenuItem(
                        text = { Text("Дополнить из TXT") },
                        onClick = { menuOpen = false; onAppend() },
                    )
                    DropdownMenuItem(
                        text = { Text("Переименовать") },
                        onClick = { menuOpen = false; onRename() },
                    )
                    DropdownMenuItem(
                        text = { Text("Удалить", color = MaterialTheme.colorScheme.error) },
                        onClick = { menuOpen = false; onDelete() },
                    )
                }
            }
        }
    }
}

@Composable
private fun ImportDialogs(
    state: ImportState,
    onDismiss: () -> Unit,
    onStart: (Long) -> Unit,
) {
    when (state) {
        ImportState.Idle -> Unit

        ImportState.Running -> AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = { Text("Импорт…") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(28.dp))
                    Spacer(Modifier.width(16.dp))
                    Text("Читаю и разбираю файл")
                }
            },
        )

        is ImportState.Done -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(state.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    state.lines.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                }
            },
            confirmButton = {
                if (state.canStart) {
                    TextButton(onClick = { onStart(state.listId) }) { Text("Начать просмотр") }
                } else {
                    TextButton(onClick = onDismiss) { Text("OK") }
                }
            },
            dismissButton = {
                if (state.canStart) TextButton(onClick = onDismiss) { Text("Закрыть") }
            },
        )

        is ImportState.Failed -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Не удалось импортировать") },
            text = { Text(state.message) },
            confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
        )
    }
}
