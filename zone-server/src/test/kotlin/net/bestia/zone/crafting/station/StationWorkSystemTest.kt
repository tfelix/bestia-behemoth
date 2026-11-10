package net.bestia.zone.crafting.station

import net.bestia.zone.prop.station.QueueEntry
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.zone.crafting.CraftingService
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.crafting.station.StationFixture.Companion.ACCOUNT
import net.bestia.zone.crafting.station.StationFixture.Companion.OWNER
import net.bestia.zone.crafting.station.StationFixture.Companion.PLANK
import net.bestia.zone.crafting.station.StationFixture.Companion.RECIPE
import net.bestia.zone.crafting.station.StationFixture.Companion.STATION
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class StationWorkSystemTest {

  private val now = Instant.parse("2026-11-10T12:00:00Z")
  private val connections = ConnectionInfoService()
  private val crafting = mockk<CraftingService>().also {
    every { it.craftSecondsFor(any(), any(), any(), any()) } returns 4f
    every { it.produceFromStation(any(), any(), any(), any()) } returns null
  }
  private val work = StationWork()

  @Test
  fun `the owner next to the station makes one item at a time from what it holds`() {
    val fixture = queued(amount = 2)
    val owner = online(fixture, fixture.givenOwner(planks = 0))

    system(fixture).update(fixture.world, 4f)

    verify(exactly = 1) { crafting.produceFromStation(fixture.world, owner, owner, fixture.recipe) }
    assertEquals(listOf(QueueEntry(RECIPE, 2, done = 1)), fixture.station.queue)
    assertEquals(mapOf(PLANK to 2), fixture.station.held)
  }

  @Test
  fun `the last item of an entry takes it off the queue`() {
    val fixture = queued(amount = 1)
    online(fixture, fixture.givenOwner(planks = 0))

    system(fixture).update(fixture.world, 4f)

    assertTrue(fixture.station.queue.isEmpty())
    assertTrue(fixture.station.held.isEmpty())
  }

  @Test
  fun `a worker walking away pauses the queue and keeps the ingredients`() {
    val fixture = queued(amount = 2)
    online(fixture, fixture.givenOwner(planks = 0, at = Vec3L(20, 0, 0)))

    system(fixture).update(fixture.world, 4f)

    assertEquals(StationPause.WORKER_AWAY, work.of(STATION).pause)
    assertEquals(mapOf(PLANK to 4), fixture.station.held)
  }

  @Test
  fun `an owner who is not playing works nothing`() {
    val fixture = queued(amount = 2)
    fixture.givenOwner(planks = 0)

    system(fixture).update(fixture.world, 4f)

    assertEquals(StationPause.WORKER_OFFLINE, work.of(STATION).pause)
  }

  @Test
  fun `a bag with no room for the item pauses rather than dropping it`() {
    val fixture = queued(amount = 2)
    online(fixture, fixture.givenOwner(planks = 0, maxWeight = 5))

    system(fixture).update(fixture.world, 4f)

    assertEquals(StationPause.BAG_FULL, work.of(STATION).pause)
  }

  @Test
  fun `a campfire that went out pauses the queue`() {
    val fixture = queued(amount = 2, StationFixture(StaticEntityKind.CAMPFIRE, burnsOutAt = now.minusSeconds(1)))
    online(fixture, fixture.givenOwner(planks = 0))

    system(fixture).update(fixture.world, 4f)

    assertEquals(StationPause.STATION_OUT, work.of(STATION).pause)
  }

  private fun queued(amount: Int, fixture: StationFixture = StationFixture()): StationFixture {
    fixture.station = fixture.station.copy(queue = listOf(QueueEntry(RECIPE, amount)), held = mapOf(PLANK to amount * 2))
    return fixture
  }

  private fun online(fixture: StationFixture, master: EntityId): EntityId {
    connections.activateSession(ACCOUNT, masterId = OWNER, masterEntityId = master)
    return master
  }

  private fun system(fixture: StationFixture): StationWorkSystem {
    return StationWorkSystem(
      structures = fixture.structures,
      recipes = fixture.recipes,
      crafting = crafting,
      itemTemplates = fixture.itemTemplates,
      connections = connections,
      work = work,
      states = mockk(relaxed = true),
      clock = Clock.fixed(now, ZoneOffset.UTC)
    )
  }
}
