package com.javenissi.filologia.jogo

import org.json.JSONArray

object DatasetLoader {

    fun carregar(json: String): List<Palavra> {
        val array = JSONArray(json)
        val palavras = ArrayList<Palavra>(array.length())
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            palavras.add(
                Palavra(
                    palavra = obj.getString("palavra"),
                    significado = obj.getString("significado"),
                    etimologia = obj.getString("etimologia"),
                    nivel = obj.getInt("nivel")
                )
            )
        }
        validar(palavras)
        return palavras
    }

    private fun validar(palavras: List<Palavra>) {
        require(palavras.isNotEmpty()) { "Dataset vazio" }
        val duplicadas = palavras.groupBy { it.palavra.lowercase() }.filterValues { it.size > 1 }.keys
        require(duplicadas.isEmpty()) { "Palavras duplicadas no dataset: $duplicadas" }
        palavras.forEach { p ->
            require(p.nivel in 1..3) { "Nível inválido em '${p.palavra}': ${p.nivel}" }
            require(p.palavra.isNotBlank() && p.significado.isNotBlank() && p.etimologia.isNotBlank()) {
                "Campos em branco em '${p.palavra}'"
            }
        }
        for (nivel in 1..3) {
            val quantidade = palavras.count { it.nivel == nivel }
            require(quantidade >= 4) { "Nível $nivel precisa de pelo menos 4 palavras, tem $quantidade" }
        }
    }
}
