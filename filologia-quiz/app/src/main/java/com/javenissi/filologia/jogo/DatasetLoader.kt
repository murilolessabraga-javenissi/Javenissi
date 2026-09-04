package com.javenissi.filologia.jogo

import org.json.JSONArray

object DatasetLoader {

    const val LACUNA = "____"

    fun carregar(json: String): List<Palavra> {
        val array = JSONArray(json)
        val palavras = ArrayList<Palavra>(array.length())
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val sinonimos = obj.optJSONArray("sinonimos")?.let { arr ->
                (0 until arr.length()).map { arr.getString(it) }
            } ?: emptyList()
            val idArea = obj.optString("area", Area.GERAL.id)
            val area = requireNotNull(Area.porId(idArea)) {
                "Área desconhecida '$idArea' em '${obj.getString("palavra")}'"
            }
            palavras.add(
                Palavra(
                    palavra = obj.getString("palavra"),
                    significado = obj.getString("significado"),
                    etimologia = obj.getString("etimologia"),
                    nivel = obj.getInt("nivel"),
                    area = area,
                    sinonimos = sinonimos,
                    frase = obj.optString("frase", "")
                )
            )
        }
        validar(palavras)
        return palavras
    }

    private fun validar(palavras: List<Palavra>) {
        require(palavras.isNotEmpty()) { "Dataset vazio" }
        // A mesma palavra pode existir em áreas diferentes (com sentidos próprios
        // de cada uma), mas nunca duas vezes dentro da mesma área.
        val duplicadas = palavras
            .groupBy { it.area to it.palavra.lowercase() }
            .filterValues { it.size > 1 }
            .keys
        require(duplicadas.isEmpty()) { "Palavras duplicadas na mesma área: $duplicadas" }
        palavras.forEach { p ->
            require(p.nivel in 1..3) { "Nível inválido em '${p.palavra}': ${p.nivel}" }
            require(p.palavra.isNotBlank() && p.significado.isNotBlank() && p.etimologia.isNotBlank()) {
                "Campos em branco em '${p.palavra}'"
            }
            require(p.frase.isBlank() || p.frase.contains(LACUNA)) {
                "Frase de '${p.palavra}' não contém a lacuna $LACUNA"
            }
            require(p.sinonimos.none { it.equals(p.palavra, ignoreCase = true) }) {
                "'${p.palavra}' lista a si mesma como sinônimo"
            }
        }
        // Toda combinação de área e nível precisa sustentar uma rodada inteira.
        Area.entries.forEach { area ->
            for (nivel in 1..3) {
                val quantidade = palavras.count { it.area == area && it.nivel == nivel }
                require(quantidade >= MINIMO_POR_NIVEL) {
                    "Área ${area.rotulo}, nível $nivel: $quantidade palavras (mínimo $MINIMO_POR_NIVEL)"
                }
            }
        }
    }

    /** Uma rodada tem 10 perguntas; exigir 10 impede repetir palavra na mesma rodada. */
    const val MINIMO_POR_NIVEL = 10
}
