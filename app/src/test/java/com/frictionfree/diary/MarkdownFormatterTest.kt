package com.frictionfree.diary

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.frictionfree.diary.ui.components.MarkdownAction
import com.frictionfree.diary.ui.components.MarkdownFormatter
import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownFormatterTest {

    @Test
    fun testBoldWithSelection() {
        val initial = TextFieldValue(
            text = "Hello world",
            selection = TextRange(0, 5) // "Hello" selected
        )
        val result = MarkdownFormatter.applyAction(initial, MarkdownAction.BOLD)
        assertEquals("**Hello** world", result.text)
        assertEquals(TextRange(2, 7), result.selection)
    }

    @Test
    fun testBoldToggleOff() {
        val initial = TextFieldValue(
            text = "**Hello** world",
            selection = TextRange(0, 9) // "**Hello**" selected
        )
        val result = MarkdownFormatter.applyAction(initial, MarkdownAction.BOLD)
        assertEquals("Hello world", result.text)
        assertEquals(TextRange(0, 5), result.selection)
    }

    @Test
    fun testBoldWithoutSelection() {
        val initial = TextFieldValue(
            text = "Hello ",
            selection = TextRange(6, 6)
        )
        val result = MarkdownFormatter.applyAction(initial, MarkdownAction.BOLD)
        assertEquals("Hello ****", result.text)
        assertEquals(TextRange(8, 8), result.selection)
    }

    @Test
    fun testItalicWithSelection() {
        val initial = TextFieldValue(
            text = "Hello world",
            selection = TextRange(6, 11) // "world" selected
        )
        val result = MarkdownFormatter.applyAction(initial, MarkdownAction.ITALIC)
        assertEquals("Hello *world*", result.text)
        assertEquals(TextRange(7, 12), result.selection)
    }

    @Test
    fun testItalicToggleOff() {
        val initial = TextFieldValue(
            text = "Hello *world*",
            selection = TextRange(6, 13) // "*world*" selected
        )
        val result = MarkdownFormatter.applyAction(initial, MarkdownAction.ITALIC)
        assertEquals("Hello world", result.text)
        assertEquals(TextRange(6, 11), result.selection)
    }

    @Test
    fun testQuoteWithMultiLineSelection() {
        val multiline = "Line one\nLine two"
        val initial = TextFieldValue(
            text = multiline,
            selection = TextRange(0, multiline.length)
        )
        val result = MarkdownFormatter.applyAction(initial, MarkdownAction.QUOTE)
        assertEquals("> Line one\n> Line two", result.text)
    }

    @Test
    fun testQuoteToggleOff() {
        val quoted = "> Line one\n> Line two"
        val initial = TextFieldValue(
            text = quoted,
            selection = TextRange(0, quoted.length)
        )
        val result = MarkdownFormatter.applyAction(initial, MarkdownAction.QUOTE)
        assertEquals("Line one\nLine two", result.text)
    }

    @Test
    fun testTaskListWithSelection() {
        val list = "Buy groceries\nWalk the dog"
        val initial = TextFieldValue(
            text = list,
            selection = TextRange(0, list.length)
        )
        val result = MarkdownFormatter.applyAction(initial, MarkdownAction.TASK_CHECKBOX)
        assertEquals("- [ ] Buy groceries\n- [ ] Walk the dog", result.text)
    }

    @Test
    fun testTaskListToggleOff() {
        val list = "- [ ] Buy groceries\n- [ ] Walk the dog"
        val initial = TextFieldValue(
            text = list,
            selection = TextRange(0, list.length)
        )
        val result = MarkdownFormatter.applyAction(initial, MarkdownAction.TASK_CHECKBOX)
        assertEquals("Buy groceries\nWalk the dog", result.text)
    }

    @Test
    fun testSingleLineCodeWithSelection() {
        val initial = TextFieldValue(
            text = "val x = 10",
            selection = TextRange(4, 9) // "x = 1"
        )
        val result = MarkdownFormatter.applyAction(initial, MarkdownAction.CODE)
        assertEquals("val `x = 1`0", result.text)
    }

    @Test
    fun testMultiLineCodeWithSelection() {
        val multiline = "fun test() {\n    return 42\n}"
        val initial = TextFieldValue(
            text = multiline,
            selection = TextRange(0, multiline.length)
        )
        val result = MarkdownFormatter.applyAction(initial, MarkdownAction.CODE)
        assertEquals("```\nfun test() {\n    return 42\n}\n```", result.text)
    }

    @Test
    fun testHashtagWithSelection() {
        val initial = TextFieldValue(
            text = "Check out my vacation",
            selection = TextRange(13, 21) // "vacation"
        )
        val result = MarkdownFormatter.applyAction(initial, MarkdownAction.HASHTAG)
        assertEquals("Check out my #vacation", result.text)
    }
}
