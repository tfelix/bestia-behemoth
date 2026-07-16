package net.bestia.zone.ecs.battle.effects

import net.bestia.zone.util.EntityId

/**
 * A single active instance of a status effect on an entity. Denormalizes [showIcon] and [isDebuff]
 * from the owning `StatusEffectDefinition` at application time so [StatusEffects.toEntityMessage] never needs a
 * registry lookup at sync time - components stay plain data, per [net.bestia.zone.ecs.core.Component].
 */
data class ActiveStatusEffect(
  val instanceId: Long,
  val definitionId: Long,
  val level: Int,
  var remainingSeconds: Float,
  val showIcon: Boolean,
  val isDebuff: Boolean,
  val sourceEntityId: EntityId? = null
)
