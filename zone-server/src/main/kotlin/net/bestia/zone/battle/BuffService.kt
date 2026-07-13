package net.bestia.zone.battle

import net.bestia.zone.battle.buff.BuffDefinitionRegistry
import net.bestia.zone.battle.buff.BuffEffect
import net.bestia.zone.ecs.battle.buff.Buffs
import net.bestia.zone.ecs.battle.buff.StatAggregationSystem
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Service
import java.util.concurrent.atomic.AtomicLong

/**
 * Entry point for applying a buff/debuff to a target entity - what skill scripts and attack
 * handlers call. Resolves the [net.bestia.zone.battle.buff.BuffDefinition], generates a fresh
 * instance id, and delegates stacking rules to [Buffs.applyBuff].
 */
@Service
class BuffService(
  private val buffDefinitionRegistry: BuffDefinitionRegistry
) {

  fun applyBuff(
    world: World,
    targetId: EntityId,
    definitionId: Long,
    level: Int,
    sourceEntityId: EntityId? = null
  ) {
    val definition = buffDefinitionRegistry.getOrThrow(definitionId)
    val durationSeconds = definition.durationSeconds(level)
    val instanceId = instanceIdGenerator.getAndIncrement()

    world.update(targetId, default = { Buffs() }) { buffs ->
      buffs.applyBuff(
        definition = definition,
        level = level,
        instanceId = instanceId,
        sourceEntityId = sourceEntityId,
        durationSeconds = durationSeconds
      )
      world.markChanged(targetId, Buffs::class)
    }

    // Pre-provision StatModifiers synchronously (this call always runs outside a System) so
    // StatAggregationSystem/SpeedModifierSystem don't hit a one-tick lag creating it themselves
    // mid-tick - see StatAggregationSystem.ensureStatModifiers.
    if (definition.effects.any { it is BuffEffect.StatModifierEffect }) {
      StatAggregationSystem.ensureStatModifiers(world, targetId)
    }
  }

  companion object {
    private val instanceIdGenerator = AtomicLong(1)
  }
}
