package de.tfelix.bestia.worldgen.description

import de.tfelix.bestia.worldgen.random.NoiseVectorBuilder
import de.tfelix.bestia.worldgen.random.SimplexNoiseProvider
import org.junit.Assert
import org.junit.Test

class Map2DDescriptionTest {

  private val desc: Map2DDescription
    get() {

      val nb = NoiseVectorBuilder()
      nb.addDimension("chunkHeight", Double::class.java, SimplexNoiseProvider(123))
      val b = Map2DDescription.Builder(
          noiseVectorBuilder = nb,
          height = 100,
          width = 100,
          partHeight = 10,
          partWidth = 10
      )

      return b.build()
    }

  @Test
  fun getMapPartCount_100() {
    val d = desc
    Assert.assertEquals(100, d.mapPartCount)
  }

  @Test
  fun getMapPartCount_SizeSmallerPartition_Always1() {

    val nb = NoiseVectorBuilder()
    nb.addDimension("chunkHeight", Double::class.java, SimplexNoiseProvider(123))

    val b = Map2DDescription.Builder(
        noiseVectorBuilder = nb,
        height = 100,
        width = 100,
        partHeight = 1000,
        partWidth = 1000
    )
    val d = b.build()

    Assert.assertEquals(1, d.mapPartCount)
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
