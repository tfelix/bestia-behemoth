package net.bestia.worldgen.history

import net.bestia.worldgen.core.ActorType
import net.bestia.worldgen.core.Chronicle
import net.bestia.worldgen.core.CultFate
import net.bestia.worldgen.core.EventKind
import net.bestia.worldgen.core.ThreadKind
import net.bestia.worldgen.core.ThreadRecord
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.pipeline.TestWorlds
import kotlin.test.Test
import kotlin.test.assertTrue

class QuestThreadsTest {

  @Test
  fun `every thread stays open since an event the log kept`() {
    for ((seed, chronicle) in chronicles) {
      val kept = chronicle.events.map { it.id }.toSet()

      for (thread in chronicle.threads) {
        assertTrue(thread.since in kept, "seed $seed: ${thread.kind} cites pruned event ${thread.since}")
      }
    }
  }

  @Test
  fun `no thread is a story the history already ended`() {
    for ((seed, chronicle) in chronicles) {
      for (thread in chronicle.threads) {
        assertTrue(isOpen(chronicle, thread), "seed $seed: ${thread.kind} ${thread.actors} is not open")
      }
    }
  }

  @Test
  fun `every world leaves a world boss to find`() {
    for ((seed, chronicle) in chronicles) {
      val bosses = chronicle.threads.count { it.kind == ThreadKind.BOSS_SEALED || it.kind == ThreadKind.BOSS_AT_LARGE }
      assertTrue(bosses == chronicle.monsters.count { it.alive }, "seed $seed: $bosses boss threads")
      assertTrue(bosses > 0, "seed $seed has no world boss left alive")
    }
  }

  @Test
  fun `the history leaves several kinds of story open`() {
    val seen = chronicles.values.flatMap { chronicle -> chronicle.threads.map { it.kind } }.toSet()
    assertTrue(seen.size >= MIN_KINDS, "only $seen across ${SEEDS.size} worlds")
  }

  private fun isOpen(chronicle: Chronicle, thread: ThreadRecord): Boolean {
    val first = thread.actors.first().index

    return when (thread.kind) {
      ThreadKind.ARTIFACT_LOST -> chronicle.artifacts[first].unclaimed
      ThreadKind.ARTIFACT_AWAY -> chronicle.artifacts[first].let { it.keptAt != it.homeTemple }
      ThreadKind.VENGEANCE_UNFULFILLED -> chronicle.events.none {
        it.kind == EventKind.VENGEANCE_FULFILLED && thread.since in it.causes
      }
      ThreadKind.BOSS_SEALED, ThreadKind.BOSS_AT_LARGE -> chronicle.monsters[first].alive
      ThreadKind.HOUSE_WITHOUT_HEIR -> {
        val house = chronicle.figures[first].dynasty
        chronicle.figures.none { it.dynasty == house && it.deathYear == 0 }
      }
      ThreadKind.CULT_PERSECUTED -> chronicle.cults[first].fate == CultFate.ACTIVE
      ThreadKind.FORGOTTEN_GOD -> chronicle.settlements.none { !it.isRuin && it.wasFounded && it.patron == first } &&
          chronicle.civs.none { it.exists && it.patron == first } &&
          thread.actors.none { it.type == ActorType.SETTLEMENT && !chronicle.settlements[it.index].isRuin }
    }
  }

  /** The same worlds `ManaHistoryTest` reads, so the suite builds them once. */
  private val chronicles by lazy {
    SEEDS.zip(TestWorlds.getAll(SEEDS.map { WorldConfig(seed = it, widthCells = 128, heightCells = 128) }))
      .associate { (seed, world) -> seed to world.world.chronicle }
  }

  private companion object {
    val SEEDS = listOf(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L)

    const val MIN_KINDS = 5
  }
}
