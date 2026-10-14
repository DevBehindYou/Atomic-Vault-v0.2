package com.example.autofill

import android.text.InputType

/**
 * One node of a screen's view tree as Autofill describes it, reduced to what
 * field detection needs. [AssistStructureParser] adapts Android's
 * AssistStructure.ViewNode to this; tests build fake trees directly.
 *
 * [T] is the platform field id (AutofillId on device, anything in tests).
 */
interface FormNode<T> {
    val id: T?
    val autofillHints: List<String>
    /** HTML attributes for web content: `type`, `autocomplete`, `name`, `id`. */
    val htmlAttributes: Map<String, String>
    val htmlTag: String?
    val idEntry: String?
    val hint: String?
    val inputType: Int
    val isVisible: Boolean
    val isEnabled: Boolean
    /** Accepts typed text (Autofill type TEXT). Labels and buttons are not fields. */
    val isEditableText: Boolean
    val text: String?
    val webDomain: String?
    val webScheme: String?
    val children: List<FormNode<T>>
}

enum class FieldKind { USERNAME, CURRENT_PASSWORD, NEW_PASSWORD, OTP }

data class ClassifiedForm<T>(
    val webDomain: String?,
    val webScheme: String?,
    val username: T?,
    val passwords: List<T>,
    /** True when the password fields are for creating a password (sign-up / change). */
    val isNewPassword: Boolean,
    val otp: T?,
    val usernameValue: String?,
    val passwordValue: String?
) {
    val password: T? get() = passwords.firstOrNull()
    val hasLoginFields: Boolean get() = username != null || passwords.isNotEmpty()
    val isInsecureWeb: Boolean get() = webDomain != null && webScheme.equals("http", ignoreCase = true)
}

/**
 * Decides which fields of a form are the username, password(s) and one-time
 * code. Signals are ranked from strongest to weakest:
 *
 *  1. Android autofill hints set by the app.
 *  2. HTML `autocomplete` / `type` on web content.
 *  3. The input type (password variations, email).
 *  4. Whole words in the view id, hint or HTML name.
 *
 * Substring matching is gone: "pass" used to match "passport", "compass" and
 * "bypass", and "user" matched "user agreement". Invisible and disabled fields
 * are skipped, since a hidden honeypot field must never receive a credential.
 */
object FormClassifier {

    private val PASSWORD_WORDS = setOf("password", "passwd", "pwd", "passcode", "pass", "pin")
    private val NEW_PASSWORD_WORDS = setOf("new", "confirm", "create", "repeat", "retype", "signup", "register")
    private val USERNAME_WORDS = setOf("username", "user", "login", "email", "mail", "userid", "account", "phone", "mobile", "identifier")
    /** Explicit 2FA words only: a bare "code" is also a postal or promo code. */
    private val OTP_WORDS = setOf("otp", "totp", "2fa", "mfa", "onetime", "verification", "authenticator", "verify")

    fun <T> classify(roots: List<FormNode<T>>): ClassifiedForm<T> {
        val fields = mutableListOf<Pair<FormNode<T>, FieldKind>>()
        var webDomain: String? = null
        var webScheme: String? = null

        fun visit(node: FormNode<T>, parentVisible: Boolean) {
            val visible = parentVisible && node.isVisible
            if (webDomain == null && !node.webDomain.isNullOrBlank()) {
                webDomain = node.webDomain
                webScheme = node.webScheme
            }
            if (visible && node.isEnabled && node.isEditableText && node.id != null) {
                kindOf(node)?.let { fields.add(node to it) }
            }
            node.children.forEach { visit(it, visible) }
        }
        roots.forEach { visit(it, true) }

        val passwordNodes = fields.filter { it.second == FieldKind.CURRENT_PASSWORD || it.second == FieldKind.NEW_PASSWORD }
        val isNew = passwordNodes.any { it.second == FieldKind.NEW_PASSWORD } || passwordNodes.size >= 2
        val usernameNode = fields.firstOrNull { it.second == FieldKind.USERNAME }?.first
        val otpNode = fields.firstOrNull { it.second == FieldKind.OTP }?.first

        return ClassifiedForm(
            webDomain = webDomain,
            webScheme = webScheme,
            username = usernameNode?.id,
            passwords = passwordNodes.mapNotNull { it.first.id },
            isNewPassword = isNew,
            otp = otpNode?.id,
            usernameValue = usernameNode?.text,
            // On a form with "new" and "confirm", either holds the new password.
            passwordValue = passwordNodes.firstNotNullOfOrNull { it.first.text?.takeIf { t -> t.isNotEmpty() } }
        )
    }

