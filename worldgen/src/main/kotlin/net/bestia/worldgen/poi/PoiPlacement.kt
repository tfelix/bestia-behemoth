package net.bestia.worldgen.poi

import net.bestia.worldgen.vector.Vec2d

/**
 * Picks where each rolled copy of a landmark stands, from candidates the stage already filtered by raster.
 *
 * Kinds go in ordinal order and copies in roll order, each taking the first candidate that is clear. Raising a
 * kind's count or appending a kind therefore never moves a copy placed before it.
 */
internal object PoiPlacement {

  /** The rolls of one kind that passed, and where it could stand in pick order. */
  class Request(val kind: PoiKind, val copies: List<Int>, val candidates: List<Vec2d>)

  class Placed(val kind: PoiKind, val copy: Int, val at: Vec2d)

  /**
   * @param landmarkClearance metres every landmark keeps from every other, of any kind
   * @param isClear whether nothing else built stands too close to a position
   * @throws PoiPlacementException when a required copy finds no position
   */
  fun place(
    requests: List<Request>,
    landmarkClearance: Double,
    seed: Long,
    isClear: (Vec2d) -> Boolean
  ): List<Placed> {
    val placed = ArrayList<Placed>()

    for (request in requests.sortedBy { it.kind.ordinal }) {
      for (copy in request.copies.sorted()) {
        val at = request.candidates.firstOrNull { candidate ->
          isClear(candidate) && keepsDistance(candidate, request.kind, placed, landmarkClearance)
        }

        if (at != null) {
          placed.add(Placed(request.kind, copy, at))
        } else if (request.kind.isRequired) {
          throw PoiPlacementException(seed, request.kind, copy, request.candidates.size)
        }
      }
    }

    return placed
  }

  private fun keepsDistance(at: Vec2d, kind: PoiKind, placed: List<Placed>, landmarkClearance: Double): Boolean {
    return placed.none { other ->
      val distance = other.at.distanceTo(at)
      distance < landmarkClearance || (other.kind == kind && distance < kind.minSpacing)
    }
  }
}
