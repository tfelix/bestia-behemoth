package net.bestia.worldgen.pipeline

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

    val report = Invariants.sweep(
      seeds = seeds,
      firstSeed = firstSeed,
      config = { seed ->
        StandardWorld.demoConfig(seed).copy(widthCells = cells, heightCells = cells)
      },
      onSeed = { seed, single ->
        if (single.isClean) {
          println("  seed $seed ok")
        } else {
          println("  seed $seed FAILED")
          single.violations.forEach { println("    $it") }
        }
      }
    )

    val seconds = (System.currentTimeMillis() - startedAt) / 1000.0
    println("$report in ${"%.1f".format(seconds)} s")

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
