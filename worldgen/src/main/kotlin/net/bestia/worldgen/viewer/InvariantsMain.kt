package net.bestia.worldgen.viewer

import net.bestia.worldgen.core.Timings
import net.bestia.worldgen.pipeline.Invariants
import net.bestia.worldgen.pipeline.StandardWorld
import java.util.Locale

/**
 * Command-line entry point for the regression sweep: `./gradlew :worldgen:invariants -Pseeds=200`.
 *
 * Prints one line per seed while it works, because a sweep of a few hundred worlds takes minutes and a
 * harness that prints nothing until the end is a harness nobody leaves running. Exits non-zero on any
 * violation so it can be wired into CI without a wrapper.
 *
 * ### Why this lives in `viewer/` while the checks stay in `pipeline/`
 *
 * The sweep is the tool that most needs pointing at a candidate params file: a tuning change is *judged* by
 * what it does to the land-fraction and lake distributions over two hundred worlds, and this is the only place
 * those distributions are printed. Reading a file means `java.io`, which `checkBoundaries` permits in this
 * package alone. `Invariants` itself has no I/O in it and stays where the stages are.
 *
 * Moving it also retired a hand-rolled argument parser. It read `--seeds`, `--cells` and `--first-seed` with a
 * three-line `indexOf` and **silently ignored everything else**, so `-Pgenesis` was accepted and discarded and
 * the sweep answered about the demo world while appearing to answer about the server's. That is the exact
 * failure [WorldArgs] exists to prevent, and the sweep was the one tool still outside it.
 */
object InvariantsMain {

  /** The sweep's own flags: how many worlds and from where, neither of which says what a world *is*. */
  private const val SEEDS = "--seeds"
  private const val FIRST_SEED = "--first-seed"

  /** Default edge of a sweep world, in coarse cells: a few hundred in a minute, and wide enough for a rain shadow. */
  private const val SWEEP_CELLS = 192

  @JvmStatic
  fun main(args: Array<String>) {
    val cli = WorldArgs(args.toList(), extraFlags = setOf(SEEDS, FIRST_SEED))

    // The sweep iterates seeds, so `--seed` cannot mean what it means everywhere else - and the first attempt
    // here refused it outright, which broke `-Pgenesis`: those settings forward the server's seed along with
    // its extent and wrapping, so refusing the seed refused the whole combination this class was moved in
    // order to support. Taking it as the *first* seed keeps every flag effective and reads correctly - "the
    // server's world, and the two hundred after it". Both together is a contradiction, so it is rejected.
    require(!(cli.has(WorldArgs.SEED) && cli.has(FIRST_SEED))) {
      "${WorldArgs.SEED} is the sweep's first seed, so passing it with $FIRST_SEED is a contradiction - pick one"
    }

    val seeds = cli.int(SEEDS) ?: 25
    val firstSeed = cli.long(FIRST_SEED) ?: cli.long(WorldArgs.SEED) ?: 1L
    val base = cli.worldConfig(
      StandardWorld.demoConfig().copy(widthCells = SWEEP_CELLS, heightCells = SWEEP_CELLS)
    )
    val tuning = cli.tuning()

    println("checking $seeds worlds of ${base.widthCells}x${base.heightCells} cells from seed $firstSeed")
    println("  ${tuning.summary()}")
    val startedAt = System.currentTimeMillis()

    // What the run is for, beyond pass/fail. The invariant on land fraction is deliberately loose - it only
    // catches a world that is all sea or all rock - so it says nothing about whether a tuning change landed
    // where it was aimed. The spread across seeds does, and this is the only place it can be seen.
    val land = ArrayList<Double>(seeds)

    // Lake counts are here for a blunter reason: the answer used to be zero on every world and no output said
    // so. `checkTheWorldHasStandingWater` now fails a world with none, but a *count* is what shows that a world
    // has three where it ought to have thirty, which no pass/fail can.
    val lakes = ArrayList<Int>(seeds)

    val report = Invariants.sweep(
      seeds = seeds,
      firstSeed = firstSeed,
      config = { seed -> base.copy(seed = seed) },
      params = tuning.params,
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

    // The sweep builds nothing but worlds and writes no images, which makes it the cheapest honest way to
    // time the generator: `./gradlew :worldgen:invariants -Ptimings -Pseeds=1 -Pcells=512`.
    Timings.printAndReset()

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
}
