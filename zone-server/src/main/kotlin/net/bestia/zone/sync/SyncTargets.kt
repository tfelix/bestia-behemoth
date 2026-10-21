package net.bestia.zone.sync

sealed interface SyncTargets {
  /** Sent to every account that sees the entity; see [net.bestia.zone.aoi.EntityAudience]. */
  data object PublicInRange : SyncTargets

  /** Sends this component only to the owner of the entity. Only works for PlayerBestia and Master */
  data object OwnerOnly : SyncTargets

  /**
   * The owner, plus the party of the master it belongs to while that master or this entity is the one being
   * played, wherever they stand. Resolved by [net.bestia.zone.aoi.EntityAudience.ownerAndParty], because who is
   * playing what lives in the session, which a component cannot see.
   */
  data object OwnerAndParty : SyncTargets

  /** Send only to these specific accounts. */
  data class Accounts(val accountIds: Set<Long>) : SyncTargets
}
