// Benchmark driver for the Stage 2 text workloads (NOT extracted code — it calls the shipped
// functions directly, so there is nothing to drift).
//
// Compiled against the repository's own sources by scripts/rust/verify-stage2-parity.sh:
//   * the four rule-based romanizers (Hangul, kana, Indic, Cyrillic)
//   * AutoEq's INDEX.md parser
//
// The point of measuring these is to compare each one against the MEASURED cost of one UniFFI/JNA
// crossing (~46-108 us on the JVM). Anything that runs per line cannot pay that toll; anything that
// runs once over a large document can, but only if the parse itself is big enough to matter.
package textharness

import com.maxrave.domain.data.model.lyrics.RomanizationLanguage
import org.simpmusic.autoeq.parseAutoEqIndex
import org.simpmusic.lyrics.romanization.CyrillicRomanizer
import org.simpmusic.lyrics.romanization.HangulRomanizer
import org.simpmusic.lyrics.romanization.IndicRomanizer
import org.simpmusic.lyrics.romanization.KanaRomanizer
import java.nio.file.Files
import java.nio.file.Path

private fun median(values: List<Long>): Double {
    val sorted = values.sorted()
    return sorted[sorted.size / 2].toDouble()
}

/** Runs [body] once per line, [runs] times; returns per-line nanoseconds for each run. */
private fun perLine(
    label: String,
    lines: List<String>,
    runs: Int,
    body: (String) -> String,
) {
    var sink = 0
    repeat(3) { lines.forEach { sink += body(it).length } }
    val samples = ArrayList<Long>(runs)
    for (r in 0 until runs) {
        val t0 = System.nanoTime()
        for (line in lines) sink += body(line).length
        samples.add((System.nanoTime() - t0) / lines.size)
    }
    println(label + "_lines=" + lines.size)
    println(label + "_median_ns_per_line=" + "%.0f".format(median(samples)))
    println(label + "_min_ns_per_line=" + samples.min())
    println(label + "_sink=" + sink)
}

fun main(args: Array<String>) {
    when (args.getOrNull(0)) {
        "romanize" -> {
            val lines = Files.readAllLines(Path.of(args[1])).filter { it.isNotBlank() }
            val runs = args.getOrNull(2)?.toInt() ?: 50
            perLine("hangul", lines, runs) { HangulRomanizer.romanize(it) }
            perLine("kana", lines, runs) { KanaRomanizer.romanize(it) }
            perLine("devanagari", lines, runs) { IndicRomanizer.romanizeDevanagari(it) }
            perLine("cyrillic", lines, runs) { CyrillicRomanizer.romanize(it, RomanizationLanguage.RUSSIAN) }
        }

        "autoeq" -> {
            val text = Files.readString(Path.of(args[1]))
            val runs = args.getOrNull(2)?.toInt() ?: 20
            var entries = 0
            repeat(3) { entries = parseAutoEqIndex(text).size }
            val samples = ArrayList<Long>(runs)
            for (r in 0 until runs) {
                val t0 = System.nanoTime()
                entries = parseAutoEqIndex(text).size
                samples.add(System.nanoTime() - t0)
            }
            val med = median(samples)
            println("autoeq_bytes=" + text.length)
            println("autoeq_entries=" + entries)
            println("autoeq_median_ns=" + "%.0f".format(med))
            println("autoeq_min_ns=" + samples.min())
            println("autoeq_ns_per_entry=" + "%.1f".format(med / entries.coerceAtLeast(1)))
        }

        else -> error("usage: TextHarness romanize <lines> [runs] | autoeq <INDEX.md> [runs]")
    }
}
