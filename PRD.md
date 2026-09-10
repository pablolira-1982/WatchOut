# PRD — ASSISTENTE VISUAL MOBILE OFFLINE

## Produto

Aplicação Android de assistência visual para pessoas cegas e pessoas com baixa visão, utilizando Gemma 4 E2B multimodal local através de LiteRT-LM.

---

# Problema

Pessoas com deficiência visual frequentemente necessitam de informação imediata sobre:

* obstáculos;
* orientação espacial;
* objetos;
* escadas;
* portas;
* placas;
* produtos;
* transportes;
* texto;
* riscos no ambiente.

Soluções baseadas exclusivamente em cloud possuem problemas de:

* latência;
* privacidade;
* falta de Internet;
* custo recorrente;
* indisponibilidade.

---

# Proposta de valor

Executar a inteligência visual diretamente no smartphone.

```text
Câmara
  ↓
Gemma 4 E2B local
  ↓
interpretação visual
  ↓
Português de Portugal
  ↓
TTS + vibração
```

Sem necessidade de enviar fotografias para servidores externos.

---

# Público-alvo

## Primário

* pessoas cegas;
* pessoas com baixa visão.

## Secundário

* familiares;
* cuidadores;
* instituições;
* associações de apoio;
* profissionais de acessibilidade.

---

# Objetivos do MVP

O utilizador deve conseguir:

1. apontar a câmara;
2. pedir uma análise;
3. receber verbalmente informação útil;
4. identificar obstáculos;
5. receber orientação esquerda/direita/frente;
6. pedir descrição do ambiente;
7. localizar objetos;
8. ler texto visível;
9. receber alertas sobre riscos;
10. utilizar tudo offline.

---

# Funcionalidades P0

Obrigatórias para primeira versão:

* Android nativo;
* CameraX;
* Gemma 4 E2B LiteRT-LM;
* seleção/importação do modelo;
* armazenamento interno;
* suporte a microSD quando disponível;
* GPU com fallback CPU;
* análise manual;
* modo automático configurável;
* TTS pt-PT;
* vibração;
* acessibilidade TalkBack;
* modo Segurança;
* modo Descrição;
* modo Encontrar objeto;
* modo Ler texto;
* estado do modelo;
* tratamento de erros.

---

# Funcionalidades P1

Depois do MVP:

* produtos/supermercado;
* transporte público;
* melhorias em passadeiras;
* comandos de voz offline;
* favoritos;
* perfis de resposta;
* benchmark no dispositivo;
* NPU;
* histórico opcional;
* headset/Bluetooth;
* smartwatch/hápticos externos.

---

# Jornada inicial

```text
Abrir aplicação
     ↓
Explicação acessível
     ↓
Permissão da câmara
     ↓
Selecionar modelo
     ↓
Escolher armazenamento
     ↓
Importar
     ↓
Modelo a inicializar
     ↓
Modelo pronto
     ↓
Câmara
```

---

# Jornada diária

```text
Abrir
 ↓
modelo carregado
 ↓
câmara ativa
 ↓
Analisar
 ↓
LLM
 ↓
"Atenção: há uma cadeira diretamente à sua frente."
 ↓
TTS
 ↓
vibração
```

---

# Requisitos de UX

A aplicação deve ser utilizável sem depender da visão.

Prioridades:

1. áudio;
2. TalkBack;
3. vibração;
4. interface visual.

Não o contrário.

---

# Métricas técnicas

MVP deve medir localmente:

* tempo de inicialização;
* tempo até primeira resposta;
* tempo médio por inferência;
* backend utilizado;
* número de falhas de inferência;
* memória livre.

Estas métricas não devem ser enviadas para cloud.

---

# Segurança funcional

O produto é uma tecnologia assistiva e não substitui:

* bengala;
* cão-guia;
* sinais sonoros;
* orientação e mobilidade;
* avaliação direta do ambiente.

Especialmente em vias públicas:

o modelo nunca deve ser a única autoridade para determinar que é seguro atravessar.

---

# Privacidade

Por padrão:

* nenhuma fotografia persistida;
* nenhuma gravação enviada;
* nenhum processamento cloud;
* nenhum tracking;
* nenhum login obrigatório;
* nenhuma conta necessária.

---

# Modelo

Arquivo fornecido:

`gemma-4-e2b.litertlm`

Tamanho aproximado:

`2.41 GB`

O app deve considerar o bundle como um modelo externo configurável.

Não pressupor pelo nome se contém ou não fine-tuning específico.

A responsabilidade do app é carregar o `.litertlm` compatível fornecido.

---

# Distribuição

O APK/AAB deve permanecer relativamente pequeno.

O modelo será:

* copiado manualmente;
* importado pelo utilizador;
* ou futuramente obtido por mecanismo separado.

Não colocar o modelo dentro de `assets/` no MVP.

---

# Definição de sucesso

Uma primeira versão é considerada utilizável quando uma pessoa consegue instalar o app, importar o modelo, apontar a câmara para uma cadeira e ouvir uma resposta como:

`Atenção: há uma cadeira diretamente à sua frente. Existe passagem pelo lado esquerdo.`

sem qualquer comunicação com um servidor externo.
