package net.bestia.zone.ai.perception

import net.bestia.zone.ai.core.state.Blackboard
import net.bestia.zone.ai.domain.bestia.BestiaDomain
import net.bestia.zone.ai.domain.bestia.NamedPlace
import net.bestia.zone.ai.domain.bestia.PlaceLookup
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.prop.station.WorkAssignment
import org.springframework.stereotype.Component

/**
 * Keeps a bestia whose orders name a work area in that area: its job areas, and its home and roaming range while it
 * works no station. Read from the place each time, so moving the pin moves every bestia that uses it.
 */
@Component
class PlaceSense(
  private val places: PlaceLookup,
) : Sense {

  override val name = "place"

  override val intervalSeconds = 2f

  override val reads: ComponentClassSet = setOf(WorkAssignment::class)

  override fun sense(context: SenseContext) {
    val placeId = context.recall(BestiaDomain.AREA_PLACE_ID) ?: return
    val place = places.placeOf(placeId)?.takeIf { it.kind == NamedPlace.Kind.AREA } ?: return
    val area = place.area

    BestiaDomain.WORK_AREAS.filter { context.recall(it) != null }.forEach { context.remember(it, area, Blackboard.PERMANENT) }

    // At a station its home is the station, as long as it works there.
    if (context.world.has(context.entityId, WorkAssignment::class)) return
    context.remember(BestiaDomain.HOME_POSITION, area.center, Blackboard.PERMANENT)
    context.remember(BestiaDomain.WANDER_RADIUS, area.radius, Blackboard.PERMANENT)
  }
}
