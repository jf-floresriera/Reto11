package com.jffloresriera.maizellm.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jffloresriera.maizellm.domain.Lang
import com.jffloresriera.maizellm.domain.Level

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(viewModel: ChatViewModel = viewModel()) {
    val estado by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Maíz IA — sanidad del cultivo") }) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

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
            CircularProgressIndicator()
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
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        var idiomaExpandido by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = idiomaExpandido,
            onExpandedChange = { idiomaExpandido = it },
            modifier = Modifier.weight(1f),
        ) {
            TextField(
                readOnly = true,
                value = if (lang == Lang.ES) "Español" else "English",
                onValueChange = {},
                label = { Text("Idioma") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = idiomaExpandido) },
                // menuAnchor() sin argumentos: así se llama en el BOM fijado en build.gradle.kts.
                // Versiones más nuevas de Material3 la marcan "deprecated" a favor de
                // menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled) — si
                // Android Studio sugiere ese cambio al compilar, es seguro aceptarlo.
                modifier = Modifier.menuAnchor(),
            )
            ExposedDropdownMenu(
                expanded = idiomaExpandido,
                onDismissRequest = { idiomaExpandido = false },
            ) {
                Lang.entries.forEach { opcion ->
                    DropdownMenuItem(
                        text = { Text(if (opcion == Lang.ES) "Español" else "English") },
                        onClick = { onLangChange(opcion); idiomaExpandido = false },
                    )
                }
            }
        }

        var nivelExpandido by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = nivelExpandido,
            onExpandedChange = { nivelExpandido = it },
            modifier = Modifier.weight(1f),
        ) {
            TextField(
                readOnly = true,
                value = etiquetaNivel(level),
                onValueChange = {},
                label = { Text("Nivel") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = nivelExpandido) },
                modifier = Modifier.menuAnchor(),
            )
            ExposedDropdownMenu(
                expanded = nivelExpandido,
                onDismissRequest = { nivelExpandido = false },
            ) {
                Level.entries.forEach { opcion ->
                    DropdownMenuItem(
                        text = { Text(etiquetaNivel(opcion)) },
                        onClick = { onLevelChange(opcion); nivelExpandido = false },
                    )
                }
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
    onEnviar: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(mensajes) { mensaje -> BurbujaMensaje(mensaje) }
            if (generandoRespuesta) {
                item { Text("Escribiendo…", modifier = Modifier.padding(8.dp)) }
            }
        }

        var texto by rememberSaveable { mutableStateOf("") }
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextField(
                value = texto,
                onValueChange = { texto = it },
                label = { Text("Escribe tu pregunta sobre el cultivo") },
                modifier = Modifier.weight(1f),
            )
            Button(onClick = {
                if (texto.isNotBlank()) {
                    onEnviar(texto)
                    texto = ""
                }
            }) {
                Text("Enviar")
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
        Card(modifier = Modifier.widthIn(max = 320.dp)) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(mensaje.texto)
                if (!esUsuario && mensaje.cifraSinVerificar) {
                    Text(
                        "⚠️ Esta respuesta trae una cifra que no pudimos verificar. " +
                            "Consulta siempre la etiqueta registrada (ICA/EPA) antes de aplicar algo.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
    }
}
