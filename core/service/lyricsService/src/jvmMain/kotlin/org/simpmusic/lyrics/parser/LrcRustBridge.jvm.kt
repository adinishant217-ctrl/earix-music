package org.simpmusic.lyrics.parser

/**
 * Desktop deliberately stays on the Kotlin implementation: the Rust core is built for Android
 * ABIs only in this batch, and shipping desktop natives is out of scope until the Android path
 * has been verified on device.
 */
internal actual fun parseLrcWithRust(data: String): List<ParsedLrcLine>? = null
