package com.javenissi.filologia

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import com.javenissi.filologia.ads.GerenciadorAnuncios
import com.javenissi.filologia.jogo.ContadorAnuncios
import com.javenissi.filologia.jogo.DatasetLoader
import com.javenissi.filologia.jogo.GeradorPerguntas
import com.javenissi.filologia.jogo.ModoJogo
import com.javenissi.filologia.jogo.Pergunta
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class Fase { INICIO, JOGANDO, RESULTADO }

data class EstadoUi(
    val fase: Fase = Fase.INICIO,
    val nivel: Int = 1,
    val modo: ModoJogo = ModoJogo.MISTO,
    val perguntas: List<Pergunta> = emptyList(),
    val indiceAtual: Int = 0,
    val respostaSelecionada: Int? = null,
    val acertos: Int = 0,
    val recorde: Int = 0,
    val novoRecorde: Boolean = false
) {
    val perguntaAtual: Pergunta? get() = perguntas.getOrNull(indiceAtual)
    val ultimaPergunta: Boolean get() = indiceAtual == perguntas.lastIndex
}

class QuizViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("filologia_quiz", Context.MODE_PRIVATE)

    private val gerador: GeradorPerguntas by lazy {
        val json = app.assets.open("palavras.json").bufferedReader().use { it.readText() }
        GeradorPerguntas(DatasetLoader.carregar(json))
    }

    private val contadorAnuncios = ContadorAnuncios(GerenciadorAnuncios.RESPOSTAS_POR_ANUNCIO)
    private var anuncioPendente = false

    private val _estado = MutableStateFlow(EstadoUi())
    val estado: StateFlow<EstadoUi> = _estado.asStateFlow()

    fun selecionarNivel(nivel: Int) = _estado.update { it.copy(nivel = nivel) }

    fun selecionarModo(modo: ModoJogo) = _estado.update { it.copy(modo = modo) }

    fun iniciarRodada() {
        val atual = _estado.value
        val perguntas = gerador.gerarRodada(atual.nivel, atual.modo, PERGUNTAS_POR_RODADA)
        _estado.update {
            it.copy(
                fase = Fase.JOGANDO,
                perguntas = perguntas,
                indiceAtual = 0,
                respostaSelecionada = null,
                acertos = 0,
                novoRecorde = false
            )
        }
    }

    fun responder(indice: Int) {
        val atual = _estado.value
        val pergunta = atual.perguntaAtual ?: return
        if (atual.respostaSelecionada != null) return
        val acertou = indice == pergunta.indiceCorreto
        _estado.update {
            it.copy(
                respostaSelecionada = indice,
                acertos = it.acertos + if (acertou) 1 else 0
            )
        }
        if (contadorAnuncios.registrarResposta()) anuncioPendente = true
    }

    /**
     * Avança para a próxima pergunta (ou para o resultado) e devolve true
     * quando um intersticial deve ser exibido neste momento.
     */
    fun avancar(): Boolean {
        val atual = _estado.value
        if (atual.respostaSelecionada == null) return false
        if (atual.ultimaPergunta) finalizarRodada() else {
            _estado.update { it.copy(indiceAtual = it.indiceAtual + 1, respostaSelecionada = null) }
        }
        val mostrar = anuncioPendente
        anuncioPendente = false
        return mostrar
    }

    private fun finalizarRodada() {
        val atual = _estado.value
        val chave = "recorde_${atual.nivel}_${atual.modo.name}"
        val recordeAnterior = prefs.getInt(chave, 0)
        val bateuRecorde = atual.acertos > recordeAnterior
        if (bateuRecorde) prefs.edit().putInt(chave, atual.acertos).apply()
        _estado.update {
            it.copy(
                fase = Fase.RESULTADO,
                recorde = maxOf(recordeAnterior, atual.acertos),
                novoRecorde = bateuRecorde
            )
        }
    }

    fun voltarAoInicio() = _estado.update { it.copy(fase = Fase.INICIO) }

    companion object {
        const val PERGUNTAS_POR_RODADA = 10
    }
}
