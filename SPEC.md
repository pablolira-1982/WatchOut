# SPEC TÉCNICA — WATCHOUT MOBILE / ASSISTENTE VISUAL OFFLINE

## 1. Objetivo

Criar uma aplicação Android nativa de assistência visual para pessoas cegas e com baixa visão, inspirada no projeto:

https://github.com/deddyjobson/watchout.git

A aplicação deve funcionar prioritariamente **offline**, usando a câmara do telemóvel e um modelo multimodal Gemma 4 E2B em formato LiteRT-LM.

Modelo fornecido:

`gemma-4-e2b.litertlm`

Tamanho atual aproximado:

`2.41 GB`

O modelo será fornecido separadamente e **não deve ser incluído no APK/AAB**.

A aplicação deve conseguir executar o modelo tanto a partir de armazenamento interno como de armazenamento externo/removível, incluindo microSD quando o dispositivo possuir suporte.

---

# 2. Stack obrigatória

## Android

* Kotlin
* Jetpack Compose
* Material 3
* Android Architecture Components
* Coroutines
* Kotlin Flow / StateFlow
* CameraX
* Android TextToSpeech
* VibratorManager / VibrationEffect
* DataStore para preferências
* LiteRT-LM oficial

Não utilizar Flutter, React Native ou WebView como tecnologia principal.

---

# 3. LLM local

Utilizar a API Android atual do LiteRT-LM.

Dependência de referência:

`com.google.ai.edge.litertlm:litertlm-android`

O Antigravity deve verificar qual é a release estável atual, resolver essa versão e depois fixá-la no projeto. Não deixar `latest.release` permanentemente numa build de produção.

O modelo deve ser carregado através de:

`EngineConfig(modelPath = ...)`

Backend preferencial:

`Backend.GPU()`

Também configurar:

`visionBackend = Backend.GPU()`

Implementar fallback para CPU caso a inicialização em GPU falhe.

Não usar APIs legadas de TensorFlow Lite quando LiteRT-LM fornecer a funcionalidade necessária.

---

# 4. Manifest para GPU

Adicionar dentro de `<application>` quando exigido pela versão atual do LiteRT-LM:

```xml
<uses-native-library
    android:name="libvndksupport.so"
    android:required="false" />

<uses-native-library
    android:name="libOpenCL.so"
    android:required="false" />
```

---

# 5. Engine Manager

Criar um componente único:

`LiteRtEngineManager`

Responsabilidades:

* receber caminho absoluto do `.litertlm`;
* validar existência;
* verificar tamanho;
* inicializar `Engine`;
* inicializar vision backend;
* manter apenas uma instância do engine;
* criar e fechar conversations corretamente;
* liberar memória em shutdown;
* reportar estados;
* fazer fallback GPU → CPU;
* impedir duas inicializações concorrentes.

Estados:

```kotlin
sealed interface ModelState {
    data object NotConfigured
    data object Loading
    data class Ready(val backend: String)
    data class Error(val message: String)
}
```

`engine.initialize()` nunca deve bloquear a main thread.

Usar Coroutine/Dispatchers adequado.

---

# 6. Armazenamento do modelo

Implementar:

`ModelStorageManager`

A aplicação deve suportar:

1. armazenamento interno;
2. armazenamento externo primário;
3. microSD/removable storage, se disponível;
4. importação manual de `.litertlm`.

Não hard-code:

`/storage/XXXX-XXXX/`

Usar APIs Android para descobrir volumes:

`ContextCompat.getExternalFilesDirs(context, null)`

Identificar volumes removíveis através de:

`Environment.isExternalStorageRemovable(file)`

e validar o estado com:

`Environment.getExternalStorageState(file)`.

---

# 7. Importação do modelo

Na primeira execução:

```text
Nenhum modelo configurado
        ↓
Selecionar modelo
        ↓
Escolher ficheiro .litertlm
        ↓
Escolher armazenamento
        ↓
Interno / Cartão SD
        ↓
Verificar espaço
        ↓
Copiar
        ↓
Validar
        ↓
Inicializar LiteRT-LM
```

Utilizar Storage Access Framework para permitir ao utilizador selecionar o ficheiro.

Não tentar converter `content://` diretamente num caminho de filesystem.

Abrir com `ContentResolver` e copiar por streaming para um diretório controlado pela aplicação.

