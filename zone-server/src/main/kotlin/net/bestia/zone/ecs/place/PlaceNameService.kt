package net.bestia.zone.ecs.place

import org.springframework.stereotype.Service

/**
 * What a position is called - one name, decided here.
 *
 * The rule is narrowest-wins: the smallest named area containing the point, and the region it sits in when
 * no area does. Because the region partition covers every cell of the world, there is always an answer.
 *
 * This is the only place that rule lives. Overlapping areas are a fact about the ground rather than a
 * problem to be pushed outwards, so nothing downstream - not [PlaceSystem], not the client - sees more
 * than one place or knows what sort of place it was.
 *
 * Separate from [PlaceSystem] because two callers need the answer and only one of them is the tick loop:
 * the system keeps a moving player's [Place] current, and `MasterEntitySpawner` needs the same answer once,
 * during login, so a player's location panel is filled the moment they appear.
 */
@Service
class PlaceNameService(
  private val regionService: PlaceRegionService,
  private val registry: AreaNameRegistry
) {

  fun resolve(x: Long, y: Long): PlaceRef {
    registry.at(x, y)?.let { return PlaceRef(it.name) }

    return PlaceRef(regionService.regions.regionAt(x.toDouble(), y.toDouble()).name)
  }
}
