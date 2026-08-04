package net.bestia.worldgen.viewer

import net.bestia.worldgen.bio.Biome
import net.bestia.worldgen.core.LayerId
import net.bestia.worldgen.core.Timings
import net.bestia.worldgen.spawn.VegetationStandChannels
import net.bestia.worldgen.vector.FeatureKind
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

  /**
   * Mean canopy over land below which a world has effectively no trees on it.
   *
   * Deliberately near zero. This is not a judgement about how wooded a world ought to be - a world of ice and
   * desert legitimately has almost nothing - it is the tripwire for a scatter that has stopped running.
   */
  private const val BARE = 0.01

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

    // Cave systems, for the third reason a count goes in this list: the *acceptance rule* is a filter on
    // terrain, so how many caves a world gets is a distribution nobody can predict from the parameters. A world
    // with none is legitimate - not all rock is limestone - and a sweep is the only place "how often is none"
    // can be answered.
    val caves = ArrayList<Int>(seeds)
    val hoards = ArrayList<Int>(seeds)

    // Canopy cover over land, for the reason that applies to nothing else on this list: vegetation has **no
    // stored output at all** beyond this raster. The trees are a function evaluated at chunk generation, so a
    // change that quietly stops it planting anything leaves a world that builds, passes, and is bare. This is
    // the only number anywhere that would move.
    val canopy = ArrayList<Double>(seeds)
    val stands = ArrayList<Int>()
    val standTrees = ArrayList<Double>()

    // The spatial index, for the reason none of the above applies: its cell size is derived from the union
    // of *every* feature's bbox, so it is a property of the whole world and no unit test over a handful of
    // features can say what it is. It decides which features land in the oversized list that `query`
    // appends to every query in the world, and it is the number `AreaFeature`'s extent cap is set from.
    // Vector ponds, for the reason the plan for them insists on: they are water the raster tier cannot
    // have, so a zero here is not "a dry world" but "the producer never fired", and no other number in
    // this sweep would move if it stopped firing entirely.
    val ponds = ArrayList<Int>(seeds)
    val oxbows = ArrayList<Int>(seeds)
    val lobes = ArrayList<Int>(seeds)
    val deltas = ArrayList<Int>(seeds)
    val districts = ArrayList<Int>(seeds)
    val corrupted = ArrayList<Double>(seeds)
    val dens = ArrayList<IntArray>(seeds)
    val manaLog = ArrayList<IntArray>(seeds)

    // Vents, pools, and the two volcanic biomes' share of the land. Four counts rather than one because they
    // fail independently and each failure looks like the others from outside: no vents is a broken emitter, vents
    // with no biome is a broken distance transform, and biome with no pool is only the strength gate being strict.
    val vents = ArrayList<Int>(seeds)
    val pools = ArrayList<Int>(seeds)
    val volcanic = ArrayList<Double>(seeds)

    val cellSizes = ArrayList<Double>(seeds)
    val oversized = ArrayList<Int>(seeds)
    val maxBuckets = ArrayList<Int>(seeds)
    val meanBuckets = ArrayList<Double>(seeds)

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

        val systems = generated.world.features.all().count { it.kind == FeatureKind.CAVE_SYSTEM }
        caves.add(systems)
        hoards.add(generated.world.features.all().count { it.kind == FeatureKind.CAVE_HOARD })

        val wooded = Invariants.meanOverLand(generated, LayerId.CANOPY_COVER)
        canopy.add(wooded)

        val standMarkers = generated.world.features.all()
          .filter { it.kind == FeatureKind.VEGETATION_STAND }
          .filterIsInstance<net.bestia.worldgen.vector.PointMarker>()
        stands.add(standMarkers.size)
        standTrees.add(
          if (standMarkers.isEmpty()) 0.0
          else standMarkers.sumOf { it.attribute(VegetationStandChannels.CAPACITY) } / standMarkers.size
        )

        ponds.add(generated.world.features.all().count { it.kind == FeatureKind.LAKE })
        oxbows.add(generated.world.features.all().count { it.kind == FeatureKind.OXBOW_LAKE })
        lobes.add(generated.world.features.all().count { it.kind == FeatureKind.ALLUVIAL_FAN })
        deltas.add(generated.world.features.all().count { it.kind == FeatureKind.DELTA })
        districts.add(generated.world.features.all().count { it.kind == FeatureKind.DISTRICT })
        vents.add(generated.world.features.all().count { it.kind == FeatureKind.VOLCANIC_VENT })
        pools.add(generated.world.features.all().count { it.kind == FeatureKind.LAVA_POOL })
        volcanic.add(Invariants.landShareOfBiomes(generated, Biome.VOLCANIC_FIELD, Biome.GEOTHERMAL_BASIN))

        // Counted per seed rather than only asserted, because the assertion is a tolerance around a target
        // and the failure worth seeing early is the distribution drifting inside it.
        corrupted.add(Invariants.corruptedLandShare(generated))
        // Four band counts, not a total: a total cannot tell a world that ramps from one that is all one
        // level, and those are a working world and a broken one.
        dens.add(Invariants.spawnerCensus(generated))
        // Five counts, and the reason they are counted rather than asserted is in `manaHistoryCensus`: each one
        // can legitimately be zero on one world and none of them may be zero across a sweep.
        manaLog.add(Invariants.manaHistoryCensus(generated))

        val index = generated.world.features.indexMetrics()
        cellSizes.add(index.cellSize)
        oversized.add(index.oversizedCount)
        maxBuckets.add(index.maxBucket)
        meanBuckets.add(index.meanBucket)

        val measured = "land ${"%.3f".format(Locale.ROOT, fraction)}  lakes $basins  caves $systems" +
            "  canopy ${"%.3f".format(Locale.ROOT, wooded)}  ponds ${ponds.last()}/${oxbows.last()}" +
            "  districts ${districts.last()}" +
            "  corrupt ${"%.3f".format(Locale.ROOT, corrupted.last())}" +
            "  mana ${manaLog.last().joinToString("/")}" +
            "  dens ${dens.last().joinToString("/")}" +
            "  index ${index.oversizedCount}/${index.size}"
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
    if (corrupted.isNotEmpty()) {
      val sorted = corrupted.sorted()
      val none = sorted.count { it <= 0.0 }
      println(
        "corrupted land: median ${"%.3f".format(Locale.ROOT, sorted[sorted.size / 2])}, " +
            "range ${"%.3f".format(Locale.ROOT, sorted.first())} .. " +
            "${"%.3f".format(Locale.ROOT, sorted.last())}" +
            ", $none of ${sorted.size} worlds with none"
      )
    }
    if (manaLog.isNotEmpty()) {
      val labels = listOf("wounds", "blights", "wards", "forsaken", "seers lost")
      val totals = IntArray(labels.size)
      val silent = IntArray(labels.size)
      for (counts in manaLog) {
        for (i in labels.indices) {
          totals[i] += counts[i]
          if (counts[i] == 0) silent[i]++
        }
      }
      // Per-world means and, beside each, how many worlds had none of it. The second number is the one that
      // matters: a mean of 0.4 over forty worlds is a rare event working, and a mean of 0 is a dead one.
      println(
        "mana history, per world: " + labels.indices.joinToString(", ") { i ->
          "${labels[i]} ${"%.1f".format(Locale.ROOT, totals[i].toDouble() / manaLog.size)}" +
              " (${silent[i]}/${manaLog.size} worlds with none)"
        }
      )
    }
    if (dens.isNotEmpty()) {
      val totals = IntArray(4)
      for (bands in dens) for (i in 0 until 4) totals[i] += bands[i]
      val all = totals.sum()
      val empty = dens.count { it.sum() == 0 }
      println(
        "spawners: ${all / dens.size} per world in bands 1-8/9-40/41-79/80-100 = " +
            totals.joinToString("/") { (it / dens.size).toString() } +
            ", $empty of ${dens.size} worlds with none"
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
    if (caves.isNotEmpty()) {
      val sorted = caves.sorted()
      val none = sorted.count { it == 0 }
      println(
        "caves: median ${sorted[sorted.size / 2]}, range ${sorted.first()} .. ${sorted.last()}" +
            ", $none of ${sorted.size} worlds with none, ${hoards.sum()} hoards in all"
      )
    }
    if (canopy.isNotEmpty()) {
      val sorted = canopy.sorted()
      val bare = sorted.count { it < BARE }
      println(
        "canopy over land: median ${"%.3f".format(Locale.ROOT, sorted[sorted.size / 2])}, " +
            "range ${"%.3f".format(Locale.ROOT, sorted.first())} .. " +
            "${"%.3f".format(Locale.ROOT, sorted.last())}, $bare of ${sorted.size} worlds all but bare"
      )
    }
    if (stands.isNotEmpty()) {
      val sorted = stands.sorted()
      val capacity = standTrees.filter { it > 0.0 }.sorted()
      println(
        "vegetation stands: median ${sorted[sorted.size / 2]}, range ${sorted.first()} .. ${sorted.last()}" +
            ", ${sorted.count { it == 0 }} of ${sorted.size} worlds with none" +
            (if (capacity.isEmpty()) "" else
              ", median ${"%.0f".format(Locale.ROOT, capacity[capacity.size / 2])} trees advertised each")
      )
    }
    if (vents.isNotEmpty()) {
      val sorted = volcanic.sorted()
      println(
        "volcanism: ${vents.sum() / vents.size} vents and ${pools.sum() / pools.size} lava pools per world, " +
            "${vents.count { it == 0 }} of ${vents.size} worlds with no vent, " +
            "${pools.count { it == 0 }} with no pool; volcanic land median " +
            "${"%.3f".format(Locale.ROOT, sorted[sorted.size / 2])}, range " +
            "${"%.3f".format(Locale.ROOT, sorted.first())} .. ${"%.3f".format(Locale.ROOT, sorted.last())}"
      )
    }
    if (ponds.isNotEmpty()) {
      val sorted = ponds.sorted()
      val none = sorted.count { it == 0 }
      println(
        "vector ponds: median ${sorted[sorted.size / 2]}, range ${sorted.first()} .. ${sorted.last()}" +
            ", $none of ${sorted.size} worlds with none, ${ponds.sum()} in all"
      )
      val quarters = districts.sorted()
      println(
        "town districts: median ${quarters[quarters.size / 2]}, range ${quarters.first()} .. " +
            "${quarters.last()}, ${quarters.count { it == 0 }} of ${quarters.size} worlds with none, " +
            "${districts.sum()} in all"
      )
      val bows = oxbows.sorted()
      println(
        "oxbow lakes: median ${bows[bows.size / 2]}, range ${bows.first()} .. ${bows.last()}" +
            ", ${bows.count { it == 0 }} of ${bows.size} worlds with none, ${oxbows.sum()} in all"
      )
      val wedges = lobes.sorted()
      val mouths = deltas.sorted()
      println(
        "deltas: median ${mouths[mouths.size / 2]}, range ${mouths.first()} .. ${mouths.last()}" +
            ", ${mouths.count { it == 0 }} of ${mouths.size} worlds with none, ${deltas.sum()} in all"
      )
      println(
        "alluvial fans: median ${wedges[wedges.size / 2]}, range ${wedges.first()} .. ${wedges.last()}" +
            ", ${wedges.count { it == 0 }} of ${wedges.size} worlds with none, ${lobes.sum()} in all"
      )
    }
    if (cellSizes.isNotEmpty()) {
      val cells = cellSizes.sorted()
      val over = oversized.sorted()
      val worst = maxBuckets.sorted()
      val mean = meanBuckets.sorted()
      println(
        "feature index: cell median ${"%.0f".format(Locale.ROOT, cells[cells.size / 2])} m " +
            "(${"%.0f".format(Locale.ROOT, cells.first())} .. ${"%.0f".format(Locale.ROOT, cells.last())}), " +
            "oversized median ${over[over.size / 2]} (max ${over.last()}), " +
            "bucket max ${worst[worst.size / 2]} (worst ${worst.last()}), " +
            "mean ${"%.1f".format(Locale.ROOT, mean[mean.size / 2])}"
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
