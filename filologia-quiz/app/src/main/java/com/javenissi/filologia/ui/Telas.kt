package com.javenissi.filologia.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.javenissi.filologia.EstadoUi
import com.javenissi.filologia.QuizViewModel
import com.javenissi.filologia.jogo.ModoJogo
import com.javenissi.filologia.jogo.TipoPergunta

private val nomesNiveis = mapOf(1 to "Fácil", 2 to "Médio", 3 to "Difícil")

@Composable
fun TelaInicio(
    estado: EstadoUi,
    aoSelecionarNivel: (Int) -> Unit,
    aoSelecionarModo: (ModoJogo) -> Unit,
    aoJogar: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("φ", style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.primary)
        Text(
            "Filologia Quiz",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Palavras, significados e origens da língua portuguesa",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(32.dp))

        Text("Nível", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            nomesNiveis.forEach { (nivel, nome) ->
                FilterChip(
                    selected = estado.nivel == nivel,
                    onClick = { aoSelecionarNivel(nivel) },
                    label = { Text(nome) }
                )
            }
        }
        Spacer(Modifier.height(24.dp))

        Text("Modo de jogo", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OpcaoModo("Palavra → Significado", estado.modo == ModoJogo.PALAVRA_PARA_SIGNIFICADO) {
                aoSelecionarModo(ModoJogo.PALAVRA_PARA_SIGNIFICADO)
            }
            OpcaoModo("Significado → Palavra", estado.modo == ModoJogo.SIGNIFICADO_PARA_PALAVRA) {
                aoSelecionarModo(ModoJogo.SIGNIFICADO_PARA_PALAVRA)
            }
            OpcaoModo("Misto (significado, sinônimo e contexto)", estado.modo == ModoJogo.MISTO) {
                aoSelecionarModo(ModoJogo.MISTO)
            }
            OpcaoModo("Completo (misto + etimologia)", estado.modo == ModoJogo.COMPLETO) {
                aoSelecionarModo(ModoJogo.COMPLETO)
            }
            OpcaoModo("Etimologia (origem das palavras)", estado.modo == ModoJogo.ETIMOLOGIA) {
                aoSelecionarModo(ModoJogo.ETIMOLOGIA)
            }
        }
        Spacer(Modifier.height(32.dp))

        Button(
            onClick = aoJogar,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Jogar", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun OpcaoModo(texto: String, selecionado: Boolean, aoClicar: () -> Unit) {
    FilterChip(
        selected = selecionado,
        onClick = aoClicar,
        label = {
            Text(texto, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp))
        },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
fun TelaQuiz(
    estado: EstadoUi,
    aoResponder: (Int) -> Unit,
    aoAvancar: () -> Unit
) {
    val pergunta = estado.perguntaAtual ?: return
    val respondida = estado.respostaSelecionada != null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "Pergunta ${estado.indiceAtual + 1} de ${estado.perguntas.size}",
                style = MaterialTheme.typography.labelLarge
            )
            Text(
                "${nomesNiveis[estado.nivel]} · Acertos: ${estado.acertos}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { (estado.indiceAtual + 1f) / estado.perguntas.size },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(20.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Text(
                pergunta.enunciado,
                modifier = Modifier.padding(20.dp),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Spacer(Modifier.height(20.dp))

        pergunta.opcoes.forEachIndexed { indice, opcao ->
            val cores = when {
                !respondida -> ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
                indice == pergunta.indiceCorreto -> ButtonDefaults.buttonColors(
                    containerColor = VerdeAcertoContainer,
                    contentColor = VerdeAcerto
                )
                indice == estado.respostaSelecionada -> ButtonDefaults.buttonColors(
                    containerColor = VermelhoErroContainer,
                    contentColor = VermelhoErro
                )
                else -> ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(
                onClick = { aoResponder(indice) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                shape = RoundedCornerShape(12.dp),
                colors = cores
            ) {
                Text(
                    opcao,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (respondida && indice == pergunta.indiceCorreto) {
                        FontWeight.Bold
                    } else {
                        FontWeight.Normal
                    }
                )
            }
        }

        if (respondida) {
            Spacer(Modifier.height(12.dp))
            val curiosidade = when (pergunta.tipo) {
                TipoPergunta.ETIMOLOGIA ->
                    "\"${pergunta.palavra.palavra}\": ${pergunta.palavra.significado}"
                TipoPergunta.SINONIMO, TipoPergunta.COMPLETAR_FRASE ->
                    "\"${pergunta.palavra.palavra}\": ${pergunta.palavra.significado}. " +
                        "Origem: ${pergunta.palavra.etimologia}"
                else ->
                    "Origem de \"${pergunta.palavra.palavra}\": ${pergunta.palavra.etimologia}"
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "📜 Você sabia?",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        curiosidade,
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = aoAvancar,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(if (estado.ultimaPergunta) "Ver resultado" else "Próxima")
            }
        }
    }
}

@Composable
fun TelaResultado(
    estado: EstadoUi,
    aoJogarDeNovo: () -> Unit,
    aoVoltar: () -> Unit
) {
    val total = QuizViewModel.PERGUNTAS_POR_RODADA
    val mensagem = when {
        estado.acertos == total -> "Impecável! Um verdadeiro filólogo."
        estado.acertos >= 7 -> "Muito bem! Seu vocabulário está afiado."
        estado.acertos >= 4 -> "Bom começo. As palavras se aprendem jogando."
        else -> "Continue tentando — cada erro ensina uma origem nova."
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            if (estado.novoRecorde) "🏆" else "📖",
            style = MaterialTheme.typography.displayLarge
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "${estado.acertos} / $total",
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(8.dp))
        Text(mensagem, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        if (estado.novoRecorde) {
            Text(
                "Novo recorde neste nível!",
                style = MaterialTheme.typography.titleSmall,
                color = VerdeAcerto,
                fontWeight = FontWeight.Bold
            )
        } else {
            Text(
                "Recorde neste nível: ${estado.recorde} / $total",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = aoJogarDeNovo,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Jogar de novo")
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = aoVoltar,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Mudar nível ou modo")
        }
    }
}
