//! End-to-end document test.
//!
//! `REALISTIC_DOCUMENT` is byte-identical to the constant used by the Kotlin golden test in
//! `core/service/lyricsService/src/commonTest/.../LrcParserGoldenTest.kt`. Both sides assert
//! the same expected (time, text) pairs, which is what makes the migration verifiable from
//! either language.

use earix_core::lyrics::{parse_lrc_lines_str, LrcLine};

const REALISTIC_DOCUMENT: &str = "\
[00:00.00] Artist: Hikaru Utada
[00:12.45] 光の向こうへ
[00:18.20]きらきら 舞い上がれ 🎵
[00:24.00]   
[00:31.75]   and the world keeps turning  
[00:37.10]&amp; we don&#39;t stop
[00:44.99]same time, same line
not a timed line
[00:51.01]   ";

fn expected() -> Vec<LrcLine> {
    vec![
        LrcLine {
            start_time_ms: 0,
            text: "Artist: Hikaru Utada".to_string(),
        },
        LrcLine {
            start_time_ms: 12_450,
            text: "光の向こうへ".to_string(),
        },
        LrcLine {
            start_time_ms: 18_200,
            text: "きらきら 舞い上がれ 🎵".to_string(),
        },
        LrcLine {
            start_time_ms: 24_000,
            text: "♫".to_string(),
        },
        // Whitespace-only content on an INTERIOR line: kept, rendered as `♫`. The same text
        // on the document's LAST line would be trimmed away by `data.trim()` and dropped.

        LrcLine {
            start_time_ms: 31_750,
            text: "and the world keeps turning  ".to_string(),
        },
        LrcLine {
            start_time_ms: 37_100,
            text: "&amp; we don&#39;t stop".to_string(),
        },
        LrcLine {
            start_time_ms: 44_990,
            text: "same time, same line".to_string(),
        },
        // The document's final line is `[00:51.01]   `. `data.trim()` strips those trailing
        // spaces, leaving `[00:51.01]` with no content, so `(.+)` does not match and the line
        // is dropped — it never becomes `♫`. Pinned on both sides of the migration.
    ]
}

#[test]
fn realistic_document_matches_expected_lines() {
    assert_eq!(parse_lrc_lines_str(REALISTIC_DOCUMENT), expected());
    assert_eq!(parse_lrc_lines_str(REALISTIC_DOCUMENT).len(), 7);
}

#[test]
fn repeated_parsing_is_stable() {
    // Guards against any accidental internal state (there is none today, and this keeps it so).
    let first = parse_lrc_lines_str(REALISTIC_DOCUMENT);
    for _ in 0..200 {
        assert_eq!(parse_lrc_lines_str(REALISTIC_DOCUMENT), first);
    }
}

#[test]
fn large_document_is_parsed_completely() {
    let mut doc = String::with_capacity(512 * 1024);
    for i in 0..10_000u32 {
        let total_cs = i % 36_000;
        doc.push_str(&format!(
            "[{:02}:{:02}.{:02}]line {i}\n",
            total_cs / 6_000,
            (total_cs % 6_000) / 100,
            total_cs % 100
        ));
    }
    let out = parse_lrc_lines_str(&doc);
    assert_eq!(out.len(), 10_000);
    assert_eq!(out[0].start_time_ms, 0);
    assert_eq!(out[9_999].text, "line 9999");
}

#[test]
fn concurrent_parsing_is_safe() {
    // The parser holds no state; prove it from several threads at once.
    let handles: Vec<_> = (0..8)
        .map(|_| {
            std::thread::spawn(|| {
                for _ in 0..50 {
                    assert_eq!(parse_lrc_lines_str(REALISTIC_DOCUMENT), expected());
                }
            })
        })
        .collect();
    for h in handles {
        h.join().expect("worker thread");
    }
}
