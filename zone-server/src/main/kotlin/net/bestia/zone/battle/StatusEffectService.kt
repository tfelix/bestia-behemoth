package net.bestia.zone.battle

import net.bestia.zone.battle.status.StatusEffectDefinitionRegistry
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.battle.status.StatusEffectScriptRegistry
import net.bestia.zone.ecs.battle.effects.StatusEffects
import net.bestia.zone.ecs.battle.status.IsStatusValueDirty
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Service

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

  fun applyEffect(
    world: World,
    targetId: EntityId,
    definitionId: Long,
    level: Int,
    sourceEntityId: EntityId? = null
  ) {
    val definition = statusEffectDefinitionRegistry.getOrThrow(definitionId)
    val script = statusEffectScriptRegistry.getOrThrow(definition.script)
    val durationSeconds = script.durationSeconds(level)

    world.update(targetId, default = { StatusEffects() }) { effects ->
      effects.applyEffect(
        definitionId = definition.id,
        stackBehavior = script.stackBehavior,
        level = level,
        sourceEntityId = sourceEntityId,
        durationSeconds = durationSeconds,
        isSyncedToClient = definition.isSyncedToClient
      )
    }

    world.add(targetId, IsStatusValueDirty)
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
}
