package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.backup.PwaMigrationImporter
import com.example.ui.theme.TichuTheme

@Composable
fun PwaMigrationDialog(
  inlineError: String?,
  onClearError: () -> Unit,
  onPickJsonFile: () -> Unit,
  onSubmitJson: (String) -> Unit,
  onDismiss: () -> Unit,
) {
  val colors = TichuTheme.colors
  val context = LocalContext.current
  var pastedJson by remember { mutableStateOf("") }
  var copiedNotice by remember { mutableStateOf<String?>(null) }

  fun copyText(label: String, text: String, notice: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText(label, text))
    copiedNotice = notice
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = colors.surface,
    titleContentColor = colors.text,
    textContentColor = colors.text,
    title = {
      Text(
        text = "Migrate from PWA",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
      )
    },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Text(
          text = "1. Export from your browser",
          style = MaterialTheme.typography.labelLarge,
          color = colors.text,
        )
        Text(
          text = "Open Tichu Counter in your browser and run the script in DevTools Console (or save the Bookmarklet as a mobile bookmark and tap it on the PWA page) to download tichu-pwa-export.json and copy your data.",
          style = MaterialTheme.typography.bodySmall,
          color = colors.textMuted,
        )

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          OutlinedButton(
            onClick = {
              copyText(
                label = "Tichu PWA Export Script",
                text = PwaMigrationImporter.EXPORT_SCRIPT,
                notice = "Console export script copied.",
              )
            },
            border = BorderStroke(1.dp, colors.borderStrong),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.brand),
            modifier = Modifier
              .weight(1f)
              .heightIn(min = 48.dp)
              .testTag("copy_pwa_script_button"),
          ) {
            Icon(
              imageVector = Icons.Rounded.ContentCopy,
              contentDescription = null,
              modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("Script", maxLines = 1)
          }

          OutlinedButton(
            onClick = {
              copyText(
                label = "Tichu PWA Export Bookmarklet",
                text = PwaMigrationImporter.EXPORT_BOOKMARKLET,
                notice = "Mobile bookmarklet copied.",
              )
            },
            border = BorderStroke(1.dp, colors.borderStrong),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.text),
            modifier = Modifier
              .weight(1f)
              .heightIn(min = 48.dp)
              .testTag("copy_pwa_bookmarklet_button"),
          ) {
            Icon(
              imageVector = Icons.Rounded.BookmarkAdd,
              contentDescription = null,
              modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("Bookmarklet", maxLines = 1)
          }
        }

        if (copiedNotice != null) {
          Text(
            text = copiedNotice!!,
            style = MaterialTheme.typography.labelMedium,
            color = colors.brand,
          )
        }

        Text(
          text = "2. Import JSON into the app",
          style = MaterialTheme.typography.labelLarge,
          color = colors.text,
        )

        OutlinedButton(
          onClick = onPickJsonFile,
          border = BorderStroke(1.dp, colors.borderStrong),
          colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.brand),
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .testTag("pwa_pick_file_button"),
        ) {
          Icon(Icons.Rounded.FolderOpen, contentDescription = null)
          Spacer(modifier = Modifier.width(8.dp))
          Text("Choose tichu-pwa-export.json", fontWeight = FontWeight.SemiBold)
        }

        OutlinedTextField(
          value = pastedJson,
          onValueChange = {
            pastedJson = it
            if (inlineError != null) onClearError()
          },
          label = { Text("Or paste exported JSON") },
          placeholder = { Text("{\"tichuPlayers\": ..., \"tichuSessions\": ...}") },
          minLines = 3,
          maxLines = 5,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("pwa_paste_json_input"),
        )

        OutlinedButton(
          onClick = {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clipText = clipboard?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
            if (clipText.isNotBlank()) {
              pastedJson = clipText
              if (inlineError != null) onClearError()
            } else {
              copiedNotice = "Clipboard is empty."
            }
          },
          border = BorderStroke(1.dp, colors.border),
          colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.textMuted),
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .testTag("pwa_paste_clipboard_button"),
        ) {
          Icon(
            imageVector = Icons.Rounded.ContentPaste,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text("Paste from clipboard")
        }

        if (!inlineError.isNullOrBlank()) {
          Surface(
            shape = RoundedCornerShape(10.dp),
            color = colors.danger.copy(alpha = 0.14f),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(
              text = inlineError,
              style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
              color = colors.danger,
              modifier = Modifier
                .padding(10.dp)
                .testTag("pwa_migration_error"),
            )
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = { onSubmitJson(pastedJson) },
        enabled = pastedJson.isNotBlank(),
        colors = ButtonDefaults.buttonColors(
          containerColor = colors.brand,
          contentColor = colors.onBrand,
        ),
        modifier = Modifier
          .heightIn(min = 48.dp)
          .testTag("pwa_import_submit_button"),
      ) {
        Text("Review & import", fontWeight = FontWeight.Bold)
      }
    },
    dismissButton = {
      TextButton(
        onClick = onDismiss,
        modifier = Modifier.heightIn(min = 48.dp),
      ) {
        Text("Cancel")
      }
    },
  )
}
