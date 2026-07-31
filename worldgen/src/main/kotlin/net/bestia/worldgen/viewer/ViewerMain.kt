package net.bestia.worldgen.viewer

import net.bestia.worldgen.bio.Biome
import net.bestia.worldgen.core.CellRegion
import net.bestia.worldgen.core.FloatLayer
import net.bestia.worldgen.core.IntLayer
import net.bestia.worldgen.core.LayerId
import net.bestia.worldgen.core.Stage
import net.bestia.worldgen.core.StageListener
import net.bestia.worldgen.core.StageResult
import net.bestia.worldgen.pipeline.GeneratedWorld
import net.bestia.worldgen.pipeline.Invariants
import net.bestia.worldgen.pipeline.StandardWorld
import net.bestia.worldgen.vector.FeatureKind
import java.awt.GraphicsEnvironment
import java.io.File
import java.util.Locale
import javax.swing.UIManager
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

    println("generating ${WorldArgs.summary(config)}")
    val started = System.currentTimeMillis()
    val generated = StandardWorld.build(config, Progress)
    val scene = WorldScene.of(generated, WorldArgs.label(config))

    println("world tier built in ${System.currentTimeMillis() - started} ms")
    println("  ${scene.fields.size} fields")
    println("  ${describeLand(generated)}")
    println("  ${describeLakes(generated)}")
    println("  ${describeBiomes(generated)}")
    println("  ${scene.featureSummary()}")

    if (exportTo != null || GraphicsEnvironment.isHeadless()) {
      val directory = File(exportTo ?: "build/viewer")
      val written = ViewerExport.exportAll(scene, directory)
      println("wrote ${written.size} images to ${directory.absolutePath}")
      written.forEach { println("  ${it.name}") }

      // Worth printing on every export: a picture will not tell you that two chunks disagree by a
      // centimetre, and that is exactly the failure this pipeline is built to avoid.
      scene.seamCheck(Viewport.fit(scene.bounds, 256, 256))?.let { println(it) }
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
   * Cheap and worth having by default. Erosion is two orders of magnitude more expensive than anything
   * else in the pipeline, and knowing that without instrumenting anything is what stops the next person
   * from optimising the wrong stage.
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
