package net.bestia.zone.ai.profile

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import net.bestia.zone.item.care.CareItems

/** A bestia's care items in one short column: the never-use ids, `|`, the also-use ids, each list joined by `,`. */
@Converter
class CareItemsConverter : AttributeConverter<CareItems, String?> {

  override fun convertToDatabaseColumn(items: CareItems?): String? {
    if (items == null || items == CareItems()) return null

    return items.neverUse.joinToString(ID) + LIST + items.alsoUse.joinToString(ID)
  }

  override fun convertToEntityAttribute(column: String?): CareItems {
    if (column.isNullOrBlank()) return CareItems()

    val lists = column.split(LIST)
    return CareItems(neverUse = idsOf(lists.getOrNull(0)), alsoUse = idsOf(lists.getOrNull(1)))
  }

  private fun idsOf(list: String?): List<Long> {
    return list.orEmpty().split(ID).mapNotNull { it.toLongOrNull() }
  }

  private companion object {
    const val LIST = "|"
    const val ID = ","
  }
}
