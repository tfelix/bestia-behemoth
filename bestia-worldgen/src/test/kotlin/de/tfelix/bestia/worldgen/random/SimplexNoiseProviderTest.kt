package de.tfelix.bestia.worldgen.random

import de.tfelix.bestia.worldgen.map.Point
import de.tfelix.bestia.worldgen.noise.OpenSimplexNoise
import de.tfelix.bestia.worldgen.noise.SimplexNoiseProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class SimplexNoiseProviderTest {

  @Test
  fun getRandom_sameMap2DDiscreteCoordinate_staticValue() {
    val c = Point(10, 5, z = 0)
    val p = SimplexNoiseProvider(1234)
    val rand1 = p.getRandom(c)
    val rand2 = p.getRandom(c)
    assertEquals(rand1, rand2, 0.0001)
  }

  @Test
  fun getRandom_differentMap2DCoordinate_randomValue() {
    val c1 = Point(10, 5, z = 0)
    val c2 = Point(11, 5, z = 0)

    val p = SimplexNoiseProvider(1234)
    val rand1 = p.getRandom(c1)
    val rand2 = p.getRandom(c2)
    assertNotEquals(rand1, rand2)
  }

  @Test
  fun rangeTest() {
    val simplexNoise = OpenSimplexNoise(123)

    var i = 0
    while (i++ < 100) {
      println((simplexNoise.eval((i + 1).toDouble(), (i + 3).toDouble()) + 1) / 2)
    }
  }
}
