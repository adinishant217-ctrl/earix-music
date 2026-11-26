//! LRCLIB proof-of-work nonce search.
//!
//! This is a behaviour-for-behaviour port of the inner loop of Kotlin's
//! `LRCLIBPublisher.solvePowFast`, which hashes `prefix + nonceAscii` with SHA-256 and accepts the
//! first nonce whose digest is numerically below the challenge's `target` hex prefix.
//!
//! What it deliberately does NOT do is own the thread pool. Kotlin already fans the search out over
//! `Dispatchers.Default` workers that each take a strided slice of the nonce space, and it already
//! owns cancellation through `ensureActive()`. Reproducing that here would mean either spawning
//! native threads (uncontrolled, and impossible to cancel from Kotlin) or linking a scheduler for
//! one call site. Instead the API is *chunked*: one call tests at most `max_attempts` nonces for a
//! given stride phase and returns either the winning nonce or "keep going". The caller loop stays
//! in Kotlin, so a cancelled publish stops the search at the next chunk boundary — the same place
//! it stops today, only coarser than every 1,000 attempts.
//!
//! Per-attempt work is allocation-free: the nonce is formatted into a stack buffer, the digest is
//! written into a stack buffer, and the target is decoded to 32 bytes ONCE per chunk (Kotlin
//! re-padded and re-decoded that target string on every single attempt).
//!
//! No `unsafe` code.

use sha2::{Digest, Sha256};

/// Upper bound for the nonce search, matching Kotlin's `MAX_NONCE`.
pub const POW_MAX_NONCE: i64 = 500_000_000;

/// Failure modes that send the caller back to the Kotlin implementation.
#[derive(Debug, Clone, PartialEq, Eq, thiserror::Error, uniffi::Error)]
pub enum PowError {
    /// The challenge target is not a hex string, so no comparison is meaningful.
    #[error("challenge target is not hexadecimal")]
    BadTarget,

    /// A zero or negative stride would test the same nonce forever.
    #[error("stride must be positive")]
    BadStride,

    /// A negative start would produce a nonce the server can never have issued.
    #[error("start nonce must not be negative")]
    BadStart,
}

/// Result of one chunk of the search.
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Record)]
pub struct PowChunk {
    /// The winning nonce, or `None` when this chunk found no match.
    pub nonce: Option<i64>,
    /// How many nonces this chunk hashed. Always `<= max_attempts`.
    pub attempts: i64,
}

/// Proves the native SHA-256 path is producing the expected bytes before it is trusted.
///
/// Kotlin already self-tests its own hasher (`SHA256("hello")`) because a platform provider can
/// return the wrong algorithm or fail to load. The Rust core gets the same test for the same
/// reason: a `false` here makes the bridge stay on the Kotlin loop, so a broken native build
/// degrades to the previous behaviour instead of computing nonces nobody can verify.
#[uniffi::export]
pub fn pow_self_test() -> bool {
    let digest = Sha256::digest(b"hello");
    let mut out = [0u8; 32];
    out.copy_from_slice(&digest);
    let mut hex = String::with_capacity(64);
    for byte in out {
        hex.push(char::from_digit(u32::from(byte >> 4), 16).unwrap_or('?'));
        hex.push(char::from_digit(u32::from(byte & 0x0F), 16).unwrap_or('?'));
    }
    hex == "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824"
}

/// Test up to `max_attempts` nonces in `start, start + stride, start + 2 * stride, ...`.
///
/// The search stops at [`POW_MAX_NONCE`] exactly as the Kotlin loop does, so a chunk that runs off
/// the end of the nonce space returns `nonce: None` with fewer attempts than requested.
#[uniffi::export]
pub fn solve_pow_chunk(
    prefix: String,
    target: String,
    start_nonce: i64,
    stride: i64,
    max_attempts: i64,
) -> Result<PowChunk, PowError> {
    solve_pow_chunk_bytes(prefix.as_bytes(), &target, start_nonce, stride, max_attempts)
}

