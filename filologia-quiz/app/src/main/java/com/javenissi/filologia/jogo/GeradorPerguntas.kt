package com.javenissi.filologia.jogo

import kotlin.random.Random

/**
 * Gera rodadas de perguntas a partir do dataset. Os distratores vêm sempre de
 * palavras do mesmo nível, para que as alternativas tenham dificuldade parecida.
 * Perguntas de etimologia só aparecem no modo MISTO, nos níveis 2 e 3.
 */
class GeradorPerguntas(
    dataset: List<Palavra>,
    private val random: Random = Random.Default
) {
    private val porNivel: Map<Int, List<Palavra>> = dataset.groupBy { it.nivel }

    fun gerarRodada(nivel: Int, modo: ModoJogo, quantidade: Int): List<Pergunta> {
        val pool = porNivel[nivel].orEmpty()
        require(pool.size >= OPCOES_POR_PERGUNTA) {
            "Nível $nivel tem só ${pool.size} palavras; mínimo é $OPCOES_POR_PERGUNTA"
        }
        return pool.shuffled(random)
            .take(quantidade.coerceAtMost(pool.size))
            .map { alvo -> gerarPergunta(alvo, pool, modo) }
    }

    private fun gerarPergunta(alvo: Palavra, pool: List<Palavra>, modo: ModoJogo): Pergunta {
        val tipo = when (modo) {
            ModoJogo.PALAVRA_PARA_SIGNIFICADO -> TipoPergunta.PALAVRA_PARA_SIGNIFICADO
            ModoJogo.SIGNIFICADO_PARA_PALAVRA -> TipoPergunta.SIGNIFICADO_PARA_PALAVRA
            ModoJogo.MISTO -> sortearTipo(alvo.nivel)
        }
        val distratores = pool.filter { it.palavra != alvo.palavra }
            .shuffled(random)
            .take(OPCOES_POR_PERGUNTA - 1)

        val (enunciado, textoCorreto, textosDistratores) = when (tipo) {
            TipoPergunta.PALAVRA_PARA_SIGNIFICADO -> Triple(
                "O que significa \"${alvo.palavra}\"?",
                alvo.significado,
                distratores.map { it.significado }
            )
            TipoPergunta.SIGNIFICADO_PARA_PALAVRA -> Triple(
                "Qual palavra significa: \"${alvo.significado}\"?",
                alvo.palavra,
                distratores.map { it.palavra }
            )
            TipoPergunta.ETIMOLOGIA -> Triple(
                "Qual é a origem da palavra \"${alvo.palavra}\"?",
                alvo.etimologia,
                distratores.map { it.etimologia }
            )
        }

        val opcoes = (textosDistratores + textoCorreto).shuffled(random)
        return Pergunta(
            tipo = tipo,
            enunciado = enunciado,
            opcoes = opcoes,
            indiceCorreto = opcoes.indexOf(textoCorreto),
            palavra = alvo
        )
    }

    private fun sortearTipo(nivel: Int): TipoPergunta {
        val tipos = if (nivel >= 2) {
            listOf(
                TipoPergunta.PALAVRA_PARA_SIGNIFICADO,
                TipoPergunta.SIGNIFICADO_PARA_PALAVRA,
                TipoPergunta.ETIMOLOGIA
            )
        } else {
            listOf(
                TipoPergunta.PALAVRA_PARA_SIGNIFICADO,
                TipoPergunta.SIGNIFICADO_PARA_PALAVRA
            )
        }
        return tipos.random(random)
    }

    companion object {
        const val OPCOES_POR_PERGUNTA = 4
    }
}
