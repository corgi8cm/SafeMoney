package io.github.corgi8cm.safemoney.ui

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.corgi8cm.safemoney.SafeMoneyApp
import io.github.corgi8cm.safemoney.data.SafeMoneyRepository
import io.github.corgi8cm.safemoney.ui.goal.GoalDetailViewModel
import io.github.corgi8cm.safemoney.ui.goal.GoalEditViewModel
import io.github.corgi8cm.safemoney.ui.home.HomeViewModel
import io.github.corgi8cm.safemoney.ui.saving.SavingEditViewModel
import io.github.corgi8cm.safemoney.ui.settings.QuickReasonsViewModel
import io.github.corgi8cm.safemoney.ui.settings.SettingsViewModel
import io.github.corgi8cm.safemoney.ui.stats.StatsViewModel

/** 所有 ViewModel 的建構方式（手動相依注入）。導覽參數透過 SavedStateHandle 傳入。 */
object AppViewModelProvider {
    val Factory = viewModelFactory {
        initializer { HomeViewModel(repository()) }
        initializer { GoalEditViewModel(createSavedStateHandle(), repository()) }
        initializer { GoalDetailViewModel(createSavedStateHandle(), repository()) }
        initializer { SavingEditViewModel(createSavedStateHandle(), repository()) }
        initializer { StatsViewModel(repository()) }
        initializer { SettingsViewModel(app(), repository()) }
        initializer { QuickReasonsViewModel(repository()) }
    }
}

private fun CreationExtras.app(): SafeMoneyApp = checkNotNull(this[APPLICATION_KEY]) as SafeMoneyApp

private fun CreationExtras.repository(): SafeMoneyRepository = app().repository
