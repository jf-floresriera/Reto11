package com.jffloresriera.maizellm.ui

import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jffloresriera.maizellm.R
import com.jffloresriera.maizellm.data.Tema
import com.jffloresriera.maizellm.domain.Lang
import com.jffloresriera.maizellm.domain.Level
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(viewModel: ChatViewModel = viewModel()) {
    val estado by viewModel.uiState.collectAsState()
    val contexto = LocalContext.current

    // Sonido corto cuando la respuesta queda lista (no es una notificación del
    // sistema, es un "beep" generado en el momento, no necesita archivos de audio).
    LaunchedEffect(Unit) {
        viewModel.eventosSonido.collect {
            val tono = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
            tono.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
        }
    }

    var menuAbierto by remember { mutableStateOf(false) }
    var dialogoAcercaDeAbierto by remember { mutableStateOf(false) }
    var dialogoConfigAbierto by remember { mutableStateOf(false) }

    if (dialogoAcercaDeAbierto) {
        DialogoAcercaDe(onCerrar = { dialogoAcercaDeAbierto = false })
    }
    if (dialogoConfigAbierto) {
        DialogoConfiguracion(
            mostrarMetricas = estado.mostrarMetricas,
            sonidoActivado = estado.sonidoActivado,
            tema = estado.tema,
            onMostrarMetricasChange = viewModel::cambiarMostrarMetricas,
            onSonidoChange = viewModel::cambiarSonido,
            onTemaChange = viewModel::cambiarTema,
            onCerrar = { dialogoConfigAbierto = false },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Agro IA", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_launcher_foreground),
                        contentDescription = null,
                        modifier = Modifier.padding(start = 12.dp).size(32.dp),
                        tint = Color.Unspecified,
                    )
                },
                actions = {
                    IconButton(onClick = { menuAbierto = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Más opciones")
                    }
                    DropdownMenu(expanded = menuAbierto, onDismissRequest = { menuAbierto = false }) {
                        DropdownMenuItem(
                            text = { Text("Acerca de") },
                            leadingIcon = { Icon(Icons.Filled.Info, contentDescription = null) },
                            onClick = { menuAbierto = false; dialogoAcercaDeAbierto = true },
                        )
                        DropdownMenuItem(
                            text = { Text("Configuración") },
                            leadingIcon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                            onClick = { menuAbierto = false; dialogoConfigAbierto = true },
                        )
                        DropdownMenuItem(
                            text = { Text("Compartir conversación") },
                            leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) },
                            onClick = {
                                menuAbierto = false
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, viewModel.construirTranscripcion())
                                }
                                contexto.startActivity(Intent.createChooser(intent, "Compartir conversación"))
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Borrar conversación") },
                            leadingIcon = { Icon(Icons.Filled.DeleteSweep, contentDescription = null) },
                            onClick = { menuAbierto = false; viewModel.borrarConversacion() },
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(), // empuja el contenido hacia arriba cuando el teclado está abierto
        ) {

            SelectorIdiomaYNivel(
                lang = estado.lang,
                level = estado.level,
                onLangChange = viewModel::cambiarIdioma,
                onLevelChange = viewModel::cambiarNivel,
            )

            when (val estadoModelo = estado.estadoModelo) {
                is EstadoModelo.Instalando ->
                    EstadoCargaCentral("Preparando el modelo por primera vez…")
                is EstadoModelo.InstalandoConProgreso ->
                    EstadoCargaCentral("Copiando el modelo… ${estadoModelo.porcentaje}%")
                is EstadoModelo.Cargando ->
                    EstadoCargaCentral("Cargando el modelo en memoria…")
                is EstadoModelo.Error ->
                    EstadoCargaCentral("No se pudo preparar el modelo: ${estadoModelo.mensaje}")
                is EstadoModelo.Listo ->
                    ConversacionLista(
                        mensajes = estado.mensajes,
                        generandoRespuesta = estado.generandoRespuesta,
                        textoEnProgreso = estado.textoEnProgreso,
                        mostrarMetricas = estado.mostrarMetricas,
                        onEnviar = viewModel::enviarPregunta,
                        modifier = Modifier.weight(1f),
                    )
            }
        }
    }
}

