package net.bestia.worldgen.pipeline

import net.bestia.worldgen.core.Params
import net.bestia.worldgen.core.ParamsText
import net.bestia.worldgen.core.ParamsTextException
import net.bestia.worldgen.climate.ClimateParams
import net.bestia.worldgen.geo.ClosedBasinParams
import net.bestia.worldgen.geo.DropletParams
import net.bestia.worldgen.geo.ErosionParams
import net.bestia.worldgen.geo.TectonicsParams
import net.bestia.worldgen.karst.CaveParams
import net.bestia.worldgen.mana.CorruptionParams
import net.bestia.worldgen.climate.WeatherParams
import net.bestia.worldgen.mana.ManaParams
import net.bestia.worldgen.resource.GradeMix
import net.bestia.worldgen.resource.ResourceParams
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Loading a params file into [WorldParams].
 *
 * The first test is the one that matters most, and it is the reason this file exists rather than a handful of
 * "does it read a number" cases: **a tunable that can be hashed but not configured, or configured but not
 * hashed, is a bug.** The first is a knob a file cannot reach - which is the state all two hundred of them were
 * in before this branch. The second is worse: a knob a file *can* reach that moves terrain without moving a
 * version, so a cached chunk from before the change stays valid and the world quietly disagrees with itself.
 *
 * Neither is visible by reading the class, because the digest and the loader are two hand-written lists twenty
 * lines apart, and the failure is a missing line in one of them.
 */
class WorldParamsLoadTest {

  /**
   * The classes whose loaders are written, each with the fields `resolved` forwards rather than reads.
   *
   * A forwarded field is deliberately absent from the loader: `WorldParams.resolved` overwrites it from the
   * field that owns it, so a file key would be applied and then discarded. It stays in the *digest* because the
   * value still decides terrain - it is simply decided elsewhere.
   */
  private val loadable: List<Triple<String, Params, Set<String>>> = listOf(
    Triple("tectonics", TectonicsParams(), emptySet()),
    Triple("climate", ClimateParams(), emptySet()),
    // Forwarded from tectonics, which carved the margin these two describe.
    Triple("erosion", ErosionParams(), setOf("oceanBorderDepth", "oceanBorderWobble")),
    Triple("erosion.basins", ClosedBasinParams(), emptySet()),
    Triple("resource", ResourceParams(), emptySet()),
    Triple("resource.grades", GradeMix(), emptySet()),
    Triple("cave", CaveParams(), emptySet()),
    Triple("mana", ManaParams(), emptySet()),
    Triple("corruption", CorruptionParams(), emptySet()),
    Triple("weather", WeatherParams(), emptySet()),
    // Ahead of the rest of the chunk tier because its cost, not its look, is the open question - see
    // `DropletParams.overriddenBy`.
    Triple("droplets", DropletParams(), emptySet())
  )

  @Test
  fun `every tunable a loader can set is hashed, and every one it cannot is forwarded`() {
    val text = empty()
    // Run every loader so the requested-key set is complete; the values are irrelevant here.
    WorldParams.load(text)

    val prefixes = loadable.map { it.first }.toSet()

    for ((prefix, params, forwarded) in loadable) {
      val digested = params.digest().names.toSet()
      val loaderKeys = text.requestedKeys
        .filter { it.startsWith("$prefix.") && it.removePrefix("$prefix.").none { c -> c == '.' } }
        .map { it.removePrefix("$prefix.") }
        .toSet()

      // A nested params object folds as one digest entry and loads as a whole scope of its own, so it is
      // covered by its own row above rather than by a flat key here. Recognised by that row existing - so a
      // nested object nobody listed stays in `expected`, matches no loader key, and fails.
      val nested = digested.filter { "$prefix.$it" in prefixes }.toSet()
      val expected = digested - forwarded - nested

      assertEquals(
        expected,
        loaderKeys,
        "$prefix: the fields it hashes and the fields a file can set differ. " +
            "Hashed but not loadable ${expected - loaderKeys} " +
            "(a nested params object needs its own row in `loadable`), " +
            "loadable but not hashed ${loaderKeys - digested}"
      )
    }
  }

  @Test
  fun `a forwarded field has no file key, so setting one is an error rather than a silent overwrite`() {
    // The mechanism, asserted from the outside: nobody asks for `erosion.oceanBorderDepth`, so it lands in the
    // unknown-key report. Were it loadable, the value would be applied and then overwritten by `resolved`, and
    // a designer would see their number ignored with no message at all.
    val text = ParamsText.parse("params-format = 1\nerosion.oceanBorderDepth = 900", "test.params")

    val error = assertFailsWith<ParamsTextException> { WorldParams.load(text) }

    assertTrue("oceanBorderDepth" in error.message!!, error.message!!)
  }

  @Test
  fun `a loaded value reaches the stage, the version and the pipeline`() {
    val text = ParamsText.parse(
      """
      params-format = 1
      tectonics.targetLandFraction = 0.30
      erosion.basins.spacing = 60000
      """.trimIndent(),
      "test.params"
    )

    val loaded = WorldParams.load(text)

    assertEquals(0.30, loaded.tectonics.targetLandFraction)
    assertEquals(60_000.0, loaded.erosion.basins.spacing)
    assertNotEquals(
      WorldParams.DEFAULT.version,
      loaded.version,
      "a file that changes a tunable has to change the params version, or two runs cannot be told apart"
    )

    val config = StandardWorld.demoConfig().copy(widthCells = 64, heightCells = 64)
    assertNotEquals(
      StandardWorld.pipeline(config).pipelineVersion,
      StandardWorld.pipeline(config, loaded).pipelineVersion,
      "...and it has to reach pipelineVersion, which is the number the server stores"
    )
  }

  @Test
  fun `a file setting only the format line is exactly the defaults`() {
    val loaded = WorldParams.load(empty())

    assertEquals(WorldParams.DEFAULT.version, loaded.version)
    assertEquals(WorldParams.DEFAULT.chunkTierVersion, loaded.chunkTierVersion)
  }

  @Test
  fun `a value outside its range is refused by the params class, not silently clamped`() {
    // The file format checks syntax; the range is the params class's own `init`, and this is the seam between
    // them. A clamp here would be the worst of both: a world generated from numbers that are not in the file.
    val text = ParamsText.parse("params-format = 1\ntectonics.oceanicShare = 1.4", "test.params")

    val error = assertFailsWith<IllegalArgumentException> { WorldParams.load(text) }

    assertTrue("oceanicShare" in error.message!!, error.message!!)
  }

  @Test
  fun `the not-yet-loadable set is exactly the classes without a loader`() {
    // Otherwise the queue drifts: a loader gets written and its prefix stays in the set, so the message tells
    // the reader a key cannot be set from a file when it now can.
    val loaded = loadable.map { it.first }.filter { '.' !in it }.toSet()
    val overlap = loaded intersect WorldParams.NOT_YET_LOADABLE

    assertEquals(emptySet(), overlap, "these have loaders and are still listed as not loadable")
  }

  private fun empty() = ParamsText.parse("params-format = 1", "test.params")
}
