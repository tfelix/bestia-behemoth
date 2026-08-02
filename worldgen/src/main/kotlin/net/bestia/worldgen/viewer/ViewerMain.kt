package net.bestia.worldgen.viewer

import net.bestia.worldgen.bio.Biome
import net.bestia.worldgen.climate.SeasonalPrecipitation
import net.bestia.worldgen.core.CellRegion
import net.bestia.worldgen.core.ChunkPos
import net.bestia.worldgen.core.FloatLayer
import net.bestia.worldgen.core.IntLayer
import net.bestia.worldgen.core.LayerId
import net.bestia.worldgen.core.SiteKind
import net.bestia.worldgen.core.Stage
import net.bestia.worldgen.core.StageListener
import net.bestia.worldgen.core.StageResult
import net.bestia.worldgen.core.Timings
import net.bestia.worldgen.pipeline.GeneratedWorld
import net.bestia.worldgen.pipeline.Invariants
import net.bestia.worldgen.pipeline.StandardWorld
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.MarkerFeature
import net.bestia.worldgen.vector.PointMarker
import net.bestia.worldgen.voxel.VoxelSeamCheck
import java.awt.GraphicsEnvironment
import java.io.File
import java.util.Locale
import javax.swing.UIManager
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Entry point for the offline viewer.
 *
 * `./gradlew :worldgen:viewer` opens the window on a freshly generated world;
 * `./gradlew :worldgen:viewerExport -Pout=some/dir` renders it to PNGs instead;
 * `./gradlew :worldgen:viewer -Pgenesis` opens the world `zone-server` boots rather than the demo one.
 *
 * Flags: `--export <dir>`, plus every world flag in [WorldArgs] - `--seed`, `--cells`, `--wrap-y` and the
 * rest. The default world is [StandardWorld.demoConfig].
 */
object ViewerMain {

  @JvmStatic
  fun main(args: Array<String>) {
    val cli = WorldArgs(args.toList(), extraFlags = setOf(EXPORT))
    val config = cli.worldConfig(StandardWorld.demoConfig())
    val exportTo = cli.value(EXPORT)

    val tuning = cli.tuning()
    println("generating ${WorldArgs.summary(config)}")
    println("  ${tuning.summary()}")
    val started = System.currentTimeMillis()
    val generated = StandardWorld.build(config, Progress, tuning.params)
    val elapsed = System.currentTimeMillis() - started
    val scene = WorldScene.of(generated, WorldArgs.label(config))

    println("world tier built in $elapsed ms")
    // Printed before the scene summaries so the sub-stage split sits next to the per-stage one above it.
    Timings.printAndReset()
    println("  ${scene.fields.size} fields")
    println("  ${describeLand(generated)}")
    println("  ${describeLakes(generated)}")
    println("  ${describeSeasons(generated)}")
    println("  ${describeBiomes(generated)}")
    println("  ${describeCaves(generated)}")
    println("  ${scene.featureSummary()}")

    if (exportTo != null || GraphicsEnvironment.isHeadless()) {
      val directory = File(exportTo ?: "build/viewer")
      val written = ViewerExport.exportAll(scene, directory)
      println("wrote ${written.size} images to ${directory.absolutePath}")
      written.forEach { println("  ${it.name}") }

      // Worth printing on every export: a picture will not tell you that two chunks disagree by a
      // centimetre, and that is exactly the failure this pipeline is built to avoid.
      scene.seamCheck(Viewport.fit(scene.bounds, 256, 256))?.let { println(it) }
      // The same argument one tier down, where the heightfield check cannot reach: it compares heights on a
      // single z, so a chunk whose blocks or occupancy came out differently the second time passes it.
      println(VoxelSeamCheck.run(generated.materializer, origin = chunkWorthChecking(generated)))
      return
    }

    installLookAndFeel()
    ViewerFrame.open(scene)
  }

