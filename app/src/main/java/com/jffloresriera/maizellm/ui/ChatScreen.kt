package com.jffloresriera.maizellm.ui

import android.content.Intent
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
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.jffloresriera.maizellm.domain.Lang
import com.jffloresriera.maizellm.domain.Level

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(viewModel: ChatViewModel = viewModel()) {
    val estado by viewModel.uiState.collectAsState()
    val contexto = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Maíz IA", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_launcher_foreground),
                        contentDescription = null,
                        modifier = Modifier.padding(start = 12.dp).size(32.dp),
                        tint = Color.Unspecified,
                    )
                },
                actions = {
                    IconButton(onClick = {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, viewModel.construirTranscripcion())
                        }
                        contexto.startActivity(Intent.createChooser(intent, "Compartir conversación"))
                    }) {
                        Icon(Icons.Filled.Share, contentDescription = "Compartir conversación")
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
                        onEnviar = viewModel::enviarPregunta,
                        modifier = Modifier.weight(1f),
                    )
            }
        }
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
            items(mensajes) { mensaje -> BurbujaMensaje(mensaje) }
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
private fun BurbujaMensaje(mensaje: Mensaje) {
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
                if (!esUsuario && mensaje.cifraSinVerificar) {
                    AvisoCifraSinVerificar()
                }
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

@Composable
private fun BurbujaEscribiendo() {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
        Card(
            shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 4.dp, bottomEnd = 18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        ) {
            Text(
                "Escribiendo…",
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}
