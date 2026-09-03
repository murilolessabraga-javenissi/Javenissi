package com.javenissi.filologia.jogo

import java.io.File
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class JogoTest {

    private val dataset: List<Palavra> by lazy {
        // Testes unitários do Android rodam com o diretório do módulo como cwd.
        val json = File("src/main/assets/palavras.json").readText()
        DatasetLoader.carregar(json)
    }

    @Test
    fun `dataset carrega e passa na validacao`() {
        assertTrue(dataset.size >= 90, "Esperava >= 90 palavras, veio ${dataset.size}")
        for (nivel in 1..3) {
            assertTrue(dataset.count { it.nivel == nivel } >= 30, "Nível $nivel com poucas palavras")
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
            val rodada = gerador.gerarRodada(nivel, ModoJogo.MISTO, 10)
            assertEquals(10, rodada.size)
            rodada.forEach { p ->
                assertEquals(4, p.opcoes.size, "Pergunta sem 4 opções: ${p.enunciado}")
                assertEquals(4, p.opcoes.toSet().size, "Opções repetidas: ${p.enunciado}")
                assertTrue(p.indiceCorreto in 0..3)
                val correta = p.opcoes[p.indiceCorreto]
                val esperada = when (p.tipo) {
                    TipoPergunta.PALAVRA_PARA_SIGNIFICADO -> p.palavra.significado
                    TipoPergunta.SIGNIFICADO_PARA_PALAVRA -> p.palavra.palavra
                    TipoPergunta.ETIMOLOGIA -> p.palavra.etimologia
                }
                assertEquals(esperada, correta)
                assertEquals(nivel, p.palavra.nivel)
            }
            assertEquals(10, rodada.map { it.palavra.palavra }.toSet().size,
                "Palavra repetida na mesma rodada")
        }
    }

    @Test
    fun `modo fixo gera apenas o tipo escolhido`() {
        val gerador = GeradorPerguntas(dataset, Random(7))
        val soSignificado = gerador.gerarRodada(2, ModoJogo.PALAVRA_PARA_SIGNIFICADO, 10)
        assertTrue(soSignificado.all { it.tipo == TipoPergunta.PALAVRA_PARA_SIGNIFICADO })
        val soPalavra = gerador.gerarRodada(2, ModoJogo.SIGNIFICADO_PARA_PALAVRA, 10)
        assertTrue(soPalavra.all { it.tipo == TipoPergunta.SIGNIFICADO_PARA_PALAVRA })
    }

    @Test
    fun `nivel facil no modo misto nao tem etimologia, niveis 2 e 3 tem`() {
        val gerador = GeradorPerguntas(dataset, Random(1))
        repeat(20) {
            val facil = gerador.gerarRodada(1, ModoJogo.MISTO, 10)
            assertFalse(facil.any { it.tipo == TipoPergunta.ETIMOLOGIA })
        }
        val tiposDificil = (1..20).flatMap {
            gerador.gerarRodada(3, ModoJogo.MISTO, 10).map { it.tipo }
        }.toSet()
        assertEquals(TipoPergunta.entries.toSet(), tiposDificil)
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
