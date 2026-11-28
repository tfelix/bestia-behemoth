package net.bestia.zone.battle.ecs.status

import net.bestia.zone.battle.status.CombatValues
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.identity.ecs.OwnedBestia
import net.bestia.zone.util.EntityId
import kotlin.test.Test
import kotlin.test.assertEquals

class WorkFatigueTest {

  private val fatigue = WorkFatigue(StaminaConfig(work = StaminaConfig.Work(gather = 4, mine = 3, craftPerTenSeconds = 2.0)))
  private val world = testWorld()

  @Test
  fun `an owned bestia pays for its work`() {
    val worker = worker()

    fatigue.charge(world, worker, WorkFatigue.Work.GATHER)
    fatigue.chargeCraft(world, worker, craftSeconds = 30f)

    assertEquals(100 - 4 - 6, world.get(worker, Stamina::class)!!.current)
  }

  @Test
  fun `drain reduction takes its share off, but never all of it`() {
    val fed = worker(drainReduction = 50)
    val tough = worker(drainReduction = 100)

    fatigue.charge(world, fed, WorkFatigue.Work.GATHER)
    fatigue.charge(world, tough, WorkFatigue.Work.MINE)

    assertEquals(100 - 2, world.get(fed, Stamina::class)!!.current)
    assertEquals(100 - 1, world.get(tough, Stamina::class)!!.current)
  }

  @Test
  fun `anyone else works for free`() {
    val master = world.createEntity { id -> add(id, Stamina(100, 100)) }

    fatigue.charge(world, master, WorkFatigue.Work.MINE)

    assertEquals(100, world.get(master, Stamina::class)!!.current)
  }

  private fun worker(drainReduction: Int = 0): EntityId = world.createEntity { id ->
    add(id, Stamina(100, 100))
    add(id, OwnedBestia(masterId = 1L, playerBestiaId = 2L))
    add(id, CombatStats(CombatValues.OBJECT_BASELINE.copy(staminaDrainReduction = drainReduction)))
  }
}
