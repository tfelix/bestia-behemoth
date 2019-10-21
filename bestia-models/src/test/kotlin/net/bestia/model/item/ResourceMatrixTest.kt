package net.bestia.model.item

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class ResourceMatrixTest {

  @Test
  fun `resource matrix can be serialized`() {
    val sut = ResourceMatrix(craftType = CraftType.ALCHEMY).apply {
      set(3, 4, ResourceEntry(Resource.GOLD, 3))
      set(3, 3, ResourceEntry(Resource.GOLD, 5))
      set(3, 2, ResourceEntry(Resource.IRON, 2))
    }

    val json = ResourceMatrix.MAPPER.writeValueAsString(sut)
    val sut2 = ResourceMatrix.MAPPER.readValue(json, ResourceMatrix::class.java)

    assertEquals(sut, sut2)
  }
}