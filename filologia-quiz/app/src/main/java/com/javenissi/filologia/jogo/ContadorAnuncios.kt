package com.javenissi.filologia.jogo

/**
 * Conta respostas ao longo de toda a sessão (atravessando rodadas) e indica
 * quando é hora de exibir um anúncio intersticial.
 */
class ContadorAnuncios(private val respostasPorAnuncio: Int) {

    init {
        require(respostasPorAnuncio > 0)
    }

    var totalRespostas: Int = 0
        private set

    /** Registra uma resposta e devolve true quando um anúncio deve ser exibido. */
    fun registrarResposta(): Boolean {
        totalRespostas++
        return totalRespostas % respostasPorAnuncio == 0
    }
}
