package net.bestia.zone.weather

import io.mockk.every
import io.mockk.mockk
import net.bestia.worldgen.climate.Temperature
import net.bestia.worldgen.core.WorldConfig
import net.bestia.zone.battle.ecs.status.Health
import net.bestia.zone.battle.ecs.status.Stamina
import net.bestia.zone.config.WorldRulesConfig
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.entity.ecs.Dead
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.stream.ChunkService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EnvironmentalExposureSystemTest {

  private val exposure = EnvironmentalExposureSystem(
    weatherService = deepColdEverywhere(),
    chunkService = readyChunks(),
    config = ExposureConfig(),
    skills = mockk(relaxed = true),
    zoneConfig = WorldRulesConfig(tickRate = 20),
  )

  private val world = testWorld(systems = listOf(exposure))

  @Test
  fun `an exhausted traveller left in the cold freezes to death`() {
    val traveller = exhausted()

    exposeFor(periods = HEALTH)

    assertEquals(0, world.getOrThrow(traveller, Health::class).current)
    assertTrue(world.has(traveller, Dead::class), "frozen to 0 HP but never marked dead, so no death path runs")
  }

  @Test
  fun `and the cold does not kill the body a second time`() {
    val traveller = exhausted()
    exposeFor(periods = HEALTH)
    world.getOrThrow(traveller, Dead::class).resolved = true

    exposeFor(periods = 1)

    assertTrue(world.getOrThrow(traveller, Dead::class).resolved, "the death penalty would be charged again")
  }

  private fun exhausted(): EntityId {
    return world.createEntity { id ->
      add(id, Position.fromVec3(Vec3L(10, 10, 0)))
      add(id, Health(HEALTH, HEALTH))
      add(id, Stamina(current = 0, max = 10))
    }
  }

  /** Each period weighs every entity exactly once. */
  private fun exposeFor(periods: Int) {
    repeat(periods * exposure.periodTicks.toInt()) {
      world.tick(DELTA)
    }
  }

  /** Far below the comfort band, whatever it is tuned to. */
  private fun deepColdEverywhere(): WeatherService {
    val weather = mockk<WeatherService>()
    every { weather.at(any(), any(), any()) } returns WeatherAt(
      regionId = 0,
      state = mockk(relaxed = true),
      temperature = Temperature(airCelsius = -40.0)
    )

    return weather
  }

  private fun readyChunks(): ChunkService {
    val chunks = mockk<ChunkService>()
    every { chunks.isReady } returns true
    every { chunks.config } returns WorldConfig(seed = 1L)

    return chunks
  }

  private companion object {
    /** Each period costs at least one point of health, so this many periods kill at any tuning. */
    const val HEALTH = 3
    const val DELTA = 0.05f
  }
}
