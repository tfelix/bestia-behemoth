package net.bestia.worldgen.viewer

import net.bestia.worldgen.core.ParamsText
import net.bestia.worldgen.pipeline.StandardWorld
import net.bestia.worldgen.pipeline.WorldParams
import net.bestia.worldgen.render.Viewport
import java.io.File
import java.util.Locale
import kotlin.math.abs

/**
 * Two worlds, one difference field: what changed, where, and by how much.
 *
 * ### Why this is a tool and not a diff of two PNGs
 *
 * This module's entire debugging method is comparing two runs - "change one number and look at the same
 * world" is habit one, and `WorldParams` exists so that the *only* thing that changes is the number. What was
 * missing was the other half: a way to see the consequence. Two exports side by side answer "these look
 * different"; they do not answer *where* or *by how much*, and on a 1400 px picture of a 512 km world a
 * twenty-metre change to every valley floor is invisible next to a coastline that moved one pixel.
 *
 * So the output is two things, and the printed one matters more. **A table of mean and worst absolute
 * difference per layer** says which subsystem moved, in the layer's own units, and is the thing to read
 * first; it is also the only half that works in a terminal. The PNGs then say where.
 *
 * ### One variable at a time
 *
 * It compares either two seeds at one set of params, or two params files at one seed - never both, and the
 * argument parser refuses the combination rather than picking one. A diff of two things that differ in two
 * ways answers nothing, and the failure it invites is exactly the one this tool exists to prevent: reading a
 * consequence off a comparison that had two causes in it.
 *
 * ```
 * ./gradlew :worldgen:diff -Pseed=7 -Pother=8
 * ./gradlew :worldgen:diff -Pseed=7 -Pparams=a.txt -Potherparams=b.txt -Pout=build/diff
 * ```
 *
 * Both worlds are built at the same size, because a difference field samples by world coordinate and two
 * worlds of different extents would silently compare a valley against open sea.
 */
object DiffMain {

