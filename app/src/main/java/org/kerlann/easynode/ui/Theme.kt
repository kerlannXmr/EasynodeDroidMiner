package org.kerlann.easynode.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val XmrOrange = Color(0xFFF26822)
val Ok = Color(0xFF5FAF5F)       // SUCCESS du script
val Warn = Color(0xFFD7D75F)     // WARNING
val Err = Color(0xFFD75F5F)      // ERROR
val Muted = Color(0xFF8A8A8A)    // GRIS

private val Scheme = darkColorScheme(
    primary = XmrOrange,
    onPrimary = Color.Black,
    secondary = Color(0xFF5FAFD7),
    background = Color(0xFF141414),
    onBackground = Color(0xFFEDEDED),
    surface = Color(0xFF141414),
    onSurface = Color(0xFFEDEDED),
    surfaceVariant = Color(0xFF242424),
    onSurfaceVariant = Color(0xFFBDBDBD),
    surfaceContainer = Color(0xFF1C1C1C),
    error = Err,
)

@Composable
fun EasyNodeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}
