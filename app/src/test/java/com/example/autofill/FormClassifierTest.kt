package com.example.autofill

import android.text.InputType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Field detection over fake view trees. Each test is a real-world form shape
 * the old parser got wrong or never handled.
 */
class FormClassifierTest {

    private data class Node(
        override val id: String?,
        override val autofillHints: List<String> = emptyList(),
        override val htmlAttributes: Map<String, String> = emptyMap(),
        override val htmlTag: String? = null,
        override val idEntry: String? = null,
        override val hint: String? = null,
        override val inputType: Int = InputType.TYPE_CLASS_TEXT,
        override val isVisible: Boolean = true,
        override val isEnabled: Boolean = true,
        override val isEditableText: Boolean = true,
        override val text: String? = null,
        override val webDomain: String? = null,
        override val webScheme: String? = null,
        override val children: List<FormNode<String>> = emptyList()
    ) : FormNode<String>

    private fun root(vararg children: Node, domain: String? = null, scheme: String? = "https") =
        Node(id = null, isEditableText = false, webDomain = domain, webScheme = scheme, children = children.toList())

    private val textPassword = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
    private val textEmail = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS

    private fun web(id: String, type: String, autocomplete: String? = null, name: String? = null, text: String? = null) = Node(
        id = id, inputType = 0, htmlTag = "input", text = text,
        htmlAttributes = buildMap {
            put("type", type)
            autocomplete?.let { put("autocomplete", it) }
            name?.let { put("name", it) }
        }
    )

    @Test
    fun `native login with autofill hints`() {
        val form = FormClassifier.classify(listOf(root(
            Node("u", autofillHints = listOf("username")),
            Node("p", autofillHints = listOf("password"), inputType = textPassword)
        )))
        assertEquals("u", form.username)
        assertEquals("p", form.password)
        assertFalse(form.isNewPassword)
    }

    @Test
    fun `native login by input type and ids, no hints`() {
        val form = FormClassifier.classify(listOf(root(
            Node("u", idEntry = "login_email", inputType = textEmail),
            Node("p", idEntry = "login_password", inputType = textPassword)
        )))
        assertEquals("u", form.username)
        assertEquals("p", form.password)
    }

    @Test
    fun `web login uses html autocomplete and reports the domain`() {
        val form = FormClassifier.classify(listOf(root(
            web("u", "text", autocomplete = "username"),
            web("p", "password", autocomplete = "current-password"),
            domain = "github.com"
        )))
        assertEquals("u", form.username)
        assertEquals("p", form.password)
        assertEquals("github.com", form.webDomain)
        assertFalse(form.isInsecureWeb)
    }

    @Test
    fun `sign-up form is a new-password form with both password fields`() {
        val form = FormClassifier.classify(listOf(root(
            web("e", "email", name = "email"),
            web("p1", "password", autocomplete = "new-password", text = "N3w!pass"),
            web("p2", "password", name = "confirm_password", text = "N3w!pass"),
            domain = "example.com"
        )))
        assertTrue(form.isNewPassword)
        assertEquals(listOf("p1", "p2"), form.passwords)
        assertEquals("N3w!pass", form.passwordValue)
    }

    @Test
    fun `passport, compass and bypass fields are not passwords`() {
        val form = FormClassifier.classify(listOf(root(
            Node("a", idEntry = "passport_number"),
            Node("b", idEntry = "compass_heading"),
            Node("c", idEntry = "bypassCache")
        )))
        assertNull(form.password)
        assertFalse(form.hasLoginFields)
    }

    @Test
    fun `hidden and disabled fields never receive a credential`() {
        val form = FormClassifier.classify(listOf(root(
            Node("trap", idEntry = "password", inputType = textPassword, isVisible = false),
            Node("off", idEntry = "username", isEnabled = false),
            web("h", "hidden", name = "password")
        )))
        assertFalse(form.hasLoginFields)
    }

    @Test
    fun `a label is not a field`() {
        val form = FormClassifier.classify(listOf(root(
            Node("label", idEntry = "username_label", isEditableText = false, inputType = 0)
        )))
        assertNull(form.username)
    }

    @Test
    fun `one-time code field is OTP, not password`() {
        val form = FormClassifier.classify(listOf(root(
            web("c", "text", autocomplete = "one-time-code"),
        )))
        assertEquals("c", form.otp)
        assertNull(form.password)

        val native = FormClassifier.classify(listOf(root(
            Node("v", idEntry = "verification_code", inputType = InputType.TYPE_CLASS_NUMBER)
        )))
        assertEquals("v", native.otp)
    }

    @Test
    fun `postal and promo codes are not OTP`() {
        val form = FormClassifier.classify(listOf(root(
            Node("z", idEntry = "zip_code", inputType = InputType.TYPE_CLASS_NUMBER),
            Node("p", idEntry = "promo_code")
        )))
        assertNull(form.otp)
    }

    @Test
    fun `plain http page is flagged insecure`() {
        val form = FormClassifier.classify(listOf(root(
            web("p", "password"), domain = "example.com", scheme = "http"
        )))
        assertTrue(form.isInsecureWeb)
    }

    @Test
    fun `words split camelCase, snake_case and spaced labels`() {
        assertEquals(setOf("user", "name"), FormClassifier.words("userName"))
        assertEquals(setOf("login", "password"), FormClassifier.words("login_password"))
        assertEquals(setOf("email", "address"), FormClassifier.words("E-mail address"))
    }
}
