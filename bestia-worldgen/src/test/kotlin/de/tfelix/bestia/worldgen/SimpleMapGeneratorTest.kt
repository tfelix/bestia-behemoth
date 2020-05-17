package de.tfelix.bestia.worldgen

import de.tfelix.bestia.worldgen.map.Map2DChunk
import de.tfelix.bestia.worldgen.map.Point
import org.junit.jupiter.api.Test

class SimpleMapGeneratorTest {

  /**
   * Test is currently disabled until the problem with non termination is solved.
   */
  @Test
  fun `general test`() {
    val generator = WorldGeneratorClient(ExampleWorkloadFactory())
    val chunk = Map2DChunk(chunkPos = Point(0, 0, 0), width = 500, height = 500)
    generator.executeWorkload("generate-noise", chunk)
    // generator.executeWorkload("add-height", chunk)
  }
}