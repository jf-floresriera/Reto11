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
                val resultado = repository.preguntar(systemPrompt, pregunta)
                _uiState.update {
                    it.copy(
                        mensajes = it.mensajes + Mensaje(
                            autor = Autor.ASISTENTE,
                            texto = resultado.text,
                            cifraSinVerificar = DoseGuardrail.containsUnverifiedFigure(resultado.text),
                            citaFuente = DoseGuardrail.citesSource(resultado.text),
                        ),
                        generandoRespuesta = false,
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        mensajes = it.mensajes + Mensaje(
                            autor = Autor.ASISTENTE,
                            texto = "No pude generar una respuesta: ${e.message}",
                        ),
                        generandoRespuesta = false,
                    )
                }
            }
        }
    }

    override fun onCleared() {
        repository.liberar()
        super.onCleared()
    }
}
