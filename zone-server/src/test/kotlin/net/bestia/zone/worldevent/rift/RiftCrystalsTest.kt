package net.bestia.zone.worldevent.rift

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.zone.bestia.BestiaCatalogue
import net.bestia.zone.bestia.persistence.Bestia
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.entity.ecs.EventCreature
import net.bestia.zone.entity.observe.KillFact
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.spawn.BestiaEntitySpawner
import net.bestia.zone.world.mana.ManaField
import net.bestia.zone.world.time.BestiaClock
import net.bestia.zone.worldevent.EventGround
import net.bestia.zone.worldevent.contract.ContributionLedger
import net.bestia.zone.worldevent.persistence.StoredRift
import net.bestia.zone.worldevent.persistence.StoredRift.Phase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class RiftCrystalsTest {

  private val world = testWorld()
  private val crystal = Bestia(id = 121, identifier = "rift_crystal", levelMin = 10, levelMax = 50, experienceReward = 1, health = 10, mana = 0)
  private val spawner = mockk<BestiaEntitySpawner> {
    every { spawnMob(any(), any<Long>(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } answers { world.createEntity { } }
  }
  private val ground = mockk<EventGround> { every { grounded(any(), any()) } returns Vec3L(5_050, 5_000, 40) }
  private val clock = mockk<BestiaClock> { every { speedFactor } returns 3.0 }
  private val crystals = RiftCrystals(
    RiftConfig(), mockk<BestiaCatalogue> { every { byIdentifier("rift_crystal") } returns crystal }, spawner, ground, clock,
  )

  private val rift = StoredRift(x = 5_000.0, y = 5_000.0, phase = Phase.OPEN, stage = 2).also { it.id = 9; it.reservoir = 1_000.0 }
  private val ledger = ContributionLedger()
  private val harvest = RiftCrystalHarvest(
    mockk<Rifts> { every { active() } returns listOf(rift) }, crystals, ManaField(), RiftConfig(), ledger,
  )

  @Test
  fun `an open rift grows a crystal now and then, one per stage`() {
    val regrow = 10 * 60 * 3L

    crystals.tend(world, listOf(rift), now = 0)
    crystals.tend(world, listOf(rift), now = regrow - 1)
    crystals.tend(world, listOf(rift), now = regrow)
    crystals.tend(world, listOf(rift), now = 2 * regrow)

    verify(exactly = 2) { spawner.spawnMob(world, 121, any(), any(), any(), false, any(), any(), any(), 20, any()) }
    assertEquals(2, crystalsAlive())
  }

  @Test
  fun `a broken crystal takes a share of the reservoir and is work for the rift`() {
    val ada = world.createEntity { id -> add(id, Master(masterId = 1, name = "Ada")) }

    harvest.onKill(world, KillFact(121, Vec3L(5_050, 5_000, 40), setOf(ada), 20, false, EventCreature("Rift", 9)))

    assertEquals(950.0, rift.reservoir, 1e-9)
    val ada1 = ledger.sharesOf(ContributionLedger.Key("Rift", 9)).single().first
    assertEquals(10.0, ada1.points.getValue(ContributionLedger.Axis.WORK), 1e-9)
  }

  @Test
  fun `a wave bestia of the rift drains nothing`() {
    harvest.onKill(world, KillFact(1, Vec3L(5_050, 5_000, 40), emptySet(), 20, false, EventCreature("Rift", 9)))

    assertEquals(1_000.0, rift.reservoir, 1e-9)
  }

  @Test
  fun `the crystals crumble when the rift closes`() {
    crystals.tend(world, listOf(rift), now = 0)
    rift.phase = Phase.SEALED

    crystals.tend(world, listOf(rift), now = 1)

    assertFalse(crystalsAlive() > 0)
  }

  private fun crystalsAlive(): Int {
    var count = 0
    world.query(EventCreature::class).each { _ -> count++ }
    return count
  }
}
