package com.tgsorter.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tgsorter.app.TgSorterApp
import com.tgsorter.app.telegram.OpenMethod
import com.tgsorter.app.telegram.TelegramLauncher
import com.tgsorter.app.util.openTelegramChannel

/** Выбор способа открытия каналов + проверка на заведомо существующем канале @telegram. */
@Composable
fun OpenMethodDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val settings = remember(context) { (context.applicationContext as TgSorterApp).container.settings }
    val current by settings.openMethod.collectAsStateWithLifecycle()
    val diagnostics = remember(context) { TelegramLauncher.diagnostics(context) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Как открывать каналы") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OpenMethod.entries.forEach { method ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .selectable(
                                selected = method == current,
                                onClick = { settings.setOpenMethod(method) },
                                role = Role.RadioButton,
                            )
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = method == current, onClick = null)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(method.title, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                method.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "Нажмите «Проверить». Если @telegram открылся, способ рабочий, а канал из списка, " +
                        "скорее всего, удалён или переименован.\n\n" +
                        "Если ссылками не открывается даже @telegram, значит, ваш Telegram сейчас не может " +
                        "находить каналы по ссылкам (то же видно, если нажать ссылку t.me внутри самого Telegram). " +
                        "Чаще всего это временный лимит Telegram на такие запросы. Выберите «Поиск в Telegram».",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    diagnostics,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Готово") } },
        dismissButton = {
            TextButton(onClick = { context.openTelegramChannel(TelegramLauncher.TEST_USERNAME, current) }) {
                Text("Проверить на @telegram")
            }
        },
    )
}
