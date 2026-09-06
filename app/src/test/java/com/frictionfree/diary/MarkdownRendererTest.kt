package com.frictionfree.diary

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import com.frictionfree.diary.ui.components.buildMarkdownAnnotatedString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownRendererTest {

    @Test
    fun testStandardMarkdownLink() {
        val input = "Check out [Google Search](https://google.com) for details."
        var clickedUrl: String? = null

        val annotated = buildMarkdownAnnotatedString(
            text = input,
            linkColor = Color.Blue,
            tagColor = Color.Magenta,
            codeBackgroundColor = Color.LightGray,
            onTagClick = {},
            onUrlClick = { clickedUrl = it }
        )

        assertEquals("Check out Google Search for details.", annotated.text)

        val linkAnnotations = annotated.getLinkAnnotations(0, annotated.length)
        assertEquals(1, linkAnnotations.size)
        val urlAnnotation = linkAnnotations[0].item as LinkAnnotation.Url
        assertEquals("https://google.com", urlAnnotation.url)
    }

    @Test
    fun testDayOneAngleBracketLink() {
        val input = "Reading [Matthew 21-23](<https://www.churchofjesuschrist.org/study/scriptures/nt/matt/21?lang=eng>) today."

        val annotated = buildMarkdownAnnotatedString(
            text = input,
            linkColor = Color.Blue,
            tagColor = Color.Magenta,
            codeBackgroundColor = Color.LightGray,
            onTagClick = {},
            onUrlClick = {}
        )

        assertEquals("Reading Matthew 21-23 today.", annotated.text)

        val linkAnnotations = annotated.getLinkAnnotations(0, annotated.length)
        assertEquals(1, linkAnnotations.size)
        val urlAnnotation = linkAnnotations[0].item as LinkAnnotation.Url
        assertEquals("https://www.churchofjesuschrist.org/study/scriptures/nt/matt/21?lang=eng", urlAnnotation.url)
    }

    @Test
    fun testMultipleLinksInHeading() {
        val input = "May 15-21 [Matthew 21-23](<https://example.com/matt>); [Mark 11](<https://example.com/mark>)"

        val annotated = buildMarkdownAnnotatedString(
            text = input,
            linkColor = Color.Blue,
            tagColor = Color.Magenta,
            codeBackgroundColor = Color.LightGray,
            onTagClick = {},
            onUrlClick = {}
        )

        assertEquals("May 15-21 Matthew 21-23; Mark 11", annotated.text)

        val linkAnnotations = annotated.getLinkAnnotations(0, annotated.length)
        assertEquals(2, linkAnnotations.size)
        assertEquals("https://example.com/matt", (linkAnnotations[0].item as LinkAnnotation.Url).url)
        assertEquals("https://example.com/mark", (linkAnnotations[1].item as LinkAnnotation.Url).url)
    }

    @Test
    fun testDayOneFootnoteLinkWithHtml() {
        val input = "Historical context.[<sup>1</sup>](<https://example.com/blessings#note1>) Next point."

        val annotated = buildMarkdownAnnotatedString(
            text = input,
            linkColor = Color.Blue,
            tagColor = Color.Magenta,
            codeBackgroundColor = Color.LightGray,
            onTagClick = {},
            onUrlClick = {}
        )

        assertEquals("Historical context.1 Next point.", annotated.text)

        val linkAnnotations = annotated.getLinkAnnotations(0, annotated.length)
        assertEquals(1, linkAnnotations.size)
        assertEquals("https://example.com/blessings#note1", (linkAnnotations[0].item as LinkAnnotation.Url).url)
    }

    @Test
    fun testRawUrlExcludesTrailingPunctuation() {
        val input = "Watch this: https://youtu.be/1hQoAnqIt0w?si=RNHiI-HWqKj8Rnt2. Great video!"

        val annotated = buildMarkdownAnnotatedString(
            text = input,
            linkColor = Color.Blue,
            tagColor = Color.Magenta,
            codeBackgroundColor = Color.LightGray,
            onTagClick = {},
            onUrlClick = {}
        )

        assertEquals("Watch this: https://youtu.be/1hQoAnqIt0w?si=RNHiI-HWqKj8Rnt2. Great video!", annotated.text)

        val linkAnnotations = annotated.getLinkAnnotations(0, annotated.length)
        assertEquals(1, linkAnnotations.size)
        assertEquals("https://youtu.be/1hQoAnqIt0w?si=RNHiI-HWqKj8Rnt2", (linkAnnotations[0].item as LinkAnnotation.Url).url)
    }

    @Test
    fun testHashtagClickableAnnotation() {
        val input = "Thoughts on #mindfulness and #peace today."
        var selectedTag: String? = null

        val annotated = buildMarkdownAnnotatedString(
            text = input,
            linkColor = Color.Blue,
            tagColor = Color.Magenta,
            codeBackgroundColor = Color.LightGray,
            onTagClick = { selectedTag = it },
            onUrlClick = {}
        )

        assertEquals("Thoughts on #mindfulness and #peace today.", annotated.text)

        val linkAnnotations = annotated.getLinkAnnotations(0, annotated.length)
        assertEquals(2, linkAnnotations.size)
        assertTrue(linkAnnotations[0].item is LinkAnnotation.Clickable)
        assertEquals("mindfulness", (linkAnnotations[0].item as LinkAnnotation.Clickable).tag)
        assertEquals("peace", (linkAnnotations[1].item as LinkAnnotation.Clickable).tag)
    }

    @Test
    fun testNullImageLabelFallback() {
        val input = "Attached: [null](<https://example.com/image.webp>)"

        val annotated = buildMarkdownAnnotatedString(
            text = input,
            linkColor = Color.Blue,
            tagColor = Color.Magenta,
            codeBackgroundColor = Color.LightGray,
            onTagClick = {},
            onUrlClick = {}
        )

        assertEquals("Attached: ðŸ“· Image", annotated.text)
        val linkAnnotations = annotated.getLinkAnnotations(0, annotated.length)
        assertEquals(1, linkAnnotations.size)
        assertEquals("https://example.com/image.webp", (linkAnnotations[0].item as LinkAnnotation.Url).url)
    }
}