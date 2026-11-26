package net.bestia.zone.worldevent.raid

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.worldgen.place.Province
import net.bestia.worldgen.vector.Vec2d
import net.bestia.zone.bestia.BestiaCatalogue
import net.bestia.zone.bestia.persistence.Bestia
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.entity.ecs.EventCreature
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.honor.HonorAct
import net.bestia.zone.spawn.BestiaEntitySpawner
import net.bestia.zone.spawn.ecs.WildSpawnerService
import net.bestia.zone.townsfolk.rumour.RumourKind
import net.bestia.zone.townsfolk.rumour.RumourService
import net.bestia.zone.world.time.BestiaClock
import net.bestia.zone.world.time.BestiaDateTime
import net.bestia.zone.worldevent.ProvinceSignals
import net.bestia.zone.worldevent.persistence.WorldEventInstance
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** A town at the origin, its camp 1.5 km east on a hill, and the road robbed halfway. */
class TownRaidEventTest {

  private val world = testWorld()
  private val view = mockk<WorldView> { every { read<Any?>(any()) } answers { firstArg<World.() -> Any?>().invoke(world) } }

  private val brigand = Bestia(id = 120, identifier = "brigand", levelMin = 20, levelMax = 30, experienceReward = 1, health = 10, mana = 1)
  private val town = RaidTowns.RaidTown(index = 3, centre = Vec2d(0.0, 0.0), provinceIndex = 0)
  private val camp = RaidTowns.Camp(at = Vec3L(1_500, 0, 40), ambush = Vec3L(750, 0, 10))
  private val towns = mockk<RaidTowns> {
    every { targetIn(any(), 0) } returns town
    every { campFor(town) } returns camp
    every { townNearest(any(), any()) } returns Vec3L(0, 0, 10)
  }

  private val spawner = mockk<BestiaEntitySpawner> {
    every { spawnMob(any(), any<Long>(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } answers { world.createEntity { } }
  }
  private val band = RaidBand(
    mockk<WildSpawnerService> { every { dens } returns emptyList() },
    mockk<BestiaCatalogue> { every { byIdentifier("brigand") } returns brigand },
    mockk(relaxed = true),
    spawner,
    mockk(relaxed = true),
  )

  private val start = BestiaDateTime(1, 1, 1, 12, 0, 0)
  private var now = start
  private val clock = mockk<BestiaClock> { every { now() } answers { now } }
  private val rumours = mockk<RumourService>(relaxed = true)

  private val sut = TownRaidEvent(towns, band, rumours, clock, view)

  private val province = Province(0, "Downs", Province.Seat(Province.SeatKind.WILD, Vec2d(0.0, 0.0), 0), listOf(0))
  private val signals = ProvinceSignals(province, false, null, 0.0, emptyMap(), 0)

  @Test
  fun `the band robs the road and walks up to its camp`() {
    val started = sut.start(signals)!!
    val chief = started.subjectId!!

    assertEquals(camp.at, started.at)
    assertEquals(EventCreature("TownRaid", chief), world.get(chief, EventCreature::class))
    verify { spawner.spawnMob(world, 120, camp.ambush, homePosition = camp.at, level = 30 + 8) }
    verify(exactly = 5) { spawner.spawnMob(world, 120, camp.ambush, persistent = false, homePosition = camp.at, level = 30) }
    verify { rumours.post(RumourKind.ROAD_ROBBED, 750, 0, any()) }
  }

  @Test
  fun `a band killed in its camp ends the raid as outlaws caught`() {
    val instance = started()
    raiders().forEach(world::destroy)

    assertEquals(HonorAct.KILL_OUTLAW, sut.honorAct(instance))
    assertTrue(sut.isDefeated(instance))
    verify { rumours.post(RumourKind.RAIDERS_ROUTED, 1_500, 0, any()) }
  }

  @Test
  fun `after a day the band marches on the town, plunders it and leaves`() {
    val instance = started()

    now = BestiaDateTime(1, 1, 2, 12, 0, 0)
    assertFalse(sut.isOver(instance))
    assertFalse(sut.isOver(instance))
    verify(exactly = 1) { rumours.post(RumourKind.RAIDERS_MARCH, 0, 0, any()) }
    assertEquals(HonorAct.DEFEND_SETTLEMENT, sut.honorAct(instance))
    assertFalse(sut.isDefeated(instance), "the band still stands")

    now = BestiaDateTime(1, 1, 2, 15, 0, 0)
    assertTrue(sut.isOver(instance))
    assertTrue(raiders().isEmpty(), "the band left")
    verify { rumours.post(RumourKind.TOWN_RAIDED, 0, 0, any()) }
  }

  private fun raiders(): List<Long> {
    val found = ArrayList<Long>()
    world.query(EventCreature::class).each { id -> found += id }
    return found
  }

  private fun started(): WorldEventInstance {
    val started = sut.start(signals)!!
    return WorldEventInstance(
      eventId = "town-raid",
      startedAtSecond = start.absoluteSecond,
      x = started.at.x,
      y = started.at.y,
      z = started.at.z,
      subjectId = started.subjectId,
    )
  }
}
