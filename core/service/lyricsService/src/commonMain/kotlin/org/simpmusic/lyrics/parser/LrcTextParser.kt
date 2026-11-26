package org.simpmusic.lyrics.parser

import com.maxrave.domain.extension.decodeHtmlEntities
import org.simpmusic.lyrics.domain.Lyrics

/**
 * Size gate for the Rust parser, in UTF-16 chars.
 *
 * MEASURED, not guessed. `scripts/rust/verify-lrc-parity.sh` runs the real Kotlin parser and the
 * real Rust parser through the same UniFFI/JNA bindings, on the JVM (x86_64, JDK 21). Six runs:
 *
 * | document                  | Kotlin        | Rust          | result            |
 * |---------------------------|---------------|---------------|-------------------|
 * | 10 lines / 266 B          | 7.8-13.1 us   | 33.4-42.0 us  | Kotlin 3.0-4.4x faster |
 * | 3,635 lines / 76 KB       | 1.30-1.75 ms  | 0.91-1.25 ms  | Rust 1.40-1.62x faster |
 *
 * The Rust side pays a fixed ~31-40 us per document to cross the UniFFI boundary and lift a list
 * of records, against ~3-6 us of Kotlin call overhead. Fitting both shapes puts the crossover at
 * roughly 160-320 lines (8-16 KB), so ordinary lyrics are always FASTER in Kotlin and the Rust
 * path is only used above this gate, where it measurably wins.
 *
 * This is deliberate, not a temporary default: shipping Rust for small documents would make the
 * feature slower. Re-measure on a device before lowering it — ART's JNA cost is not identical to
 * HotSpot's — and note that the durable fix for small documents is a JNI or packed-buffer binding,
 * not a lower gate.
 */
private const val LRC_RUST_MIN_CHARS = 32_768

/**
 * Parses line-synced LRC lyrics.
 *
 * Delegates the text/timestamp work to the Rust core on Android and falls back to
 * [parseSyncedLrcLinesKotlin] everywhere else, or whenever the Rust core is unavailable. Both
 * paths produce the same [ParsedLrcLine] list, and [decodeHtmlEntities] is applied afterwards by
 * this function regardless of which one ran — so the rendered result cannot diverge.
 *
 * Sync/timing semantics are unchanged: centiseconds become milliseconds (x10), `endTimeMs` stays
 * `"0"`, and the sync type stays `LINE_SYNCED`. Playback reads these values exactly as before.
 */
fun parseSyncedLyrics(data: String): Lyrics {
    val parsed = (if (data.length >= LRC_RUST_MIN_CHARS) parseLrcWithRust(data) else null)
        ?: parseSyncedLrcLinesKotlin(data)
    val linesLyrics = ArrayList<Lyrics.LyricsX.Line>(parsed.size)
    for (line in parsed) {
        linesLyrics.add(
            Lyrics.LyricsX.Line(
                endTimeMs = "0",
                startTimeMs = line.startTimeMs.toString(),
                syllables = listOf(),
                words = decodeHtmlEntities(line.text),
            ),
        )
    }
    return Lyrics(
        lyrics =
            Lyrics.LyricsX(
                lines = linesLyrics,
                syncType = "LINE_SYNCED",
            ),
    )
}

/**
 * Kotlin reference implementation of the LRC line parser.
 *
 * Kept as the runtime fallback and as the equivalence oracle for the Rust port. Do not "clean up"
 * its quirks — the Rust implementation mirrors them on purpose, and the golden tests in
 * `LrcParserGoldenTest` plus the Rust `lrc_document` test assert the same expectations:
 *
 * * the document is trimmed before splitting, so trailing whitespace on the FINAL line is gone
 *   (a whitespace-only final line therefore stops matching and is dropped entirely);
 * * `(.+)` requires at least one content character, so a bare `[00:24.00]` line is dropped while
 *   `[00:24.00]   ` is kept as a `♫` line;
 * * only leading whitespace is trimmed from the content.
 */
