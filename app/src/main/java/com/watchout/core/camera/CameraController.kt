package com.watchout.core.camera

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.graphics.Bitmap
import android.util.Log
import android.util.Size
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.Executors

/**
 * Configura e gere o CameraX.
 * - Preview: exibe a câmara em tempo real no ecrã
 * - ImageAnalysis: STRATEGY_KEEP_ONLY_LATEST (nunca acumula frames)
 * - FrameProcessor: processa apenas quando solicitado
 */
class CameraController(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val onFrameReady: (Bitmap) -> Unit
) {

    companion object {
        private const val TAG = "CameraController"
    }

    private val analysisExecutor = Executors.newSingleThreadExecutor()
    val frameProcessor = FrameProcessor(onFrameReady)

    private var cameraProvider: ProcessCameraProvider? = null

    /**
     * Inicializa a câmara traseira e liga ao PreviewView fornecido.
     */
    fun startCamera(previewView: PreviewView) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            try {
                val provider = cameraProviderFuture.get()
                cameraProvider = provider

                val preview = Preview.Builder()
                    .build()
                    .also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    // Uma imagem pequena e recente é mais útil para alertas
                    // contínuos do que um frame 1080p que demora a comprimir e
                    // chega atrasado ao modelo.
                    .setTargetResolution(Size(640, 480))
                    .build()
                    .also {
                        it.setAnalyzer(analysisExecutor, frameProcessor)
                    }

                val cameraSelector = widestRearCameraSelector()

                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )

                Log.i(TAG, "Câmara iniciada com sucesso.")
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao iniciar a câmara: ${e.message}")
            }
        }, ContextCompat.getMainExecutor(context))
    }

    /**
     * Escolhe a câmara traseira mais ampla (menor distância focal). Em
     * aparelhos com várias câmaras, a grande angular enquadra melhor o chão e
     * reduz a probabilidade de o Gemma receber apenas uma parte do obstáculo.
     */
    @OptIn(ExperimentalCamera2Interop::class)
    private fun widestRearCameraSelector(): CameraSelector {
        return try {
            CameraSelector.Builder()
                .addCameraFilter { cameras ->
                    val rearCameras = cameras
                        .filter { it.lensFacing == CameraSelector.LENS_FACING_BACK }
                        .ifEmpty { cameras }
                    val widest = rearCameras.minByOrNull { cameraInfo ->
                        try {
                            Camera2CameraInfo.from(cameraInfo)
                                .getCameraCharacteristic(
                                    CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS
                                )
                                ?.minOrNull() ?: Float.MAX_VALUE
                        } catch (_: Exception) {
                            Float.MAX_VALUE
                        }
                    }
                    widest?.let(::listOf) ?: rearCameras
                }
                .build()
        } catch (e: Exception) {
            Log.w(TAG, "Não foi possível selecionar a grande angular: ${e.message}")
            CameraSelector.DEFAULT_BACK_CAMERA
        }
    }

    /**
     * Solicita a captura do próximo frame para análise pela IA.
     * Descarta o pedido se uma inferência já estiver a decorrer.
     */
    fun requestAnalysis() {
        frameProcessor.requestCapture()
    }

    /**
     * Informa o processador que a inferência terminou e pode aceitar novos frames.
     */
    fun onInferenceComplete() {
        frameProcessor.setInferenceRunning(false)
    }

    /**
     * Informa o processador que a inferência começou.
     */
    fun onInferenceStarted() {
        frameProcessor.setInferenceRunning(true)
    }

    /**
     * Para a câmara e libera recursos.
     */
    fun shutdown() {
        cameraProvider?.unbindAll()
        analysisExecutor.shutdown()
    }
}
