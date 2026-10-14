package com.example.autofill

import android.app.assist.AssistStructure
import android.view.View
import android.view.autofill.AutofillId

/** A screen as Autofill sees it: who is asking, and which fields are which. */
data class ParsedForm(
    val packageName: String?,
    val form: ClassifiedForm<AutofillId>
) {
    val webDomain: String? get() = form.webDomain

    /** Every field AtomicVault may fill or save, for FillResponse/SaveInfo. */
    val fillableIds: List<AutofillId>
        get() = listOfNotNull(form.username) + form.passwords + listOfNotNull(form.otp)
}

/**
 * Adapts Android's AssistStructure to [FormClassifier], which holds the
 * actual detection rules (pure Kotlin, unit tested in FormClassifierTest).
 */
object AssistStructureParser {

    fun parse(structure: AssistStructure): ParsedForm {
        val roots = (0 until structure.windowNodeCount).map { ViewNodeAdapter(structure.getWindowNodeAt(it).rootViewNode) }
        return ParsedForm(
            packageName = structure.activityComponent?.packageName,
            form = FormClassifier.classify(roots)
        )
    }

    /**
     * For a save after a multi-page login (email on one screen, password on
     * the next, saved with SaveInfo.FLAG_DELAY_SAVE): the password comes from
     * the newest screen, the username from the newest screen that had one.
     */
    fun parseForSave(structures: List<AssistStructure>): ParsedForm? {
        val parsed = structures.map { parse(it) }
        val last = parsed.lastOrNull() ?: return null
        val username = last.form.usernameValue?.takeIf { it.isNotBlank() }
            ?: parsed.asReversed().firstNotNullOfOrNull { it.form.usernameValue?.takeIf { v -> v.isNotBlank() } }
        val webDomain = last.form.webDomain ?: parsed.asReversed().firstNotNullOfOrNull { it.form.webDomain }
        return last.copy(form = last.form.copy(usernameValue = username, webDomain = webDomain))
    }

    private class ViewNodeAdapter(private val node: AssistStructure.ViewNode) : FormNode<AutofillId> {
        override val id: AutofillId? get() = node.autofillId
        override val autofillHints: List<String> get() = node.autofillHints?.toList().orEmpty()
        override val htmlAttributes: Map<String, String>
            get() = node.htmlInfo?.attributes?.associate { (it.first ?: "") to (it.second ?: "") }.orEmpty()
        override val htmlTag: String? get() = node.htmlInfo?.tag
        override val idEntry: String? get() = node.idEntry
        override val hint: String? get() = node.hint
        override val inputType: Int get() = node.inputType
        override val isVisible: Boolean get() = node.visibility == View.VISIBLE
        override val isEnabled: Boolean get() = node.isEnabled
        override val isEditableText: Boolean get() = node.autofillType == View.AUTOFILL_TYPE_TEXT
        override val text: String?
            get() = node.autofillValue?.takeIf { it.isText }?.textValue?.toString() ?: node.text?.toString()
        override val webDomain: String? get() = node.webDomain
        override val webScheme: String? get() = node.webScheme
        override val children: List<FormNode<AutofillId>>
            get() = (0 until node.childCount).map { ViewNodeAdapter(node.getChildAt(it)) }
    }
}
