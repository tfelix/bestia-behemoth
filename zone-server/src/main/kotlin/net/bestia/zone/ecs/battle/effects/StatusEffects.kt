package net.bestia.zone.ecs.battle.effects

import net.bestia.zone.battle.status.StackBehavior
import net.bestia.zone.ecs.Dirtyable
import net.bestia.zone.ecs.SyncTargets
import net.bestia.zone.ecs.account.Account
import net.bestia.zone.ecs.core.Component
import net.bestia.zone.ecs.core.World
import net.bestia.zone.message.EntitySMSG
import net.bestia.zone.util.EntityId

/**
 * Every status effect currently active on an entity. Synced to the client via the generic
 * [Dirtyable] pipeline ([net.bestia.zone.ecs.ZoneEngine]).
 *
 * Sync is driven by this component's own dirty flag: [applyEffect]/[tickDown] mark it dirty as
 * they mutate, and a freshly added instance starts dirty, so changes reach the client without any
 * external bookkeeping.
 */
class StatusEffects(
  val activeEffects: MutableList<ActiveStatusEffect> = mutableListOf()
) : Component, Dirtyable {

  private var dirty = true

  /** Applies [definitionId] at [level], resolving [stackBehavior] against any existing instance. */
  fun applyEffect(
    definitionId: Long,
    stackBehavior: StackBehavior,
    level: Int,
    sourceEntityId: EntityId?,
    durationSeconds: Double,
    isSyncedToClient: Boolean
  ) {
    fun newInstance() = ActiveStatusEffect(
      definitionId = definitionId,
      level = level,
      remainingSeconds = durationSeconds.toFloat(),
      sourceEntityId = sourceEntityId,
      isSyncedToClient = isSyncedToClient
    )

    val existing = activeEffects.firstOrNull { it.definitionId == definitionId }

    when (stackBehavior) {
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

  /** Ticks down every active instance by [deltaTime] and removes any that expired. Returns whether anything expired. */
  fun tickDown(deltaTime: Float): Boolean {
    val iterator = activeEffects.iterator()
    var expired = false

    while (iterator.hasNext()) {
      val effect = iterator.next()
      effect.remainingSeconds -= deltaTime
      if (effect.remainingSeconds <= 0f) {
        iterator.remove()
        dirty = true
        expired = true
      }
    }

    return expired
  }

  override fun isDirty(): Boolean = dirty

  override fun markDirty() {
    dirty = true
  }

  override fun clearDirty() {
    dirty = false
  }

  override fun toEntityMessage(entityId: Long, removed: Boolean): EntitySMSG {
    val visible = activeEffects.filter { it.isSyncedToClient }
    return StatusEffectsComponentSMSG(
      entityId = entityId,
      effects = visible.map {
        StatusEffectsComponentSMSG.StatusEffectEntry(
          effectId = it.definitionId,
          level = it.level,
          remainingSeconds = it.remainingSeconds,
          // No server-side source of truth for buff/debuff polarity anymore - the client
          // classifies icons by looking effectId up in its own local status effect DB (mirrors
          // how the Godot Attack DB works for skills). Candidate for removal from the wire
          // message in a later, separate proto change.
          debuff = false
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
