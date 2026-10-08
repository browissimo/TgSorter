@file:OptIn(ExperimentalMaterial3Api::class)

package com.tgsorter.app.ui.screens.review

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tgsorter.app.R
import com.tgsorter.app.domain.model.Channel
import com.tgsorter.app.domain.model.ChannelStatus
import com.tgsorter.app.domain.model.LastAction
import com.tgsorter.app.domain.model.ReviewMode
import com.tgsorter.app.domain.model.StatusCounts
import com.tgsorter.app.ui.components.CenteredMessage
import com.tgsorter.app.ui.components.CenteredProgress
import com.tgsorter.app.ui.components.StatLine
import com.tgsorter.app.ui.components.StatusBadge
import com.tgsorter.app.ui.components.containerColor
import com.tgsorter.app.ui.components.contentColor
import com.tgsorter.app.ui.components.title
import com.tgsorter.app.ui.theme.AppTheme
import com.tgsorter.app.util.copyToClipboard
import com.tgsorter.app.util.formatCount
import com.tgsorter.app.util.formatPercent
import com.tgsorter.app.util.openTelegramChannel
import com.tgsorter.app.util.toast

/**
 * Главный рабочий экран: открыть канал → посмотреть в Telegram → + / − → следующий.
 * Все основные кнопки — внизу, в зоне большого пальца.
 */
@Composable
fun ReviewScreen(
    onBack: () -> Unit,
    onOpenResults: (Long) -> Unit,
    onOpenStatusList: (Long, ChannelStatus) -> Unit,
    viewModel: ReviewViewModel = viewModel(factory = ReviewViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    var menuOpen by remember { mutableStateOf(false) }
    var showGoToDialog by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ReviewEvent.OpenTelegram -> context.openTelegramChannel(event.username)
                is ReviewEvent.Message -> context.toast(event.text)
            }
        }
    }

    val rate: (ChannelStatus) -> Unit = { status ->
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        viewModel.rate(status)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = state.listName,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (state.mode == ReviewMode.SKIPPED) {
                            Text(
                                text = "Разбор пропущенных",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.tertiary,
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
                        ReviewMenu(
                            expanded = menuOpen,
                            state = state,
                            onDismiss = { menuOpen = false },
                            onGoToNumber = { showGoToDialog = true },
                            onLastReviewed = viewModel::goToLastReviewed,
                            onFirstInQueue = viewModel::goToFirstInQueue,
                            onSwitchMode = viewModel::setMode,
                            onResults = { onOpenResults(viewModel.listId) },
                            onToggleAutoOpen = { viewModel.setAutoOpenNext(!state.autoOpenNext) },
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                state.loading -> CenteredProgress()
                state.listMissing -> CenteredMessage("Список не найден", "Возможно, он был удалён")
                state.counts.total == 0 -> CenteredMessage("В списке нет каналов")
                state.showCompletion -> CompletionContent(
                    state = state,
                    onShowPositive = { onOpenStatusList(viewModel.listId, ChannelStatus.POSITIVE) },
                    onShowNegative = { onOpenStatusList(viewModel.listId, ChannelStatus.NEGATIVE) },
                    onResults = { onOpenResults(viewModel.listId) },
                    onSwitchMode = viewModel::setMode,
                    onUndo = viewModel::undo,
                    onBrowse = viewModel::dismissCompletion,
                )
                else -> ReviewContent(
                    state = state,
                    onOpen = { state.channel?.let { context.openTelegramChannel(it.username) } },
                    onCopy = { state.channel?.let { context.copyToClipboard("@" + it.username) } },
                    onRate = rate,
                    onUndo = viewModel::undo,
                    onPrevious = viewModel::previous,
                    onNext = viewModel::next,
                )
            }
        }
    }

    if (showGoToDialog) {
        GoToNumberDialog(
            total = state.counts.total,
            onDismiss = { showGoToDialog = false },
            onGo = { number ->
                showGoToDialog = false
                viewModel.goToNumber(number)
            },
        )
    }
}

