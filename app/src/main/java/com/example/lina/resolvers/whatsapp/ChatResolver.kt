package com.example.lina.resolvers.whatsapp

import android.view.accessibility.AccessibilityNodeInfo
import com.example.lina.parser.AccessibilityTreeParser
import org.json.JSONArray
import org.json.JSONObject

object ChatResolver {

    private val messageFinderConfig = """
    [
        {"name": "message_list", "queries": [{"keys": ["className"], "action": "equals", "value": "android.widget.ListView"}]}
    ]
    """

    private val messageExtractionConfig = """
    [
      {
          "content": [[{"key": "children", "action": "first", "where": "className", "value": "android.widget.LinearLayout"}, {"key": "children", "action": "last", "where": "className", "value": "android.widget.FrameLayout"}, {"key": "children", "action": "item_0"}, {"key": "content", "action": "get"}]],
          "hour": [[{"key": "children", "action": "first", "where": "className", "value": "android.widget.LinearLayout"}, {"key": "children", "action": "last", "where": "className", "value": "android.widget.FrameLayout"}, {"key": "children", "action": "item_1"}, {"key": "children", "action": "item_0"}, {"key": "content", "action": "get"}]],
          "received": [[{"key": "children", "action": "first", "where": "className", "value": "android.widget.LinearLayout"}, {"key": "children", "action": "last", "where": "className", "value": "android.widget.FrameLayout"}, {"key": "children", "action": "item_1"}, {"key": "children", "action": "item_1"}, {"key": "content", "action": "get"}]],
          "response_to.content": [[{"key": "children", "action": "first", "where": "className", "value": "android.widget.LinearLayout"}, {"key": "children", "action": "item_0"}, {"key": "children", "action": "item_0"}, {"key": "children", "action": "item_0"}, {"key": "children", "action": "item_1"}, {"key": "children", "action": "item_1"}, {"key": "children", "action": "item_0"}, {"key": "content", "action": "get"}]],
          "response_to.received": [[{"key": "children", "action": "first", "where": "className", "value": "android.widget.LinearLayout"}, {"key": "children", "action": "item_0"}, {"key": "children", "action": "item_0"}, {"key": "children", "action": "item_0"}, {"key": "children", "action": "item_1"}, {"key": "children", "action": "item_0"}, {"key": "children", "action": "item_0"}, {"key": "content", "action": "get"}]]
      }
    ]
    """

    fun resolve(rootNode: AccessibilityNodeInfo, validationConfig: String): JSONObject {
        val result = JSONObject()
        result.put("screen", "chat")

        val chatRoot = navigateToNode(rootNode, listOf(0, 0, 0, 0, 0, 0))

        if (chatRoot != null) {
            val usernameNode = navigateToNode(chatRoot, listOf(0, 0, 0, 1, 0, 0))
            result.put("username", usernameNode?.let { it.text ?: it.contentDescription } ?: JSONObject.NULL)
            usernameNode?.recycle()

            val foundNodes = AccessibilityTreeParser.findNodes(chatRoot, messageFinderConfig)
            val messages = JSONArray()

            foundNodes["message_list"]?.firstOrNull()?.let { listNode ->
                for (i in 0 until listNode.childCount) {
                    val messageOrDateNode = listNode.getChild(i)
                    if (messageOrDateNode != null) {
                        val rawNodeTree = AccessibilityTreeParser.parseNodeTree(messageOrDateNode)
                        if (rawNodeTree != null) {
                            // val extractedData = AccessibilityTreeParser.extractDataFromNode(rawNodeTree, messageExtractionConfig)
                            // val processedData = postProcessMessage(extractedData)
                            // processedData.put("type", "message")
                            messages.put(rawNodeTree)
                        }
                        messageOrDateNode.recycle()
                    }
                }
            }
            result.put("messages", messages)
            chatRoot.recycle()
        }

        return result
    }
    
    private fun postProcessMessage(messageJson: JSONObject): JSONObject {
        val receivedStatus = messageJson.optString("received", null)
        messageJson.put("received", "Entregue".equals(receivedStatus, ignoreCase = true))

        val responseObj = messageJson.optJSONObject("response_to")
        if (responseObj != null && responseObj != JSONObject.NULL) {
            val responder = responseObj.optString("received", null)
            responseObj.put("received", "Você".equals(responder, ignoreCase = true))
        }
        
        return messageJson
    }

    private fun navigateToNode(startNode: AccessibilityNodeInfo?, path: List<Int>): AccessibilityNodeInfo? {
        var currentNode = startNode
        val intermediateNodes = mutableListOf<AccessibilityNodeInfo>()

        for (index in path) {
            if (currentNode == null || currentNode.childCount <= index) {
                intermediateNodes.forEach { it.recycle() }
                return null
            }
            val nextNode = currentNode.getChild(index)
            if (currentNode != startNode) {
                intermediateNodes.add(currentNode)
            }
            currentNode = nextNode
        }

        intermediateNodes.forEach { it.recycle() }
        return currentNode
    }
}
