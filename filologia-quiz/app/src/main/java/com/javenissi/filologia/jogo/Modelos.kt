package com.javenissi.filologia.jogo

/** Área de conhecimento a que o vocabulário pertence. */
enum class Area(val id: String, val rotulo: String, val descricao: String) {
    GERAL("geral", "Geral", "Vocabulário do português do dia a dia ao erudito"),
    JURIDICO("juridico", "Jurídico", "Termos e conceitos do direito"),
    TEOLOGICO("teologico", "Teológico", "Termos e conceitos da teologia"),
    FILOSOFICO("filosofico", "Filosófico", "Termos e conceitos da filosofia");

    companion object {
        fun porId(id: String): Area? = entries.firstOrNull { it.id == id }
    }
}

data class Palavra(
    val palavra: String,
    val significado: String,
    val etimologia: String,
    val nivel: Int,
    val area: Area = Area.GERAL,
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
    COMPLETO,
    ETIMOLOGIA
}

data class Pergunta(
    val tipo: TipoPergunta,
    val enunciado: String,
    val opcoes: List<String>,
    val indiceCorreto: Int,
    val palavra: Palavra
)
