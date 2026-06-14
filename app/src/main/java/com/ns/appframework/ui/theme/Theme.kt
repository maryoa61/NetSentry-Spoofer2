package com.ns.appframework.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = CyberCyan,
    secondary = CyberPurple,
    tertiary = LightAccent,
    background = SpaceBg,
    surface = SpaceCard,
    onBackground = PureWhite,
    onSurface = SilverText,
    error = TechRed
  )

private val LightColorScheme = DarkColorScheme // Always enforce Dark themed IDE experience

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true, // Force dark
  dynamicColor: Boolean = false, // Always keep custom cyber design tokens consistent
  content: @Composable () -> Unit,
) {
  val colorScheme = DarkColorScheme

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
