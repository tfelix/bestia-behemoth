package net.bestia.zone.world

import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.pipeline.GeneratedWorld
import net.bestia.worldgen.pipeline.StandardWorld

/**
 * Generated worlds, built once per test run and shared by every test that wants one.
 *
 * ### Why this is worth a file
 *
 * Several suites assert against real worlds rather than fixtures, which is the right call - a
 * knowledge model or a lore query tested against a hand-built chronicle is testing the fixture. The
 * cost is that each of them was generating its own, and JUnit builds a fresh instance per test method,
 * so one class with six tests over three seeds generated eighteen worlds.
 *
 * That is slow, and it is worse than slow: it shares a JVM with the rest of the suite, and the memory
 * churn was enough to fail a timing-sensitive chunk-streaming scenario in a completely unrelated
 * package. Twice, reproducibly, and never when that scenario ran on its own. Sharing the worlds fixed
 * it; the same three seeds now cost three generations for the whole run instead of nine or more.
 *
 * ### Read-only, and that is not enforced
 *
 * A [GeneratedWorld] is immutable in practice - the world tier is computed once and read forever - but
 * nothing stops a test mutating something reachable from one. Do not. A test that needs to change a
 * world needs its own, through [StandardWorld.build] directly.
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

  private val cache = HashMap<Pair<Long, Int>, GeneratedWorld>()

  @Synchronized
  fun of(seed: Long, cells: Int = CELLS): GeneratedWorld {
    return cache.getOrPut(seed to cells) {
      StandardWorld.build(WorldConfig(seed = seed, widthCells = cells, heightCells = cells))
    }
  }
}
