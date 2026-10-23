package net.bestia.zone.travel

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import net.bestia.zone.geometry.Vec3L

/**
 * A player's journey as an entity's row keeps it across a restart: the stops still ahead. How far along the
 * way the entity was is its position, which the row keeps anyway.
 */
data class StoredJourney(
  val waypoints: List<Vec3L>,
  val name: String? = null,
  val savedRouteId: Long? = null,
) {

  fun toJourney(): Journey {
    return Journey(waypoints, JourneyOrder.PLAYER, name, savedRouteId)
  }

  fun encode(): String {
    return MAPPER.writeValueAsString(this)
  }

  companion object {
    private val MAPPER = jacksonObjectMapper()

    /** Null for a creature's own journey, which its AI starts again by itself. */
    fun of(journey: Journey): StoredJourney? {
      if (journey.order != JourneyOrder.PLAYER) return null
      return StoredJourney(journey.waypoints.drop(journey.nextIndex), journey.name, journey.savedRouteId)
    }

    /** Null for no journey, and for one that cannot be read any more - it is not worth refusing a spawn over. */
    fun decode(text: String?): StoredJourney? {
      if (text.isNullOrEmpty()) return null
      return runCatching { MAPPER.readValue<StoredJourney>(text) }.getOrNull()?.takeIf { it.waypoints.isNotEmpty() }
    }
  }
}
