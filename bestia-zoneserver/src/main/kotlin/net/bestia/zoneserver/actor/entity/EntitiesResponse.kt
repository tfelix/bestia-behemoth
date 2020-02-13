package net.bestia.zoneserver.actor.entity

import net.bestia.zoneserver.entity.Entity
import java.lang.IllegalArgumentException

data class EntitiesResponse(
    private val data: Map<Long, Entity>
) {
  operator fun get(key: Long): Entity {
    return data[key]
        ?: throw IllegalArgumentException("Entity with id $key was not inside response.")
  }

  val all get() = data.values
}