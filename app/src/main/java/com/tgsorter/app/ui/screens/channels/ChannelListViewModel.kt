package com.tgsorter.app.ui.screens.channels

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.tgsorter.app.data.io.ExportManager
import com.tgsorter.app.data.io.SharedExport
import com.tgsorter.app.data.repository.ChannelRepository
import com.tgsorter.app.di.appContainer
import com.tgsorter.app.domain.export.ExportFormatter
import com.tgsorter.app.domain.export.ExportKind
import com.tgsorter.app.domain.model.Channel
import com.tgsorter.app.domain.model.ChannelStatus
import com.tgsorter.app.navigation.Routes
import com.tgsorter.app.util.channelsCount
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel as EventChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ChannelListUiState(
    val loading: Boolean = true,
    val missing: Boolean = false,
    val listName: String = "",
    val all: List<Channel> = emptyList(),
    val visible: List<Channel> = emptyList(),
    val query: String = "",
)

sealed interface ChannelListEvent {
    data class Share(val export: SharedExport) : ChannelListEvent
    data class Message(val text: String) : ChannelListEvent
    data class Moved(val username: String, val newStatus: ChannelStatus) : ChannelListEvent
}

class ChannelListViewModel(
    val listId: Long,
    val status: ChannelStatus,
    private val repository: ChannelRepository,
    private val exportManager: ExportManager,
) : ViewModel() {

    private val query = MutableStateFlow("")

    val uiState: StateFlow<ChannelListUiState> = combine(
        repository.observeList(listId),
        repository.observeChannels(listId, status),
        query,
    ) { list, channels, q ->
        val needle = q.trim().removePrefix("@").lowercase()
        ChannelListUiState(
            loading = false,
            missing = list == null,
            listName = list?.name.orEmpty(),
            all = channels,
            visible = if (needle.isEmpty()) channels else channels.filter { it.username.lowercase().contains(needle) },
            query = q,
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChannelListUiState())

    private val _events = EventChannel<ChannelListEvent>(EventChannel.BUFFERED)
    val events: Flow<ChannelListEvent> = _events.receiveAsFlow()

    val exportKind: ExportKind = ExportKind.forStatus(status)

    fun setQuery(value: String) {
        query.value = value
    }

    /** Весь список `@username` построчно — для кнопки «Копировать всё». */
    fun allUsernamesText(): String = ExportFormatter.usernames(uiState.value.all).trimEnd()

    fun move(channel: Channel, newStatus: ChannelStatus) = launchSafe {
        repository.setStatus(listId, channel.id, newStatus)
        _events.send(ChannelListEvent.Moved(channel.username, newStatus))
    }

    fun undo() = launchSafe {
        if (repository.undo(listId) == null) _events.send(ChannelListEvent.Message("Нечего отменять"))
    }

    fun share() = launchSafe {
        _events.send(ChannelListEvent.Share(exportManager.prepareShare(listId, exportKind)))
    }

    fun saveTo(uri: Uri) = launchSafe {
        val count = exportManager.saveTo(uri, listId, exportKind)
        _events.send(ChannelListEvent.Message("Сохранено: ${channelsCount(count)} → ${exportKind.fileName}"))
    }

    private fun launchSafe(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.send(ChannelListEvent.Message("Ошибка: ${e.message ?: e.javaClass.simpleName}"))
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                val args = createSavedStateHandle()
                val listId = checkNotNull(args.get<Long>(Routes.ARG_LIST_ID))
                val status = ChannelStatus.fromDb(args.get<String>(Routes.ARG_STATUS) ?: ChannelStatus.POSITIVE.dbValue)
                ChannelListViewModel(listId, status, container.repository, container.exportManager)
            }
        }
    }
}
