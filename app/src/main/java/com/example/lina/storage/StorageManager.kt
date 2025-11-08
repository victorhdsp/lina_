package com.example.lina.storage

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

object StorageManager {

    private const val TAG = "StorageManager"
    private const val FIREBASE_DB_URL = "https://assistent-wpp-default-rtdb.firebaseio.com/accessibility/.json"
    private const val WEBHOOK_URL = "https://n8n.victorhdsp.cloud/webhook/07882236-d54b-4d04-8d60-65ac443e9321"
    private const val PAYLOADS_DIR_NAME = "payloads"

    private const val CACHE_SIZE = 1000
    private val sentPayloadsCache = object : LinkedHashSet<Int>() {
        override fun add(element: Int): Boolean {
            if (size >= CACHE_SIZE) {
                remove(first())
            }
            return super.add(element)
        }
    }
    // -----------------------------------------

    private var appContext: Context? = null

    @Volatile
    private var isUploaderRunning = false
    private var uploaderThread: Thread? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        startUploader()
    }

    fun shutdown() {
        stopUploader()
    }

    fun savePayload(jsonData: JSONObject) {
        val dataObject = jsonData.optJSONObject("data")
        val payloadHash = dataObject?.toString()?.hashCode() ?: jsonData.toString().hashCode()

        if (sentPayloadsCache.contains(payloadHash)) {
            return // Payload duplicado ignorado
        }

        sentPayloadsCache.add(payloadHash)
        Log.d(TAG, "Novo payload detectado (hash: $payloadHash). Salvando para upload.")

        savePayloadToFile(jsonData)
    }

    private fun savePayloadToFile(jsonData: JSONObject) {
        val context = appContext ?: return
        thread {
            try {
                val payloadsDir = File(context.filesDir, PAYLOADS_DIR_NAME)
                if (!payloadsDir.exists()) {
                    payloadsDir.mkdirs()
                }
                val file = File(payloadsDir, "${System.currentTimeMillis()}.json")
                file.writeText(jsonData.toString())
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao salvar payload localmente", e)
            }
        }
    }

    private fun startUploader() {
        if (uploaderThread?.isAlive == true) return
        val context = appContext ?: return

        isUploaderRunning = true
        uploaderThread = thread {
            Log.d(TAG, "Thread de upload iniciada.")
            while (isUploaderRunning) {
                try {
                    val payloadsDir = File(context.filesDir, PAYLOADS_DIR_NAME)
                    val fileToUpload = payloadsDir.listFiles { _, name -> name.endsWith(".json") }?.minByOrNull { it.lastModified() }

                    if (fileToUpload != null) {
                        val fileContent = fileToUpload.readText()
                        val eventTypeForLog = try { JSONObject(fileContent).getString("eventType") } catch (e: Exception) { "unknown" }

                        when (val responseCode = sendToFirebase(fileContent)) {
                            HttpURLConnection.HTTP_OK -> {
                                Log.d(TAG, "Sucesso (Firebase): Arquivo ${fileToUpload.name} enviado.")
                                sendToWebhook(fileContent)
                                fileToUpload.delete()
                            }
                            HttpURLConnection.HTTP_BAD_REQUEST -> {
                                Log.e(TAG, "Erro 400 (Firebase): Arquivo ${fileToUpload.name}. Renomeando para .failed.")
                                val failedFile = File(fileToUpload.parent, fileToUpload.name + ".failed")
                                fileToUpload.renameTo(failedFile)
                            }
                            else -> {
                                Log.w(TAG, "Falha (Firebase): Código $responseCode para ${fileToUpload.name}. Tentando novamente em 30s.")
                                Thread.sleep(30_000)
                            }
                        }
                    } else {
                        Thread.sleep(10_000)
                    }
                } catch (e: InterruptedException) {
                    isUploaderRunning = false
                } catch (e: Exception) {
                    Log.e(TAG, "Erro no loop da thread de upload", e)
                    Thread.sleep(60_000)
                }
            }
            Log.d(TAG, "Thread de upload terminada.")
        }
    }

    private fun stopUploader() {
        isUploaderRunning = false
        uploaderThread?.interrupt()
        uploaderThread = null
    }

    private fun sendToFirebase(jsonString: String): Int {
        var connection: HttpURLConnection? = null
        try {
            val url = URL(FIREBASE_DB_URL)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json; utf-8")
            connection.doOutput = true

            OutputStreamWriter(connection.outputStream).use { it.write(jsonString) }

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                val errorResponse = connection.errorStream?.bufferedReader()?.use { it.readText() }
                Log.e(TAG, "Erro do Firebase. Código: $responseCode. Resposta: $errorResponse")
            }
            return responseCode
        } catch (e: Exception) {
            Log.e(TAG, "Exceção ao enviar dados para o Firebase", e)
            return -1
        } finally {
            connection?.disconnect()
        }
    }
    
    private fun sendToWebhook(jsonString: String) {
        thread {
            var connection: HttpURLConnection? = null
            try {
                val url = URL(WEBHOOK_URL)
                connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json; utf-8")
                connection.doOutput = true

                OutputStreamWriter(connection.outputStream).use { it.write(jsonString) }

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    Log.d(TAG, "Sucesso (Webhook): Payload enviado.")
                } else {
                    val errorResponse = connection.errorStream?.bufferedReader()?.use { it.readText() }
                    Log.w(TAG, "Falha (Webhook): Código $responseCode - $errorResponse")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exceção ao enviar para o Webhook", e)
            } finally {
                connection?.disconnect()
            }
        }
    }
}
