package net.bestia.zone.questgen.persistence

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import net.bestia.zone.questgen.plan.QuestPlan

/** A plan is read and written whole, so it is one JSON column. */
@Converter
class QuestPlanConverter : AttributeConverter<QuestPlan, String> {

  override fun convertToDatabaseColumn(attribute: QuestPlan): String {
    return MAPPER.writeValueAsString(attribute)
  }

  override fun convertToEntityAttribute(dbData: String): QuestPlan {
    return MAPPER.readValue(dbData)
  }

  private companion object {
    val MAPPER: ObjectMapper = ObjectMapper()
      .registerKotlinModule()
      .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
  }
}
