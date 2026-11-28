package net.bestia.zone.ai.profile

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import net.bestia.zone.prop.GatherCategory

/**
 * A gatherer's filter in one short column: the skipped categories, `|`, the only items, each list joined by `,`. A
 * category this server no longer knows is dropped.
 */
@Converter
class GatherFilterConverter : AttributeConverter<GatherFilter, String?> {

  override fun convertToDatabaseColumn(filter: GatherFilter?): String? {
    if (filter == null || filter == GatherFilter()) return null

    return filter.skip.sorted().joinToString(ENTRY) { it.name } + LIST + filter.onlyItems.joinToString(ENTRY)
  }

  override fun convertToEntityAttribute(column: String?): GatherFilter {
    if (column.isNullOrBlank()) return GatherFilter()

    val lists = column.split(LIST)
    val skip = entriesOf(lists.getOrNull(0)).mapNotNull { name -> GatherCategory.entries.find { it.name == name } }
    val onlyItems = entriesOf(lists.getOrNull(1)).mapNotNull { it.toLongOrNull() }

    return GatherFilter(skip.toSet(), onlyItems)
  }

  private fun entriesOf(list: String?): List<String> {
    return list.orEmpty().split(ENTRY).filter { it.isNotEmpty() }
  }

  private companion object {
    const val LIST = "|"
    const val ENTRY = ","
  }
}
