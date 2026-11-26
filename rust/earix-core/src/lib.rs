//! Earix Rust core.
//!
//! Scope: the line-synced LRC *text* parser ([`lyrics::parse_lrc_lines`]) and the chunked
//! LRCLIB proof-of-work nonce search ([`pow::solve_pow_chunk`]). This crate contains no audio,
//! no playback, no decoder, no Android API and no global state, and it starts no threads — the
//! caller keeps its own concurrency and its own cancellation. That is pure computation, so the
//! same code can be reused by the desktop and iOS targets.
//!
//! Safety: this crate contains **no `unsafe` code**. The only unsafe surface is inside
//! `uniffi`, which owns the FFI boundary and converts panics into Kotlin exceptions.

pub mod lyrics;
pub mod pow;

uniffi::setup_scaffolding!();

/// Health check used by the Kotlin bridge for diagnostics and by the load-failure path.
///
/// Returns the crate version. Intentionally trivial: it exists so the app can prove the
/// native library loaded and is callable before trusting it with lyrics.
#[uniffi::export]
pub fn core_version() -> String {
    env!("CARGO_PKG_VERSION").to_string()
}
