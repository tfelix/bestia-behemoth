package net.bestia.zone.skill.persistence

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

/** Keeps [SkillMechanics] in one column: most rows set nothing, and the fields grow with the catalogue. */
@Converter
class SkillMechanicsConverter : AttributeConverter<SkillMechanics, String> {

  override fun convertToDatabaseColumn(attribute: SkillMechanics?): String {
    return MAPPER.writeValueAsString(attribute ?: SkillMechanics.NONE)
  }

  override fun convertToEntityAttribute(dbData: String?): SkillMechanics {
    if (dbData.isNullOrBlank()) {
      return SkillMechanics.NONE
    }

    return MAPPER.readValue(dbData)
  }

  private companion object {
    val MAPPER: ObjectMapper = ObjectMapper()
      .registerKotlinModule()
      .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
  }
}
