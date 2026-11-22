package net.bestia.zone.battle

import net.bestia.zone.geometry.Vec3
import net.bestia.zone.world.sight.SightLines
import org.springframework.stereotype.Service

/**
 * Whether an attacker can see its target, through terrain and player-built walls: asked by every ranged shot, every
 * skill whose `needsLineOfSight` is set, and every target of an area spell. See [SightLines] for how it stays cheap.
 *
 * Without [sightLines] (unit tests that build a strategy by hand) everything is in sight, which is what they want:
 * those tests are about damage, not terrain.
 */
@Service
class LineOfSightService(
  private val sightLines: SightLines? = null,
) {

  fun hasLineOfSight(a: Vec3<*>, b: Vec3<*>): Boolean =
    hasLineOfSight(a.x.toLong(), a.y.toLong(), a.z.toLong(), b.x.toLong(), b.y.toLong(), b.z.toLong())

  /** The same, for a caller iterating raw coordinates that would otherwise build two vectors per target. */
  fun hasLineOfSight(ax: Long, ay: Long, az: Long, bx: Long, by: Long, bz: Long): Boolean {
    val lines = sightLines ?: return true

    return lines.isClear(ax, ay, az, bx, by, bz)
  }
}
