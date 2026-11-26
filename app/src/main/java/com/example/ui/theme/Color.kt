package com.example.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// Light palette tokens (card-table emerald & warm ivory)
val LightBg = Color(0xFFF2F0E8)
val LightSurface = Color(0xFFFFFDF8)
val LightSurface2 = Color(0xFFF5F1E7)
val LightSurface3 = Color(0xFFE9E3D5)
val LightBorder = Color(0xFFE1DACB)
val LightBorderStrong = Color(0xFFC9BEAA)
val LightText = Color(0xFF202B26)
val LightTextMuted = Color(0xFF58675F)
val LightTextFaint = Color(0xFF7D8981)
val LightBrand = Color(0xFF18583F)
val LightBrandStrong = Color(0xFF104832)
val LightNavBg = Color(0xFF104832)
val LightOnBrand = Color(0xFFFFFFFF)
val LightTeamA = Color(0xFFA96313)
val LightTeamAFill = Color(0xFFD6942E)
val LightTeamASoft = Color(0x29D6942E)
val LightTeamB = Color(0xFF326C83)
val LightTeamBFill = Color(0xFF4B91A8)
val LightTeamBSoft = Color(0x294B91A8)
val LightTeamC = Color(0xFF80549A)
val LightTeamCFill = Color(0xFF80549A)
val LightTeamCSoft = Color(0x29905ABE)
val LightDanger = Color(0xFFBD4540)

// Dark palette tokens (deep tabletop baize & luminous jade)
val DarkBg = Color(0xFF111B16)
val DarkSurface = Color(0xFF1B2921)
val DarkSurface2 = Color(0xFF26372B)
val DarkSurface3 = Color(0xFF344639)
val DarkBorder = Color(0xFF405344)
val DarkBorderStrong = Color(0xFF647666)
val DarkText = Color(0xFFF4F0E5)
val DarkTextMuted = Color(0xFFC5CBBD)
val DarkTextFaint = Color(0xFFA4B0A1)
val DarkBrand = Color(0xFF73C59C)
val DarkBrandStrong = Color(0xFF90D6B1)
val DarkNavBg = Color(0xFF103C2D)
val DarkOnBrand = Color(0xFF10251A)
val DarkTeamA = Color(0xFFEFBD6B)
val DarkTeamAFill = Color(0xFFEFBD6B)
val DarkTeamASoft = Color(0x29EFBD6B)
val DarkTeamB = Color(0xFF8BC4D1)
val DarkTeamBFill = Color(0xFF8BC4D1)
val DarkTeamBSoft = Color(0x298BC4D1)
val DarkTeamC = Color(0xFFD3B2E2)
val DarkTeamCFill = Color(0xFFD3B2E2)
val DarkTeamCSoft = Color(0x29D3B2E2)
val DarkDanger = Color(0xFFF39B8F)

val NavForeground = Color(0xFFF6F3E9)

@Immutable
data class TichuSemanticColors(
  val bg: Color,
  val surface: Color,
  val surface2: Color,
  val surface3: Color,
  val border: Color,
  val borderStrong: Color,
  val text: Color,
  val textMuted: Color,
  val textFaint: Color,
  val brand: Color,
  val brandStrong: Color,
  val navBg: Color,
  val onBrand: Color,
  val teamA: Color,
  val teamAFill: Color,
  val teamASoft: Color,
  val teamB: Color,
  val teamBFill: Color,
  val teamBSoft: Color,
  val teamC: Color,
  val teamCFill: Color,
  val teamCSoft: Color,
  val danger: Color,
  val isDark: Boolean,
) {
  fun teamColor(index: Int): Color = when (index) {
    0 -> teamA
    1 -> teamB
    else -> teamC
  }

  fun teamFill(index: Int): Color = when (index) {
    0 -> teamAFill
    1 -> teamBFill
    else -> teamCFill
  }

  fun teamSoft(index: Int): Color = when (index) {
    0 -> teamASoft
    1 -> teamBSoft
    else -> teamCSoft
  }
}

val LightTichuColors = TichuSemanticColors(
  bg = LightBg,
  surface = LightSurface,
  surface2 = LightSurface2,
  surface3 = LightSurface3,
  border = LightBorder,
  borderStrong = LightBorderStrong,
  text = LightText,
  textMuted = LightTextMuted,
  textFaint = LightTextFaint,
  brand = LightBrand,
  brandStrong = LightBrandStrong,
  navBg = LightNavBg,
  onBrand = LightOnBrand,
  teamA = LightTeamA,
  teamAFill = LightTeamAFill,
  teamASoft = LightTeamASoft,
  teamB = LightTeamB,
  teamBFill = LightTeamBFill,
  teamBSoft = LightTeamBSoft,
  teamC = LightTeamC,
  teamCFill = LightTeamCFill,
  teamCSoft = LightTeamCSoft,
  danger = LightDanger,
  isDark = false,
)

val DarkTichuColors = TichuSemanticColors(
  bg = DarkBg,
  surface = DarkSurface,
  surface2 = DarkSurface2,
  surface3 = DarkSurface3,
  border = DarkBorder,
  borderStrong = DarkBorderStrong,
  text = DarkText,
  textMuted = DarkTextMuted,
  textFaint = DarkTextFaint,
  brand = DarkBrand,
  brandStrong = DarkBrandStrong,
  navBg = DarkNavBg,
  onBrand = DarkOnBrand,
  teamA = DarkTeamA,
  teamAFill = DarkTeamAFill,
  teamASoft = DarkTeamASoft,
  teamB = DarkTeamB,
  teamBFill = DarkTeamBFill,
  teamBSoft = DarkTeamBSoft,
  teamC = DarkTeamC,
  teamCFill = DarkTeamCFill,
  teamCSoft = DarkTeamCSoft,
  danger = DarkDanger,
  isDark = true,
)
