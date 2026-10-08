package com.tgsorter.app.di

import android.content.Context
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.tgsorter.app.TgSorterApp
import com.tgsorter.app.data.database.AppDatabase
import com.tgsorter.app.data.io.ExportManager
import com.tgsorter.app.data.io.TextFileReader
import com.tgsorter.app.data.repository.ChannelRepository
import com.tgsorter.app.data.settings.SettingsRepository

/** Простейший ручной DI: все зависимости создаются один раз на процесс. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val database: AppDatabase by lazy { AppDatabase.create(appContext) }
    val repository: ChannelRepository by lazy { ChannelRepository(database) }
    val settings: SettingsRepository by lazy { SettingsRepository(appContext) }
    val fileReader: TextFileReader by lazy { TextFileReader(appContext) }
    val exportManager: ExportManager by lazy { ExportManager(appContext, repository) }
}

/** Доступ к контейнеру из фабрик ViewModel. */
fun CreationExtras.appContainer(): AppContainer =
    (checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]) as TgSorterApp).container