Para ficheiro de 2.41 GB:

* não carregar tudo em RAM;
* copiar com buffer;
* mostrar progresso;
* mostrar MB/GB copiados;
* permitir cancelamento;
* verificar espaço livre antes;
* usar ficheiro `.partial` durante cópia;
* renomear apenas após cópia completa.

Opcionalmente calcular SHA-256.

---

# 8. Cartão SD

Caso exista microSD:

mostrar nas definições:

```text
Local do modelo

● Memória interna
○ Cartão SD
```

Mostrar também:

* capacidade;
* espaço livre;
* tamanho do modelo;
* estado `Disponível / Removido / Apenas leitura`.

Se o cartão SD for removido durante execução:

* parar novas inferências;
* não crashar;
* fechar engine se necessário;
* anunciar por TTS:
  `O modelo deixou de estar disponível.`
* mostrar opção para selecionar outro armazenamento.

Nunca assumir que o microSD estará permanentemente disponível.

---

# 9. Câmara

Usar CameraX.

Componentes:

* Preview
* ImageAnalysis

Estratégia:

`STRATEGY_KEEP_ONLY_LATEST`

Não tentar executar a LLM em todos os frames.

Criar processamento adaptativo.

Fluxo:

```text
CameraX
   ↓
latest frame
   ↓
conversão de imagem
   ↓
fila de inferência = capacidade 1
   ↓
Gemma 4 E2B
   ↓
resposta
   ↓
TTS + vibração
```

Nunca permitir múltiplas inferências simultâneas.

Se uma inferência estiver ativa, frames antigos devem ser descartados.

---

# 10. Imagem enviada à LLM

Preferir:

`Content.ImageBytes`

quando a versão atual da API permitir integração eficiente com os bytes produzidos pelo CameraX.

Caso a API ou o formato da imagem exija ficheiro:

* escrever JPEG temporário;
* usar `Content.ImageFile`;
* apagar após inferência.

Evitar acumular fotografias no dispositivo.

Redimensionar imagens antes da inferência quando necessário para reduzir:

* memória;
* latência;
* consumo energético.

Não prejudicar de forma excessiva a capacidade de reconhecer texto, obstáculos ou produtos.

---

# 11. Modos da aplicação

## Modo 1 — Segurança / obstáculos

Modo principal.

Prompt conceptual:

`Identifica apenas obstáculos, riscos e informação necessária para eu continuar em segurança. Responde em Português de Portugal, de forma curta e objetiva.`

Prioridades:

* obstáculos no chão;
* degraus;
* escadas;
* buracos;
* objetos salientes;
* obstáculos à altura da cabeça;
* mobiliário;
* portas;
* caminhos livres;
* risco de tropeçar;
* risco de queda.

---

## Modo 2 — Descrever ambiente

Descrever de forma resumida:

* tipo de ambiente;
* objetos principais;
* orientação esquerda/direita/frente;
* passagens;
* entradas;
* pessoas sem inferir dados pessoais desnecessários.

---

## Modo 3 — Encontrar objeto

Utilizador informa:

`Procura as minhas chaves.`

ou:

`Procura uma cadeira vazia.`

O app envia a imagem mais recente e o objeto procurado.

---

## Modo 4 — Ler texto

Usar a capacidade visual do modelo para:

* placas;
* números de porta;
* etiquetas;
* preços;
* embalagens;
* datas;
* pequenos textos.

Se o texto estiver desfocado ou ilegível, informar a incerteza.

Não inventar texto.

---

## Modo 5 — Produtos

Auxiliar em:

* localizar produtos;
* diferenciar embalagens;
* preços;
* posição em prateleiras.

---

## Modo 6 — Transportes

Identificar quando visível:

* autocarros;
* números;
* paragens;
* plataformas;
* elementos de segurança.

---

## Modo 7 — Passadeira / exterior

Este modo exige política de segurança especial.

O sistema pode informar:

* existência de passadeira;
* veículos visíveis;
* semáforo aparentemente vermelho/verde;
* obstáculos.

O sistema NUNCA deve dizer:

`Pode atravessar em segurança.`

com base apenas numa imagem.

Usar formulações como:

`O semáforo parece verde, mas confirme o trânsito e os sinais disponíveis antes de avançar.`

---

# 12. Prompt do sistema da LLM

