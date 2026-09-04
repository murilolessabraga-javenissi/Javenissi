package com.javenissi.filologia.jogo

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class JogoTest {

    private val dataset: List<Palavra> by lazy {
        // Testes unitários do Android rodam com o diretório do módulo como cwd.
        val json = java.io.File("src/main/assets/palavras.json").readText()
        DatasetLoader.carregar(json)
    }

    private fun respostaEsperada(p: Pergunta): String = when (p.tipo) {
        TipoPergunta.PALAVRA_PARA_SIGNIFICADO -> p.palavra.significado
        TipoPergunta.SIGNIFICADO_PARA_PALAVRA -> p.palavra.palavra
        TipoPergunta.COMPLETAR_FRASE -> p.palavra.palavra
        TipoPergunta.ETIMOLOGIA -> p.palavra.etimologia
        TipoPergunta.SINONIMO -> p.opcoes[p.indiceCorreto].also { correta ->
            assertTrue(correta in p.palavra.sinonimos,
                "Resposta de sinônimo '$correta' não está em ${p.palavra.sinonimos}")
        }
    }

    @Test
    fun `dataset carrega e passa na validacao`() {
        assertTrue(dataset.size >= 90, "Esperava >= 90 palavras, veio ${dataset.size}")
        for (nivel in 1..3) {
            assertTrue(dataset.count { it.nivel == nivel } >= 30, "Nível $nivel com poucas palavras")
        }
        dataset.forEach { p ->
            assertTrue(p.frase.contains(DatasetLoader.LACUNA), "'${p.palavra}' sem frase de contexto")
        }
    }

    @Test
    fun `significados e etimologias sao unicos por nivel`() {
        for (nivel in 1..3) {
            val doNivel = dataset.filter { it.nivel == nivel }
            assertEquals(doNivel.size, doNivel.map { it.significado }.toSet().size,
                "Significados repetidos no nível $nivel")
            assertEquals(doNivel.size, doNivel.map { it.etimologia }.toSet().size,
                "Etimologias repetidas no nível $nivel")
        }
    }

    @Test
    fun `rodada tem a quantidade pedida e respostas corretas`() {
        val gerador = GeradorPerguntas(dataset, Random(42))
        for (nivel in 1..3) {
            for (modo in ModoJogo.entries) {
                val rodada = gerador.gerarRodada(nivel, modo, 10)
                assertEquals(10, rodada.size)
                rodada.forEach { p ->
                    assertEquals(4, p.opcoes.size, "Pergunta sem 4 opções: ${p.enunciado}")
                    assertEquals(4, p.opcoes.toSet().size, "Opções repetidas: ${p.enunciado}")
                    assertTrue(p.indiceCorreto in 0..3)
                    assertEquals(respostaEsperada(p), p.opcoes[p.indiceCorreto])
                    assertEquals(nivel, p.palavra.nivel)
                }
                assertEquals(10, rodada.map { it.palavra.palavra }.toSet().size,
                    "Palavra repetida na mesma rodada")
            }
        }
    }

    @Test
    fun `modo fixo gera apenas o tipo escolhido`() {
        val gerador = GeradorPerguntas(dataset, Random(7))
        assertTrue(gerador.gerarRodada(2, ModoJogo.PALAVRA_PARA_SIGNIFICADO, 10)
            .all { it.tipo == TipoPergunta.PALAVRA_PARA_SIGNIFICADO })
        assertTrue(gerador.gerarRodada(2, ModoJogo.SIGNIFICADO_PARA_PALAVRA, 10)
            .all { it.tipo == TipoPergunta.SIGNIFICADO_PARA_PALAVRA })
        assertTrue(gerador.gerarRodada(2, ModoJogo.ETIMOLOGIA, 10)
            .all { it.tipo == TipoPergunta.ETIMOLOGIA })
    }

    @Test
    fun `modo misto e semantico, sem etimologia e com todos os tipos semanticos`() {
        val gerador = GeradorPerguntas(dataset, Random(1))
        for (nivel in 1..3) {
            val tipos = (1..30).flatMap {
                gerador.gerarRodada(nivel, ModoJogo.MISTO, 10).map { it.tipo }
            }.toSet()
            assertFalse(TipoPergunta.ETIMOLOGIA in tipos,
                "Etimologia apareceu no modo misto, nível $nivel")
            assertTrue(TipoPergunta.SINONIMO in tipos, "Sem perguntas de sinônimo no nível $nivel")
            assertTrue(TipoPergunta.COMPLETAR_FRASE in tipos, "Sem completar frase no nível $nivel")
            assertTrue(TipoPergunta.PALAVRA_PARA_SIGNIFICADO in tipos)
            assertTrue(TipoPergunta.SIGNIFICADO_PARA_PALAVRA in tipos)
        }
    }

    @Test
    fun `modo completo inclui etimologia junto com os tipos semanticos`() {
        val gerador = GeradorPerguntas(dataset, Random(5))
        for (nivel in 1..3) {
            val tipos = (1..30).flatMap {
                gerador.gerarRodada(nivel, ModoJogo.COMPLETO, 10).map { it.tipo }
            }.toSet()
            assertEquals(TipoPergunta.entries.toSet(), tipos,
                "Modo completo deveria sortear todos os tipos no nível $nivel, veio $tipos")
        }
    }

    @Test
    fun `distratores nunca sao semanticamente proximos do alvo`() {
        val gerador = GeradorPerguntas(dataset, Random(3))
        val porPalavra = dataset.associateBy { it.palavra }
        repeat(30) {
            for (nivel in 1..3) {
                gerador.gerarRodada(nivel, ModoJogo.MISTO, 10).forEach { p ->
                    if (p.tipo == TipoPergunta.SINONIMO || p.tipo == TipoPergunta.COMPLETAR_FRASE ||
                        p.tipo == TipoPergunta.SIGNIFICADO_PARA_PALAVRA
                    ) {
                        p.opcoes.filterIndexed { i, _ -> i != p.indiceCorreto }.forEach { opcao ->
                            val distrator = porPalavra[opcao]
                            if (distrator != null) {
                                assertFalse(opcao in p.palavra.sinonimos,
                                    "Distrator '$opcao' é sinônimo do alvo '${p.palavra.palavra}'")
                                assertFalse(p.palavra.palavra in distrator.sinonimos,
                                    "Alvo '${p.palavra.palavra}' é sinônimo do distrator '$opcao'")
                                assertFalse(distrator.sinonimos.any { it in p.palavra.sinonimos },
                                    "'$opcao' e '${p.palavra.palavra}' compartilham sinônimos")
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `contador dispara anuncio a cada N respostas`() {
        val contador = ContadorAnuncios(5)
        val disparos = (1..17).map { contador.registrarResposta() }
        assertEquals(listOf(5, 10, 15), disparos.withIndex().filter { it.value }.map { it.index + 1 })
        assertEquals(17, contador.totalRespostas)
    }

    @Test
    fun `rodadas sucessivas variam as perguntas`() {
        val gerador = GeradorPerguntas(dataset, Random(99))
        val a = gerador.gerarRodada(1, ModoJogo.MISTO, 10).map { it.palavra.palavra }
        val b = gerador.gerarRodada(1, ModoJogo.MISTO, 10).map { it.palavra.palavra }
        assertTrue(a != b, "Duas rodadas idênticas em sequência")
    }
}
