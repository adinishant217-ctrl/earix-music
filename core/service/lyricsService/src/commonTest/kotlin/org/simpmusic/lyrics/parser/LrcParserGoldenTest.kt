package org.simpmusic.lyrics.parser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Golden equivalence test for the LRC line parser.
 *
 * `REALISTIC_DOCUMENT` and the expected values below are byte-identical to the Rust test in
 * `rust/earix-core/tests/lrc_document.rs`. Both sides assert the same expectations, so if the
 * Rust port ever drifts from the Kotlin oracle, one of the two suites fails.
 *
 * This suite runs on every target. On Android the app uses the Rust path at runtime; on
 * desktop/iOS it exercises the Kotlin fallback, which is exactly the fallback contract.
 */
class LrcParserGoldenTest {
    private val realisticDocument =
        "[00:00.00] Artist: Hikaru Utada\n" +
            "[00:12.45] 光の向こうへ\n" +
            "[00:18.20]きらきら 舞い上がれ 🎵\n" +
            "[00:24.00]   \n" +
            "[00:31.75]   and the world keeps turning  \n" +
            "[00:37.10]&amp; we don't stop\n" +
            "[00:44.99]same time, same line\n" +
            "not a timed line\n" +
            "[00:51.01]   "

    private val expected =
        listOf(
            ParsedLrcLine(0, "Artist: Hikaru Utada"),
            ParsedLrcLine(12_450, "光の向こうへ"),
            ParsedLrcLine(18_200, "きらきら 舞い上がれ 🎵"),
            // Whitespace-only content on an INTERIOR line stays as `♫`.
            ParsedLrcLine(24_000, "♫"),
            ParsedLrcLine(31_750, "and the world keeps turning  "),
            ParsedLrcLine(37_100, "&amp; we don't stop"),
            ParsedLrcLine(44_990, "same time, same line"),
            // The FINAL line `[00:51.01]   ` is trimmed to `[00:51.01]`, loses its content and is
            // dropped — it never becomes `♫`. Mirrored exactly by the Rust implementation.
        )

    @Test
    fun parsesRealisticDocument() {
        assertEquals(expected, parseSyncedLrcLinesKotlin(realisticDocument))
    }

    @Test
    fun producesTheSameLyricsStructureAsBefore() {
        val lyrics = parseSyncedLyrics(realisticDocument)
        val lines = lyrics.lyrics?.lines
        assertEquals("LINE_SYNCED", lyrics.lyrics?.syncType)
        assertEquals(expected.size, lines?.size)
        for (i in expected.indices) {
            assertEquals("0", lines!![i].endTimeMs)
            assertEquals(expected[i].startTimeMs.toString(), lines[i].startTimeMs)
            assertEquals(emptyList(), lines[i].syllables)
        }
    }

    @Test
    fun stillDecodesHtmlEntities() {
        // Entity decoding deliberately stayed in Kotlin, after the Rust parse.
        val lyrics = parseSyncedLyrics(realisticDocument)
        assertEquals("& we don't stop", lyrics.lyrics?.lines?.get(5)?.words)
    }

    @Test
    fun centisecondsBecomeMilliseconds() {
        assertEquals(listOf(ParsedLrcLine(62_340, "x")), parseSyncedLrcLinesKotlin("[01:02.34]x"))
    }

    @Test
    fun emptyInputProducesNoLines() {
        assertTrue(parseSyncedLrcLinesKotlin("").isEmpty())
        assertTrue(parseSyncedLrcLinesKotlin("   \n\t \r\n ").isEmpty())
        assertEquals(emptyList(), parseSyncedLyrics("").lyrics?.lines)
    }

    @Test
    fun malformedInputIsIgnoredNotFatal() {
        val doc = "[0:00.00]short\n[00:00.000]wide\n[00:00:00]bad\nno timestamp\n[00:00.00]"
        assertTrue(parseSyncedLrcLinesKotlin(doc).isEmpty())
    }

    @Test
    fun handlesCrlfAndLoneCr() {
        val doc = "[00:01.00]a\r\n[00:02.00]b\r[00:03.00]c"
        assertEquals(
            listOf(ParsedLrcLine(1_000, "a"), ParsedLrcLine(2_000, "b"), ParsedLrcLine(3_000, "c")),
            parseSyncedLrcLinesKotlin(doc),
        )
    }

    @Test
    fun repeatedParsingIsStable() {
        val first = parseSyncedLrcLinesKotlin(realisticDocument)
        repeat(50) { assertEquals(first, parseSyncedLrcLinesKotlin(realisticDocument)) }
    }

    @Test
    fun largeDocumentIsParsedCompletely() {
        val doc = buildString {
            repeat(5_000) { i ->
                append("[").append((i / 6000).toString().padStart(2, '0')).append(":")
                append(((i % 6000) / 100).toString().padStart(2, '0')).append(".")
                append((i % 100).toString().padStart(2, '0')).append("]line ").append(i).append("\n")
            }
        }
        val parsed = parseSyncedLrcLinesKotlin(doc)
        assertEquals(5_000, parsed.size)
        assertEquals("line 4999", parsed.last().text)
    }
}
