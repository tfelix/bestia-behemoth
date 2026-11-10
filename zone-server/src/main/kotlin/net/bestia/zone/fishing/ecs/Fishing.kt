package net.bestia.zone.fishing.ecs

import net.bestia.zone.ecs.core.World
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.message.EntitySMSG
import net.bestia.zone.sync.DirtyableComponent
import net.bestia.zone.sync.Removable
import net.bestia.zone.sync.SyncTargets
import net.bestia.zone.util.EntityId

/**
 * A master's line in the water. It waits for a bite, then bites for a short window. [FishingSystem] moves it on
 * and reels it in; a strike resolves it, see [net.bestia.zone.fishing.FishingService].
 */
class Fishing(
  val bobber: Vec3L,
  val castFrom: Vec3L,
  val rodUniqueId: Long,
  waitSeconds: Float,
) : DirtyableComponent(), Removable {

  var secondsLeft: Float = waitSeconds
    private set

  var isBiting: Boolean = false
    private set

  /** When the bite began, in `System.nanoTime` terms, to time the strike against. */
  var biteStartedNanos: Long = 0L
    private set

  val isDue: Boolean
    get() = secondsLeft <= 0f

  fun countDown(deltaSeconds: Float) {
    secondsLeft -= deltaSeconds
  }

  fun bite(windowSeconds: Float, nowNanos: Long) {
    isBiting = true
    secondsLeft = windowSeconds
    biteStartedNanos = nowNanos
    markDirty()
  }

  override fun toEntityMessage(entityId: Long, removed: Boolean): EntitySMSG {
    return FishingComponentSMSG(entityId, bobber, isBiting, removed)
  }

  // Bystanders see the bobber too, so an angler at the shore is visibly busy.
  override fun syncTargets(world: World, entityId: EntityId): SyncTargets {
    return SyncTargets.PublicInRange
  }
}
