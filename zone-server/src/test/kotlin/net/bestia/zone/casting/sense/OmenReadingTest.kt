package net.bestia.zone.casting.sense

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import net.bestia.worldgen.bio.Biome
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.place.PlaceRegion
import net.bestia.worldgen.place.PlaceRegions
import net.bestia.worldgen.place.RegionKind
import net.bestia.worldgen.vector.Vec2d
import net.bestia.zone.casting.sense.OmenReading.Reading
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.identity.ecs.Account
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.place.ecs.PlaceRegionService
import net.bestia.zone.world.WorldService
import net.bestia.zone.world.mana.ManaConcentrationService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Two regions side by side: a calm one the caster stands in, and an ominous one to the east. */
class OmenReadingTest {

  private val world = testWorld()
  private val here = region(0, "Sube Vale", Vec2d(1_000.0, 1_000.0))
  private val east = region(1, "Cold Waste", Vec2d(5_000.0, 1_000.0))

  private val regions = mockk<PlaceRegions> {
    every { regionAt(any(), any()) } answers { if (firstArg<Double>() < SPLIT) here else east }
    every { indexAt(any(), any()) } answers { if (firstArg<Double>() < SPLIT) 0 else 1 }
    every { neighboursOf(0) } returns intArrayOf(1)
    every { this@mockk.regions } returns listOf(here, east)
    every { cellSize } returns 1_000.0
  }
  private val placeRegions = mockk<PlaceRegionService> { every { regions } returns this@OmenReadingTest.regions }
  private val mana = mockk<ManaConcentrationService> {
    every { densityAt(any()) } answers { if (firstArg<Vec3L>().x < SPLIT) 0.2 else 0.6 }
  }
  private val worldService = mockk<WorldService> { every { config } returns WorldConfig(seed = 1, widthCells = 128, heightCells = 128) }
  private val messages = mockk<OutMessageProcessor>(relaxed = true)
  private var events = emptyList<Pair<Double, Double>>()

  private val sut = OmenReading(placeRegions, mana, worldService, messages) { events }

  private val caster = world.createEntity { id ->
    add(id, Account(accountId = 7))
    add(id, Position.fromVec3(Vec3L(1_000, 1_000, 0)))
  }

  @Test
  fun `level 1 reads only the caster's own region`() {
    assertEquals(listOf("Sube Vale" to Reading.CALM), readings(level = 1))
  }

  @Test
  fun `level 2 also says a neighbour is worse, without naming it`() {
    assertEquals(listOf("Sube Vale" to Reading.CALM, "" to Reading.WORSE_NEARBY), readings(level = 2))
  }

  @Test
  fun `level 3 reads every neighbour by name`() {
    assertEquals(
      listOf("Sube Vale" to Reading.CALM, "" to Reading.WORSE_NEARBY, "Cold Waste" to Reading.OMINOUS),
      readings(level = 3),
    )
  }

  @Test
  fun `an event running in the region makes it read ominous`() {
    events = listOf(1_200.0 to 900.0)

    assertEquals(listOf("Sube Vale" to Reading.OMINOUS), readings(level = 1))
  }

  private fun readings(level: Int): List<Pair<String, Reading>> {
    sut.read(world, caster, level)
    val sent = slot<SenseMarksSMSG>()
    verify { messages.sendToPlayer(7, capture(sent)) }
    return sent.captured.marks.map { it.label to Reading.entries[it.refId.toInt()] }
  }

  private fun region(index: Int, name: String, centre: Vec2d) = PlaceRegion(
    index = index, seed = centre, centre = centre, cellCount = 4, kind = RegionKind.entries.first(), name = name,
    nameSeed = 0, cultureIndex = -1, isWater = false, landShare = 1.0, meanElevation = 40.0, relief = 100.0,
    dominantBiome = Biome.GRASSLAND,
  )

  private companion object {
    const val SPLIT = 3_000.0
  }
}
