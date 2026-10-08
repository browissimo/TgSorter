package com.tgsorter.app.data.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Небольшие пользовательские настройки (SharedPreferences, локально). */
class SettingsRepository(context: Context) {

    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _autoOpenNext = MutableStateFlow(prefs.getBoolean(KEY_AUTO_OPEN_NEXT, false))

    /**
     * Режим «конвейер»: после + / − / Пропустить сразу открывать следующий канал в Telegram.
     * По умолчанию выключен — основной сценарий из ТЗ: открыть → посмотреть → отметить.
     */
    val autoOpenNext: StateFlow<Boolean> = _autoOpenNext.asStateFlow()

    fun setAutoOpenNext(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_OPEN_NEXT, enabled).apply()
        _autoOpenNext.value = enabled
    }

    private companion object {
        const val KEY_AUTO_OPEN_NEXT = "auto_open_next"
    }
}
