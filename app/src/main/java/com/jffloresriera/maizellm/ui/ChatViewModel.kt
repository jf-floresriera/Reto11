package com.jffloresriera.maizellm.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jffloresriera.maizellm.data.InstallProgress
import com.jffloresriera.maizellm.data.ModelAssetInstaller
import com.jffloresriera.maizellm.data.ModelRepository
import com.jffloresriera.maizellm.domain.DoseGuardrail
import com.jffloresriera.maizellm.domain.Lang
import com.jffloresriera.maizellm.domain.Level
import com.jffloresriera.maizellm.domain.SystemPrompts
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class Autor { USUARIO, ASISTENTE }

data class Mensaje(
    val autor: Autor,
    val texto: String,
    val cifraSinVerificar: Boolean = false,
    val citaFuente: Boolean = false,
)

sealed interface EstadoModelo {
    data object Instalando : EstadoModelo
    data class InstalandoConProgreso(val porcentaje: Int) : EstadoModelo
    data object Cargando : EstadoModelo
    data object Listo : EstadoModelo
    data class Error(val mensaje: String) : EstadoModelo
}

data class ChatUiState(
    val estadoModelo: EstadoModelo = EstadoModelo.Instalando,
    val lang: Lang = Lang.ES,
    val level: Level = Level.AGRICULTOR,
    val mensajes: List<Mensaje> = emptyList(),
    val generandoRespuesta: Boolean = false,
    // Texto que se va revelando poco a poco una vez que el modelo ya terminó de
    // generar la respuesta completa (ver nota en enviarPregunta: no es streaming
    // real del modelo, es una animación de "tecleo" sobre la respuesta ya lista).
    val textoEnProgreso: String? = null,
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val installer = ModelAssetInstaller(application)
    private val repository = ModelRepository()

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    init {
        prepararModelo()
    }

    private fun prepararModelo() {
        viewModelScope.launch {
            try {
                installer.install().collect { progreso ->
                    when (progreso) {
                        is InstallProgress.Copying -> {
                            val pct = if (progreso.bytesTotales > 0) {
                                (progreso.bytesCopiados * 100 / progreso.bytesTotales).toInt()
                            } else 0
                            _uiState.update { it.copy(estadoModelo = EstadoModelo.InstalandoConProgreso(pct)) }
                        }
                        is InstallProgress.Listo -> {
                            _uiState.update { it.copy(estadoModelo = EstadoModelo.Cargando) }
                            repository.cargar(progreso.rutaModelo)
                            _uiState.update { it.copy(estadoModelo = EstadoModelo.Listo) }
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(estadoModelo = EstadoModelo.Error(e.message ?: "Error desconocido al preparar el modelo"))
                }
            }
        }
    }

    fun cambiarIdioma(lang: Lang) = _uiState.update { it.copy(lang = lang) }

    fun cambiarNivel(level: Level) = _uiState.update { it.copy(level = level) }

    fun enviarPregunta(pregunta: String) {
        if (pregunta.isBlank() || _uiState.value.estadoModelo != EstadoModelo.Listo) return

        val estadoActual = _uiState.value
        _uiState.update {
            it.copy(
                mensajes = it.mensajes + Mensaje(Autor.USUARIO, pregunta),
                generandoRespuesta = true,
            )
        }

        viewModelScope.launch {
            try {
                val systemPrompt = SystemPrompts.forAndroid(estadoActual.lang, estadoActual.level)
                // La librería gratuita no da streaming real del modelo: la respuesta
                // completa llega de una sola vez aquí. Lo que sigue (revelarEnPantalla)
                // es solo una animación visual sobre el texto ya generado.
                val resultado = repository.preguntar(systemPrompt, pregunta)
                revelarEnPantalla(resultado.text)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        mensajes = it.mensajes + Mensaje(
                            autor = Autor.ASISTENTE,
                            texto = "No pude generar una respuesta: ${e.message}",
                        ),
                        generandoRespuesta = false,
                        textoEnProgreso = null,
                    )
                }
            }
        }
    }

    /**
     * Simula el efecto de "tecleo" revelando el texto ya generado de a pocos
     * caracteres, en vez de mostrarlo todo de golpe. Al terminar, lo convierte
     * en un Mensaje definitivo con las señales de riesgo ya calculadas.
     */
    private suspend fun revelarEnPantalla(textoCompleto: String) {
        val tamanoBloque = 3
        var i = 0
        while (i < textoCompleto.length) {
            i = (i + tamanoBloque).coerceAtMost(textoCompleto.length)
            _uiState.update { it.copy(textoEnProgreso = textoCompleto.substring(0, i)) }
            delay(16)
        }
        _uiState.update {
            it.copy(
                mensajes = it.mensajes + Mensaje(
                    autor = Autor.ASISTENTE,
                    texto = textoCompleto,
                    cifraSinVerificar = DoseGuardrail.containsUnverifiedFigure(textoCompleto),
                    citaFuente = DoseGuardrail.citesSource(textoCompleto),
                ),
                generandoRespuesta = false,
                textoEnProgreso = null,
            )
        }
    }

    /** Texto plano de toda la conversación, para compartir o descargar. */
    fun construirTranscripcion(): String {
        val mensajes = _uiState.value.mensajes
        if (mensajes.isEmpty()) return "Todavía no hay conversación con Maíz IA."
        return buildString {
            appendLine("Conversación con Maíz IA")
            appendLine()
            mensajes.forEach { m ->
                appendLine(if (m.autor == Autor.USUARIO) "Tú:" else "Maíz IA:")
                appendLine(m.texto)
                appendLine()
            }
        }
    }

    override fun onCleared() {
        repository.liberar()
        super.onCleared()
    }
}
