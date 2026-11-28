package net.bestia.zone.ai.profile

import net.bestia.zone.prop.GatherCategory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class GatherFilterConverterTest {

  private val converter = GatherFilterConverter()

  @Test
  fun `a filter reads back as stored`() {
    val filter = GatherFilter(skip = setOf(GatherCategory.TREE, GatherCategory.LOOSE), onlyItems = listOf(32, 142))

    assertEquals(filter, converter.convertToEntityAttribute(converter.convertToDatabaseColumn(filter)))
  }

  @Test
  fun `only items alone read back as stored`() {
    val filter = GatherFilter(onlyItems = listOf(32))

    assertEquals(filter, converter.convertToEntityAttribute(converter.convertToDatabaseColumn(filter)))
  }

  @Test
  fun `a category this server no longer knows is dropped`() {
    assertEquals(GatherFilter(skip = setOf(GatherCategory.TREE)), converter.convertToEntityAttribute("TREE,MUSHROOM|"))
  }

  @Test
  fun `an empty filter needs no column`() {
    assertNull(converter.convertToDatabaseColumn(GatherFilter()))
    assertEquals(GatherFilter(), converter.convertToEntityAttribute(null))
  }
}
