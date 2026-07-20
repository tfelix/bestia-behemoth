package net.bestia.account

/**
 * A role bundles a set of [Authority]s. Roles are carried inside the login JWT (as the `role`
 * claim) and translated into the concrete authorities when the token is validated on the zone.
 */
enum class Role(val authorities: Set<Authority>) {
  USER(setOf(Authority.ITEM, Authority.MAP_MOVE)),
  GM(setOf(Authority.ITEM, Authority.MAP_MOVE, Authority.KILL, Authority.EXP, Authority.SPAWN)),
  SUPER_GM(Authority.entries.toSet())
}
