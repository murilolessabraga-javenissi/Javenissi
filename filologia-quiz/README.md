# Filologia Quiz 📜

Jogo de perguntas sobre a língua portuguesa: palavras, significados e etimologias.

## Como funciona o jogo

O foco do jogo é **semântica**: o sentido das palavras.

- **Palavra → Significado**: aparece uma palavra e 4 significados como opções.
- **Significado → Palavra**: aparece um significado e 4 palavras como opções.
- **Misto**: além dos dois modos acima, acrescenta perguntas de **sinônimo**
  ("Qual palavra é sinônimo de...?") e de **sentido em contexto**
  ("Qual palavra completa a frase?").
- **Etimologia**: modo opcional dedicado à origem das palavras, para quem
  quiser o lado filológico.
- **3 níveis de dificuldade**: fácil (35 palavras comuns), médio (35 palavras
  cultas) e difícil (35 palavras eruditas). Um filtro semântico garante que as
  alternativas erradas nunca sejam sinônimos da resposta certa.
- Cada rodada tem 10 perguntas. Após cada resposta, o app mostra a origem da
  palavra como curiosidade. O recorde por nível/modo fica salvo no aparelho.
- **Monetização**: um anúncio intersticial (vídeo/tela cheia do AdMob) a cada
  5 respostas.

## Estrutura do código

```
app/src/main/
├── assets/palavras.json                  ← banco de palavras (edite aqui p/ adicionar palavras)
├── java/com/javenissi/filologia/
│   ├── jogo/                             ← lógica pura do jogo (coberta por testes)
│   │   ├── Modelos.kt                    ← Palavra, Pergunta, modos e tipos
│   │   ├── DatasetLoader.kt              ← carrega e valida o JSON
│   │   ├── GeradorPerguntas.kt           ← sorteia perguntas e alternativas
│   │   └── ContadorAnuncios.kt           ← dispara o anúncio a cada N respostas
│   ├── ads/GerenciadorAnuncios.kt        ← AdMob + consentimento (UMP)
│   ├── QuizViewModel.kt                  ← estado do jogo e recordes
│   ├── MainActivity.kt
│   └── ui/                               ← telas em Jetpack Compose
└── src/test/                             ← testes unitários da lógica
```

## Rodando o projeto

1. Instale o [Android Studio](https://developer.android.com/studio) (Ladybug ou mais novo).
2. **File → Open** e selecione a pasta `filologia-quiz`.
3. Espere a sincronização do Gradle terminar.
4. Rode os testes: clique com o botão direito em `app/src/test` → *Run tests*
   (ou `./gradlew test` no terminal).
5. Rode o app num emulador ou celular (botão ▶). Os anúncios exibidos serão os
   **anúncios de teste** do Google — isso é o correto durante o desenvolvimento.

## Antes de publicar: checklist

### 1. AdMob (monetização)

1. Crie uma conta em [admob.google.com](https://admob.google.com) e cadastre o app.
2. Crie um **bloco de anúncios intersticial** e copie os dois IDs:
   - **ID do app** (`ca-app-pub-...~...`) → substitua em
     `app/src/main/AndroidManifest.xml` (meta-data `APPLICATION_ID`).
   - **ID do bloco** (`ca-app-pub-.../...`) → substitua em
     `GerenciadorAnuncios.kt` (`ID_INTERSTICIAL`).
3. Nunca teste com os IDs reais no seu próprio aparelho sem registrá-lo como
   [dispositivo de teste](https://developers.google.com/admob/android/test-ads) —
   cliques próprios podem banir sua conta AdMob.
4. A frequência do anúncio está em `GerenciadorAnuncios.RESPOSTAS_POR_ANUNCIO`
   (hoje: 5). Um anúncio a cada ~30 segundos de jogo é agressivo; se a retenção
   cair, aumente para 10.
5. O consentimento LGPD/GDPR (obrigatório para anúncios) já está implementado
   via UMP; configure a mensagem de consentimento no painel do AdMob em
   **Privacidade e mensagens**.

### 2. Identidade do app

- Troque o `applicationId` em `app/build.gradle.kts` pelo seu domínio definitivo
  (ex.: `br.com.seunome.filologia`). **Depois de publicado, não pode mudar.**
- O nome exibido está em `res/values/strings.xml` (`app_name`).

### 3. Assinatura (keystore)

1. No Android Studio: **Build → Generate Signed App Bundle** → *Create new keystore*.
2. Guarde o arquivo `.jks` e as senhas em local seguro — **perdê-los impede
   atualizações futuras do app**. Nunca os coloque no repositório.

### 4. Gerar o pacote para a Play Store

- **Build → Generate Signed App Bundle** → escolha **Android App Bundle (.aab)**
  e o build type **release** (o `.aab` é obrigatório na Play, não use APK).

### 5. Play Console

1. Crie a conta de desenvolvedor em
   [play.google.com/console](https://play.google.com/console) (taxa única de US$ 25).
2. Crie o app, envie o `.aab` e preencha:
   - **Política de privacidade**: obrigatória por causa dos anúncios. Crie uma
     página (pode ser um site gratuito) dizendo que o app exibe anúncios do
     Google AdMob e cite a coleta feita pelo SDK.
   - **Segurança dos dados**: declare a coleta do AdMob (identificadores de
     dispositivo para publicidade).
   - **Classificação de conteúdo**: questionário — jogo educativo, livre.
   - **Público-alvo**: se declarar público infantil, as regras de anúncios
     mudam bastante; recomendo declarar 13+.
   - Capturas de tela, ícone 512×512 e banner 1024×500.
3. Publique primeiro em **teste interno/fechado**, valide os anúncios reais e
   só então promova para produção.

## Adicionando palavras

Edite `app/src/main/assets/palavras.json`. Cada entrada tem:

```json
{"palavra": "...", "significado": "...", "etimologia": "...", "nivel": 1,
 "sinonimos": ["...", "..."], "frase": "Frase de exemplo com ____ no lugar da palavra."}
```

Regras (verificadas automaticamente pelos testes): sem palavras duplicadas,
nível de 1 a 3, cada nível com pelo menos 4 palavras, e toda frase deve conter
a lacuna `____`. `sinonimos` pode ficar vazio (`[]`) quando a palavra não tiver
sinônimo natural — ela simplesmente não aparecerá em perguntas de sinônimo.
Rode `./gradlew test` após editar.
