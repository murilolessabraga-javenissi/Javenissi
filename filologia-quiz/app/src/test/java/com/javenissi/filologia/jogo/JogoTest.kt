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
    fun `toda area e nivel sustentam uma rodada inteira`() {
        Area.entries.forEach { area ->
            for (nivel in 1..3) {
                val n = dataset.count { it.area == area && it.nivel == nivel }
                assertTrue(n >= DatasetLoader.MINIMO_POR_NIVEL,
                    "${area.rotulo} nível $nivel tem só $n palavras")
            }
        }
        dataset.forEach { p ->
            assertTrue(p.frase.contains(DatasetLoader.LACUNA), "'${p.palavra}' sem frase de contexto")
        }
    }

    @Test
    fun `significados e etimologias sao unicos dentro de cada area e nivel`() {
        Area.entries.forEach { area ->
            for (nivel in 1..3) {
                val grupo = dataset.filter { it.area == area && it.nivel == nivel }
                assertEquals(grupo.size, grupo.map { it.significado }.toSet().size,
                    "Significados repetidos em ${area.rotulo} nível $nivel")
                assertEquals(grupo.size, grupo.map { it.etimologia }.toSet().size,
                    "Etimologias repetidas em ${area.rotulo} nível $nivel")
            }
        }
    }

    @Test
    fun `rodada tem a quantidade pedida e respostas corretas em toda area`() {
        val gerador = GeradorPerguntas(dataset, Random(42))
        Area.entries.forEach { area ->
            for (nivel in 1..3) {
                for (modo in ModoJogo.entries) {
                    val rodada = gerador.gerarRodada(area, nivel, modo, 10)
                    assertEquals(10, rodada.size, "${area.rotulo} nível $nivel modo $modo")
                    rodada.forEach { p ->
                        assertEquals(4, p.opcoes.size, "Pergunta sem 4 opções: ${p.enunciado}")
                        assertEquals(4, p.opcoes.toSet().size, "Opções repetidas: ${p.enunciado}")
                        assertTrue(p.indiceCorreto in 0..3)
                        assertEquals(respostaEsperada(p), p.opcoes[p.indiceCorreto])
                        assertEquals(nivel, p.palavra.nivel)
                        assertEquals(area, p.palavra.area, "Palavra de outra área na rodada")
                    }
                    assertEquals(10, rodada.map { it.palavra.palavra }.toSet().size,
                        "Palavra repetida na mesma rodada")
                }
            }
        }
    }

    @Test
    fun `perguntas nunca misturam vocabulario de areas diferentes`() {
        val gerador = GeradorPerguntas(dataset, Random(11))
        val textosPorArea = Area.entries.associateWith { area ->
            dataset.filter { it.area == area }
                .flatMap { listOf(it.palavra, it.significado, it.etimologia) }
                .toSet()
        }
        Area.entries.forEach { area ->
            for (nivel in 1..3) {
                gerador.gerarRodada(area, nivel, ModoJogo.COMPLETO, 10).forEach { p ->
                    p.opcoes.forEach { opcao ->
                        val deOutraArea = Area.entries.any { outra ->
                            outra != area &&
                                opcao in textosPorArea.getValue(outra) &&
                                opcao !in textosPorArea.getValue(area)
                        }
                        assertFalse(deOutraArea,
                            "Opção '$opcao' veio de fora da área ${area.rotulo}")
                    }
                }
            }
        }
    }

    @Test
    fun `modo fixo gera apenas o tipo escolhido`() {
        val gerador = GeradorPerguntas(dataset, Random(7))
        Area.entries.forEach { area ->
            assertTrue(gerador.gerarRodada(area, 2, ModoJogo.PALAVRA_PARA_SIGNIFICADO, 10)
                .all { it.tipo == TipoPergunta.PALAVRA_PARA_SIGNIFICADO })
            assertTrue(gerador.gerarRodada(area, 2, ModoJogo.SIGNIFICADO_PARA_PALAVRA, 10)
                .all { it.tipo == TipoPergunta.SIGNIFICADO_PARA_PALAVRA })
            assertTrue(gerador.gerarRodada(area, 2, ModoJogo.ETIMOLOGIA, 10)
                .all { it.tipo == TipoPergunta.ETIMOLOGIA })
        }
    }

    @Test
    fun `modo misto e semantico, sem etimologia e com todos os tipos semanticos`() {
        val gerador = GeradorPerguntas(dataset, Random(1))
        Area.entries.forEach { area ->
            for (nivel in 1..3) {
                val tipos = (1..40).flatMap {
                    gerador.gerarRodada(area, nivel, ModoJogo.MISTO, 10).map { it.tipo }
                }.toSet()
                assertFalse(TipoPergunta.ETIMOLOGIA in tipos,
                    "Etimologia apareceu no modo misto (${area.rotulo}, nível $nivel)")
                assertTrue(TipoPergunta.SINONIMO in tipos,
                    "Sem perguntas de sinônimo em ${area.rotulo} nível $nivel")
                assertTrue(TipoPergunta.COMPLETAR_FRASE in tipos,
                    "Sem completar frase em ${area.rotulo} nível $nivel")
                assertTrue(TipoPergunta.PALAVRA_PARA_SIGNIFICADO in tipos)
                assertTrue(TipoPergunta.SIGNIFICADO_PARA_PALAVRA in tipos)
            }
        }
    }

    @Test
    fun `modo completo inclui etimologia junto com os tipos semanticos`() {
        val gerador = GeradorPerguntas(dataset, Random(5))
        Area.entries.forEach { area ->
            for (nivel in 1..3) {
                val tipos = (1..40).flatMap {
                    gerador.gerarRodada(area, nivel, ModoJogo.COMPLETO, 10).map { it.tipo }
                }.toSet()
                assertEquals(TipoPergunta.entries.toSet(), tipos,
                    "Modo completo não cobriu todos os tipos em ${area.rotulo} nível $nivel")
            }
        }
    }

    @Test
    fun `distratores nunca sao semanticamente proximos do alvo`() {
        val gerador = GeradorPerguntas(dataset, Random(3))
        repeat(20) {
            Area.entries.forEach { area ->
                val porPalavra = dataset.filter { it.area == area }.associateBy { it.palavra }
                for (nivel in 1..3) {
                    gerador.gerarRodada(area, nivel, ModoJogo.MISTO, 10).forEach { p ->
                        if (p.tipo == TipoPergunta.SINONIMO ||
                            p.tipo == TipoPergunta.COMPLETAR_FRASE ||
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
        val a = gerador.gerarRodada(Area.GERAL, 1, ModoJogo.MISTO, 10).map { it.palavra.palavra }
        val b = gerador.gerarRodada(Area.GERAL, 1, ModoJogo.MISTO, 10).map { it.palavra.palavra }
        assertTrue(a != b, "Duas rodadas idênticas em sequência")
    }
}
