# PROMPT PARA ANTIGRAVITY

Quero que cries uma aplicação Android funcional de assistência visual offline baseada no projeto de referência:

https://github.com/deddyjobson/watchout.git

Antes de escrever código:

1. clona/inspeciona completamente o repositório;
2. identifica arquitetura, linguagem, módulos, dependências e implementação atual;
3. identifica quais partes são reutilizáveis;
4. verifica a documentação atual do LiteRT-LM Android;
5. verifica a versão Maven estável atual do `litertlm-android`;
6. só depois começa a implementação.

Não copies cegamente APIs antigas do projeto WatchOut. Usa as APIs atuais do Android e LiteRT-LM.

---

## OBJETIVO

Criar uma aplicação Android nativa para pessoas cegas e com baixa visão.

Fluxo principal:

```text
CameraX
   ↓
Imagem
   ↓
Gemma 4 E2B LiteRT-LM local
   ↓
Resposta em Português de Portugal
   ↓
TextToSpeech
   ↓
Vibração
```

A aplicação deve funcionar offline.

---

## MODELO

O utilizador já possui:

`gemma-4-e2b.litertlm`

Tamanho aproximado:

`2.41 GB`

Não incluir o modelo no APK ou AAB.

Não fazer download obrigatório de cloud.

O app deve permitir importar o modelo existente.

Não assumas se o fine-tuning está ou não embutido no bundle: trata o `.litertlm` fornecido como modelo compatível e opaco.

---

## TECNOLOGIAS

Obrigatórias:

* Kotlin;
* Jetpack Compose;
* Material 3;
* CameraX;
* Coroutines;
* Flow/StateFlow;
* ViewModel;
* DataStore;
* TextToSpeech;
* Android haptics;
* LiteRT-LM Android.

Usa a dependência Maven atual de:

`com.google.ai.edge.litertlm:litertlm-android`

Resolve a versão estável atual e depois fixa essa versão no projeto para builds reproduzíveis.

---

## LITERT-LM

Criar:

`LiteRtEngineManager`

Inicialização conceptual:

```kotlin
val config = EngineConfig(
    modelPath = modelPath,
    backend = Backend.GPU(),
    visionBackend = Backend.GPU(),
    cacheDir = context.cacheDir.path,
)
```

Não copies este código sem confirmar a assinatura exata da versão instalada.

Consulta a API real.

Inicializa o engine numa coroutine/background thread.

Nunca na UI thread.

Mantém apenas um engine ativo.

Implementa:

```text
GPU
 ↓ se falhar
CPU
```

Para GPU, adiciona ao Manifest as native libraries atualmente exigidas pelo LiteRT-LM.

---

## MULTIMODALIDADE

Enviar:

* imagem CameraX;
* prompt textual.

Preferir `Content.ImageBytes` para evitar escrita contínua no disco.

Se a API exigir um ficheiro num determinado formato, usar `Content.ImageFile` através de ficheiro temporário e eliminá-lo após inferência.

Usar `Contents.of(...)` conforme API real instalada.

---

## CAMERA

Usar CameraX:

* Preview;
* ImageAnalysis;
* `STRATEGY_KEEP_ONLY_LATEST`.

Nunca enviar cada frame para o modelo.

Criar uma fila de inferência com capacidade efetiva de 1.

Enquanto uma inferência estiver ativa:

descartar frames antigos.

Sempre utilizar a imagem mais recente.

---

## MODOS

Implementar:

### Segurança

Identificar:

* obstáculos;
* escadas;
* degraus;
* objetos no chão;
* buracos;
* portas;
* obstáculos à altura da cabeça;
* caminho livre;
* riscos.

### Descrever

Descrição curta do ambiente.

### Encontrar

Utilizador informa um objeto e o sistema procura visualmente.

### Ler

Ler:

* placas;
* preços;
* números;
* embalagens;
* datas;
* pequenos textos.

### Produtos

Localizar produtos e posição em prateleiras.

### Transporte

Identificar autocarros, números e elementos relevantes.

### Passadeira

Informar elementos visíveis, mas nunca declarar que é seguro atravessar apenas porque a IA viu semáforo/passadeira.

---

## SYSTEM PROMPT

Usa este prompt base:

```text
És um assistente visual para uma pessoa cega ou com baixa visão.

Responde sempre em Português de Portugal.

Prioriza segurança e informação útil.

Responde de forma curta, clara e objetiva.

Utiliza orientação espacial como esquerda, direita, diretamente à frente, ao fundo, junto ao chão e à altura da cabeça.

Não inventes objetos, textos, distâncias, cores ou perigos que não consigas confirmar visualmente.

Se houver incerteza, diz claramente que não consegues confirmar.

Se existir perigo, começa pelo perigo.

Nunca digas que é seguro atravessar uma estrada apenas com base numa imagem.

Não incluas explicações longas quando um alerta curto for suficiente.
```

Adicionar prompt específico conforme o modo.

---

## ARMAZENAMENTO

Implementa:

`ModelStorageManager`

O utilizador deve poder escolher:

```text
Memória interna
Cartão SD
```

quando cartão SD existir.

Não hard-code caminhos como:

`/storage/XXXX-XXXX`.

Usar:

`ContextCompat.getExternalFilesDirs(...)`

e APIs oficiais para identificar volumes removíveis.

---

## IMPORTAR MODELO

Usar Storage Access Framework para escolher `.litertlm`.

