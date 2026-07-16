package net.bestia.zone.ecs.battle.effects

import net.bestia.zone.battle.buff.StatusEffectDefinition
import net.bestia.zone.battle.buff.StatusEffectSource
import net.bestia.zone.battle.buff.StackBehavior
import net.bestia.zone.ecs.Dirtyable
import net.bestia.zone.ecs.SyncTargets
import net.bestia.zone.ecs.account.Account
import net.bestia.zone.ecs.core.Component
import net.bestia.zone.ecs.core.World
import net.bestia.zone.message.EntitySMSG
import net.bestia.zone.util.EntityId

/**
 * Every status effect currently active on an entity. Synced to the client via the generic
 * [Dirtyable] pipeline ([net.bestia.zone.ecs.ZoneEngine]); [toEntityMessage] is what filters out
 * effects with `showIcon = false` so internal bookkeeping effects never reach the client (see
 * [ActiveStatusEffect.showIcon]).
 *
 * Sync is driven by this component's own dirty flag: [applyEffect]/[tickDown]/[consume] mark it
 * dirty as they mutate, and a freshly added instance starts dirty, so changes reach the client
 * without any external bookkeeping. To force a resend when nothing changed, call [markDirty].
 */
class StatusEffects(
  val activeEffects: MutableList<ActiveStatusEffect> = mutableListOf()
) : Component, Dirtyable {

  private var dirty = true

  /** Applies [definition] at [level], resolving [StackBehavior] against any existing instance. */
  fun applyEffect(
    definition: StatusEffectDefinition,
    level: Int,
    instanceId: Long,
    sourceEntityId: EntityId?,
    durationSeconds: Double
  ) {
    fun newInstance() = ActiveStatusEffect(
      instanceId = instanceId,
      definitionId = definition.id,
      level = level,
      remainingSeconds = durationSeconds.toFloat(),
      showIcon = definition.showIcon,
      isDebuff = definition.polarity == StatusEffectSource.DEBUFF,
      sourceEntityId = sourceEntityId
    )

    val existing = activeEffects.firstOrNull { it.definitionId == definition.id }

    when (definition.stackBehavior) {
      StackBehavior.STACK_INDEPENDENT -> activeEffects.add(newInstance())
      StackBehavior.IGNORE_IF_PRESENT -> if (existing == null) activeEffects.add(newInstance())
      StackBehavior.REFRESH_DURATION -> {
        if (existing != null) {
          existing.remainingSeconds = durationSeconds.toFloat()
        } else {
          activeEffects.add(newInstance())
        }
      }
      StackBehavior.REPLACE_IF_STRONGER -> {
        if (existing == null) {
          activeEffects.add(newInstance())
        } else if (level > existing.level) {
          activeEffects.remove(existing)
          activeEffects.add(newInstance())
        }
      }
    }

    dirty = true
  }

  /** Ticks down every active instance by [deltaTime] and removes any that expired. */
  fun tickDown(deltaTime: Float) {
    val iterator = activeEffects.iterator()
    while (iterator.hasNext()) {
      val effect = iterator.next()
      effect.remainingSeconds -= deltaTime
      if (effect.remainingSeconds <= 0f) {
        iterator.remove()
        dirty = true
      }
    }
  }

  /** Removes a single active instance (e.g. a trigger effect consuming itself). */
  fun consume(instanceId: Long): Boolean {
    val removed = activeEffects.removeIf { it.instanceId == instanceId }
    if (removed) dirty = true
    return removed
  }

  override fun isDirty(): Boolean = dirty

  override fun markDirty() {
    dirty = true
  }

  override fun clearDirty() {
    dirty = false
  }

  override fun toEntityMessage(entityId: Long): EntitySMSG {
    val visible = activeEffects.filter { it.showIcon }
    return StatusEffectListSMSG(
      entityId = entityId,
      effects = visible.map {
        StatusEffectListSMSG.StatusEffectEntry(
          effectId = it.definitionId,
          level = it.level,
          remainingSeconds = it.remainingSeconds,
          debuff = it.isDebuff
        )
      }
    )
  }

  override fun syncTargets(world: World, entityId: EntityId): SyncTargets {
    val owner = world.get(entityId, Account::class)?.accountId
      ?: return SyncTargets.PublicInRange
    return SyncTargets.Accounts(setOf(owner))
  }
}
