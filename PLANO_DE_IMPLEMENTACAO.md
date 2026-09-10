# Plano de Implementação: Assistente Visual Mobile Offline (WATCHOUT)

Com base na análise minuciosa dos documentos `PRD.md`, `PROMPT.md` e `SPEC.md`, este plano detalha a construção de uma aplicação Android nativa, focada em acessibilidade (cegos e baixa visão), totalmente offline e impulsionada localmente pelo modelo multimodal Gemma 4 E2B através do LiteRT-LM.

**Atenção:** Em resposta ao seu pedido, este plano foi criado e está a ser mantido estritamente dentro do diretório de desenvolvimento do projeto (`/home/paablo/Documentos/WATCHOUT`).

## Questões em Aberto (Por favor, confirme antes de avançarmos):

1. **Repositório de Referência vs. Novo Projeto:** O `PROMPT.md` menciona clonar `https://github.com/deddyjobson/watchout.git`. Posso clonar este repositório para o nosso diretório e substituir/reconstruir por cima dele, ou prefere que eu inicie um novo projeto Android limpo no diretório atual que siga estritamente a nova arquitetura especificada no `SPEC.md`? (Recomendo um projeto limpo para garantir alinhamento total com as versões mais recentes do Compose, CameraX e LiteRT-LM).
2. **Versão LiteRT-LM:** Preciso de verificar a versão estável mais recente do `com.google.ai.edge.litertlm:litertlm-android` aquando da criação do projeto. Concorda em usar a última versão estável disponível?

---

## Proposta de Arquitetura e Implementação

A aplicação seguirá uma arquitetura baseada em MVVM, Clean Architecture (simplificada) e Kotlin Flow, estruturada nos seguintes pacotes:

### 1. Configuração Inicial e Dependências (`app/build.gradle.kts`)
- Configurar Kotlin, Jetpack Compose e Material 3.
- Adicionar dependências essenciais: CameraX, ViewModel, StateFlow, Coroutines, DataStore.
- Adicionar dependência oficial do LiteRT-LM: `com.google.ai.edge.litertlm:litertlm-android`.
- Configurar o `AndroidManifest.xml` (permissões de `CAMERA`, `VIBRATE` e declarações `uses-native-library` para `libvndksupport.so` e `libOpenCL.so` para suporte nativo de GPU).

---

### 2. Módulos Core (`core/`)

#### `core/storage` (Gestão de Armazenamento e SD Card)
- **`ModelStorageManager`**: Lógica para descobrir volumes (armazenamento interno e SD Card) através de `ContextCompat.getExternalFilesDirs()`.
- **`ModelImporter`**: Utilizar o *Storage Access Framework* (SAF) para permitir ao utilizador selecionar o modelo `.litertlm` (2.41 GB). Copiar o modelo por stream usando extensão `.partial` (com feedback de progresso e verificação preventiva de espaço), prevenindo erros de memória (OOM).
- **`StorageVolume`**: Modelo de dados para representar os volumes disponíveis.

#### `core/model` (Integração LiteRT-LM e LLM)
- **`LiteRtEngineManager`**: Manager responsável por inicializar o `EngineConfig(modelPath)`. Priorizará `Backend.GPU()` com fallback para CPU. Garantirá que apenas *uma* inferência decorre de cada vez, evitando bloqueios na Main Thread.
- **`AssistivePromptFactory`**: Geração dinâmica de system prompts baseados no modo selecionado (Segurança, Descrever, Encontrar, Ler, etc.).
- **`ModelState`**: Estados do modelo (NotConfigured, Loading, Ready, Error) a refletir na interface.

#### `core/camera` (Câmara e Captura Visual)
- **`CameraController`**: Configuração do CameraX (`Preview` e `ImageAnalysis`).
- **`FrameProcessor`**: Captura usando a estratégia `STRATEGY_KEEP_ONLY_LATEST`. O processador extrairá `Content.ImageBytes` ou criará um ficheiro temporário (consoante o requisito estrito da API instalada do LiteRT-LM), descartando agressivamente frames antigos durante uma inferência ativa.

#### `core/speech` & `core/haptics` (Acessibilidade e Feedback)
- **`SpeechManager`**: Gestão de `TextToSpeech` (com preferência absoluta para o locale `pt-PT`). Implementará interrupção imediata de fala caso surjam alertas mais críticos ou mais recentes.
- **`HapticManager`**: Padrões de vibração via `VibratorManager` para sinais claros (1 pulso = informação, 2 = atenção, 3 = perigo).

---

### 3. Funcionalidades de Interface (Features)

#### `feature/onboarding`
- Ecrãs para consentimento e gestão de permissões.
- Fluxo de seleção de armazenamento interno ou SD e importação guiada do modelo `gemma-4-e2b.litertlm`.

#### `feature/assistant` (Ecrã Principal)
- **`AssistantViewModel`**: Orquestrador reativo entre a câmara, engine de IA, TTS e estado da UI.
- **UI Totalmente Acessível**: 100% desenhada para ser operada por voz (TalkBack), sem depender da visualização física do preview da câmara. Descrições (content descriptions) claras.
- **Modos de Operação**: Botões de acesso rápido para alternar entre modos (Segurança vs Procurar vs Ler).
- **Modo Automático**: Implementação de um temporizador / scheduler assíncrono que só submete novas capturas quando o LiteRT-LM terminar o processamento anterior.

#### `feature/settings`
- Definições avançadas incluindo diagnóstico do sistema (memória livre, tempo de carga, local e estado do modelo e backend usado).
- Ajustes de velocidade de fala, vibração e temporização.

---

### 4. Plano de Validação e Testes

#### Testes de Carga e Exceções (Manual e Automatizado)
1. **Armazenamento**: Simular remoção abrupta do cartão SD durante a execução; testar importação de modelo sem espaço em disco suficiente.
2. **Concorrência**: Simular múltiplos toques no botão "Analisar", validando que apenas o *latest frame* é processado sem encravar o engine.
3. **Hardware (GPU/CPU)**: Testar inicialização de GPU forçando uma falha lógica, garantindo que o CPU assume imediatamente (fallback mode).
4. **Offline Only**: Garantir a 100% que a aplicação opera com as redes de Dados/Wi-Fi desligadas.

Aguardo a sua aprovação ou comentários em relação às Questões em Aberto acima para dar início à configuração ou clonagem do projeto base!
