package org.simpmusic.lyrics.parser

/**
 * One timed lyric line, independent of the Rust binding type so that common code never sees a
 * platform-only class.
 */
internal data class ParsedLrcLine(
    val startTimeMs: Long,
    val text: String,
)

/**
 * Parses an LRC document with the Rust core where one is available.
 *
 * Returns `null` when Rust cannot be used (Android without the native library, desktop, iOS, or
 * any failure inside the bridge). Callers must then use the Kotlin implementation — that is the
 * whole fallback contract, and it is why this returns nullable rather than throwing.
 *
 * An **empty list** is a valid result ("this document has no timed lines"), not a failure.
 */
internal expect fun parseLrcWithRust(data: String): List<ParsedLrcLine>?
