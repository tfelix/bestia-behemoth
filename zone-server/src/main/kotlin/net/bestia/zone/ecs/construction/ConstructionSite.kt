package net.bestia.zone.ecs.construction

import net.bestia.zone.ecs.SyncTargets
import net.bestia.zone.ecs.core.Countdown
import net.bestia.zone.ecs.core.Removable
import net.bestia.zone.ecs.core.World
import net.bestia.zone.message.EntitySMSG
import net.bestia.zone.util.EntityId
import net.bestia.zone.world.prop.StaticEntityKind

/**
 * Something being built, and what it will be when it is.
 *
 * ### It is an ordinary entity, not a static prop
 *
 * A finished station reaches clients on the per-chunk static batch, which is what makes thousands of trees
 * affordable. A site cannot use it: the batch is a whole-column snapshot with no per-entity update, and
 * `ZoneEngine` keeps anything carrying `StaticSync` out of the entity channel entirely - so a static site's
 * health and progress would reach nobody. Sites are rare enough that the cheap channel buys nothing.
 *
 * ### Why a [Countdown] that nothing counts down
 *
 * [remainingSeconds] is work owed, not time left, and [ConstructionSystem] only advances it on the ticks
 * somebody is actually working. Countdown is still the right base: the throttled dirty flag it exists for is
 * what keeps a progress bar from costing 20 broadcasts a second, and it is the same machinery a cast and a
 * craft already use.
 *
 * @param structureId the [net.bestia.zone.world.prop.PlayerStructure] row this site belongs to, which is what
 *   survives a restart. The entity id does not.
 */
class ConstructionSite(
  val kind: StaticEntityKind,
  val ownerMasterId: Long,
  val structureId: Long,
  val yaw: Float,
  val totalSeconds: Float,
  remainingSeconds: Float = totalSeconds
) : Countdown(remainingSeconds), Removable {

  /**
   * Whether anybody is working on it this tick, set by [ConstructionSystem].
   *
   * Dirties on change rather than waiting for the next heartbeat, because it is what tells a client whether
   * to keep animating: a bar that holds for two seconds after the builder walks away reads as a stutter.
   */
  var active: Boolean = false
    set(value) {
      if (field == value) return
      field = value
      markDirty()
    }

  // A site reports "still going" rather than feeding a decision, so it beats at the slower rate a craft bar
  // does rather than a cast bar's. The accumulator itself is Countdown's.
  override val syncIntervalSeconds = RESYNC_INTERVAL

  /** Worked seconds since the row was last written. See [isPersistDue]. */
  private var sinceLastPersist = 0f

  init {
    require(totalSeconds > 0f) { "A construction site needs a positive totalSeconds, got $totalSeconds" }
  }

  /**
   * Puts [deltaTime] seconds of work in.
   *
   * Only ever called for a tick somebody actually worked, which is what makes both accumulators measure
   * *effort* rather than elapsed time - a site nobody is building has nothing new to say and nothing new to
   * record.
   */
  fun work(deltaTime: Float) {
    countdown(deltaTime)
    sinceLastPersist += deltaTime
  }

  /** Whether enough work has gone in to be worth a row write - see [ConstructionSystem]. */
  val isPersistDue: Boolean
    get() {
      return sinceLastPersist >= PERSIST_INTERVAL
    }

  fun markPersisted() {
    sinceLastPersist = 0f
  }

  /** How much of the work is done, 0 to 1. What the client fades the art in by. */
  val progress: Float
    get() {
      return ((totalSeconds - remainingSeconds) / totalSeconds).coerceIn(0f, 1f)
    }

  override fun toEntityMessage(entityId: Long, removed: Boolean): EntitySMSG {
    return ConstructionComponentSMSG(
      entityId = entityId,
      remainingSeconds = remainingSeconds.coerceAtLeast(0f),
      totalSeconds = totalSeconds,
      active = active,
      removed = removed
    )
  }

  // Everyone nearby sees the scaffolding, the same choice Casting and Crafting make about their bars.
  override fun syncTargets(world: World, entityId: EntityId): SyncTargets {
    return SyncTargets.PublicInRange
  }

  companion object {
    /**
     * What a site can take before any work has gone into it.
     *
     * One, not zero, for the reason `PropPromotionService` gives for its baseline stat: a defender at exactly
     * zero degenerates the damage ratio. It is also the design - a site is meant to be trivially wrecked at
     * the start and solid at the end.
     */
    const val START_HP = 1

    private const val RESYNC_INTERVAL = 2f

    /**
     * Worked seconds between two row writes.
     *
     * A crash costs at most this much progress, against twenty JPA round trips a second per site if the row
     * were written whenever the number changed.
     */
    private const val PERSIST_INTERVAL = 10f
  }
}
