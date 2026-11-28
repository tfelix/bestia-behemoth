package net.bestia.zone.ai.profile

import net.bestia.zone.item.care.CareItems
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CareItemsConverterTest {

  private val converter = CareItemsConverter()

  @Test
  fun `care items read back as stored`() {
    val items = CareItems(neverUse = listOf(46, 58), alsoUse = listOf(3))

    assertEquals(items, converter.convertToEntityAttribute(converter.convertToDatabaseColumn(items)))
  }

  @Test
  fun `one list alone reads back as stored`() {
    val items = CareItems(alsoUse = listOf(3))

    assertEquals(items, converter.convertToEntityAttribute(converter.convertToDatabaseColumn(items)))
  }

  @Test
  fun `no care items need no column`() {
    assertNull(converter.convertToDatabaseColumn(CareItems()))
    assertEquals(CareItems(), converter.convertToEntityAttribute(null))
  }
}
