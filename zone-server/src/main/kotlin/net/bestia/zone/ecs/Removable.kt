package net.bestia.zone.ecs

/**
 * Opt-in for [Dirtyable] components whose *removal* is itself meaningful to the client (as opposed
 * to the whole entity vanishing, which is a separate signal). There is nothing to implement beyond
 * the marker: [net.bestia.zone.ecs.ZoneEngine] detects a [Removable] component being taken off an
 * entity and calls its own [Dirtyable.toEntityMessage] one more time with `removed = true`,
 * delivered to the same [SyncTargets] it already syncs to. Reusing the component's existing message
 * type - instead of a separate generic "removed" message - means a new removable component needs no
 * wiring beyond this interface: no id to register anywhere, nothing for the client to dispatch on
 * that it doesn't already handle for the component's normal sync.
 */
interface Removable : Dirtyable
