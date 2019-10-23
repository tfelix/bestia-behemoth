package de.tfelix.bestia.worldgen.random

import de.tfelix.bestia.worldgen.map.MapCoordinate
import org.junit.Assert
import org.junit.Test
import org.mockito.Mockito.mock
import java.util.*

class NoiseVectorTest {

  @Test(expected = NullPointerException::class)
  fun ctor_nullCoordinate_throws() {
    NoiseVector(null!!, HashMap<String, Number>())
  }

  @Test(expected = NullPointerException::class)
  fun ctor_nullValue_throws() {
    NoiseVector(null!!, null!!)
  }

  @Test
  fun ctor_ok() {
    NoiseVector(mock(MapCoordinate::class.java), HashMap())
  }

  @Test
  fun getValue_unknownKey_0Integer() {
    val v = NoiseVector(mock(MapCoordinate::class.java), HashMap())
    val i = v.getValueInt("test123")
    Assert.assertEquals(0, i)
  }

  @Test
  fun getValue_knownKey_works() {
    val values = HashMap<String, Number>()
    values["test"] = 1337.0
    val v = NoiseVector(mock(MapCoordinate::class.java), values)
    val i = v.getValueDouble("test")
    Assert.assertEquals(0.001, 1337.0, i)
  }

  @Test
  fun getValue_knownKeyDifferentNumberClass_isCasting() {
    val values = HashMap<String, Number>()
    values["test"] = 1337.0
    val v = NoiseVector(mock(MapCoordinate::class.java), values)
    val i = v.getValueInt("test")
    Assert.assertEquals(1337, i)
  }
}
