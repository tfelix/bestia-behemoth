package net.bestia.zone.account.master.status

import io.mockk.every
import io.mockk.mockk
import net.bestia.zone.account.Account
import net.bestia.zone.account.master.BodyType
import net.bestia.zone.account.master.Face
import net.bestia.zone.account.master.Hairstyle
import net.bestia.zone.account.master.Master
import net.bestia.zone.account.master.MasterRepository
import net.bestia.zone.account.master.MasterResolver
import net.bestia.zone.ecs.battle.status.BaseStatusValues
import net.bestia.zone.ecs.battle.status.StatusPoints
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.awt.Color
import java.util.Optional

/**
 * Drives the real service against a real (system-less) [World], mocking only the repository and the
 * master -> entity lookup. The point is the pricing: a status point is not a flat +1 any more, so what
 * comes off the pool and what the attribute ends up at are two different numbers.
 */
class InvestStatusPointServiceTest {

  private val world: World = testWorld()
  private val masterRepository = mockk<MasterRepository>(relaxed = true)
  private val masterResolver = mockk<MasterResolver>()

  private val service = InvestStatusPointService(
    masterRepository = masterRepository,
    world = world,
    masterResolver = masterResolver,
    effortValueCostCalculator = EffortValueCostCalculator()
  )

  /**
   * Wires one master with [strength] STR and [statusPoints] to spend, present both in the DB (the
   * pricing authority) and in the world (the pool authority), the way a selected master really is.
   */
  private fun givenMaster(strength: Int, statusPoints: Int): Pair<Master, EntityId> {
    val master = Master(
      account = Account(1L),
      name = "spender",
      hairColor = Color.BLUE,
      skinColor = Color.BLUE,
      hair = Hairstyle.HAIR_1,
      face = Face.FACE_1,
      body = BodyType.BODY_M_1
    )
    master.strength = strength
    master.statusPoints = statusPoints

    val entityId = world.createEntity { id ->
      add(id, BaseStatusValues(strength, 1, 1, 1, 1, 1))
      add(id, StatusPoints(statusPoints))
    }

    every { masterRepository.findById(master.id) } returns Optional.of(master)
    // A relaxed mock would return a bare Object here, which JpaRepository.save's erased generic return
    // type then fails to cast. Echoing the argument also matches what JPA does for a managed entity.
    every { masterRepository.save(any<Master>()) } answers { firstArg() }
    every { masterResolver.getEntityIdByMasterId(master.id) } returns entityId

    return master to entityId
  }

  private fun remainingPoints(entityId: EntityId): Int =
    world.read { get(entityId, StatusPoints::class)?.value } ?: -1

  private fun worldStrength(entityId: EntityId): Int =
    world.read { get(entityId, BaseStatusValues::class)?.strength } ?: -1

  @Test
  fun `a point bought at 9 costs 3, not 1`() {
    val (master, entityId) = givenMaster(strength = 9, statusPoints = 3)

    service.investStatusPoints(master.id, listOf(StatusPointInvestment(StatusAttribute.STRENGTH, 1)))

    assertEquals(10, master.strength)
    assertEquals(0, master.statusPoints, "stepCost(10) is 3, so all three points are gone")
    // The world is only mutated after commit; with no active transaction that happens inline.
    assertEquals(10, worldStrength(entityId))
    assertEquals(0, remainingPoints(entityId))
  }

  @Test
  fun `a batch prices each step against the value the previous step reached`() {
    // 4 -> 5 costs 1, 5 -> 6 costs 2: three points buy exactly two attribute points, not three.
    val (master, entityId) = givenMaster(strength = 4, statusPoints = 3)

    service.investStatusPoints(master.id, listOf(StatusPointInvestment(StatusAttribute.STRENGTH, 2)))

    assertEquals(6, master.strength)
    assertEquals(0, master.statusPoints)
    assertEquals(6, worldStrength(entityId))
  }

  @Test
  fun `cheap points still cost one each`() {
    // Below 6 the curve is flat, so the old 1-point-per-attribute-point behaviour is preserved there.
    val (master, entityId) = givenMaster(strength = 1, statusPoints = 4)

    service.investStatusPoints(master.id, listOf(StatusPointInvestment(StatusAttribute.STRENGTH, 4)))

    assertEquals(5, master.strength)
    assertEquals(0, master.statusPoints)
    assertEquals(5, worldStrength(entityId))
  }

  @Test
  fun `an unaffordable batch is refused whole, leaving nothing spent`() {
    val (master, entityId) = givenMaster(strength = 9, statusPoints = 2)

    assertThrows<NoStatusPointsAvailableException> {
      service.investStatusPoints(master.id, listOf(StatusPointInvestment(StatusAttribute.STRENGTH, 1)))
    }

    // The whole batch is priced before anything is written, so a refusal cannot leave a partial spend.
    assertEquals(9, master.strength)
    assertEquals(2, master.statusPoints)
    assertEquals(9, worldStrength(entityId))
    assertEquals(2, remainingPoints(entityId))
  }
}
