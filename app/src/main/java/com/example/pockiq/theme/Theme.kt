package com.example.pockiq.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary             = Emerald80,
    onPrimary           = DarkBg,
    primaryContainer    = DarkCard,
    onPrimaryContainer  = OnDarkPrimary,
    secondary           = Teal80,
    onSecondary         = DarkBg,
    secondaryContainer  = DarkElevated,
    onSecondaryContainer = OnDarkPrimary,
    tertiary            = OtherGold,
    background          = DarkBg,
    onBackground        = OnDarkPrimary,
    surface             = DarkSurface,
    onSurface           = OnDarkPrimary,
    surfaceVariant      = DarkCard,
    onSurfaceVariant    = OnDarkSecondary,
    error               = ExpenseRed,
    onError             = DarkBg
)

private val LightColorScheme = lightColorScheme(
    primary          = Emerald40,
    onPrimary        = Color.White,
    primaryContainer = Color(0xFFD0F5E8),
    secondary        = Teal40,
    background       = Color(0xFFF6FBF9),
    surface          = Color.White,
    error            = ExpenseRed
)

@Composable
fun PockiqTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography  = Typography,
        content     = content
    )
}

object WalletColors {
    val income  = IncomeGreen
    val expense = ExpenseRed
    val other   = OtherGold
}
