package com.watchout.core.camera

import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.ImageProxy
import androidx.camera.core.ImageAnalysis
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Processa frames da câmara vindos do CameraX.
 * Estratégia: STRATEGY_KEEP_ONLY_LATEST — descarta frames antigos durante inferência.
 * Nunca envia cada frame para o modelo — apenas o mais recente quando solicitado.
 */
class FrameProcessor(
    private val onFrameReady: (Bitmap) -> Unit
) : ImageAnalysis.Analyzer {

    companion object {
        private const val TAG = "FrameProcessor"
    }

    // Flag que indica se uma inferência está ativa — descarta frames se true
    private val isInferenceRunning = AtomicBoolean(false)
    private var captureOnNextFrame = AtomicBoolean(false)

    /**
     * Chamado a cada frame do CameraX.
     * Só processa se houver um pedido de captura pendente.
     */
    override fun analyze(image: ImageProxy) {
        if (!captureOnNextFrame.getAndSet(false)) {
            image.close()
            return
        }

        if (isInferenceRunning.get()) {
            // Inferência ativa — descarta frame antigo
            image.close()
            return
        }

        try {
            val rawBitmap = image.toBitmap()
            val rotation = image.imageInfo.rotationDegrees
            val bitmap = if (rotation != 0) {
                val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
                Bitmap.createBitmap(
                    rawBitmap,
                    0,
                    0,
                    rawBitmap.width,
                    rawBitmap.height,
                    matrix,
                    true
                ).also {
                    if (it !== rawBitmap && !rawBitmap.isRecycled) rawBitmap.recycle()
                }
            } else {
                rawBitmap
            }
            onFrameReady(bitmap)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao processar frame: ${e.message}")
        } finally {
            image.close()
        }
    }

    /** Agenda a captura do próximo frame disponível. */
    fun requestCapture() {
        captureOnNextFrame.set(true)
    }

    /** Marca que uma inferência está a decorrer. */
    fun setInferenceRunning(running: Boolean) {
        isInferenceRunning.set(running)
    }

}