/// Borrowing core, used by the tests and the benchmark binary.
pub fn solve_pow_chunk_bytes(
    prefix: &[u8],
    target: &str,
    start_nonce: i64,
    stride: i64,
    max_attempts: i64,
) -> Result<PowChunk, PowError> {
    if stride <= 0 {
        return Err(PowError::BadStride);
    }
    if start_nonce < 0 {
        return Err(PowError::BadStart);
    }
    let target_bytes = parse_target(target)?;

    let mut hasher = Sha256::new();
    let mut nonce_digits = [0u8; 20];
    let mut digest = [0u8; 32];
    let mut nonce = start_nonce;
    let mut attempts = 0i64;

    while nonce < POW_MAX_NONCE && attempts < max_attempts {
        let len = write_decimal(nonce, &mut nonce_digits);

        // Same bytes Kotlin hashes: the prefix, then the ASCII decimal nonce.
        hasher.update(prefix);
        hasher.update(&nonce_digits[..len]);
        digest.copy_from_slice(&hasher.finalize_reset());

        attempts += 1;
        if digest_less_than(&digest, &target_bytes) {
            return Ok(PowChunk {
                nonce: Some(nonce),
                attempts,
            });
        }

        nonce += stride;
    }

    Ok(PowChunk {
        nonce: None,
        attempts,
    })
}

/// Decimal ASCII of `value` written into the front of `buf`. Returns the length.
///
/// Mirrors Kotlin's `writeLongAscii`, including the `0` special case.
fn write_decimal(value: i64, buf: &mut [u8; 20]) -> usize {
    if value == 0 {
        buf[0] = b'0';
        return 1;
    }
    // `value` is always >= 0 here: negative starts are rejected above.
    let mut n = value as u64;
    let mut len = 0usize;
    while n > 0 {
        buf[len] = b'0' + (n % 10) as u8;
        n /= 10;
        len += 1;
    }
    buf[..len].reverse();
    len
}

/// Decode the challenge target into 32 bytes, padding a short target with `0xF` nibbles.
///
/// Kotlin does `targetHex.padEnd(64, 'F')` and compares nibble by nibble, so `"00"` means
/// "first byte < 0x00, i.e. nothing", and `"0"` means "first byte < 0x0F". Anything beyond 64 hex
/// characters is ignored rather than rejected, which is what indexing the padded string did.
fn parse_target(target: &str) -> Result<[u8; 32], PowError> {
    let mut out = [0xFFu8; 32];
    let bytes = target.as_bytes();
    if bytes.is_empty() {
        return Err(PowError::BadTarget);
    }
    let n = bytes.len().min(64);
    for (i, &c) in bytes[..n].iter().enumerate() {
        let v = hex_value(c).ok_or(PowError::BadTarget)?;
        let slot = &mut out[i / 2];
        *slot = if i % 2 == 0 {
            (*slot & 0x0F) | (v << 4)
        } else {
            (*slot & 0xF0) | v
        };
    }
    Ok(out)
}

fn hex_value(c: u8) -> Option<u8> {
    match c {
        b'0'..=b'9' => Some(c - b'0'),
        b'a'..=b'f' => Some(c - b'a' + 10),
        b'A'..=b'F' => Some(c - b'A' + 10),
        _ => None,
    }
}

