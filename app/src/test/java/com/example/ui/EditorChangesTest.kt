package com.example.ui

import com.example.database.CredentialInput
import com.example.ui.editor.editorHasChanges
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorChangesTest {

    private val loaded = CredentialInput(title = "GitHub", username = "ashu", tagIds = listOf("a", "b"))

    @Test
    fun `nothing changed`() = assertFalse(editorHasChanges(loaded, loaded.copy()))

    @Test
    fun `tags picked again in a different order are not a change`() =
        assertFalse(editorHasChanges(loaded, loaded.copy(tagIds = listOf("b", "a"))))

    @Test
    fun `an edited field is a change`() = assertTrue(editorHasChanges(loaded, loaded.copy(username = "ashu2")))

    @Test
    fun `a tag removed is a change`() = assertTrue(editorHasChanges(loaded, loaded.copy(tagIds = listOf("a"))))

    @Test
    fun `still loading is never a change`() = assertFalse(editorHasChanges(null, loaded))
}
