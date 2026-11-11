package net.bestia.zone.ai.profile

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

/**
 * A bestia's rules in one short column, `CONDITION:threshold:ACTION` per line joined by `;`. A line this server no
 * longer knows is dropped rather than failing the whole row.
 */
@Converter
class OrderRulesConverter : AttributeConverter<List<OrderRule>, String?> {

  override fun convertToDatabaseColumn(rules: List<OrderRule>?): String? {
    if (rules.isNullOrEmpty()) return null

    return rules.joinToString(LINE) { "${it.condition.name}$PART${it.threshold ?: ""}$PART${it.action.name}" }
  }

  override fun convertToEntityAttribute(column: String?): List<OrderRule> {
    if (column.isNullOrBlank()) return emptyList()

    return column.split(LINE).mapNotNull { line ->
      val (condition, threshold, action) = line.split(PART).takeIf { it.size == 3 } ?: return@mapNotNull null
      OrderRule.fromNamesOrNull(condition, threshold.toIntOrNull(), action)
    }
  }

  private companion object {
    const val LINE = ";"
    const val PART = ":"
  }
}
