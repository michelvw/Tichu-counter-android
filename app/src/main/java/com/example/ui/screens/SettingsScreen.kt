package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.ThemeMode
import com.example.ui.components.ConfirmActionDialog
import com.example.ui.components.TichuTopBar
import com.example.ui.theme.TichuTheme
import com.example.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
  viewModel: SettingsViewModel,
  onBack: () -> Unit,
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val colors = TichuTheme.colors
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  val presets = remember { listOf(300, 500, 750, 1000) }
  var isCustomSelected by remember { mutableStateOf(state.winThreshold !in presets) }
  var selectedPreset by remember { mutableStateOf(if (state.winThreshold in presets) state.winThreshold else 500) }
  var customInput by remember {
    mutableStateOf(if (state.winThreshold !in presets) state.winThreshold.toString() else "")
  }
  var pendingConfirmThreshold by remember { mutableStateOf<Int?>(null) }
  var pendingImportJson by remember { mutableStateOf<String?>(null) }

  LaunchedEffect(state.winThreshold) {
    if (state.winThreshold in presets) {
      isCustomSelected = false
      selectedPreset = state.winThreshold
      customInput = ""
    } else {
      isCustomSelected = true
      customInput = state.winThreshold.toString()
    }
  }

  val exportLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.CreateDocument("application/json"),
  ) { uri: Uri? ->
    if (uri == null) return@rememberLauncherForActivityResult
    scope.launch {
      runCatching {
        val json = viewModel.exportBackupJson()
        withContext(Dispatchers.IO) {
          context.contentResolver.openOutputStream(uri)?.use { stream ->
            stream.write(json.toByteArray(Charsets.UTF_8))
          } ?: error("Could not open destination file.")
        }
      }.onSuccess {
        viewModel.showFeedback("Backup exported successfully.")
      }.onFailure { err ->
        viewModel.showFeedback(err.message ?: "Export failed.", isError = true)
      }
    }
  }

  val importLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument(),
  ) { uri: Uri? ->
    if (uri == null) return@rememberLauncherForActivityResult
    scope.launch {
      runCatching {
        withContext(Dispatchers.IO) {
          context.contentResolver.openInputStream(uri)?.use { stream ->
            stream.bufferedReader(Charsets.UTF_8).readText()
          } ?: error("Could not read selected file.")
        }
      }.onSuccess { content ->
        pendingImportJson = content
      }.onFailure { err ->
        viewModel.showFeedback(err.message ?: "Could not read backup file.", isError = true)
      }
    }
  }

  if (pendingConfirmThreshold != null) {
    ConfirmActionDialog(
      title = "Change target mid-game?",
      message = "Change the target during this game? A team may reach the new target immediately.",
      confirmLabel = "Save target",
      onConfirm = {
        val target = pendingConfirmThreshold!!
        pendingConfirmThreshold = null
        viewModel.saveThreshold(target)
      },
      onDismiss = { pendingConfirmThreshold = null },
    )
  }

  if (pendingImportJson != null) {
    ConfirmActionDialog(
      title = "Restore backup?",
      message = "Importing a JSON backup replaces all current players, sessions, games, rounds, and settings.",
      confirmLabel = "Restore",
      isDanger = true,
      onConfirm = {
        val json = pendingImportJson!!
        pendingImportJson = null
        viewModel.importBackupJson(json)
      },
      onDismiss = { pendingImportJson = null },
    )
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(colors.bg),
  ) {
    TichuTopBar(title = "Settings", onBack = onBack)

    Column(
      modifier = Modifier
        .weight(1f)
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .padding(16.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Column(
        modifier = Modifier.widthIn(max = 600.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        // Appearance section
        Surface(
          shape = RoundedCornerShape(16.dp),
          color = colors.surface,
          border = BorderStroke(1.dp, colors.border),
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Text(
              text = "Appearance",
              style = MaterialTheme.typography.titleMedium,
              color = colors.text,
            )
            ThemeMode.entries.forEach { mode ->
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .heightIn(min = 48.dp)
                  .clip(RoundedCornerShape(10.dp))
                  .clickable { viewModel.setThemeMode(mode) }
                  .padding(horizontal = 6.dp)
                  .testTag("theme_option_${mode.key}"),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                RadioButton(
                  selected = state.themeMode == mode,
                  onClick = { viewModel.setThemeMode(mode) },
                  colors = RadioButtonDefaults.colors(
                    selectedColor = colors.brand,
                    unselectedColor = colors.textFaint,
                  ),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  text = mode.label,
                  style = MaterialTheme.typography.bodyLarge,
                  color = colors.text,
                )
              }
            }
          }
        }

        // Points to win section
        Surface(
          shape = RoundedCornerShape(16.dp),
          color = colors.surface,
          border = BorderStroke(1.dp, colors.border),
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Text(
              text = "Points to win",
              style = MaterialTheme.typography.titleMedium,
              color = colors.text,
            )

            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              presets.forEach { preset ->
                val selected = !isCustomSelected && selectedPreset == preset
                FilterChip(
                  selected = selected,
                  onClick = {
                    isCustomSelected = false
                    selectedPreset = preset
                    viewModel.consumeStatus()
                  },
                  label = { Text("$preset") },
                  colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = colors.brand,
                    selectedLabelColor = colors.onBrand,
                  ),
                  modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("threshold_preset_$preset"),
                )
              }
              FilterChip(
                selected = isCustomSelected,
                onClick = {
                  isCustomSelected = true
                  viewModel.consumeStatus()
                },
                label = { Text("Custom") },
                colors = FilterChipDefaults.filterChipColors(
                  selectedContainerColor = colors.brand,
                  selectedLabelColor = colors.onBrand,
                ),
                modifier = Modifier
                  .heightIn(min = 48.dp)
                  .testTag("threshold_preset_custom"),
              )
            }

            if (isCustomSelected) {
              OutlinedTextField(
                value = customInput,
                onValueChange = { customInput = it.filter { ch -> ch.isDigit() } },
                label = { Text("Custom target points") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("custom_threshold_input"),
              )
            }

            Button(
              onClick = {
                val target = if (isCustomSelected) customInput.toIntOrNull() ?: -1 else selectedPreset
                if (target <= 0) {
                  viewModel.saveThreshold(-1)
                } else if (state.hasActiveGameInProgress && target != state.winThreshold && !state.isThreeTeamWinnerPending) {
                  pendingConfirmThreshold = target
                } else {
                  viewModel.saveThreshold(target)
                }
              },
              colors = ButtonDefaults.buttonColors(
                containerColor = colors.brand,
                contentColor = colors.onBrand,
              ),
              modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag("save_threshold_button"),
            ) {
              Text("Save target", fontWeight = FontWeight.Bold)
            }
          }
        }

        // Backup & restore section
        Surface(
          shape = RoundedCornerShape(16.dp),
          color = colors.surface,
          border = BorderStroke(1.dp, colors.border),
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Text(
              text = "Backup & restore",
              style = MaterialTheme.typography.titleMedium,
              color = colors.text,
            )
            Text(
              text = "Export players, sessions, games, rounds, and settings to a JSON file or restore from an existing backup.",
              style = MaterialTheme.typography.bodyMedium,
              color = colors.textMuted,
            )
            OutlinedButton(
              onClick = { exportLauncher.launch("tichu-counter-backup.json") },
              border = BorderStroke(1.dp, colors.borderStrong),
              colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.brand),
              modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag("export_backup_button"),
            ) {
              Icon(Icons.Rounded.Upload, contentDescription = null)
              Spacer(modifier = Modifier.width(8.dp))
              Text("Export JSON backup", fontWeight = FontWeight.SemiBold)
            }
            OutlinedButton(
              onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) },
              border = BorderStroke(1.dp, colors.borderStrong),
              colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.text),
              modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag("import_backup_button"),
            ) {
              Icon(Icons.Rounded.Download, contentDescription = null)
              Spacer(modifier = Modifier.width(8.dp))
              Text("Import JSON backup", fontWeight = FontWeight.SemiBold)
            }
          }
        }

        val status = state.statusMessage
        if (!status.isNullOrBlank()) {
          Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (state.isStatusError) colors.danger.copy(alpha = 0.14f) else colors.brand.copy(alpha = 0.14f),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(
              text = status,
              style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
              color = if (state.isStatusError) colors.danger else colors.brand,
              modifier = Modifier
                .padding(14.dp)
                .testTag("settings_status_message"),
            )
          }
        }

        Spacer(modifier = Modifier.height(12.dp))
      }
    }
  }
}
