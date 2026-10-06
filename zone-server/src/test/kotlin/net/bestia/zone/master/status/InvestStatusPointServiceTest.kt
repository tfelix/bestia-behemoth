package net.bestia.zone.master.status

import io.mockk.mockk
import io.mockk.verify
import net.bestia.zone.identity.ecs.Master as MasterComponent
import net.bestia.zone.battle.ecs.status.BaseStatusValues
import net.bestia.zone.battle.ecs.status.StatusPoints
import net.bestia.zone.ecs.core.EcsWorld
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.persistence.EntityWriteBehind
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Drives the real service against a real (system-less) world. The point is the pricing: a status point is
 * not a flat +1 any more, so what comes off the pool and what the attribute ends up at are two different
 * numbers.
 */
class InvestStatusPointServiceTest {

  private val world: EcsWorld = testWorld()
  private val writeBehind = mockk<EntityWriteBehind>(relaxed = true)

  private val service = InvestStatusPointService(EffortValueCostCalculator(), writeBehind)

  private fun givenMaster(strength: Int, statusPoints: Int): EntityId {
    return world.createEntity { id ->
      add(id, MasterComponent(1L, "spender"))
      add(id, BaseStatusValues(strength, 1, 1, 1, 1, 1))
      add(id, StatusPoints(statusPoints))
    }
  }

  private fun invest(entityId: EntityId, amount: Int) {
    service.investStatusPoints(world, entityId, listOf(StatusPointInvestment(StatusAttribute.STRENGTH, amount)))
  }

  private fun remainingPoints(entityId: EntityId): Int {
    return world.get(entityId, StatusPoints::class)!!.value
  }

  private fun strength(entityId: EntityId): Int {
    return world.get(entityId, BaseStatusValues::class)!!.strength
  }

  @Test
  fun `a point bought at 9 costs 3, not 1`() {
    val entityId = givenMaster(strength = 9, statusPoints = 3)

    invest(entityId, 1)

    assertEquals(10, strength(entityId))
    assertEquals(0, remainingPoints(entityId), "stepCost(10) is 3, so all three points are gone")
    verify(exactly = 1) { writeBehind.persist(world, listOf(entityId), withStatusEffects = false) }
  }

  @Test
  fun `a batch prices each step against the value the previous step reached`() {
    // 4 -> 5 costs 1, 5 -> 6 costs 2: three points buy exactly two attribute points, not three.
    val entityId = givenMaster(strength = 4, statusPoints = 3)

    invest(entityId, 2)

    assertEquals(6, strength(entityId))
    assertEquals(0, remainingPoints(entityId))
  }

  @Test
  fun `cheap points still cost one each`() {
    // Below 6 the curve is flat, so the old 1-point-per-attribute-point behaviour is preserved there.
    val entityId = givenMaster(strength = 1, statusPoints = 4)

    invest(entityId, 4)

    assertEquals(5, strength(entityId))
    assertEquals(0, remainingPoints(entityId))
  }

  @Test
  fun `an unaffordable batch is refused whole, leaving nothing spent or saved`() {
    val entityId = givenMaster(strength = 9, statusPoints = 2)

    assertThrows<NoStatusPointsAvailableException> {
      invest(entityId, 1)
    }

    assertEquals(9, strength(entityId))
    assertEquals(2, remainingPoints(entityId))
    verify(exactly = 0) { writeBehind.persist(any(), any(), any(), any()) }
  }
}
