package com.tgsorter.app.ui.screens.review

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.tgsorter.app.data.repository.ChannelRepository
import com.tgsorter.app.data.repository.RateOutcome
import com.tgsorter.app.data.settings.SettingsRepository
import com.tgsorter.app.di.appContainer
import com.tgsorter.app.domain.model.Channel
import com.tgsorter.app.domain.model.ChannelListInfo
import com.tgsorter.app.domain.model.ChannelStatus
import com.tgsorter.app.domain.model.LastAction
import com.tgsorter.app.domain.model.ReviewMode
import com.tgsorter.app.domain.model.StatusCounts
import com.tgsorter.app.navigation.Routes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel as EventChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class ReviewUiState(
    val loading: Boolean = true,
    val listMissing: Boolean = false,
    val listId: Long = 0,
    val listName: String = "",
    val mode: ReviewMode = ReviewMode.PENDING,
    val channel: Channel? = null,
    val counts: StatusCounts = StatusCounts(),
    val lastAction: LastAction? = null,
    val undoCount: Int = 0,
    val completionDismissed: Boolean = false,
    val autoOpenNext: Boolean = false,
) {
    /** Сколько каналов осталось в текущей очереди (непросмотренные или пропущенные). */
    val queueRemaining: Int get() = counts.of(mode.queueStatus)

    val showCompletion: Boolean
        get() = !loading && !listMissing && counts.total > 0 && queueRemaining == 0 && !completionDismissed
}

sealed interface ReviewEvent {
    data class OpenTelegram(val username: String) : ReviewEvent
    data class Message(val text: String) : ReviewEvent
}

class ReviewViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val repository: ChannelRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    val listId: Long = checkNotNull(savedStateHandle.get<Long>(Routes.ARG_LIST_ID)) { "listId is required" }

    private val mode = MutableStateFlow(ReviewMode.fromArg(savedStateHandle.get<String>(Routes.ARG_MODE)))
    private val prepared = MutableStateFlow(false)
    private val completionDismissed = MutableStateFlow(false)

    /** Все изменения выполняются строго по очереди — никаких гонок при быстрых нажатиях. */
    private val actionMutex = Mutex()

    private val _events = EventChannel<ReviewEvent>(EventChannel.BUFFERED)
    val events: Flow<ReviewEvent> = _events.receiveAsFlow()

    private data class Core(val list: ChannelListInfo?, val channel: Channel?, val counts: StatusCounts)

    private data class Extra(
        val lastAction: LastAction?,
        val undoCount: Int,
        val mode: ReviewMode,
        val prepared: Boolean,
        val dismissed: Boolean,
    )

    val uiState: StateFlow<ReviewUiState> = combine(
        combine(
            repository.observeList(listId),
            repository.observeCurrentChannel(listId),
            repository.observeCounts(listId),
        ) { list, channel, counts -> Core(list, channel, counts) },
        combine(
            repository.observeLastAction(listId),
            repository.observeUndoCount(listId),
            mode,
            prepared,
            completionDismissed,
        ) { lastAction, undoCount, currentMode, isPrepared, dismissed ->
            Extra(lastAction, undoCount, currentMode, isPrepared, dismissed)
        },
        settings.autoOpenNext,
    ) { core, extra, autoOpen ->
        ReviewUiState(
            loading = !extra.prepared,
            listMissing = extra.prepared && core.list == null,
            listId = listId,
            listName = core.list?.name.orEmpty(),
            mode = extra.mode,
            channel = core.channel,
            counts = core.counts,
            lastAction = extra.lastAction,
            undoCount = extra.undoCount,
            completionDismissed = extra.dismissed,
            autoOpenNext = autoOpen,
        )
    }
        .catch { e ->
            _events.trySend(ReviewEvent.Message("Ошибка чтения данных: ${e.message ?: e.javaClass.simpleName}"))
            emit(ReviewUiState(loading = false, listMissing = true))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReviewUiState())

    init {
        viewModelScope.launch {
            actionMutex.withLock {
                try {
                    val jumpTo = savedStateHandle.get<Int>(Routes.ARG_POSITION) ?: 0
                    if (jumpTo > 0) {
                        // Пришли из списка каналов «Показать в просмотре»
                        repository.moveToPosition(listId, jumpTo)
                        repository.touchList(listId)
                        // Чтобы после пересоздания процесса не прыгнуть туда ещё раз
                        savedStateHandle[Routes.ARG_POSITION] = 0
                    } else {
                        // Продолжаем с того места, где остановились
                        repository.prepareReview(listId, mode.value.queueStatus)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    message("Не удалось открыть список: ${e.message ?: e.javaClass.simpleName}")
                }
                prepared.value = true
            }
        }
    }

    // ---------- Основной сценарий ----------

    /** + / − / Пропустить: сохранить отметку и перейти к следующему каналу очереди. */
    fun rate(status: ChannelStatus) {
        val channel = uiState.value.channel ?: return
        perform {
            val outcome = repository.rate(listId, channel.id, status, mode.value.queueStatus)
            if (outcome is RateOutcome.Done && settings.autoOpenNext.value) {
                val next = outcome.next
                if (next != null && next.id != channel.id) {
                    _events.send(ReviewEvent.OpenTelegram(next.username))
                }
            }
        }
    }

    fun undo() = perform {
        if (repository.undo(listId) == null) {
            message("Нечего отменять")
        } else {
            completionDismissed.value = false
        }
    }

    // ---------- Навигация ----------

    fun next() = perform {
        if (!repository.moveNext(listId)) message("Это последний канал списка")
    }

    fun previous() = perform {
        if (!repository.movePrevious(listId)) message("Это первый канал списка")
    }

    fun goToLastReviewed() = perform {
        if (!repository.moveToLastReviewed(listId)) message("Ещё нет отмеченных каналов")
    }

    fun goToFirstInQueue() = perform {
        if (!repository.moveToFirstWithStatus(listId, mode.value.queueStatus)) {
            message(
                if (mode.value == ReviewMode.PENDING) "Непросмотренных каналов нет" else "Пропущенных каналов нет",
            )
        }
    }

    fun goToNumber(position: Int) = perform {
        if (!repository.moveToPosition(listId, position)) message("Нет канала с номером $position")
    }

    fun setMode(newMode: ReviewMode) = perform {
        repository.prepareReview(listId, newMode.queueStatus)
        mode.value = newMode
        savedStateHandle[Routes.ARG_MODE] = newMode.name
        completionDismissed.value = false
    }

    /** «Вернуться к каналам» с экрана завершения — свободный просмотр стрелками. */
    fun dismissCompletion() {
        completionDismissed.value = true
    }

    fun setAutoOpenNext(enabled: Boolean) = settings.setAutoOpenNext(enabled)

    // ---------- Внутреннее ----------

    private fun perform(block: suspend () -> Unit) {
        viewModelScope.launch {
            actionMutex.withLock {
                try {
                    block()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    message("Ошибка сохранения: ${e.message ?: e.javaClass.simpleName}")
                }
            }
        }
    }

    private suspend fun message(text: String) = _events.send(ReviewEvent.Message(text))

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                ReviewViewModel(createSavedStateHandle(), container.repository, container.settings)
            }
        }
    }
}
