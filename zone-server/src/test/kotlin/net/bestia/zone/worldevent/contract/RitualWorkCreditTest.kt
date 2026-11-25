package net.bestia.zone.worldevent.contract

import io.mockk.every
import io.mockk.mockk
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.worldevent.persistence.StoredRift
import net.bestia.zone.worldevent.persistence.StoredRift.Phase
import net.bestia.zone.worldevent.rift.RiftConfig
import net.bestia.zone.worldevent.rift.Rifts
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RitualWorkCreditTest {

  private val world = testWorld()
  private val ledger = ContributionLedger()
  private val rift = StoredRift(x = 1_000.0, y = 1_000.0, phase = Phase.OPEN).also { it.id = 7 }
  private val rifts = mockk<Rifts> { every { active() } returns listOf(rift) }
  private val sut = RitualWorkCredit(rifts, RiftConfig(), ledger)

  private val ada = world.createEntity { id -> add(id, Master(masterId = 1, name = "Ada")) }
  private val bestia = world.createEntity { }

  @Test
  fun `a ritual on a rift's ground is work for every master who stayed`() {
    sut.onRitualCompleted(world, Vec3L(1_500, 1_000, 0), listOf(ada, bestia))

    val shares = ledger.sharesOf(ContributionLedger.Key("Rift", 7))
    assertEquals(listOf(1L), shares.map { it.first.masterId })
  }

  @Test
  fun `a ritual far from any rift is nobody's work`() {
    sut.onRitualCompleted(world, Vec3L(9_000, 9_000, 0), listOf(ada))

    assertTrue(ledger.snapshot().isEmpty())
  }
}
