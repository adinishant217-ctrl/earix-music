//! Development tool for cross-language equivalence and benchmarking.
//!
//! Not shipped in the APK (the Android build only packages the cdylib), but it shares the
//! exact parser the Kotlin app calls, so its output is the reference the Kotlin side is
//! diffed against.
//!
//! Usage:
//!   lrc-parity dump  <file>              canonical TSV on stdout
//!   lrc-parity bench <file> <iters>      timing for a corpus file
//!   lrc-parity gen   <lines> <out>       write a deterministic synthetic corpus

use std::io::Write;
use std::process::ExitCode;
use std::time::Instant;

fn escape(text: &str) -> String {
    let mut out = String::with_capacity(text.len());
    for c in text.chars() {
        match c {
            '\\' => out.push_str("\\\\"),
            '\t' => out.push_str("\\t"),
            '\n' => out.push_str("\\n"),
            '\r' => out.push_str("\\r"),
            other => out.push(other),
        }
    }
    out
}

fn dump(data: &str) -> String {
    let mut out = String::new();
    for line in earix_core::lyrics::parse_lrc_lines_str(data) {
        out.push_str(&line.start_time_ms.to_string());
        out.push('\t');
        out.push_str(&escape(&line.text));
        out.push('\n');
    }
    out
}

/// Deterministic synthetic corpus: varied timestamps, unicode, blank lines, malformed rows.
fn synthesize(lines: usize) -> String {
    let words = [
        "hold", "on", "to", "me", "夜", "明け", "🎵", "amor", "&amp;", "quiet", "storm",
        "さよなら", "never", "again", "heart", "beat",
    ];
    let mut s = String::with_capacity(lines * 48);
    for i in 0..lines {
        match i % 11 {
            0 => s.push('\n'),
            1 => s.push_str("[bad line]\n"),
            2 => s.push_str("\n"),
            _ => {
                let total_cs = (i * 37) % 18_000;
                let minutes = total_cs / 6_000;
                let seconds = (total_cs % 6_000) / 100;
                let centis = total_cs % 100;
                s.push_str(&format!("[{minutes:02}:{seconds:02}.{centis:02}]"));
                if i % 23 == 0 {
                    s.push_str("   \n");
                    continue;
                }
                if i % 7 == 0 {
                    s.push_str("   ");
                }
                s.push_str(words[i % words.len()]);
                s.push(' ');
                s.push_str(words[(i * 3) % words.len()]);
                s.push('\n');
            }
        }
    }
    s
}

fn main() -> ExitCode {
    let args: Vec<String> = std::env::args().skip(1).collect();
    match args.first().map(String::as_str) {
        Some("dump") if args.len() == 2 => {
            let data = std::fs::read_to_string(&args[1]).expect("read corpus");
            print!("{}", dump(&data));
            ExitCode::SUCCESS
        }
        Some("gen") if args.len() == 3 => {
            let lines: usize = args[1].parse().expect("line count");
            let mut f = std::fs::File::create(&args[2]).expect("create corpus");
            f.write_all(synthesize(lines).as_bytes()).expect("write corpus");
            ExitCode::SUCCESS
        }
        Some("bench") if args.len() == 3 => {
            let data = std::fs::read_to_string(&args[1]).expect("read corpus");
            let iters: u32 = args[2].parse().expect("iterations");

            // Warm-up (also forces allocator/page warmup), then measure.
            let mut sink = 0usize;
            for _ in 0..(iters / 10).max(1) {
                sink += earix_core::lyrics::parse_lrc_lines_str(&data).len();
            }

            let start = Instant::now();
            for _ in 0..iters {
                sink += earix_core::lyrics::parse_lrc_lines_str(&data).len();
            }
            let elapsed = start.elapsed();

            let lines = earix_core::lyrics::parse_lrc_lines_str(&data).len();
            let per_iter_ns = elapsed.as_nanos() as f64 / f64::from(iters);
            println!("corpus_bytes={}", data.len());
            println!("parsed_lines={lines}");
            println!("iterations={iters}");
            println!("total_ns={}", elapsed.as_nanos());
            println!("ns_per_document={per_iter_ns:.1}");
            println!(
                "docs_per_second={:.1}",
                f64::from(iters) / elapsed.as_secs_f64()
            );
            println!(
                "lines_per_second={:.1}",
                (lines as f64 * f64::from(iters)) / elapsed.as_secs_f64()
            );
            println!("sink={sink}");
            ExitCode::SUCCESS
        }
        _ => {
            eprintln!("usage: lrc-parity dump <file> | bench <file> <iters> | gen <lines> <out>");
            ExitCode::FAILURE
        }
    }
}
