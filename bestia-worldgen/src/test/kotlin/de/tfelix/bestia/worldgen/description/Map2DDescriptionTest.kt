package de.tfelix.bestia.worldgen.description

import de.tfelix.bestia.worldgen.map.Map2DDescription
import de.tfelix.bestia.worldgen.random.NoiseVectorBuilder
import de.tfelix.bestia.worldgen.noise.SimplexNoiseProvider
import org.junit.Assert
import org.junit.Test

class Map2DDescriptionTest {

  private val desc: Map2DDescription
    get() {

      val nb = NoiseVectorBuilder()
      nb.addDimension("chunkHeight", Double::class.java, SimplexNoiseProvider(123))

      return Map2DDescription(
          noiseVectorBuilder = nb,
          mapHeight = 100,
          mapWidth = 100,
          chunkHeight = 10,
          chunkWidth = 10
      )
    }

  @Test
  fun getMapPartCount_100() {
    val d = desc
    Assert.assertEquals(100, d.chunkCount)
  }

  @Test
  fun getMapPartCount_SizeSmallerPartition_Always1() {

    val nb = NoiseVectorBuilder()
    nb.addDimension("chunkHeight", Double::class.java, SimplexNoiseProvider(123))

    val d = Map2DDescription(
        noiseVectorBuilder = nb,
        mapHeight = 100,
        mapWidth = 100,
        chunkHeight = 1000,
        chunkWidth = 1000
    )

    Assert.assertEquals(1, d.chunkCount)
  }

  @Test
  fun getMapParts_correctMapParts() {
    val d = desc

    val mps = d.mapParts

    var i = 0
    while (mps.hasNext()) {

      mps.next()
      i++
    }

    Assert.assertEquals(100, i.toLong())
  }

  @Test
  fun getNoiseVectorBuilder_builderNotNull() {
    val d = desc
    Assert.assertNotNull(d.noiseVectorBuilder)
  }

}
