@file:OptIn(ExperimentalMaterial3Api::class)

package com.tgsorter.app.ui.screens.results

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tgsorter.app.R
import com.tgsorter.app.domain.export.ExportKind
import com.tgsorter.app.domain.model.ChannelStatus
import com.tgsorter.app.domain.model.ReviewMode
import com.tgsorter.app.ui.components.CenteredMessage
import com.tgsorter.app.ui.components.CenteredProgress
import com.tgsorter.app.ui.components.StatLine
import com.tgsorter.app.ui.components.containerColor
import com.tgsorter.app.ui.components.contentColor
import com.tgsorter.app.ui.components.rememberExportSaver
import com.tgsorter.app.ui.theme.AppTheme
import com.tgsorter.app.util.formatCount
import com.tgsorter.app.util.shareExport
import com.tgsorter.app.util.toast

@Composable
fun ResultsScreen(
    onBack: () -> Unit,
    onOpenStatusList: (Long, ChannelStatus) -> Unit,
    onReview: (Long, ReviewMode) -> Unit,
    viewModel: ResultsViewModel = viewModel(factory = ResultsViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val listId = viewModel.listId

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ExportEvent.Share -> context.shareExport(event.export)
                is ExportEvent.Message -> context.toast(event.text)
            }
        }
    }

    val save = rememberExportSaver { kind, uri -> viewModel.saveTo(kind, uri) }
    val guardEmpty: (ExportKind, () -> Unit) -> Unit = { kind, action ->
        if (state.countFor(kind) == 0) context.toast("Список пуст — экспортировать нечего") else action()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Результаты", style = MaterialTheme.typography.titleMedium)
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
            )
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                state.loading -> CenteredProgress()
                state.missing -> CenteredMessage("Список не найден")
                else -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val counts = state.counts
                    val colors = AppTheme.statusColors

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            StatLine("+ Подходит", counts.positive, dotColor = colors.positive)
                            StatLine("− Не подходит", counts.negative, dotColor = colors.negative)
                            StatLine("Пропущено", counts.skipped, dotColor = colors.skipped)
                            StatLine("Не просмотрено", counts.pending, dotColor = colors.pending)
                        }
                    }

                    ShowListButton("Показать +", counts.positive, ChannelStatus.POSITIVE) {
                        onOpenStatusList(listId, ChannelStatus.POSITIVE)
                    }
                    ShowListButton("Показать −", counts.negative, ChannelStatus.NEGATIVE) {
                        onOpenStatusList(listId, ChannelStatus.NEGATIVE)
                    }
                    ShowListButton("Показать пропущенные", counts.skipped, ChannelStatus.SKIPPED) {
                        onOpenStatusList(listId, ChannelStatus.SKIPPED)
                    }
                    ShowListButton("Показать непросмотренные", counts.pending, ChannelStatus.PENDING) {
                        onOpenStatusList(listId, ChannelStatus.PENDING)
                    }

                    if (counts.pending > 0) {
                        Button(
                            onClick = { onReview(listId, ReviewMode.PENDING) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                        ) { Text("Продолжить просмотр (${counts.pending.formatCount()})", fontSize = 16.sp) }
                    }
                    if (counts.skipped > 0) {
                        FilledTonalButton(
                            onClick = { onReview(listId, ReviewMode.SKIPPED) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                        ) { Text("Разобрать пропущенные (${counts.skipped.formatCount()})") }
                    }

                    Spacer(Modifier.height(4.dp))
                    Text("Экспорт", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        "«Сохранить» — выбрать папку и имя файла. «Поделиться» — отправить в Telegram, почту, на диск и т. п. " +
                            "CSV можно потом импортировать обратно — отметки восстановятся.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    ExportKind.entries.forEach { kind ->
                        ExportRow(
                            kind = kind,
                            count = state.countFor(kind),
                            onSave = { guardEmpty(kind) { save(kind) } },
                            onShare = { guardEmpty(kind) { viewModel.share(kind) } },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ShowListButton(label: String, count: Int, status: ChannelStatus, onClick: () -> Unit) {
    val container: Color = status.containerColor()
    val content: Color = status.contentColor()
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content),
    ) {
        Text(label, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Text(count.formatCount(), fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(4.dp))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
    }
}

private fun ExportKind.description(): String = when (this) {
    ExportKind.POSITIVE -> "Подходят (+)"
    ExportKind.NEGATIVE -> "Не подходят (−)"
    ExportKind.SKIPPED -> "Пропущенные"
    ExportKind.PENDING -> "Непросмотренные"
    ExportKind.CSV -> "Все каналы: username,status,position"
}

@Composable
private fun ExportRow(kind: ExportKind, count: Int, onSave: () -> Unit, onShare: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(kind.fileName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "${kind.description()} · ${count.formatCount()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onSave) {
                Icon(painterResource(R.drawable.ic_download), contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Сохранить")
            }
            IconButton(onClick = onShare) {
                Icon(Icons.Filled.Share, contentDescription = "Поделиться ${kind.fileName}")
            }
        }
    }
}
