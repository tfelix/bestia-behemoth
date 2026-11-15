package net.bestia.worldgen.history

import net.bestia.worldgen.core.Chronicle
import net.bestia.worldgen.core.EventKind
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.pipeline.StandardWorld
import net.bestia.worldgen.pipeline.TestWorlds
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Pins the mix of peoples, wars, gods, cults, world bosses and families in a history, so that a later change
 * cannot thin it out again without a failing test. The bounds sit well inside what `chronicle -Psweep` measured
 * over 32 genesis and 16 large worlds when the rates were tuned.
 */
class HistoryMixTest {

  @Test
  fun `every world has peoples at war, gods and a world boss still alive`() {
    for ((seed, mix) in small) {
      assertTrue(mix.civs >= 2 && mix.wars >= 1 && mix.battles >= 1, "seed $seed has no war between peoples: $mix")
      assertTrue(mix.gods >= MIN_GODS, "seed $seed: $mix")
      assertTrue(mix.bosses >= MIN_BOSSES && mix.bossesAlive >= 1, "seed $seed: $mix")
    }
  }

  @Test
  fun `most worlds have cults and family strife`() {
    val worlds = small.values
    assertShare(worlds, "a cult") { it.cults > 0 }
    assertShare(worlds, "a persecuted cult") { it.persecutions > 0 }
    assertShare(worlds, "a feud") { it.feuds > 0 }
    assertShare(worlds, "an oath of vengeance") { it.oaths > 0 }
    assertShare(worlds, "a marriage between peoples") { it.marriages > 0 }
  }

  @Test
  fun `the land does not crowd out the people`() {
    for ((seed, mix) in small + large) {
      assertTrue(mix.nature <= mix.events * MAX_NATURE_SHARE, "seed $seed: ${mix.nature} of ${mix.events} events")
    }
  }

  @Test
  fun `a larger world has more peoples and more wars`() {
    val civs = large.values.map { it.civs }.average() / small.values.map { it.civs }.average()
    val wars = large.values.map { it.wars }.average() / small.values.map { it.wars }.average()
    assertTrue(civs >= MIN_GROWTH && wars >= MIN_GROWTH, "4x the land: civs x$civs, wars x$wars")
  }

  private fun assertShare(worlds: Collection<Mix>, what: String, has: (Mix) -> Boolean) {
    val share = worlds.count(has).toDouble() / worlds.size
    assertTrue(share >= MIN_SHARE, "only ${worlds.count(has)} of ${worlds.size} worlds have $what: $worlds")
  }

  private data class Mix(
    val civs: Int, val wars: Int, val battles: Int, val gods: Int, val cults: Int, val persecutions: Int,
    val bosses: Int, val bossesAlive: Int, val feuds: Int, val oaths: Int, val marriages: Int,
    val nature: Int, val events: Int
  ) {
    companion object {
      fun of(chronicle: Chronicle): Mix {
        val census = chronicle.eventCensus().toMap()
        fun count(kind: EventKind) = census[kind] ?: 0
        return Mix(
          civs = chronicle.civs.size, wars = count(EventKind.WAR_DECLARED), battles = count(EventKind.BATTLE),
          gods = chronicle.deities.size, cults = chronicle.cults.size, persecutions = count(EventKind.CULT_PERSECUTED),
          bosses = chronicle.monsters.size, bossesAlive = chronicle.monsters.count { it.alive },
          feuds = count(EventKind.FEUD_BEGAN), oaths = count(EventKind.VENGEANCE_SWORN),
          marriages = count(EventKind.MARRIAGE_ALLIANCE), nature = NATURE.sumOf { count(it) },
          events = chronicle.events.size
        )
      }
    }
  }

  /** The same worlds `ManaHistoryTest` and `QuestThreadsTest` read, so the suite builds them once. */
  private val small by lazy {
    SMALL_SEEDS.zip(TestWorlds.getAll(SMALL_SEEDS.map { WorldConfig(seed = it, widthCells = 128, heightCells = 128) }))
      .associate { (seed, world) -> seed to Mix.of(world.world.chronicle) }
  }

  /** Four times the land of [small]. Reduced as they are built: only the census is needed. */
  private val large by lazy {
    val configs = LARGE_SEEDS.map { StandardWorld.demoConfig(it).copy(widthCells = 256, heightCells = 256) }
    LARGE_SEEDS.zip(TestWorlds.sweep(configs) { Mix.of(it.world.chronicle) }).toMap()
  }

  private companion object {
    val SMALL_SEEDS = listOf(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L)
    val LARGE_SEEDS = listOf(1L, 2L, 3L, 4L)

    /** What the land does to people, rather than what people do: the filler a history must not drown in. */
    val NATURE = setOf(EventKind.ERUPTION, EventKind.FAMINE, EventKind.PLAGUE, EventKind.FLOOD, EventKind.BLIGHT_SPREAD)

    const val MIN_GODS = 4
    const val MIN_BOSSES = 2
    const val MIN_SHARE = 0.5
    const val MAX_NATURE_SHARE = 0.35
    const val MIN_GROWTH = 1.3
  }
}
