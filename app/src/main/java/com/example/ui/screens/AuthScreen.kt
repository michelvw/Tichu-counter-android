package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Login
import androidx.compose.material.icons.rounded.PersonOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.data.auth.GoogleAuthHelper
import com.example.ui.theme.TichuTheme

@Composable
fun AuthScreen(
  onAuthSuccess: () -> Unit,
  onContinueAsGuest: () -> Unit = {},
) {
  val colors = TichuTheme.colors
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val credentialManager = remember(context) { CredentialManager.create(context) }

  var isLoading by remember { mutableStateOf(false) }
  var errorMessage by remember { mutableStateOf<String?>(null) }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(colors.bg)
      .windowInsetsPadding(WindowInsets.systemBars),
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 20.dp, vertical = 20.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .widthIn(max = 520.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
      // Hero banner + app introduction
      Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.border),
      ) {
        Column(modifier = Modifier.fillMaxWidth()) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(176.dp),
          ) {
            Image(
              painter = painterResource(id = R.drawable.img_welcome_hero),
              contentDescription = "Tichu playing cards on an emerald tabletop",
              contentScale = ContentScale.Crop,
              modifier = Modifier.fillMaxSize(),
            )
            Box(
              modifier = Modifier
                .fillMaxSize()
                .background(
                  Brush.verticalGradient(
                    colors = listOf(
                      Color.Black.copy(alpha = 0.08f),
                      Color.Black.copy(alpha = 0.72f),
                    ),
                  ),
                ),
            )
            Column(
              modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 20.dp, vertical = 16.dp),
              verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              Text(
                text = "Tichu Counter",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                color = Color.White,
              )
              Text(
                text = "Fast tabletop scoring, session rotations & player stats for Tichu nights.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.90f),
              )
            }
          }

          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Text(
              text = "WHAT'S INSIDE",
              style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
              color = colors.textFaint,
            )

            FeatureHighlightRow(
              icon = Icons.Rounded.Casino,
              accent = colors.brand,
              title = "Effortless round scoring",
              subtitle = "Auto-balancing 100-point tricks with one-tap Tichu, Grand Tichu, and Double Win calls.",
            )
            FeatureHighlightRow(
              icon = Icons.Rounded.Groups,
              accent = colors.teamA,
              title = "Built for 4, 5 & 6 players",
              subtitle = "Play quick games or host multi-game evenings with fixed teams, 5-player rotation, and 3-team modes.",
            )
            FeatureHighlightRow(
              icon = Icons.Rounded.EmojiEvents,
              accent = colors.teamB,
              title = "Leaderboards & career stats",
              subtitle = "Track win rates, point averages, and special-call records—or import backups from the web app.",
            )
            FeatureHighlightRow(
              icon = Icons.Rounded.CloudSync,
              accent = colors.teamC,
              title = "Sync or stay offline",
              subtitle = "Sign in to back up and share live scores with friends, or jump straight in offline as a guest.",
            )
          }
        }
      }

      // Sign-in / Guest choice card
      Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.border),
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Text(
            text = "Choose how to get started",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = colors.text,
            textAlign = TextAlign.Center,
          )

          if (!errorMessage.isNullOrBlank()) {
            Surface(
              shape = RoundedCornerShape(12.dp),
              color = colors.danger.copy(alpha = 0.12f),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text(
                text = errorMessage.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.danger,
                modifier = Modifier
                  .padding(12.dp)
                  .testTag("auth_error_message"),
              )
            }
          }

          Button(
            onClick = {
              errorMessage = null
              isLoading = true
              GoogleAuthHelper.onGoogleSignInClicked(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = {
                  isLoading = false
                  onAuthSuccess()
                },
                onAuthError = { msg ->
                  isLoading = false
                  errorMessage = msg
                },
                scope = scope,
                onAuthCancelled = {
                  isLoading = false
                },
              )
            },
            enabled = !isLoading,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
              containerColor = colors.brand,
              contentColor = colors.onBrand,
            ),
            modifier = Modifier
              .fillMaxWidth()
              .heightIn(min = 52.dp)
              .testTag("google_sign_in_button"),
          ) {
            if (isLoading) {
              CircularProgressIndicator(
                color = colors.onBrand,
                strokeWidth = 2.dp,
                modifier = Modifier.size(22.dp),
              )
              Spacer(modifier = Modifier.width(10.dp))
              Text("Signing in...", fontWeight = FontWeight.Bold)
            } else {
              Icon(Icons.Rounded.Login, contentDescription = null)
              Spacer(modifier = Modifier.width(8.dp))
              Text("Sign in with Google", fontWeight = FontWeight.Bold)
            }
          }

          OutlinedButton(
            onClick = onContinueAsGuest,
            enabled = !isLoading,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, colors.borderStrong),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.text),
            modifier = Modifier
              .fillMaxWidth()
              .heightIn(min = 48.dp)
              .testTag("continue_as_guest_button"),
          ) {
            Icon(Icons.Rounded.PersonOutline, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Continue as guest", fontWeight = FontWeight.SemiBold)
          }

          Text(
            text = "Guest mode saves games on this device only. You can sign in from Settings anytime to back up and sync.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textMuted,
            textAlign = TextAlign.Center,
          )
        }
      }
      }
    }
  }
}

@Composable
private fun FeatureHighlightRow(
  icon: ImageVector,
  accent: Color,
  title: String,
  subtitle: String,
) {
  val colors = TichuTheme.colors
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.Top,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Box(
      modifier = Modifier
        .size(36.dp)
        .clip(CircleShape)
        .background(accent.copy(alpha = 0.14f)),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = accent,
        modifier = Modifier.size(18.dp),
      )
    }
    Column(
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      Text(
        text = title,
        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
        color = colors.text,
      )
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = colors.textMuted,
      )
    }
  }
}
