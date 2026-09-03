package com.javenissi.filologia.jogo

data class Palavra(
    val palavra: String,
    val significado: String,
    val etimologia: String,
    val nivel: Int
)

enum class TipoPergunta {
    PALAVRA_PARA_SIGNIFICADO,
    SIGNIFICADO_PARA_PALAVRA,
    ETIMOLOGIA
}

enum class ModoJogo {
    PALAVRA_PARA_SIGNIFICADO,
    SIGNIFICADO_PARA_PALAVRA,
    MISTO
}

data class Pergunta(
    val tipo: TipoPergunta,
    val enunciado: String,
    val opcoes: List<String>,
    val indiceCorreto: Int,
    val palavra: Palavra
)