internal fun parseSyncedLrcLinesKotlin(data: String): List<ParsedLrcLine> {
    val regex = Regex("\\[(\\d{2}):(\\d{2})\\.(\\d{2})\\](.+)")
    // Strip leading/trailing blank lines (publishers often end with "\n", which would
    // otherwise produce a final empty split). Interior lines are untouched.
    val lines = data.trim().lines()
    val parsed = ArrayList<ParsedLrcLine>(lines.size)
    for (line in lines) {
        val matchResult = regex.matchEntire(line) ?: continue
        val minutes = matchResult.groupValues[1].toLong()
        val seconds = matchResult.groupValues[2].toLong()
        // Group 3 is CENTIseconds (LRC [mm:ss.xx]) — ×10 for milliseconds. The rich
        // parser already does this; the line parser previously added the raw value,
        // shrinking every timestamp 10x and breaking sync.
        val milliseconds = matchResult.groupValues[3].toLong() * 10
        val timeInMillis = minutes * 60_000L + seconds * 1000L + milliseconds
        // Trim, don't drop a fixed first character. Most providers leave a space after
        // "]", but LRCLIB packs the text right against it — "[00:27.12]boy, you got me"
        // — so removeRange(0, 1) ate the first letter of every single line.
        val rawContent = matchResult.groupValues[4]
        val content = if (rawContent.isBlank()) "♫" else rawContent.trimStart()
        parsed.add(ParsedLrcLine(startTimeMs = timeInMillis, text = content))
    }
    return parsed
}

fun parseRichSyncLyrics(data: String): Lyrics {
    // Unescape JSON string if needed (remove quotes and replace \n with actual newlines)
    val unescapedData =
        data
            .trim()
            .removePrefix("\"")
            .removeSuffix("\"")
            .replace("\\\\", "\\")
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")

    // Handle different line separators (Unix \n, Windows \r\n, Mac \r)
    val lines = unescapedData.lines()
    // Skip offset line if present (starts with [offset:)
    val lyricsLines =
        lines.filter { line ->
            line.isNotBlank() && !line.trim().startsWith("[offset:")
        }

    println("[parseRichSyncLyrics] Total lines: ${lines.size}, Filtered lines: ${lyricsLines.size}")
    if (lyricsLines.isNotEmpty()) {
        println("[parseRichSyncLyrics] First line sample: ${lyricsLines.first()}")
    }

    // Regex to match [MM:SS.mm] format (flexible with 1-2 digits)
    val regex = Regex("\\[(\\d{1,2}):(\\d{2})\\.(\\d{2,3})\\](.+)")
    // Strip a leading voice marker (e.g. "v1:", "v2:") that some lyrics
    // providers prepend before the first word timestamp, so it doesn't leak
    // into the word content and stick to the first word.
    val voiceMarkerRegex = Regex("""^v\d+:""")
    val linesLyrics = ArrayList<Lyrics.LyricsX.Line>()

    lyricsLines.forEachIndexed { index, line ->
        val matchResult = regex.matchEntire(line.trim())
        if (matchResult != null) {
            val minutes = matchResult.groupValues[1].toLongOrNull() ?: 0L
            val seconds = matchResult.groupValues[2].toLongOrNull() ?: 0L
            val centiseconds = matchResult.groupValues[3].toLongOrNull() ?: 0L

            // Convert to milliseconds
            // If centiseconds has 3 digits (milliseconds), use directly
            // If 2 digits (centiseconds), multiply by 10
            val millisPart = if (matchResult.groupValues[3].length == 3) centiseconds else centiseconds * 10
            val timeInMillis = minutes * 60_000L + seconds * 1000L + millisPart

            // Keep the rich sync content as-is (with <MM:SS.mm> word format)
            val content =
                matchResult.groupValues[4]
                    .trimStart()
                    .replace(voiceMarkerRegex, "")
                    .trimStart()

            if (content.isNotBlank()) {
                linesLyrics.add(
                    Lyrics.LyricsX.Line(
                        endTimeMs = "0",
                        startTimeMs = timeInMillis.toString(),
                        syllables = listOf(),
                        words = content,
                    ),
                )
            }
        } else {
            if (index < 3) { // Only log first 3 failed matches to avoid spam
                println("[parseRichSyncLyrics] Line $index failed to match: '${line.take(100)}'")
            }
        }
    }

    println("[parseRichSyncLyrics] Parsed ${linesLyrics.size} lines successfully")

    return Lyrics(
        lyrics =
            Lyrics.LyricsX(
                lines = linesLyrics,
                syncType = "RICH_SYNCED",
            ),
    )
}

/**
 * Parse TTML (Timed Text Markup Language) lyrics from BetterLyrics.
 * Supports both line-synced and word-by-word synced lyrics.
 *
 * TTML format: `<p begin="M:SS.mmm" end="M:SS.mmm">` contains `<span begin="..." end="...">word</span>`
 * If spans with timing exist → word-by-word (RICH_SYNCED)
 * If no spans → line-synced (LINE_SYNCED)
 */
