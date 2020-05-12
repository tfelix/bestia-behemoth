package de.tfelix.bestia.worldgen.map

import com.nhaarman.mockitokotlin2.*
import de.tfelix.bestia.worldgen.noise.NoiseProvider
import org.junit.Assert
import org.junit.Test

class Map2DDiscreteCoordinateTest {

  @Test
  fun generate_providerGetsVisit() {
    val prov = mock<NoiseProvider> {  }
    whenever(prov.getRandom(any())).thenReturn(1337.0)

    val c = Map2DDiscreteCoordinate(10, 5)
    c.generate(prov)

    verify(prov, times(1)).getRandom(c)
  }

  @Test
  fun hashcode_2objects_different() {
    val m1 = Map2DDiscreteCoordinate(10, 1337)
    val m2 = Map2DDiscreteCoordinate(12, 1337)

    Assert.assertTrue(m1 == m1)
    Assert.assertTrue(m2 == m2)
    Assert.assertFalse(m1 == m2)
  }

  @Test
  fun hashcode_2objects_same() {
    val m1 = Map2DDiscreteCoordinate(12, 1337)
    val m2 = Map2DDiscreteCoordinate(12, 1337)

    Assert.assertTrue(m1 == m1)
    Assert.assertTrue(m2 == m2)
    Assert.assertTrue(m1 == m2)
  }

  @Test
  fun equals_2objects_different() {
    val m1 = Map2DDiscreteCoordinate(10, 1337)
    val m2 = Map2DDiscreteCoordinate(12, 1337)

    Assert.assertFalse(m1.hashCode() == m2.hashCode())
  }

  @Test
  fun equals_2objects_same() {
    val m1 = Map2DDiscreteCoordinate(12, 1337)
    val m2 = Map2DDiscreteCoordinate(12, 1337)

    Assert.assertTrue(m1.hashCode() == m2.hashCode())
  }
}
