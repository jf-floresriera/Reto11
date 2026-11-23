package com.jffloresriera.maizellm.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// ⚠️ PLACEHOLDER: tema neutro de Material 3 por defecto. Pendiente de reemplazar por el
// estilo de referencia ("Gulupa") en cuanto el usuario comparta cómo se ve esa app
// (captura de pantalla, código o descripción). Cambiar solo este archivo no afecta el
// resto de la app: toda la UI consume MaterialTheme.colorScheme, no colores sueltos.

private val EsquemaClaro = lightColorScheme()
private val EsquemaOscuro = darkColorScheme()

@Composable
fun MaizeLlmTheme(
    useDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (useDarkTheme) EsquemaOscuro else EsquemaClaro
    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
