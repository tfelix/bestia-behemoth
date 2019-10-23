package de.tfelix.bestia.worldgen.random

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.whenever
import org.junit.Test
import org.mockito.Mockito.mock

class NoiseVectorBuilderTest {

  private val provider: NoiseProvider
    get() {
      val prov = mock(NoiseProvider::class.java)
      whenever(prov.getRandom(any())).thenReturn(Math.PI)

      return prov
    }

  @Test
  fun addDimension_ok() {
    val b = NoiseVectorBuilder()
    b.addDimension("test", Float::class.java, provider)
  }
}
