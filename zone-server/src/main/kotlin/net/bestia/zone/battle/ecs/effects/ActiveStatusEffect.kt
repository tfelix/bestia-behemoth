package net.bestia.zone.battle.ecs.effects

import net.bestia.zone.battle.status.HarmShield
import net.bestia.zone.util.EntityId

/**
 * A single active instance of a status effect on an entity. Components stay plain data - the
 * script lookup happens at recalc time via
 * [net.bestia.zone.battle.status.StatusEffectDefinitionRegistry] /
 * [net.bestia.zone.battle.status.StatusEffectScriptRegistry], not stored here.
 *
 * [isSyncedToClient] denormalizes [net.bestia.zone.battle.status.StatusEffectDefinition.isSyncedToClient]
 * at application time so [StatusEffects.toEntityMessage] can filter out internal bookkeeping
 * effects without a registry lookup at sync time - the one exception to "components are plain
 * data with no derived fields", forced by [net.bestia.zone.sync.Dirtyable.toEntityMessage] taking
 * no world/registry parameter to look it up with.
 */
data class ActiveStatusEffect(
  val definitionId: Long,
  val level: Int,
  var remainingSeconds: Float,
  val sourceEntityId: EntityId? = null,
  val isSyncedToClient: Boolean = true,
  /** Denormalized like [isSyncedToClient], so the damage gate needs no registry on every hit. */
  val shield: HarmShield? = null
) {
  /** Runtime only, so it is kept out of `equals`, `copy` and storage. */
  var sinceLastTick: Float = 0f
}
