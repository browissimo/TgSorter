package com.tgsorter.app.ui.screens.home

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.tgsorter.app.data.io.FileReadException
import com.tgsorter.app.data.io.TextFileReader
import com.tgsorter.app.data.repository.ChannelRepository
import com.tgsorter.app.di.appContainer
import com.tgsorter.app.domain.model.Channel
import com.tgsorter.app.domain.model.ChannelListSummary
import com.tgsorter.app.domain.parser.ChannelListParser
import com.tgsorter.app.util.channelsCount
import com.tgsorter.app.util.formatCount
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel as EventChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class HomeUiState(
    val loading: Boolean = true,
    val lists: List<ChannelListSummary> = emptyList(),
    /** Канал, с которого продолжится просмотр активного списка. */
    val resumeChannel: Channel? = null,
    val error: String? = null,
) {
    /** Активный список — последний открытый. */
    val active: ChannelListSummary? get() = lists.firstOrNull()
}

sealed interface ImportState {
    data object Idle : ImportState
    data object Running : ImportState
    data class Done(val listId: Long, val title: String, val lines: List<String>, val canStart: Boolean) : ImportState
    data class Failed(val message: String) : ImportState
}

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val repository: ChannelRepository,
    private val fileReader: TextFileReader,
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = repository.observeListSummaries()
        .mapLatest { lists ->
            HomeUiState(
                loading = false,
                lists = lists,
                resumeChannel = lists.firstOrNull()?.let { repository.findResumeChannel(it.id) },
            )
        }
        .catch { e ->
            emit(HomeUiState(loading = false, error = "Не удалось загрузить данные: ${e.message ?: e.javaClass.simpleName}"))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    private val _importState = MutableStateFlow<ImportState>(ImportState.Idle)
    val importState: StateFlow<ImportState> = _importState.asStateFlow()

    private val _messages = EventChannel<String>(EventChannel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    /**
     * Импорт TXT/CSV.
     * @param appendToListId null — создать новый список; иначе дополнить существующий.
     */
    fun importFile(uri: Uri, appendToListId: Long?) {
        if (_importState.value == ImportState.Running) return
        _importState.value = ImportState.Running
        viewModelScope.launch {
            _importState.value = try {
                val file = withContext(Dispatchers.IO) { fileReader.read(uri) }
                val parsed = withContext(Dispatchers.Default) { ChannelListParser.parse(file.text) }
                when {
                    parsed.entries.isEmpty() -> ImportState.Failed(
                        buildString {
                            append("В файле нет ни одного корректного username Telegram.")
                            if (parsed.invalidExamples.isNotEmpty()) {
                                append("\n\nПримеры строк:\n")
                                append(parsed.invalidExamples.joinToString("\n") { "• $it" })
                            }
                            append("\n\nОжидается по одному каналу в строке: @username, t.me/username или https://t.me/username")
                        },
                    )

                    appendToListId == null -> {
                        val name = defaultListName(file.displayName)
                        val listId = repository.createList(name, parsed.entries)
                        ImportState.Done(
                            listId = listId,
                            title = "Список «$name» создан",
                            lines = report(parsed, added = parsed.entries.size, alreadyInList = 0),
                            canStart = true,
                        )
                    }

                    else -> {
                        val outcome = repository.appendToList(appendToListId, parsed.entries)
                        ImportState.Done(
                            listId = appendToListId,
                            title = if (outcome.added > 0) "Список дополнен" else "Новых каналов нет",
                            lines = report(parsed, added = outcome.added, alreadyInList = outcome.alreadyInList),
                            canStart = outcome.added > 0,
                        )
                    }
                }
            } catch (e: FileReadException) {
                ImportState.Failed(e.message ?: "Не удалось прочитать файл")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ImportState.Failed("Ошибка сохранения данных: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    fun dismissImportResult() {
        _importState.value = ImportState.Idle
    }

    fun renameList(listId: Long, name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        launchSafe { repository.renameList(listId, clean) }
    }

    fun deleteList(listId: Long) = launchSafe {
        repository.deleteList(listId)
        _messages.send("Список удалён")
    }

    /** Делает список активным (первым на главном экране). */
    fun makeActive(listId: Long) = launchSafe { repository.touchList(listId) }

    private fun launchSafe(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _messages.send("Ошибка: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    private fun report(parsed: ChannelListParser.Result, added: Int, alreadyInList: Int): List<String> = buildList {
        add("Добавлено: ${channelsCount(added)}")
        if (parsed.duplicates > 0) add("Удалено дубликатов: ${parsed.duplicates.formatCount()}")
        if (alreadyInList > 0) add("Уже были в списке: ${alreadyInList.formatCount()}")
        if (parsed.restoredStatuses > 0) add("Восстановлено отметок из CSV: ${parsed.restoredStatuses.formatCount()}")
        if (parsed.invalidLines > 0) {
            add("Не распознано строк: ${parsed.invalidLines.formatCount()}")
            parsed.invalidExamples.take(3).forEach { add("   • $it") }
        }
    }

    private fun defaultListName(displayName: String?): String {
        val fromFile = displayName
            ?.substringBeforeLast('.')
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        return fromFile ?: ("Список от " + SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date()))
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                HomeViewModel(container.repository, container.fileReader)
            }
        }
    }
}