@Composable
private fun DialogoAcercaDe(onCerrar: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text("Acerca de Agro IA") },
        text = {
            Column {
                Text(
                    "Chatbot de sanidad del cultivo de maíz, con un modelo de lenguaje " +
                        "especializado que corre 100% en el celular, sin conexión a internet.",
                )
                Text(
                    "Modelo: Llama 3.2 1B + DAPT + SFT (Reto 11).",
                    modifier = Modifier.padding(top = 10.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "Desarrollador: Jesús Enrique Flores Riera",
                    modifier = Modifier.padding(top = 10.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "Estudiante de Geomática",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = { TextButton(onClick = onCerrar) { Text("Cerrar") } },
    )
}

@Composable
private fun DialogoConfiguracion(
    mostrarMetricas: Boolean,
    sonidoActivado: Boolean,
    tema: Tema,
    onMostrarMetricasChange: (Boolean) -> Unit,
    onSonidoChange: (Boolean) -> Unit,
    onTemaChange: (Tema) -> Unit,
    onCerrar: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text("Configuración") },
        text = {
            Column {
                FilaInterruptor(
                    etiqueta = "Mostrar métricas de generación",
                    activado = mostrarMetricas,
                    onCambio = onMostrarMetricasChange,
                )
                FilaInterruptor(
                    etiqueta = "Sonido al terminar la respuesta",
                    activado = sonidoActivado,
                    onCambio = onSonidoChange,
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                Text("Tema", style = MaterialTheme.typography.bodyMedium)
                FilaTema("Igual que el sistema", Tema.SISTEMA, tema, onTemaChange)
                FilaTema("Claro", Tema.CLARO, tema, onTemaChange)
                FilaTema("Oscuro", Tema.OSCURO, tema, onTemaChange)
            }
        },
        confirmButton = { TextButton(onClick = onCerrar) { Text("Cerrar") } },
    )
}

@Composable
private fun FilaInterruptor(etiqueta: String, activado: Boolean, onCambio: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(etiqueta, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = activado, onCheckedChange = onCambio)
    }
}

@Composable
private fun FilaTema(etiqueta: String, valor: Tema, seleccionado: Tema, onSeleccionar: (Tema) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = valor == seleccionado, onClick = { onSeleccionar(valor) })
        Text(etiqueta, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun EstadoCargaCentral(texto: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            Text(texto, modifier = Modifier.padding(top = 16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectorIdiomaYNivel(
    lang: Lang,
    level: Level,
    onLangChange: (Lang) -> Unit,
    onLevelChange: (Level) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SelectorDesplegable(
            etiquetaActual = if (lang == Lang.ES) "Español" else "English",
            opciones = Lang.entries.map { it to (if (it == Lang.ES) "Español" else "English") },
            onSeleccionar = onLangChange,
            modifier = Modifier.weight(1f),
        )
        SelectorDesplegable(
            etiquetaActual = etiquetaNivel(level),
            opciones = Level.entries.map { it to etiquetaNivel(it) },
            onSeleccionar = onLevelChange,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Selector simple con un botón que abre un DropdownMenu (API de nivel superior, estable,
 * sin la ambigüedad de scope de ExposedDropdownMenuBox). Genérico en T para reusarlo con
 * Lang y Level.
 */
@Composable
private fun <T> SelectorDesplegable(
    etiquetaActual: String,
    opciones: List<Pair<T, String>>,
    onSeleccionar: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expandido by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { expandido = true },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(etiquetaActual, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expandido, onDismissRequest = { expandido = false }) {
            opciones.forEach { (valor, etiqueta) ->
                DropdownMenuItem(
                    text = { Text(etiqueta) },
                    onClick = { onSeleccionar(valor); expandido = false },
                )
            }
        }
    }
}

private fun etiquetaNivel(level: Level): String = when (level) {
    Level.AGRICULTOR -> "Agricultor"
    Level.TECNICO -> "Técnico"
    Level.CIENTIFICO -> "Científico"
}

@Composable
private fun ConversacionLista(
    mensajes: List<Mensaje>,
    generandoRespuesta: Boolean,
    textoEnProgreso: String?,
    mostrarMetricas: Boolean,
    onEnviar: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    // Se desplaza solo hasta abajo cada vez que hay un mensaje nuevo o mientras
    // se va revelando la respuesta — así siempre se ve lo último, incluso con
    // el teclado abierto tapando parte de la pantalla.
    LaunchedEffect(mensajes.size, textoEnProgreso) {
        val total = mensajes.size + if (generandoRespuesta) 1 else 0
        if (total > 0) listState.animateScrollToItem(total - 1)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(mensajes) { mensaje -> BurbujaMensaje(mensaje, mostrarMetricas) }
            if (generandoRespuesta) {
                item {
                    if (textoEnProgreso.isNullOrEmpty()) {
                        BurbujaEscribiendo()
                    } else {
                        BurbujaTexto(textoEnProgreso)
                    }
                }
            }
        }

        var texto by rememberSaveable { mutableStateOf("") }
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 4.dp,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextField(
                    value = texto,
                    onValueChange = { texto = it },
                    placeholder = { Text("Escribe tu pregunta sobre el cultivo…") },
                    shape = RoundedCornerShape(24.dp),
                    colors = TextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                    ),
                    modifier = Modifier.weight(1f),
                )
                FilledIconButton(onClick = {
                    if (texto.isNotBlank()) {
                        onEnviar(texto)
                        texto = ""
                    }
                }) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Enviar")
                }
            }
        }
    }
}

@Composable
private fun BurbujaMensaje(mensaje: Mensaje, mostrarMetricas: Boolean) {
    val esUsuario = mensaje.autor == Autor.USUARIO
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (esUsuario) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Card(
            modifier = Modifier.widthIn(max = 320.dp),
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (esUsuario) 18.dp else 4.dp,
                bottomEnd = if (esUsuario) 4.dp else 18.dp,
            ),
            colors = CardDefaults.cardColors(
                containerColor = if (esUsuario) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.secondaryContainer
                },
            ),
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(
                    mensaje.texto,
                    color = if (esUsuario) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    },
                )
                if (!esUsuario && mensaje.referencias.isNotEmpty()) {
                    ListaReferencias(mensaje.referencias)
                }
                if (!esUsuario && mensaje.cifraSinVerificar) {
                    AvisoCifraSinVerificar()
                }
                if (!esUsuario && mostrarMetricas && mensaje.tokensGenerados != null) {
                    Text(
                        "${mensaje.tokensGenerados} tokens · " +
                            "%.1f tok/s · %.1f s".format(
                                mensaje.tokensPorSegundo ?: 0f,
                                (mensaje.tiempoGeneracionMs ?: 0L) / 1000f,
                            ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
    }
}

/** Muestra cada cita que el modelo incluyó como una fila separada (puede ser 1 o más). */
@Composable
private fun ListaReferencias(referencias: List<String>) {
    Column(modifier = Modifier.padding(top = 8.dp)) {
        referencias.forEach { referencia ->
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(
                    Icons.Filled.Link,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp).padding(top = 2.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    referencia,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

@Composable
private fun AvisoCifraSinVerificar() {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.padding(top = 8.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                Icons.Filled.WarningAmber,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(18.dp),
            )
            Text(
                "Cifra sin verificar. Consulta siempre la etiqueta registrada (ICA/EPA).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

/** Burbuja del asistente mientras se "revela" el texto (ver nota en ChatViewModel). */
@Composable
private fun BurbujaTexto(texto: String) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
        Card(
            modifier = Modifier.widthIn(max = 320.dp),
            shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 4.dp, bottomEnd = 18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        ) {
            Text(
                texto,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

// Datos y curiosidades sobre maíz/agronomía/ciencia, para que la espera (puede
// tardar bastante en CPU de celular) se sienta más entretenida que un simple
// "Escribiendo…". Van rotando con un efecto de desvanecido (Crossfade).
private val MENSAJES_ESPERA = listOf(
    "🌱 El maíz se domesticó hace más de 9.000 años en México.",
    "🔬 Este modelo se entrenó con más de 41.000 documentos científicos.",
    "🌾 Colombia tiene una enorme diversidad de maíces nativos.",
    "💡 Agro IA corre 100% en tu celular, sin necesitar internet.",
    "📚 La ciencia agrícola ayuda a alimentar a millones de personas.",
    "🧬 El maíz tiene uno de los genomas más grandes entre los cereales.",
    "🚜 El manejo integrado de plagas reduce el uso de agroquímicos.",
    "🎓 Cada pregunta que haces ayuda a entender mejor estas herramientas.",
)

@Composable
private fun BurbujaEscribiendo() {
    var indice by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(3200)
            indice = (indice + 1) % MENSAJES_ESPERA.size
        }
    }
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
        Card(
            modifier = Modifier.widthIn(max = 280.dp),
            shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 4.dp, bottomEnd = 18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Text(
                        "Generando respuesta…",
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Crossfade(targetState = indice, modifier = Modifier.padding(top = 6.dp)) { i ->
                    Text(
                        MENSAJES_ESPERA[i],
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}
