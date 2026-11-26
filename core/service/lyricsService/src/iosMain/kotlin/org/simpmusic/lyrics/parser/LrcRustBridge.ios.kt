package org.simpmusic.lyrics.parser

/** iOS has no Rust core in this batch; the Kotlin parser is authoritative there. */
internal actual fun parseLrcWithRust(data: String): List<ParsedLrcLine>? = null
