package com.javenissi.filologia

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.javenissi.filologia.ads.GerenciadorAnuncios
import com.javenissi.filologia.ui.FilologiaTheme
import com.javenissi.filologia.ui.TelaArea
import com.javenissi.filologia.ui.TelaInicio
import com.javenissi.filologia.ui.TelaQuiz
import com.javenissi.filologia.ui.TelaResultado

class MainActivity : ComponentActivity() {

    private lateinit var anuncios: GerenciadorAnuncios

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        anuncios = GerenciadorAnuncios(this)
        anuncios.iniciar()
        setContent {
            FilologiaTheme {
                AppFilologia(aoMostrarAnuncio = { anuncios.mostrar() })
            }
        }
    }
}

@Composable
fun AppFilologia(
    aoMostrarAnuncio: () -> Unit,
    viewModel: QuizViewModel = viewModel()
) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()

    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        when (estado.fase) {
            Fase.AREA -> TelaArea(
                aoSelecionarArea = viewModel::selecionarArea
            )
            Fase.INICIO -> TelaInicio(
                estado = estado,
                aoSelecionarNivel = viewModel::selecionarNivel,
                aoSelecionarModo = viewModel::selecionarModo,
                aoJogar = viewModel::iniciarRodada,
                aoTrocarArea = viewModel::voltarParaAreas
            )
            Fase.JOGANDO -> TelaQuiz(
                estado = estado,
                aoResponder = viewModel::responder,
                aoAvancar = {
                    if (viewModel.avancar()) aoMostrarAnuncio()
                }
            )
            Fase.RESULTADO -> TelaResultado(
                estado = estado,
                aoJogarDeNovo = viewModel::iniciarRodada,
                aoVoltar = viewModel::voltarAoInicio
            )
        }
    }
}
