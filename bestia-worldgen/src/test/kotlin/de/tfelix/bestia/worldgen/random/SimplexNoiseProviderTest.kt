package de.tfelix.bestia.worldgen.random

import de.tfelix.bestia.worldgen.map.Map2DDiscreteCoordinate
import org.junit.Assert
import org.junit.Test

class SimplexNoiseProviderTest {

  @Test
  fun getRandom_sameMap2DDiscreteCoordinate_staticValue() {
    val c = Map2DDiscreteCoordinate(10, 5)
    val p = SimplexNoiseProvider(1234)
    val rand1 = p.getRandom(c)
    val rand2 = p.getRandom(c)
    Assert.assertEquals(rand1, rand2, 0.0001)
  }

  @Test
  fun getRandom_differentMap2DCoordinate_randomValue() {
    val c1 = Map2DDiscreteCoordinate(10, 5)
    val c2 = Map2DDiscreteCoordinate(11, 5)

    val p = SimplexNoiseProvider(1234)
    val rand1 = p.getRandom(c1)
    val rand2 = p.getRandom(c2)
    Assert.assertNotEquals(rand1, rand2)
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