fun parseTtmlLyrics(data: String): Lyrics {
    val linesLyrics = ArrayList<Lyrics.LyricsX.Line>()
    var hasWordTiming = false

    // Match each <p ...>...</p> element (use [\s\S] instead of . with DOT_MATCHES_ALL)
    val pRegex = Regex("""<p\s[^>]*begin="([^"]+)"[^>]*end="([^"]+)"[^>]*>([\s\S]*?)</p>""")
    // Match each <span ...>word</span> element
    val spanRegex = Regex("""<span\s[^>]*begin="([^"]+)"[^>]*end="([^"]+)"[^>]*>(.*?)</span>""")

    for (pMatch in pRegex.findAll(data)) {
        val lineBegin = parseTtmlTime(pMatch.groupValues[1])
        val lineEnd = parseTtmlTime(pMatch.groupValues[2])
        val innerContent = pMatch.groupValues[3]

        val spans = spanRegex.findAll(innerContent).toList()

        if (spans.isNotEmpty()) {
            hasWordTiming = true
            // Build word-by-word content with <MM:SS.mm> timing format for rich sync
            val wordParts = StringBuilder()
            for (span in spans) {
                val spanBegin = parseTtmlTime(span.groupValues[1])
                val word = span.groupValues[3].trim()
                if (word.isNotEmpty()) {
                    val beginFormatted = formatMsToLrc(spanBegin)
                    wordParts.append("<$beginFormatted>$word ")
                }
            }
            val words = wordParts.toString().trimEnd()
            if (words.isNotBlank()) {
                linesLyrics.add(
                    Lyrics.LyricsX.Line(
                        startTimeMs = lineBegin.toString(),
                        endTimeMs = lineEnd.toString(),
                        syllables = listOf(),
                        words = words,
                    ),
                )
            }
        } else {
            // No spans — extract plain text (strip any remaining tags)
            val plainText = innerContent.replace(Regex("<[^>]*>"), "").trim()
            if (plainText.isNotBlank()) {
                linesLyrics.add(
                    Lyrics.LyricsX.Line(
                        startTimeMs = lineBegin.toString(),
                        endTimeMs = lineEnd.toString(),
                        syllables = listOf(),
                        words = plainText,
                    ),
                )
            }
        }
    }

    return Lyrics(
        lyrics =
            Lyrics.LyricsX(
                lines = linesLyrics,
                syncType = if (hasWordTiming) "RICH_SYNCED" else "LINE_SYNCED",
            ),
    )
}

/**
 * Parse TTML time format to milliseconds.
 * Supports: "M:SS.mmm", "MM:SS.mmm", "H:MM:SS.mmm", "SS.mmm"
 */
private fun parseTtmlTime(time: String): Long {
    val parts = time.split(":")
    return when (parts.size) {
        3 -> {
            val hours = parts[0].toLongOrNull() ?: 0L
            val minutes = parts[1].toLongOrNull() ?: 0L
            val secParts = parts[2].split(".")
            val seconds = secParts[0].toLongOrNull() ?: 0L
            val millis = parseMillisPart(secParts.getOrNull(1))
            hours * 3_600_000L + minutes * 60_000L + seconds * 1000L + millis
        }
        2 -> {
            val minutes = parts[0].toLongOrNull() ?: 0L
            val secParts = parts[1].split(".")
            val seconds = secParts[0].toLongOrNull() ?: 0L
            val millis = parseMillisPart(secParts.getOrNull(1))
            minutes * 60_000L + seconds * 1000L + millis
        }
        else -> {
            val secParts = time.split(".")
            val seconds = secParts[0].toLongOrNull() ?: 0L
            val millis = parseMillisPart(secParts.getOrNull(1))
            seconds * 1000L + millis
        }
    }
}

private fun parseMillisPart(part: String?): Long {
    if (part.isNullOrEmpty()) return 0L
    val value = part.toLongOrNull() ?: return 0L
    return when (part.length) {
        1 -> value * 100
        2 -> value * 10
        3 -> value
        else -> value
    }
}

private fun formatMsToLrc(ms: Long): String {
    val minutes = ms / 60_000L
    val seconds = (ms % 60_000L) / 1000L
    val centis = (ms % 1000L) / 10L
    val m = if (minutes < 10) "0$minutes" else "$minutes"
    val s = if (seconds < 10) "0$seconds" else "$seconds"
    val c = if (centis < 10) "0$centis" else "$centis"
    return "$m:$s.$c"
}

fun parseUnsyncedLyrics(data: String): Lyrics {
    val lines = data.lines()
    val linesLyrics = ArrayList<Lyrics.LyricsX.Line>()
    lines.map { line ->
        linesLyrics.add(
            Lyrics.LyricsX.Line(
                endTimeMs = "0",
                startTimeMs = "0",
                syllables = listOf(),
                words = line,
            ),
        )
    }
    return Lyrics(
        lyrics =
            Lyrics.LyricsX(
                lines = linesLyrics,
                syncType = "UNSYNCED",
            ),
    )
}