@Composable
private fun ReviewContent(
    state: ReviewUiState,
    onOpen: () -> Unit,
    onCopy: () -> Unit,
    onRate: (ChannelStatus) -> Unit,
    onUndo: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    val channel = state.channel
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        ProgressHeader(position = channel?.position ?: 0, counts = state.counts)
        LastActionBar(lastAction = state.lastAction, undoCount = state.undoCount, onUndo = onUndo)

        // Центр: крупный username (тап — открыть в Telegram)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            if (channel == null) {
                Text("Канал не найден", style = MaterialTheme.typography.titleMedium)
            } else {
                ChannelCard(channel = channel, mode = state.mode, onOpen = onOpen, onCopy = onCopy)
            }
        }

        // Зона большого пальца
        Button(
            onClick = onOpen,
            enabled = channel != null,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            shape = RoundedCornerShape(18.dp),
        ) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
            Spacer(Modifier.width(10.dp))
            Text("Открыть в Telegram", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(108.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RateButton(
                symbol = "−",
                label = "Не подходит",
                container = AppTheme.statusColors.negative,
                content = AppTheme.statusColors.onNegative,
                selected = channel?.status == ChannelStatus.NEGATIVE,
                enabled = channel != null,
                onClick = { onRate(ChannelStatus.NEGATIVE) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
            RateButton(
                symbol = "+",
                label = "Подходит",
                container = AppTheme.statusColors.positive,
                content = AppTheme.statusColors.onPositive,
                selected = channel?.status == ChannelStatus.POSITIVE,
                enabled = channel != null,
                onClick = { onRate(ChannelStatus.POSITIVE) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = onPrevious,
                modifier = Modifier
                    .width(68.dp)
                    .fillMaxHeight(),
                contentPadding = PaddingValues(0.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Предыдущий канал")
            }
            FilledTonalButton(
                onClick = { onRate(ChannelStatus.SKIPPED) },
                enabled = channel != null,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(painterResource(R.drawable.ic_skip), contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Пропустить", fontSize = 16.sp)
            }
            OutlinedButton(
                onClick = onNext,
                modifier = Modifier
                    .width(68.dp)
                    .fillMaxHeight(),
                contentPadding = PaddingValues(0.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Следующий канал")
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun ProgressHeader(position: Int, counts: StatusCounts) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "${position.formatCount()} / ${counts.total.formatCount()}",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { counts.progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
        )
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val colors = AppTheme.statusColors
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = colors.positive, fontWeight = FontWeight.Bold)) {
                        append("+${counts.positive.formatCount()}")
                    }
                    append("   ")
                    withStyle(SpanStyle(color = colors.negative, fontWeight = FontWeight.Bold)) {
                        append("−${counts.negative.formatCount()}")
                    }
                    append("   ")
                    append("↷${counts.skipped.formatCount()}")
                },
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "осталось ${counts.pending.formatCount()} · ${counts.progress.formatPercent()}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LastActionBar(lastAction: LastAction?, undoCount: Int, onUndo: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = lastAction?.let { "@${it.username}: ${it.newStatus.title().lowercase()}" }
                ?: "Откройте канал, затем отметьте + или −",
            style = MaterialTheme.typography.bodyMedium,
            color = lastAction?.newStatus?.let { statusTextColor(it) }
                ?: MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onUndo, enabled = undoCount > 0) {
            Icon(painterResource(R.drawable.ic_undo), contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(if (undoCount > 0) "Отменить ($undoCount)" else "Отменить")
        }
    }
}

@Composable
private fun statusTextColor(status: ChannelStatus): Color = when (status) {
    ChannelStatus.POSITIVE -> AppTheme.statusColors.positive
    ChannelStatus.NEGATIVE -> AppTheme.statusColors.negative
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun ChannelCard(channel: Channel, mode: ReviewMode, onOpen: () -> Unit, onCopy: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val showBadge = channel.status != ChannelStatus.PENDING &&
            !(mode == ReviewMode.SKIPPED && channel.status == ChannelStatus.SKIPPED)
        if (showBadge) {
            StatusBadge(status = channel.status, prefix = "Уже отмечен: ")
            Spacer(Modifier.height(12.dp))
        }
        Text(
            text = "@${channel.username}",
            fontSize = usernameFontSize(channel.username.length + 1),
            lineHeight = usernameFontSize(channel.username.length + 1) * 1.15f,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable(onClick = onOpen)
                .padding(horizontal = 8.dp, vertical = 16.dp),
        )
        TextButton(onClick = onCopy) {
            Icon(painterResource(R.drawable.ic_copy), contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Копировать username")
        }
    }
}

private fun usernameFontSize(length: Int): TextUnit = when {
    length <= 12 -> 38.sp
    length <= 16 -> 32.sp
    length <= 22 -> 27.sp
    else -> 22.sp
}

@Composable
private fun RateButton(
    symbol: String,
    label: String,
    container: Color,
    content: Color,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content),
        border = if (selected) BorderStroke(3.dp, MaterialTheme.colorScheme.onBackground) else null,
        contentPadding = PaddingValues(4.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(symbol, fontSize = 46.sp, lineHeight = 48.sp, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun ReviewMenu(
    expanded: Boolean,
    state: ReviewUiState,
    onDismiss: () -> Unit,
    onGoToNumber: () -> Unit,
    onLastReviewed: () -> Unit,
    onFirstInQueue: () -> Unit,
    onSwitchMode: (ReviewMode) -> Unit,
    onResults: () -> Unit,
    onToggleAutoOpen: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text("Перейти к номеру…") },
            onClick = { onDismiss(); onGoToNumber() },
        )
        DropdownMenuItem(
            text = { Text("К последнему отмеченному") },
            onClick = { onDismiss(); onLastReviewed() },
        )
        DropdownMenuItem(
            text = {
                Text(
                    if (state.mode == ReviewMode.PENDING) "К первому непросмотренному" else "К первому пропущенному",
                )
            },
            onClick = { onDismiss(); onFirstInQueue() },
        )
        HorizontalDivider()
        if (state.mode == ReviewMode.PENDING) {
            DropdownMenuItem(
                text = { Text("Разобрать пропущенные (${state.counts.skipped.formatCount()})") },
                enabled = state.counts.skipped > 0,
                onClick = { onDismiss(); onSwitchMode(ReviewMode.SKIPPED) },
            )
        } else {
            DropdownMenuItem(
                text = { Text("К непросмотренным (${state.counts.pending.formatCount()})") },
                onClick = { onDismiss(); onSwitchMode(ReviewMode.PENDING) },
            )
        }
        DropdownMenuItem(
            text = { Text("Результаты и экспорт") },
            onClick = { onDismiss(); onResults() },
        )
        HorizontalDivider()
        DropdownMenuItem(
            text = {
                Column {
                    Text("Сразу открывать следующий")
                    Text(
                        "после + / − / Пропустить",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            trailingIcon = {
                if (state.autoOpenNext) Icon(Icons.Filled.Check, contentDescription = "Включено")
            },
            onClick = { onDismiss(); onToggleAutoOpen() },
        )
    }
}

@Composable
private fun CompletionContent(
    state: ReviewUiState,
    onShowPositive: () -> Unit,
    onShowNegative: () -> Unit,
    onResults: () -> Unit,
    onSwitchMode: (ReviewMode) -> Unit,
    onUndo: () -> Unit,
    onBrowse: () -> Unit,
) {
    val counts = state.counts
    val colors = AppTheme.statusColors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = colors.positive,
            modifier = Modifier.size(64.dp),
        )
        Text(
            text = if (state.mode == ReviewMode.PENDING) "Список завершён!" else "Пропущенные разобраны!",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "Всего: ${counts.total.formatCount()}",
            style = MaterialTheme.typography.titleMedium,
        )
        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            StatLine("+ Подходит", counts.positive, dotColor = colors.positive)
            StatLine("− Не подходит", counts.negative, dotColor = colors.negative)
            StatLine("Пропущено", counts.skipped, dotColor = colors.skipped)
            if (counts.pending > 0) StatLine("Не просмотрено", counts.pending, dotColor = colors.pending)
        }

        val big = Modifier
            .fillMaxWidth()
            .height(56.dp)
        Button(
            onClick = onShowPositive,
            modifier = big,
            colors = ButtonDefaults.buttonColors(
                containerColor = ChannelStatus.POSITIVE.containerColor(),
                contentColor = ChannelStatus.POSITIVE.contentColor(),
            ),
        ) { Text("Посмотреть +", fontSize = 17.sp) }
        Button(
            onClick = onShowNegative,
            modifier = big,
            colors = ButtonDefaults.buttonColors(
                containerColor = ChannelStatus.NEGATIVE.containerColor(),
                contentColor = ChannelStatus.NEGATIVE.contentColor(),
            ),
        ) { Text("Посмотреть −", fontSize = 17.sp) }
        Button(onClick = onResults, modifier = big) {
            Icon(painterResource(R.drawable.ic_download), contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Экспорт результатов", fontSize = 17.sp)
        }
        if (state.mode == ReviewMode.PENDING && counts.skipped > 0) {
            FilledTonalButton(onClick = { onSwitchMode(ReviewMode.SKIPPED) }, modifier = big) {
                Text("Разобрать пропущенные (${counts.skipped.formatCount()})")
            }
        }
        if (state.mode == ReviewMode.SKIPPED && counts.pending > 0) {
            FilledTonalButton(onClick = { onSwitchMode(ReviewMode.PENDING) }, modifier = big) {
                Text("К непросмотренным (${counts.pending.formatCount()})")
            }
        }
        if (state.undoCount > 0) {
            OutlinedButton(onClick = onUndo, modifier = big) {
                Icon(painterResource(R.drawable.ic_undo), contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Отменить последнее действие")
            }
        }
        TextButton(onClick = onBrowse) { Text("Листать каналы списка") }
    }
}

@Composable
private fun GoToNumberDialog(total: Int, onDismiss: () -> Unit, onGo: (Int) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val number = text.toIntOrNull()
    val valid = number != null && number in 1..total
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Перейти к номеру") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { value -> text = value.filter { it.isDigit() }.take(7) },
                label = { Text("от 1 до ${total.formatCount()}") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = text.isNotEmpty() && !valid,
            )
        },
        confirmButton = {
            TextButton(onClick = { number?.let(onGo) }, enabled = valid) { Text("Перейти") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
