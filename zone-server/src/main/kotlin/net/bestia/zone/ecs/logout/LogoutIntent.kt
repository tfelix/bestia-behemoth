package net.bestia.zone.ecs.logout

import net.bestia.zone.ecs.Dirtyable
import net.bestia.zone.ecs.RemovableComponentType
import net.bestia.zone.ecs.RemovalNotifiable
import net.bestia.zone.ecs.SyncTargets
import net.bestia.zone.ecs.core.Component
import net.bestia.zone.ecs.core.World
import net.bestia.zone.message.EntitySMSG
import net.bestia.zone.util.EntityId

/**
 * Marks a master entity as logging out (WoW-style delayed logout). While present the entity stays in
 * the world; once [remainingSeconds] reaches zero the [LogoutSystem] persists-and-despawns it, which
 * the client sees as a vanish and treats as "logout complete".
 *
 * Synced owner-only via the [Dirtyable] pipeline so the client can show a countdown; it is re-sent
 * roughly every [SYNC_INTERVAL_SECONDS] to correct drift while the client interpolates locally in
 * between. It is [RemovalNotifiable]: cancelling a logout is simply removing this component, which
 * emits a `ComponentRemovedSMSG` the client reads as "logout aborted".
 */
class LogoutIntent(
  remainingSeconds: Float = DEFAULT_LOGOUT_SECONDS
) : Component, Dirtyable, RemovalNotifiable {

  var remainingSeconds: Float = remainingSeconds
    set(value) {
      if (field != value) {
        field = value
        dirty = true
      }
    }

  private var dirty = true

  override val removableComponentType = RemovableComponentType.LOGOUT_INTENT

  override fun isDirty(): Boolean = dirty

  override fun markDirty() {
    dirty = true
  }

  override fun clearDirty() {
    dirty = false
  }

  override fun toEntityMessage(entityId: Long): EntitySMSG =
    LogoutIntentComponentSMSG(entityId = entityId, remainingSeconds = remainingSeconds.coerceAtLeast(0f))

  // Only the logging-out player cares about their own countdown.
  override fun syncTargets(world: World, entityId: EntityId): SyncTargets = SyncTargets.OwnerOnly

  companion object {
    const val DEFAULT_LOGOUT_SECONDS = 20f
  }
}
