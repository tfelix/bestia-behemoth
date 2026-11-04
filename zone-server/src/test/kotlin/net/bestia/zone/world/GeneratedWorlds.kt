package net.bestia.zone.world

import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.pipeline.GeneratedWorld
import net.bestia.worldgen.pipeline.TestWorlds

/**
 * The worlds zone-server's tests agree on, built once through [TestWorlds] and shared by every test that asks.
 *
 * A test reads these and never mutates them. Hold one in an instance property or a local, never in a companion:
 * a static reference keeps the world for the rest of the run, and [TestWorlds] can only free what nobody holds.
 */
object GeneratedWorlds {

  /**
   * The size the world-dependent suites agree on.
   *
   * `VolcanicHistoryTest`'s, and for its reason: at 128 cells a world rarely puts a town within ash
   * reach of a vent, so the rarer history never happens and the tests pass by having nothing to check.
   */
  const val CELLS = 256

  /** The seeds those suites agree on. Sharing them is what makes one cache serve all of them. */
  val SEEDS = listOf(1L, 3L, 42L)

  /** `application.yml`'s pinned seed. */
  const val DEV_SEED = 11_753_242L

  /** The world the dev server runs, from the birth settings' defaults. The Spring test context runs it too. */
  val devConfig: WorldConfig = WorldGenConfig().toWorldConfig(DEV_SEED)

  fun of(seed: Long, cells: Int = CELLS): GeneratedWorld {
    return TestWorlds.get(WorldConfig(seed = seed, widthCells = cells, heightCells = cells))
  }

  fun dev(): GeneratedWorld {
    return TestWorlds.get(devConfig)
  }
}
