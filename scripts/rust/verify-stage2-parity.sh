#!/usr/bin/env bash
#
# Reproducible Kotlin <-> Rust harness for the LRCLIB proof-of-work search (Stage 2).
#
# Runs the REAL Kotlin loop (`writeLongAscii` + `powHash` + `hashLessThanTarget`, extracted from
# the repository by `stage2_harness.py`) against the REAL Rust implementation, both in-process and
# through the SAME UniFFI/JNA bindings the Android app uses. The difference between those two Rust
# columns is the FFI cost, measured rather than assumed.
#
# Requires:
#   - cargo / rustc                  (https://rustup.rs)
#   - python3
#   - a JDK 21 on PATH or JAVA_HOME
#   - kotlinc                        (https://github.com/JetBrains/kotlin/releases)
#   - JNA jar                        (net.java.dev.jna:jna:5.19.1, Maven Central)
#
# Usage:
#   JNA_JAR=/path/to/jna-5.19.1.jar scripts/rust/verify-stage2-parity.sh
#
# Optional overrides: KOTLINC, JAVA_BIN, ATTEMPTS, RUNS, THREADS.
#
# NOTE ON WHAT THIS MEASURES: the host is x86_64 and the JVM is HotSpot, not ART on arm64. Both
# sides use hardware SHA-256 here (SHA-NI; the sha2 crate detects it at runtime), so the RATIO is
# indicative rather than exact. Re-run on a device before changing any production gate.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

KOTLINC="${KOTLINC:-kotlinc}"
JAVA_BIN="${JAVA_BIN:-java}"
JNA_JAR="${JNA_JAR:-}"
ATTEMPTS="${ATTEMPTS:-2000000}"
RUNS="${RUNS:-5}"
THREADS="${THREADS:-8}"

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

echo "==> building the Rust core (release, and debug for the binding metadata)"
cargo build --release --package earix-core --lib --manifest-path rust/Cargo.toml
cargo build --package earix-core --lib --manifest-path rust/Cargo.toml
cargo build --release --package earix-core --bins --manifest-path rust/Cargo.toml

echo "==> regenerating the UniFFI bindings from the built library"
# Run from `rust/`: the generator shells out to `cargo metadata` in its own working directory.
(
    cd rust
    cargo run -q --package earix-core --features uniffi-bindgen --bin uniffi-bindgen -- \
        generate --library target/debug/libearix_core.so --language kotlin \
        --out-dir "$WORK/gen" --config earix-core/uniffi.toml --no-format
)

echo "==> generating the Kotlin harness from the repository sources"
python3 scripts/rust/stage2_harness.py "$WORK"

echo "==> compiling (kotlinc)"
"$KOTLINC" -nowarn -include-runtime -cp "$JNA_JAR" \
    "$WORK/gen/com/maxrave/earixrust/earix_core.kt" \
    "$WORK/LoggerStub.kt" \
    "$WORK/Stage2Harness.kt" \
    -d "$WORK/harness.jar"

RUN=("$JAVA_BIN" "-Djna.library.path=$ROOT/rust/target/release" -cp "$WORK/harness.jar:$JNA_JAR" harness.Stage2HarnessKt)

echo
echo "===== equivalence: Kotlin loop vs Rust, same UniFFI bindings ====="
"${RUN[@]}" parity "lrclib-challenge-prefix-2f9a1c"

echo
echo "===== single-thread throughput, ${ATTEMPTS} attempts, ${RUNS} runs ====="
"${RUN[@]}" chunk "lrclib-challenge-prefix-2f9a1c" "0000000000000000" "$ATTEMPTS" 1 "$RUNS"

echo
echo "===== Rust in-process (no JVM, no FFI) ====="
rust/target/release/stage2-bench pow "lrclib-challenge-prefix-2f9a1c" "0000000000000000" "$ATTEMPTS" 1

echo
# LRCLIB's real difficulty is about 17M attempts on average (6 leading zero nibbles =
# 1 in 16^6), which is why 6 zeros is used here: it is the only shape where thread start-up
# cost stops dominating the comparison. Chunk size matches POW_RUST_CHUNK_ATTEMPTS.
echo "===== full parallel solve, ${THREADS} threads, realistic difficulty (~17M attempts) ====="
echo "--- Kotlin (Dispatchers.Default workers today) ---"
"${RUN[@]}" threads kotlin "lrclib-challenge-prefix-2f9a1c" "000000" 1 "$THREADS" 100000
echo "--- Rust chunked ---"
"${RUN[@]}" threads rust "lrclib-challenge-prefix-2f9a1c" "000000" 1 "$THREADS" 100000
echo "--- Rust in-process ---"
rust/target/release/stage2-bench powthreads "lrclib-challenge-prefix-2f9a1c" "000000" 1 "$THREADS"

# ---------------------------------------------------------------------------------------------
# Text workloads. These are the candidates that were RETAINED in Kotlin (see the Stage 2 report);
# they are measured so that decision is reproducible rather than asserted.
# ---------------------------------------------------------------------------------------------
ROMANIZATION=core/service/lyricsService/src/commonMain/kotlin/org/simpmusic/lyrics/romanization

echo
echo "===== romanization + AutoEq: Kotlin cost per line / per parse ====="
cat > "$WORK/lines.txt" <<'LINES'
밤하늘에 별이 빛나고 있어요
그대의 목소리가 들려오네
사랑은 아직도 내 안에 있어
日本語のテスト用の一行です
きらきら光る星を数えましょう
もう一度だけ君に会いたい
एक समुद्र सा है जीवन
सपने देखते रहना चाहिए
हम सब मिलकर गाते हैं
Дмитриев пел эту песню вчера
Солнце садится за горизонт
Я помню чудное мгновенье
LINES
for i in $(seq 1 40); do cat "$WORK/lines.txt"; done > "$WORK/lines_many.txt"

"$KOTLINC" -nowarn -include-runtime \
    core/domain/src/commonMain/kotlin/com/maxrave/domain/data/model/lyrics/RomanizationLanguage.kt \
    "$ROMANIZATION/RomanizationLanguage.kt" \
    "$ROMANIZATION/HangulRomanizer.kt" \
    "$ROMANIZATION/KanaRomanizer.kt" \
    "$ROMANIZATION/IndicRomanizer.kt" \
    "$ROMANIZATION/CyrillicRomanizer.kt" \
    core/service/autoEqService/src/commonMain/kotlin/org/simpmusic/autoeq/AutoEqModels.kt \
    core/service/autoEqService/src/commonMain/kotlin/org/simpmusic/autoeq/AutoEqParser.kt \
    scripts/rust/TextHarness.kt \
    -d "$WORK/text.jar"

TEXT_RUN=("$JAVA_BIN" -cp "$WORK/text.jar" textharness.TextHarnessKt)
"${TEXT_RUN[@]}" romanize "$WORK/lines_many.txt" 50

# AutoEq's real 851 kB index, if it is around. Download it with:
#   curl -sSfL https://raw.githubusercontent.com/jaakkopasanen/AutoEq/master/results/INDEX.md \
#     -o /tmp/INDEX.md
if [[ -n "${AUTOEQ_INDEX:-}" && -f "${AUTOEQ_INDEX}" ]]; then
    "${TEXT_RUN[@]}" autoeq "$AUTOEQ_INDEX" 20
else
    echo "autoeq: skipped (set AUTOEQ_INDEX=/path/to/INDEX.md to measure it)"
fi
