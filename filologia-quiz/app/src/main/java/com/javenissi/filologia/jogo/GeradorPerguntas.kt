package com.javenissi.filologia.jogo

import kotlin.random.Random

/**
 * Gera rodadas de perguntas a partir do dataset. O foco do jogo é semântica:
 * significado nas duas direções, sinônimos e sentido em contexto (completar a
 * frase). Etimologia entra apenas no modo COMPLETO (junto com os tipos
 * semânticos) e no modo dedicado ETIMOLOGIA.
 *
 * Os distratores vêm sempre de palavras do mesmo nível e passam por um filtro
 * que descarta palavras semanticamente próximas do alvo (sinônimos cruzados),
 * para que só exista uma resposta correta.
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
            ModoJogo.ETIMOLOGIA -> TipoPergunta.ETIMOLOGIA
            ModoJogo.MISTO -> sortearTipo(alvo, pool, comEtimologia = false)
            ModoJogo.COMPLETO -> sortearTipo(alvo, pool, comEtimologia = true)
        }
        val distratores = pool.filter { semanticamenteDistinta(alvo, it) }
            .shuffled(random)
            .take(OPCOES_POR_PERGUNTA - 1)

        val (enunciado, textoCorreto, textosDistratores) = when (tipo) {
            TipoPergunta.PALAVRA_PARA_SIGNIFICADO -> Triple(
                "Qual é o sentido de \"${alvo.palavra}\"?",
                alvo.significado,
                distratores.map { it.significado }
            )
            TipoPergunta.SIGNIFICADO_PARA_PALAVRA -> Triple(
                "Qual palavra tem o sentido de: \"${alvo.significado}\"?",
                alvo.palavra,
                distratores.map { it.palavra }
            )
            TipoPergunta.SINONIMO -> Triple(
                "Qual palavra é sinônimo de \"${alvo.palavra}\"?",
                alvo.sinonimos.random(random),
                distratores.map { it.palavra }
            )
            TipoPergunta.COMPLETAR_FRASE -> Triple(
                "Qual palavra completa a frase?\n\n${alvo.frase}",
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

    private fun sortearTipo(alvo: Palavra, pool: List<Palavra>, comEtimologia: Boolean): TipoPergunta {
        val tipos = buildList {
            add(TipoPergunta.PALAVRA_PARA_SIGNIFICADO)
            add(TipoPergunta.SIGNIFICADO_PARA_PALAVRA)
            if (alvo.frase.isNotBlank()) add(TipoPergunta.COMPLETAR_FRASE)
            if (alvo.sinonimos.isNotEmpty() &&
                pool.count { semanticamenteDistinta(alvo, it) } >= OPCOES_POR_PERGUNTA - 1
            ) {
                add(TipoPergunta.SINONIMO)
            }
            if (comEtimologia) add(TipoPergunta.ETIMOLOGIA)
        }
        return tipos.random(random)
    }

    /**
     * Descarta como distrator qualquer palavra próxima demais do alvo: a
     * própria, uma que seja sinônimo dela (em qualquer direção) ou que
     * compartilhe sinônimos com ela.
     */
    private fun semanticamenteDistinta(alvo: Palavra, outra: Palavra): Boolean =
        !outra.palavra.equals(alvo.palavra, ignoreCase = true) &&
            alvo.sinonimos.none { it.equals(outra.palavra, ignoreCase = true) } &&
            outra.sinonimos.none { it.equals(alvo.palavra, ignoreCase = true) } &&
            outra.sinonimos.none { s -> alvo.sinonimos.any { it.equals(s, ignoreCase = true) } }

    companion object {
        const val OPCOES_POR_PERGUNTA = 4
    }
}
