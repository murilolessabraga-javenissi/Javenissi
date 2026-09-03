package com.javenissi.filologia.ads

import android.app.Activity
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform

/**
 * Cuida do ciclo completo dos anúncios: consentimento (UMP), inicialização do
 * SDK, pré-carregamento e exibição do intersticial. O anúncio é sempre
 * carregado com antecedência; se ainda não houver um pronto na hora de exibir,
 * o jogo simplesmente segue sem anúncio (nunca bloqueia o jogador).
 */
class GerenciadorAnuncios(private val activity: Activity) {

    private var intersticial: InterstitialAd? = null
    private var sdkInicializado = false

    /** Chamar uma vez no onCreate da Activity. */
    fun iniciar() {
        val consentInfo = UserMessagingPlatform.getConsentInformation(activity)
        val params = ConsentRequestParameters.Builder().build()
        consentInfo.requestConsentInfoUpdate(
            activity,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { erroFormulario ->
                    if (erroFormulario != null) {
                        Log.w(TAG, "Formulário de consentimento: ${erroFormulario.message}")
                    }
                    if (consentInfo.canRequestAds()) inicializarSdk()
                }
            },
            { erro ->
                Log.w(TAG, "Consentimento indisponível: ${erro.message}")
                if (consentInfo.canRequestAds()) inicializarSdk()
            }
        )
        // Consentimento já obtido em sessão anterior: não espera o formulário.
        if (consentInfo.canRequestAds()) inicializarSdk()
    }

    private fun inicializarSdk() {
        if (sdkInicializado) return
        sdkInicializado = true
        MobileAds.initialize(activity) { carregar() }
    }

    private fun carregar() {
        InterstitialAd.load(
            activity,
            ID_INTERSTICIAL,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(anuncio: InterstitialAd) {
                    intersticial = anuncio
                }

                override fun onAdFailedToLoad(erro: LoadAdError) {
                    intersticial = null
                    Log.w(TAG, "Falha ao carregar intersticial: ${erro.message}")
                }
            }
        )
    }

    /** Exibe o intersticial se houver um carregado; senão, tenta carregar para a próxima vez. */
    fun mostrar() {
        val anuncio = intersticial
        if (anuncio == null) {
            if (sdkInicializado) carregar()
            return
        }
        anuncio.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                intersticial = null
                carregar()
            }

            override fun onAdFailedToShowFullScreenContent(erro: AdError) {
                Log.w(TAG, "Falha ao exibir intersticial: ${erro.message}")
                intersticial = null
                carregar()
            }
        }
        intersticial = null
        anuncio.show(activity)
    }

    companion object {
        /**
         * Um intersticial a cada N respostas. Está em 5 conforme pedido, mas
         * um anúncio a cada ~30 segundos de jogo tende a derrubar a retenção
         * e pode gerar reprovação por frequência excessiva; considere 10.
         */
        const val RESPOSTAS_POR_ANUNCIO = 5

        /**
         * ID DE TESTE oficial do Google para intersticiais.
         * TODO: troque pelo ID do seu bloco de anúncios criado no AdMob
         * (https://admob.google.com) ANTES de publicar. Usar ID de teste em
         * produção = receita zero; usar ID real durante o desenvolvimento =
         * risco de banimento da conta AdMob por cliques inválidos.
         */
        private const val ID_INTERSTICIAL = "ca-app-pub-3940256099942544/1033173712"

        private const val TAG = "GerenciadorAnuncios"
    }
}
