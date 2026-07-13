package net.bestia.zone.ecs.battle.buff

import net.bestia.zone.util.EntityId

/**
 * A single active instance of a buff/debuff on an entity. Denormalizes [showIcon] and [debuff]
 * from the owning `BuffDefinition` at application time so [Buffs.toEntityMessage] never needs a
 * registry lookup at sync time - components stay plain data, per [net.bestia.zone.ecs.core.Component].
 */
data class ActiveBuff(
  val instanceId: Long,
  val definitionId: Long,
  val level: Int,
  var remainingSeconds: Float,
  val showIcon: Boolean,
  val debuff: Boolean,
  val sourceEntityId: EntityId? = null
)
