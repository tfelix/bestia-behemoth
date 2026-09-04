package net.bestia.zone.ecs.core

import net.bestia.zone.message.EntitySMSG
import net.bestia.zone.util.EntityId

/**
 * Opt-in for [Dirtyable] components whose *removal* is itself meaningful to the client (as opposed
 * to the whole entity vanishing, which is a separate signal). There is nothing to implement beyond
 * the marker: [net.bestia.zone.ecs.ZoneEngine] detects a [Removable] component being taken off an
 * entity and calls its own [Dirtyable.toEntityMessage] one more time with `removed = true`,
 * delivered to the same [net.bestia.zone.ecs.SyncTargets] it already syncs to. Reusing the component's existing message
 * type - instead of a separate generic "removed" message - means a new removable component needs no
 * wiring beyond this interface: no id to register anywhere, nothing for the client to dispatch on
 * that it doesn't already handle for the component's normal sync.
 */
interface Removable : Dirtyable {

  /**
   * The message announcing this component's removal.
   *
   * Defaults to exactly what the marker promises above - the component's own message, re-sent with
   * `removed = true`. Override it only when the notification has to say something the component itself
   * cannot answer: [net.bestia.zone.ecs.movement.Path] does, because a stop has to carry *where* the
   * entity stopped, which lives on its [net.bestia.zone.ecs.movement.Position] and not on the path.
   *
   * Called with the world lock held, from the removal itself, so the entity's other components are
   * still readable and still current. Do no I/O here.
   */
  fun toRemovedMessage(world: World, entityId: EntityId): EntitySMSG = toEntityMessage(entityId, removed = true)
}