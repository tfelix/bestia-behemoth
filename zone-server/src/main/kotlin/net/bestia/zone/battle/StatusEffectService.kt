package net.bestia.zone.battle

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.battle.damage.DamageGate
import net.bestia.zone.battle.status.StatusEffectDefinition
import net.bestia.zone.battle.status.StatusEffectDefinitionRegistry
import net.bestia.zone.battle.status.StatusEffectPolarity
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.battle.status.StatusEffectScriptRegistry
import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.ecs.status.IsStatusValueDirty
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Service
import net.bestia.zone.ecs.core.update

/**
 * Entry point for applying a status effect to a target entity - what skill scripts and attack
 * handlers call. Resolves the [net.bestia.zone.battle.status.StatusEffectDefinition] and its
 * [net.bestia.zone.battle.status.StatusEffectScript], delegates stacking rules to
 * [StatusEffects.applyEffect], and marks the target for a status value recalc.
 */
@Service
class StatusEffectService(
  private val statusEffectDefinitionRegistry: StatusEffectDefinitionRegistry,
  private val statusEffectScriptRegistry: StatusEffectScriptRegistry
) {

  /** Returns false when the damage gate turns a harmful effect away. */
  fun applyEffect(
    world: World,
    targetId: EntityId,
    definitionId: Long,
    level: Int,
    sourceEntityId: EntityId? = null
  ): Boolean {
    val definition = statusEffectDefinitionRegistry.getOrThrow(definitionId)
    if (isTurnedAway(world, definition, sourceEntityId, targetId)) {
      LOG.debug { "${definition.identifier} from $sourceEntityId turned away from $targetId" }
      return false
    }

    val script = statusEffectScriptRegistry.getOrThrow(definition.script)
    val durationSeconds = script.durationSeconds(level)

    var change = StatusEffects.Change.UNCHANGED
    world.update(targetId, default = { StatusEffects() }) { effects ->
      change = effects.applyEffect(
        definitionId = definition.id,
        stackBehavior = script.stackBehavior,
        level = level,
        sourceEntityId = sourceEntityId,
        durationSeconds = durationSeconds,
        isSyncedToClient = definition.isSyncedToClient,
        shield = script.shield
      )
    }

    if (change == StatusEffects.Change.CHANGED) {
      world.add(targetId, IsStatusValueDirty)
    }

    return true
  }

  /** A harmful effect is harm like a blow, so the gate that stops a blow stops it too. */
  private fun isTurnedAway(
    world: World,
    definition: StatusEffectDefinition,
    sourceEntityId: EntityId?,
    targetId: EntityId
  ): Boolean {
    return definition.polarity == StatusEffectPolarity.DEBUFF &&
        DamageGate.verdict(world, sourceEntityId, targetId) != DamageGate.Verdict.ADMITTED
  }

  /**
   * Preferred overload for server-side call sites: the effect is named, not a magic id, and
   * [net.bestia.zone.battle.status.StatusEffectCatalogBootValidator] guarantees the constant
   * resolves against `status_effects.yml`. The `Long` overload stays for ids that only exist as
   * data at runtime (a skill script's `Buff.effectId`, a wire message).
   */
  fun applyEffect(
    world: World,
    targetId: EntityId,
    effect: StatusEffectId,
    level: Int,
    sourceEntityId: EntityId? = null
  ) = applyEffect(world, targetId, effect.id, level, sourceEntityId)

  private companion object {
    val LOG = KotlinLogging.logger { }
  }
}
