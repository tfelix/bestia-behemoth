package net.bestia.zone.ecs

/**
 * Opt-in for [Dirtyable] components that want the client notified when the component is *removed*
 * from an entity (as opposed to the whole entity being destroyed, which is already covered by the
 * vanish path). [net.bestia.zone.ecs.ZoneEngine] turns each such removal into a generic
 * `ComponentRemovedSMSG` delivered to the same [SyncTargets] the component syncs to.
 *
 * A component must be [Dirtyable] as well so its live [SyncTargets] can be resolved at removal time.
 */
interface RemovalNotifiable {
  val removableComponentType: RemovableComponentType
}

/**
 * Stable identifiers for the components that participate in removal notifications. Mirrors the
 * `RemovableComponent` enum in `component_removed_smsg.proto` and the client-side dispatch — keep the
 * three in sync when adding a new removable component.
 */
enum class RemovableComponentType {
  LOGOUT_INTENT
}