  /**
   * The platform look and feel, unless loading it would take the JVM down with it.
   *
   * On Linux the system look and feel is GTK, and choosing it loads native GTK into the process. That is
   * fine from an ordinary terminal and fatal from a snap-confined one: the VS Code snap exports
   * `GTK_PATH`, `GDK_PIXBUF_MODULEDIR` and friends into its children, so the GTK the JVM loads is the
   * snap's, resolved against the snap's own `core20` runtime, whose `libpthread` disagrees with the
   * system glibc. The process dies before the window exists with
   *
   * ```
   * symbol lookup error: /snap/core20/.../libpthread.so.0: undefined symbol: __libc_pthread_init
   * ```
   *
   * which names neither Swing nor this file. Since the VS Code snap is Ubuntu's default install, that is
   * a likely way for someone to meet the viewer for the first time.
   *
   * **The `runCatching` this replaces was never protection.** A native symbol lookup failure is not a Java
   * exception, so there was nothing to catch — the guard read as though the risk had been handled while the
   * JVM died anyway. The only defence is to not load GTK, and `SNAP` in the environment is exactly the
   * condition under which loading it is unsafe. The cross-platform look and feel is plainer, and a plain
   * window beats a glibc message that does not mention the window.
   *
   * `-Dworldgen.laf=<class>` overrides both branches, so a snap user who knows their GTK works can ask for
   * it. It is reported rather than silent, because a viewer that quietly looks different from the one in
   * somebody's screenshot is its own small confusion.
   */
  private fun installLookAndFeel() {
    val requested = System.getProperty(LAF_PROPERTY)
    val confined = System.getenv("SNAP") != null

    val target = when {
      requested != null -> requested
      confined -> UIManager.getCrossPlatformLookAndFeelClassName()
      else -> UIManager.getSystemLookAndFeelClassName()
    }
    if (requested == null && confined) {
      println("snap-confined environment - using the cross-platform look and feel")
      println("  loading native GTK here kills the JVM; -D$LAF_PROPERTY=<class> overrides")
    }

    // Still guarded, for the failures that *are* exceptions - a class name that does not resolve, or a
    // look and feel that refuses the current display. Those leave the default installed and a window opens.
    runCatching { UIManager.setLookAndFeel(target) }
      .onFailure { println("look and feel $target unavailable (${it.message}) - using the default") }
  }

  /**
   * Per-stage timings on the console.
   *
   * Cheap and worth having by default, and the reason is a cautionary tale rather than a principle. This
   * used to say erosion was *two orders of magnitude* more expensive than anything else in the pipeline.
   * It is not, and was not: measured on the 512 km reference world, erosion is about a third of the build
   * and town layout is a quarter, with settlement roads behind them. Nobody had checked, the claim sat
   * here for a long time as the one piece of performance guidance in the module, and it pointed at
   * roughly the wrong half of the pipeline.
   *
   * So: this line is worth printing because a measurement beats a remembered measurement, and
   * `-Dworldgen.timings=true` splits the expensive stages further still - see
   * [net.bestia.worldgen.core.Timings] for the sub-stage counters and
   * [net.bestia.worldgen.viewer.BenchMain] for comparing two ways of running the same world.
   */
  private object Progress : StageListener {
    override fun onStageFinish(
      stage: Stage,
      region: CellRegion,
      result: StageResult,
      millis: Long
    ) {
      val produced = buildList {
        if (result.layers.isNotEmpty()) add("${result.layers.size} layers")
        if (result.features.isNotEmpty()) add("${result.features.size} features")
      }.joinToString(", ").ifEmpty { "nothing" }

      println("  ${stage.id} @ ${region.resolution}: $produced in $millis ms")
    }
  }

