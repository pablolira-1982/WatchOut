package com.watchout.feature.assistant

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.watchout.core.camera.CameraController
import com.watchout.core.haptics.HapticManager
import com.watchout.core.model.AssistivePromptFactory
import com.watchout.core.model.AssistanceMode
import com.watchout.core.model.LiteRtEngineManager
import com.watchout.core.model.ModelState
import com.watchout.core.speech.SpeechManager
import com.watchout.ui.theme.CardSurface
import com.watchout.ui.theme.SuccessGreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@Composable
fun AssistantScreen(
    modelPath: String,
    initialMode: AssistanceMode = AssistanceMode.SAFETY,
    onBack: () -> Unit,
    onExit: () -> Unit,
    onOpenSettings: () -> Unit,
    showSettings: Boolean,
    onCloseSettings: () -> Unit,
    onChangeModel: () -> Unit,
    viewModel: AssistantViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val speech = remember { SpeechManager(context) }
    val haptics = remember { HapticManager(context) }
    val engine = remember { LiteRtEngineManager(context) }
    val engineState by engine.modelState.collectAsStateWithLifecycle()
    var latestFrame by remember { mutableStateOf<Bitmap?>(null) }
    var analysisRequested by remember { mutableStateOf(false) }
    var showModes by remember { mutableStateOf(false) }
    var retryModel by remember { mutableIntStateOf(0) }
    var showFullModelError by remember { mutableStateOf(false) }
    var voiceEnabled by remember { mutableStateOf(true) }
    var hapticsEnabled by remember { mutableStateOf(true) }
    var lastSafetyAlert by remember { mutableStateOf("") }
    var lastSafetyAlertAt by remember { mutableLongStateOf(0L) }
    var activeAnalysisJob by remember { mutableStateOf<Job?>(null) }
    val cleanupScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.IO) }
    val selectedModeState = rememberUpdatedState(state.selectedMode)

    LaunchedEffect(initialMode) {
        viewModel.selectMode(initialMode)
    }

    var controllerRef: CameraController? = null
    val cameraController = remember {
        CameraController(context, lifecycleOwner) { bitmap ->
            latestFrame?.takeIf { it !== bitmap && !it.isRecycled }?.recycle()
            latestFrame = bitmap
            if (analysisRequested) {
                analysisRequested = false
                controllerRef?.onInferenceStarted()
                activeAnalysisJob = scope.launch {
                    val startedAt = SystemClock.elapsedRealtime()
                    val selectedMode = selectedModeState.value
                    val sampling = AssistivePromptFactory.samplingForMode(selectedMode)
                    val result = engine.analyze(
                        bitmap = bitmap,
                        userPrompt = AssistivePromptFactory.getUserPromptForMode(selectedMode),
                        systemPrompt = AssistivePromptFactory.getPromptForMode(selectedMode),
                        topK = sampling.topK,
                        topP = sampling.topP,
                        temperature = sampling.temperature
                    )
                    val elapsed = SystemClock.elapsedRealtime() - startedAt
                    result.fold(
                        onSuccess = { response ->
                            viewModel.onAnalysisComplete(response, elapsed)
                            haptics.vibrateAttention()
                            val now = SystemClock.elapsedRealtime()
                            val normalized = response.lowercase().trim()
                            val shouldRepeatSafetyAlert =
                                selectedMode == AssistanceMode.SAFETY &&
                                    (normalized != lastSafetyAlert || now - lastSafetyAlertAt >= 8_000L)
                            if (selectedMode == AssistanceMode.SAFETY && shouldRepeatSafetyAlert) {
                                lastSafetyAlert = normalized
                                lastSafetyAlertAt = now
                                speech.speakUrgent(response)
                            } else if (selectedMode != AssistanceMode.SAFETY) {
                                speech.speak(response)
                            }
                        },
                        onFailure = { error ->
                            val message = userFriendlyAnalysisError(error)
                            viewModel.onAnalysisComplete(message, elapsed)
                            haptics.vibrateError()
                            speech.speakUrgent(message)
                        }
                    )
                    controllerRef?.onInferenceComplete()
                    if (!bitmap.isRecycled) bitmap.recycle()
                    latestFrame = null
                }
            }
        }
    }.also { controllerRef = it }

    DisposableEffect(Unit) {
        speech.initialize { }
        onDispose {
            analysisRequested = false
            activeAnalysisJob?.cancel()
            cameraController.shutdown()
            // Fechar o motor JNI durante sendMessage bloqueava a navegação.
            // A câmara fecha já; o motor é liberado em segundo plano quando a
            // inferência nativa terminar.
            cleanupScope.launch { engine.shutdownWhenIdle() }
            speech.shutdown()
            latestFrame?.takeIf { !it.isRecycled }?.recycle()
        }
    }

    LaunchedEffect(modelPath, retryModel) {
        when (val result = engine.initialize(modelPath)) {
            is ModelState.Ready -> {
                viewModel.onModelReady(result.backend)
                haptics.vibrateInfo()
                speech.speak("Modelo pronto. A câmara está disponível.")
            }
            is ModelState.Error -> {
                viewModel.onModelError(result.message)
                haptics.vibrateError()
                speech.speakUrgent("Erro ao iniciar o modelo. ${result.message}")
            }
            else -> Unit
        }
    }

    // O modo automático segue o comportamento do protótipo original, mas fica
    // opt-in durante a validação para não disparar inferências repetidas antes
    // de o utilizador confirmar que o modelo carregou corretamente.
    LaunchedEffect(engineState, state.autoAnalysisEnabled, state.isAnalyzing) {
        if (engineState !is ModelState.Ready || !state.autoAnalysisEnabled || state.isAnalyzing) return@LaunchedEffect
        delay(state.autoIntervalSeconds.coerceAtLeast(1) * 1_000L)
        if (engineState is ModelState.Ready && !state.isAnalyzing) {
            viewModel.onAnalysisStarted()
            analysisRequested = true
            cameraController.requestAnalysis()
        }
    }

    BackHandler {
        haptics.vibrateInfo()
        speech.speak("A voltar.")
        if (showSettings) onCloseSettings() else onBack()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                haptics.vibrateInfo()
                speech.speak("A voltar.")
                onBack()
            }) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = "Voltar", tint = Color.White)
            }
            Text(
                text = "WatchOut",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = Color.White)
            )
            IconButton(onClick = {
                haptics.vibrateInfo()
                speech.speak("A abrir definições.")
                onOpenSettings()
            }) {
                Icon(Icons.Rounded.Settings, contentDescription = "Definições", tint = Color.White)
            }
        }

        val (statusText, statusColor) = when (val ms = engineState) {
            is ModelState.Ready -> "Modelo pronto (${ms.backend})" to SuccessGreen
            is ModelState.Loading -> "A carregar modelo…" to MaterialTheme.colorScheme.primary
            is ModelState.Error -> "Erro no modelo" to Color(0xFFE53935)
            ModelState.NotConfigured -> "Nenhum modelo configurado" to Color(0xFFFF9800)
        }
        Row(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(CardSurface)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(statusColor))
            Spacer(Modifier.width(8.dp))
            Text(statusText, color = statusColor, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }

        if (engineState is ModelState.Error) {
            val errorMessage = (engineState as ModelState.Error).message
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF32151A))
                    .padding(12.dp)
            ) {
                Text(
                    text = "Detalhes do erro",
                    color = Color(0xFFFF8A80),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    text = errorMessage,
                    color = Color.White,
                    fontSize = 12.sp,
                    maxLines = 4,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    TextButton(
                        onClick = {
                        haptics.vibrateInfo()
                        speech.speak("A tentar carregar o modelo novamente.")
                        retryModel++
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Tentar novamente") }
                    TextButton(
                        onClick = {
                        speech.speak("A abrir a escolha de outro modelo.")
                        onChangeModel()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Mudar modelo") }
                    TextButton(
                        onClick = {
                        haptics.vibrateInfo()
                        showFullModelError = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Ver diagnóstico completo") }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF0D1117)),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).also { previewView ->
                        previewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        cameraController.startCamera(previewView)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = {
                haptics.vibrateInfo()
                speech.speak("A analisar a imagem.")
                viewModel.onAnalysisStarted()
                analysisRequested = true
                cameraController.requestAnalysis()
            },
            enabled = engineState is ModelState.Ready && !state.isAnalyzing,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .height(64.dp),
            shape = RoundedCornerShape(20.dp)
        ) {
            if (state.isAnalyzing) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Text("A analisar…", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            } else {
                Icon(Icons.Rounded.CameraAlt, contentDescription = null, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                Text("Analisar agora", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            listOf(AssistanceMode.SAFETY, AssistanceMode.READ_TEXT, AssistanceMode.FIND).forEach { mode ->
                ModeButton(mode, mode.emoji, mode.label, state.selectedMode == mode) {
                    viewModel.selectMode(mode)
                    haptics.vibrateInfo()
                    speech.speak("Modo ${mode.label} selecionado.")
                }
            }
            ModeButton(AssistanceMode.DESCRIBE, "…", "Mais", false) {
                haptics.vibrateInfo()
                speech.speak("A abrir modos de pesquisa.")
                showModes = true
            }
        }

        Spacer(Modifier.height(16.dp))
        if (state.lastResult.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(CardSurface)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    haptics.vibrateInfo()
                    speech.speakUrgent(state.lastResult)
                }) {
                    Icon(Icons.Rounded.VolumeUp, contentDescription = "Ouvir resultado", tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = state.lastResult,
                    color = Color.White,
                    fontSize = 15.sp,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { contentDescription = state.lastResult }
                )
            }
        }
        Spacer(Modifier.height(16.dp))
    }

    if (showModes) {
        AlertDialog(
            onDismissRequest = {
                showModes = false
                speech.speak("Lista de modos fechada.")
            },
            title = { Text("Escolher modo de pesquisa") },
            text = {
                Column {
                    AssistanceMode.values().forEach { mode ->
                        TextButton(
                            onClick = {
                                viewModel.selectMode(mode)
                                showModes = false
                                haptics.vibrateInfo()
                                speech.speak("Modo ${mode.label} selecionado.")
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("${mode.emoji}  ${mode.label} — ${modeHelp(mode)}") }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showModes = false
                    speech.speak("Fechar modos.")
                }) { Text("Fechar") }
            }
        )
    }

    if (showSettings) {
        AlertDialog(
            onDismissRequest = onCloseSettings,
            title = { Text("Definições") },
            text = {
                Column {
                    SettingSwitch("Voz e alertas", voiceEnabled) {
                        voiceEnabled = it
                        speech.isEnabled = it
                        speech.speak(if (it) "Voz ativada." else "Voz desativada.")
                    }
                    SettingSwitch("Vibração", hapticsEnabled) {
                        hapticsEnabled = it
                        haptics.isEnabled = it
                        if (it) speech.speak("Vibração ativada.")
                    }
                    SettingSwitch("Análise automática", state.autoAnalysisEnabled) {
                        viewModel.toggleAutoAnalysis(it)
                        haptics.vibrateInfo()
                        speech.speak(if (it) "Análise automática ativada." else "Análise automática desativada.")
                    }
                    TextButton(
                        onClick = {
                            speech.speak("A voltar para escolher outro modelo.")
                            onChangeModel()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Mudar modelo LiteRT / SD") }
                    TextButton(
                        onClick = {
                            engine.clearSession()
                            viewModel.clearLastResult()
                            haptics.vibrateInfo()
                            speech.speakUrgent("Sessão apagada. As imagens e respostas anteriores não serão usadas.")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Limpar sessão") }
                    TextButton(
                        onClick = {
                            haptics.vibrateInfo()
                            speech.speak("A sair da aplicação.")
                            onExit()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Sair da aplicação") }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    speech.speak("Definições fechadas.")
                    onCloseSettings()
                }) { Text("Fechar") }
            }
        )
    }

    if (showFullModelError && engineState is ModelState.Error) {
        AlertDialog(
            onDismissRequest = { showFullModelError = false },
            title = { Text("Diagnóstico completo") },
            text = {
                SelectionContainer {
                    Column(
                        modifier = Modifier
                            .heightIn(max = 360.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text((engineState as ModelState.Error).message)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showFullModelError = false }) { Text("Fechar") }
            }
        )
    }
}

private fun userFriendlyAnalysisError(error: Throwable): String {
    val technicalMessage = error.message.orEmpty()
    return when {
        technicalMessage.contains("token", ignoreCase = true) ->
            "A imagem demorou demasiado a preparar. Vou tentar novamente com a próxima imagem."
        technicalMessage.contains("timeout", ignoreCase = true) ||
            technicalMessage.contains("tempo limite", ignoreCase = true) ->
            "A análise demorou mais do que o esperado. Vou tentar novamente."
        else -> "Não foi possível analisar esta imagem. Vou tentar novamente."
    }
}

private fun modeHelp(mode: AssistanceMode): String = when (mode) {
    AssistanceMode.SAFETY -> "obstáculos e riscos"
    AssistanceMode.READ_TEXT -> "placas e etiquetas"
    AssistanceMode.FIND -> "encontrar objetos"
    AssistanceMode.DESCRIBE -> "descrever o ambiente"
    AssistanceMode.PRODUCTS -> "produtos e preços"
    AssistanceMode.TRANSPORT -> "transportes e linhas"
    AssistanceMode.CROSSWALK -> "passadeira e trânsito"
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = label },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun ModeButton(
    mode: AssistanceMode,
    emoji: String,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.semantics {
            contentDescription = "Modo $label${if (isSelected) ", selecionado" else ""}"
        }
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (isSelected) MaterialTheme.colorScheme.primary else CardSurface)
        ) { Text(emoji, fontSize = 22.sp, textAlign = TextAlign.Center) }
        Spacer(Modifier.height(4.dp))
        Text(label, color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
    }
}
