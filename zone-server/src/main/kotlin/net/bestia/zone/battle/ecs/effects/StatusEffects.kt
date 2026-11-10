package net.bestia.zone.battle.ecs.effects

import net.bestia.zone.battle.status.ActionLock
import net.bestia.zone.battle.status.HarmShield
import net.bestia.zone.battle.status.StackBehavior
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.battle.status.StatusEffectPolarity
import net.bestia.zone.sync.Dirtyable
import net.bestia.zone.sync.SyncTargets
import net.bestia.zone.sync.DirtyableComponent
import net.bestia.zone.ecs.core.World
import net.bestia.zone.message.EntitySMSG
import net.bestia.zone.util.EntityId

/**
 * Every status effect currently active on an entity. Synced to the client via the generic
 * [Dirtyable] pipeline ([net.bestia.zone.engine.ZoneEngine]).
 *
 * Sync is driven by this component's own dirty flag: [applyEffect]/[tickDown] mark it dirty as
 * they mutate, and a freshly added instance starts dirty, so changes reach the client without any
 * external bookkeeping.
 *
 * Everybody in range is told, not only the owner: that is how a client sees that a ward stone protects
 * another player before it aims at them.
 */
class StatusEffects(
  val activeEffects: MutableList<ActiveStatusEffect> = mutableListOf()
) : DirtyableComponent() {

  /**
   * Applies [definitionId] at [level], resolving [stackBehavior] against any existing instance. An aura
   * refreshes its effect on everybody in range every few seconds, so a refresh must stay cheap: see [Change].
   */
  fun applyEffect(
    definitionId: Long,
    stackBehavior: StackBehavior,
    level: Int,
    sourceEntityId: EntityId?,
    durationSeconds: Double,
    isSyncedToClient: Boolean,
    shield: HarmShield? = null,
    polarity: StatusEffectPolarity = StatusEffectPolarity.NEUTRAL,
    showsCountdown: Boolean = true,
    locks: Set<ActionLock> = emptySet(),
    potency: Int = 0,
  ): Change {
    fun newInstance() = ActiveStatusEffect(
      definitionId = definitionId,
      level = level,
      remainingSeconds = durationSeconds.toFloat(),
      sourceEntityId = sourceEntityId,
      isSyncedToClient = isSyncedToClient,
      shield = shield,
      polarity = polarity,
      showsCountdown = showsCountdown,
      locks = locks,
      potency = potency
    )

    val existing = activeEffects.firstOrNull { it.definitionId == definitionId }

    // Otherwise a faint buff from an ally could lower a friend's protection, and a weak curse could cover a strong
    // one to make it easy to lift.
    if (existing != null && stackBehavior != StackBehavior.STACK_INDEPENDENT && potency < existing.potency) {
      return Change.WEAKER
    }

    return when (stackBehavior) {
      StackBehavior.STACK_INDEPENDENT -> add(newInstance())
      StackBehavior.IGNORE_IF_PRESENT -> if (existing == null) add(newInstance()) else Change.UNCHANGED
      StackBehavior.REFRESH_DURATION -> when {
        existing == null -> add(newInstance())
        // The newest cast decides: a buff recast at another level takes that level's numbers.
        existing.level != level -> {
          activeEffects.remove(existing)
          add(newInstance())
        }
        else -> refresh(existing, durationSeconds)
      }
      StackBehavior.REPLACE_IF_STRONGER -> when {
        existing == null -> add(newInstance())
        level > existing.level -> {
          activeEffects.remove(existing)
          add(newInstance())
        }
        else -> Change.UNCHANGED
      }
    }
  }

  private fun add(effect: ActiveStatusEffect): Change {
    activeEffects.add(effect)
    markDirty()

    return Change.CHANGED
  }

  private fun refresh(effect: ActiveStatusEffect, durationSeconds: Double): Change {
    effect.remainingSeconds = durationSeconds.toFloat()
    // The client counts a visible effect down from the last sync, so it has to hear about the new clock.
    if (effect.isSyncedToClient && effect.showsCountdown) {
      markDirty()
    }

    return Change.REFRESHED
  }

  fun hasEffect(definitionId: Long): Boolean = activeEffects.any { it.definitionId == definitionId }

  /** The HIDDEN or CLOAKED effect on the bearer, for a detection contest against its Potency; null if neither. */
  fun concealment(): ActiveStatusEffect? {
    return activeEffects.firstOrNull { it.definitionId == StatusEffectId.HIDDEN.id || it.definitionId == StatusEffectId.CLOAKED.id }
  }

  /** Hidden or cloaked: nothing may pick the bearer out as a target. */
  fun isConcealed(): Boolean {
    return activeEffects.any { it.definitionId == StatusEffectId.HIDDEN.id || it.definitionId == StatusEffectId.CLOAKED.id }
  }

  fun isLocked(lock: ActionLock): Boolean {
    return activeEffects.any { lock in it.locks }
  }

  fun hasShield(shield: HarmShield): Boolean {
    return activeEffects.any { it.shield == shield }
  }

  /** Adds stored effects to the live ones. A live effect wins over a stored one with the same id. */
  fun restore(stored: List<ActiveStatusEffect>) {
    val missing = stored.filter { restored -> !hasEffect(restored.definitionId) }
    if (missing.isEmpty()) {
      return
    }

    activeEffects.addAll(missing)
    markDirty()
  }

  /**
   * Drops every instance of [definitionId] and returns whether anything was actually removed.
   *
   * Effects normally leave through [tickDown], so this is for the ones that decide their own end:
   * a one-shot marker removing itself once it has done its job (see
   * [net.bestia.zone.battle.status.scripts.MasterIntroMarker]), a dispel, a skill that consumes a buff.
   */
  fun removeEffect(definitionId: Long): Boolean {
    val removed = activeEffects.removeAll { it.definitionId == definitionId }

    if (removed) {
      markDirty()
    }

    return removed
  }

  /** Ticks down every active instance by [deltaTime], removes any that expired and returns them. */
  fun tickDown(deltaTime: Float): List<ActiveStatusEffect> {
    val iterator = activeEffects.iterator()
    val expired = mutableListOf<ActiveStatusEffect>()

    while (iterator.hasNext()) {
      val effect = iterator.next()
      effect.remainingSeconds -= deltaTime
      effect.ageSeconds += deltaTime
      if (effect.remainingSeconds <= 0f) {
        iterator.remove()
        markDirty()
        expired.add(effect)
      }
    }

    return expired
  }

  override fun toEntityMessage(entityId: Long, removed: Boolean): EntitySMSG {
    val visible = activeEffects.filter { it.isSyncedToClient }
    return StatusEffectsComponentSMSG(
      entityId = entityId,
      effects = visible.map {
        StatusEffectsComponentSMSG.StatusEffectEntry(
          effectId = it.definitionId,
          level = it.level,
          remainingSeconds = if (it.showsCountdown) it.remainingSeconds else Float.POSITIVE_INFINITY,
          debuff = it.polarity == StatusEffectPolarity.DEBUFF
        )
      }
    )
  }

  /** What an [applyEffect] did. Only [CHANGED] can change what the effects do to the entity's values. */
  enum class Change {
    UNCHANGED,
    REFRESHED,
    CHANGED,

    /** Turned away: the effect already on the bearer came from a stronger cast. */
    WEAKER,
  }

  override fun syncTargets(world: World, entityId: EntityId): SyncTargets {
    return SyncTargets.PublicInRange
  }
}
