package net.bestia.zone.ecs.persistence.persisters

import net.bestia.zone.ecs.persistence.EntitySnapshot
import net.bestia.zone.util.EntityId
import java.time.Instant

/** Mutable state of a dropped ground item. */
data class LootSnapshot(
  override val entityId: EntityId,
  val itemId: Long,
  val amount: Int,
  val uniqueId: Long,
  val x: Long,
  val y: Long,
  val z: Long,
  /** Null for a unique item, which does not decay, and for rows written before items decayed. */
  val despawnAt: Instant? = null,
) : EntitySnapshot