Criar `AssistivePromptFactory`.

System instruction base:

```text
És um assistente visual para uma pessoa cega ou com baixa visão.

Responde sempre em Português de Portugal.

Prioriza segurança e informação útil.

As respostas devem ser curtas, claras e objetivas.

Para orientação espacial utiliza termos como:
à esquerda, à direita, diretamente à frente, ao fundo, junto ao chão e à altura da cabeça.

Não inventes objetos, texto, pessoas, distâncias ou perigos que não consigas confirmar visualmente.

Quando houver incerteza, diz claramente que não consegues confirmar.

Em situações potencialmente perigosas, começa pelo aviso mais importante.

Nunca afirmes que é seguro atravessar uma estrada apenas com base numa imagem.

Evita descrições decorativas quando existe informação de segurança mais importante.
```

Adicionar instrução específica conforme o modo selecionado.

---

# 13. Resposta do modelo

MVP deve funcionar primeiro com resposta textual natural.

Exemplo:

`Atenção: há uma cadeira diretamente à sua frente. A passagem está mais livre do lado esquerdo.`

Não tornar JSON obrigatório na primeira implementação, porque o modelo foi treinado principalmente para respostas naturais em pt-PT.

Preparar arquitetura para futuramente suportar saída estruturada:

```kotlin
data class AssistiveResult(
    val text: String,
    val risk: RiskLevel? = null,
    val confidence: Float? = null
)
```

---

# 14. TTS

Criar:

`SpeechManager`

Utilizar Android `TextToSpeech`.

Idioma preferencial:

Português de Portugal.

Tentar:

`Locale("pt", "PT")`

Se não existir voz pt-PT:

* selecionar uma voz portuguesa compatível;
* informar nas definições.

Funções:

* speak;
* stop;
* queue flush;
* ajustar velocidade;
* ativar/desativar.

Para novos alertas críticos:

interromper fala anterior.

---

# 15. Vibração

Criar:

`HapticManager`

Padrões sugeridos:

```text
Informação:
1 pulso curto

Atenção:
2 pulsos

Perigo:
3 pulsos rápidos

Erro:
1 pulso longo
```

Permitir desativar nas definições.

Não depender exclusivamente de vibração para alertas críticos.

---

# 16. Interface acessível

O público principal inclui pessoas cegas e com baixa visão.

Toda a UI deve ser compatível com TalkBack.

Requisitos:

* contentDescription útil;
* ordem de foco lógica;
* botões grandes;
* áreas de toque >= recomendação Android;
* contraste elevado;
* fonte escalável;
* não depender apenas de cores;
* feedback sonoro;
* feedback háptico;
* evitar menus complexos.

---

# 17. Ecrã principal

Layout conceptual:

```text
┌────────────────────────────────┐
│ ● Modelo pronto         GPU    │
│                                │
│                                │
│       CAMERA PREVIEW           │
│                                │
│                                │
│                                │
│    [ ANALISAR AGORA ]          │
│                                │
│ Segurança | Descrever | Procurar
│                                │
│ Último aviso:                  │
│ Cadeira diretamente à frente. │
└────────────────────────────────┘
```

O utilizador cego não deve depender da preview para operar o app.

---

# 18. Modo automático

Adicionar opção:

`Análise automática`

Intervalo configurável:

* 1 segundo;
* 2 segundos;
* 3 segundos;
* 5 segundos;
* manual.

O scheduler não deve iniciar outra inferência enquanto a anterior ainda estiver em execução.

Se a inferência demorar mais que o intervalo:

não criar backlog.

Sempre processar apenas a imagem mais recente.

---

# 19. Estados operacionais

Implementar claramente:

```text
MODEL_NOT_FOUND
MODEL_LOADING
MODEL_READY
CAMERA_READY
ANALYZING
SPEAKING
SD_CARD_REMOVED
LOW_STORAGE
GPU_FAILED_CPU_FALLBACK
MODEL_ERROR
```

A UI deve refletir estes estados de forma acessível.

---

# 20. Backend

Prioridade:

```text
GPU
 ↓ falhou
CPU
```

NPU pode ser adicionada posteriormente, mas não deve bloquear o MVP.

Registrar qual backend foi efetivamente iniciado.

Mostrar em:

`Definições > Diagnóstico`.

---

# 21. LiteRT-LM performance

