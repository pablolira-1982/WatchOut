package com.watchout.feature.onboarding

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.watchout.core.haptics.HapticManager
import com.watchout.core.speech.SpeechManager
import com.watchout.core.storage.ModelImporter
import com.watchout.core.storage.ModelStorageManager
import com.watchout.ui.theme.CardSurface
import com.watchout.ui.theme.SuccessGreen
import com.watchout.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun OnboardingScreen(
    initialModelPath: String? = null,
    onContinue: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val storageManager = remember { ModelStorageManager(context) }
    val importer = remember { ModelImporter(context) }
    val speech = remember { SpeechManager(context) }
    val haptics = remember { HapticManager(context) }

    var cameraGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFileName by remember { mutableStateOf<String?>(null) }
    var selectedVolumeIndex by remember { mutableIntStateOf(0) }
    var volumes by remember { mutableStateOf(storageManager.getAvailableVolumes()) }
    var isImporting by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var copiedBytes by remember { mutableLongStateOf(0L) }
    var totalBytes by remember { mutableLongStateOf(-1L) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        cameraGranted = granted
        haptics.vibrateInfo()
        speech.speak(if (granted) "Câmara autorizada." else "A permissão da câmara foi recusada.")
    }

    val fileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val fileName = displayName(context, uri)
        if (!fileName.endsWith(".litertlm", ignoreCase = true)) {
            errorMessage = "Selecione um ficheiro com extensão .litertlm."
            haptics.vibrateError()
            speech.speakUrgent("O ficheiro selecionado não é um modelo LiteRT válido.")
            return@rememberLauncherForActivityResult
        }
        selectedUri = uri
        selectedFileName = fileName
        errorMessage = null
        haptics.vibrateInfo()
        val size = contentLength(context, uri)
        val sizeText = if (size > 0L) " com ${formatBytes(size)}" else ""
        speech.speak("Ficheiro $fileName$sizeText selecionado. Escolha agora o destino.")
    }

    DisposableEffect(Unit) {
        speech.initialize { }
        onDispose { speech.shutdown() }
    }

    LaunchedEffect(Unit) {
        speech.speak("Bem-vindo ao WatchOut. Toque numa opção para começar.")
        if (!cameraGranted) {
            speech.speak("Precisamos da permissão da câmara para mostrar a imagem.")
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    fun chooseModel() {
        if (isImporting) return
        haptics.vibrateInfo()
        speech.speak("A abrir os ficheiros. Selecione o modelo LiteRT.")
        fileLauncher.launch(arrayOf("*/*"))
    }

    fun requestCamera() {
        if (cameraGranted) {
            haptics.vibrateInfo()
            speech.speak("A câmara já está autorizada.")
        } else {
            haptics.vibrateInfo()
            speech.speak("Vou pedir autorização para usar a câmara.")
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val configuredModelPath = initialModelPath?.takeIf { File(it).isFile }
    val selectedVolume = volumes.getOrNull(selectedVolumeIndex)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Preparando\no seu assistente",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 32.sp,
                lineHeight = 40.sp,
                color = MaterialTheme.colorScheme.onBackground
            ),
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { contentDescription = "Preparando o seu assistente." }
        )
        Spacer(modifier = Modifier.height(32.dp))

        StatusCard(
            icon = Icons.Rounded.CameraAlt,
            title = "Câmara",
            status = if (cameraGranted) "Ativada" else "Toque para autorizar",
            isReady = cameraGranted,
            onClick = ::requestCamera
        )
        Spacer(modifier = Modifier.height(16.dp))
        StatusCard(
            icon = Icons.Rounded.Psychology,
            title = "Modelo de IA",
            status = when {
                selectedFileName != null -> "Ficheiro selecionado"
                configuredModelPath != null -> "Modelo pronto"
                else -> "Toque para escolher o .litertlm"
            },
            isReady = configuredModelPath != null,
            onClick = ::chooseModel
        )

        Spacer(modifier = Modifier.height(16.dp))
        StatusCard(
            icon = Icons.Rounded.VolumeUp,
            title = "Vibração e alertas",
            status = "Ativados ao abrir a aplicação",
            isReady = true,
            onClick = {
                haptics.vibrateInfo()
                speech.speak("Vibração e alertas ativos. A vibração não precisa de uma janela de permissão no Android.")
            }
        )

        if (selectedUri != null && selectedVolume != null) {
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = "Destino do modelo",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Escolha onde guardar o modelo" }
            )
            Spacer(modifier = Modifier.height(8.dp))
            volumes.forEachIndexed { index, volume ->
                OutlinedButton(
                    onClick = {
                        selectedVolumeIndex = index
                        haptics.vibrateInfo()
                        speech.speak("Destino selecionado: ${volume.label}.")
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        width = if (index == selectedVolumeIndex) 2.dp else 1.dp
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (index == selectedVolumeIndex) MaterialTheme.colorScheme.primary else Color.White
                    )
                ) {
                    Text("${if (index == selectedVolumeIndex) "●" else "○"} ${volume.label} — ${formatBytes(volume.freeBytes)} livres")
                }
                Spacer(modifier = Modifier.height(6.dp))
            }
            Button(
                onClick = {
                    val uri = selectedUri ?: return@Button
                    val volume = selectedVolume ?: return@Button
                    isImporting = true
                    progress = 0f
                    errorMessage = null
                    haptics.vibrateInfo()
                    speech.speak("A copiar o modelo para ${volume.label}. Esta operação pode demorar.")
                    scope.launch {
                        when (val result = importer.importModel(
                            uri = uri,
                            destFile = storageManager.getModelFile(volume),
                            expectedBytes = contentLength(context, uri),
                            onProgress = { currentProgress, copied, total ->
                                progress = currentProgress
                                copiedBytes = copied
                                totalBytes = total
                            }
                        )) {
                            is ModelImporter.ImportResult.Success -> {
                                isImporting = false
                                haptics.vibrateInfo()
                                speech.speak("Modelo importado. A iniciar o assistente.")
                                onContinue(result.file.absolutePath)
                            }
                            is ModelImporter.ImportResult.Cancelled -> {
                                isImporting = false
                                speech.speak("Cópia cancelada.")
                            }
                            is ModelImporter.ImportResult.Error -> {
                                isImporting = false
                                errorMessage = result.message
                                haptics.vibrateError()
                                speech.speakUrgent(result.message)
                            }
                        }
                    }
                },
                enabled = !isImporting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
            ) {
                Text(if (isImporting) "A copiar… ${formatPercent(progress)}" else "Copiar e usar este modelo")
            }
            if (isImporting) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "${formatBytes(copiedBytes)}${if (totalBytes > 0) " / ${formatBytes(totalBytes)}" else " copiados"}",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
        }

        errorMessage?.let { message ->
            Spacer(modifier = Modifier.height(10.dp))
            Text(message, color = Color(0xFFFF6B6B), fontSize = 13.sp)
        }

        Spacer(modifier = Modifier.weight(1f))
        Button(
            onClick = {
                haptics.vibrateInfo()
                when {
                    !cameraGranted -> requestCamera()
                    configuredModelPath != null -> {
                        speech.speak("A abrir o assistente.")
                        onContinue(configuredModelPath)
                    }
                    else -> chooseModel()
                }
            },
            enabled = !isImporting,
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            shape = RoundedCornerShape(30.dp)
        ) {
            Text(
                text = when {
                    !cameraGranted -> "Dar Permissão à Câmara"
                    configuredModelPath != null -> "Começar"
                    else -> "Selecionar Modelo"
                },
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = when {
                !cameraGranted -> "Toque em Câmara para autorizar"
                configuredModelPath != null -> "Tudo pronto para o ajudar!"
                else -> "Escolha o ficheiro .litertlm para continuar"
            },
            style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary, fontSize = 14.sp),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun StatusCard(
    icon: ImageVector,
    title: String,
    status: String,
    isReady: Boolean,
    onClick: (() -> Unit)? = null
) {
    val modifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(20.dp))
        .background(CardSurface)
        .then(
            if (onClick != null) {
                Modifier
                    .clickable(onClick = onClick)
                    .semantics {
                        role = Role.Button
                        contentDescription = "$title. $status. Toque para abrir."
                    }
            } else Modifier
        )
        .padding(20.dp)

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
        }
        Spacer(modifier = Modifier.width(20.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, color = Color.White, fontSize = 18.sp)
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(status, color = if (isReady) SuccessGreen else TextSecondary, fontSize = 14.sp)
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = if (isReady) Icons.Rounded.Check else Icons.Rounded.Close,
                    contentDescription = if (isReady) "Pronto" else "Necessita de ação",
                    tint = if (isReady) SuccessGreen else Color(0xFFFFB74D),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

private fun displayName(context: Context, uri: Uri): String {
    val name = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (cursor.moveToFirst() && index >= 0) cursor.getString(index) else null
    }
    return name ?: uri.lastPathSegment?.substringAfterLast('/') ?: "modelo.litertlm"
}

private fun contentLength(context: Context, uri: Uri): Long {
    return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(OpenableColumns.SIZE)
        if (cursor.moveToFirst() && index >= 0) cursor.getLong(index) else -1L
    } ?: -1L
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("B", "KiB", "MiB", "GiB", "TiB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    return "%.1f %s".format(value, units[unit])
}

private fun formatPercent(progress: Float): String = "%.0f%%".format(progress.coerceIn(0f, 1f) * 100)
