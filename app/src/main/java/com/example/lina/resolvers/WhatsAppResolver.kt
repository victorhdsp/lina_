package com.example.lina.resolvers

import android.view.accessibility.AccessibilityNodeInfo
import com.example.lina.parser.AccessibilityTreeParser
import com.example.lina.resolvers.whatsapp.ChatResolver
import com.example.lina.resolvers.whatsapp.ConversationListResolver
import org.json.JSONObject

object WhatsAppResolver {

    private val conversationListValidationConfig = """
    [
        {
            "name": "chat_list",
            "queries": [
                {"keys": ["className"], "action": "equals", "value": "androidx.recyclerview.widget.RecyclerView"},
                {"keys": ["text", "contentDescription"], "action": "contains", "value": "Deslize para baixo"}
            ]
        },
        {"name": "conversas_button", "queries": [{"keys": ["text", "contentDescription"], "action": "contains", "value": "Conversas"}]},
        {"name": "updates_button", "queries": [{"keys": ["text", "contentDescription"], "action": "contains", "value": "Atualizações"}]},
        {"name": "communities_button", "queries": [{"keys": ["text", "contentDescription"], "action": "contains", "value": "Comunidades"}]},
        {"name": "calls_button", "queries": [{"keys": ["text", "contentDescription"], "action": "contains", "value": "Ligações"}]}
    ]
    """

    // Nova configuração de validação para a tela de chat.
    private val chatValidationConfig = """
    [
        {
            "name": "message_list",
            "queries": [
                {"keys": ["className"], "action": "equals", "value": "android.widget.ListView"}
            ]
        }
    ]
    """

    fun resolve(rootNode: AccessibilityNodeInfo): JSONObject? {
        val screen = identifyScreen(rootNode)

        return when (screen) {
            "conversation_list" -> ConversationListResolver.resolve(rootNode, conversationListValidationConfig)
            // Habilita a chamada para o novo ChatResolver.
            "chat" -> ChatResolver.resolve(rootNode, chatValidationConfig)
            else -> null
        }
    }

    private fun identifyScreen(rootNode: AccessibilityNodeInfo?): String {
        if (rootNode == null) return "unknown"

        if (AccessibilityTreeParser.validate(rootNode, conversationListValidationConfig)) {
            return "conversation_list"
        }

        if (AccessibilityTreeParser.validate(rootNode, chatValidationConfig)) {
            return "chat"
        }

        return "unknown"
    }
}
