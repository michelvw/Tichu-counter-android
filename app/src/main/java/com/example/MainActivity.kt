package com.example

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.auth.GoogleAuthHelper
import com.example.data.preferences.AppPreferences
import com.example.data.repository.PendingGuestData
import com.example.domain.ThemeMode
import com.example.ui.navigation.TichuAppRoot
import com.example.ui.screens.AuthScreen
import com.example.ui.theme.TichuCounterTheme
import com.example.ui.theme.TichuTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val app = application as TichuApplication

    enableEdgeToEdge(
      statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
    )

    setContent {
      val scope = rememberCoroutineScope()
      val credentialManager = remember { CredentialManager.create(this@MainActivity) }
      var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }

      DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
          currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose {
          Firebase.auth.removeAuthStateListener(listener)
        }
      }

      val guestModeState by app.preferencesStore.guestModeFlow.collectAsStateWithLifecycle(
        initialValue = null,
      )
      val localPrefs by app.preferencesStore.preferencesFlow.collectAsStateWithLifecycle(
        initialValue = AppPreferences(),
      )

      LaunchedEffect(currentUser, guestModeState) {
        if (currentUser == null && guestModeState == false) {
          GoogleAuthHelper.attemptAutoSignIn(
            context = this@MainActivity,
            credentialManager = credentialManager,
            onAuthSuccess = { currentUser = Firebase.auth.currentUser },
            onUnauthenticated = { },
            scope = scope,
          )
        }
      }

      val systemDark = isSystemInDarkTheme()
      val isDark = when (localPrefs.themeMode) {
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

      TichuCounterTheme(themeMode = localPrefs.themeMode) {
        val signedInUser = currentUser
        if (signedInUser != null) {
          LaunchedEffect(signedInUser.uid) {
            app.preferencesStore.setGuestModeActive(false)
          }

          key(signedInUser.uid) {
            val repository = remember(signedInUser.uid) {
              app.createCloudRepository(signedInUser)
            }
            val cloudPrefs by repository.preferencesFlow.collectAsStateWithLifecycle(
              initialValue = localPrefs,
            )
            val pendingGuestMerge by repository.pendingGuestMergeFlow.collectAsStateWithLifecycle()
            var isMerging by remember { mutableStateOf(false) }
            var mergeError by remember { mutableStateOf<String?>(null) }

            DisposableEffect(repository) {
              onDispose {
                repository.close()
              }
            }
            LaunchedEffect(repository) {
              repository.initializeUserCloudState()
            }

            TichuCounterTheme(themeMode = cloudPrefs.themeMode) {
              TichuAppRoot(
                repository = repository,
                onSignOut = {
                  GoogleAuthHelper.signOut(
                    credentialManager = credentialManager,
                    scope = scope,
                    onBeforeSignOut = { repository.close() },
                    onSignOutComplete = { currentUser = null },
                  )
                },
              )

              val pending = pendingGuestMerge
              if (pending != null) {
                GuestDataMergeDialog(
                  pending = pending,
                  isBusy = isMerging,
                  errorMessage = mergeError,
                  onMerge = {
                    if (isMerging) return@GuestDataMergeDialog
                    isMerging = true
                    mergeError = null
                    scope.launch {
                      repository.resolvePendingGuestMerge(mergeIntoCloud = true)
                        .onFailure { err -> mergeError = err.message ?: "Could not merge local data." }
                      isMerging = false
                    }
                  },
                  onDiscard = {
                    if (isMerging) return@GuestDataMergeDialog
                    isMerging = true
                    mergeError = null
                    scope.launch {
                      repository.resolvePendingGuestMerge(mergeIntoCloud = false)
                        .onFailure { err -> mergeError = err.message ?: "Could not clear local data." }
                      isMerging = false
                    }
                  },
                )
              }
            }
          }
        } else if (guestModeState == true) {
          key("guest_local") {
            val localRepository = remember { app.createLocalRepository() }
            DisposableEffect(localRepository) {
              onDispose {
                localRepository.close()
              }
            }
            TichuAppRoot(
              repository = localRepository,
              onSignOut = {
                scope.launch {
                  app.preferencesStore.setGuestModeActive(false)
                }
              },
            )
          }
        } else if (guestModeState == false) {
          AuthScreen(
            onAuthSuccess = { currentUser = Firebase.auth.currentUser },
            onContinueAsGuest = {
              scope.launch {
                app.preferencesStore.setGuestModeActive(true)
              }
            },
          )
        } else {
          Box(
            modifier = Modifier
              .fillMaxSize()
              .background(TichuTheme.colors.bg),
          )
        }
      }
    }
  }
}

@androidx.compose.runtime.Composable
private fun GuestDataMergeDialog(
  pending: PendingGuestData,
  isBusy: Boolean,
  errorMessage: String?,
  onMerge: () -> Unit,
  onDiscard: () -> Unit,
) {
  val colors = TichuTheme.colors
  val summaryParts = buildList {
    if (pending.playerCount > 0) {
      add("${pending.playerCount} player${if (pending.playerCount == 1) "" else "s"}")
    }
    if (pending.sessionCount > 0) {
      add("${pending.sessionCount} session${if (pending.sessionCount == 1) "" else "s"}")
    }
    if (pending.gameCount > 0) {
      add("${pending.gameCount} game${if (pending.gameCount == 1) "" else "s"}")
    }
  }
  val summaryText = if (summaryParts.isEmpty()) "local data" else summaryParts.joinToString(", ")

  AlertDialog(
    onDismissRequest = {},
    containerColor = colors.surface,
    title = { Text("Keep guest data?", color = colors.text) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
          text = "You have $summaryText saved on this device from Guest mode. Would you like to merge them into your cloud account or discard them?",
          style = MaterialTheme.typography.bodyMedium,
          color = colors.textMuted,
        )
        if (!errorMessage.isNullOrBlank()) {
          Text(
            text = errorMessage,
            style = MaterialTheme.typography.bodySmall,
            color = colors.danger,
          )
        }
      }
    },
    confirmButton = {
      TextButton(
        onClick = onMerge,
        enabled = !isBusy,
        modifier = Modifier
          .heightIn(min = 48.dp)
          .testTag("merge_guest_data_button"),
      ) {
        if (isBusy) {
          CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
          Spacer(modifier = Modifier.width(6.dp))
        }
        Text("Merge into cloud", color = colors.brand, fontWeight = FontWeight.Bold)
      }
    },
    dismissButton = {
      TextButton(
        onClick = onDiscard,
        enabled = !isBusy,
        modifier = Modifier
          .heightIn(min = 48.dp)
          .testTag("discard_guest_data_button"),
      ) {
        Text("Discard local data", color = colors.danger, fontWeight = FontWeight.SemiBold)
      }
    },
  )
}
