package net.bestia.zone.environment.weather

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.worldgen.climate.Temperature
import net.bestia.worldgen.climate.WeatherKind
import net.bestia.worldgen.climate.WeatherState
import net.bestia.zone.skill.SkillRepository
import net.bestia.zone.socket.OutMessageHandler
import net.bestia.zone.world.stream.ChunkService
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The dedup contract, which is what lets `SelectMasterHandler` and [WeatherSystem] both publish.
 *
 * Neither caller is exercised here - each is a single call into [WeatherPublisher.publish], and what matters
 * is that the second of those calls stays silent when the sky has not moved.
 */
class WeatherPublisherTest {

  private val accountId = 7L
  private val voxelX = 1_000L
  private val voxelY = 2_000L

  private val out = mockk<OutMessageHandler>(relaxed = true)

  /** A publisher whose world hands back [states] in order, one per call to `publish`. */
  private fun publisherSeeing(vararg states: WeatherState): WeatherPublisher {
    val weatherService = mockk<WeatherService> {
      every { at(any(), any(), any()) } returnsMany states.map {
        WeatherAt(regionId = 3, state = it, temperature = Temperature(12.0))
      }
    }
    val chunkService = mockk<ChunkService> {
      every { surfaceElevationAt(any(), any()) } returns 100.0
    }
    // No WEATHER_SENSE in the catalogue, so no forecast is ever asked for.
    val skills = mockk<SkillRepository> { every { findByIdentifier(any()) } returns null }

    return WeatherPublisher(weatherService, chunkService, out, WeatherConfig(), skills)
  }

  private fun sky(kind: WeatherKind, cloud: Double = 0.1, intensity: Double = 0.0) = WeatherState(
    kind = kind,
    intensity = intensity,
    cloudCover = cloud,
    windSpeed = 2.0,
    windDirection = 0.0
  )

  @Test
  fun `the first call sends and an identical second call does not`() {
    val publisher = publisherSeeing(sky(WeatherKind.CLEAR), sky(WeatherKind.CLEAR))

    assertTrue(publisher.publish(accountId, voxelX, voxelY, 0), "the first message must always go out")
    assertFalse(publisher.publish(accountId, voxelX, voxelY, 0), "an unchanged sky must not be re-sent")

    verify(exactly = 1) { out.sendMessage(accountId, any()) }
  }

  @Test
  fun `a kind change sends again`() {
    val publisher = publisherSeeing(
      sky(WeatherKind.CLEAR),
      sky(WeatherKind.RAIN, intensity = 0.4)
    )

    assertTrue(publisher.publish(accountId, voxelX, voxelY, 0))
    assertTrue(publisher.publish(accountId, voxelX, voxelY, 0), "rain is not a clear sky")

    verify(exactly = 2) { out.sendMessage(accountId, any()) }
  }

  @Test
  fun `a drift smaller than the band stays quiet`() {
    val config = WeatherConfig()
    val publisher = publisherSeeing(
      sky(WeatherKind.CLEAR, cloud = 0.10),
      sky(WeatherKind.CLEAR, cloud = 0.10 + config.cloudBand / 2)
    )

    assertTrue(publisher.publish(accountId, voxelX, voxelY, 0))
    assertFalse(publisher.publish(accountId, voxelX, voxelY, 0), "half a band is not a change")

    verify(exactly = 1) { out.sendMessage(accountId, any()) }
  }

  @Test
  fun `forgetting an account makes the next call send again`() {
    val publisher = publisherSeeing(
      sky(WeatherKind.CLEAR), sky(WeatherKind.CLEAR), sky(WeatherKind.CLEAR)
    )

    assertTrue(publisher.publish(accountId, voxelX, voxelY, 0))
    assertFalse(publisher.publish(accountId, voxelX, voxelY, 0))

    // What WeatherSystem does for an account that no longer has an active entity.
    publisher.retainOnly(emptySet())

    assertTrue(publisher.publish(accountId, voxelX, voxelY, 0), "a forgotten account is a fresh one")
  }

  @Test
  fun `a column with no surface is skipped rather than invented`() {
    val weatherService = mockk<WeatherService>()
    val chunkService = mockk<ChunkService> {
      every { surfaceElevationAt(any(), any()) } returns null
    }
    val skills = mockk<SkillRepository> { every { findByIdentifier(any()) } returns null }
    val publisher = WeatherPublisher(weatherService, chunkService, out, WeatherConfig(), skills)

    assertFalse(publisher.publish(accountId, voxelX, voxelY, 0))

    verify(exactly = 0) { out.sendMessage(any(), any()) }
  }

  @Test
  fun `disabled weather sends nothing at all`() {
    val weatherService = mockk<WeatherService>()
    val chunkService = mockk<ChunkService>()
    val skills = mockk<SkillRepository> { every { findByIdentifier(any()) } returns null }
    val publisher = WeatherPublisher(
      weatherService, chunkService, out, WeatherConfig(enabled = false), skills
    )

    assertFalse(publisher.publish(accountId, voxelX, voxelY, 0))

    verify(exactly = 0) { out.sendMessage(any(), any()) }
  }
}
