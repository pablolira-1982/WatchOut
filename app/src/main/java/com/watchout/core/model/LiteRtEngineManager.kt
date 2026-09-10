package com.watchout.core.model

import android.content.Context
import android.graphics.Bitmap
import android.os.Environment
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/**
 * Manager único do LiteRT-LM.
 * - Garante inicialização em background
 * - Tenta GPU primeiro e faz fallback para CPU
 * - Mantém o estado do modelo disponível para a UI
 * - Reutiliza a conversa entre frames, como no projeto WatchOut original
 */
class LiteRtEngineManager(
    private val context: Context
) {
    private val initMutex = Mutex()
    private val analysisMutex = Mutex()

    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private var conversationSystemPrompt: String? = null
    private var conversationMessageCount = 0
    private var configuredModelPath: String? = null

    private val _modelState = MutableStateFlow<ModelState>(ModelState.NotConfigured)
    val modelState: StateFlow<ModelState> = _modelState.asStateFlow()

    suspend fun initialize(modelPath: String): ModelState = initMutex.withLock {
        if (configuredModelPath == modelPath && engine != null && _modelState.value is ModelState.Ready) {
            return@withLock _modelState.value
        }

        val modelFile = File(modelPath)
        if (!modelFile.exists() || !modelFile.isFile || modelFile.length() <= 0L) {
            val error = ModelState.Error("Modelo não encontrado em $modelPath")
            _modelState.value = error
            return@withLock error
        }
        if (!modelFile.canRead()) {
            val error = ModelState.Error("Sem permissão para ler o modelo em $modelPath")
            _modelState.value = error
            return@withLock error
        }

        _modelState.value = ModelState.Loading
        Log.i(TAG, "A carregar ${modelFile.name} (${formatBytes(modelFile.length())}) de $modelPath")

        val readyState = withContext(Dispatchers.IO) {
            initializeOnBackground(modelPath)
        }

        _modelState.value = readyState
        if (readyState is ModelState.Ready) {
            configuredModelPath = modelPath
        }
        readyState
    }

    private fun initializeOnBackground(modelPath: String): ModelState {
        shutdownEngine()

        val cacheDir = File(context.cacheDir, "litertlm").also { it.mkdirs() }

        // Alguns drivers OpenCL de Android falham já na criação do motor. CPU
        // primeiro evita contaminar a tentativa seguinte com um delegate GPU
        // que não conseguiu inicializar; GPU continua como alternativa para
        // aparelhos compatíveis.
        val attempts = listOf(
            "CPU + visão CPU" to (Backend.CPU() to Backend.CPU()),
            "GPU + visão CPU" to (Backend.GPU() to Backend.CPU())
        )

        val errors = mutableListOf<String>()
        for ((label, backends) in attempts) {
            try {
                val newEngine = Engine(
                    EngineConfig(
                        modelPath = modelPath,
                        backend = backends.first,
                        visionBackend = backends.second,
                        // A sessão é reiniciada por frame. 2048 é mais que
                        // suficiente para a imagem atual (cerca de 510 tokens)
                        // e evita reservar o contexto de 8192 na CPU.
                        maxNumTokens = 2048,
                        cacheDir = cacheDir.absolutePath
                    )
                )
                newEngine.initialize()
                engine = newEngine
                return ModelState.Ready(label)
            } catch (t: Throwable) {
                errors += "$label: ${t.message ?: t::class.java.simpleName}"
                shutdownEngine()
            }
        }

        return ModelState.Error(
            buildFailureDiagnostic(File(modelPath), errors)
        )
    }

    /**
     * Quando o motor nativo rejeita o modelo, confirma se a cópia no telefone
     * ainda é o artefacto oficial. O cálculo só ocorre na falha, portanto não
     * atrasa os arranques que funcionam.
     */
    private fun buildFailureDiagnostic(modelFile: File, errors: List<String>): String {
        val storage = when {
            modelFile.absolutePath.startsWith(context.filesDir.absolutePath) -> "memória interna privada"
            Environment.isExternalStorageRemovable(modelFile) -> "cartão SD removível"
            else -> "armazenamento externo/interno partilhado"
        }
        val integrity = if (modelFile.length() == OFFICIAL_MODEL_BYTES) {
            val digest = sha256(modelFile)
            if (digest == OFFICIAL_MODEL_SHA256) {
                "SHA-256 oficial confirmado"
            } else {
                "SHA-256 diferente do modelo oficial: ${digest?.take(16) ?: "não foi possível calcular"}"
            }
        } else {
            "Tamanho diferente do modelo oficial de ${formatBytes(OFFICIAL_MODEL_BYTES)}"
        }

        return "Ficheiro carregado: ${modelFile.name} (${formatBytes(modelFile.length())}, $storage). " +
            "$integrity. Falha ao inicializar o motor LiteRT-LM. " + errors.joinToString(" | ")
    }

    private fun sha256(file: File): String? = runCatching {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }.getOrNull()

    suspend fun analyze(
        bitmap: Bitmap,
        userPrompt: String,
        systemPrompt: String,
        topK: Int = 40,
        topP: Double = 0.95,
        temperature: Double = 0.2
    ): Result<String> = analysisMutex.withLock {
        withContext(Dispatchers.IO) {
            val activeEngine = engine
                ?: return@withContext Result.failure(IllegalStateException("Motor não inicializado."))

            // O protótipo original analisava apenas os 30% inferiores do frame.
            // Isso perdia obstáculos à frente e à altura da cabeça. Mantemos o
            // enquadramento completo, mas reduzido, para entregar um frame
            // recente sem transformar a aplicação num processador de vídeo.
            val preparedBitmap = bitmap.resizeForInference()
            try {
                // Para alertas por câmara, cada frame é uma observação nova.
                // Guardar imagens anteriores na conversa faz os tokens crescer
                // a cada ciclo até o motor rejeitar a entrada.
                resetConversation()
                val activeConversation = getOrCreateConversation(
                    engine = activeEngine,
                    systemPrompt = systemPrompt,
                    topK = topK,
                    topP = topP,
                    temperature = temperature
                )
                val jpegBytes = preparedBitmap.toJpegBytes(quality = 75)
                val response = withTimeoutOrNull(INFERENCE_TIMEOUT_MS) {
                    activeConversation.sendMessage(
                        Contents.of(
                            listOf(
                                Content.ImageBytes(jpegBytes),
                                Content.Text(userPrompt)
                            )
                        )
                    )
                } ?: run {
                    Log.w(TAG, "Inferência excedeu ${INFERENCE_TIMEOUT_MS}ms; a conversa será reiniciada")
                    resetConversation()
                    return@withContext Result.failure(
                        IllegalStateException("A análise excedeu o tempo limite de 30 segundos.")
                    )
                }

                conversationMessageCount++
                val text = response.contents.contents
                    .filterIsInstance<Content.Text>()
                    .joinToString(separator = "") { it.text }
                    .trim()

                Log.i(TAG, "Resposta LiteRT-LM recebida (${text.length} caracteres)")
                if (text.isEmpty()) {
                    Result.failure(IllegalStateException("O modelo devolveu uma resposta vazia."))
                } else {
                    Result.success(text)
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Falha durante a inferência LiteRT-LM", t)
                // Uma conversa que falhou pode manter estado JNI inválido. O
                // próximo frame começa limpo e o motor continua reutilizável.
                resetConversation()
                Result.failure(t)
            } finally {
                if (preparedBitmap !== bitmap && !preparedBitmap.isRecycled) {
                    preparedBitmap.recycle()
                }
            }
        }
    }

    fun shutdown() {
        if (initMutex.tryLock()) {
            try {
                shutdownEngine()
                configuredModelPath = null
                _modelState.value = ModelState.NotConfigured
            } finally {
                initMutex.unlock()
            }
        } else {
            shutdownEngine()
            configuredModelPath = null
            _modelState.value = ModelState.NotConfigured
        }
    }

    /** Apaga imagens e respostas anteriores, mantendo o modelo carregado. */
    fun clearSession() {
        resetConversation()
    }

    /** Aguarda a inferência atual fora da interface antes de liberar o motor. */
    suspend fun shutdownWhenIdle() {
        analysisMutex.withLock {
            shutdown()
        }
    }

    private fun shutdownEngine() {
        resetConversation()
        engine?.close()
        engine = null
    }

    private fun getOrCreateConversation(
        engine: Engine,
        systemPrompt: String,
        topK: Int,
        topP: Double,
        temperature: Double
    ): Conversation {
        val existing = conversation
        if (
            existing != null &&
            existing.isAlive &&
            conversationSystemPrompt == systemPrompt &&
            conversationMessageCount < MAX_CONVERSATION_MESSAGES
        ) {
            return existing
        }

        resetConversation()
        return engine.createConversation(
            ConversationConfig(
                systemInstruction = Contents.of(systemPrompt),
                samplerConfig = SamplerConfig(
                    topK = topK,
                    topP = topP,
                    temperature = temperature
                )
            )
        ).also {
            conversation = it
            conversationSystemPrompt = systemPrompt
            conversationMessageCount = 0
        }
    }

    private fun resetConversation() {
        conversation?.close()
        conversation = null
        conversationSystemPrompt = null
        conversationMessageCount = 0
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024L) return "$bytes B"
        val units = arrayOf("B", "KiB", "MiB", "GiB", "TiB")
        var value = bytes.toDouble()
        var unit = 0
        while (value >= 1024.0 && unit < units.lastIndex) {
            value /= 1024.0
            unit++
        }
        return "%.2f %s".format(value, units[unit])
    }

    private fun Bitmap.toJpegBytes(quality: Int = 85): ByteArray {
        val stream = ByteArrayOutputStream()
        compress(Bitmap.CompressFormat.JPEG, quality, stream)
        return stream.toByteArray()
    }

    private fun Bitmap.resizeForInference(maxLongestSide: Int = 640): Bitmap {
        val longestSide = maxOf(width, height)
        if (longestSide <= maxLongestSide) return this
        val scale = maxLongestSide.toFloat() / longestSide.toFloat()
        return Bitmap.createScaledBitmap(
            this,
            (width * scale).toInt().coerceAtLeast(1),
            (height * scale).toInt().coerceAtLeast(1),
            true
        )
    }

    companion object {
        private const val TAG = "WatchOutLiteRT"
        private const val INFERENCE_TIMEOUT_MS = 30_000L
        private const val MAX_CONVERSATION_MESSAGES = 20
        private const val OFFICIAL_MODEL_BYTES = 2_588_147_712L
        private const val OFFICIAL_MODEL_SHA256 =
            "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c"
    }
}
