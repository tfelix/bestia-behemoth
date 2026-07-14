package net.bestia.zone.ecs.battle.buff

import net.bestia.zone.battle.buff.BuffDefinition
import net.bestia.zone.battle.buff.BuffPolarity
import net.bestia.zone.battle.buff.StackBehavior
import net.bestia.zone.ecs.Dirtyable
import net.bestia.zone.ecs.SyncTargets
import net.bestia.zone.ecs.account.Account
import net.bestia.zone.ecs.core.Component
import net.bestia.zone.ecs.core.World
import net.bestia.zone.message.EntitySMSG
import net.bestia.zone.util.EntityId

/**
 * Every buff/debuff currently active on an entity. Synced to the client via the generic
 * [Dirtyable] pipeline ([net.bestia.zone.ecs.ZoneEngine]); [toEntityMessage] is what filters out
 * buffs with `showIcon = false` so internal bookkeeping buffs never reach the client (see
 * [ActiveBuff.showIcon]).
 *
 * Sync is driven by this component's own dirty flag: [applyBuff]/[tickDown]/[consume] mark it
 * dirty as they mutate, and a freshly added instance starts dirty, so changes reach the client
 * without any external bookkeeping. To force a resend when nothing changed, call [markDirty].
 */
class Buffs(
  val activeBuffs: MutableList<ActiveBuff> = mutableListOf()
) : Component, Dirtyable {

  private var dirty = true

  /** Applies [definition] at [level], resolving [StackBehavior] against any existing instance. */
  fun applyBuff(
    definition: BuffDefinition,
    level: Int,
    instanceId: Long,
    sourceEntityId: EntityId?,
    durationSeconds: Double
  ) {
    fun newInstance() = ActiveBuff(
      instanceId = instanceId,
      definitionId = definition.id,
      level = level,
      remainingSeconds = durationSeconds.toFloat(),
      showIcon = definition.showIcon,
      isDebuff = definition.polarity == BuffPolarity.DEBUFF,
      sourceEntityId = sourceEntityId
    )

    val existing = activeBuffs.firstOrNull { it.definitionId == definition.id }

    when (definition.stackBehavior) {
      StackBehavior.STACK_INDEPENDENT -> activeBuffs.add(newInstance())
      StackBehavior.IGNORE_IF_PRESENT -> if (existing == null) activeBuffs.add(newInstance())
      StackBehavior.REFRESH_DURATION -> {
        if (existing != null) {
          existing.remainingSeconds = durationSeconds.toFloat()
        } else {
          activeBuffs.add(newInstance())
        }
      }
      StackBehavior.REPLACE_IF_STRONGER -> {
        if (existing == null) {
          activeBuffs.add(newInstance())
        } else if (level > existing.level) {
          activeBuffs.remove(existing)
          activeBuffs.add(newInstance())
        }
      }
    }

    dirty = true
  }

  /** Ticks down every active instance by [deltaTime] and removes any that expired. */
  fun tickDown(deltaTime: Float) {
    val iterator = activeBuffs.iterator()
    while (iterator.hasNext()) {
      val buff = iterator.next()
      buff.remainingSeconds -= deltaTime
      if (buff.remainingSeconds <= 0f) {
        iterator.remove()
        dirty = true
      }
    }
  }

  /** Removes a single active instance (e.g. a trigger effect consuming itself). */
  fun consume(instanceId: Long): Boolean {
    val removed = activeBuffs.removeIf { it.instanceId == instanceId }
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
    val visible = activeBuffs.filter { it.showIcon }
    return BuffListSMSG(
      entityId = entityId,
      buffs = visible.map {
        BuffListSMSG.BuffEntry(
          buffId = it.definitionId,
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
