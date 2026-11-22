package net.bestia.zone.stronghold

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.worldgen.stronghold.EmpyriteSeed
import net.bestia.zone.ecs.core.testWorld
import org.junit.jupiter.api.Test

class SeedSurveysTest {

  private val inside = EmpyriteSeed(rank = 1, x = 2_900.0, y = 0.0, name = "Hochvein", settlement = -1)
  private val outside = EmpyriteSeed(rank = 2, x = 3_100.0, y = 0.0, name = "Goldgrund", settlement = -1)
  private val discoveries = mockk<EmpyriteSeedDiscoveries>(relaxed = true)

  private val surveys = SeedSurveys(seeds = mockk { every { awake() } returns listOf(inside, outside) }, discoveries)

  @Test
  fun `a survey discovers the seeds its chart covers for the surveyor`() {
    surveys.surveyed(testWorld(), ownerMasterId = 5L, centreX = 0.0, centreY = 0.0, radiusMetres = 3_000.0)

    verify { discoveries.discover(5L, 1) }
    verify(exactly = 0) { discoveries.discover(5L, 2) }
  }
}
