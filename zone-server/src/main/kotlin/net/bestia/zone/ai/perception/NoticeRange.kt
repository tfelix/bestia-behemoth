package net.bestia.zone.ai.perception

import net.bestia.zone.battle.ecs.status.CombatStats
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.World
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.util.EntityId
import kotlin.math.abs
import kotlin.math.max

/** How close a candidate may come before a creature notices it: the wild notice a camouflaged master later. */
object NoticeRange {

  val READS: ComponentClassSet = setOf(CombatStats::class)

  /**
   * Whether [perceiver] at [from] notices [candidate] at [to]. Measured like the sight cube, along the longest axis,
   * so a candidate with no camouflage is noticed anywhere in it.
   */
  fun notices(world: World, perceiver: EntityId, candidate: EntityId, from: Vec3L, to: Vec3L, sightRadius: Int): Boolean {
    val cut = world.get(candidate, CombatStats::class)?.values?.noticeRangeCutPercent ?: 0
    if (cut <= 0 || !Hostility.isUntamed(world, perceiver)) return true

    val reach = sightRadius.toLong() * (100 - cut.coerceAtMost(100)) / 100
    val axisDistance = max(abs(to.x - from.x), max(abs(to.y - from.y), abs(to.z - from.z)))

    return axisDistance <= reach
  }
}
