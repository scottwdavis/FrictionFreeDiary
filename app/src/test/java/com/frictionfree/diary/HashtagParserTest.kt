package com.frictionfree.diary

import com.frictionfree.diary.utils.HashtagParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HashtagParserTest {

    @Test
    fun testExtractStandardHashtags() {
        val text = "Had a great morning working on my #android project. Feeling #gratitude today!"
        val tags = HashtagParser.extractHashtags(text)

        assertEquals(2, tags.size)
        assertTrue(tags.contains("android"))
        assertTrue(tags.contains("gratitude"))
    }

    @Test
    fun testIgnoresMarkdownHeadings() {
        val text = """
            # Project Notes
            ## Subheading
            Here is a thought with #important tag.
            ### Minor Heading
            Another line with #focus tag.
        """.trimIndent()

        val tags = HashtagParser.extractHashtags(text)

        assertEquals(2, tags.size)
        assertTrue(tags.contains("important"))
        assertTrue(tags.contains("focus"))
        assertFalse(tags.contains("project"))
        assertFalse(tags.contains("subheading"))
    }

    @Test
    fun testIgnoresPureNumbersAndCleansPunctuation() {
        val text = "Issue #123 was discussed in #standup, followed by #coffee!"
        val tags = HashtagParser.extractHashtags(text)

        assertEquals(2, tags.size)
        assertTrue(tags.contains("standup"))
        assertTrue(tags.contains("coffee"))
        assertFalse(tags.contains("123"))
    }

    @Test
    fun testDuplicateTagsAreDeduplicatedAndNormalized() {
        val text = "Working on #Android and #android and #ANDROID."
        val tags = HashtagParser.extractHashtags(text)

        assertEquals(1, tags.size)
        assertEquals("android", tags[0])
    }
}
