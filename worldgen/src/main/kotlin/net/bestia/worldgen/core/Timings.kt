package net.bestia.worldgen.core

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Opt-in timing for the parts of a stage, which [StageListener] cannot see.
 *
 * The pipeline already times each stage as a whole ([WorldGenPipeline.execute]), and that is enough to
 * know *which* stage to look at but not enough to know what to do about it. Erosion is one call that
 * spends its time across five sub-steps with completely different characters - a global priority queue,
 * two ordered stack walks and two grid sweeps - and optimising it without knowing the split between them
 * is guesswork. This is the missing half of that instrumentation.
 *
 * Off unless `-Dworldgen.timings=true`, and when off [measure] is an inlined call to its own body: no
 * clock read, no map lookup, nothing for the JIT to keep. So call sites can be left in place rather than
 * added and removed around each investigation, which is the only way the numbers stay available.
 *
 * Totals are per name and cumulative, because the interesting sub-steps run in a loop forty-five times.
 * Thread-safe, since the whole point of the exercise is to compare a serial run against a parallel one.
 */
object Timings {

  val enabled: Boolean = System.getProperty("worldgen.timings")?.toBoolean() ?: false

  private val totals = ConcurrentHashMap<String, AtomicLong>()
  private val counts = ConcurrentHashMap<String, AtomicLong>()

  /** Accumulate the wall time of [body] under [name]. Free when disabled. */
  inline fun <T> measure(name: String, body: () -> T): T {
    if (!enabled) return body()

    val started = System.nanoTime()
    try {
      return body()
    } finally {
      record(name, System.nanoTime() - started)
    }
  }

  /** Public because [measure] is inline and its callers need it; not meant to be called directly. */
  fun record(name: String, nanos: Long) {
    totals.computeIfAbsent(name) { AtomicLong() }.addAndGet(nanos)
    counts.computeIfAbsent(name) { AtomicLong() }.incrementAndGet()
  }

  fun reset() {
    totals.clear()
    counts.clear()
  }

  /** Cumulative nanoseconds per name, for a caller that wants to compare two phases of its own run. */
  fun snapshot(): Map<String, Long> = totals.mapValues { it.value.get() }

  /** [snapshot] then [reset], which is what a benchmark alternating two modes actually wants. */
  fun drain(): Map<String, Long> {
    val taken = snapshot()
    reset()
    return taken
  }

  /**
   * Every measured name, slowest first.
   *
   * Sorted by total rather than by name: the reason to print this is to find the top of the list, and a
   * list ordered by something else makes the reader do the sorting.
   */
  fun report(): String {
    if (totals.isEmpty()) return "no timings recorded"

    val rows = totals.entries
      .map { Triple(it.key, it.value.get(), counts[it.key]?.get() ?: 0L) }
      .sortedByDescending { it.second }

    val width = rows.maxOf { it.first.length }
    val total = rows.sumOf { it.second }

    return buildString {
      appendLine("timings (cumulative, nested entries double-count their parents):")
      for ((name, nanos, calls) in rows) {
        val ms = nanos / 1_000_000.0
        appendLine(
          "  %-${width}s %9.1f ms  %6d calls  %7.3f ms/call"
            .format(java.util.Locale.ROOT, name, ms, calls, ms / calls)
        )
      }
      append("  (sum of all entries %.1f ms)".format(java.util.Locale.ROOT, total / 1_000_000.0))
    }
  }

  /** Prints [report] and clears, so a caller can time several worlds without them running together. */
  fun printAndReset() {
    if (!enabled) return
    println(report())
    reset()
  }
}
