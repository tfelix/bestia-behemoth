package net.bestia.zone.crafting.station

import net.bestia.zone.bestia.diary.BestiaHappened
import net.bestia.zone.bestia.diary.BestiaNews
import net.bestia.zone.prop.station.QueueEntry
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.zone.crafting.CraftingService
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.prop.CampfireService
import net.bestia.zone.crafting.station.StationFixture.Companion.ACCOUNT
import net.bestia.zone.crafting.station.StationFixture.Companion.OWNER
import net.bestia.zone.crafting.station.StationFixture.Companion.PLANK
import net.bestia.zone.crafting.station.StationFixture.Companion.RECIPE
import net.bestia.zone.crafting.station.StationFixture.Companion.STATION
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.prop.station.WorkAssignment
import net.bestia.zone.util.EntityId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class StationWorkSystemTest {

  private val now = Instant.parse("2026-11-10T12:00:00Z")
  private val connections = ConnectionInfoService()
  private val crafting = mockk<CraftingService>().also {
    every { it.craftSecondsFor(any(), any()) } returns 4f
    every { it.produceFromStation(any(), any(), any(), any()) } returns null
  }
  private val profiles = mockk<CrafterProfiles>(relaxed = true)
  private val campfires = mockk<CampfireService>(relaxed = true)
  private val work = StationWork()
  private val told = mutableListOf<BestiaHappened>()

  @Test
  fun `the owner next to the station makes one item at a time from what it holds`() {
    val fixture = queued(amount = 2)
    val owner = online(fixture, fixture.givenOwner(planks = 0))

    system(fixture).update(fixture.world, 4f)

    verify(exactly = 1) { crafting.produceFromStation(fixture.world, owner, any(), fixture.recipe) }
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

  @Test
  fun `a station waiting for a free crafter is not worked by its owner`() {
    val fixture = queued(amount = 2)
    fixture.station = fixture.station.copy(takesAnyCrafter = true)
    online(fixture, fixture.givenOwner(planks = 0))

    system(fixture).update(fixture.world, 4f)

    assertEquals(StationPause.NO_FREE_CRAFTER, work.of(STATION).pause)
    assertEquals(mapOf(PLANK to 4), fixture.station.held)
  }

  @Test
  fun `a free crafter whose station has nothing left to do is let go, a picked bestia is not`() {
    val fixture = StationFixture()
    val bestia = fixture.givenBestia()
    fixture.workers.assign(fixture.world, bestia, fixture.station)

    system(fixture).update(fixture.world, 1f)
    assertEquals(STATION, fixture.world.get(bestia, WorkAssignment::class)?.structureId)

    fixture.station = fixture.station.copy(takesAnyCrafter = true)
    system(fixture).update(fixture.world, 1f)

    assertNull(fixture.world.get(bestia, WorkAssignment::class))
    assertTrue(fixture.station.waitsForCrafter)
  }

  @Test
  fun `a bestia working a campfire keeps it burning, its owner adds wood by hand`() {
    val fixture = queued(amount = 2, StationFixture(StaticEntityKind.CAMPFIRE, burnsOutAt = now.plusSeconds(60)))
    online(fixture, fixture.givenOwner(planks = 0))

    system(fixture).update(fixture.world, 1f)
    verify(exactly = 0) { campfires.addWoodFromChest(any(), any()) }

    fixture.workers.assign(fixture.world, fixture.givenBestia(), fixture.station)
    system(fixture).update(fixture.world, 1f)
    verify { campfires.addWoodFromChest(fixture.world, STATION) }
  }

  /** The whole point of a bestia worker: the queue goes on with nobody playing. */
  @Test
  fun `a bestia keeps working while its master is logged out`() {
    val fixture = queued(amount = 2)
    val bestia = fixture.givenBestia()
    fixture.workers.assign(fixture.world, bestia, fixture.station)
    every { profiles.crafterFor(fixture.world, bestia, OWNER) } returns
      fixture.crafters.Crafter(Master(OWNER, "Joiner"), StationFixture.PLAYER_BESTIA, null, null)

    system(fixture).update(fixture.world, 4f)

    verify { crafting.produceFromStation(fixture.world, bestia, any(), fixture.recipe) }
    assertEquals(listOf(QueueEntry(RECIPE, 2, done = 1)), fixture.station.queue)
  }

  @Test
  fun `a bestia that finishes the queue tells its master's diary`() {
    val fixture = queued(amount = 1)
    givenBestiaWorker(fixture)

    system(fixture).update(fixture.world, 4f)

    assertEquals(listOf(BestiaHappened.Kind.QUEUE_FINISHED), told.map { it.kind })
    assertEquals(StationFixture.PLAYER_BESTIA, told.single().playerBestiaId)
  }

  @Test
  fun `a fire going out under a bestia is told once, a full bag not at all`() {
    val fixture = queued(amount = 2, StationFixture(StaticEntityKind.CAMPFIRE, burnsOutAt = now.minusSeconds(1)))
    givenBestiaWorker(fixture)

    system(fixture).update(fixture.world, 4f)
    system(fixture).update(fixture.world, 4f)

    assertEquals(listOf("STATION_OUT"), told.map { it.reason })
  }

  private fun givenBestiaWorker(fixture: StationFixture) {
    val bestia = fixture.givenBestia()
    fixture.workers.assign(fixture.world, bestia, fixture.station)
    every { profiles.crafterFor(fixture.world, bestia, OWNER) } returns
      fixture.crafters.Crafter(Master(OWNER, "Joiner"), StationFixture.PLAYER_BESTIA, null, null)
  }

  @Test
  fun `a bestia worker waits while what its absent master knows is loading`() {
    val fixture = queued(amount = 2)
    val bestia = fixture.givenBestia()
    fixture.workers.assign(fixture.world, bestia, fixture.station)
    every { profiles.crafterFor(any(), any(), any()) } returns null

    system(fixture).update(fixture.world, 4f)

    assertEquals(StationPause.WORKER_OFFLINE, work.of(STATION).pause)
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
      crafters = fixture.crafters,
      profiles = profiles,
      workers = fixture.workers,
      itemTemplates = fixture.itemTemplates,
      connections = connections,
      work = work,
      states = mockk(relaxed = true),
      campfires = campfires,
      clock = Clock.fixed(now, ZoneOffset.UTC),
      news = BestiaNews { told += it },
    )
  }
}
