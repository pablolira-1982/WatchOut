package com.watchout.feature.home

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.watchout.core.haptics.HapticManager
import com.watchout.core.model.AssistanceMode
import com.watchout.core.speech.SpeechManager
import com.watchout.ui.theme.CardSurface

@Composable
fun AccessibleHomeScreen(
    showInitialGuide: Boolean,
    onGuideComplete: () -> Unit,
    onOpenCamera: (AssistanceMode) -> Unit,
    onChangeModel: () -> Unit,
    onExit: () -> Unit
) {
    val context = LocalContext.current
    val speech = remember { SpeechManager(context) }
    val haptics = remember { HapticManager(context) }
    var selectedMode by remember { mutableStateOf(AssistanceMode.SAFETY) }
    var showVoiceSetup by remember { mutableStateOf(false) }
    var speechReady by remember { mutableStateOf(false) }

    fun selectMode(mode: AssistanceMode) {
        selectedMode = mode
        haptics.vibrateInfo()
        speech.speak("${mode.label}. ${modeDescription(mode)}. Escolha Abrir câmara para continuar.")
    }

    fun handleVoiceCommand(command: String) {
        when {
            command.contains("seguran", true) -> selectMode(AssistanceMode.SAFETY)
            command.contains("texto", true) || command.contains("ler", true) -> selectMode(AssistanceMode.READ_TEXT)
            command.contains("procur", true) || command.contains("encontr", true) -> selectMode(AssistanceMode.FIND)
            command.contains("descrev", true) || command.contains("ambiente", true) -> selectMode(AssistanceMode.DESCRIBE)
            command.contains("compra", true) || command.contains("supermerc", true) || command.contains("produto", true) -> selectMode(AssistanceMode.PRODUCTS)
            command.contains("passadeira", true) || command.contains("atravessar", true) || command.contains("semáforo", true) -> selectMode(AssistanceMode.CROSSWALK)
            command.contains("autocarro", true) || command.contains("ônibus", true) || command.contains("transporte", true) -> selectMode(AssistanceMode.TRANSPORT)
            command.contains("câmara", true) || command.contains("camera", true) || command.contains("abrir", true) -> {
                haptics.vibrateAttention()
                speech.speak("A abrir a câmara no modo ${selectedMode.label}.")
                onOpenCamera(selectedMode)
            }
            else -> speech.speak("Não reconheci o comando. Diga segurança, ler texto, procurar, compras, passadeira, transportes ou abrir câmara.")
        }
    }

    val voiceResult = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val phrase = if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        } else null
        if (phrase.isNullOrBlank()) {
            showVoiceSetup = true
            speech.speakUrgent("O reconhecimento de voz não está disponível. Escolha Configurar reconhecimento de voz.")
        } else handleVoiceCommand(phrase)
    }
    val microphonePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            voiceResult.launch(voiceIntent())
        } else {
            speech.speak("O microfone é necessário para comandos por voz.")
        }
    }

    DisposableEffect(Unit) {
        speech.initialize { speechReady = it }
        onDispose { speech.shutdown() }
    }
    LaunchedEffect(showInitialGuide, speechReady) {
        if (showInitialGuide || !speechReady) return@LaunchedEffect
        speech.speak("Bem-vindo ao WatchOut. Toque numa opção para ouvir o que ela faz. Com leitor de ecrã, deslize até ao botão e toque duas vezes para ativar. A câmara só abre no botão Abrir câmara.")
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("WatchOut", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 34.sp)
        Text(
            "Escolha uma função. Toque para ouvir a explicação; use Abrir câmara quando estiver pronto.",
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 16.sp
        )
        MenuOption(AssistanceMode.SAFETY, selectedMode, ::selectMode)
        MenuOption(AssistanceMode.READ_TEXT, selectedMode, ::selectMode)
        MenuOption(AssistanceMode.FIND, selectedMode, ::selectMode)
        MenuOption(AssistanceMode.DESCRIBE, selectedMode, ::selectMode)
        MenuOption(AssistanceMode.PRODUCTS, selectedMode, ::selectMode)
        MenuOption(AssistanceMode.CROSSWALK, selectedMode, ::selectMode)
        MenuOption(AssistanceMode.TRANSPORT, selectedMode, ::selectMode)

        Spacer(Modifier.height(6.dp))
        Button(
            onClick = {
                haptics.vibrateAttention()
                speech.speak("A abrir a câmara no modo ${selectedMode.label}.")
                onOpenCamera(selectedMode)
            },
            modifier = Modifier.fillMaxWidth().height(68.dp).semantics {
                contentDescription = "Abrir câmara. Modo selecionado: ${selectedMode.label}. Toque duas vezes para ativar."
            },
            shape = RoundedCornerShape(20.dp)
        ) {
            androidx.compose.material3.Icon(Icons.Rounded.CameraAlt, null)
            Spacer(Modifier.padding(horizontal = 6.dp))
            Text("Abrir câmara", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Button(
            onClick = {
                haptics.vibrateInfo()
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    voiceResult.launch(voiceIntent())
                } else microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
            },
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Comando por voz. Diga segurança, ler texto, procurar ou abrir câmara." },
            colors = ButtonDefaults.buttonColors(containerColor = CardSurface)
        ) {
            androidx.compose.material3.Icon(Icons.Rounded.Mic, null)
            Text("  Comando por voz")
        }
        Button(
            onClick = {
                haptics.vibrateInfo()
                speech.speak("A abrir as definições de reconhecimento de voz do Android.")
                runCatching { context.startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)) }
                    .onFailure { speech.speakUrgent("Não foi possível abrir as definições de voz neste telemóvel.") }
            },
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Configurar reconhecimento de voz no Android." },
            colors = ButtonDefaults.buttonColors(containerColor = CardSurface)
        ) { Text("Configurar reconhecimento de voz") }
        Button(onClick = { haptics.vibrateInfo(); speech.speak("A abrir a escolha de modelo."); onChangeModel() }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = CardSurface)) { Text("Mudar modelo") }
        Button(onClick = { haptics.vibrateInfo(); speech.speak("A sair da aplicação."); onExit() }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = CardSurface)) { Text("Sair") }
    }

    if (showInitialGuide) {
        if (speechReady) InitialGuide(speech = speech, haptics = haptics, onComplete = onGuideComplete)
        else GuideLoadingScreen()
    }
    if (showVoiceSetup) {
        AlertDialog(
            onDismissRequest = { showVoiceSetup = false },
            title = { Text("Reconhecimento de voz indisponível") },
            text = { Text("Ative ou instale um serviço de reconhecimento de voz e o idioma Português nas definições do Android. Depois volte ao WatchOut.") },
            confirmButton = {
                Button(onClick = {
                    showVoiceSetup = false
                    runCatching { context.startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)) }
                }) { Text("Abrir definições") }
            },
            dismissButton = { Button(onClick = { showVoiceSetup = false }) { Text("Voltar") } }
        )
    }
    }
}

