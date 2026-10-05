package net.bestia.zone.ecs

sealed interface SyncTargets {
  /** Sent to every account that sees the entity; see [net.bestia.zone.ecs.visibility.EntityAudience]. */
  data object PublicInRange : SyncTargets

  /** Sends this component only to the owner of the entity. Only works for PlayerBestia and Master */
  data object OwnerOnly : SyncTargets

  /** Send only to these specific accounts (e.g. the owner, plus party members). */
  data class Accounts(val accountIds: Set<Long>) : SyncTargets
}