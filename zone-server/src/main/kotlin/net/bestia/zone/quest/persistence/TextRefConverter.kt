package net.bestia.zone.quest.persistence

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import net.bestia.zone.text.TextRef

@Converter
class TextRefConverter : AttributeConverter<TextRef, String> {

  override fun convertToDatabaseColumn(attribute: TextRef): String {
    return MAPPER.writeValueAsString(attribute)
  }

  override fun convertToEntityAttribute(dbData: String): TextRef {
    return MAPPER.readValue(dbData)
  }

  private companion object {
    val MAPPER: ObjectMapper = ObjectMapper()
      .registerKotlinModule()
      .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
  }
}
