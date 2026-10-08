package com.tgsorter.app.ui.screens.results

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
import com.tgsorter.app.domain.export.ExportKind
import com.tgsorter.app.domain.model.StatusCounts
import com.tgsorter.app.navigation.Routes
import com.tgsorter.app.util.channelsCount
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel as EventChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ResultsUiState(
    val loading: Boolean = true,
    val missing: Boolean = false,
    val listName: String = "",
    val counts: StatusCounts = StatusCounts(),
) {
    fun countFor(kind: ExportKind): Int = kind.status?.let { counts.of(it) } ?: counts.total
}

sealed interface ExportEvent {
    data class Share(val export: SharedExport) : ExportEvent
    data class Message(val text: String) : ExportEvent
}

class ResultsViewModel(
    val listId: Long,
    private val repository: ChannelRepository,
    private val exportManager: ExportManager,
) : ViewModel() {

    val uiState: StateFlow<ResultsUiState> = combine(
        repository.observeList(listId),
        repository.observeCounts(listId),
    ) { list, counts ->
        ResultsUiState(loading = false, missing = list == null, listName = list?.name.orEmpty(), counts = counts)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ResultsUiState())

    private val _events = EventChannel<ExportEvent>(EventChannel.BUFFERED)
    val events: Flow<ExportEvent> = _events.receiveAsFlow()

    /** Запись в файл, выбранный в системном диалоге «Сохранить как». */
    fun saveTo(kind: ExportKind, uri: Uri) = launchExport {
        val count = exportManager.saveTo(uri, listId, kind)
        _events.send(ExportEvent.Message("Сохранено: ${channelsCount(count)} → ${kind.fileName}"))
    }

    /** Подготовка файла для системного меню «Поделиться». */
    fun share(kind: ExportKind) = launchExport {
        _events.send(ExportEvent.Share(exportManager.prepareShare(listId, kind)))
    }

    private fun launchExport(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.send(ExportEvent.Message("Ошибка экспорта: ${e.message ?: e.javaClass.simpleName}"))
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                val listId = checkNotNull(createSavedStateHandle().get<Long>(Routes.ARG_LIST_ID))
                ResultsViewModel(listId, container.repository, container.exportManager)
            }
        }
    }
}
