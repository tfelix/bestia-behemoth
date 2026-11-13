package net.bestia.zone.battle.ecs.damage

import net.bestia.zone.util.EntityId

/**
 * Told about every blow or spell that landed, after the health dropped. A watcher only takes note: it runs inside
 * [ReceivedDamageSystem] and acts later, so it reads no component the system did not declare.
 */
fun interface LandedHits {

  fun landed(hit: LandedHit)

  /** [healthBefore] and [healthAfter] count the whole tick's hits on [targetId], so a threshold is crossed once. */
  data class LandedHit(
    val targetId: EntityId,
    val sourceId: EntityId,
    val healthBefore: Int,
    val healthAfter: Int,
    val maxHealth: Int,
  )
}