  /**
   * How many caves there are, how big they are, and - the number that actually matters - how hard one is to
   * find.
   *
   * The counts alone cannot answer "does a cave feel special", because a cave system is not a thing you
   * encounter at world scale: a player walks a few kilometres and either passes a way in or does not. So the
   * headline figure is **the share of dry land within a day's walk of an entrance**, measured on the coarse
   * grid. Somewhere in the low single digits is the target - common enough that caves are a real part of the
   * world and rare enough that finding one is an event.
   *
   * Entrances rather than systems, because a system with no mouth you happened to pass is a system you did not
   * find, and the two counts differ by a lot: most galleries end blind.
   */
  private fun describeCaves(generated: GeneratedWorld): String {
    val all = generated.world.features.all()
    val systems = all.count { it.kind == FeatureKind.CAVE_SYSTEM }
    val entrances = all.filter { it.kind == FeatureKind.CAVE_ENTRANCE }.filterIsInstance<PointMarker>()
    val passages = all.filter { it.kind == FeatureKind.CAVE_PASSAGE }.filterIsInstance<MarkerFeature>()
    val hoards = all.count { it.kind == FeatureKind.CAVE_HOARD }
    // How many hoards hold a *named* relic rather than anonymous plate. Counted because the branch that puts
    // one there is easy to write and hard to reach - a figure fleeing a sack would carry their sword, not bury
    // it - and a branch nothing reaches looks exactly like one that works.
    val named = generated.world.chronicle?.sites
      ?.count { it.kind == SiteKind.HOARD && it.artifact >= 0 } ?: 0

    if (systems == 0) return "caves 0 - no limestone wet enough, or none of it near a hillside"

    val metres = passages.sumOf { it.centerline.length }
    val elevation = generated.world.layers[LayerId.ELEVATION] as? FloatLayer
      ?: return "caves $systems systems, ${passages.size} passages, ${entrances.size} entrances"

    val reach = Invariants.landShareNear(
      generated, elevation, entrances.map { it.position }, CAVE_WALK_METRES
    )

    return "caves $systems systems, ${passages.size} passages, ${entrances.size} entrances, " +
        "${(metres / 1000.0).roundToInt()} km of gallery, $hoards hoards ($named with a named relic) - " +
        "${"%.1f".format(Locale.ROOT, reach * 100.0)}% of land within " +
        "${(CAVE_WALK_METRES / 1000.0).roundToInt()} km of a way in"
  }

  /**
   * How far a player might reasonably wander off a road in one outing, in metres.
   *
   * Not a tuning knob - it is the yardstick the density is judged against, and moving it would move the
   * measurement rather than the world.
   */
  private const val CAVE_WALK_METRES = 4_000.0

  /**
   * A chunk with something in it, for [VoxelSeamCheck].
   *
   * A determinism check over a block of ocean floor or open sky compares a few million identical zeroes and
   * reports itself clean, which is true and tells you nothing - so the block is anchored on a building rather
   * than on the origin or the middle of the map. Buildings are the densest thing the chunk tier writes: a town
   * chunk has masonry, roofing, paving, spans in one buffer and now removals, which is every code path the
   * check exists for in one place.
   *
   * The fallback is the centre of the world with no claim attached, because a world with no buildings at all is
   * a much larger finding than a vacuous seam check, and `Report.solidVoxels` says which happened.
   */
  private fun chunkWorthChecking(generated: GeneratedWorld): ChunkPos {
    val config = generated.config
    val built = generated.world.features.all().firstOrNull { it.kind == FeatureKind.BUILDING }

    val x = if (built != null) (built.bbox.minX + built.bbox.maxX) * 0.5 else config.widthMetres * 0.5
    val y = if (built != null) (built.bbox.minY + built.bbox.maxY) * 0.5 else config.heightMetres * 0.5

    return ChunkPos(
      Math.floorDiv(Math.floor(x / config.voxelSize).toInt(), config.chunkSize),
      Math.floorDiv(Math.floor(y / config.voxelSize).toInt(), config.chunkSize)
    )
  }

  /**
   * How much of the world is dry, in both the form the generator aims at and the form a player gets.
   *
   * Two numbers rather than one, and it is worth the extra word. `TectonicsStage` normalises the *bedrock*
   * land fraction to a target; erosion and deposition then move the shoreline. Printing only the final figure
   * makes a normalisation that missed indistinguishable from a seed whose rivers happened to build a lot of
   * delta, and telling those apart is otherwise two more runs of the generator.
   */
  private fun describeLand(generated: GeneratedWorld): String {
    val land = Invariants.landFraction(generated, LayerId.ELEVATION)
    val bedrock = Invariants.landFraction(generated, LayerId.BEDROCK_ELEVATION)
    val elevation = generated.world.layers.require<FloatLayer>(LayerId.ELEVATION)

    return "land ${"%.3f".format(Locale.ROOT, land)} of the world " +
        "(bedrock ${"%.3f".format(Locale.ROOT, bedrock)})" +
        ", ${elevation.data.min().toInt()} .. ${elevation.data.max().toInt()} m"
  }

