#!/usr/bin/env bash
#
# Reproducible Kotlin <-> Rust LRC parity and benchmark harness.
#
# This is the script that produced the numbers quoted in
# `core/service/lyricsService/.../LrcTextParser.kt` (LRC_RUST_MIN_CHARS) and in the Phase 1
# report. It runs the REAL Kotlin parser (extracted from the repository by
# `lrc_parity_harness.py`) against the REAL Rust parser, through the SAME UniFFI bindings the
# Android app uses, on the host machine.
#
# Requires:
#   - cargo / rustc                  (https://rustup.rs)
#   - python3
#   - a JDK 21 on PATH or JAVA_HOME  (only to run kotlinc and the harness)
#   - kotlinc                        (https://github.com/JetBrains/kotlin/releases)
#   - JNA jar                        (net.java.dev.jna:jna:5.19.1, Maven Central)
#
# Usage:
#   JNA_JAR=/path/to/jna-5.19.1.jar scripts/rust/verify-lrc-parity.sh
#
# Optional overrides: KOTLINC, JAVA_BIN, ITERS_LARGE, ITERS_TYPICAL.
#
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

KOTLINC="${KOTLINC:-kotlinc}"
JAVA_BIN="${JAVA_BIN:-java}"
JNA_JAR="${JNA_JAR:-}"
ITERS_LARGE="${ITERS_LARGE:-1000}"
ITERS_TYPICAL="${ITERS_TYPICAL:-20000}"

if [[ -z "$JNA_JAR" || ! -f "$JNA_JAR" ]]; then
    echo "error: set JNA_JAR to the jna-5.19.1.jar from Maven Central" >&2
    exit 2
fi
for tool in cargo python3 "$KOTLINC" "$JAVA_BIN"; do
    command -v "$tool" >/dev/null 2>&1 || {
        echo "error: '$tool' not found on PATH (see the header of this script)" >&2
        exit 2
    }
done

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

echo "==> building the Rust core (host release)"
cargo build --release --manifest-path rust/Cargo.toml

echo "==> generating corpora"
cargo run -q --release --manifest-path rust/Cargo.toml --bin lrc-parity -- gen 5000 "$WORK/corpus.lrc"
cat > "$WORK/typical.lrc" <<'LRC'
[00:00.00] Artist: Hikaru Utada
[00:12.45] 光の向こうへ
[00:18.20]きらきら 舞い上がれ 🎵
[00:24.00]   
[00:31.75]   and the world keeps turning  
[00:37.10]&amp; we don't stop
[00:44.99]same time, same line
[00:51.01]another line here
[00:58.30]and one more
[01:05.10]closing line
LRC

echo "==> generating the Kotlin oracle from the repository sources"
python3 scripts/rust/lrc_parity_harness.py > "$WORK/Harness.kt"

echo "==> compiling (kotlinc)"
"$KOTLINC" -nowarn -include-runtime -cp "$JNA_JAR" \
    core/rustBridge/src/main/java/com/maxrave/earixrust/earix_core.kt \
    "$WORK/Harness.kt" \
    -d "$WORK/harness.jar"

RUN=("$JAVA_BIN" "-Djna.library.path=$ROOT/rust/target/release" -cp "$WORK/harness.jar:$JNA_JAR" harness.HarnessKt)

echo
echo "===== equivalence (Kotlin oracle vs Rust, same UniFFI bindings) ====="
"${RUN[@]}" equiv "$WORK/corpus.lrc"

echo
echo "===== benchmark: typical lyrics file ====="
"${RUN[@]}" bench "$WORK/typical.lrc" "$ITERS_TYPICAL"

echo
echo "===== benchmark: large document ====="
"${RUN[@]}" bench "$WORK/corpus.lrc" "$ITERS_LARGE"
