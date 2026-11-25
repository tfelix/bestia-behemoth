package net.bestia.zone.worldevent.rare

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.worldgen.bio.Biome
import net.bestia.worldgen.climate.WeatherKind
import net.bestia.worldgen.place.Province
import net.bestia.worldgen.vector.Vec2d
import net.bestia.zone.bestia.BestiaCatalogue
import net.bestia.zone.bestia.persistence.Bestia
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.entity.ecs.EntityVisual
import net.bestia.zone.entity.ecs.EventCreature
import net.bestia.zone.entity.ecs.VisualKind
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.spawn.BestiaEntitySpawner
import net.bestia.zone.townsfolk.knowledge.Knowledge
import net.bestia.zone.townsfolk.rumour.RumourKind
import net.bestia.zone.townsfolk.rumour.RumourService
import net.bestia.zone.world.time.BestiaClock
import net.bestia.zone.world.time.BestiaDateTime
import net.bestia.zone.world.time.DayPart
import net.bestia.zone.worldevent.ProvinceSignals
import net.bestia.zone.worldevent.persistence.WorldEventInstance
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** A province of one marsh and one meadow; the Storm Heron comes to marshes in a thunderstorm at dusk. */
class RareBestiaEventTest {

  private val world = testWorld()
  private val view = mockk<WorldView> { every { read<Any?>(any()) } answers { firstArg<World.() -> Any?>().invoke(world) } }

  private val heron = RareBestia(
    "storm-heron", "storm_heron", biomes = setOf(Biome.SWAMP), weather = setOf(WeatherKind.THUNDERSTORM),
    dayParts = setOf(DayPart.DUSK),
  )
  private val rares = mockk<RareBestiaCatalogue> {
    every { all() } returns listOf(heron)
    every { ofSpecies("storm_heron") } returns heron
  }

  private val marsh = RareGround.Spot(0, 1_000.0, 1_000.0, Biome.SWAMP)
  private val meadow = RareGround.Spot(1, 5_000.0, 1_000.0, Biome.GRASSLAND)
  private val marshGround = Vec3L(1_000, 1_000, 12)
  private var weather = WeatherKind.THUNDERSTORM
  private val ground = mockk<RareGround> {
    every { spotsOf(listOf(0, 1)) } returns listOf(marsh, meadow)
    every { grounded(marsh) } returns marshGround
    every { grounded(meadow) } returns Vec3L(5_000, 1_000, 12)
    every { weatherAt(any()) } answers { weather }
  }

  private var now = BestiaDateTime(1, 1, 1, 21, 0, 0)
  private val clock = mockk<BestiaClock> { every { now() } answers { now } }

  private val species = Bestia(id = 117, identifier = "storm_heron", levelMin = 46, levelMax = 50, experienceReward = 1, health = 10, mana = 1)
  private val catalogue = mockk<BestiaCatalogue> {
    every { byIdentifier("storm_heron") } returns species
    every { find(117) } returns species
  }
  private val spawner = mockk<BestiaEntitySpawner> {
    every { spawnMob(any(), any<Long>(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } answers {
      world.createEntity { id -> add(id, EntityVisual(VisualKind.BESTIA, secondArg())) }
    }
  }
  private val rumours = mockk<RumourService>(relaxed = true)

  private val sut = RareBestiaEvent(rares, ground, clock, catalogue, spawner, rumours, mockk(relaxed = true), view)

  private val province = Province(0, "Fens", Province.Seat(Province.SeatKind.WILD, Vec2d(0.0, 0.0), 0), listOf(0, 1))
  private val signals = ProvinceSignals(province, false, null, 0.0, emptyMap(), 0)

  @Test
  fun `the heron comes down on the marsh in a storm at dusk and someone sees it`() {
    val started = sut.start(signals)!!

    assertEquals(marshGround, started.at)
    assertEquals(EventCreature("RareBestia", started.subjectId!!), world.get(started.subjectId!!, EventCreature::class))
    verify { spawner.spawnMob(world, 117L, marshGround, homePosition = marshGround) }
    verify { rumours.post(RumourKind.RARE_BESTIA_SEEN, 1_000, 1_000, any(), mapOf("rare" to Knowledge.Slot.Token("BESTIA_STORM_HERON"))) }
  }

  @Test
  fun `at noon or under a clear sky it does not come`() {
    now = BestiaDateTime(1, 1, 1, 12, 0, 0)
    assertFalse(sut.fits(signals))

    now = BestiaDateTime(1, 1, 1, 21, 0, 0)
    weather = WeatherKind.CLEAR
    assertFalse(sut.fits(signals))
    assertNull(sut.start(signals))
  }

  @Test
  fun `it leaves when dusk is over`() {
    val started = sut.start(signals)!!
    val instance = WorldEventInstance(eventId = "rare-bestia", x = started.at.x, y = started.at.y, z = started.at.z, subjectId = started.subjectId)

    assertFalse(sut.isOver(instance))
    now = BestiaDateTime(1, 1, 1, 22, 30, 0)
    assertTrue(sut.isOver(instance))
  }
}
