package com.javenissi.filologia.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Paleta "pergaminho e tinta", em clima de biblioteca antiga.
private val EsquemaClaro = lightColorScheme(
    primary = Color(0xFF6D4C41),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE8D8C3),
    onPrimaryContainer = Color(0xFF3E2F23),
    secondary = Color(0xFF7A5C3E),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF0E2CB),
    onSecondaryContainer = Color(0xFF43301C),
    background = Color(0xFFFAF3E3),
    onBackground = Color(0xFF2E2418),
    surface = Color(0xFFFFFBF2),
    onSurface = Color(0xFF2E2418),
    surfaceVariant = Color(0xFFF0E5D0),
    onSurfaceVariant = Color(0xFF4E4232),
    outline = Color(0xFF8A7A61),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF)
)

private val EsquemaEscuro = darkColorScheme(
    primary = Color(0xFFD7B894),
    onPrimary = Color(0xFF3E2F23),
    primaryContainer = Color(0xFF55422F),
    onPrimaryContainer = Color(0xFFF2E2C9),
    secondary = Color(0xFFC9AC85),
    onSecondary = Color(0xFF3A2B18),
    secondaryContainer = Color(0xFF4C3A24),
    onSecondaryContainer = Color(0xFFEEDDC2),
    background = Color(0xFF1E1810),
    onBackground = Color(0xFFEDE2CF),
    surface = Color(0xFF2A2318),
    onSurface = Color(0xFFEDE2CF),
    surfaceVariant = Color(0xFF3A3223),
    onSurfaceVariant = Color(0xFFCFC2A8),
    outline = Color(0xFF9A8B70),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410)
)

val VerdeAcerto = Color(0xFF2E7D32)
val VerdeAcertoContainer = Color(0xFFC8E6C9)
val VermelhoErro = Color(0xFFC62828)
val VermelhoErroContainer = Color(0xFFFFCDD2)

@Composable
fun FilologiaTheme(
    temaEscuro: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (temaEscuro) EsquemaEscuro else EsquemaClaro,
        content = content
    )
}
