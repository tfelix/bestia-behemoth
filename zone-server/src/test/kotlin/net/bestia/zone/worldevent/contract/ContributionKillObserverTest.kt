package net.bestia.zone.worldevent.contract

import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.entity.ecs.EventCreature
import net.bestia.zone.entity.observe.KillFact
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.Master
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ContributionKillObserverTest {

  private val world = testWorld()
  private val ledger = ContributionLedger()
  private val sut = ContributionKillObserver(ledger)
  private val ada = world.createEntity { id -> add(id, Master(masterId = 1, name = "Ada")) }
  private val bo = world.createEntity { id -> add(id, Master(masterId = 2, name = "Bo")) }
  private val key = ContributionLedger.Key("Rift", 9)

  @Test
  fun `a kill of an event creature is split between who earned it, a boss counts five times`() {
    sut.onKill(world, KillFact(1, Vec3L(0, 0, 0), setOf(ada, bo), level = 30, boss = false, event = EventCreature("Rift", 9)))
    sut.onKill(world, KillFact(2, Vec3L(0, 0, 0), setOf(ada), level = 40, boss = true, event = EventCreature("Rift", 9)))

    val shares = ledger.sharesOf(key).associate { (who, share) -> who.masterId to share }
    assertEquals((15.0 + 200.0) / 230.0, shares.getValue(1), 1e-9)
    assertEquals(15.0 / 230.0, shares.getValue(2), 1e-9)
  }

  @Test
  fun `an ordinary kill counts for no event`() {
    sut.onKill(world, KillFact(1, Vec3L(0, 0, 0), setOf(ada), level = 30, boss = false))

    assertTrue(ledger.snapshot().isEmpty())
  }
}
