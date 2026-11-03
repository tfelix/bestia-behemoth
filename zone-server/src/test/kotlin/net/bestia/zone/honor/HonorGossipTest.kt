package net.bestia.zone.honor

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import net.bestia.worldgen.vector.Vec2d
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.townsfolk.knowledge.Knowledge
import net.bestia.zone.townsfolk.rumour.RumourKind
import net.bestia.zone.townsfolk.rumour.RumourService
import java.time.Clock
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals

class HonorGossipTest {

  private val rumours = mockk<RumourService>()
  private val fixture = HonorFixture()
  private val world = fixture.world
  private val clock = mockk<Clock>().also { every { it.instant() } answers { fixture.now } }
  private val gossip = HonorGossipSystem(
    fixture.catalog, fixture.service, fixture.localHonor, rumours, fixture.sites, fixture.worldService, clock,
  )

  private val killer = fixture.master(HERE)
  private val masterId = fixture.masterIdOf(killer)

  init {
    fixture.townsperson(HERE + Vec3L(5, 0, 0), settlement = VILLAGE)
    fixture.centres[VILLAGE] = Vec2d(10_000.0, 10_000.0)
    fixture.centres[NEIGHBOUR] = Vec2d(19_000.0, 10_000.0)
  }

  @Test
  fun `the town hears half of what the witness saw a Bestia day later`() {
    fixture.service.record(world, HonorAct.KILL_PLAYER, killer, HERE)

    fixture.advance(Duration.ofHours(7))
    gossip.update(world, 60f)
    assertEquals(10, fixture.service.settlementValue(masterId, VILLAGE), "not told yet")

    fixture.advance(Duration.ofHours(2))
    gossip.update(world, 60f)
    gossip.update(world, 60f)
    assertEquals(10 - 25, fixture.service.settlementValue(masterId, VILLAGE))
  }

  @Test
  fun `a town that will never forget warns its neighbours by name`() {
    val posted = slot<Map<String, Knowledge.Slot>>()
    every { rumours.post(RumourKind.MISDEED_SEEN, any(), any(), any(), capture(posted)) } returns listOf(VILLAGE, NEIGHBOUR)

    repeat(4) { fixture.service.record(world, HonorAct.KILL_NPC, killer, HERE) }
    fixture.advance(Duration.ofHours(9))
    gossip.update(world, 60f)

    verify(exactly = 1) { rumours.post(RumourKind.MISDEED_SEEN, 10_000, 10_000, any(), any()) }
    assertEquals(Knowledge.Slot.Name("Griefer1"), posted.captured["name"])
    // Half the reach away, so half the loss.
    assertEquals(10 - 15, fixture.service.settlementValue(masterId, NEIGHBOUR))
  }

  private companion object {
    val HERE = Vec3L(10_000, 10_000, 0)
    const val VILLAGE = HonorFixture.VILLAGE
    const val NEIGHBOUR = 4
  }
}