    internal fun <T> kindOf(node: FormNode<T>): FieldKind? {
        // 1. Explicit Android autofill hints.
        val hints = node.autofillHints.map { it.lowercase() }
        if (hints.isNotEmpty()) {
            when {
                hints.any { it == "newpassword" || it.contains("new-password") } -> return FieldKind.NEW_PASSWORD
                hints.any { it.contains("otp") || it.contains("one-time-code") || it.contains("onetimecode") } -> return FieldKind.OTP
                hints.any { it.contains("password") } -> return FieldKind.CURRENT_PASSWORD
                hints.any { it.contains("username") || it.contains("email") || it.contains("phone") } -> return FieldKind.USERNAME
            }
        }

        // 2. HTML autocomplete / type.
        val html = node.htmlAttributes.mapKeys { it.key.lowercase() }
        val autocomplete = html["autocomplete"]?.lowercase().orEmpty()
        val htmlType = html["type"]?.lowercase().orEmpty()
        when {
            "new-password" in autocomplete -> return FieldKind.NEW_PASSWORD
            "current-password" in autocomplete -> return FieldKind.CURRENT_PASSWORD
            "one-time-code" in autocomplete -> return FieldKind.OTP
            "username" in autocomplete || autocomplete == "email" -> return FieldKind.USERNAME
        }
        if (htmlType in setOf("hidden", "submit", "button", "checkbox", "radio", "search")) return null

        val words = words(node.idEntry) + words(node.hint) + words(html["name"]) + words(html["id"])

        // 3. Input type.
        val variation = node.inputType and InputType.TYPE_MASK_VARIATION
        val clazz = node.inputType and InputType.TYPE_MASK_CLASS
        val isPasswordType = htmlType == "password" ||
            (clazz == InputType.TYPE_CLASS_TEXT && (
                variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                    variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
                    variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                )) ||
            (clazz == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD)
        if (isPasswordType) {
            if (words.any { it in OTP_WORDS } && words.none { it in PASSWORD_WORDS }) return FieldKind.OTP
            return if (words.any { it in NEW_PASSWORD_WORDS }) FieldKind.NEW_PASSWORD else FieldKind.CURRENT_PASSWORD
        }
        val isEmailType = htmlType == "email" ||
            (clazz == InputType.TYPE_CLASS_TEXT && (
                variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
                    variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
                ))

        // 4. Whole words in id / hint / name.
        if (words.any { it in OTP_WORDS } && words.none { it in USERNAME_WORDS }) return FieldKind.OTP
        if (isEmailType) return FieldKind.USERNAME
        if (words.any { it in USERNAME_WORDS }) {
            // Text inputs only: a "username" label or a checkbox is not a field.
            val isTextInput = node.inputType == 0 && node.htmlTag.equals("input", ignoreCase = true) ||
                clazz == InputType.TYPE_CLASS_TEXT || clazz == InputType.TYPE_CLASS_PHONE
            if (isTextInput) return FieldKind.USERNAME
        }
        return null
    }

    /** Lowercase words from an id/hint such as "login_password", "userName", "E-mail address". */
    internal fun words(raw: String?): Set<String> {
        if (raw.isNullOrBlank()) return emptySet()
        val spaced = raw
            .replace(Regex("([a-z0-9])([A-Z])"), "$1 $2")
            .lowercase()
            .replace("e-mail", "email")
            .replace("one-time", "onetime")
            .replace("sign-up", "signup")
            .replace("user-name", "username")
        return spaced.split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }.toSet()
    }
}
