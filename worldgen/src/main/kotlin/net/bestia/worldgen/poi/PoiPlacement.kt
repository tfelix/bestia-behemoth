package net.bestia.worldgen.poi

import net.bestia.worldgen.vector.Vec2d

/**
 * Picks where each rolled instance of a POI stands, from candidates the stage already filtered by raster.
 *
 * Kinds go in ordinal order and instances in roll order, each taking the first candidate that is clear. Raising a
 * kind's count or appending a kind therefore never moves an instance placed before it.
 */
internal object PoiPlacement {

  /** The rolls of one kind that passed, and where it could stand in pick order. */
  class Request(val kind: PoiKind, val instanceIds: List<Int>, val candidates: List<Vec2d>)

  class Placed(val kind: PoiKind, val instanceId: Int, val at: Vec2d)

  /**
   * @param poiClearance metres every POI keeps from every other, of any kind
   * @param isClear whether nothing else built stands too close to a position
   * @throws PoiPlacementException when a required instance finds no position
   */
  fun place(
    requests: List<Request>,
    poiClearance: Double,
    seed: Long,
    isClear: (Vec2d) -> Boolean
  ): List<Placed> {
    val placed = ArrayList<Placed>()

    for (request in requests.sortedBy { it.kind.ordinal }) {
      for (instanceId in request.instanceIds.sorted()) {
        val at = request.candidates.firstOrNull { candidate ->
          isClear(candidate) && keepsDistance(candidate, request.kind, placed, poiClearance)
        }

        if (at != null) {
          placed.add(Placed(request.kind, instanceId, at))
        } else if (request.kind.isRequired) {
          throw PoiPlacementException(seed, request.kind, instanceId, request.candidates.size)
        }
      }
    }

    return placed
  }

  private fun keepsDistance(at: Vec2d, kind: PoiKind, placed: List<Placed>, poiClearance: Double): Boolean {
    return placed.none { other ->
      val distance = other.at.distanceTo(at)
      distance < poiClearance || (other.kind == kind && distance < kind.minSpacing)
    }
  }
}
