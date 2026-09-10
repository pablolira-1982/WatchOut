package com.watchout.core.storage

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Importa o ficheiro .litertlm de um URI do Storage Access Framework
 * para o armazenamento controlado pela app, sem carregar o ficheiro em RAM.
 */
class ModelImporter(private val context: Context) {

    sealed interface ImportResult {
        data class Success(val file: File) : ImportResult
        data class Error(val message: String) : ImportResult
        data object Cancelled : ImportResult
    }

    /**
     * Copia o modelo de um content:// URI para o destino especificado.
     * Usa .partial durante a cópia e renomeia no final para evitar ficheiros corrompidos.
     *
     * @param uri URI do ficheiro selecionado via SAF
     * @param destFile Destino final do modelo
     * @param onProgress Callback com progresso 0.0..1.0 e bytes copiados
     */
    suspend fun importModel(
        uri: Uri,
        destFile: File,
        expectedBytes: Long = -1L,
        onProgress: (progress: Float, copiedBytes: Long, totalBytes: Long) -> Unit
    ): ImportResult = withContext(Dispatchers.IO) {
        val partialFile = File(destFile.parent, destFile.name + ModelStorageManager.PARTIAL_SUFFIX)

        try {
            val contentResolver = context.contentResolver
            val inputStream = contentResolver.openInputStream(uri)
                ?: return@withContext ImportResult.Error("Não foi possível abrir o ficheiro.")

            val totalBytes = contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst() && sizeIndex >= 0) cursor.getLong(sizeIndex) else -1L
            } ?: -1L

            val expectedSize = when {
                expectedBytes > 0 -> expectedBytes
                totalBytes > 0 -> totalBytes
                else -> -1L
            }

            if (expectedBytes > 0 && totalBytes > 0 && totalBytes != expectedBytes) {
                return@withContext ImportResult.Error(
                    "Tamanho inválido: ${formatBytes(totalBytes)}. " +
                        "O ficheiro de origem indicou ${formatBytes(expectedBytes)}."
                )
            }

            if (expectedSize > 0) {
                val freeSpace = destFile.parentFile?.usableSpace ?: 0L
                if (freeSpace < expectedSize) {
                    return@withContext ImportResult.Error("Espaço insuficiente para copiar o modelo.")
                }
            }

            partialFile.parentFile?.mkdirs()

            inputStream.use { input ->
                partialFile.outputStream().use { output ->
                    val buffer = ByteArray(8 * 1024 * 1024) // 8MB buffer para ficheiro grande
                    var copiedBytes = 0L
                    var read: Int

                    while (input.read(buffer).also { read = it } != -1) {
                        if (!isActive) {
                            partialFile.delete()
                            return@withContext ImportResult.Cancelled
                        }
                        output.write(buffer, 0, read)
                        copiedBytes += read
                        val progress = if (totalBytes > 0) copiedBytes.toFloat() / totalBytes else 0f
                        onProgress(progress, copiedBytes, totalBytes)
                    }

                    if (expectedBytes > 0 && copiedBytes != expectedBytes) {
                        partialFile.delete()
                        return@withContext ImportResult.Error(
                            "Cópia incompleta: ${formatBytes(copiedBytes)} de ${formatBytes(expectedBytes)}. " +
                                "Verifique o ficheiro de origem no cartão SD."
                        )
                    }
                }
            }

            // Renomeia apenas após cópia completa
            if (destFile.exists()) destFile.delete()
            if (partialFile.renameTo(destFile)) {
                ImportResult.Success(destFile)
            } else {
                ImportResult.Error("Não foi possível finalizar a cópia do modelo.")
            }

        } catch (e: IOException) {
            partialFile.delete()
            ImportResult.Error("Erro na cópia: ${e.message}")
        } catch (e: Exception) {
            partialFile.delete()
            ImportResult.Error("Erro inesperado: ${e.message}")
        }
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
}
