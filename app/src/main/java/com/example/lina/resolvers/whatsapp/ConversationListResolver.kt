package com.example.lina.resolvers.whatsapp

import android.view.accessibility.AccessibilityNodeInfo
import com.example.lina.parser.AccessibilityTreeParser
import org.json.JSONArray
import org.json.JSONObject

object ConversationListResolver {

    fun resolve(rootNode: AccessibilityNodeInfo, validationConfig: String): JSONObject {
        // 1. Encontra todos os nós nomeados na configuração de uma vez.
        val foundNodes = AccessibilityTreeParser.findNodes(rootNode, validationConfig)

        val contacts = JSONArray()

        // 2. Procura pelo nó da lista de chats (RecyclerView).
        foundNodes["chat_list"]?.firstOrNull()?.let { chatListNode ->
            // 3. Itera apenas sobre os filhos diretos do RecyclerView.
            for (i in 0 until chatListNode.childCount) {
                val chatItemNode = chatListNode.getChild(i)
                if (chatItemNode != null) {
                    // 4. Para cada filho (item da lista), parseia sua sub-árvore completa.
                    AccessibilityTreeParser.parseNodeTree(chatItemNode)?.let {
                        contacts.put(it)
                    }
                    chatItemNode.recycle()
                }
            }
        }

        // 5. Monta o JSON de saída final com a lista de contatos hierárquica.
        return JSONObject().apply {
            put("app", "whatsapp")
            put("screen", "conversation_list")
            put("contacts", contacts)
            // A extração dos botões de navegação pode ser adicionada aqui se necessário
        }
    }
}
