package net.bestia.zone.battle.attack

import io.mockk.every
import io.mockk.mockk
import net.bestia.zone.aoi.EntityAOIService
import net.bestia.zone.battle.BattleContextFixture
import net.bestia.zone.battle.Element
import net.bestia.zone.battle.Size
import net.bestia.zone.battle.damage.HitDamage
import net.bestia.zone.battle.ecs.damage.IncomingDamage
import net.bestia.zone.battle.ecs.status.CombatStats
import net.bestia.zone.battle.ecs.status.Health
import net.bestia.zone.battle.ecs.status.Nature
import net.bestia.zone.battle.status.CombatValues
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Test
import java.util.Random
import kotlin.test.assertEquals

class EssenceSwingsTest {

  private val world = testWorld()
  private val aoi = mockk<EntityAOIService>()
  private val rolls = mockk<Random> { every { nextInt(100) } returns 0 }
  private val swings = EssenceSwings(aoi, rolls)

  @Test
  fun `a reaping scythe strikes the foes next to the target with the same blow`() {
    val reaper = attacker { copy(meleeSplash = true) }
    val target = mob(at = 0)
    val beside = mob(at = 1)
    val farther = mob(at = 3)
    every { aoi.queryEntitiesInCube(any(), any(), any()) } returns setOf(reaper, target, beside, farther)

    swings.onSwingLanded(world, reaper, target, HitDamage(30))

    assertEquals(30, staged(beside))
    assertEquals(0, staged(farther))
    assertEquals(0, staged(target), "the target's own blow is staged by the swing, not here")
  }

  @Test
  fun `doom drops a target to 1 HP, but never a boss`() {
    val doomBlade = attacker { copy(doomChancePercent = 1) }
    val wolf = mob(at = 1)
    val boss = mob(at = 1, boss = true)

    swings.onSwingLanded(world, doomBlade, wolf, HitDamage(1))
    swings.onSwingLanded(world, doomBlade, boss, HitDamage(1))

    assertEquals(99, staged(wolf))
    assertEquals(0, staged(boss))
  }

  private fun attacker(tuned: CombatValues.() -> CombatValues): EntityId = world.createEntity { id ->
    add(id, Master(1L))
    add(id, Position(0, 0, 0))
    add(id, CombatStats(BattleContextFixture.combatStats().values.tuned()))
  }

  private fun mob(at: Long, boss: Boolean = false): EntityId = world.createEntity { id ->
    add(id, Position(at, 0, 0))
    add(id, Health(100, 100))
    add(id, Nature(Element.NORMAL, Size.MEDIUM, boss = boss))
  }

  private fun staged(id: EntityId): Int = world.get(id, IncomingDamage::class)?.total() ?: 0
}
