package com.example.autofill

import android.app.assist.AssistStructure
import android.os.Build
import android.view.View
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import androidx.annotation.RequiresApi

data class ParsedAutofillStructure(
    val packageName: String?,
    val webDomain: String?,
    val usernameId: AutofillId?,
    val passwordId: AutofillId?,
    val saveUsernameValue: String? = null,
    val savePasswordValue: String? = null
)

@RequiresApi(Build.VERSION_CODES.O)
object AssistStructureParser {

    fun parse(structure: AssistStructure): ParsedAutofillStructure {
        val packageName = structure.activityComponent?.packageName
        var webDomain: String? = null
        var usernameId: AutofillId? = null
        var passwordId: AutofillId? = null
        var saveUserVal: String? = null
        var savePassVal: String? = null

        val nodeCount = structure.windowNodeCount
        for (i in 0 until nodeCount) {
            val windowNode = structure.getWindowNodeAt(i)
            val rootNode = windowNode.rootViewNode
            val result = traverseNode(rootNode)
            if (webDomain == null && result.webDomain != null) webDomain = result.webDomain
            if (usernameId == null && result.usernameId != null) {
                usernameId = result.usernameId
                saveUserVal = result.saveUsernameValue
            }
            if (passwordId == null && result.passwordId != null) {
                passwordId = result.passwordId
                savePassVal = result.savePasswordValue
            }
        }

        return ParsedAutofillStructure(
            packageName = packageName,
            webDomain = webDomain,
            usernameId = usernameId,
            passwordId = passwordId,
            saveUsernameValue = saveUserVal,
            savePasswordValue = savePassVal
        )
    }

    private fun traverseNode(node: AssistStructure.ViewNode): ParsedAutofillStructure {
        var webDomain: String? = node.webDomain
        var usernameId: AutofillId? = null
        var passwordId: AutofillId? = null
        var saveUserVal: String? = null
        var savePassVal: String? = null

        val hints = node.autofillHints
        val idEntry = node.idEntry?.lowercase() ?: ""
        val hintText = (node.hint ?: "").lowercase()
        val textVal = node.autofillValue?.textValue?.toString() ?: node.text?.toString()

        val isPassword = (hints != null && hints.any { it.contains(View.AUTOFILL_HINT_PASSWORD, ignoreCase = true) }) ||
                idEntry.contains("password") || idEntry.contains("pass") || hintText.contains("password")

        val isUsername = (hints != null && (hints.any { it.contains(View.AUTOFILL_HINT_USERNAME, ignoreCase = true) } ||
                hints.any { it.contains(View.AUTOFILL_HINT_EMAIL_ADDRESS, ignoreCase = true) })) ||
                idEntry.contains("user") || idEntry.contains("email") || idEntry.contains("login") ||
                hintText.contains("email") || hintText.contains("username")

        if (isPassword && node.autofillId != null) {
            passwordId = node.autofillId
            savePassVal = textVal
        } else if (isUsername && node.autofillId != null) {
            usernameId = node.autofillId
            saveUserVal = textVal
        }

        for (i in 0 until node.childCount) {
            val child = node.getChildAt(i)
            val childResult = traverseNode(child)
            if (webDomain == null && childResult.webDomain != null) webDomain = childResult.webDomain
            if (usernameId == null && childResult.usernameId != null) {
                usernameId = childResult.usernameId
                saveUserVal = childResult.saveUsernameValue
            }
            if (passwordId == null && childResult.passwordId != null) {
                passwordId = childResult.passwordId
                savePassVal = childResult.savePasswordValue
            }
        }

        return ParsedAutofillStructure(
            packageName = null,
            webDomain = webDomain,
            usernameId = usernameId,
            passwordId = passwordId,
            saveUsernameValue = saveUserVal,
            savePasswordValue = savePassVal
        )
    }
}
