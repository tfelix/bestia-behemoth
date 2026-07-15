package net.bestia.zone.battle

import net.bestia.zone.battle.buff.StatusEffectDefinitionRegistry
import net.bestia.zone.battle.buff.StatusEffectEffect
import net.bestia.zone.ecs.battle.buff.StatusEffects
import net.bestia.zone.ecs.battle.buff.StatAggregationSystem
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Service
import java.util.concurrent.atomic.AtomicLong

/**
 * Entry point for applying a status effect to a target entity - what skill scripts and attack
 * handlers call. Resolves the [net.bestia.zone.battle.buff.StatusEffectDefinition], generates a fresh
 * instance id, and delegates stacking rules to [StatusEffects.applyEffect].
 */
@Service
class StatusEffectService(
  private val statusEffectDefinitionRegistry: StatusEffectDefinitionRegistry
) {

  fun applyEffect(
    world: World,
    targetId: EntityId,
    definitionId: Long,
    level: Int,
    sourceEntityId: EntityId? = null
  ) {
    val definition = statusEffectDefinitionRegistry.getOrThrow(definitionId)
    val durationSeconds = definition.durationSeconds(level)
    val instanceId = instanceIdGenerator.getAndIncrement()

    world.update(targetId, default = { StatusEffects() }) { effects ->
      effects.applyEffect(
        definition = definition,
        level = level,
        instanceId = instanceId,
        sourceEntityId = sourceEntityId,
        durationSeconds = durationSeconds
      )
    }

    // Pre-provision StatModifiers synchronously (this call always runs outside a System) so
    // StatAggregationSystem/SpeedModifierSystem don't hit a one-tick lag creating it themselves
    // mid-tick - see StatAggregationSystem.ensureStatModifiers.
    if (definition.effects.any { it is StatusEffectEffect.StatModifierEffect }) {
      StatAggregationSystem.ensureStatModifiers(world, targetId)
    }
  }

  companion object {
    private val instanceIdGenerator = AtomicLong(1)
  }
}
