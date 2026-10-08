package com.tgsorter.app.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tgsorter.app.domain.model.ChannelStatus
import com.tgsorter.app.ui.theme.AppTheme
import com.tgsorter.app.util.formatCount

// ---------- Тексты статусов ----------

fun ChannelStatus.title(): String = when (this) {
    ChannelStatus.POSITIVE -> "Подходит"
    ChannelStatus.NEGATIVE -> "Не подходит"
    ChannelStatus.SKIPPED -> "Пропущен"
    ChannelStatus.PENDING -> "Не просмотрен"
}

fun ChannelStatus.listTitle(): String = when (this) {
    ChannelStatus.POSITIVE -> "Подходят"
    ChannelStatus.NEGATIVE -> "Не подходят"
    ChannelStatus.SKIPPED -> "Пропущенные"
    ChannelStatus.PENDING -> "Непросмотренные"
}

fun ChannelStatus.moveLabel(): String = when (this) {
    ChannelStatus.POSITIVE -> "Перенести в «Подходит» (+)"
    ChannelStatus.NEGATIVE -> "Перенести в «Не подходит» (−)"
    ChannelStatus.SKIPPED -> "Перенести в пропущенные"
    ChannelStatus.PENDING -> "Сбросить отметку"
}

fun ChannelStatus.symbol(): String = when (this) {
    ChannelStatus.POSITIVE -> "+"
    ChannelStatus.NEGATIVE -> "−"
    ChannelStatus.SKIPPED -> "↷"
    ChannelStatus.PENDING -> "•"
}

@Composable
fun ChannelStatus.containerColor(): Color = when (this) {
    ChannelStatus.POSITIVE -> AppTheme.statusColors.positive
    ChannelStatus.NEGATIVE -> AppTheme.statusColors.negative
    ChannelStatus.SKIPPED -> AppTheme.statusColors.skipped
    ChannelStatus.PENDING -> AppTheme.statusColors.pending
}

@Composable
fun ChannelStatus.contentColor(): Color = when (this) {
    ChannelStatus.POSITIVE -> AppTheme.statusColors.onPositive
    ChannelStatus.NEGATIVE -> AppTheme.statusColors.onNegative
    ChannelStatus.SKIPPED -> AppTheme.statusColors.onSkipped
    ChannelStatus.PENDING -> AppTheme.statusColors.onPending
}

// ---------- Общие элементы ----------

/** Строка статистики: «Подходит ........ 183». */
@Composable
fun StatLine(
    label: String,
    value: Int,
    modifier: Modifier = Modifier,
    dotColor: Color? = null,
    emphasized: Boolean = false,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dotColor != null) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(dotColor, CircleShape),
            )
            Spacer(Modifier.width(10.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value.formatCount(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (emphasized) FontWeight.Bold else FontWeight.SemiBold,
        )
    }
}

/** Плашка статуса канала. */
@Composable
fun StatusBadge(status: ChannelStatus, prefix: String = "") {
    Surface(
        color = status.containerColor(),
        contentColor = status.contentColor(),
        shape = RoundedCornerShape(50),
    ) {
        Text(
            text = "$prefix${status.symbol()} ${status.title()}",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
fun CenteredProgress() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
fun CenteredMessage(title: String, subtitle: String? = null) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        if (subtitle != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
fun TextInputDialog(
    title: String,
    initialValue: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by rememberSaveable { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it.take(100) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value.trim()) }, enabled = value.isNotBlank()) {
                Text(confirmLabel)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
