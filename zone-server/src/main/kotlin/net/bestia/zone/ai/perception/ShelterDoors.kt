package net.bestia.zone.ai.perception

import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.world.settlement.SettlementSiteIndex
import org.springframework.stereotype.Service

/**
 * Doorsteps somebody could get to from where they are standing — the world lookup behind [ShelterSense].
 *
 * Split off the sense for [ForageGround]'s reason: the real answer needs a generated world, and an AI
 * scenario test has none. What a scenario is about is whether the villager runs for a door, not which of
 * a generated town's doors it is.
 */
fun interface ShelterDoors {

  /** Doorsteps within [reach] tiles of [at], both in position units. */
  fun near(at: Vec3L, reach: Long): List<Vec3L>
}

/**
 * The real answer: every door of every settlement the point is near.
 *
 * Every door rather than only the public ones, because a doorway is shelter whoever lives behind it. The
 * expansion over a whole city's buildings is affordable because [ShelterSense] asks only while there is a
 * fight in view and only until it has picked one.
 */
@Service
class SettlementShelterDoors(private val sites: SettlementSiteIndex) : ShelterDoors {

  override fun near(at: Vec3L, reach: Long): List<Vec3L> {
    // `coveringWithin` measures to the settlement's footprint, so a farmer in a field outside the walls
    // still finds the village. The per-door filter is what turns that into "close enough to run to".
    return sites.coveringWithin(at.x, at.y, reach)
      .mapNotNull { sites.siteOf(it) }
      .flatMap { site -> site.buildings.map { sites.doorstepOf(it) } }
      .filter { it.distance(at) <= reach }
  }
}
