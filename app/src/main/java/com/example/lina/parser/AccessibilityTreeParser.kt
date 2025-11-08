package com.example.lina.parser

import android.view.accessibility.AccessibilityNodeInfo
import org.json.JSONArray
import org.json.JSONObject

object AccessibilityTreeParser {

    fun findNodes(rootNode: AccessibilityNodeInfo?, configJson: String): Map<String, List<AccessibilityNodeInfo>> {
        if (rootNode == null) return emptyMap()
        val results = mutableMapOf<String, MutableList<AccessibilityNodeInfo>>()
        val config = JSONArray(configJson)
        val queryGroups = List(config.length()) { i -> config.getJSONObject(i) }
        traverse(rootNode) { node ->
            for (group in queryGroups) {
                val groupName = group.getString("name")
                val queries = group.getJSONArray("queries")
                if (nodeMatchesAllRules(node, queries)) {
                    results.getOrPut(groupName) { mutableListOf() }.add(node)
                }
            }
        }
        return results
    }

    fun validate(rootNode: AccessibilityNodeInfo?, validationConfigJson: String): Boolean {
        if (rootNode == null) return false
        val validationGroups = JSONArray(validationConfigJson)
        val namesToFind = (0 until validationGroups.length()).map { validationGroups.getJSONObject(it).getString("name") }.toSet()
        val foundNodesMap = findNodes(rootNode, validationConfigJson)
        return foundNodesMap.keys.containsAll(namesToFind)
    }

    fun parseNodeTree(node: AccessibilityNodeInfo?): JSONObject? {
        if (node == null) return null
        val jsonNode = JSONObject()
        val className = node.className?.toString()
        val content = (node.text ?: node.contentDescription)?.toString()?.trim()
        if (!className.isNullOrEmpty()) {
            jsonNode.put("className", className)
        }
        if (!content.isNullOrEmpty()) {
            jsonNode.put("content", content)
        }
        if (node.childCount > 0) {
            val children = JSONArray()
            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                if (child != null) {
                    parseNodeTree(child)?.let { children.put(it) }
                }
            }
            if (children.length() > 0) {
                jsonNode.put("children", children)
            }
        }
        return if (jsonNode.length() > 0) jsonNode else null
    }

    fun extractDataFromNode(nodeJson: JSONObject, configJson: String): JSONObject {
        val result = JSONObject()
        val config = JSONArray(configJson).getJSONObject(0)
        val groupedKeys = mutableMapOf<String, MutableList<String>>()
        config.keys().forEach {
            val rootKey = it.split('.').first()
            groupedKeys.getOrPut(rootKey) { mutableListOf() }.add(it)
        }
        groupedKeys.forEach { (rootKey, keys) ->
            if (keys.size == 1 && rootKey == keys.first()) {
                val resolvedValue = resolveValueForKey(nodeJson, config.getJSONArray(rootKey))
                result.put(rootKey, resolvedValue ?: JSONObject.NULL)
            } else {
                val nestedObject = JSONObject()
                var hasNonNullValue = false
                for (fullKey in keys) {
                    val childKey = fullKey.substringAfter('.')
                    val resolvedValue = resolveValueForKey(nodeJson, config.getJSONArray(fullKey))
                    if (resolvedValue != null && resolvedValue.toString() != "null") {
                        nestedObject.put(childKey, resolvedValue)
                        hasNonNullValue = true
                    }
                }
                result.put(rootKey, if (hasNonNullValue) nestedObject else JSONObject.NULL)
            }
        }
        return result
    }

    private fun resolveValueForKey(nodeJson: JSONObject, paths: JSONArray): Any? {
        for (i in 0 until paths.length()) {
            val resolvedValue = resolvePath(nodeJson, paths.getJSONArray(i))
            if (resolvedValue != null && resolvedValue != JSONObject.NULL) {
                return resolvedValue
            }
        }
        return null
    }

    private fun resolvePath(startNode: JSONObject, path: JSONArray): Any? {
        var currentNode: Any = startNode
        for (i in 0 until path.length()) {
            val step = path.getJSONObject(i)
            val key = step.getString("key")
            val action = step.getString("action")
            val nextNode: Any? = when (val prop = (currentNode as? JSONObject)?.opt(key)) {
                is JSONArray -> {
                    when {
                        action.startsWith("item_") -> {
                            val index = action.substringAfter("item_").toIntOrNull()
                            if (index != null && prop.length() > index) prop.get(index) else null
                        }
                        action == "first" -> findItemInArray(prop, step) { j -> j }
                        action == "last" -> findItemInArray(prop, step) { j -> prop.length() - 1 - j }
                        else -> null
                    }
                }
                else -> if (action == "get") prop else null
            }
            if (nextNode != null) {
                currentNode = nextNode
            } else {
                return null
            }
        }
        return currentNode
    }

    private fun findItemInArray(array: JSONArray, step: JSONObject, indexer: (Int) -> Int): JSONObject? {
        val whereKey = step.optString("where", null)
        val whereValue = step.optString("value", null)
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(indexer(i))
            if (item != null && (whereKey == null || item.optString(whereKey).equals(whereValue, ignoreCase = true))) {
                return item
            }
        }
        return null
    }

    private fun nodeMatchesAllRules(node: AccessibilityNodeInfo, rules: JSONArray): Boolean {
        for (i in 0 until rules.length()) {
            if (!doesNodeSatisfyRule(node, rules.getJSONObject(i))) {
                return false
            }
        }
        return true
    }

    private fun doesNodeSatisfyRule(node: AccessibilityNodeInfo, rule: JSONObject): Boolean {
        val keys = rule.getJSONArray("keys")
        val action = rule.getString("action")
        val valueToMatch = rule.getString("value")
        for (i in 0 until keys.length()) {
            val key = keys.getString(i)
            val nodeValue = getPropertyValue(node, key)?.toString() ?: ""
            val match = when (action) {
                "equals" -> nodeValue.equals(valueToMatch, ignoreCase = true)
                "contains" -> nodeValue.contains(valueToMatch, ignoreCase = true)
                else -> false
            }
            if (match) return true
        }
        return false
    }

    fun traverse(node: AccessibilityNodeInfo?, action: (AccessibilityNodeInfo) -> Unit) {
        if (node == null) return
        action(node)
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                traverse(child, action)
            }
        }
    }

    fun getPropertyValue(node: AccessibilityNodeInfo, propertyName: String): Any? {
        if (propertyName.contains(".")) {
            val parts = propertyName.split(".", limit = 2)
            if (parts.first() == "extras") {
                return node.extras?.getCharSequence(parts.last())
            }
        }
        return when (propertyName) {
            "text" -> node.text
            "contentDescription" -> node.contentDescription
            "className" -> node.className
            else -> null
        }
    }
}
