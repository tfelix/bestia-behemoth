package net.bestia.zone.chronicle.persistence

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import net.bestia.worldgen.core.Actor
import net.bestia.worldgen.core.ActorType

/** A few actors in one readable column: `ARTIFACT#7,SETTLEMENT#3`. */
@Converter
class ActorsConverter : AttributeConverter<List<Actor>, String> {

  override fun convertToDatabaseColumn(attribute: List<Actor>): String {
    return attribute.joinToString(SEPARATOR) { "${it.type.name}$INDEX_MARK${it.index}" }
  }

  override fun convertToEntityAttribute(dbData: String): List<Actor> {
    return dbData.split(SEPARATOR).map { actor ->
      val (type, index) = actor.split(INDEX_MARK)
      Actor(ActorType.valueOf(type), index.toInt())
    }
  }

  private companion object {
    const val SEPARATOR = ","
    const val INDEX_MARK = "#"
  }
}
