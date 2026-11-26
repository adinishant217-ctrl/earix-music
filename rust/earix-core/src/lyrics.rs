//! Line-synced LRC parsing.
//!
//! This is a byte-for-byte behavioural port of Kotlin's `parseSyncedLyrics` content
//! extraction. Every rule below is deliberate and matches the Kotlin original; the
//! equivalence tests in `tests/lrc_parity.rs` and the Kotlin golden tests pin them:
//!
//! * The document is trimmed first, then split into lines (Java/Kotlin `lines()` splits on
//!   `\r\n`, `\r` and `\n`; splitting on either byte independently is equivalent here
//!   because an empty segment can never match a line pattern).
//! * A line must match `^\[(\d{2}):(\d{2})\.(\d{2})\](.+)$` — exactly two ASCII digits for
//!   minutes, seconds and centiseconds, and at least one content character. Java's `\d` is
//!   ASCII-only in the default flags, and Rust compares ASCII digits explicitly.
//! * The centiseconds field is multiplied by 10 to reach milliseconds (LRC `[mm:ss.xx]`).
//! * A line whose content is entirely whitespace becomes `♫`.
//! * Otherwise leading whitespace is trimmed and the rest is kept verbatim.
//!
//! HTML entity decoding is intentionally **not** done here: it stays in Kotlin
//! (`decodeHtmlEntities`) so this migration cannot change the rendered text.

/// One parsed LRC line. Mirrors the Kotlin `Lyrics.LyricsX.Line` prefix/words pair.
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct LrcLine {
    /// Start time in milliseconds, derived from `[mm:ss.xx]`.
    pub start_time_ms: i64,
    /// Lyric text: leading whitespace trimmed, whitespace-only content replaced by `♫`.
    pub text: String,
}

/// Parse an LRC document into timed lines. FFI entry point.
///
/// Takes `String` (owned) because UniFFI must copy the Kotlin string across the boundary
/// exactly once; the parsing itself borrows it through [`parse_lrc_lines_str`].
#[uniffi::export]
pub fn parse_lrc_lines(data: String) -> Vec<LrcLine> {
    parse_lrc_lines_str(&data)
}

/// Borrowing form used by the test suite, the parity tool and the benchmark harness.
pub fn parse_lrc_lines_str(data: &str) -> Vec<LrcLine> {
    // Kotlin: `data.trim().lines()` — a whitespace-only document produces a single empty
    // line that can never match, so returning early is equivalent.
    let trimmed = data.trim_matches(is_java_whitespace);
    if trimmed.is_empty() {
        return Vec::new();
    }

    let mut out: Vec<LrcLine> = Vec::new();
    for line in trimmed.split(|c| c == '\n' || c == '\r') {
        if let Some(parsed) = parse_line(line) {
            out.push(parsed);
        }
    }
    out
}

/// Parse one line, mirroring `Regex("\\[(\\d{2}):(\\d{2})\\.(\\d{2})\\](.+)")` + `matchEntire`.
fn parse_line(line: &str) -> Option<LrcLine> {
    let bytes = line.as_bytes();

    // Shortest possible match: "[00:00.00]" (10 bytes) + at least 1 content byte.
    if bytes.len() < 11 {
        return None;
    }

    if bytes[0] != b'[' || bytes[3] != b':' || bytes[6] != b'.' || bytes[9] != b']' {
        return None;
    }

    let minutes = two_digits(bytes, 1)?;
    let seconds = two_digits(bytes, 4)?;
    let centiseconds = two_digits(bytes, 7)?;

    // Index 10 is a char boundary: the first ten bytes are ASCII.
    let content = &line[10..];
    if content.is_empty() {
        return None;
    }

    // Java's `.` excludes line terminators. `\r` and `\n` are already gone because we split
    // on them, but U+2028/U+2029 are not split points in Kotlin's `lines()` while still
    // failing `matchEntire`, so a line containing them must not match either.
    if content.contains(['\u{0085}', '\u{2028}', '\u{2029}']) {
        return None;
    }

    let start_time_ms = minutes as i64 * 60_000 + seconds as i64 * 1_000 + centiseconds as i64 * 10;

    let text = if content.trim_matches(is_java_whitespace).is_empty() {
        // Kotlin: `if (rawContent.isBlank()) "♫" else ...`
        "♫".to_string()
    } else {
        content
            .trim_start_matches(is_java_whitespace)
            .to_string()
    };

    Some(LrcLine { start_time_ms, text })
}

