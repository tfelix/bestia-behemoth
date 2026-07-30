package net.bestia.worldgen.pipeline

import java.util.Locale

/**
 * Command-line entry point for the regression sweep: `./gradlew :worldgen:invariants -Pseeds=200`.
 *
 * Prints one line per seed while it works, because a sweep of a few hundred worlds takes minutes and a
 * harness that prints nothing until the end is a harness nobody leaves running. Exits non-zero on any
 * violation so it can be wired into CI without a wrapper.
 */
object InvariantsMain {

  @JvmStatic
  fun main(args: Array<String>) {
    val seeds = args.valueOf("--seeds")?.toInt() ?: 25
    val cells = args.valueOf("--cells")?.toInt() ?: 192
    val firstSeed = args.valueOf("--first-seed")?.toLong() ?: 1L

    println("checking $seeds worlds of ${cells}x$cells cells from seed $firstSeed")
    val startedAt = System.currentTimeMillis()

    // What the run is for, beyond pass/fail. The invariant on land fraction is deliberately loose - it only
    // catches a world that is all sea or all rock - so it says nothing about whether a tuning change landed
    // where it was aimed. The spread across seeds does, and this is the only place it can be seen.
    val land = ArrayList<Double>(seeds)

    // Lake counts are here for a blunter reason: the answer used to be zero on every world and no output said
    // so. `checkIceCarvedWorldsHaveLakes` now fails a glaciated world with none, but a *count* is what shows
    // that a world has three where it ought to have thirty, which no pass/fail can.
    val lakes = ArrayList<Int>(seeds)

    val report = Invariants.sweep(
      seeds = seeds,
      firstSeed = firstSeed,
      config = { seed ->
        StandardWorld.demoConfig(seed).copy(widthCells = cells, heightCells = cells)
      },
      onSeed = { seed, single, generated ->
        val fraction = Invariants.landFraction(generated)
        land.add(fraction)

        val basins = Invariants.lakeCount(generated)
        lakes.add(basins)

        val measured = "land ${"%.3f".format(Locale.ROOT, fraction)}  lakes $basins"
        if (single.isClean) {
          println("  seed $seed ok    $measured")
        } else {
          println("  seed $seed FAILED $measured")
          single.violations.forEach { println("    $it") }
        }
      }
    )

    val seconds = (System.currentTimeMillis() - startedAt) / 1000.0
    if (land.isNotEmpty()) {
      val sorted = land.sorted()
      // Locale.ROOT: these are read straight against `targetLandFraction` in a source file, and a decimal
      // comma invites the reader to wonder whether it is the same kind of number.
      println(
        "land fraction: median ${"%.3f".format(Locale.ROOT, sorted[sorted.size / 2])}, " +
            "range ${"%.3f".format(Locale.ROOT, sorted.first())} .. " +
            "${"%.3f".format(Locale.ROOT, sorted.last())}"
      )
    }
    if (lakes.isNotEmpty()) {
      val sorted = lakes.sorted()
      val dry = sorted.count { it == 0 }
      println(
        "lakes: median ${sorted[sorted.size / 2]}, range ${sorted.first()} .. ${sorted.last()}" +
            ", $dry of ${sorted.size} worlds with none"
      )
    }
    // Locale.ROOT here too, for the same reason the fractions above have it - and because this line was
    // printing "31,6 s" three lines below one that had been careful about exactly that.
    println("$report in ${"%.1f".format(Locale.ROOT, seconds)} s")

    if (!report.isClean) {
      // Non-zero so CI notices. The detail is already on stdout, seed by seed.
      System.exit(1)
    }
  }

  private fun Array<String>.valueOf(flag: String): String? {
    val i = indexOf(flag)
    return if (i >= 0 && i + 1 < size) this[i + 1] else null
  }
}
