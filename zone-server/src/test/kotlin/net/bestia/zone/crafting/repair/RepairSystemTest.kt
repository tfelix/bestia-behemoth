package net.bestia.zone.crafting.repair

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.zone.battle.ecs.status.Health
import net.bestia.zone.crafting.repair.ecs.RepairSystem
import net.bestia.zone.crafting.repair.ecs.Repairing
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.prop.PlayerStructureRegistry
import net.bestia.zone.prop.ecs.wear.StructureWear
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RepairSystemTest {

  private val at = Vec3L(10, 10, 0)
  private val world = testWorld()
  private val structures = mockk<PlayerStructureRegistry>(relaxed = true)
  private val repairs = mockk<RepairService> {
    every { isInReach(any(), any(), any(), any()) } returns true
    every { isInStandingTown(any()) } returns true
    every { costOf(any(), any(), any()) } returns RepairCost(800, 1800f, mapOf(1L to 40))
  }
  private val system = RepairSystem(repairs, structures)

  @Test
  fun `work restores health at half the build time for a full repair and uses up the stock`() {
    val building = world.createEntity { id ->
      add(id, Health(current = 500, max = 800))
      add(id, StructureWear())
    }
    every { repairs.targetOf(any(), building) } returns RepairService.Target.Building(at, 42L, stockHp = 100)
    val worker = spawnWorker(building)

    system.update(world, 10f)

    assertEquals(508, world.get(building, Health::class)?.current)
    verify { structures.setWear(42L, 292, 92) }
    assertEquals(building, world.get(worker, Repairing::class)?.targetEntityId)
  }

  @Test
  fun `work stops when the paid-in stock runs out`() {
    val building = world.createEntity { id ->
      add(id, Health(current = 500, max = 800))
      add(id, StructureWear())
    }
    every { repairs.targetOf(any(), building) } returns RepairService.Target.Building(at, 42L, stockHp = 5)
    val worker = spawnWorker(building)

    system.update(world, 10f)

    assertEquals(505, world.get(building, Health::class)?.current)
    assertNull(world.get(worker, Repairing::class))
  }

  @Test
  fun `the sign needs no stock`() {
    val sign = world.createEntity { id -> add(id, Health(current = 1_000, max = 2_000)) }
    every { repairs.targetOf(any(), sign) } returns RepairService.Target.Sign(at, 4L)
    every { repairs.costOf(any(), sign, any()) } returns RepairCost(2_000, 400f, emptyMap())
    spawnWorker(sign)

    system.update(world, 2f)

    assertEquals(1_020, world.get(sign, Health::class)?.current)
  }

  private fun spawnWorker(target: Long): Long {
    return world.createEntity { id ->
      add(id, Position.fromVec3(at))
      add(id, Repairing(target))
    }
  }
}
