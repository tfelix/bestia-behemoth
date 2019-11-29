package de.tfelix.bestia.worldgen.map

import org.junit.Assert
import org.junit.Test

class Map2DDiscretePartTest {

  private val info = Map2DDiscreteInfo(
      totalWidth = 1000,
      totalHeight = 1000
  )

  @Test
  fun size_ok() {
    val c = Map2DDiscreteChunk(2, 5, 12, 10, info)
    Assert.assertEquals(120, c.size())
  }

  @Test
  fun getIterator_ok() {
    val c = Map2DDiscreteChunk(10, 10, 10, 10, info)
    Assert.assertEquals(100, c.iterator.asSequence().count())
  }

  @Test
  fun iteratorNext_countsToTheCorrectNumber_ok() {
    val c = Map2DDiscreteChunk(5, 5, 2, 2, info)
    val it = c.iterator

    while (it.hasNext()) {
      val cord = it.next() as Map2DDiscreteCoordinate

      println(cord.toString())
    }
  }

  @Test
  fun getIdent_differentObj_different() {
    val c1 = Map2DDiscreteChunk(10, 10, 10, 10, info)
    val c2 = Map2DDiscreteChunk(12, 13, 10, 10, info)
    Assert.assertNotEquals(c1.ident, c2.ident)
  }

  @Test
  fun getIdent_equalObj_same() {
    val c1 = Map2DDiscreteChunk(12, 13, 10, 10, info)
    val c2 = Map2DDiscreteChunk(12, 13, 10, 10, info)
    Assert.assertEquals(c1.ident, c2.ident)
  }
}