Inicializar engine uma única vez sempre que possível.

Não inicializar a cada frame.

Não criar múltiplos engines.

Criar/cachear conversation conforme estratégia escolhida.

Avaliar MTP/speculative decoding no backend GPU quando suportado pela combinação:

* versão LiteRT-LM;
* Gemma 4 E2B;
* bundle `.litertlm`.

Manter opção interna para desativá-lo caso provoque incompatibilidade.

---

# 22. Privacidade

Princípio:

`Offline first.`

Não enviar:

* imagens;
* prompts;
* respostas;
* dados do utilizador

para servidores externos.

Não implementar analytics cloud no MVP.

Não guardar imagens da câmara por padrão.

Não guardar histórico sensível por padrão.

Logs nunca devem conter imagens.

---

# 23. Permissões

Solicitar apenas o necessário.

Obrigatória:

`CAMERA`

Possivelmente:

`VIBRATE`

Não solicitar permissões de armazenamento antigas desnecessárias.

Usar SAF e diretórios app-specific conforme Android moderno.

---

# 24. Arquitetura sugerida

```text
app/
│
├── core/
│   ├── model/
│   │   ├── LiteRtEngineManager.kt
│   │   ├── ModelState.kt
│   │   └── AssistivePromptFactory.kt
│   │
│   ├── camera/
│   │   ├── CameraController.kt
│   │   └── FrameProcessor.kt
│   │
│   ├── storage/
│   │   ├── ModelStorageManager.kt
│   │   ├── ModelImporter.kt
│   │   └── StorageVolume.kt
│   │
│   ├── speech/
│   │   └── SpeechManager.kt
│   │
│   └── haptics/
│       └── HapticManager.kt
│
├── feature/
│   ├── onboarding/
│   ├── assistant/
│   └── settings/
│
├── data/
│   └── preferences/
│
└── MainActivity.kt
```

Usar ViewModel + StateFlow.

Não colocar toda a lógica dentro de `MainActivity`.

---

# 25. Diagnóstico

Criar ecrã:

`Definições > Diagnóstico`

Mostrar:

* nome do modelo;
* caminho;
* tamanho;
* armazenamento interno/SD;
* backend;
* engine ready;
* versão LiteRT-LM;
* memória disponível;
* último tempo de inferência;
* último erro.

Não mostrar tokens, credenciais ou informação privada.

---

# 26. Testes obrigatórios

## Modelo

* modelo válido;
* modelo inexistente;
* extensão errada;
* ficheiro incompleto;
* erro de inicialização;
* GPU indisponível;
* fallback CPU.

## Armazenamento

* interno;
* SD;
* cartão removido;
* cartão read-only;
* espaço insuficiente;
* importação cancelada;
* cópia interrompida.

## Câmara

* permissão aceite;
* permissão recusada;
* pausa/resume;
* rotação;
* frame inválido.

## Inferência

* uma inferência de cada vez;
* frames antigos descartados;
* resposta vazia;
* erro nativo;
* timeout lógico;
* app continua responsivo.

## TTS

* pt-PT disponível;
* voz indisponível;
* TTS desligado;
* nova emergência interrompe fala anterior.

---

# 27. Critérios de conclusão

O MVP só está concluído quando for possível:

1. instalar o APK;
2. abrir o app;
3. conceder permissão da câmara;
4. selecionar `gemma-4-e2b.litertlm`;
5. escolher memória interna ou SD quando disponível;
6. importar um modelo de aproximadamente 2.41 GB sem OOM;
7. inicializar o LiteRT-LM;
8. visualizar a câmara;
9. capturar uma imagem;
10. enviar imagem + prompt ao Gemma 4 E2B;
11. receber resposta;
12. ouvir resposta por TTS;
13. receber vibração quando apropriado;
14. executar novamente sem reinicializar o modelo;
15. funcionar sem Internet.

---

# 28. Não fazer

Não:

* embutir 2.41 GB no APK;
* depender de API cloud;
* usar servidor remoto para inferência;
* guardar frames continuamente;
* executar várias inferências simultâneas;
* hard-code de caminho de cartão SD;
* assumir que SD nunca será removido;
* bloquear UI durante `engine.initialize()`;
* afirmar que estrada está segura apenas pela fotografia;
* substituir LiteRT-LM por API antiga sem motivo documentado.