@Composable
private fun InitialGuide(speech: SpeechManager, haptics: HapticManager, onComplete: () -> Unit) {
    var step by remember { mutableStateOf(0) }
    val steps = listOf(
        "Bem-vindo ao WatchOut. Este guia é falado lentamente e aparece apenas uma vez. A câmara não abre sozinha. Você decide quando abrir.",
        "Na lista de funções: Segurança procura obstáculos no caminho. Ler texto lê placas e etiquetas. Procurar ajuda a encontrar objetos. Descrever explica o ambiente à frente.",
        "Há também Compras, para supermercado, produtos e preços. Passadeira, para semáforos e trânsito. E Transportes, para autocarros, paragens e linhas.",
        "Depois de escolher uma função, encontre o botão grande Abrir câmara. Ele fica depois da lista de funções. Com TalkBack, deslize até ele e toque duas vezes para ativar.",
        "O botão Microfone aceita comandos como segurança, compras, passadeira, transportes e abrir câmara. Se o reconhecimento não estiver disponível, use Configurar reconhecimento de voz para abrir as definições do Android.",
        "Dentro da câmara, a seta no canto superior esquerdo volta ao menu. A engrenagem no canto superior direito abre as definições. Em Definições existe Limpar sessão, que apaga respostas anteriores sem apagar o modelo. O guia terminou."
    )
    LaunchedEffect(step) {
        speech.setSpeechRate(0.72f)
        haptics.vibrateInfo()
        speech.speakUrgent(steps[step]) {
            if (step == steps.lastIndex) {
                speech.setSpeechRate(1f)
                onComplete()
            } else {
                step++
            }
        }
    }
    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Guia inicial", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 30.sp)
        Spacer(Modifier.height(24.dp))
        Text("Etapa ${step + 1} de ${steps.size}", color = MaterialTheme.colorScheme.primary, fontSize = 18.sp)
        Spacer(Modifier.height(16.dp))
        Text(steps[step], color = Color.White, fontSize = 22.sp)
        Spacer(Modifier.height(24.dp))
        Text("A ouvir instruções…", color = Color.White.copy(alpha = 0.7f), fontSize = 16.sp)
    }
}

@Composable
private fun GuideLoadingScreen() {
    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) { Text("A preparar o guia falado…", color = Color.White, fontSize = 22.sp) }
}

@Composable
private fun MenuOption(mode: AssistanceMode, selectedMode: AssistanceMode, onSelect: (AssistanceMode) -> Unit) {
    Button(
        onClick = { onSelect(mode) },
        modifier = Modifier.fillMaxWidth().height(62.dp).semantics {
            contentDescription = "${mode.label}. ${modeDescription(mode)}. Toque duas vezes para selecionar."
        },
        colors = ButtonDefaults.buttonColors(containerColor = if (mode == selectedMode) MaterialTheme.colorScheme.primary else CardSurface),
        shape = RoundedCornerShape(18.dp)
    ) {
        Text("${mode.emoji}  ${mode.label}", fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun modeDescription(mode: AssistanceMode): String = when (mode) {
    AssistanceMode.SAFETY -> "Procura obstáculos e riscos no caminho."
    AssistanceMode.READ_TEXT -> "Lê textos, placas e etiquetas visíveis."
    AssistanceMode.FIND -> "Ajuda a encontrar objetos e indica a direção."
    AssistanceMode.DESCRIBE -> "Descreve brevemente o ambiente à frente."
    AssistanceMode.PRODUCTS -> "Ajuda em compras, supermercado, produtos e preços."
    AssistanceMode.CROSSWALK -> "Identifica passadeira, semáforos e trânsito; não confirma que é seguro atravessar."
    AssistanceMode.TRANSPORT -> "Identifica paragens, autocarros e linhas de transporte."
}

private fun voiceIntent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-PT")
    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
    putExtra(RecognizerIntent.EXTRA_PROMPT, "Diga um comando")
}
