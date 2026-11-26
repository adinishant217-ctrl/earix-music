//! Stage 2 measurement harness (Rust half).
//!
//! Pairs with `scripts/rust/verify-stage2-parity.sh`, which runs the Kotlin side of the same
//! workload through the real UniFFI/JNA bindings on the JVM. Numbers from this binary are what the
//! parity script prints as the "Rust, in-process" column; the script's JVM half prints the
//! "Rust, through FFI" column so the boundary cost is visible instead of assumed.
//!
//! Usage:
//!   stage2-bench pow <prefix> <target> <attempts> <stride>
//!   stage2-bench powthreads <prefix> <target> <stride> <threads>
//!   stage2-bench powgen <target-nibbles>     suggested challenge target for a time budget

use std::process::ExitCode;
use std::sync::atomic::{AtomicI64, Ordering};
use std::time::Instant;

use earix_core::pow::{solve_pow_chunk_bytes, POW_MAX_NONCE};

fn main() -> ExitCode {
    let args: Vec<String> = std::env::args().skip(1).collect();
    match args.first().map(String::as_str) {
        Some("pow") if args.len() == 5 => {
            let prefix = args[1].as_bytes();
            let target = &args[2];
            let attempts: i64 = args[3].parse().expect("attempts");
            let stride: i64 = args[4].parse().expect("stride");

            let chunk = solve_pow_chunk_bytes(prefix, target, 0, stride, attempts).expect("chunk");
            if chunk.nonce.is_some() {
                // A solved chunk would stop early and understate throughput.
                eprintln!("warning: chunk solved at nonce {:?}", chunk.nonce);
            }

            let start = Instant::now();
            let chunk = solve_pow_chunk_bytes(prefix, target, 0, stride, attempts).expect("chunk");
            let elapsed = start.elapsed();

            let done = chunk.attempts.max(1);
            let ns_per_attempt = elapsed.as_nanos() as f64 / done as f64;
            println!("rust_attempts={done}");
            println!("rust_total_ns={}", elapsed.as_nanos());
            println!("rust_ns_per_attempt={ns_per_attempt:.2}");
            println!(
                "rust_attempts_per_sec={:.0}",
                done as f64 / elapsed.as_secs_f64()
            );
            ExitCode::SUCCESS
        }

        Some("powthreads") if args.len() == 5 => {
            let prefix = args[1].as_bytes().to_vec();
            let target = args[2].clone();
            let stride: i64 = args[3].parse().expect("stride");
            let threads: i64 = args[4].parse().expect("threads");

            let start = Instant::now();
            let winner = AtomicI64::new(-1);
            let total = AtomicI64::new(0);
            std::thread::scope(|scope| {
                for phase in 0..threads {
                    let prefix = prefix.clone();
                    let target = target.clone();
                    let winner = &winner;
                    let total = &total;
                    scope.spawn(move || {
                        let mut nonce = phase;
                        while winner.load(Ordering::Acquire) < 0 && nonce < POW_MAX_NONCE {
                            let chunk =
                                solve_pow_chunk_bytes(&prefix, &target, nonce, stride, 50_000)
                                    .expect("chunk");
                            total.fetch_add(chunk.attempts, Ordering::Relaxed);
                            match chunk.nonce {
                                Some(found) => {
                                    winner.store(found, Ordering::Release);
                                    break;
                                }
                                None => nonce += stride * 50_000,
                            }
                        }
                    });
                }
            });
            let elapsed = start.elapsed();
            let done = total.load(Ordering::Relaxed).max(1);
            println!("threads={threads}");
            println!("winner={}", winner.load(Ordering::Relaxed));
            println!("rust_attempts={done}");
            println!("rust_total_ns={}", elapsed.as_nanos());
            println!(
                "rust_attempts_per_sec={:.0}",
                done as f64 / elapsed.as_secs_f64()
            );
            ExitCode::SUCCESS
        }

        _ => {
            eprintln!(
                "usage: stage2-bench pow <prefix> <target> <attempts> <stride>\n       \
                 stage2-bench powthreads <prefix> <target> <stride> <threads>"
            );
            ExitCode::FAILURE
        }
    }
}
