//! Earix Rust core.
//!
//! Phase 1 scope is deliberately narrow: the line-synced LRC *text* parser only
//! ([`lyrics::parse_lrc_lines`]). This crate contains no audio, no playback, no decoder,
//! no Android API and no global state. It is pure string processing so that the same code
//! can later be reused by the desktop and iOS targets.
//!
//! Safety: this crate contains **no `unsafe` code**. The only unsafe surface is inside
//! `uniffi`, which owns the FFI boundary and converts panics into Kotlin exceptions.

pub mod lyrics;

uniffi::setup_scaffolding!();

/// Health check used by the Kotlin bridge for diagnostics and by the load-failure path.
///
/// Returns the crate version. Intentionally trivial: it exists so the app can prove the
/// native library loaded and is callable before trusting it with lyrics.
#[uniffi::export]
pub fn core_version() -> String {
    env!("CARGO_PKG_VERSION").to_string()
}
