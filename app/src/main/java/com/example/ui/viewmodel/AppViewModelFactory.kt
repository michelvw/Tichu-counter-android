package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.data.repository.TichuRepository

class AppViewModelFactory(
  private val repository: TichuRepository,
  private val argId: String? = null,
) : ViewModelProvider.Factory {
  @Suppress("UNCHECKED_CAST")
  override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
    modelClass.isAssignableFrom(ScoreboardViewModel::class.java) ->
      ScoreboardViewModel(repository) as T
    modelClass.isAssignableFrom(RoundScoresViewModel::class.java) ->
      RoundScoresViewModel(repository, argId) as T
    modelClass.isAssignableFrom(SessionsViewModel::class.java) ->
      SessionsViewModel(repository, argId) as T
    modelClass.isAssignableFrom(PlayersViewModel::class.java) ->
      PlayersViewModel(repository, argId) as T
    modelClass.isAssignableFrom(SettingsViewModel::class.java) ->
      SettingsViewModel(repository) as T
    else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
  }
}
