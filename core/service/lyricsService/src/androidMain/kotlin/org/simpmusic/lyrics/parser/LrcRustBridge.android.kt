package org.simpmusic.lyrics.parser

import com.maxrave.earixrust.RustLyricsBridge

/**
 * Android is the only target that ships the Rust core. The bridge already swallows load and call
 * failures and returns `null`, which sends the caller back to Kotlin.
 */
internal actual fun parseLrcWithRust(data: String): List<ParsedLrcLine>? =
    RustLyricsBridge.parseLrc(data)?.map { line -> ParsedLrcLine(line.startTimeMs, line.text) }