  /**
   * How many lakes there are, how much of the world they cover, and how many are salt.
   *
   * Printed because for a long time the answer was **none, on every world at every size**, and nothing said
   * so. `hydro/Lakes.kt` had the whole endorheic balance in it and never received a basin to apply it to,
   * because erosion conditions its output surface to be depression-free and nothing else dug one; the
   * invariant that should have caught it skips every cell that has no lake in it, so it passed. A count that
   * appears on every run is the cheapest possible guard against a subsystem being quietly dead.
   *
   * Endorheic basins are called out separately because they are the interesting half - a terminal lake
   * concentrates salt, which is where `ResourceStage` puts its evaporite deposits - and because "some lakes,
   * none of them terminal" and "no lakes" are different failures.
   *
   * The tectonic basin count is beside them because the two lake sources fail independently and at different
   * world sizes. Ice gave the 512 km world a hundred and fifteen lakes while leaving the 128 km world with
   * none, and a single total would have read as "lakes are working" on the world it was being read on.
   */
  private fun describeLakes(generated: GeneratedWorld): String {
    val lakes = generated.world.layers[LayerId.LAKE_ID] as? IntLayer ?: return "no lake layer"
    val basins = generated.world.features.all().count { it.kind == FeatureKind.TECTONIC_BASIN }

    // The count itself comes from Invariants so the sweep and this agree by construction; the breakdown is
    // this tool's own, because a sweep wants one number per world and a single world can afford a sentence.
    val total = Invariants.lakeCount(generated)
    if (total == 0) return "lakes 0 - $basins closed basins were carved and none of them holds water"

    val endorheic = HashSet<Int>()
    var cells = 0
    for (id in lakes.data) {
      if (id == 0) continue
      if (id < 0) endorheic.add(id)
      cells++
    }

    val share = 100.0 * cells / lakes.data.size
    return "lakes $total (${endorheic.size} endorheic), ${"%.2f".format(Locale.ROOT, share)}% of the world" +
        ", from $basins tectonic basins and the ice"
  }

  /**
   * How strong the seasonal cycle is, and whether the two hemispheres disagree about when summer is.
   *
   * Printed for the same reason the lake count is: the failure this subsystem can have without anything else
   * noticing is that the four seasonal fields come out **identical**. Nothing downstream would complain - the
   * annual sum is right either way, seven consumers read only that, and four identical maps look exactly like
   * four correct ones at map scale. The layers would simply be four copies of a quarter-year, and the phase
   * that added them would look finished.
   *
   * Two numbers rather than one. The amplitude says the cycle exists; the hemisphere split says it is a
   * *season* rather than a global wobble, which is the part a shared sine phase would silently get wrong.
   */
  private fun describeSeasons(generated: GeneratedWorld): String {
    val seasonal = SeasonalPrecipitation.from(generated.world.layers) ?: return "no seasonal precipitation"
    val summer = generated.world.layers[LayerId.PRECIPITATION_SUMMER] as? FloatLayer
      ?: return "no seasonal precipitation"
    val winter = generated.world.layers[LayerId.PRECIPITATION_WINTER] as? FloatLayer
      ?: return "no seasonal precipitation"

    val region = summer.region
    var north = 0.0
    var south = 0.0
    var northCells = 0
    var southCells = 0
    var amplitude = 0.0

    for (y in 0 until region.height) {
      // The world is a latitude band centred on the equator, so the grid's own midpoint is it.
      val northern = y >= region.height / 2

      for (x in 0 until region.width) {
        val i = y * region.width + x
        val delta = summer.data[i] - winter.data[i]
        amplitude += abs(delta)

        if (northern) {
          north += delta
          northCells++
        } else {
          south += delta
          southCells++
        }
      }
    }

    val cells = (northCells + southCells).coerceAtLeast(1)
    val meanNorth = if (northCells > 0) north / northCells else 0.0
    val meanSouth = if (southCells > 0) south / southCells else 0.0
    val opposed = if (meanNorth * meanSouth < 0.0) "opposed" else "IN PHASE - suspect"

    // Where in the year the wettest quarter falls, over the world, as a sanity check that all four are used.
    val wettest = IntArray(SeasonalPrecipitation.COUNT)
    val metres = region.resolution.metresPerCell
    for (y in 0 until region.height step 4) {
      for (x in 0 until region.width step 4) {
        wettest[seasonal.wettestSeason((x + 0.5) * metres, (y + 0.5) * metres)]++
      }
    }
    val sampled = wettest.sum().coerceAtLeast(1)
    val spread = SeasonalPrecipitation.LAYERS.indices.joinToString(" ") { season ->
      val label = SeasonalPrecipitation.LAYERS[season].name.removePrefix("precipitation_").take(2)
      "$label ${(100.0 * wettest[season] / sampled).roundToInt()}%"
    }

    return "seasons |summer-winter| ${"%.0f".format(Locale.ROOT, amplitude / cells)} mm mean, " +
        "north ${"%+.0f".format(Locale.ROOT, meanNorth)} south ${"%+.0f".format(Locale.ROOT, meanSouth)} " +
        "($opposed), wettest: $spread"
  }