  @JvmStatic
  fun main(argv: Array<String>) {
    val args = WorldArgs(argv.toList(), extraFlags = setOf(OTHER, OTHER_PARAMS, OUT))
    val baseConfig = args.worldConfig(StandardWorld.demoConfig().copy(widthCells = 192, heightCells = 192))

    val otherSeed = args.long(OTHER)
    val otherParamsPath = args.value(OTHER_PARAMS)

    require(otherSeed != null || otherParamsPath != null) {
      "Nothing to compare against: give --other <seed> or --other-params <file>"
    }
    require(otherSeed == null || otherParamsPath == null) {
      "--other and --other-params together would change two things at once, and a difference with two " +
          "causes in it says nothing about either. Run it twice."
    }

    val leftTuning = args.tuning()
    val rightParams = if (otherParamsPath == null) leftTuning.params else {
      val file = File(otherParamsPath)
      require(file.isFile) { "$OTHER_PARAMS: no such file '$otherParamsPath'" }
      WorldParams.load(ParamsText.parse(file.readText(), otherParamsPath), WorldParams.DEFAULT)
    }
    val leftParams = leftTuning.params

    val rightConfig = if (otherSeed == null) baseConfig else baseConfig.copy(seed = otherSeed)

    val what = if (otherSeed != null) "seed ${baseConfig.seed} against seed $otherSeed"
    else "$otherParamsPath against the baseline params, both on seed ${baseConfig.seed}"
    println("diffing $what at ${baseConfig.widthCells}x${baseConfig.heightCells} cells")
    println("  left  ${leftTuning.summary()}")
    println("  right params ${"%016x".format(Locale.ROOT, rightParams.version)}")

    val left = WorldScene.of(StandardWorld.build(baseConfig, params = leftParams), "left")
    val right = WorldScene.of(StandardWorld.build(rightConfig, params = rightParams), "right")

    val byName = right.fields.associateBy { it.name }
    val shared = left.fields.mapNotNull { field -> byName[field.name]?.let { field to it } }

    // Raster fields only. A chunk-backed field generates on demand, so sixty-five thousand probes of one is
    // sixty-five thousand chunks - the sampling below would take longer than every other tool in this module
    // put together. Said out loud rather than filtered quietly, because "the voxel tier did not move" and
    // "the voxel tier was not looked at" are the same output otherwise.
    val pairs = shared.filterNot { (a, _) -> a is ChunkBudgeted }
    val skipped = shared.size - pairs.size

    println(
      "${shared.size} of ${left.fields.size} fields are in both worlds; comparing ${pairs.size} raster " +
          "fields and skipping $skipped chunk-backed ones, which would generate a chunk per probe"
    )

    // Measured before anything is drawn, because the table is the half that answers the question and the
    // half that survives a headless run. Sampled on a fixed lattice rather than per cell: the fields include
    // chunk-scale ones that generate on demand, and a full sweep of those is a second export.
    val rows = ArrayList<Row>(pairs.size)
    for ((a, b) in pairs) {
      var sum = 0.0
      var worst = 0.0
      var counted = 0
      var worstAt = ""

      for (iy in 0 until SAMPLES) {
        for (ix in 0 until SAMPLES) {
          val x = baseConfig.widthMetres * (ix + 0.5) / SAMPLES
          val y = baseConfig.heightMetres * (iy + 0.5) / SAMPLES
          val va = a.valueAt(x, y)
          val vb = b.valueAt(x, y)
          if (va.isNaN() || vb.isNaN()) continue

          val d = abs(va - vb)
          sum += d
          counted++
          if (d > worst) {
            worst = d
            worstAt = "${x.toInt()},${y.toInt()}"
          }
        }
      }

      if (counted > 0) rows.add(Row(a.name, a.unit, sum / counted, worst, worstAt, counted))
    }

    // Loudest first: the point of the table is to name the subsystem that moved, and a list in field order
    // buries it under a dozen layers that did not.
    rows.sortByDescending { it.mean }

    println()
    println("%-34s %10s %12s %10s  %s".format(Locale.ROOT, "field", "mean |d|", "worst |d|", "unit", "worst at"))
    for (row in rows) {
      println(
        "%-34s %10s %12s %10s  %s".format(
          Locale.ROOT,
          row.name,
          "%.4g".format(Locale.ROOT, row.mean),
          "%.4g".format(Locale.ROOT, row.worst),
          row.unit,
          if (row.worst > 0.0) row.worstAt else "-"
        )
      )
    }

    val moved = rows.count { it.mean > 0.0 }
    println()
    println("$moved of ${rows.size} fields moved at all")
    if (moved == 0) {
      // Not a success message. Two worlds that differ in a seed and produce byte-identical fields mean the
      // seed did not reach the generator, which is a bug in the tool or in the plumbing rather than a finding.
      println("nothing moved - check that the switch reached the generator before concluding it had no effect")
    }

    val out = args.value(OUT) ?: return
    val directory = File(out)
    directory.mkdirs()

    val renderer = MapRenderer(left.config, left::populationOf)
    val view = Viewport.fit(left.bounds, WIDTH_PX, HEIGHT_PX)
    val options = RenderOptions(autoRange = true, features = false)

    var written = 0
    for ((a, b) in pairs) {
      if (a.availabilityFor(view) != null) continue

      val field = DifferenceField(a, b, name = "diff ${a.name}")
      val map = renderer.render(field, view, options, emptyList())
      if (map.unavailable != null) continue

      ViewerExport.write(map, directory, field.name)
      written++
    }
    println("wrote $written difference maps to ${directory.absolutePath}")
  }

  private class Row(
    val name: String,
    val unit: String,
    val mean: Double,
    val worst: Double,
    val worstAt: String,
    val counted: Int
  )

  const val OTHER = "--other"
  const val OTHER_PARAMS = "--other-params"
  const val OUT = "--out"

  /** Samples per axis. 256 x 256 is 65k probes per field, which is a second over a dozen fields. */
  private const val SAMPLES = 256

  private const val WIDTH_PX = 1400
  private const val HEIGHT_PX = 1400
}