/// Read exactly two ASCII digits at `offset`.
#[inline]
fn two_digits(bytes: &[u8], offset: usize) -> Option<u32> {
    let hi = bytes[offset].wrapping_sub(b'0');
    let lo = bytes[offset + 1].wrapping_sub(b'0');
    if hi > 9 || lo > 9 {
        return None;
    }
    Some(u32::from(hi) * 10 + u32::from(lo))
}

/// Kotlin's `Char.isWhitespace()` is `Character.isWhitespace(c) || Character.isSpaceChar(c)`.
///
/// That union is Unicode `White_Space` (what `char::is_whitespace` uses) plus the separator
/// characters Java counts through `isSpaceChar` but Unicode does not mark as White_Space.
/// In the BMP the only such code point is U+2007 FIGURE SPACE; U+00A0 and U+202F are also
/// `Zs` but are already White_Space. Everything else (`Zs`, `Zl`, `Zp` and the ASCII/control
/// whitespace) is covered by `char::is_whitespace`.
#[inline]
fn is_java_whitespace(c: char) -> bool {
    c.is_whitespace() || c == '\u{2007}'
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_a_basic_line() {
        let out = parse_lrc_lines_str("[00:27.12]boy, you got me");
        assert_eq!(
            out,
            vec![LrcLine {
                start_time_ms: 27_120,
                text: "boy, you got me".to_string()
            }]
        );
    }

    #[test]
    fn centiseconds_become_milliseconds() {
        // 1:02.34 -> 62_340 ms, not 62_034.
        let out = parse_lrc_lines_str("[01:02.34]x");
        assert_eq!(out[0].start_time_ms, 62_340);
    }

    #[test]
    fn ignores_lines_that_do_not_match() {
        let doc = "\nno timestamp\n[00:00:00]bad\n[0:00.00]short\n[00:00.000]wide\n[00:00.00]\n";
        assert!(parse_lrc_lines_str(doc).is_empty());
    }

    #[test]
    fn keeps_blank_content_as_note_glyph() {
        // Must not be the last line: `data.trim()` strips trailing document whitespace, which
        // would leave `[00:05.00]` with no content to match.
        let out = parse_lrc_lines_str("[00:05.00]   \n[00:06.00]next");
        assert_eq!(out[0].text, "♫");
    }

    #[test]
    fn trims_only_leading_whitespace() {
        // Interior line, so the document trim cannot reach its trailing spaces.
        let out = parse_lrc_lines_str("[00:05.00]  hello  world  \n[00:06.00]next");
        assert_eq!(out[0].text, "hello  world  ");
    }

    #[test]
    fn document_trim_removes_the_final_lines_trailing_spaces() {
        // Kotlin: `data.trim().lines()` — this is a real behavioural detail, not an accident.
        let out = parse_lrc_lines_str("[00:05.00]  hello  world  ");
        assert_eq!(out[0].text, "hello  world");

        // A whitespace-only final line loses its content entirely and stops matching `(.+)`.
        assert!(parse_lrc_lines_str("[00:05.00]x\n[00:06.00]   ").len() == 1);
    }

    #[test]
    fn handles_crlf_and_cr() {
        let doc = "[00:01.00]a\r\n[00:02.00]b\r[00:03.00]c";
        let out = parse_lrc_lines_str(doc);
        assert_eq!(out.len(), 3);
        assert_eq!(out[2].text, "c");
        assert_eq!(out[2].start_time_ms, 3_000);
    }

    #[test]
    fn preserves_unicode_and_entities() {
        let out = parse_lrc_lines_str("[00:09.99]日本語 🎵 &amp; &#39;");
        assert_eq!(out[0].text, "日本語 🎵 &amp; &#39;");
        assert_eq!(out[0].start_time_ms, 9_990);
    }

    #[test]
    fn empty_and_whitespace_documents() {
        assert!(parse_lrc_lines_str("").is_empty());
        assert!(parse_lrc_lines_str("   \n\t \r\n ").is_empty());
    }

    #[test]
    fn unicode_line_separator_does_not_match() {
        // U+2028 and U+2029 are not split points in Kotlin's `lines()` but do fail `matchEntire`.
        assert!(parse_lrc_lines_str("[00:01.00]a\u{2028}b").is_empty());
        assert!(parse_lrc_lines_str("[00:01.00]a\u{0085}b").is_empty());
    }

    #[test]
    fn figure_space_counts_as_whitespace() {
        let out = parse_lrc_lines_str("[00:01.00]\u{2007}\n[00:02.00]next");
        assert_eq!(out[0].text, "♫");
    }

    #[test]
    fn exported_entry_point_matches_borrowing_form() {
        let doc = "[00:01.00]a\n[00:02.00]b";
        assert_eq!(parse_lrc_lines(doc.to_string()), parse_lrc_lines_str(doc));
    }
}
