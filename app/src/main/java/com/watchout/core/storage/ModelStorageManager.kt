package com.watchout.core.storage

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import androidx.core.content.ContextCompat
import java.io.File

/**
 * Gere e descobre os volumes de armazenamento disponíveis para o modelo de IA.
 * Suporta memória interna e cartão SD removível.
 */
class ModelStorageManager(private val context: Context) {

    data class StorageVolume(
        val file: File,
        val label: String,
        val totalBytes: Long,
        val freeBytes: Long,
        val isRemovable: Boolean,
        val state: String
    )

    companion object {
        // Destination name is independent of the selected model. The user
        // may provide the official model or a compatible fine-tuned .litertlm
        // exported from the Kaggle training pipeline.
        const val MODEL_FILENAME = "gemma-4-E2B-it.litertlm"
        const val PARTIAL_SUFFIX = ".partial"
        private const val MODEL_DIR = "models"
    }

    /**
     * Devolve todos os volumes disponíveis no dispositivo.
     */
    fun getAvailableVolumes(): List<StorageVolume> {
        val result = mutableListOf<StorageVolume>()
        val internalDir = context.filesDir
        val internalStats = StatFs(internalDir.path)
        result.add(
            StorageVolume(
                file = internalDir,
                label = "Memória interna",
                totalBytes = internalStats.totalBytes,
                freeBytes = internalStats.availableBytes,
                isRemovable = false,
                state = Environment.MEDIA_MOUNTED
            )
        )

        val externalDirs = ContextCompat.getExternalFilesDirs(context, null)

        externalDirs.forEach { dir ->
            if (dir == null) return@forEach
            if (dir.absolutePath == internalDir.absolutePath) return@forEach
            val state = Environment.getExternalStorageState(dir)
            if (state != Environment.MEDIA_MOUNTED && state != Environment.MEDIA_MOUNTED_READ_ONLY) return@forEach

            val isRemovable = try {
                Environment.isExternalStorageRemovable(dir)
            } catch (e: Exception) {
                false
            }

            val stats = StatFs(dir.path)
            result.add(
                StorageVolume(
                    file = dir,
                    label = if (isRemovable) "Cartão SD" else "Memória interna",
                    totalBytes = stats.totalBytes,
                    freeBytes = stats.availableBytes,
                    isRemovable = isRemovable,
                    state = state
                )
            )
        }
        return result
    }

    /**
     * Devolve o diretório do modelo para um volume específico.
     */
    fun getModelDir(volume: StorageVolume): File {
        return File(volume.file, MODEL_DIR).also { it.mkdirs() }
    }

    /**
     * Devolve o caminho completo do modelo num volume.
     */
    fun getModelFile(volume: StorageVolume): File {
        return File(getModelDir(volume), MODEL_FILENAME)
    }

    /**
     * Verifica se o modelo já existe e está completo num volume.
     */
    fun isModelReady(volume: StorageVolume): Boolean {
        val file = getModelFile(volume)
        return file.exists() && file.length() > 0
    }

    /**
     * Verifica se há espaço livre suficiente para o modelo.
     */
    fun hasEnoughSpace(volume: StorageVolume, requiredBytes: Long): Boolean {
        return volume.freeBytes >= requiredBytes
    }
}
