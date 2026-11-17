package net.bestia.zone.quest.persistence

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import jakarta.persistence.AttributeConverter

/** A short list in one column: quests are made at runtime, and their parts are read and written whole. */
abstract class JsonListConverter<T>(private val type: TypeReference<List<T>>) : AttributeConverter<List<T>, String> {

  override fun convertToDatabaseColumn(attribute: List<T>?): String {
    return MAPPER.writeValueAsString(attribute ?: emptyList<T>())
  }

  override fun convertToEntityAttribute(dbData: String?): List<T> {
    if (dbData.isNullOrBlank()) {
      return emptyList()
    }

    return MAPPER.readValue(dbData, type)
  }

  private companion object {
    val MAPPER: ObjectMapper = ObjectMapper()
      .registerKotlinModule()
      .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
  }
}
