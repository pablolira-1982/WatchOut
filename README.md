# WatchOut — Assistente Visual Offline para Android

<p align="center">
  <img src="app/src/main/res/drawable/hero_image.png" width="360" alt="WatchOut">
</p>

<p align="center"><strong>Assistência visual local para pessoas cegas e com baixa visão.</strong><br>
Câmara, IA no dispositivo, voz e vibração — sem enviar imagens para servidores.</p>

## Objetivo do projeto

O WatchOut ajuda pessoas cegas e com baixa visão a obter uma descrição falada do que a câmara observa. A aplicação funciona sem conta, anúncios ou envio de imagens para serviços externos. O modelo Gemma 4 E2B é executado no próprio Android através do LiteRT-LM.

O projeto oferece modos para Segurança, Ler texto, Procurar, Descrever, Compras, Passadeira e Transportes. A resposta é uma ajuda complementar: não substitui bengala, cão-guia, treino de orientação e mobilidade, sinais sonoros ou a avaliação direta do ambiente.

> Em particular, o WatchOut nunca deve ser a única fonte para decidir atravessar uma rua.

## Downloads

O modelo de IA não está incluído no APK. Assim, o APK continua pequeno e a pessoa escolhe o modelo a partir da memória interna ou de um cartão SD.

| Ficheiro | Download | Tamanho aproximado |
|---|---|---:|
| WatchOut Android APK, versão 1.0.11 | [Baixar APK](https://drive.google.com/file/d/1_zja-Nxpu1Vto5zhLZK9P4VGP4sjhoVV/view?usp=sharing) | 59 MB |
| Gemma 4 E2B LiteRT-LM | [Baixar modelo LiteRT](https://drive.google.com/file/d/1GQDUMtJGQI9KASoJNqH-_9BIOquUF5gU/view?usp=sharing) | 2,59 GB / 2,41 GiB |

Nome esperado do modelo: `gemma-4-E2B-it.litertlm`.

## Instalação no Android

1. Baixe e instale o APK. Se o Android pedir, permita instalar aplicações desta origem.
2. Baixe o ficheiro `.litertlm` para a memória interna ou cartão SD. Não é necessário copiá-lo para uma pasta especial.
3. Abra o WatchOut. O guia inicial falado explica o menu uma única vez.
4. Permita a câmara. O microfone é opcional e usado para comandos de voz.
5. Escolha uma função, por exemplo **Segurança**, e depois toque em **Abrir câmara**.
6. Quando o app pedir o modelo, selecione o ficheiro `.litertlm` e escolha memória interna ou cartão SD. O app faz a cópia e valida o ficheiro antes de o usar.

Para pessoas que usam TalkBack: deslize até ao botão para ouvir a descrição e toque duas vezes para ativá-lo.

### Funções e comandos de voz

| Função | Uso |
|---|---|
| Segurança | Obstáculos e riscos no caminho. |
| Ler texto | Placas, rótulos e etiquetas. |
| Procurar | Ajuda a encontrar objetos. |
| Compras | Produtos, supermercado e preços. |
| Passadeira | Elementos visíveis de trânsito; não confirma travessia segura. |
| Transportes | Paragens, autocarros e linhas. |

O microfone aceita comandos como `segurança`, `ler texto`, `procurar`, `compras`, `passadeira`, `transportes` e `abrir câmara`. Se o Android informar que o reconhecimento de voz não está disponível, use **Configurar reconhecimento de voz** no app para abrir as definições do sistema e ativar/instalar um serviço com idioma português.

Na câmara, a seta superior esquerda regressa ao menu. A engrenagem abre definições, incluindo **Limpar sessão**, que apaga respostas e contexto anteriores sem apagar o modelo.

## Desenvolvimento no Linux

### Dependências

É necessário JDK 17, Git, Android Studio e Android SDK.

Em Ubuntu/Debian:

```bash
sudo apt update
sudo apt install -y openjdk-17-jdk git unzip
java -version
```

Instale o [Android Studio](https://developer.android.com/studio). No primeiro arranque, no **SDK Manager**, instale:

- Android SDK Platform 35;
- Android SDK Build-Tools 35.0.0;
- Android SDK Platform-Tools;
- Android SDK Command-line Tools (latest).

O caminho habitual do SDK Linux é `~/Android/Sdk`.

### Abrir e compilar

```bash
git clone <URL-DO-SEU-REPOSITORIO> WATCHOUT
cd WATCHOUT
printf 'sdk.dir=%s\n' "$HOME/Android/Sdk" > local.properties
./gradlew :app:lintRelease :app:testReleaseUnitTest :app:assembleRelease
```

Abra a pasta no Android Studio e aguarde a sincronização do Gradle. Kotlin, Compose, CameraX e LiteRT-LM são descarregados pelo Gradle.

O APK release é criado nesta pasta:

```text
app/build/outputs/apk/release/
```

Use o nome `.apk` mostrado pelo Gradle. A saída normal do projeto é `app-release.apk`.

Para instalar por USB:

```bash
# Ative Opções de programador > Depuração USB no telemóvel
adb install -r app/build/outputs/apk/release/app-release.apk
```

## Desenvolvimento no Windows

### Dependências

1. Instale [Android Studio](https://developer.android.com/studio) com o JDK incluído, ou JDK 17 separadamente.
2. Em **More Actions > SDK Manager**, instale Android Platform 35, Build-Tools 35.0.0, Platform-Tools e Command-line Tools.
3. Instale [Git for Windows](https://git-scm.com/download/win), se necessário.

O SDK costuma ficar em:

```text
C:\Users\SEU_UTILIZADOR\AppData\Local\Android\Sdk
```

### Abrir e compilar

No PowerShell:

```powershell
git clone <URL-DO-SEU-REPOSITORIO> WATCHOUT
cd WATCHOUT
'sdk.dir=C:\\Users\\SEU_UTILIZADOR\\AppData\\Local\\Android\\Sdk' | Set-Content local.properties
.\gradlew.bat :app:lintRelease :app:testReleaseUnitTest :app:assembleRelease
```

Abra `WATCHOUT` no Android Studio e use **Sync Project with Gradle Files**. O APK resultante fica em:

```text
app\build\outputs\apk\release\
```

Também é possível usar **Build > Generate App Bundles or APKs > Generate APKs**.

## Gerar APK, validar e distribuir para testes

### Gerar APK release

Linux/macOS:

```bash
./gradlew :app:assembleRelease
```

Windows:

```powershell
.\gradlew.bat :app:assembleRelease
```

### Validar antes de entregar

```bash
apksigner verify --verbose app/build/outputs/apk/release/app-release.apk
sha256sum app/build/outputs/apk/release/app-release.apk
```

Verifique ainda num telefone real: permissões, guia falado, navegação pelo TalkBack, seleção do modelo no SD/memória interna, análise e retorno ao menu.

### Assinatura de produção

Para uma distribuição pública, crie e proteja uma chave própria. Não partilhe a palavra-passe nem o ficheiro do keystore.

```bash
keytool -genkeypair -v -keystore watchout-release.jks -alias watchout \
  -keyalg RSA -keysize 2048 -validity 10000
```

Configure a assinatura de produção fora do controlo de versão, por exemplo com `key.properties`. A chave de teste serve apenas para desenvolvimento e não deve ser usada numa publicação nova na Play Store.

## Deploy e publicação

### APK: testes diretos

Use o APK release para testes internos, distribuído por Google Drive, USB ou MDM. O modelo LiteRT deve continuar num download separado; não o incorpore no APK.

### Google Play: quando estiver autorizado

Ainda não gere AAB se a publicação não foi aprovada. Quando chegar o momento:

```bash
./gradlew :app:bundleRelease
```

O resultado será `app/build/outputs/bundle/release/app-release.aab`. Antes do envio para a Play Store:

1. use assinatura de produção estável;
2. aumente `versionCode` e `versionName`;
3. teste numa instalação limpa e numa atualização;
4. descreva claramente as permissões Câmara e Microfone;
5. publique uma política de privacidade que confirme o processamento local;
6. confirme os termos de redistribuição do modelo Gemma/LiteRT;
7. mantenha o download de 2,59 GB fora do APK/AAB.

## Configuração técnica

| Componente | Configuração |
|---|---|
| Linguagem | Kotlin |
| Interface | Jetpack Compose |
| Câmara | CameraX |
| IA local | LiteRT-LM `0.17.0` + Gemma 4 E2B |
| Android SDK | compile/target 35; minSdk 24 |
| Java | 17 |
| Gradle | Wrapper 8.14 |
| Privacidade | Sem permissão de Internet; imagens não são enviadas para servidores |

Cada alerta usa uma sessão de IA nova. Os modos Segurança e Ler texto usam temperatura mais baixa para reduzir respostas inventadas.

## Problemas frequentes

| Problema | Solução |
|---|---|
| `SDK location not found` | Confira `local.properties`. |
| O modelo não aparece | Confirme a extensão `.litertlm` e escolha-o pelo seletor de ficheiros Android. |
| Erro de modelo | Use a cópia oficial e selecione **Mudar modelo**. |
| Reconhecimento de voz indisponível | Abra **Configurar reconhecimento de voz** e instale/ative idioma português no Android. |
| Aplicação lenta | A IA roda localmente; carregue o telefone e deixe memória disponível. |
| Resposta parece errada | Use **Engrenagem > Limpar sessão**, enquadre novamente e não trate a resposta como certeza de segurança. |

## Referências

- Projeto de referência: [deddyjobson/watchout](https://github.com/deddyjobson/watchout)
- Notebook de treino/exportação: [Gemma 4 E2B Training for LiteRT](https://www.kaggle.com/code/pablowilliam/gemma-4-e2b-training-for-litert)

## ❤️ Apoie este projeto

Se este projeto foi útil para você e quiser apoiar o desenvolvimento, pode fazer uma doação via PIX.

### 🇧🇷 PIX

<p align="center">
  <img src="./qrcode-chave-pix.png" width="250" alt="QR Code PIX">
</p>

**Chave PIX:**

```text
b3652001-9daa-4131-ad05-6ea1f59e1721
```

## Licença

MIT License — livre para uso pessoal, institucional e académico. Antes de redistribuir o modelo Gemma, confira também os termos da respetiva licença.