  /**
   * What the land is covered in, as a share of the land, commonest first.
   *
   * The measurement that "there is too much desert and not enough farmland" needs, and until this existed the
   * only way to answer it was to look at a picture and argue. Climate is the most indirectly-tuned thing in the
   * pipeline - a change to plate density moves mountain height, which moves rain shadow, which moves the biome
   * mix three stages later - so the mix is exactly the kind of number that has to be visible or it drifts.
   *
   * Water is excluded because it is [describeLand]'s subject, and a world that is half sea would otherwise
   * report ocean as its commonest biome and bury everything the reader is asking about.
   */
  private fun describeBiomes(generated: GeneratedWorld): String {
    val biome = generated.world.layers.require<IntLayer>(LayerId.BIOME)

    val counts = HashMap<Biome, Int>()
    for (id in biome.data) {
      val kind = Biome.entries.getOrNull(id) ?: continue
      if (kind.isWater) continue
      counts[kind] = (counts[kind] ?: 0) + 1
    }

    val land = counts.values.sum()
    if (land == 0) return "no land"

    val green = counts.entries.filter { it.key in GREEN }.sumOf { it.value }
    val mix = counts.entries
      .sortedByDescending { it.value }
      .take(BIOME_REPORT)
      .joinToString(", ") { "${it.key.label} ${(100.0 * it.value / land).roundToInt()}%" }

    return "green ${(100.0 * green / land).roundToInt()}% - $mix"
  }

  /**
   * The land a player would call liveable: closed canopy, or grass a herd could be kept on.
   *
   * One number, printed first, because "too much desert" is a judgement about the whole map and a list of
   * seven shares is not one - it takes arithmetic to answer from, and a target nobody can read off the output
   * is a target that gets tuned past in both directions. Steppe, shrubland and savanna are deliberately *out*:
   * they are the semi-arid margin, and counting them is how a world talks itself into being green.
   */
  private val GREEN = setOf(
    Biome.TEMPERATE_FOREST,
    Biome.TEMPERATE_RAINFOREST,
    Biome.TROPICAL_SEASONAL_FOREST,
    Biome.TROPICAL_RAINFOREST,
    Biome.TAIGA,
    Biome.GRASSLAND,
    Biome.WETLAND,
    Biome.RIPARIAN
  )

  /** Enough of the mix to see what a world is made of without printing the whole vocabulary. */
  private const val BIOME_REPORT = 7

  /** Where to write PNGs instead of opening a window. Says nothing about which world. */
  private const val EXPORT = "--export"

  /** Escape hatch for [installLookAndFeel], as a system property rather than a world flag. */
  private const val LAF_PROPERTY = "worldgen.laf"
}
