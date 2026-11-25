package net.bestia.zone.worldevent.apex

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.bio.Biome
import net.bestia.worldgen.place.PlaceRegion
import net.bestia.worldgen.place.RegionKind
import net.bestia.worldgen.place.Province
import net.bestia.worldgen.vector.Vec2d
import net.bestia.zone.bestia.BestiaCatalogue
import net.bestia.zone.bestia.persistence.Bestia
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.place.ecs.PlaceRegionService
import net.bestia.zone.place.ecs.ProvinceService
import net.bestia.zone.spawn.BestiaEntitySpawner
import net.bestia.zone.spawn.ecs.DenIdentity
import net.bestia.zone.spawn.ecs.WildSpawnerService
import net.bestia.zone.world.WorldService
import net.bestia.zone.world.mana.ManaField.Cell
import net.bestia.zone.world.mana.ManaTier
import net.bestia.zone.world.mana.ManaTiers
import net.bestia.zone.world.stream.ChunkService
import net.bestia.zone.worldevent.ProvinceSignals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ApexRoamerEventTest {

  private val world = testWorld()
  private val view = mockk<WorldView> { every { read<Any?>(any()) } answers { firstArg<World.() -> Any?>().invoke(world) } }

  private val province = Province(0, "Fells", Province.Seat(Province.SeatKind.WILD, Vec2d(0.0, 0.0), 0), listOf(0, 1))
  private val near = region(0, Vec2d(1_000.0, 1_000.0))
  private val far = region(1, Vec2d(9_000.0, 1_000.0))

  private val wolf = species(1, "wolf", level = 20)
  private val bear = species(2, "bear", level = 35)
  private val lord = species(3, "lord", level = 60).apply { boss = true }

  private val provinces = mockk<ProvinceService> { every { provinceAt(any(), any()) } returns province }
  private val regions = mockk<PlaceRegionService> { every { regions.regions } returns listOf(near, far) }
  private val dens = mockk<WildSpawnerService> {
    every { dens } returns listOf(wolf, bear, lord).map {
      WildSpawnerService.Den(DenIdentity(it.id, 1, 1), it.id, Vec3L(500, 500, 0), pack = 3, range = 10, activationRange = 60)
    }
  }
  private val catalogue = mockk<BestiaCatalogue> {
    every { find(any()) } answers { listOf(wolf, bear, lord).first { it.id == firstArg<Long>() } }
  }
  private val spawner = mockk<BestiaEntitySpawner> {
    every { spawnMob(any(), any<Long>(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns 77L
  }
  private val tiers = mockk<ManaTiers> { every { tierOf(any()) } returns ManaTier.OMINOUS }
  private val chunks = mockk<ChunkService> { every { surfaceElevationAt(any(), any()) } returns 40.0 }
  private val worldService = mockk<WorldService> { every { config } returns WorldConfig(seed = 1, widthCells = 128, heightCells = 128) }

  private val sut = ApexRoamerEvent(
    provinces, regions, dens, catalogue, spawner, tiers, chunks, worldService, mockk(relaxed = true), view,
  )

  @Test
  fun `the strongest beast of the province's dens comes in from the far side and makes for the mana`() {
    val started = sut.start(signals())!!

    assertEquals(77L, started.subjectId)
    assertEquals(9_000L, started.at.x)
    verify {
      spawner.spawnMob(
        world, bear.id, Vec3L(9_000, 1_000, started.at.z),
        homePosition = Vec3L(1_280 + 64, 1_280 + 64, started.at.z),
        level = 35 + 5,
        manaTier = ManaTier.OMINOUS,
      )
    }
  }

  private fun signals() = ProvinceSignals(province, false, Cell(10, 10), 0.6, mapOf(ManaTier.OMINOUS to 20), 0)

  private fun species(id: Long, identifier: String, level: Int) =
    Bestia(id = id, identifier = identifier, levelMin = level, levelMax = level, experienceReward = 1, health = 10, mana = 1)

  private fun region(index: Int, centre: Vec2d) = PlaceRegion(
    index = index, seed = centre, centre = centre, cellCount = 30, kind = RegionKind.entries.first(), name = "R$index",
    nameSeed = 0, cultureIndex = -1, isWater = false, landShare = 1.0, meanElevation = 40.0, relief = 100.0,
    dominantBiome = Biome.GRASSLAND,
  )
}
