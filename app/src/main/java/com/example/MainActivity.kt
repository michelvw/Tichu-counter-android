package com.example

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.preferences.AppPreferences
import com.example.domain.ThemeMode
import com.example.ui.navigation.TichuAppRoot
import com.example.ui.theme.TichuCounterTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val app = application as TichuApplication

    enableEdgeToEdge(
      statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
    )

    setContent {
      val prefs by app.repository.preferencesFlow.collectAsStateWithLifecycle(
        initialValue = AppPreferences(),
      )
      val systemDark = isSystemInDarkTheme()
      val isDark = when (prefs.themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
      }

      LaunchedEffect(isDark) {
        enableEdgeToEdge(
          statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
          navigationBarStyle = if (isDark) {
            SystemBarStyle.dark(Color.TRANSPARENT)
          } else {
            SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
          },
        )
      }

      TichuCounterTheme(themeMode = prefs.themeMode) {
        TichuAppRoot(repository = app.repository)
      }
    }
  }
}