/// `digest < target`, big-endian byte order — the same numeric comparison Kotlin performs.
///
/// Fully equal digests are NOT accepted, matching `hashLessThanTarget`'s trailing `return false`.
fn digest_less_than(digest: &[u8; 32], target: &[u8; 32]) -> bool {
    for i in 0..32 {
        if digest[i] != target[i] {
            return digest[i] < target[i];
        }
    }
    false
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn self_test_matches_the_kotlin_gate() {
        assert!(pow_self_test());
    }

    #[test]
    fn writes_decimal_like_kotlin() {
        let mut buf = [0u8; 20];
        assert_eq!(write_decimal(0, &mut buf), 1);
        assert_eq!(&buf[..1], b"0");
        assert_eq!(write_decimal(7, &mut buf), 1);
        assert_eq!(&buf[..1], b"7");
        assert_eq!(write_decimal(10, &mut buf), 2);
        assert_eq!(&buf[..2], b"10");
        assert_eq!(write_decimal(499_999_998, &mut buf), 9);
        assert_eq!(&buf[..9], b"499999998");
        assert_eq!(write_decimal(i64::MAX, &mut buf), 19);
        assert_eq!(&buf[..19], b"9223372036854775807");
    }

    #[test]
    fn decodes_target_with_ff_padding() {
        let t = parse_target("00").unwrap();
        assert_eq!(t[0], 0x00);
        assert_eq!(t[1], 0xFF);

        let t = parse_target("0").unwrap();
        assert_eq!(t[0], 0x0F);
        assert_eq!(t[1], 0xFF);

        let t = parse_target("ab").unwrap();
        assert_eq!(t[0], 0xAB);

        let t = parse_target("AB").unwrap();
        assert_eq!(t[0], 0xAB);

        // Beyond 64 characters the tail is ignored, not rejected.
        let long = "0".repeat(66);
        assert_eq!(parse_target(&long).unwrap()[31], 0x00);

        assert_eq!(parse_target(""), Err(PowError::BadTarget));
        assert_eq!(parse_target("zz"), Err(PowError::BadTarget));
        assert_eq!(parse_target("0 0"), Err(PowError::BadTarget));
    }

    #[test]
    fn rejects_impossible_chunks() {
        assert_eq!(
            solve_pow_chunk_bytes(b"p", "00", 0, 0, 10),
            Err(PowError::BadStride)
        );
        assert_eq!(
            solve_pow_chunk_bytes(b"p", "00", 0, -1, 10),
            Err(PowError::BadStride)
        );
        assert_eq!(
            solve_pow_chunk_bytes(b"p", "00", -1, 1, 10),
            Err(PowError::BadStart)
        );
        assert_eq!(
            solve_pow_chunk_bytes(b"p", "zz", 0, 1, 10),
            Err(PowError::BadTarget)
        );
    }

    #[test]
    fn finds_the_lowest_nonce_in_a_chunk() {
        // Whatever this challenge is, the chunk must agree with a hand-rolled reference scan.
        let prefix = b"challenge-blob";
        let target = "00f";
        let mut expected = None;
        let mut h = Sha256::new();
        for nonce in 0..2_000i64 {
            h.update(prefix);
            h.update(nonce.to_string().as_bytes());
            let d: [u8; 32] = h.finalize_reset().into();
            if digest_less_than(&d, &parse_target(target).unwrap()) {
                expected = Some(nonce);
                break;
            }
        }
        let got = solve_pow_chunk_bytes(prefix, target, 0, 1, 2_000).unwrap();
        assert_eq!(got.nonce, expected);
        match expected {
            Some(n) => assert_eq!(got.attempts, n + 1),
            None => assert_eq!(got.attempts, 2_000),
        }
    }

    #[test]
    fn stride_slices_reproduce_the_sequential_answer() {
        // Splitting the space across 4 stride phases must agree with one sequential scan: the
        // minimum over the phases cannot be lower than the sequential answer (it scanned every
        // nonce below it) and cannot be higher (the phase owning it finds it too).
        let target = "0ff";
        let sequential = solve_pow_chunk_bytes(b"abc", target, 0, 1, 5_000).unwrap();
        assert!(sequential.nonce.is_some(), "challenge must solve in range");

        let mut best: Option<i64> = None;
        for phase in 0..4i64 {
            if let Some(n) =
                solve_pow_chunk_bytes(b"abc", target, phase, 4, 5_000).unwrap().nonce
            {
                best = Some(best.map_or(n, |b| b.min(n)));
            }
        }
        assert_eq!(best, sequential.nonce);
    }

    #[test]
    fn stops_at_the_nonce_ceiling() {
        let chunk = solve_pow_chunk_bytes(b"p", "00", POW_MAX_NONCE - 2, 1, 100).unwrap();
        assert_eq!(chunk.nonce, None);
        assert_eq!(chunk.attempts, 2);
    }

    #[test]
    fn zero_attempts_is_a_no_op() {
        let chunk = solve_pow_chunk_bytes(b"p", "00", 0, 1, 0).unwrap();
        assert_eq!(chunk.nonce, None);
        assert_eq!(chunk.attempts, 0);
    }
}
