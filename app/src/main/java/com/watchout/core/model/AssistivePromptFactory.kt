package com.watchout.core.model

/**
 * Modo de assistência selecionado pelo utilizador.
 */
enum class AssistanceMode(val label: String, val emoji: String) {
    SAFETY("Segurança", "🛡"),
    DESCRIBE("Descrever", "📋"),
    FIND("Procurar", "🔍"),
    READ_TEXT("Ler texto", "📄"),
    PRODUCTS("Produtos", "🛒"),
    TRANSPORT("Transportes", "🚌"),
    CROSSWALK("Passadeira", "🚶"),
}

/**
 * Gera os prompts de sistema baseados no modo selecionado.
 */
object AssistivePromptFactory {

    data class SamplingProfile(val temperature: Double, val topK: Int, val topP: Double)

    /** Valores conservadores para funções que exigem precisão. */
    fun samplingForMode(mode: AssistanceMode): SamplingProfile = when (mode) {
        AssistanceMode.SAFETY -> SamplingProfile(temperature = 0.05, topK = 12, topP = 0.75)
        AssistanceMode.READ_TEXT -> SamplingProfile(temperature = 0.0, topK = 1, topP = 1.0)
        AssistanceMode.FIND, AssistanceMode.CROSSWALK, AssistanceMode.TRANSPORT ->
            SamplingProfile(temperature = 0.08, topK = 16, topP = 0.8)
        AssistanceMode.DESCRIBE, AssistanceMode.PRODUCTS ->
            SamplingProfile(temperature = 0.15, topK = 24, topP = 0.9)
    }

    private val BASE_SYSTEM_PROMPT = """
        És um assistente visual para uma pessoa cega ou com baixa visão.
        Responde sempre em Português de Portugal.
        Prioriza segurança e informação útil.
        As respostas devem ser curtas, claras e objetivas.
        Para orientação espacial utiliza termos como: à esquerda, à direita, diretamente à frente, ao fundo, junto ao chão e à altura da cabeça.
        Não inventes objetos, texto, pessoas, distâncias ou perigos que não consigas confirmar visualmente.
        Quando houver incerteza, diz claramente que não consegues confirmar.
        Em situações potencialmente perigosas, começa pelo aviso mais importante.
        Nunca afirmes que é seguro atravessar uma estrada apenas com base numa imagem.
        Evita descrições decorativas quando existe informação de segurança mais importante.
    """.trimIndent()

    fun getPromptForMode(mode: AssistanceMode): String {
        val modeInstruction = when (mode) {
            AssistanceMode.SAFETY ->
                "Identifica apenas obstáculos, riscos e informação necessária para eu continuar em segurança. Responde de forma curta e objetiva."
            AssistanceMode.DESCRIBE ->
                "Descreve brevemente o ambiente à minha frente: tipo de espaço, objetos principais e passagens disponíveis."
            AssistanceMode.FIND ->
                "Procura o objeto que vou mencionar e indica a sua posição relativa usando termos como à esquerda, à direita, ao fundo."
            AssistanceMode.READ_TEXT ->
                "Lê todo o texto visível na imagem: placas, preços, etiquetas, números. Se o texto estiver ilegível, informa."
            AssistanceMode.PRODUCTS ->
                "Identifica produtos visíveis, embalagens, preços e a sua posição em prateleiras."
            AssistanceMode.TRANSPORT ->
                "Identifica autocarros, números de linha, paragens e elementos relevantes de transporte público."
            AssistanceMode.CROSSWALK ->
                "Informa o que vês: passadeira, semáforo e veículos. NUNCA digas que é seguro atravessar apenas com base na imagem."
        }
        return "$BASE_SYSTEM_PROMPT\n\nInstrução específica: $modeInstruction"
    }

    /** Prompt curto enviado junto com a imagem em cada turno da conversa. */
    fun getUserPromptForMode(mode: AssistanceMode): String {
        return when (mode) {
            AssistanceMode.SAFETY ->
                "Diz em no máximo 12 palavras apenas o risco ou obstáculo mais próximo à frente, à esquerda ou à direita. Se não houver risco visível, responde: sem obstáculo visível."
            AssistanceMode.READ_TEXT ->
                "Lê o texto visível nesta imagem e responde apenas com o texto que conseguires confirmar."
            AssistanceMode.FIND ->
                "Procura o objeto mais importante visível e indica se está à esquerda, à direita ou à frente."
            AssistanceMode.DESCRIBE ->
                "Descreve brevemente o que está à frente e indica as posições relativas."
            AssistanceMode.PRODUCTS ->
                "Lista os produtos e preços que conseguires confirmar, com a posição de cada um."
            AssistanceMode.TRANSPORT ->
                "Identifica os elementos de transporte e números de linha que conseguires confirmar."
            AssistanceMode.CROSSWALK ->
                "Identifica passadeira, semáforos e veículos visíveis. Não concluas que é seguro atravessar."
        }
    }
}
