package net.bestia.worldgen.viewer

import net.bestia.worldgen.core.CellRegion
import net.bestia.worldgen.core.Stage
import net.bestia.worldgen.core.StageListener
import net.bestia.worldgen.core.StageResult
import net.bestia.worldgen.pipeline.StandardWorld
import java.awt.GraphicsEnvironment
import java.io.File
import javax.swing.UIManager

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

    runCatching { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()) }
    ViewerFrame.open(scene)
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

  /** Where to write PNGs instead of opening a window. Says nothing about which world. */
  private const val EXPORT = "--export"
}
