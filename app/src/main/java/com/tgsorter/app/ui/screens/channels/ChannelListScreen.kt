@file:OptIn(ExperimentalMaterial3Api::class)

package com.tgsorter.app.ui.screens.channels

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tgsorter.app.R
import com.tgsorter.app.domain.model.Channel
import com.tgsorter.app.domain.model.ChannelStatus
import com.tgsorter.app.ui.components.CenteredMessage
import com.tgsorter.app.ui.components.CenteredProgress
import com.tgsorter.app.ui.components.listTitle
import com.tgsorter.app.ui.components.moveLabel
import com.tgsorter.app.ui.components.rememberExportSaver
import com.tgsorter.app.ui.components.title
import com.tgsorter.app.util.copyToClipboard
import com.tgsorter.app.util.formatCount
import com.tgsorter.app.util.openTelegramChannel
import com.tgsorter.app.util.shareExport
import com.tgsorter.app.util.toast
import kotlinx.coroutines.launch

/** Список каналов одного статуса (+, −, пропущенные, непросмотренные). */
@Composable
fun ChannelListScreen(
    onBack: () -> Unit,
    onShowInReview: (Long, Int) -> Unit,
    viewModel: ChannelListViewModel = viewModel(factory = ChannelListViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var query by rememberSaveable { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }
    val status = viewModel.status

    LaunchedEffect(query) { viewModel.setQuery(query) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ChannelListEvent.Share -> context.shareExport(event.export)
                is ChannelListEvent.Message -> context.toast(event.text)
                is ChannelListEvent.Moved -> launch {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    val result = snackbarHostState.showSnackbar(
                        message = "@${event.username} → ${event.newStatus.title()}",
                        actionLabel = "Отменить",
                        duration = SnackbarDuration.Short,
                    )
                    if (result == SnackbarResult.ActionPerformed) viewModel.undo()
                }
            }
        }
    }

    val save = rememberExportSaver { _, uri -> viewModel.saveTo(uri) }
    val copyAll = {
        if (state.all.isEmpty()) {
            context.toast("Список пуст")
        } else {
            context.copyToClipboard(viewModel.allUsernamesText(), "Скопировано: ${state.all.size.formatCount()}")
        }
    }
    val shareAll = {
        if (state.all.isEmpty()) context.toast("Список пуст") else viewModel.share()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "${status.listTitle()} — ${state.all.size.formatCount()}",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        if (state.listName.isNotEmpty()) {
                            Text(
                                state.listName,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Меню")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Копировать весь список") },
                                onClick = { menuOpen = false; copyAll() },
                            )
                            DropdownMenuItem(
                                text = { Text("Сохранить в файл (${viewModel.exportKind.fileName})") },
                                onClick = {
                                    menuOpen = false
                                    if (state.all.isEmpty()) context.toast("Список пуст") else save(viewModel.exportKind)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Поделиться файлом") },
                                onClick = { menuOpen = false; shareAll() },
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (state.all.isNotEmpty()) {
                Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Button(
                            onClick = copyAll,
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Icon(painterResource(R.drawable.ic_copy), contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Копировать всё")
                        }
                        OutlinedButton(
                            onClick = shareAll,
                            modifier = Modifier.height(52.dp),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Поделиться")
                        }
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                state.loading -> CenteredProgress()
                state.missing -> CenteredMessage("Список не найден")
                state.all.isEmpty() -> CenteredMessage(
                    "Здесь пока пусто",
                    when (status) {
                        ChannelStatus.POSITIVE -> "Отмеченные «+» каналы появятся здесь"
                        ChannelStatus.NEGATIVE -> "Отмеченные «−» каналы появятся здесь"
                        ChannelStatus.SKIPPED -> "Пропущенных каналов нет"
                        ChannelStatus.PENDING -> "Все каналы просмотрены"
                    },
                )

                else -> {
                    if (state.all.size > 10) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = { Text("Поиск по username") },
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                            trailingIcon = {
                                if (query.isNotEmpty()) {
                                    IconButton(onClick = { query = "" }) {
                                        Icon(Icons.Filled.Clear, contentDescription = "Очистить")
                                    }
                                }
                            },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    if (state.visible.isEmpty()) {
                        CenteredMessage("Ничего не найдено")
                    } else {
                        LazyColumn(Modifier.fillMaxSize()) {
                            items(state.visible, key = { it.id }) { channel ->
                                ChannelRow(
                                    channel = channel,
                                    onOpen = { context.openTelegramChannel(channel.username) },
                                    onCopy = { context.copyToClipboard("@" + channel.username) },
                                    onMove = { target -> viewModel.move(channel, target) },
                                    onShowInReview = { onShowInReview(viewModel.listId, channel.position) },
                                )
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelRow(
    channel: Channel,
    onOpen: () -> Unit,
    onCopy: () -> Unit,
    onMove: (ChannelStatus) -> Unit,
    onShowInReview: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    ListItem(
        modifier = Modifier.clickable(onClick = onOpen),
        leadingContent = {
            Text(
                text = channel.position.formatCount(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(44.dp),
            )
        },
        headlineContent = {
            Text(
                text = "@${channel.username}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onCopy) {
                    Icon(painterResource(R.drawable.ic_copy), contentDescription = "Копировать username")
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Действия")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Открыть в Telegram") },
                            leadingIcon = { Icon(painterResource(R.drawable.ic_open_in_new), contentDescription = null) },
                            onClick = { menuOpen = false; onOpen() },
                        )
                        DropdownMenuItem(
                            text = { Text("Копировать username") },
                            leadingIcon = { Icon(painterResource(R.drawable.ic_copy), contentDescription = null) },
                            onClick = { menuOpen = false; onCopy() },
                        )
                        DropdownMenuItem(
                            text = { Text("Показать в просмотре") },
                            onClick = { menuOpen = false; onShowInReview() },
                        )
                        HorizontalDivider()
                        ChannelStatus.entries
                            .filter { it != channel.status }
                            .forEach { target ->
                                DropdownMenuItem(
                                    text = { Text(target.moveLabel()) },
                                    onClick = { menuOpen = false; onMove(target) },
                                )
                            }
                    }
                }
            }
        },
    )
}
