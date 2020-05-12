package de.tfelix.bestia.worldgen.map

import de.tfelix.bestia.worldgen.noise.NoiseProvider
import de.tfelix.bestia.worldgen.random.NoiseVector
import de.tfelix.bestia.worldgen.random.NoiseVectorBuilder
import org.junit.Assert
import org.junit.Test
import org.mockito.Mockito.mock

class MapDataPartTest {

  private val cord: MapCoordinate
    get() = Map2DDiscreteCoordinate(57, 1986)

  private val noise: NoiseVector
    get() {
      val builder = NoiseVectorBuilder()
      builder.addDimension("test", Double::class.java, object : NoiseProvider {
        override fun getRandom(coordinate: Map2DDiscreteCoordinate): Double {
          return Math.PI
        }
      })
      return builder.generate(cord)
    }

  private val mapPart: MapChunk
    get() = mock(MapChunk::class.java)

  @Test
  fun ctor_ok() {
    MapDataPart("test", mapPart)
  }

  @Test(expected = IllegalArgumentException::class)
  fun ctor_empty_throws() {
    MapDataPart("", mapPart)
  }

  @Test
  fun getIdent_ok() {
    val (ident) = MapDataPart("test", mapPart)
    Assert.assertEquals("test", ident)
  }

  @Test
  fun getCoordinateNoise_knownCord_ok() {
    val p = MapDataPart("test", mapPart)
    val nv1 = noise
    p.addCoordinateNoise(cord, nv1)
    val nv2 = p.getCoordinateNoise(cord)
    Assert.assertEquals(nv1, nv2)
  }

  @Test
  fun getMapPart_ok() {
    val mp = mapPart
    val (_, mapChunk) = MapDataPart("test", mp)
    Assert.assertEquals(mp, mapChunk)
  }
}