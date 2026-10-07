package net.bestia.zone.item.persistence

import net.bestia.zone.persistence.EntitySnapshot
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
  /** 0 for an unharmed item, and for rows written before items could be damaged. */
  val integrityLost: Int = 0,
) : EntitySnapshot