Como SAF normalmente fornece `content://`, não tentar converter URI arbitrariamente em path.

Copiar por stream para armazenamento app-specific.

Como o modelo possui aproximadamente 2.41 GB:

* verificar espaço;
* não carregar em RAM;
* buffer streaming;
* mostrar progresso;
* permitir cancelamento;
* usar `.partial`;
* validar tamanho final;
* opcional SHA-256.

Se SD for removido:

* app não pode crashar;
* cancelar inferência;
* marcar modelo indisponível;
* avisar por TTS;
* oferecer seleção de outro modelo.

---

## TTS

Criar `SpeechManager`.

Idioma principal:

`pt-PT`

Usar Android TextToSpeech.

Configuração de velocidade nas definições.

Alertas críticos interrompem fala menos importante.

---

## VIBRAÇÃO

Criar `HapticManager`.

Padrões:

```text
informação = 1 curto
atenção = 2 pulsos
perigo = 3 pulsos rápidos
erro = 1 longo
```

Permitir desativar.

---

## UI

Criar interface altamente acessível.

O ecrã principal deve ter:

* status do modelo;
* status backend;
* Camera Preview;
* botão grande `Analisar agora`;
* seletor de modo;
* último resultado;
* botão repetir áudio.

TalkBack deve conseguir operar toda a aplicação.

Usa `contentDescription` adequada.

Não utilizar apenas ícones sem descrição.

Não depender de cor para comunicar estado.

---

## DEFINIÇÕES

Adicionar:

* modelo atual;
* trocar modelo;
* local do modelo;
* armazenamento interno/SD;
* TTS on/off;
* velocidade de fala;
* vibração;
* análise automática;
* intervalo;
* backend Auto/GPU/CPU;
* diagnóstico.

---

## DIAGNÓSTICO

Mostrar:

* model path;
* model size;
* armazenamento;
* LiteRT-LM version;
* engine state;
* backend;
* tempo último load;
* tempo última inferência;
* memória disponível;
* último erro.

---

## ARQUITETURA

Quero separação semelhante a:

```text
core/model
core/camera
core/storage
core/speech
core/haptics
feature/onboarding
feature/assistant
feature/settings
data/preferences
```

Usa MVVM.

StateFlow para estado de UI.

Não colocar lógica do modelo dentro da Activity.

---

## PERFORMANCE

Regras obrigatórias:

* um Engine;
* uma inferência de cada vez;
* nenhuma fila infinita;
* descartar frames antigos;
* engine carregado entre inferências;
* não reinstanciar LLM a cada foto;
* não bloquear main thread;
* não conservar Bitmaps desnecessariamente;
* fechar `ImageProxy`;
* liberar recursos no lifecycle correto.

Avalia MTP/speculative decoding em GPU conforme suporte atual do LiteRT-LM/Gemma 4 E2B, mas implementa de forma que possa ser desligado em caso de incompatibilidade.

---

## PRIVACIDADE

Offline first.

Não adicionar:

* Firebase Analytics;
* crash reporting cloud;
* tracking;
* conta obrigatória;
* login;
* upload de fotografia;
* API LLM externa.

Não guardar frames por padrão.

---

## SEGURANÇA

Especial atenção para:

* estradas;
* passadeiras;
* veículos;
* escadas;
* buracos;
* vidro;
* obras;
* obstáculos.

Nunca transformar uma inferência visual numa garantia de segurança.

---

## TESTES

Implementa testes para:

* seleção do modelo;
* modelo inválido;
* cópia de 2+ GB;
* falta de espaço;
* armazenamento removido;
* GPU failure;
* CPU fallback;
* permissão de câmara negada;
* TTS indisponível;
* inferência concorrente;
* cancelamento;
* lifecycle;
* rotação.

---

## ENTREGA

Não quero apenas um exemplo ou protótipo parcial.

Produz um projeto Android compilável.

Ao terminar:

1. executa build;
2. corrige todos os erros de compilação;
3. executa testes;
4. faz análise estática;
5. remove imports mortos;
6. remove código mock que não seja necessário;
7. garante que não existem TODOs críticos;
8. produz APK debug;
9. documenta como instalar;
10. documenta como copiar/importar `gemma-4-e2b.litertlm`;
11. documenta como testar inferência multimodal.

Cria também:

`README.md`

com:

* requisitos;
* build;
* instalação;
* modelo;
* SD card;
* GPU;
* fallback CPU;
* troubleshooting.

---

## IMPORTANTE

Não substituas uma API que não conheces por pseudocódigo.

Se a API do LiteRT-LM instalada diferir dos exemplos:

1. consulta a documentação e o código da versão real;
2. adapta;
3. compila;
4. só considera concluído quando o código compilar.

Não inventes classes ou métodos LiteRT-LM.

---

## CRITÉRIO FINAL

Considera a tarefa terminada apenas quando este fluxo funcionar:

```text
Instalar APK
  ↓
Abrir
  ↓
Selecionar gemma-4-e2b.litertlm
  ↓
Escolher interno ou SD
  ↓
Modelo inicializa
  ↓
CameraX inicia
  ↓
Capturar imagem
  ↓
Gemma 4 recebe imagem
  ↓
Resposta pt-PT
  ↓
TTS fala
  ↓
Vibração
  ↓
Nova análise sem recarregar modelo
```

O projeto deve conseguir executar este fluxo **sem Internet**.
