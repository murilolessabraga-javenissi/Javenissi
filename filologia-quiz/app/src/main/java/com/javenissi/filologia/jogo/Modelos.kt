package com.javenissi.filologia.jogo

data class Palavra(
    val palavra: String,
    val significado: String,
    val etimologia: String,
    val nivel: Int,
    val sinonimos: List<String> = emptyList(),
    val frase: String = ""
)

enum class TipoPergunta {
    PALAVRA_PARA_SIGNIFICADO,
    SIGNIFICADO_PARA_PALAVRA,
    SINONIMO,
    COMPLETAR_FRASE,
    ETIMOLOGIA
}

enum class ModoJogo {
    PALAVRA_PARA_SIGNIFICADO,
    SIGNIFICADO_PARA_PALAVRA,
    MISTO,
    ETIMOLOGIA
}

data class Pergunta(
    val tipo: TipoPergunta,
    val enunciado: String,
    val opcoes: List<String>,
    val indiceCorreto: Int,
    val palavra: Palavra
)
